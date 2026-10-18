// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.support.*;
import net.battlespades.world.*;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TransitionBoundaryTest {
    @TempDir Path temp;
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private static void pump(Queue<Runnable> owner){for(Runnable work;(work=owner.poll())!=null;)work.run();}
    @Test void actualWorkerPreflightConfinesSymlinksAndParsesMetadataWithoutChangingTheLiveWorld()throws Exception {
        Path maps=Files.createDirectory(temp.resolve("maps"));byte[] vxl={0,(byte)239,(byte)239,0,1,2,3,4};Files.write(maps.resolve("valid.vxl"),vxl);Files.writeString(maps.resolve("valid.json"),"{\"gravity\":0.4,\"vxl_format\":\"retail\"}");
        try(var worker=Executors.newSingleThreadExecutor();var live=new MutableWorld(VxlMap.loadServer(vxl,"retail"),1,64)){
            var preflight=new WorldPreflight(maps,worker);var candidate=preflight.load("valid.VXL","tdm");
            // Extension spelling is normalized to the actual source protocol stem, not to a second filename.
            var prepared=candidate.toCompletableFuture().get();assertEquals("tdm",prepared.metadata().activeMode());assertEquals(26.0/64,prepared.metadata().environment().gravity());assertEquals(0,live.revision());
            try(var replacement=prepared.world(2,64)){assertEquals(2,replacement.epoch());assertEquals(live.sourceCrc(),replacement.sourceCrc());replacement.set(new MutableWorld.Cell(36,36,100),true,1,null);assertFalse(live.solid(new MutableWorld.Cell(36,36,100)));}
            Files.writeString(maps.resolve("valid.json"),"{\"vxl_format\":\"bad\"}");assertInstanceOf(IllegalArgumentException.class,assertThrows(ExecutionException.class,()->preflight.load("valid","tdm").toCompletableFuture().get()).getCause());assertEquals(0,live.revision());
            assertInstanceOf(IllegalArgumentException.class,assertThrows(ExecutionException.class,()->preflight.load("../outside","tdm").toCompletableFuture().get()).getCause());
            if(!System.getProperty("os.name").startsWith("Windows")){Path outside=temp.resolve("outside.vxl");Files.write(outside,vxl);Files.createSymbolicLink(maps.resolve("escape.vxl"),outside);var error=assertThrows(ExecutionException.class,()->preflight.load("escape","tdm").toCompletableFuture().get());assertEquals("Map path must stay inside the configured maps directory",error.getCause().getMessage());}
        }
    }
    @Test void workerCompletionCannotShowStatsOrMutateSceneBeforeOwnerResumes()throws Exception {
        var h=new TransitionHarness(0);var owner=new ConcurrentLinkedQueue<Runnable>();var candidate=new CompletableFuture<Object>();var service=new MatchTransitions<>(h,(map,code)->candidate,owner::add);
        var transition=service.changeAfterScores("Other",130,99).toCompletableFuture();assertTrue(h.log.isEmpty());assertTrue(service.preparing());assertFalse(service.changeMap("another").toCompletableFuture().join().ok());
        var worker=new Thread(()->candidate.complete(h.candidate));worker.start();worker.join();assertTrue(h.log.isEmpty());assertFalse(transition.isDone());pump(owner);assertTrue(transition.join().ok());assertFalse(service.busy());
        var prefix=TransitionPackets.statistics(120,99).stream().map(p->"broadcast:"+HexFormat.of().formatHex(p.encode())).toList();assertEquals(prefix,h.log.subList(0,4));assertEquals(List.of("flush","sleep:120"),h.log.subList(4,6));assertTrue(h.log.get(6).startsWith("notice:"));
    }
    @Test void failedPreflightNeverPublishesTerminalUiAndReleasesOwnershipForTheNextRequest(){
        for(Throwable error:List.of(new IllegalArgumentException("bad map"),new IllegalStateException("codec crash"),new CancellationException("retired"),new AssertionError("fatal"))){
            var h=new TransitionHarness(0);var candidate=new CompletableFuture<Object>();var service=new MatchTransitions<>(h,(map,code)->candidate,Runnable::run);var transition=service.changeAfterScores("Other",7,2).toCompletableFuture();boolean[] released={false};transition.whenComplete((r,e)->released[0]=!service.busy());candidate.completeExceptionally(error);
            if(error instanceof CancellationException||error instanceof Error)assertSame(error,assertThrows(CompletionException.class,transition::join).getCause());else assertFalse(transition.join().ok());assertTrue(released[0]);assertFalse(service.busy());assertTrue(h.log.isEmpty());assertEquals("London",h.map);
        }
    }
    @Test void retainedAdminRequestDefersWorkRejectsRacingRequestsAndReportsFailurePrivately(){
        var h=new TransitionHarness(0);var owner=new ArrayDeque<Runnable>();var candidate=new CompletableFuture<Object>();var notices=new ArrayList<String>();var service=new MatchTransitions<>(h,(map,code)->candidate,owner::add);
        assertEquals("Preparing map Other",service.requestMap("Other",notices::add).message());var task=service.requestCompletion().orElseThrow().toCompletableFuture();assertTrue(service.busy());assertFalse(service.preparing());assertFalse(service.requestMode("oc",notices::add).ok());pump(owner);assertTrue(service.preparing());candidate.completeExceptionally(new IllegalArgumentException("missing candidate"));assertEquals(List.of("missing candidate"),notices);assertFalse(task.join().ok());assertFalse(service.busy());assertTrue(service.requestCompletion().isEmpty());assertTrue(h.log.isEmpty());
    }
    @Test void canonicalModeChangeReloadsTheCurrentMapAndRejectsUnimplementedFactoryAfterPreflight(){
        var h=new TransitionHarness(0);var calls=new ArrayList<String>();var service=new MatchTransitions<>(h,(map,code)->{calls.add(map+":"+code);return CompletableFuture.completedFuture(h.candidate);},Runnable::run);
        var result=service.changeMode("occupation").toCompletableFuture().join();assertEquals(List.of("London:oc"),calls);assertEquals("Unknown mode: oc",result.message());assertTrue(h.log.isEmpty());assertFalse(service.busy());assertEquals("tdm",h.code);
        h.code="occupation";assertTrue(service.changeMode("oc").toCompletableFuture().join().ok());assertEquals(1,calls.size());
    }
    @Test void allPeerReloadsStartTogetherAndOneFailureRetiresOnlyThatPeer(){
        var h=new TransitionHarness(0);var a=new CompletableFuture<Boolean>();var b=new CompletableFuture<Boolean>();h.connections.clear();
        h.connections.add(h.new Peer(0){@Override public CompletionStage<Boolean> reload(){h.log.add("reload:0");return a;}});h.connections.add(h.new Peer(1){@Override public CompletionStage<Boolean> reload(){h.log.add("reload:1");return b;}});
        var service=new MatchTransitions<>(h,(map,code)->CompletableFuture.completedFuture(h.candidate),Runnable::run);var result=service.changeMap("Other").toCompletableFuture();assertFalse(result.isDone());assertEquals(List.of("reload:0","reload:1"),h.log.subList(h.log.size()-2,h.log.size()));a.completeExceptionally(new IllegalStateException("one peer failed"));assertFalse(result.isDone());b.complete(true);assertTrue(result.join().ok());assertTrue(result.join().reconnectRequired());assertTrue(h.log.contains("disconnect:0:18"));assertFalse(h.log.contains("disconnect:1:18"));assertFalse(service.busy());
    }
    @Test void queuedLeavesAwaitBeforeDiscardAndModeFailureSkipsOnlyItsOwnPlugin()throws Exception {
        var h=new TransitionHarness(0);var owner=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Void>();var log=new ArrayList<String>();
        var mode=h.new Probe("leaves"){@Override public CompletionStage<Void> handle(ModeEvents.Event event){int id=(Integer)event.arguments().getFirst();log.add("mode:"+id);return id==1?gate:done();}};
        var events=new ModeEvents(32,8,()->mode,event->{log.add("plugin:"+event.arguments().getFirst());return done();},(label,error)->log.add("failure"),owner::add);
        events.offer(ModeEvents.Event.of("other"));events.offer(ModeEvents.Event.of("on_player_leave",1));events.offer(ModeEvents.Event.of("on_player_leave",2));var settled=events.settleLeaves(mode).toCompletableFuture();events.offer(ModeEvents.Event.of("on_player_leave",3));assertEquals(List.of("mode:1"),log);assertEquals(2,events.size());var worker=new Thread(()->gate.completeExceptionally(new IllegalStateException("leave fault")));worker.start();worker.join();assertEquals(1,log.size());pump(owner);settled.join();assertEquals(List.of("mode:1","failure","mode:2","plugin:2"),log);assertEquals(List.of("other","on_player_leave"),events.snapshot().stream().map(ModeEvents.Event::name).toList());
    }
    @Test void transitionDetachmentAwaitsOldModeBeforeRemovingCanonicalLifeTeamAndRoster()throws Exception {
        try(var h=new TdmHarness(new TdmMode.Settings(10,0,1,0),512)){
            var player=h.admit(0,2);h.mode.start();h.life.wire.clear();var owner=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Void>();var port=new TransitionHarness(0);var mode=port.new Probe("old"){@Override public CompletionStage<Void> handle(ModeEvents.Event event){assertSame(player,event.arguments().getFirst());return gate;}};
            var roster=new TransitionRoster(h.life.runtime,h.binding,()->h.teams,owner::add);var detached=roster.detach(0,mode).toCompletableFuture();assertTrue(h.teams.getFirst().players().contains(player.life));assertSame(player,h.binding.players().get(0));var worker=new Thread(()->gate.complete(null));worker.start();worker.join();assertFalse(detached.isDone());pump(owner);detached.join();assertTrue(h.life.runtime.players().isEmpty());assertTrue(h.binding.players().isEmpty());assertTrue(h.teams.getFirst().players().isEmpty());assertTrue(h.life.wire.isEmpty());assertEquals(0,h.events.size());
        }
    }
    @Test void endingTaskCanRestartItsOwnActualTdmModeWithoutCancellingItself(){
        try(var h=new TdmHarness(new TdmMode.Settings(1,0,1,0),512)){
            h.admit(0,2);h.stopping=false;h.mode.start();var ports=new TransitionHarness(14);ports.active=h.mode;var transitions=new MatchTransitions<>(ports,(map,code)->CompletableFuture.completedFuture(ports.candidate),Runnable::run);
            h.ending=new RoundEndSequence(new RoundEndSequence.Services(){
                public void endingMusic(){}public boolean scoreboard(){return true;}public void forceScores(boolean forced){}public void botsEnd(){}public CompletionStage<Void> sleep(double seconds){return done();}public void statistics(Integer winner){}public CompletionStage<Void> waitForVote(){return done();}public String consumeMap(){return null;}public String currentMap(){return "London";}public double screenSeconds(){return 7;}public Integer headline(Integer winner){return null;}public RoundEndSequence.Transition transition(){return transitions;}public CompletionStage<Void> restartInPlace(){throw new AssertionError("Unexpected recovery");}public CompletionStage<Void> cancelPending(){throw new AssertionError("Ending task cancelled itself");}public boolean recoverable(){return true;}public void failed(){h.mode.endSequenceFailed();}public void failure(String label,Throwable error){fail(error);}
            },Runnable::run);
            h.mode.end(2);assertTrue(h.endTask.isDone());h.endTask.join();assertFalse(h.ending.running());assertFalse(h.mode.clock().ended);assertFalse(h.mode.endSequenceRunning());assertTrue(h.mode.started());assertFalse(transitions.busy());assertFalse(h.effects.contains("cancel"));
        }
    }
}
