// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.GameplaySounds;
import net.battlespades.protocol.*;
import net.battlespades.simulation.PeriodicServices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplayServicesBoundaryTest {
    @Test void suspendedBotRetirementResumesOnOwnerBeforeOtherServicesAndSameFrameRespawn()throws Exception{
        try(var h=new BalanceHarness()){
            var bot=h.h.runtime.player(0);bot.bot(true);bot.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,97),100,1,0x123456);h.bots.add(bot);h.config.put("balance_bot_wait_seconds",0);h.removal=new CompletableFuture<>();h.addition=new CompletableFuture<>();var order=new ArrayList<String>();
            var periodic=new PeriodicServices(h.balance::tick,now->order.add("skill"),now->order.add("report"),(label,error)->fail(label,error),h.loop);
            var frame=periodic.respawns(60,60,100,()->{order.add("respawns");h.h.wall+=2;h.h.runtime.processRespawns();return CompletableFuture.completedFuture(null);}).toCompletableFuture();assertFalse(frame.isDone());assertTrue(order.isEmpty());assertThrows(IllegalStateException.class,()->h.balance.tick(101));assertThrows(IllegalStateException.class,()->periodic.respawns(61,60,101,()->CompletableFuture.completedFuture(null)));
            h.detach(bot);Thread.ofPlatform().start(()->h.removal.complete(true)).join();assertFalse(frame.isDone());assertTrue(order.isEmpty());h.loop.pump(100_000_000_000L);assertEquals(List.of(List.of("remove",0L),List.of("add",3L)),h.log);assertFalse(frame.isDone());Thread.ofPlatform().start(()->h.addition.completeExceptionally(new IllegalStateException("replacement"))).join();h.loop.pump(100_000_000_000L);frame.join();assertEquals(List.of("replacement bot"),h.failures);assertEquals(List.of("skill","report","respawns"),order);assertEquals(1,h.balance.botMoves());assertTrue(h.h.runtime.player(3).alive());assertEquals(2,h.h.runtime.player(3).team(),"Retirement stands, no human moved");
            order.clear();periodic.respawns(61,60,101,()->{order.add("respawns");return CompletableFuture.completedFuture(null);}).toCompletableFuture().join();assertEquals(List.of("respawns"),order);
        }
    }
    @Test void ordinaryServiceFailuresDoNotSkipRespawnsButCancellationAbortsTheFrame()throws Exception{
        try(var h=new BalanceHarness()){
            var order=new ArrayList<String>();var failures=new ArrayList<String>();var balance=new CompletableFuture<Boolean>();var periodic=new PeriodicServices(now->balance,now->{order.add("skill");throw new IllegalArgumentException("skill");},now->{order.add("report");throw new IllegalStateException("report");},(name,error)->failures.add(name),h.loop);
            var frame=periodic.respawns(0,0,100,()->{order.add("respawns");return CompletableFuture.completedFuture(null);}).toCompletableFuture();Thread.ofPlatform().start(()->balance.completeExceptionally(new IllegalStateException("balance"))).join();assertFalse(frame.isDone());h.loop.pump(100_000_000_000L);frame.join();assertEquals(List.of("team balance","bot skill balance","anticheat report"),failures);assertEquals(List.of("skill","report","respawns"),order);
            var cancel=new CompletableFuture<Boolean>();var aborted=new PeriodicServices(now->cancel,now->fail(),now->fail(),(name,error)->fail(),h.loop);var result=aborted.respawns(60,60,101,()->{fail();return CompletableFuture.completedFuture(null);}).toCompletableFuture();cancel.cancel(false);assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,result::join).getCause());
            try(var worker=Executors.newSingleThreadExecutor()){assertInstanceOf(IllegalStateException.class,assertThrows(ExecutionException.class,()->worker.submit(()->h.balance.force()).get(4,TimeUnit.SECONDS)).getCause());}
        }
    }
    @Test void botCancellationAndFatalReplacementRemainFailuresWhileBrokenVetoesAreSafe(){
        try(var h=new BalanceHarness()){
            h.allowFailure=true;assertFalse(h.balance.tick(100).toCompletableFuture().join());h.allowFailure=false;h.transitionFailure=true;assertEquals(0,h.balance.force().toCompletableFuture().join());h.transitionFailure=false;var bot=h.h.runtime.player(0);bot.bot(true);bot.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,97),100,1,0x123456);h.bots.add(bot);h.config.put("balance_bot_wait_seconds",0);h.addition=new CompletableFuture<>();var result=h.balance.tick(101).toCompletableFuture();h.addition.completeExceptionally(new AssertionError("fatal"));assertInstanceOf(AssertionError.class,assertThrows(CompletionException.class,result::join).getCause());assertEquals(0,h.balance.botMoves());assertTrue(h.failures.isEmpty());
        }
    }
    @Test void audioTransportFailurePreventsHearingAndTeamSendSnapshotsRoster(){
        var players=new ArrayList<Integer>(List.of(0,1));var sent=new ArrayList<Integer>();boolean[] failBroadcast={true};var sounds=new GameplaySounds<>(new GameplaySounds.Ports<Integer>(){
            public List<Integer> players(){return players;}public int id(Integer p){return p;}public int team(Integer p){return 2;}public boolean connected(Integer p){return true;}public boolean settled(Integer p){return true;}public void broadcast(GamePacket packet,Integer exclude,boolean reliable){if(failBroadcast[0])throw new IllegalStateException("transport");sent.add(10);}public void send(Integer p,GamePacket packet,boolean reliable){sent.add(p);players.clear();}public boolean hearingEnabled(){return true;}public void blockDestroyed(GameplaySounds.Position p,int id,int team,double life){sent.add(11);throw new IllegalArgumentException("hearing");}
        });var cue=new GameplaySounds.Cue(33,1,new GameplaySounds.Position(1,2,3),1);assertThrows(IllegalStateException.class,()->sounds.play(cue,null,false,0));assertTrue(sent.isEmpty());failBroadcast[0]=false;assertThrows(IllegalArgumentException.class,()->sounds.play(cue,null,false,0));assertEquals(List.of(10,11),sent);sent.clear();assertEquals(2,sounds.team(2,cue,null));assertEquals(List.of(0,1),sent);assertThrows(IllegalArgumentException.class,()->sounds.stop(256));assertThrows(IllegalArgumentException.class,()->sounds.stop(-1));
    }
}
