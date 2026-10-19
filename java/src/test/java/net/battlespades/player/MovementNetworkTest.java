// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.session.*;
import net.battlespades.support.*;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.BoundedSnapshotCompression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static net.battlespades.player.MovementPacketRates.Kind.*;

class MovementNetworkTest {
    @TempDir Path temp;
    @Test void sourceBudgetKeepsHitchAllowanceAndEarnsSimulatedTimeWhileLanesStayIndependent(){
        var rates=new MovementPacketRates();int admitted=0;for(int i=0;i<1000;i++)if(rates.allow(CLIENT_DATA,false,5,0))admitted++;
        assertEquals(64,admitted);assertEquals(936,rates.snapshot().get(CLIENT_DATA).rejected());assertTrue(rates.allow(CLOCK_SYNC,false,5,0));assertTrue(rates.allow(POSITION_DATA,false,5,0));
        // A two-second hitch earns 480 tokens; the allowance must not shrink to 64 during drain.
        for(int i=0;i<480;i++)assertTrue(rates.allow(CLIENT_DATA,false,7,0));assertFalse(rates.allow(CLIENT_DATA,false,7,0));
        var offline=new MovementPacketRates();for(int loop=1;loop<=600;loop++)assertTrue(offline.allow(CLIENT_DATA,false,0,loop));
        var bypass=new MovementPacketRates();for(int i=0;i<1000;i++){assertTrue(bypass.allow(CLIENT_DATA,true,0,0));assertTrue(bypass.allow(CLOCK_SYNC,false,0,0,0,0));}assertTrue(bypass.snapshot().isEmpty());
    }
    @Test void hostedRateAdmissionPrecedesTimelineStateAndSurvivesRespawnButNotSlotReuse()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("Rates.vxl"),"tdm");var ports=new StandardPorts();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("Rates","tdm",prepared,1,10000,Map.of("tdm",(map,code,bound,metadata)->ports.create(map,code,bound,metadata,loop)));var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2))){
            state.attach(host);var match=(StandardMatchDomain)state.domain();match.mode().start();match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"Rates"),null,0);var p=match.lives().player(0);
            for(int label=100;label<164;label++)match.input(0,new LiveClientInput(label,127,false,8,1,0,0,0,0,24,0),1000);
            var before=p.inputTimeline().state();var last=p.motion().snapshot().lastPacket();ports.monotonic+=.001;match.input(0,new LiveClientInput(500,127,false,5,0,1,0,0,32,3,0),1000);
            assertEquals(before,p.inputTimeline().state());assertEquals(last,p.motion().snapshot().lastPacket());assertEquals(8,p.inventory().tool());assertEquals(new Vector(1,0,0),p.aim());assertEquals(List.of("rate:client_data"),ports.movementReports);
            // Position and clock traffic have their own source budgets and diagnostics.
            match.position(0,new LivePositionReport(Double.NaN,0,0),1000);assertEquals(0,p.positionReports().state().received());assertEquals("position_nonfinite",ports.movementReports.getLast());var at=p.position();match.position(0,new LivePositionReport(at.x()+4,at.y()-2,at.z()+1),1000);assertEquals(at,p.position());assertEquals(Math.sqrt(21),p.positionReports().state().distance());for(int i=0;i<16;i++)assertTrue(match.clockAllowed(0,1000));assertFalse(match.clockAllowed(0,1000));assertEquals("rate:clock_sync",ports.movementReports.getLast());
            match.lives().die(0,null,0);match.lives().respawn(0);var spawnedTimeline=p.inputTimeline().state();match.input(0,new LiveClientInput(600,127,false,8,1,0,0,0,0,24,0),1000);assertEquals(spawnedTimeline,p.inputTimeline().state());assertEquals(64,p.packetRates().snapshot().get(CLIENT_DATA).accepted());assertEquals(1,p.positionReports().state().received());
            match.disconnected(0);match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"New"),null,0);var replacement=match.lives().player(0);assertNotSame(p,replacement);assertTrue(replacement.packetRates().snapshot().isEmpty());assertEquals(0,replacement.positionReports().state().received());assertTrue(ports.errors.isEmpty());
        }
    }
    @Test void livePositionLayoutsRetainNonfiniteValuesForDomainRejectionAndFreshPinPreservesVelocity(){
        var raw=new LivePositionReport(12.5,48.25,99);assertEquals(raw,LivePositionReport.decode(raw.encode()));
        var fixed=new Wire.Writer().u8(116).i16(0x8320).i16(Fixed.encodePosition(48.25)).i16(Fixed.encodePosition(99)).u8(7).toByteArray();assertEquals(new LivePositionReport(-12.5,48.25,99),LivePositionReport.decode(fixed));
        assertTrue(Double.isNaN(LivePositionReport.decode(new LivePositionReport(Double.NaN,0,0).encode()).x()));assertThrows(ProtocolException.class,()->LivePositionReport.decode(new byte[]{116,1,2,3,4,5}));
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var report=p.positionReports();var target=new Vector(40,41,90);var velocity=new Vector(.25,.5,-.125);p.body().velocity(velocity);assertTrue(report.receive(p,new LivePositionReport(target.x(),target.y(),target.z()),1000));var before=p.position();report.pin(p,1001.0001);assertEquals(before,p.position());report.pin(p,1001);assertEquals(target,p.position());assertEquals(velocity,p.body().state().velocity());assertEquals(0,report.state().distance());
            assertFalse(report.receive(p,new LivePositionReport(Double.POSITIVE_INFINITY,0,0),1002));assertEquals(1,report.state().received());h.runtime.die(0,null,0);h.runtime.respawn(0);assertEquals(p.position(),report.state().reported());assertEquals(1,report.state().received());assertEquals(1000,report.state().updated());
        }
    }
}
