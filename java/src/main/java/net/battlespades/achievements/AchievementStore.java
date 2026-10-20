// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.achievements;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.battlespades.match.AchievementCatalog;
import net.battlespades.world.*;

/** Worker-owned portable transactions. File locks merge increments and arbitrate unlocks across Java server processes. */
public final class AchievementStore {
    public record Delta(String identity,String stat,long amount,String playerName){public Delta{identity=key(identity);Objects.requireNonNull(stat);Objects.requireNonNull(playerName);if(!AchievementCatalog.tiers().containsKey(stat)||amount<0)throw new IllegalArgumentException("Invalid achievement delta");}}
    public record Request(String identity,String apiName,String playerName){public Request{identity=key(identity);Objects.requireNonNull(playerName);if(!AchievementCatalog.definitions().containsKey(apiName))throw new IllegalArgumentException("Unknown achievement");}}
    public record Unlock(double at,String playerName,Double reported){public Unlock{if(!Double.isFinite(at)||reported!=null&&!Double.isFinite(reported))throw new IllegalArgumentException("Invalid achievement timestamp");Objects.requireNonNull(playerName);}}
    public record Created(String identity,String apiName,String playerName){}
    public record Account(Map<String,Long> counters,Map<String,Unlock> unlocks){public Account{counters=Map.copyOf(counters);unlocks=Map.copyOf(unlocks);}}
    public record Result(Map<String,Account> accounts,List<Created> created,Map<String,List<String>> taken){public Result{accounts=Map.copyOf(accounts);created=List.copyOf(created);taken=Map.copyOf(taken);}}
    private static final java.util.concurrent.ConcurrentMap<Path,java.util.concurrent.locks.ReentrantLock> JVM_LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final class Data {final Map<String,Map<String,Long>> counters=new LinkedHashMap<>();final Map<String,Map<String,Unlock>> unlocks=new LinkedHashMap<>();}
    private final Thread owner=Thread.currentThread();private final Path path;private Data memory=new Data();
    /** Open on the IO worker before host admission. Null selects an explicitly volatile store. */
    public AchievementStore(Path path)throws IOException{this.path=path==null?null:path.toAbsolutePath().normalize();if(path!=null){Files.createDirectories(this.path.getParent());read();}}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Achievement store outside IO worker");}
    private static String key(String identity){if(identity==null||identity.isBlank()||identity.length()>512)throw new IllegalArgumentException("Invalid achievement identity");return identity;}
    private static Data copy(Data d){var result=new Data();d.counters.forEach((k,v)->result.counters.put(k,new LinkedHashMap<>(v)));d.unlocks.forEach((k,v)->result.unlocks.put(k,new LinkedHashMap<>(v)));return result;}
    @SuppressWarnings("unchecked") private Data read()throws IOException{if(path==null)return copy(memory);if(!Files.exists(path))return new Data();if(Files.size(path)>MetadataDocument.MAX_BYTES)throw new IOException("Achievement store exceeds portable document limit");try{
        Object parsed=MetadataDocument.json(Files.readString(path));if(!(parsed instanceof Map<?,?> data)||!(data.get("version") instanceof Number version)||version.intValue()!=1||!(data.get("counters") instanceof Map<?,?> counters)||!(data.get("unlocks") instanceof Map<?,?> unlocks))throw new IllegalArgumentException("Invalid portable achievement schema");var result=new Data();
        for(var entry:counters.entrySet()){String identity=key((String)entry.getKey());var values=new LinkedHashMap<String,Long>();for(var stat:((Map<String,Object>)entry.getValue()).entrySet()){if(!(stat.getValue() instanceof Number n)||n.longValue()<0)throw new IllegalArgumentException("Invalid achievement counter");values.put(stat.getKey(),n.longValue());}result.counters.put(identity,values);}
        for(var entry:unlocks.entrySet()){String identity=key((String)entry.getKey());var values=new LinkedHashMap<String,Unlock>();for(var stat:((Map<String,Map<String,Object>>)entry.getValue()).entrySet()){var row=stat.getValue();values.put(stat.getKey(),new Unlock(((Number)row.get("at")).doubleValue(),(String)row.get("name"),row.get("reported")==null?null:((Number)row.get("reported")).doubleValue()));}result.unlocks.put(identity,values);}return result;
    }catch(IllegalArgumentException|ClassCastException|NullPointerException error){throw new IOException("Invalid portable achievement store; export legacy SQLite separately",error);}}
    private static Map<String,Account> accounts(Data d){var ids=new LinkedHashSet<>(d.counters.keySet());ids.addAll(d.unlocks.keySet());var result=new LinkedHashMap<String,Account>();for(var id:ids)result.put(id,new Account(d.counters.getOrDefault(id,Map.of()),d.unlocks.getOrDefault(id,Map.of())));return result;}
    public Map<String,Account> snapshot()throws IOException{owned();return Map.copyOf(accounts(read()));}
    public Result transact(List<Delta> deltas,List<Request> requests,Set<String> take,double now)throws IOException{
        return transact(deltas,requests,take,Map.of(),now);
    }
    /** Selective handoff preserves a result's owner-time eligibility boundary. */
    public Result takeSelected(String identity,Set<String> eligible,double now)throws IOException{return transact(List.of(),List.of(),Set.of(key(identity)),Map.of(identity,Set.copyOf(eligible)),now);}
    private Result transact(List<Delta> deltas,List<Request> requests,Set<String> take,Map<String,Set<String>> selected,double now)throws IOException{
        owned();if(!Double.isFinite(now))throw new IllegalArgumentException("Nonfinite achievement clock");if(path==null){var data=copy(memory);var result=apply(data,deltas,requests,take,selected,now);memory=data;return result;}
        var local=JVM_LOCKS.computeIfAbsent(path,key->new java.util.concurrent.locks.ReentrantLock());if(!local.tryLock())throw new IOException("Achievement store is busy");
        try{try(var channel=FileChannel.open(path.resolveSibling(path.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            FileLock lease;try{lease=channel.tryLock();}catch(OverlappingFileLockException busy){throw new IOException("Achievement store is busy",busy);}if(lease==null)throw new IOException("Achievement store is busy");try(lease){var data=read();var result=apply(data,deltas,requests,take,selected,now);write(data);return result;}
        }}finally{local.unlock();}
    }
    private static Result apply(Data data,List<Delta> deltas,List<Request> requests,Set<String> take,Map<String,Set<String>> selected,double now){var created=new ArrayList<Created>();for(var delta:deltas){if(delta.amount==0)continue;var counters=data.counters.computeIfAbsent(delta.identity,k->new LinkedHashMap<>());long value=Math.addExact(counters.getOrDefault(delta.stat,0L),delta.amount);counters.put(delta.stat,value);String name=delta.playerName;for(var tier:AchievementCatalog.tiers().get(delta.stat))if(value>=tier.threshold())unlock(data,new Request(delta.identity,tier.apiName(),name),now,created);}
        for(var request:requests)unlock(data,request,now,created);var taken=new LinkedHashMap<String,List<String>>();for(var identity:take){key(identity);var unlocks=data.unlocks.getOrDefault(identity,Map.of());var names=unlocks.entrySet().stream().filter(e->e.getValue().reported==null&&(!selected.containsKey(identity)||selected.get(identity).contains(e.getKey()))).sorted(Comparator.<Map.Entry<String,Unlock>>comparingDouble(e->e.getValue().at).thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).toList();for(var name:names){var old=unlocks.get(name);unlocks.put(name,new Unlock(old.at,old.playerName,now));}taken.put(identity,names);}return new Result(accounts(data),created,taken);
    }
    private static void unlock(Data data,Request r,double now,List<Created> created){var unlocks=data.unlocks.computeIfAbsent(r.identity,k->new LinkedHashMap<>());if(!unlocks.containsKey(r.apiName)){unlocks.put(r.apiName,new Unlock(now,r.playerName,null));created.add(new Created(r.identity,r.apiName,r.playerName));}}
    private void write(Data d)throws IOException{var unlocks=new LinkedHashMap<String,Object>();d.unlocks.forEach((identity,values)->{var rows=new LinkedHashMap<String,Object>();values.forEach((name,u)->{var row=new LinkedHashMap<String,Object>();row.put("at",u.at);row.put("name",u.playerName);row.put("reported",u.reported);rows.put(name,row);});unlocks.put(identity,rows);});byte[] bytes=JsonData.encode(Map.of("version",1,"counters",d.counters,"unlocks",unlocks)).getBytes(StandardCharsets.UTF_8);if(bytes.length>MetadataDocument.MAX_BYTES)throw new IOException("Achievement store exceeds portable document limit");var temp=Files.createTempFile(path.getParent(),".achievements-",".tmp");try{try(var out=FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);}Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
}
