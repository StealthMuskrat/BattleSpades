// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.battlespades.match.ProfileStats;
import net.battlespades.results.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResultsTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static List<?> list(Object value){return (List<?>)value;}
    private static long n(Object value){return ((Number)value).longValue();}
    private static long n(Map<String,Object> values,String key){return values.containsKey(key)?n(values.get(key)):0;}
    private static final class Actor {
        final Object token=new Object();final Map<String,Object> fields;
        Actor(Map<String,Object> fields){this.fields=new LinkedHashMap<>(fields);}
        RoundResultLedger.Player snapshot(){var id=fields.get("identity");var identity=id==null?null:new RoundResultLedger.Identity((String)id,(String)fields.get("nickname"));Map<Integer,ProfileStats.Delta> profile=null;if(fields.containsKey("profile")){profile=new LinkedHashMap<>();for(var e:map(fields.get("profile")).entrySet()){var pair=list(e.getValue());profile.put(Integer.parseInt(e.getKey()),new ProfileStats.Delta(n(pair.getFirst()),n(pair.get(1))));}}return new RoundResultLedger.Player(token,identity,Boolean.TRUE.equals(fields.get("bot")),(int)n(fields,"team"),new RoundResultLedger.Counters(n(fields,"kills"),n(fields,"deaths"),n(fields,"captures"),n(fields,"score")),profile,new RoundResultLedger.Participation(n(fields,"connected"),n(fields,"active"),n(fields,"afk"),(int)n(fields,"humans"),(int)n(fields,"bots")));}
    }
    @Test void unchangedSourceReservationsMatchAcrossProfileDeltasResetDepartureAndReconnect()throws Exception{
        String text;try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/golden/result-reservations.json"))){text=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}var fixture=map(MetadataDocument.json(text));
        for(var rawCase:list(fixture.get("cases"))){var test=map(rawCase);Instant[] now={Instant.parse("2026-10-19T00:00:00Z")};var ids=new AtomicInteger();var ledger=new RoundResultLedger(()->now[0],()->String.format(Locale.ROOT,"%032x",ids.incrementAndGet()));var slots=new LinkedHashMap<Integer,Actor>();var tokens=new HashMap<String,Actor>();
            for(var operation:list(test.get("steps"))){var step=map(operation);var players=slots.values().stream().map(Actor::snapshot).toList();switch((String)step.get("op")){
                case "begin"->ledger.begin(new RoundResultLedger.Context("one:27015",(String)test.get("mode"),"8",-1,""),players);
                case "player"->{var actor=new Actor(step);slots.put((int)n(step,"slot"),actor);tokens.put((String)step.get("token"),actor);}
                case "advance"->now[0]=now[0].plusSeconds(n(step,"seconds"));
                case "update"->tokens.get((String)step.get("token")).fields.putAll(step);
                case "reset"->{for(var actor:slots.values())for(String name:List.of("kills","deaths","captures","score"))actor.fields.put(name,0L);ledger.resetScoreboard(slots.values().stream().map(Actor::snapshot).toList());}
                case "depart"->{var actor=tokens.get((String)step.get("token"));ledger.depart(actor.snapshot());slots.values().removeIf(a->a==actor);}
                case "capture"->{Integer winner=step.get("winner")==null?null:(int)n(step,"winner");var event=ledger.capture(players,winner,(Boolean)step.get("finished"),identity->List.of());assertEquals(step.get("expected"),event.stream().map(ResultOutbox.Event::payload).toList(),test.get("mode")+" "+step);}
                default->fail("Unknown native fixture operation");
            }}
        }
        for(String mode:List.of("ugc","tut","tutorial")){var ledger=new RoundResultLedger(Instant::now,()->"id");ledger.begin(new RoundResultLedger.Context("one:27015",mode,"8",1,""),List.of());assertTrue(ledger.capture(List.of(),2,true,id->List.of()).isEmpty());}
    }
    private static ResultOutbox.Event event(String id,String server){var p=new LinkedHashMap<String,Object>();p.put("event_id",id);p.put("server_id",server);p.put("players",List.of(Map.of("name","雪")));return ResultOutbox.Event.from(p);}
    @Test void portableSpoolPreservesAtomicBatchesPartitionsMirrorsConcurrentWritersAndBounds()throws Exception{
        Path directory=temp.resolve("spool"),mirror=temp.resolve("mirror");Files.createDirectories(directory);Path unrelated=directory.resolve("0".repeat(64)+".json");Files.writeString(unrelated,"unrelated operator data");var outbox=new ResultOutbox(directory,mirror,new ResultOutbox.Limits(1,1024));var a=event("a","one:27015");var b=event("b","two:27015");outbox.putMany(List.of(a,b));outbox.putMany(List.of(a));assertEquals("unrelated operator data",Files.readString(unrelated));Files.delete(mirror.resolve("a.json"));outbox.putMany(List.of(a));assertEquals(a.payload(),MetadataDocument.json(Files.readString(mirror.resolve("a.json"))));
        assertThrows(IllegalArgumentException.class,()->outbox.putMany(List.of(event("c","three:27015"),event("a","changed:27015"))));assertTrue(outbox.pending("three:27015",64).isEmpty());assertThrows(java.io.IOException.class,()->outbox.putMany(List.of(event("d","one:27015"))));assertEquals(List.of(a),new ResultOutbox(directory,mirror).pending("one:27015",64));
        var large=ResultOutbox.Event.from(Map.of("event_id","large","server_id","three:27015","padding","x".repeat(2048)));assertThrows(IllegalArgumentException.class,()->outbox.putMany(List.of(large)));assertTrue(outbox.pending("three:27015",64).isEmpty());outbox.acknowledge("a");assertFalse(Files.exists(mirror.resolve("a.json")));assertEquals(List.of(b),outbox.pending("two:27015",64));
        Path blocked=temp.resolve("blocked");Files.writeString(blocked,"operator-owned file");var repair=new ResultOutbox(temp.resolve("repair"),blocked);assertThrows(java.io.IOException.class,()->repair.putMany(List.of(a)));assertEquals(List.of(a),repair.pending("one:27015",64));Files.delete(blocked);repair.putMany(List.of(a));assertTrue(Files.isRegularFile(blocked.resolve("a.json")));
        String manifest=Files.readString(directory.resolve("index.json"));Files.writeString(directory.resolve("index.json"),"{broken");assertThrows(java.io.IOException.class,()->outbox.putMany(List.of(event("safe","safe:27015"))));assertEquals("{broken",Files.readString(directory.resolve("index.json")));Files.writeString(directory.resolve("index.json"),manifest);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.json().getBytes(java.nio.charset.StandardCharsets.UTF_8)));Files.writeString(directory.resolve(hash+".json"),"{}");assertThrows(java.io.IOException.class,()->outbox.pending("two:27015",64));Files.writeString(directory.resolve(hash+".json"),b.json());assertEquals(List.of(b),outbox.pending("two:27015",64));
        Path shared=temp.resolve("fleet");try(var workers=Executors.newFixedThreadPool(2)){var barrier=new CyclicBarrier(2);var tasks=new ArrayList<Future<?>>();for(int i=0;i<2;i++){final int number=i;tasks.add(workers.submit(()->{barrier.await();new ResultOutbox(shared,null).putMany(List.of(event("fleet"+number,"same:27015")));return null;}));}for(var task:tasks)task.get(4,TimeUnit.SECONDS);}assertEquals(2,new ResultOutbox(shared,null).pending("same:27015",64).size());
    }
    private static void pump(OwnerLoop owner,BooleanSupplier done){long deadline=System.nanoTime()+4_000_000_000L;while(!done.getAsBoolean()){assertTrue(System.nanoTime()<deadline,"Result owner continuation timed out");owner.pump(System.nanoTime());Thread.onSpinWait();}}
    @Test void publisherReservationsSurviveCancellationUnacknowledgedResponsesShutdownAndMirrorFailure()throws Exception{
        var outbox=new ResultOutbox(temp.resolve("spool"),null);var failures=new ArrayList<Throwable>();var sent=new ArrayList<ResultOutbox.Event>();var response=new CompletableFuture<Boolean>();var first=event("first","one:27015");var second=event("second","one:27015");
        try(var worker=Executors.newSingleThreadExecutor();var owner=new OwnerLoop(0,1024,256,error->fail(error))){
            var publisher=new ResultPublisher(outbox,"one:27015",worker,owner,e->{sent.add(e);return response;},failures::add);publisher.offer(first);var flush=publisher.flush().toCompletableFuture();pump(owner,()->!sent.isEmpty());assertEquals(0,publisher.pendingMemory());assertEquals(List.of(first),outbox.pending("one:27015",64));var cancelled=publisher.flush().toCompletableFuture();assertTrue(cancelled.cancel(true));assertFalse(flush.isCancelled());publisher.offer(second);response.complete(false);pump(owner,flush::isDone);assertThrows(CompletionException.class,flush::join);assertEquals(1,publisher.pendingMemory());var stopped=publisher.shutdown().toCompletableFuture();pump(owner,stopped::isDone);stopped.join();assertEquals(List.of(first,second),outbox.pending("one:27015",64));
            var replay=new ArrayList<ResultOutbox.Event>();var restarted=new ResultPublisher(new ResultOutbox(temp.resolve("spool"),null),"one:27015",worker,owner,e->{replay.add(e);return CompletableFuture.completedFuture(true);},failures::add);var delivered=restarted.flush().toCompletableFuture();pump(owner,delivered::isDone);delivered.join();assertEquals(List.of(first,second),replay);assertTrue(outbox.pending("one:27015",64).isEmpty());var closed=restarted.shutdown().toCompletableFuture();pump(owner,closed::isDone);closed.join();
            Path mirror=temp.resolve("bad-mirror");Files.writeString(mirror,"blocked");var mirrored=new ResultOutbox(temp.resolve("mirrored"),mirror);var attempts=new AtomicInteger();var recovery=new ResultPublisher(mirrored,"one:27015",worker,owner,e->{attempts.incrementAndGet();return CompletableFuture.completedFuture(true);},failures::add);recovery.offer(first);var broken=recovery.flush().toCompletableFuture();pump(owner,broken::isDone);assertThrows(CompletionException.class,broken::join);assertEquals(0,attempts.get());assertEquals(1,recovery.pendingMemory());Files.delete(mirror);var retry=recovery.flush().toCompletableFuture();pump(owner,retry::isDone);retry.join();assertEquals(1,attempts.get());assertTrue(mirrored.pending("one:27015",64).isEmpty());var finalClose=recovery.shutdown().toCompletableFuture();pump(owner,finalClose::isDone);finalClose.join();assertTrue(failures.isEmpty());
        }
    }
}
