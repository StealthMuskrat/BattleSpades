// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import net.battlespades.plugin.RuntimePlugin;
/** Builds actual external SPI artifacts with the current JDK; never included in production. */
public final class PluginFixtures {
    private PluginFixtures(){}
    public static Path jar(Path directory,String filename,Map<String,String> sources,String providers)throws Exception{
        Files.createDirectories(directory);Path work=Files.createTempDirectory(directory.getParent(),"plugin-compile-");var args=new ArrayList<>(List.of("--release","25","-encoding","UTF-8","-classpath",Path.of(RuntimePlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),"-d",work.toString()));for(var entry:sources.entrySet()){Path source=work.resolve(entry.getKey());Files.createDirectories(source.getParent());Files.writeString(source,entry.getValue());args.add(source.toString());}var compiler=Objects.requireNonNull(ToolProvider.getSystemJavaCompiler());var output=new ByteArrayOutputStream();int code=compiler.run(null,output,output,args.toArray(String[]::new));if(code!=0)throw new IOException("Plugin compilation: "+output.toString(StandardCharsets.UTF_8));Path target=directory.resolve(filename);try(var jar=new JarOutputStream(Files.newOutputStream(target));var files=Files.walk(work)){for(Path file:files.filter(p->p.toString().endsWith(".class")).sorted().toList()){jar.putNextEntry(new JarEntry(work.relativize(file).toString().replace('\\','/')));Files.copy(file,jar);jar.closeEntry();}jar.putNextEntry(new JarEntry("META-INF/services/"+RuntimePlugin.class.getName()));jar.write(providers.getBytes(StandardCharsets.UTF_8));jar.closeEntry();}return target;
    }
    public static String example()throws IOException{return Files.readString(Path.of("examples/plugins/ExamplePlugin.java"));}
    public static String gate(){return """
        package external;
        import java.util.concurrent.*;
        import net.battlespades.plugin.RuntimePlugin;
        import net.battlespades.match.ModeEvents;
        public final class GatePlugin implements RuntimePlugin {
          private Context context;private CompletableFuture<?> entered;private CompletionStage<Void> gate=CompletableFuture.completedFuture(null);
          public String name(){return "Gate";}
          public CompletionStage<Void> onLoad(Context context){this.context=context;context.log("loaded");return CompletableFuture.completedFuture(null);}
          public CompletionStage<Void> onEvent(ModeEvents.Event event){
            if(event.name().equals("pause")&&event.arguments().getFirst() instanceof CompletionStage<?> wait){gate=wait.thenApply(_->null);if(event.arguments().size()>1&&event.arguments().get(1) instanceof CompletableFuture<?> started)entered=started;return CompletableFuture.completedFuture(null);}
            if(event.name().equals("hold")&&event.arguments().getFirst() instanceof CompletionStage<?> wait)return wait.thenApply(_->null);
            if(event.name().equals("on_tick")){if(!gate.toCompletableFuture().isDone()&&entered!=null)entered.complete(null);return gate;}
            if(event.name().equals("probe")||event.name().equals("on_player_spawn"))context.broadcast("External plugin event");
            return CompletableFuture.completedFuture(null);
          }
          public CompletionStage<Void> onUnload(){context.log("unloaded");return CompletableFuture.completedFuture(null);}
        }
        """;}
}
