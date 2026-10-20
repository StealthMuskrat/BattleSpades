// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.tomlj.TomlTable;
/** Bounded nonblocking producers; formatting, rotation and sinks run on one retained worker. */
public final class LoggingRuntime {
    public enum Level {DEBUG,INFO,WARNING,ERROR,CRITICAL}
    public record Settings(Path file,boolean console,int capacity,long maxBytes,int backups,Level level){
        public Settings{Objects.requireNonNull(file);Objects.requireNonNull(level);capacity=Math.max(256,capacity);maxBytes=Math.max(1048576,maxBytes);backups=Math.max(1,backups);}
        public static Settings from(TomlTable raw,Path logDirectory){String file=raw.getString("logging.file",()->"server.log");String text=raw.getString("logging.level",()->"INFO").toUpperCase(Locale.ROOT);Level level;try{level=Level.valueOf(text);}catch(IllegalArgumentException unknown){level=Level.INFO;}return new Settings(logDirectory.resolve(file),raw.getBoolean("logging.console",()->true),Math.toIntExact(raw.getLong("logging.queue_capacity",()->8192)),raw.getLong("logging.max_bytes",()->16777216),Math.toIntExact(raw.getLong("logging.backup_count",()->3)),level);}
    }
    public interface Sink extends AutoCloseable {void write(String record)throws IOException;@Override void close()throws IOException;}
    private record Event(Instant time,Level level,String logger,Supplier<String> message,String exception){}
    private final ArrayBlockingQueue<Event> queue;private final List<Sink> sinks;private final Level level;private final AtomicLong dropped=new AtomicLong(),failures=new AtomicLong();private final CompletableFuture<Void> stopped=new CompletableFuture<>();private final Object admission=new Object();private volatile boolean stopping;
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    public LoggingRuntime(int capacity,Level level,List<Sink> sinks){this.queue=new ArrayBlockingQueue<>(Math.max(256,capacity));this.level=Objects.requireNonNull(level);this.sinks=List.copyOf(sinks);Thread.ofPlatform().name("battlespades-log").daemon(true).start(this::run);}
    public static LoggingRuntime open(Settings settings)throws IOException {var sinks=new ArrayList<Sink>();sinks.add(new RotatingFile(settings));if(settings.console())sinks.add(new Sink(){public void write(String s){System.err.print(s);}public void close(){System.err.flush();}});return new LoggingRuntime(settings.capacity(),settings.level(),sinks);}
    /** Capture mutable arguments before calling. Supplier evaluation happens on the logging worker. */
    public boolean log(Level severity,String logger,Supplier<String> message,Throwable exception){Objects.requireNonNull(severity);Objects.requireNonNull(logger);Objects.requireNonNull(message);if(severity.ordinal()<level.ordinal())return true;String trace=null;if(exception!=null){var text=new StringWriter();exception.printStackTrace(new PrintWriter(text));trace=text.toString();}var event=new Event(Instant.now(),severity,logger,message,trace);synchronized(admission){if(stopping||!queue.offer(event)){dropped.incrementAndGet();return false;}}return true;}
    public long droppedRecords(){return dropped.get();}public long failedRecords(){return failures.get();}
    /** Idempotent, returns immediately even with a full queue; completion means every admitted event and sink has drained. */
    public CompletionStage<Void> stop(){synchronized(admission){stopping=true;}return stopped.minimalCompletionStage();}
    private void run(){try{while(!stopping||!queue.isEmpty()){Event e;try{e=queue.poll(100,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){continue;}if(e==null)continue;String text;try{text=FORMAT.format(e.time())+" ["+e.level()+"] "+e.logger()+": "+e.message().get()+"\n"+(e.exception()==null?"":e.exception());}catch(RuntimeException bad){failures.incrementAndGet();continue;}for(var sink:sinks)try{sink.write(text);}catch(IOException|RuntimeException bad){failures.incrementAndGet();}}}finally{for(var sink:sinks)try{sink.close();}catch(IOException|RuntimeException bad){failures.incrementAndGet();}stopped.complete(null);}}
    private static final class RotatingFile implements Sink{
        private final Settings settings;private OutputStream stream;private long size;
        RotatingFile(Settings settings)throws IOException{this.settings=settings;Files.createDirectories(settings.file().toAbsolutePath().getParent());open();}
        private void open()throws IOException{stream=Files.newOutputStream(settings.file(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);size=Files.size(settings.file());}
        public void write(String record)throws IOException{byte[] bytes=record.getBytes(StandardCharsets.UTF_8);if(size>0&&size+record.codePointCount(0,record.length())>=settings.maxBytes()){stream.close();for(int i=settings.backups();i>=1;i--){Path old=i==1?settings.file():Path.of(settings.file()+"."+(i-1));if(Files.exists(old))Files.move(old,Path.of(settings.file()+"."+i),StandardCopyOption.REPLACE_EXISTING);}open();}stream.write(bytes);stream.flush();size+=bytes.length;}
        public void close()throws IOException{stream.close();}
    }
}
