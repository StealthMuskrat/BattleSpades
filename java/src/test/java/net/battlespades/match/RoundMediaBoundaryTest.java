// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundMediaBoundaryTest {
    private static MapBallot<Integer> ballot(OwnerLoop loop,List<GamePacket> wire){var players=Map.of(0,0,1,1);return new MapBallot<>(List.of("First","Other"),()->players,i->i,i->true,()->loop.nowNanos()/1e9,loop::sleep,wire::add,loop);}
    @Test void independentWaitCancellationRemovesOnlyItsTimerAndTimeoutResolvesWithoutATick(){
        var wire=new ArrayList<GamePacket>();try(var loop=new OwnerLoop(0,16,16,error->fail(error))){var ballot=ballot(loop,wire);ballot.start(List.of("First","Other"),1000);var a=ballot.waitForResult().toCompletableFuture();var b=ballot.waitForResult().toCompletableFuture();assertEquals(2,loop.pending());a.cancel(false);assertEquals(1,loop.pending());assertTrue(ballot.active());loop.pump(9_999_999_999L);assertFalse(b.isDone());loop.pump(10_000_000_000L);assertEquals("First",b.join());assertEquals(0,loop.pending());assertTrue(a.isCancelled());assertFalse(ballot.active());assertEquals(1,wire.stream().filter(p->p instanceof VotePacket vote&&vote.messageType()==3).count());}
    }
    @Test void completionThatStartsAReplacementCannotLetTheOldBallotTimeoutCloseIt(){
        try(var loop=new OwnerLoop(0,16,16,error->fail(error))){var ballot=ballot(loop,new ArrayList<>());ballot.start(List.of("First"),1000);var wait=ballot.waitForResult().toCompletableFuture();wait.thenRun(()->assertTrue(ballot.start(List.of("Other"),1001)));ballot.cast(0,0);ballot.cast(1,0);assertEquals("First",wait.join());assertTrue(ballot.active());assertEquals(List.of("Other"),ballot.candidates());assertEquals(0,loop.pending());loop.pump(20_000_000_000L);assertTrue(ballot.active());ballot.tick(null);assertEquals("Other",ballot.consume());}
    }
    @Test void workerTimerCompletionCannotMutateTheBallotBeforeTheOwnerPumps()throws Exception{
        var timer=new CompletableFuture<Void>();try(var loop=new OwnerLoop(0,16,16,error->fail(error))){var ballot=new MapBallot<>(List.of("First"),()->Map.of(0,0),i->i,i->true,()->0.0,seconds->timer,p->{},loop);ballot.start(List.of("First"),0);var wait=ballot.waitForResult().toCompletableFuture();var worker=new Thread(()->timer.complete(null));worker.start();worker.join();assertFalse(wait.isDone());assertTrue(ballot.active());loop.pump(0);assertEquals("First",wait.join());assertFalse(ballot.active());}
    }
    private static final class EndPorts implements RoundPresentation.Bindings {
        int restarts,cancellations;
        public boolean scoreboard(){return true;}public void botsEnd(){}public void statistics(Integer winner){}public double screenSeconds(){return 0;}public Integer headline(Integer winner){return null;}public RoundEndSequence.Transition transition(){return null;}public CompletionStage<Void> restartInPlace(){restarts++;return CompletableFuture.completedFuture(null);}public CompletionStage<Void> cancelPending(){cancellations++;return CompletableFuture.completedFuture(null);}public boolean recoverable(){return true;}public void failed(){fail();}public void failure(String label,Throwable error){fail(error);}
    }
    @Test void endingCancellationForwardsThroughPresentationToItsBallotWaitWithoutResolvingTheBallot(){
        var wire=new ArrayList<GamePacket>();try(var loop=new OwnerLoop(0,16,16,error->fail(error))){var ballot=ballot(loop,wire);ballot.start(List.of("Other"),0);var ports=new EndPorts();var music=new MusicService(wire::add,new SourceRandom(0));var ending=new RoundEndSequence(new RoundPresentation(music,ballot,loop,()->"First",wire::add,ports),loop);var old=ending.run(2).toCompletableFuture();loop.pump(5_000_000_000L);assertEquals(1,loop.pending());ending.cancel().toCompletableFuture().join();assertEquals(0,loop.pending());assertTrue(ballot.active());assertEquals(1,ports.cancellations);assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,old::join).getCause());ballot.cancel();ballot.start(List.of("First"),5);loop.pump(10_000_000_000L);assertTrue(ballot.active());assertEquals(0,ports.restarts);assertFalse(ending.running());ballot.cancel();}
    }
    @Test void failedMusicDeliveryDoesNotClaimANewFamilyAndOwnerGuardsRejectWorkerMutation()throws Exception{
        var wire=new ArrayList<GamePacket>();boolean[] fail={false};var music=new MusicService(packet->{if(fail[0]&&packet.id()==26)throw new IllegalStateException("Transport refusal");wire.add(packet);},new SourceRandom(0));music.gameplay(true);assertEquals("last_man_standing",music.family());fail[0]=true;assertThrows(IllegalStateException.class,music::ending);assertEquals("last_man_standing",music.family());fail[0]=false;assertTrue(music.ending());assertFalse(music.ending());assertEquals("game_ending",music.family());
        try(var worker=Executors.newSingleThreadExecutor()){assertInstanceOf(IllegalStateException.class,worker.submit(()->{try{music.stop();return null;}catch(RuntimeException e){return e;}}).get());}
    }
}
