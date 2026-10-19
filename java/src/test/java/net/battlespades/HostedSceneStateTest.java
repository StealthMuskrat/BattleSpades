// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.session.*;
import net.battlespades.support.*;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HostedSceneStateTest {
    @TempDir Path temp;
    private PreparedMap prepared(String mode)throws Exception{Path path=temp.resolve("map.vxl");Files.write(path,new byte[]{0,(byte)239,(byte)239,0,1,2,3,4});return PreparedMap.prepare(MapAssembly.load(path,mode));}
    private static ApplicationHost host(HostedSceneState state)throws Exception{return new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));}
    @Test void concreteHostCommitRetiresOnlyTheOldDomainAfterSuccessfulReloadAndInvalidatesItsSnapshot()throws Exception{
        var maps=prepared("tdm");var domains=new ArrayList<HostedTdmDomain>();try(var loop=new OwnerLoop(0,64,64,error->fail(error));var state=new HostedSceneState("First","tdm",maps,1,64,Map.of("tdm",(map,code,bound,metadata)->{var domain=new HostedTdmDomain(map,code,bound,loop);domains.add(domain);return domain;}));var host=host(state)){
            var previous=state.snapshot();var oldWorld=state.prepared().world();var services=new HostedTransitionServices(state,host,loop,new HostedTdmDomain.Bindings());var transitions=new MatchTransitions<>(services,(m,c)->CompletableFuture.completedFuture(maps),loop);
            assertTrue(transitions.requestMap("Second",message->fail(message)).ok());assertEquals("First",state.snapshot().map());loop.pump(0);assertFalse(transitions.busy());assertEquals("Second",state.snapshot().map());assertSame(state.scene(),host.transitionScene());assertSame(domains.get(1).mode(),state.snapshot().mode());assertEquals(1,domains.getFirst().closes);assertEquals(0,domains.get(1).closes);assertEquals(2,state.prepared().world().epoch());assertThrows(IllegalStateException.class,oldWorld::revision);assertThrows(IllegalArgumentException.class,()->state.restore(previous));
        }assertEquals(1,domains.get(1).closes);
    }
    @Test void failedReplacementRestoresTheActualHostAndWorldBeforeRetiringTheAbortedDomain()throws Exception{
        var maps=prepared("tdm");var domains=new ArrayList<HostedTdmDomain>();try(var loop=new OwnerLoop(0,64,64,error->fail(error));var state=new HostedSceneState("First","tdm",maps,1,64,Map.of("tdm",(map,code,bound,metadata)->{var domain=new HostedTdmDomain(map,code,bound,loop);domains.add(domain);return domain;}));var host=host(state)){
            state.fogOverride(0x123456);var previous=state.snapshot();var old=state.prepared();var bindings=new HostedTdmDomain.Bindings();bindings.rebindFailures=1;var transitions=new MatchTransitions<>(new HostedTransitionServices(state,host,loop,bindings),(m,c)->CompletableFuture.completedFuture(maps),loop);
            assertFalse(transitions.changeMap("Failed").toCompletableFuture().join().ok());assertSame(previous.world(),state.snapshot().world());assertSame(old,state.prepared());assertSame(domains.getFirst().scene(),host.transitionScene());assertEquals(0,old.world().revision());assertTrue(domains.getFirst().match.mode.started());assertEquals(0,domains.getFirst().closes);assertEquals(1,domains.get(1).closes);assertFalse(transitions.busy());
            assertEquals(0x123456,state.fogOverride());assertTrue(transitions.changeMap("Third").toCompletableFuture().join().ok());assertNull(state.fogOverride());assertEquals(3,state.prepared().world().epoch());assertEquals(1,domains.getFirst().closes);assertEquals(1,domains.get(1).closes);
        }
    }
    @Test void factoryAndMetadataFailuresCannotPublishAWorldOrAcceptForeignSnapshots()throws Exception{
        var maps=prepared("tdm");var foreign=prepared("oc");var domains=new ArrayList<HostedTdmDomain>();try(var loop=new OwnerLoop(0,64,64,error->fail(error));var state=new HostedSceneState("First","tdm",maps,1,64,Map.of("tdm",(map,code,bound,metadata)->{if(map.equals("Bad"))throw new IllegalStateException("Factory failure");var domain=new HostedTdmDomain(map,code,bound,loop);domains.add(domain);return domain;}));var host=host(state)){
            state.attach(host);var previous=state.snapshot();assertThrows(IllegalStateException.class,()->state.install("Bad","tdm",maps));assertThrows(IllegalArgumentException.class,()->state.install("Other","tdm",foreign));assertThrows(IllegalArgumentException.class,()->state.install("Other","oc",foreign));assertSame(previous.world(),state.snapshot().world());assertSame(state.scene(),host.transitionScene());assertFalse(state.hasMode("oc"));assertThrows(IllegalArgumentException.class,()->state.restore(new SceneRollover.State("First","tdm",previous.mode(),new Object(),null)));assertThrows(IllegalArgumentException.class,()->state.setMode(null));assertEquals(0,state.prepared().world().revision());
        }assertEquals(1,domains.size());
    }
    @Test void cleanupFailureIsReportedAfterCommitWithoutRollingBackToADisposedWorld()throws Exception{
        var maps=prepared("tdm");var domains=new ArrayList<HostedTdmDomain>();try(var loop=new OwnerLoop(0,64,64,error->fail(error));var state=new HostedSceneState("First","tdm",maps,1,64,Map.of("tdm",(map,code,bound,metadata)->{var domain=new HostedTdmDomain(map,code,bound,loop);domains.add(domain);return domain;}));var host=host(state)){
            domains.getFirst().failClose=true;var transitions=new MatchTransitions<>(new HostedTransitionServices(state,host,loop,new HostedTdmDomain.Bindings()),(m,c)->CompletableFuture.completedFuture(maps),loop);assertInstanceOf(IllegalStateException.class,assertThrows(CompletionException.class,()->transitions.changeMap("Second").toCompletableFuture().join()).getCause());assertEquals("Second",state.snapshot().map());assertSame(state.scene(),host.transitionScene());assertEquals(0,state.prepared().world().revision());assertFalse(transitions.busy());state.finish();assertEquals(1,domains.getFirst().closes);
        }
    }
}
