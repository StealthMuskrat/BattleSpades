// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.results;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import net.battlespades.world.*;

/** Blocking, portable fleet-partitioned spool. Invoke on an IO worker. The forced index replacement commits each batch. */
public final class ResultOutbox {
    public static final int MAX_EVENTS=4096,MAX_EVENT_BYTES=256*1024,MAX_INDEX_BYTES=8*1024*1024;
    public record Limits(int events,int eventBytes){public Limits{if(events<1||events>MAX_EVENTS||eventBytes<128||eventBytes>MAX_EVENT_BYTES)throw new IllegalArgumentException("Result limits");}public static Limits defaults(){return new Limits(MAX_EVENTS,MAX_EVENT_BYTES);}}
    public record Event(String eventId,String serverId,String json){
        public Event{identifier(eventId);server(serverId);Objects.requireNonNull(json);if(json.getBytes(StandardCharsets.UTF_8).length>MAX_EVENT_BYTES)throw new IllegalArgumentException("Round result exceeds event size limit");Object value=MetadataDocument.json(json);if(!(value instanceof Map<?,?> row)||!eventId.equals(row.get("event_id"))||!serverId.equals(row.get("server_id")))throw new IllegalArgumentException("Result envelope identity mismatch");}
        public static Event from(Map<String,?> payload){if(!(payload.get("event_id")instanceof String id)||!(payload.get("server_id")instanceof String server))throw new IllegalArgumentException("Missing result identity");return new Event(id,server,JsonData.encodeAscii(payload).stripTrailing());}
        @SuppressWarnings("unchecked") public Map<String,Object> payload(){return (Map<String,Object>)MetadataDocument.json(json);}
    }
    private record Row(String id,String server,String hash){}
    private final Path directory,mirror;private final Limits limits;
    public ResultOutbox(Path directory,Path mirror){this(directory,mirror,Limits.defaults());}
    public ResultOutbox(Path directory,Path mirror,Limits limits){this.directory=directory.toAbsolutePath().normalize();this.mirror=mirror==null?null:mirror.toAbsolutePath().normalize();this.limits=Objects.requireNonNull(limits);}
    public static void identifier(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("Invalid result identifier");}
    public static void server(String id){if(id==null||!id.matches("[A-Za-z0-9._:-]{1,128}"))throw new IllegalArgumentException("Invalid result server identifier");}
    private Path index(){return directory.resolve("index.json");}
    private Path data(String hash){if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid result digest");return directory.resolve(hash+".json");}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
    @FunctionalInterface private interface Operation<T>{T run()throws IOException;}
    private <T>T locked(Operation<T> operation)throws IOException{
        Files.createDirectories(directory);if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(directory.resolve("outbox.lock")))throw new IOException("Result spool cannot be a symbolic link");
        try(var channel=FileChannel.open(directory.resolve("outbox.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            long deadline=System.nanoTime()+5_000_000_000L;FileLock acquired=null;
            while(acquired==null){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Result lock interrupted");try{acquired=channel.tryLock();}catch(OverlappingFileLockException busy){/* Another worker in this JVM owns the fleet transaction. */}if(acquired==null){if(System.nanoTime()>=deadline)throw new IOException("Result spool transaction timed out");LockSupport.parkNanos(1_000_000);}}
            try(var lease=acquired){if(!lease.isValid())throw new IOException("Invalid result lease");return operation.run();}
        }
    }
    private LinkedHashMap<String,Row> readIndex()throws IOException{
        var result=new LinkedHashMap<String,Row>();if(!Files.exists(index(),LinkOption.NOFOLLOW_LINKS))return result;
        try{if(Files.isSymbolicLink(index())||Files.size(index())>MAX_INDEX_BYTES)throw new IllegalArgumentException("Invalid result index size");Object value=MetadataDocument.json(Files.readString(index()));if(!(value instanceof Map<?,?> doc)||!(doc.get("version")instanceof Number version)||version.doubleValue()!=1||!(doc.get("events")instanceof List<?> rows))throw new IllegalArgumentException("Invalid result index");
            for(var item:rows){if(!(item instanceof Map<?,?> row)||!(row.get("id")instanceof String id)||!(row.get("server")instanceof String server)||!(row.get("sha256")instanceof String hash))throw new IllegalArgumentException("Invalid result index row");identifier(id);server(server);data(hash);if(result.putIfAbsent(id,new Row(id,server,hash))!=null)throw new IllegalArgumentException("Duplicate result index identity");}}
        catch(IllegalArgumentException error){throw new IOException("Malformed result index; retained without replacement",error);}return result;
    }
    private static byte[] encodedIndex(Map<String,Row> index){var rows=new ArrayList<Map<String,Object>>();for(var r:index.values()){var row=new LinkedHashMap<String,Object>();row.put("id",r.id);row.put("server",r.server);row.put("sha256",r.hash);rows.add(row);}var doc=new LinkedHashMap<String,Object>();doc.put("version",1);doc.put("events",rows);byte[] bytes=JsonData.encodeAscii(doc).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_INDEX_BYTES)throw new IllegalStateException("Result fleet index is full");return bytes;}
    private Event readEvent(Row row)throws IOException{
        Path path=data(row.hash);if(Files.isSymbolicLink(path)||Files.size(path)>limits.eventBytes())throw new IOException("Invalid stored result size");String json=Files.readString(path);if(!digest(json).equals(row.hash))throw new IOException("Stored result checksum mismatch");try{return new Event(row.id,row.server,json);}catch(IllegalArgumentException error){throw new IOException("Invalid stored result",error);}
    }
    private static void atomic(Path path,byte[] bytes)throws IOException{
        Files.createDirectories(path.getParent());if(Files.isSymbolicLink(path))throw new IOException("Result destination cannot be a symbolic link");Path temporary=Files.createTempFile(path.getParent(),".result-",".tmp");try{try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())file.write(buffer);file.force(true);}try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException error){throw new IOException("Result spool requires atomic file replacement",error);}}finally{Files.deleteIfExists(temporary);}
    }
    private void mirror(Event event)throws IOException{if(mirror!=null)atomic(mirror.resolve(event.eventId()+".json"),event.json().getBytes(StandardCharsets.UTF_8));}
    public void putMany(List<Event> events)throws IOException{
        if(events.isEmpty())return;var immutable=List.copyOf(events);locked(()->{var index=readIndex();var counts=new HashMap<String,Integer>();for(var row:index.values())counts.merge(row.server,1,Integer::sum);
            var writes=new LinkedHashMap<String,Event>();for(var event:immutable){if(event.json().getBytes(StandardCharsets.UTF_8).length>limits.eventBytes())throw new IllegalArgumentException("Round result exceeds event size limit");var old=index.get(event.eventId());if(old!=null){if(!readOrNew(old,writes).json().equals(event.json()))throw new IllegalArgumentException("Round result ID reused with a different payload");continue;}int count=counts.merge(event.serverId(),1,Integer::sum);if(count>limits.events())throw new IOException("Round result outbox is full; restore master connectivity");var row=new Row(event.eventId(),event.serverId(),digest(event.json()));index.put(row.id,row);writes.put(row.id,event);}
            byte[] manifest=encodedIndex(index);for(var event:writes.values())atomic(data(digest(event.json())),event.json().getBytes(StandardCharsets.UTF_8));atomic(index(),manifest);
            // A retry also repairs mirrors after a previous commit followed by an interrupted mirror write.
            for(var event:immutable)mirror(event);return null;});
    }
    private Event readOrNew(Row row,Map<String,Event> writes)throws IOException{return writes.containsKey(row.id)?writes.get(row.id):readEvent(row);}
    public List<Event> pending(String serverId,int limit)throws IOException{server(serverId);if(limit<1||limit>MAX_EVENTS)throw new IllegalArgumentException("Result read limit");return locked(()->{var result=new ArrayList<Event>();for(var row:readIndex().values())if(row.server.equals(serverId)){result.add(readEvent(row));if(result.size()==limit)break;}return List.copyOf(result);});}
    public void acknowledge(String eventId)throws IOException{identifier(eventId);locked(()->{var index=readIndex();var removed=index.remove(eventId);if(removed!=null){atomic(index(),encodedIndex(index));Files.deleteIfExists(data(removed.hash));}if(mirror!=null)Files.deleteIfExists(mirror.resolve(eventId+".json"));return null;});}
    /** Idempotent NDJSON import from the read-only SQLite exporter. Each 64-row batch is atomic. */
    public int importJsonLines(Path input)throws IOException{int count=0;var batch=new ArrayList<Event>();try(var reader=Files.newBufferedReader(input,StandardCharsets.UTF_8)){var line=new StringBuilder();for(int value;(value=reader.read())!=-1;){if(value=='\n'){if(!line.isEmpty()){batch.add(parseImport(line.toString()));count++;line.setLength(0);}if(batch.size()==64){putMany(batch);batch.clear();}}else{if(line.length()>=MAX_EVENT_BYTES)throw new IOException("Imported result exceeds line size limit");line.append((char)value);}}if(!line.isEmpty()){batch.add(parseImport(line.toString()));count++;}putMany(batch);}return count;}
    private static Event parseImport(String text)throws IOException{try{Object value=MetadataDocument.json(text);if(!(value instanceof Map<?,?> row)||!(row.get("event_id")instanceof String id)||!(row.get("server_id")instanceof String server))throw new IllegalArgumentException("Invalid result envelope");return new Event(id,server,text);}catch(IllegalArgumentException error){throw new IOException("Invalid imported result",error);}}
}
