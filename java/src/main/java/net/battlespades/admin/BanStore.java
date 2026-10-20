// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.admin;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.world.JsonData;
import net.battlespades.world.MetadataDocument;

/** Source-compatible bans.json; owner memory, ordered immutable disk writes and an exclusive process lease. */
public final class BanStore implements AutoCloseable {
    public record Entry(String name,String reason,double until){
        public Entry{Objects.requireNonNull(name);Objects.requireNonNull(reason);if(name.length()>256||reason.length()>4096||!Double.isFinite(until)||until<0)throw new IllegalArgumentException("Invalid ban entry");}
        public int disconnectReason(){return until==0?1:19;}
    }
    private static final int MAX_ENTRIES=32768,MAX_BYTES=4*1024*1024;
    private final Thread owner=Thread.currentThread();private final Path path;private final DoubleSupplier wall;private final Executor writer;private final Consumer<Throwable> failure;
    private final FileChannel leaseChannel;private final FileLock lease;private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private CompletableFuture<Void> tail=CompletableFuture.completedFuture(null);private boolean closed;
    public BanStore(Path path,DoubleSupplier wall,Executor writer,Consumer<Throwable> failure)throws IOException{
        this.path=path.toAbsolutePath().normalize();this.wall=Objects.requireNonNull(wall);this.writer=Objects.requireNonNull(writer);this.failure=Objects.requireNonNull(failure);
        Files.createDirectories(this.path.getParent());leaseChannel=FileChannel.open(this.path.resolveSibling(this.path.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired=null;
        try{try{acquired=leaseChannel.tryLock();}catch(OverlappingFileLockException conflict){throw new IOException("Ban store is already in use",conflict);}if(acquired==null)throw new IOException("Ban store is already in use");load();lease=acquired;}
        catch(IOException|RuntimeException|Error error){if(acquired!=null)acquired.close();leaseChannel.close();throw error;}
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Ban store outside active owner");}
    private static String key(String host){if(host==null||host.isBlank()||host.length()>256||host.equals("unknown"))throw new IllegalArgumentException("Invalid ban address");return host;}
    private double now(){double now=wall.getAsDouble();if(!Double.isFinite(now)||now<0)throw new IllegalStateException("Invalid ban wall clock");return now;}
    private void load()throws IOException{
        if(!Files.exists(path))return;
        try{if(Files.size(path)>MAX_BYTES)throw new IllegalArgumentException("Ban document too large");Object data=MetadataDocument.json(Files.readString(path));if(!(data instanceof Map<?,?> rows)||rows.size()>MAX_ENTRIES)throw new IllegalArgumentException("Invalid ban document");
            for(var row:rows.entrySet()){if(!(row.getKey() instanceof String host)||!(row.getValue() instanceof Map<?,?> e)||!(e.get("name") instanceof String name)||!(e.get("reason") instanceof String reason)||!(e.get("until") instanceof Number until))throw new IllegalArgumentException("Invalid ban row");entries.put(key(host),new Entry(name,reason,until.doubleValue()));}}
        catch(IllegalArgumentException error){throw new IOException("Invalid ban store: "+path.getFileName(),error);}
    }
    public Optional<Entry> lookup(String host){owned();var entry=entries.get(host);if(entry!=null&&entry.until()!=0&&now()>=entry.until()){entries.remove(host);persist();return Optional.empty();}return Optional.ofNullable(entry);}
    public Map<String,Entry> snapshot(){owned();return Collections.unmodifiableMap(new LinkedHashMap<>(entries));}
    public CompletionStage<Void> add(String host,String name,String reason,long seconds){owned();host=key(host);if(entries.size()>=MAX_ENTRIES&&!entries.containsKey(host))throw new IllegalStateException("Ban store capacity reached");var entry=new Entry(name,reason,seconds<=0?0:now()+seconds);var prospective=new LinkedHashMap<>(entries);prospective.put(host,entry);encode(prospective);entries.put(host,entry);return persist();}
    public CompletionStage<Boolean> remove(String host){owned();boolean removed=entries.remove(host)!=null;return removed?persist().thenApply(unused->true):CompletableFuture.completedFuture(false);}
    private static byte[] encode(Map<String,Entry> source){var rows=new LinkedHashMap<String,Object>();source.forEach((host,e)->rows.put(host,Map.of("name",e.name(),"reason",e.reason(),"until",e.until())));byte[] bytes=JsonData.encode(rows).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Ban document too large");return bytes;}
    private CompletionStage<Void> persist(){byte[] bytes=encode(entries);tail=tail.handle((unused,error)->null).thenRunAsync(()->{try{write(bytes);}catch(IOException error){throw new CompletionException(error);}},writer);tail.whenComplete((unused,error)->{if(error!=null)failure.accept(error);});return tail;}
    private void write(byte[] bytes)throws IOException{
        Path temp=Files.createTempFile(path.getParent(),".bans-",".tmp");
        try{try(var file=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var data=ByteBuffer.wrap(bytes);while(data.hasRemaining())file.write(data);file.force(true);}
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temp);}
    }
    public CompletionStage<Void> flush(){owned();return tail;}
    /** Caller stops admissions before close. Pending disk writes finish without owner continuations. */
    @Override public void close()throws IOException{
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Ban store outside owner");if(closed)return;
        try{tail.get(30,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted flushing bans; lease retained",interrupted);}catch(TimeoutException failed){throw new IOException("Timed out flushing bans; lease retained",failed);}catch(ExecutionException failed){closed=true;try{lease.close();}finally{leaseChannel.close();}throw new IOException("Could not flush bans",failed);}
        closed=true;try{lease.close();}finally{leaseChannel.close();}
    }
    public static long parseDuration(String text){if(text==null||text.isEmpty())return 0;String token=text.strip().toLowerCase(Locale.ROOT);if(Set.of("perma","permanent","forever").contains(token))return 0;if(token.isEmpty())return -1;long multiplier=switch(token.charAt(token.length()-1)){case 's'->1;case 'm'->60;case 'h'->3600;case 'd'->86400;case 'w'->604800;default->0;};String number=multiplier==0?token:token.substring(0,token.length()-1);if(number.isEmpty()||!number.chars().allMatch(c->c>='0'&&c<='9'))return -1;try{return Math.multiplyExact(Long.parseLong(number),multiplier==0?1:multiplier);}catch(ArithmeticException|NumberFormatException ignored){return -1;}}
}
