// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.session.*;
import net.battlespades.support.StandardPorts;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.BoundedSnapshotCompression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HostedInputRuntimeTest {
    @TempDir Path temp;
    @Test void freshOwnedInputSelectsToolsPreservesReloadAndRepairsRevokedMounts()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("Tools.vxl"),"tdm");var ports=new StandardPorts();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("Tools","tdm",prepared,1,10000,Map.of("tdm",(map,code,bound,metadata)->ports.create(map,code,bound,metadata,loop)));var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2))){
            state.attach(host);var match=(StandardMatchDomain)state.domain();match.mode().start();match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"Tools"),null,0);var p=match.lives().player(0);var inputs=match.inputs();
            assertTrue(p.selection().loadout().containsAll(List.of(5,8)));p.fire().tool(8,true);p.inventory().ammo(1,20);assertTrue(p.fire().startReload(ports.monotonic));input(match,100,8,0);assertTrue(p.inventory().reloading());input(match,101,5,0);assertEquals(5,p.inventory().tool());assertFalse(p.inventory().reloading());
            input(match,99,8,32);assertEquals(5,p.inventory().tool());input(match,102,15,0);assertEquals(5,p.inventory().tool());assertEquals(1,inputs.rejected(p));assertTrue(ports.log.contains("tool-rejected:0:15:5"));
            ports.mounted=true;input(match,103,15,0);assertEquals(15,p.inventory().tool());assertEquals(5,p.fire().state().beforeMachineGun());ports.mounted=false;input(match,104,255,0);assertEquals(5,p.inventory().tool());assertEquals(2,inputs.rejected(p));
            ports.mounted=true;input(match,105,15,0);ports.mounted=false;assertEquals(5,inputs.unmounted(p));assertNull(p.fire().state().beforeMachineGun());assertNull(inputs.unmounted(p));
            p.fire().tool(15,true);var snapshot=new PlayerSnapshot(p,ports::presentation);assertFalse(snapshot.state(106,ports.monotonic).toolAuthorized());assertEquals(5,inputs.ensureLegal(p));assertTrue(snapshot.state(106,ports.monotonic).toolAuthorized());
            match.disconnected(0);assertEquals(0,inputs.rejected(p));match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"Replacement"),null,0);assertNotSame(p,match.lives().player(0));assertTrue(ports.errors.isEmpty());inputs.close();assertThrows(IllegalStateException.class,()->inputs.ensureLegal(p));
        }
    }
    private static void input(StandardMatchDomain match,int label,int tool,int movement){match.input(0,new LiveClientInput(label,127,false,tool,1,0,0,0,movement,24,0),1000+label);}
    @Test void onlyFreshCrouchPressesAwardActualEnemyDeathAndSpawnResetsCompletedDeaths()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("Crouch.vxl"),"tdm");var ports=new StandardPorts();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error));var state=new HostedSceneState("Crouch","tdm",prepared,1,10000,Map.of("tdm",(map,code,bound,metadata)->ports.create(map,code,bound,metadata,loop)));var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2))){
            state.attach(host);var match=(StandardMatchDomain)state.domain();match.mode().start();for(int id=0;id<2;id++)match.scene().admit(id,new SimplePackets.NewPlayerConnection(id==0?2:3,0,0,0,"P"+id),null,0);var p=match.lives().player(0);var victim=match.lives().player(1);p.body().position(new Vector(10,10,97));victim.body().position(new Vector(11,10,97));assertTrue(match.lives().die(1,null,0));var row=match.binding().row(p);long score=row.score;
            input(match,100,8,32);input(match,101,8,32);input(match,99,8,0);input(match,102,8,32);assertFalse(row.roundStats.awards.containsKey(7));input(match,103,8,0);ports.monotonic+=.1;input(match,104,8,32);input(match,105,8,0);ports.monotonic+=.1;input(match,106,8,32);assertEquals(1L,row.roundStats.awards.get(7));assertEquals(1,row.profileStats.values.get(152).count());assertEquals(score,row.score);
            for(int label=107;label<113;label++)input(match,label,8,label%2==0?32:0);assertEquals(1L,row.roundStats.awards.get(7));
            // The source clears the completed-death set on a new actor life, not on every input row.
            match.lives().die(0,null,0);match.lives().respawn(0);p.body().position(new Vector(10,10,97));for(int label=200;label<206;label++)input(match,label,8,label%2==0?32:0);assertEquals(2L,row.roundStats.awards.get(7));assertEquals(2,row.profileStats.values.get(152).count());assertTrue(ports.errors.isEmpty());
        }
    }
}
