// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotMessages.*;
/** One independently reaped JVM. Blocking pipe work stays on the worker thread; cancellation can kill a stalled write. */
final class BotProcessSession implements AutoCloseable {
    public record Request(MapSnapshot snapshot,WorldDelta delta,List<Perception> frames){public Request{frames=List.copyOf(frames);}}
    public record Response(boolean rejected,long processed,List<Intent> intents,Map<String,Number> planning,Map<String,Object> behavior,List<String> faults){public Response{intents=List.copyOf(intents);planning=Map.copyOf(planning);behavior=BotMessages.freeze(behavior);faults=List.copyOf(faults);}}
    @FunctionalInterface interface Launcher {Process start(ProcessBuilder builder)throws IOException;}
    static final Launcher DEFAULT_LAUNCHER=ProcessBuilder::start;
    private record Fault(IOException error){}
    private final ArrayBlockingQueue<Object> responses=new ArrayBlockingQueue<>(1);private final double decisionHz,planningRate;private final Launcher launcher;private volatile Process process;private Thread reader;private volatile boolean cancelled;
    BotProcessSession(double decisionHz,double planningRate,Launcher launcher){this.decisionHz=decisionHz;this.planningRate=planningRate;this.launcher=Objects.requireNonNull(launcher);}
    Integer processId(){var child=process;return child==null||!child.isAlive()?null:Math.toIntExact(child.pid());}
    private void start()throws IOException{if(cancelled)throw new IOException("Bot process cancelled");String executable=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java";var command=List.of(Path.of(System.getProperty("java.home"),"bin",executable).toString(),"-Xmx512m","-cp",classpath(),BotProcessMain.class.getName(),Double.toString(decisionHz),Double.toString(planningRate));var child=launcher.start(new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT));process=child;if(cancelled){child.destroyForcibly();throw new IOException("Bot process cancelled during startup");}reader=Thread.ofPlatform().daemon().name("bot-process-pipe-reader").start(()->{try{while(!cancelled){Object value=BotWire.receive(child.getInputStream());if(!(value instanceof Response))throw new IOException("Unexpected bot process response");responses.put(value);}}catch(IOException error){responses.offer(new Fault(error));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});}
    Response exchange(Request request)throws IOException,TimeoutException{if(process==null)start();BotWire.send(process.getOutputStream(),request);Object response;try{response=responses.poll(request.snapshot()==null?3:45,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Bot process exchange interrupted",interrupted);}if(response==null)throw new TimeoutException("Bot process stopped acknowledging work");if(response instanceof Fault fault)throw fault.error();return (Response)response;}
    static String classpath(){var paths=new LinkedHashSet<String>();for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))if(!entry.isEmpty())paths.add(Path.of(entry).toAbsolutePath().normalize().toString());for(ClassLoader loader=Thread.currentThread().getContextClassLoader();loader!=null;loader=loader.getParent())if(loader instanceof URLClassLoader urls)for(var url:urls.getURLs())if(url.getProtocol().equals("file"))try{paths.add(Path.of(url.toURI()).toAbsolutePath().normalize().toString());}catch(URISyntaxException error){throw new IllegalArgumentException("Invalid worker classpath",error);}try{paths.add(Path.of(BotProcessMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());}catch(URISyntaxException error){throw new IllegalArgumentException(error);}return String.join(File.pathSeparator,paths);}
    /** Called from the gameplay owner during close, without waiting for a pipe reader or writer lock. */
    void cancel(){cancelled=true;var child=process;if(child!=null)child.destroyForcibly();var active=reader;if(active!=null)active.interrupt();}
    @Override public void close()throws IOException{cancel();var child=process;if(child!=null)try{if(!child.waitFor(2,TimeUnit.SECONDS))throw new IOException("Bot child process did not exit; retaining its handle");if(reader!=null){reader.join(java.time.Duration.ofSeconds(1));if(reader.isAlive())throw new IOException("Bot child pipe reader did not exit");}}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Bot child reap interrupted",interrupted);}}
}
