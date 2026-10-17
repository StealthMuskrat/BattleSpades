// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.protocol.*;
import net.battlespades.world.*;

final class TerrainHealthDeliveryTest {
    private static MutableWorld world(){return new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,128);}
    @Test void damageDuringPartialCanonicalEnqueueRestartsThatCellWithCurrentHealth(){
        try(var world=world()){
            var cell=new MutableWorld.Cell(10,10,100);var capture=world.capture(false,false);world.set(cell,true,0x406080,9.);var packets=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.WAITING,capture.lease().pump(0,100,1,p->{packets.add(p);return true;}));world.terrainHealth().apply(new BlockDamageModel.Cell(10,10,100),2,5);
            while(capture.lease().pump(0,101,1,p->{packets.add(p);return true;})==MutableWorld.Replay.WAITING){}assertEquals(List.of(33,33,7,38,38,33,7,38,38),packets.stream().map(GamePacket::id).toList());assertEquals(7,((BlockManagerState)packets.getLast()).damaged().getFirst().health());capture.lease().close();
        }
    }
    @Test void achievementFailureCannotSuppressCommittedCollapseRepairAndModeNotification(){
        try(var harness=new net.battlespades.support.LifeHarness()){
            harness.admit(0,2);var ports=new net.battlespades.support.TerrainPorts();ports.failAchievement=true;var hit=new MutableWorld.Cell(10,10,100);var floating=new MutableWorld.Cell(11,10,100);harness.world.set(hit,true,0x406080,9.);harness.world.set(floating,true,0x406080,9.);var terrain=new net.battlespades.combat.TerrainDamage(harness.world,ports,()->0);assertTrue(terrain.gun(harness.runtime.player(0),new BlockDamageModel.Cell(10,10,100),10));assertFalse(harness.world.solid(hit));assertFalse(harness.world.solid(floating));assertTrue(ports.events.stream().anyMatch(e->e.startsWith("collapsed:")));assertTrue(ports.events.stream().anyMatch(e->e.startsWith("mode:")));
        }
    }
    @Test void healthScaleChangeReopensAnAdmissionLeaseWithUpdatedRemainingHealth(){
        try(var world=world()){
            var cell=new MutableWorld.Cell(10,10,100);world.set(cell,true,0x406080,9.);world.terrainHealth().apply(new BlockDamageModel.Cell(10,10,100),2,5);var lease=world.capture(false,false).lease();assertEquals(MutableWorld.Replay.COMPLETE,lease.pump(0,100,99,p->true));world.terrainHealth().configure(2,TerrainHealth.UserMode.NORMAL);var packets=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.COMPLETE,lease.pump(0,101,99,p->{packets.add(p);return true;}));assertEquals(16,packets.stream().filter(BlockManagerState.class::isInstance).map(BlockManagerState.class::cast).flatMap(p->p.damaged().stream()).findFirst().orElseThrow().health());lease.close();
        }
    }
}
