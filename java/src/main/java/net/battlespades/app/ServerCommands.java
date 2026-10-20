// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.battlespades.config.ServerConfig;
import net.battlespades.world.JsonData;
/** Long-running standalone loop, bounded console control and owner-driven graceful termination. */
final class ServerCommands {
    private ServerCommands(){}
    static int run(String[] args,InputStream input,PrintStream out,PrintStream error){
        Path configPath=Path.of("config.toml");String bind=null;Integer port=null;boolean control=false;double duration=0;
        try{for(int i=args.length>0&&args[0].equals("--serve")?1:0;i<args.length;i++){String flag=args[i];if(flag.equals("--control-stdin")){control=true;continue;}if(i+1>=args.length)throw new IllegalArgumentException("Missing value for "+flag);String value=args[++i];switch(flag){case "--config"->configPath=Path.of(value);case "--bind"->bind=value;case "--port"->{port=Integer.parseInt(value);if(port<1||port>65535)throw new IllegalArgumentException("Port must be 1..65535");}case "--shutdown-after"->{duration=Double.parseDouble(value);if(!Double.isFinite(duration)||duration<=0||duration>86400)throw new IllegalArgumentException("Shutdown duration must be 0..86400 seconds");}default->throw new IllegalArgumentException("Unknown server option "+flag);}}
            var core=ServerConfig.load(configPath);if(port!=null)core=new ServerConfig(core.name(),port,core.maxPlayers(),core.steamId(),core.maxConnections(),core.incomingCapacity(),core.drainBudget(),core.requireProtocolVersion(),core.root(),core.raw());var settings=ServerSettings.from(core);var requested=new AtomicBoolean();var closed=new CountDownLatch(1);Thread hook=Thread.ofPlatform().name("server-shutdown-signal").unstarted(()->{requested.set(true);try{closed.await(35,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});Runtime.getRuntime().addShutdownHook(hook);
            try(var console=new ConsoleControl(input,out,control);var server=DedicatedServer.open(settings,bind==null?null:new InetSocketAddress(bind,core.port()))){
                long origin=System.nanoTime(),startupDeadline=origin+120000000000L;while(!server.ready().toCompletableFuture().isDone()&&!requested.get()){if(System.nanoTime()>=startupDeadline)throw new IOException("Plugin startup timed out");server.pump(System.nanoTime()-origin);LockSupport.parkNanos(1000000);}if(requested.get())return 0;server.start();console.reply("READY "+JsonData.encode(server.status()).stripTrailing());long began=System.nanoTime();
                while(!server.stopping()&&!requested.get()){server.pump(System.nanoTime()-origin);for(int n=0;n<8;n++){String line=console.poll();if(line==null)break;try{server.command(line).whenComplete((reply,failure)->console.reply(failure==null?reply:"Command failed: "+failure.getMessage()));}catch(RuntimeException failure){console.reply("Command failed: "+failure.getMessage());}}if(duration>0&&(System.nanoTime()-began)/1e9>=duration)requested.set(true);LockSupport.parkNanos(1000000);}return 0;
            }finally{closed.countDown();try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shutdownInProgress){/* The running signal hook observes closed above. */}}
        }catch(IOException|IllegalArgumentException|IllegalStateException|CompletionException failure){error.println("Server failed: "+failure.getMessage());return 2;}
    }
}
