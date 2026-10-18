// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.protocol.SimplePackets;
import net.battlespades.simulation.*;
import net.battlespades.support.TdmHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundOrchestrationTest {
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private static void drain(Queue<Runnable> queue){for(Runnable work;(work=queue.poll())!=null;)work.run();}
    private static class FramePorts implements SimulationFrame.Services {
        final List<String> log=new ArrayList<>();final List<SimulationFrame.Phase> measured=new ArrayList<>();
        public boolean enabled(SimulationFrame.Phase phase){return true;}
        public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int loop){log.add(phase+":"+loop);return done();}
        public void synchronous(SimulationFrame.Phase phase,int loop){log.add(phase+":"+loop);}
        public void subsystem(SimulationFrame.Phase phase,double milliseconds){assertTrue(milliseconds>=0);measured.add(phase);}
        public void failure(String name,Throwable error){log.add("failure:"+name);}
        public void tickMetrics(double milliseconds){log.add("metrics");}public void health(double milliseconds){log.add("health");}
    }
    @Test void frameAwaitsOnOwnerPreservesProjectileBeforePlayerOrderAndIsolatesFaults()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Void>();Thread thread=Thread.currentThread();boolean[] bots={true};
        var ports=new FramePorts(){
            @Override public boolean enabled(SimulationFrame.Phase phase){return phase==SimulationFrame.Phase.BOTS?bots[0]:phase!=SimulationFrame.Phase.GOO;}
            @Override public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int loop){assertSame(thread,Thread.currentThread());super.asynchronous(phase,loop);return phase==SimulationFrame.Phase.BOTS?gate:done();}
            @Override public void synchronous(SimulationFrame.Phase phase,int loop){assertSame(thread,Thread.currentThread());super.synchronous(phase,loop);if(phase==SimulationFrame.Phase.PROJECTILES)throw new IllegalStateException("projectile fault");}
            @Override public void tickMetrics(double milliseconds){super.tickMetrics(milliseconds);throw new IllegalStateException("metric fault");}
        };
        var frame=new SimulationFrame(ports,System::nanoTime,owner::add);var result=frame.step(77).toCompletableFuture();assertEquals(List.of("PACKET_DRAIN:77","BOTS:77"),ports.log);assertThrows(IllegalStateException.class,()->frame.step(78));bots[0]=false;
        var worker=new Thread(()->gate.complete(null));worker.start();worker.join();assertFalse(result.isDone());assertEquals(2,ports.log.size());drain(owner);result.join();
        var expected=new ArrayList<String>();for(var phase:SimulationFrame.Phase.values())if(phase!=SimulationFrame.Phase.GOO){expected.add(phase+":77");if(phase==SimulationFrame.Phase.PROJECTILES)expected.add("failure:projectiles");}expected.addAll(List.of("metrics","failure:metrics","health"));assertEquals(expected,ports.log);
        assertEquals(16,ports.measured.size());assertTrue(ports.log.indexOf("PROJECTILES:77")<ports.log.indexOf("PLAYERS:77"));
    }
    @Test void cancelledFrameSkipsRemainingSubsystemsAndCanBeEnteredAgain(){
        var gate=new CompletableFuture<Void>();int[] calls={0};var ports=new FramePorts(){@Override public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int loop){super.asynchronous(phase,loop);return calls[0]++==0?gate:done();}};
        var frame=new SimulationFrame(ports,System::nanoTime,Runnable::run);var result=frame.step(1).toCompletableFuture();gate.cancel(false);assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,result::join).getCause());assertEquals(List.of("PACKET_DRAIN:1"),ports.log);assertEquals(List.of(SimulationFrame.Phase.PACKET_DRAIN),ports.measured);frame.step(2).toCompletableFuture().join();assertTrue(ports.log.contains("health"));
    }
    @Test void catchUpRetainsHitchDebtAwaitsEachFrameAndYieldsBetweenReplication()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Void>();var yields=new ArrayDeque<CompletableFuture<Void>>();var log=new ArrayList<String>();
        var runtime=new AsyncFixedStepRuntime(()->true,tick->{log.add("step:"+tick);if(tick==1)return gate;if(tick==3)throw new IllegalStateException("bad frame");return done();},()->{log.add("replicate");if(log.contains("step:3")&&!log.contains("step:4"))throw new IllegalStateException("bad snapshot");},()->{log.add("yield");var yielded=new CompletableFuture<Void>();yields.add(yielded);return yielded;},(name,error)->log.add("failure:"+name),owner::add);
        var batch=runtime.advance(10).toCompletableFuture();assertEquals(1,runtime.ticks());assertEquals(List.of("step:1"),log);assertThrows(IllegalStateException.class,()->runtime.advance(0));var worker=new Thread(()->gate.complete(null));worker.start();worker.join();assertEquals(1,log.size());drain(owner);
        for(int i=0;i<5;i++){assertFalse(batch.isDone());assertEquals(i+1,runtime.ticks());yields.remove().complete(null);}assertEquals(5,batch.join());assertEquals(2-5*FixedStepRuntime.DELTA,runtime.debt(),1e-12);assertEquals(5,log.stream().filter("replicate"::equals).count());assertEquals(5,log.stream().filter("yield"::equals).count());assertTrue(log.contains("failure:step"));assertTrue(log.contains("failure:world_updates"));
    }
    @Test void stopAndCancellationDoNotRunAnotherCatchUpFrame(){
        boolean[] running={true};var runtime=new AsyncFixedStepRuntime(()->running[0],tick->{running[0]=false;return done();},()->{},RoundOrchestrationTest::done,(name,error)->fail(error),Runnable::run);assertEquals(1,runtime.advance(1).toCompletableFuture().join());assertEquals(1,runtime.ticks());
        var gate=new CompletableFuture<Void>();var cancelled=new AsyncFixedStepRuntime(()->true,tick->gate,()->fail("Replication after cancellation"),RoundOrchestrationTest::done,(name,error)->fail("Cancellation isolated"),Runnable::run);var batch=cancelled.advance(1).toCompletableFuture();gate.cancel(false);assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,batch::join).getCause());assertEquals(1,cancelled.ticks());assertThrows(IllegalArgumentException.class,()->cancelled.advance(Double.NaN));
    }
    private static class EndPorts implements RoundEndSequence.Services {
        final List<String> log=new ArrayList<>();CompletableFuture<Void> timer=new CompletableFuture<>();
        public void endingMusic(){log.add("music");}public boolean scoreboard(){return true;}public void forceScores(boolean forced){log.add("scores:"+forced);}public void botsEnd(){log.add("bots");}
        public CompletionStage<Void> sleep(double seconds){log.add("sleep:"+seconds);return timer.minimalCompletionStage();}public void statistics(Integer winner){log.add("statistics");}
        public CompletionStage<Void> waitForVote(){return done();}public String consumeMap(){return "next";}public String currentMap(){return "current";}public double screenSeconds(){return 7;}public Integer headline(Integer winner){return null;}
        public RoundEndSequence.Transition transition(){return null;}public CompletionStage<Void> restartInPlace(){log.add("restart");return done();}public CompletionStage<Void> cancelPending(){return done();}public boolean recoverable(){return true;}public void failed(){log.add("failed");}public void failure(String label,Throwable error){log.add("failure");}
    }
    @Test void cancellingMinimalTimerStageFinishesRetirementAndOldCompletionCannotRestartReplacement()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var ports=new EndPorts();var ending=new RoundEndSequence(ports,owner::add);var old=ending.run(2).toCompletableFuture();assertTrue(ending.running());assertThrows(IllegalStateException.class,()->ending.run(3));ending.cancel().toCompletableFuture().join();assertFalse(ending.running());assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,old::join).getCause());
        int count=ports.log.size();var worker=new Thread(()->ports.timer.complete(null));worker.start();worker.join();drain(owner);assertEquals(count,ports.log.size());assertFalse(ports.log.contains("restart"));ports.timer=new CompletableFuture<>();ending.run(3);assertTrue(ending.running());ending.cancel().toCompletableFuture().join();
    }
    @Test void restartAwaitsBotsUsesRosterSnapshotSkipsSpectatorsAndContinuesAfterRespawnFault(){
        var log=new ArrayList<String>();var prepare=new CompletableFuture<Void>();var reset=new CompletableFuture<Void>();var roster=new ArrayList<>(List.of(0,1,2,3));var team=new TeamState(2,"Blue",0,0,255);team.score=12;
        var restart=new RoundRestart<Integer>(new RoundRestart.Services<>(){
            public boolean scoreboard(){return true;}public void forceScores(boolean forced){assertFalse(forced);log.add("release");}public CompletionStage<Void> prepareBots(){log.add("prepare");return prepare;}public void resetRuntime(){log.add("runtime");}public List<TeamState> teams(){return List.of(team);}public CompletionStage<Void> startMode(){assertEquals(0,team.score);log.add("start");return done();}public void teamScore(TeamState current){log.add("team");}
            public List<Integer> players(){return roster;}public boolean playable(Integer p){return p!=1;}public boolean connected(Integer p){return p!=2;}public void respawn(Integer p){log.add("spawn:"+p);roster.remove(p);if(p==0)throw new IllegalStateException("spawn failed");}public void respawnFailure(Integer p,Throwable error){log.add("failure:"+p);}public CompletionStage<Void> resetBots(){log.add("reset-bots");return reset;}
        },Runnable::run);
        var result=restart.run().toCompletableFuture();assertEquals(List.of("release","prepare"),log);assertEquals(12,team.score);assertThrows(IllegalStateException.class,restart::run);prepare.complete(null);assertFalse(result.isDone());assertEquals(List.of("release","prepare","runtime","start","team","spawn:0","failure:0","spawn:3","reset-bots"),log);reset.complete(null);result.join();assertEquals(List.of(1,2),roster);
    }
    @Test void acceptedKillImmediatelyFollowedByDisconnectEarnsTdmPointBeforePlayerLeft(){
        try(var h=new TdmHarness(new TdmMode.Settings(10,0,1,0),512)){
            var killer=h.admit(0,2);h.admit(1,3);h.mode.start();h.life.runtime.damage(1,1000,0,0);assertEquals(2,h.events.size());h.packets.clear();h.life.destination=new net.battlespades.player.LifeRuntime.Messages(){public void owner(int id,net.battlespades.protocol.GamePacket packet){}public boolean inGame(int id){return true;}public void broadcast(net.battlespades.protocol.GamePacket packet){if(packet instanceof SimplePackets.PlayerLeft){assertEquals(1,h.teams.getFirst().score);assertEquals(100,killer.score);}}};h.life.runtime.disconnected(0);
            assertEquals(1,h.teams.getFirst().score);assertEquals(100,killer.score);assertEquals(0,h.events.size());assertEquals(List.of("plugin:on_player_death","plugin:on_player_kill","plugin:on_player_leave"),h.effects.stream().filter(s->s.startsWith("plugin:")).toList());assertTrue(h.life.wire.getLast() instanceof SimplePackets.PlayerLeft);assertFalse(h.life.runtime.players().contains(killer.life));
        }
    }
    @Test void serialFrameDrivesActualTdmWinThenRetainedVoteAndSameSceneLifeRestart(){
        try(var h=new TdmHarness(new TdmMode.Settings(1,0,1,0),512)){
            var killer=h.admit(0,2);var victim=h.admit(1,3);h.stopping=false;
            var timers=new ArrayDeque<CompletableFuture<Void>>();var vote=new CompletableFuture<Void>();var prepare=new CompletableFuture<Void>();var journal=new ArrayList<String>();
            var restart=new RoundRestart<net.battlespades.player.PlayerLife>(new RoundRestart.Services<>(){
                public boolean scoreboard(){return true;}public void forceScores(boolean forced){h.delivery.accept(null,new SimplePackets.ForceShowScores(forced?1:0));journal.add("release");}
                public CompletionStage<Void> prepareBots(){journal.add("prepare");return prepare;}public void resetRuntime(){h.events.clear();journal.add("runtime");}public List<TeamState> teams(){return h.teams;}
                public CompletionStage<Void> startMode(){h.mode.start();journal.add("start");return done();}public void teamScore(TeamState team){h.board.teamScore(team,null);}
                public List<net.battlespades.player.PlayerLife> players(){return h.life.runtime.players();}public boolean playable(net.battlespades.player.PlayerLife player){return player.team()==2||player.team()==3;}public boolean connected(net.battlespades.player.PlayerLife player){return h.life.runtime.inGame(player.id());}
                public void respawn(net.battlespades.player.PlayerLife player){h.life.runtime.respawn(player.id());journal.add("respawn:"+player.id());}public void respawnFailure(net.battlespades.player.PlayerLife player,Throwable error){fail(error);}public CompletionStage<Void> resetBots(){journal.add("reset-bots");return done();}
            },Runnable::run);
            h.ending=new RoundEndSequence(new EndPorts(){
                @Override public CompletionStage<Void> sleep(double seconds){journal.add("sleep:"+(int)seconds);var gate=new CompletableFuture<Void>();timers.add(gate);return gate;}
                @Override public void forceScores(boolean forced){h.delivery.accept(null,new SimplePackets.ForceShowScores(forced?1:0));journal.add("force");}
                @Override public void statistics(Integer winner){assertEquals(2,winner);journal.add("stats");}
                @Override public CompletionStage<Void> waitForVote(){journal.add("vote");return vote;}@Override public String consumeMap(){journal.add("consume");return null;}
                @Override public CompletionStage<Void> restartInPlace(){return restart.run();}@Override public CompletionStage<Void> cancelPending(){return restart.cancel();}@Override public void failed(){h.mode.endSequenceFailed();}@Override public void failure(String name,Throwable error){fail(error);}
            },Runnable::run);
            h.mode.start();var packets=new net.battlespades.runtime.IngamePackets<Integer>(new net.battlespades.runtime.IngamePackets.Settings(8,8,8),id->h.life.runtime.inGame(id),(id,data)->{h.life.runtime.damage(1,1000,id,0);return done();},error->fail(error),Runnable::run);packets.offer(0,new byte[]{1});
            var ports=new FramePorts(){@Override public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int loop){return switch(phase){case PACKET_DRAIN->packets.drain();case MODE->h.events.tick(loop);default->done();};}};
            var frame=new SimulationFrame(ports,System::nanoTime,Runnable::run);var runtime=new AsyncFixedStepRuntime(()->true,tick->frame.step(Math.toIntExact(tick)),()->journal.add("replicate"),RoundOrchestrationTest::done,(name,error)->fail(error),Runnable::run);
            assertEquals(1,runtime.advance(FixedStepRuntime.DELTA).toCompletableFuture().join());assertTrue(h.mode.clock().ended);assertEquals(1,h.teams.getFirst().score);assertEquals(100,killer.score);assertFalse(victim.life.alive());assertTrue(h.ending.running());assertTrue(journal.contains("replicate"));
            timers.remove().complete(null);assertEquals(List.of("force","sleep:5","replicate","stats","vote"),journal);vote.complete(null);assertEquals("sleep:7",journal.getLast());assertTrue(h.mode.clock().ended);timers.remove().complete(null);assertEquals("prepare",journal.getLast());assertTrue(h.mode.clock().ended);prepare.complete(null);h.endTask.join();
            assertFalse(h.ending.running());assertFalse(h.mode.clock().ended);assertFalse(h.mode.endSequenceRunning());assertEquals(0,h.teams.getFirst().score);assertEquals(0,killer.score);assertTrue(victim.life.alive());assertTrue(killer.life.alive());assertSame(killer,h.binding.row(killer.life));assertSame(victim,h.binding.row(victim.life));assertEquals(List.of("release","prepare","runtime","start","respawn:0","respawn:1","reset-bots"),journal.subList(journal.indexOf("release"),journal.size()));
        }
    }

    @Test void cancellationDuringNestedRestartInvalidatesPreparationBeforeReplacement()throws Exception {
        var owner=new ConcurrentLinkedQueue<Runnable>();var prepare=new CompletableFuture<Void>();var cleanup=new CompletableFuture<Void>();var journal=new ArrayList<String>();
        var restart=new RoundRestart<Integer>(new RoundRestart.Services<>(){
            public boolean scoreboard(){return true;}public void forceScores(boolean forced){journal.add("release");}public CompletionStage<Void> prepareBots(){return prepare.minimalCompletionStage();}public void resetRuntime(){journal.add("reset");}public List<TeamState> teams(){return List.of();}public CompletionStage<Void> startMode(){journal.add("start");return done();}public void teamScore(TeamState team){fail();}public List<Integer> players(){return List.of();}public boolean playable(Integer p){return true;}public boolean connected(Integer p){return true;}public void respawn(Integer p){fail();}public void respawnFailure(Integer p,Throwable error){fail(error);}public CompletionStage<Void> resetBots(){journal.add("reset-bots");return done();}
        },owner::add);
        var ending=new RoundEndSequence(new EndPorts(){
            @Override public CompletionStage<Void> sleep(double seconds){return done();}@Override public String consumeMap(){return null;}@Override public CompletionStage<Void> restartInPlace(){return restart.run();}
            @Override public CompletionStage<Void> cancelPending(){restart.cancel().toCompletableFuture().join();return cleanup;}
        },owner::add);
        var task=ending.run(2).toCompletableFuture();assertEquals(List.of("release"),journal);var retired=ending.cancel().toCompletableFuture();assertFalse(retired.isDone());assertTrue(ending.running());assertThrows(IllegalStateException.class,()->ending.run(3));cleanup.complete(null);retired.join();assertFalse(ending.running());assertInstanceOf(CancellationException.class,assertThrows(CompletionException.class,task::join).getCause());
        var worker=new Thread(()->prepare.complete(null));worker.start();worker.join();drain(owner);assertEquals(List.of("release"),journal);
    }

    @Test void synchronousFatalSubsystemStillRecordsTimingAndDoesNotBecomeAnOrdinaryFault(){
        var fatal=new AssertionError("fatal projectile service");var ports=new FramePorts(){@Override public void synchronous(SimulationFrame.Phase phase,int loop){super.synchronous(phase,loop);if(phase==SimulationFrame.Phase.PROJECTILES)throw fatal;}};
        var frame=new SimulationFrame(ports,System::nanoTime,Runnable::run);var result=frame.step(1).toCompletableFuture();assertSame(fatal,assertThrows(CompletionException.class,result::join).getCause());assertEquals(List.of(SimulationFrame.Phase.PACKET_DRAIN,SimulationFrame.Phase.BOTS,SimulationFrame.Phase.BOT_ACTIONS,SimulationFrame.Phase.PROJECTILES),ports.measured);assertFalse(ports.log.stream().anyMatch(s->s.startsWith("failure:")||s.startsWith("PLAYERS")));
    }

}
