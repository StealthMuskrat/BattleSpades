// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.*;
import net.battlespades.session.*;
import net.battlespades.simulation.*;
import net.battlespades.support.StandardPorts;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StandardMatchDomainTest {
    @TempDir Path temp;
    private static ApplicationHost host(HostedSceneState state)throws Exception{return new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));}
    private static void admit(StandardMatchDomain match,int id,int team){match.scene().admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"Player"+id),null,0);}
    @Test void productionTdmBindsPreparedSpawnsScoresRespawnsAndCanonicalTeamCleanup()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("tdm.vxl"),"tdm");var ports=new StandardPorts();var ballotWait=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<String>>();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("tdm","tdm",prepared,1,10000,Map.of("tdm",(map,code,bound)->ports.create(map,code,bound,prepared.assembly().metadata(),loop)));var host=host(state)){
            state.attach(host);var match=(StandardMatchDomain)state.domain();match.mode().start();
            ports.failSpawn=true;assertThrows(IllegalStateException.class,()->admit(match,0,2));assertTrue(match.lives().players().isEmpty());assertEquals(0,match.teams().getFirst().playerCount());ports.failSpawn=false;
            admit(match,0,2);admit(match,1,3);var killer=match.lives().player(0);var victim=match.lives().player(1);assertSame(killer,match.teams().getFirst().players().getFirst());assertTrue(state.prepared().spawns().safePosition(new SpawnGeometry.Position(killer.position().x(),killer.position().y(),killer.position().z())));
            assertTrue(match.lives().die(1,0,0));assertEquals(0,match.teams().getFirst().score);match.events().tick(1).toCompletableFuture().join();assertEquals(1,match.teams().getFirst().score);assertEquals(100,match.binding().row(killer).score);assertNotNull(match.binding().row(killer).profileStats);assertEquals(1,match.spawns().resolver().deaths().size());
            long old=victim.generation();ports.wall+=1.5;ports.monotonic+=1.5;match.lives().processRespawns();assertTrue(victim.alive());assertTrue(victim.generation()>old);assertEquals("on_player_spawn",match.events().snapshot().getFirst().name());
            match.disconnected(1);assertTrue(match.teams().get(1).players().isEmpty());assertFalse(match.binding().players().containsKey(1));admit(match,1,3);assertNotSame(victim,match.lives().player(1));assertEquals(1,match.teams().get(1).playerCount());
            var ballot=match.media().ballot();assertTrue(ballot.start(List.of("tdm"),ports.wall));ballotWait.set(ballot.waitForResult().thenApply(value->{assertFalse(ballot.start(List.of("tdm"),ports.wall));return value;}).toCompletableFuture());assertFalse(ballotWait.get().isDone());
            match.mode().deactivate().toCompletableFuture().join();assertTrue(ports.errors.isEmpty());
        }assertEquals(1,ports.closes);assertNull(ballotWait.get().join());
    }
    @Test void arenaFactoryAttachesToCommittedHostAndRetiresOnlyInactiveWorlds()throws Exception{
        var tdm=StandardPorts.prepared(temp.resolve("tdm.vxl"),"tdm");var arena=StandardPorts.prepared(temp.resolve("arena.vxl"),"arena");var ports=new StandardPorts();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("tdm","tdm",tdm,1,10000,Map.of("tdm",(map,code,bound)->ports.create(map,code,bound,tdm.assembly().metadata(),loop),"arena",(map,code,bound)->ports.create(map,code,bound,arena.assembly().metadata(),loop)));var host=host(state)){
            state.attach(host);var old=state.snapshot();state.install("arena","arena",arena);var match=(StandardMatchDomain)state.domain();assertNotNull(match.controls());assertNotNull(match.media());assertSame(match.scene(),host.transitionScene());match.mode().start();admit(match,0,2);admit(match,1,3);var mode=(ArenaMode)match.mode();
            ports.wall+=5;ports.monotonic+=5;match.events().tick(60).toCompletableFuture().join();assertTrue(mode.state().started());assertEquals(2,mode.state().fighters().size());
            admit(match,2,3);assertFalse(match.lives().player(2).alive());assertEquals(2,match.teams().get(1).playerCount());match.lives().die(1,0,0);match.events().tick(61).toCompletableFuture().join();assertTrue(mode.state().ended());assertEquals(1,match.teams().getFirst().score);
            ports.wall+=5;ports.monotonic+=5;match.events().tick(62).toCompletableFuture().join();assertTrue(match.lives().players().stream().allMatch(PlayerLife::alive));assertEquals(3,match.teams().stream().mapToInt(TeamState::playerCount).sum());
            match.mode().deactivate().toCompletableFuture().join();state.restore(old);assertSame(old.mode(),state.domain().mode());state.finish();assertEquals(1,ports.closes);assertThrows(IllegalStateException.class,match::world);assertTrue(ports.errors.isEmpty());
        }assertEquals(2,ports.closes);
    }
    @Test void hostedFramesKeepOwnerProgressDuringAwaitAndStopAfterCurrentFrame()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("tdm.vxl"),"tdm");var ports=new StandardPorts();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("tdm","tdm",prepared,1,10000,Map.of("tdm",(map,code,bound)->ports.create(map,code,bound,prepared.assembly().metadata(),loop)));var host=host(state)){
            state.attach(host);var match=(StandardMatchDomain)state.domain();var wait=new CompletableFuture<Void>();ports.playerPhase=wait;int[] snapshots={0};var periodic=ports.periodic(loop);var repair=ports.repair(host,match);
            var simulation=new HostedSimulation(host,loop,()->(StandardMatchDomain)state.domain(),()->periodic,()->repair,ports,()->snapshots[0]++,System::nanoTime,IngamePackets.Settings.defaults(64,16));simulation.start();simulation.pump(20_000_000);assertEquals(1,simulation.ticks());assertTrue(simulation.advancing());assertFalse(ports.log.contains("MODE"));assertEquals(0,snapshots[0]);
            loop.execute(()->ports.log.add("owner-progress"));simulation.pump(1_000_000_000);assertTrue(ports.log.contains("owner-progress"));assertEquals(1,simulation.ticks());assertThrows(IllegalArgumentException.class,()->simulation.pump(999_999_999));
            var stop=simulation.stop().toCompletableFuture();assertFalse(stop.isDone());var worker=new Thread(()->wait.complete(null));worker.start();worker.join();assertFalse(stop.isDone());
            for(int i=0;i<8&&!stop.isDone();i++)simulation.pump(1_000_000_000);stop.join();assertEquals(1,simulation.ticks());assertEquals(1,snapshots[0]);assertTrue(match.mode().clock().retiring);assertTrue(ports.log.indexOf("PLAYERS")<ports.log.indexOf("PLUGINS"));assertTrue(ports.log.indexOf("health")<ports.log.indexOf("cancel-end"));assertTrue(ports.errors.isEmpty());assertThrows(IllegalStateException.class,simulation::start);
        }
    }
}
