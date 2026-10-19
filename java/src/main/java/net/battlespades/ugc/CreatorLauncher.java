// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.battlespades.config.ServerConfig;
import net.battlespades.scene.SceneConfig;
import net.battlespades.world.*;

/** Standalone world-creation lifecycle with portable project paths and an owner-thread shutdown save. */
public final class CreatorLauncher {
    private CreatorLauncher(){}
    private static final Set<String> FLAGS=Set.of("offline","check","overwrite","control-stdin");
    private static final Set<String> VALUES=Set.of("config","project","output-dir","publish-root","terrain","target-mode","retail-root","title","description","author","prefab-set","port","bind");
    public record Options(ServerConfig config,UgcAssets assets,Path directory,String name,
            String terrain,String mode,String title,String description,String author,Integer palette,
            InetSocketAddress bind,boolean overwrite,boolean controlStdin,boolean check) {
        public Options{if(title!=null&&(title.isBlank()||title.codePointCount(0,title.length())>80||title.indexOf('\0')>=0||title.indexOf('\r')>=0||title.indexOf('\n')>=0))throw new IllegalArgumentException("Title must contain 1..80 characters without newlines");}
        public UgcWorkspace.Opened prepare()throws IOException{
            var files=UgcWorkspace.paths(directory,name);UgcWorkspace.Opened opened;
            if(Files.isRegularFile(files.sidecar())&&!overwrite){
                opened=UgcWorkspace.open(directory,name,mode);
                if(terrain!=null&&!UgcProject.terrain(terrain).equals(opened.project().terrain()))throw new IllegalArgumentException("--terrain cannot replace an existing project; use --overwrite explicitly");
            }else{
                var p=new UgcProject(title==null?name:title,description==null?"":description,author==null?"":author,terrain==null?"grassland":terrain,mode==null?"tdm":mode);
                var presentation=UgcAssets.presentation(Files.readString(assets.files(p.terrain()).metadata()));p.skybox(presentation.skybox());p.groundColors(presentation.colors());if(palette!=null)p.prefabSet(palette);
                opened=UgcWorkspace.create(p,directory,name,assets,overwrite);
            }
            var p=opened.project();if(mode!=null)p.targetMode(mode);if(title!=null&&!p.title(title))throw new IllegalArgumentException("Title must contain 1..80 characters without newlines");if(description!=null)p.description(description);if(author!=null)p.author(author);if(palette!=null)p.prefabSet(palette);
            if(assets.compatible(p.prefabTerrain()).isEmpty())throw new IOException("No installed constructs match this project's palette");return opened;
        }
        public void preflight()throws IOException{
            var models=new HashMap<String,Path>();try(var paths=Files.list(assets.prefabs())){for(var p:paths.filter(Files::isRegularFile).toList())models.putIfAbsent(p.getFileName().toString().toLowerCase(Locale.ROOT),p);}
            var checked=new HashSet<String>();
            for(var t:UgcProject.TERRAINS){var files=assets.files(t);VxlMap.loadServer(Files.readAllBytes(files.vxl()),"retail");var names=assets.compatible(t);if(names.isEmpty())throw new IOException("No installed constructs for "+t.key());for(var name:names){String key=(name+".kv6").toLowerCase(Locale.ROOT);if(checked.add(key)&&Kv6.decode(Files.readAllBytes(models.get(key))).voxels().isEmpty())throw new IOException("Empty creator construct: "+name);}}
            var files=UgcWorkspace.paths(directory,name);if(Files.exists(files.sidecar())&&!overwrite){var opened=UgcWorkspace.open(directory,name,mode);VxlMap.loadServer(opened.source(),"retail");new UgcWorkspace(files).verifySceneMetadata();}
        }
    }
    private static Path resolve(Path root,String value){var p=Path.of(value);return (p.isAbsolute()?p:root.resolve(p)).toAbsolutePath().normalize();}
    public static Options options(String[] args)throws IOException{
        if(args.length==0||!args[0].equals("--ugc-host"))throw new IllegalArgumentException("Expected --ugc-host");
        var values=new LinkedHashMap<String,String>();var flags=new HashSet<String>();
        for(int i=1;i<args.length;i++){
            if(!args[i].startsWith("--"))throw new IllegalArgumentException("Unexpected creator argument: "+args[i]);String key=args[i].substring(2);
            if(FLAGS.contains(key)){if(!flags.add(key))throw new IllegalArgumentException("Repeated --"+key);}
            else if(VALUES.contains(key)){if(i+1==args.length||args[i+1].startsWith("--"))throw new IllegalArgumentException("Missing value for --"+key);if(values.putIfAbsent(key,args[++i])!=null)throw new IllegalArgumentException("Repeated --"+key);}
            else throw new IllegalArgumentException("Unknown creator option --"+key);
        }
        Path configPath=Path.of(values.getOrDefault("config","config.toml")).toAbsolutePath().normalize();var config=ServerConfig.load(configPath);Path root=config.root();var raw=config.raw();
        java.util.function.Function<String,String> setting=key->{String cli=values.get(key);if(cli!=null&&!cli.isBlank())return cli;Object v=raw.get("map_creator."+key.replace('-','_'));return v==null||v.toString().isBlank()?null:v.toString();};
        String project=setting.apply("project");if(project==null)project="MyUGCMap";
        String output,publish;if(values.containsKey("output-dir")||values.containsKey("publish-root")){output=values.get("output-dir");publish=values.get("publish-root");}else{output=setting.apply("output-dir");publish=setting.apply("publish-root");}
        if(output!=null&&publish!=null)throw new IllegalArgumentException("--output-dir and --publish-root are mutually exclusive");
        Path supplied=Path.of(project);String filename=supplied.getFileName().toString();boolean path=supplied.getParent()!=null||filename.toLowerCase(Locale.ROOT).endsWith(".ugc");Path directory;
        if(path){Path file=resolve(root,project);directory=file.getParent();if(filename.toLowerCase(Locale.ROOT).endsWith(".ugc"))filename=filename.substring(0,filename.length()-4);}
        else directory=publish!=null?UgcWorkspace.publishDirectory(resolve(root,publish)):resolve(root,output==null?"ugc-projects":output);
        String name=UgcProject.slug(filename),terrain=setting.apply("terrain"),mode=setting.apply("target-mode");if(terrain!=null)terrain=UgcProject.terrain(terrain).key();if(mode!=null)mode=UgcProject.target(mode);
        String paletteText=setting.apply("prefab-set");Integer palette=paletteText==null?null:Integer.valueOf(paletteText);if(palette!=null&&(palette<0||palette>=6))throw new IllegalArgumentException("--prefab-set must be 0..5");
        String retail=setting.apply("retail-root");var assets=UgcAssets.discover(retail==null?List.of(root,root.resolve("client")):List.of(resolve(root,retail)));
        int port=values.containsKey("port")?Integer.parseInt(values.get("port")):config.port();if(port<1||port>65535)throw new IllegalArgumentException("Creator port must be 1..65535");
        String address=values.getOrDefault("bind","127.0.0.1");var bind=new InetSocketAddress(address,port);if(bind.isUnresolved())throw new IllegalArgumentException("Unresolved creator bind address");
        SceneConfig.from(config,"tdm");
        return new Options(config,assets,directory,name,terrain,mode,setting.apply("title"),setting.apply("description"),setting.apply("author"),palette,bind,flags.contains("overwrite"),flags.contains("control-stdin"),flags.contains("check"));
    }
    public static int run(String[] args,InputStream input,PrintStream out,PrintStream error){
        try{
            var options=options(args);options.preflight();
            if(options.check()){out.println("World creator preflight passed: 9 terrains, installed constructs, configuration and project paths");return 0;}
            var opened=options.prepare();return serve(options,opened,input,out,error);
        }catch(IOException|IllegalArgumentException|IllegalStateException e){error.println("World creator error: "+e.getMessage());return 2;}
    }
    private static int serve(Options options,UgcWorkspace.Opened opened,InputStream input,PrintStream out,PrintStream error)throws IOException{
        var stop=new AtomicBoolean();var failed=new AtomicBoolean();var done=new CountDownLatch(1);var commands=new ArrayBlockingQueue<String>(64);
        var hook=new Thread(()->{stop.set(true);try{done.await(30,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"creator-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        Thread reader=null;
        try(var creator=CreatorHost.open(options.bind(),SceneConfig.from(options.config(),"tdm"),options.config().raw(),opened,options.assets(),e->{failed.set(true);error.println("World creator runtime error: "+e.getMessage());})){
            if(options.controlStdin())reader=Thread.ofPlatform().daemon().name("creator-operator-input").start(()->{
                try{var text=new InputStreamReader(input,StandardCharsets.UTF_8);StringBuilder line=new StringBuilder();boolean overflow=false;for(int c;(c=text.read())!=-1;){if(c=='\n'){if(overflow){stop.set(true);break;}String command=line.toString().strip();line.setLength(0);if(command.equals("shutdown")||command.equals("stop")){stop.set(true);break;}if(!command.isEmpty()&&!commands.offer(command)){stop.set(true);break;}}else if(c!='\r'){if(line.length()<4096)line.append((char)c);else overflow=true;}}if(!overflow&&!line.isEmpty())commands.offer(line.toString().strip());}catch(IOException e){error.println("Creator operator input error: "+e.getMessage());}finally{stop.set(true);}
            });
            out.println("World creator listening on "+creator.address());out.println("Project: "+opened.files().sidecar());out.println("Commands: status, title TEXT, mode MODE, skybox NAME.txt, save, shutdown");out.flush();
            long started=System.nanoTime();while(!stop.get()&&!Thread.currentThread().isInterrupted()){
                creator.service((System.nanoTime()-started)/1_000_000);for(int n=0;n<16;n++){String command=commands.poll();if(command==null)break;creator.scene().control(command).thenAccept(out::println);}LockSupport.parkNanos(1_000_000);
            }
            // Commands already read before EOF/shutdown still run before the final checkpoint.
            for(String command;(command=commands.poll())!=null;)creator.scene().control(command).thenAccept(out::println);
        }finally{
            done.countDown();try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown is already waiting for this owner. */}
            if(reader!=null)reader.interrupt();
        }
        out.println("World creator stopped; project saved");out.flush();return failed.get()?2:0;
    }
}
