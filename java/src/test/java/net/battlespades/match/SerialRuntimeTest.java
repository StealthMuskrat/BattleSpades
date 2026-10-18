// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.runtime.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SerialRuntimeTest {
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    @Test void packetAwaitReleasesSharesDefersNewArrivalsAndRechecksDetachedTailIdentity()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Void>();var seen=new ArrayList<Integer>();boolean[] current={true};class EqualPeer {@Override public boolean equals(Object other){return other instanceof EqualPeer;}@Override public int hashCode(){return 1;}}var connection=new EqualPeer();var other=new EqualPeer();
        var queue=new IngamePackets<Object>(new IngamePackets.Settings(4,2,3),c->c!=connection||current[0],(c,data)->{int value=data[0]&255;seen.add(value);return value==1?gate:done();},failure->fail(failure),owner::add);
        byte[] first={1};assertTrue(queue.offer(connection,first));first[0]=99;assertTrue(queue.offer(connection,new byte[]{2}));assertFalse(queue.offer(connection,new byte[]{9}));assertTrue(queue.offer(other,new byte[]{3}));
        var frame=queue.drain().toCompletableFuture();assertEquals(List.of(1),seen);assertFalse(frame.isDone());assertEquals(0,queue.share(connection));assertTrue(queue.offer(other,new byte[]{4}));assertThrows(IllegalStateException.class,queue::drain);current[0]=false;
        var worker=new Thread(()->gate.complete(null));worker.start();worker.join();assertEquals(List.of(1),seen);assertFalse(frame.isDone());for(Runnable r;(r=owner.poll())!=null;)r.run();frame.join();
        assertEquals(List.of(1,3),seen);assertEquals(1,queue.size());queue.drain().toCompletableFuture().join();assertEquals(List.of(1,3,4),seen);assertEquals(1,queue.dropped());
    }
    @Test void modeHandlerAndPluginAreBothAwaitedBeforeReplacementModeReceivesTheNextEvent()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var handler=new CompletableFuture<Void>();var plugin=new CompletableFuture<Void>();var log=new ArrayList<String>();ModeEvents.Mode[] active={null};
        class Probe implements ModeEvents.Mode {
            final String name;final RoundClock clock=new RoundClock(new ClockPorts());Probe(String name){this.name=name;}
            public RoundClock clock(){return clock;}public CompletionStage<Void> handle(ModeEvents.Event e){log.add(name+":"+e.name());return name.equals("A")?handler:done();}public CompletionStage<Void> tick(int loop){log.add(name+":tick");return done();}
        }
        var a=new Probe("A");var b=new Probe("B");a.clock.timeoutRemainder(2);b.clock.timeoutRemainder(77);active[0]=a;
        var queue=new ModeEvents(8,2,()->active[0],e->{log.add("plugin:"+e.name());return e.name().equals("first")?plugin:done();},(n,e)->fail(e),owner::add);
        queue.offer(ModeEvents.Event.of("first"));queue.offer(ModeEvents.Event.of("second"));var frame=queue.tick(42).toCompletableFuture();queue.offer(ModeEvents.Event.of("late"));active[0]=b;
        var worker=new Thread(()->handler.complete(null));worker.start();worker.join();assertEquals(List.of("A:first"),log);for(Runnable r;(r=owner.poll())!=null;)r.run();assertEquals(List.of("A:first","plugin:first"),log);assertFalse(frame.isDone());
        plugin.complete(null);frame.join();assertEquals(List.of("A:first","plugin:first","B:second","plugin:second","B:tick"),log);assertEquals(0,b.clock.timeoutRemaining());assertEquals(1,queue.size());assertEquals("late",queue.snapshot().getFirst().name());
    }
    @Test void cancellationAbortsTheFrameWithoutSwallowingItAndReleasesTheDrainGuard(){
        var gate=new CompletableFuture<Void>();var queue=new IngamePackets<Object>(new IngamePackets.Settings(4,4,4),c->true,(c,b)->gate,e->fail("Cancellation must not be reported as an ordinary packet fault"),Runnable::run);queue.offer(new Object(),new byte[]{1});var frame=queue.drain().toCompletableFuture();gate.cancel(false);var error=assertThrows(CompletionException.class,frame::join);assertInstanceOf(CancellationException.class,error.getCause());assertFalse(queue.draining());queue.drain().toCompletableFuture().join();
    }
    @Test void aFullImmediateModeBudgetUsesConstantStackAndOwnerMethodsRejectWorkerAccess()throws Exception {
        int[] calls={0};var clock=new RoundClock(new ClockPorts());var mode=new ModeEvents.Mode(){public RoundClock clock(){return clock;}public CompletionStage<Void> handle(ModeEvents.Event e){calls[0]++;return done();}public CompletionStage<Void> tick(int loop){return done();}};
        var queue=new ModeEvents(8192,8192,()->mode,e->{calls[0]++;return done();},(n,e)->fail(e),Runnable::run);for(int i=0;i<8192;i++)assertTrue(queue.offer(ModeEvents.Event.of("item",i)));queue.tick(1).toCompletableFuture().join();assertEquals(16384,calls[0]);assertEquals(0,queue.size());
        try(var worker=Executors.newSingleThreadExecutor()){assertInstanceOf(IllegalStateException.class,worker.submit(()->{try{queue.offer(ModeEvents.Event.of("foreign"));return null;}catch(RuntimeException e){return e;}}).get());}assertEquals(0,queue.size());
    }
    private static class ClockPorts implements RoundClock.Effects {
        public void escapeWatch(){}public void escapeFailure(RuntimeException e){throw e;}public void timeoutMusic(){}public void announce(FlowCatalog.Cue c){}public void ensureVote(double wall){}public CompletionStage<Void> endByTime(){return done();}
    }
}
