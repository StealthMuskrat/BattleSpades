// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.List;
import java.util.Map;
import net.battlespades.protocol.*;
import net.battlespades.session.PlayerRoster;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRosterTest {
    private PlayerPackets.CreatePlayer player(int id) {return new PlayerPackets.CreatePlayer(id,0,5,1,0,0,new Vector3f(1,2,3),new Vector3f(1,0,0),"Source",List.of(7),List.of());}
    @Test void identicalLookingLivesStillCreateWhilePaletteUpdatesDoNotRecreate() {
        var roster=new PlayerRoster();long first=roster.create(player(1),0x123456);var plan=roster.reconcile(Map.of(),0);assertEquals(List.of(28,11),plan.packets().stream().map(GamePacket::id).toList());
        assertTrue(roster.reconcile(plan.known(),0).packets().isEmpty());roster.color(1,0x654321);var color=roster.reconcile(plan.known(),0);assertEquals(List.of(11),color.packets().stream().map(GamePacket::id).toList());
        assertTrue(roster.create(player(1),0x654321)>first);assertEquals(List.of(28,11),roster.reconcile(color.known(),0).packets().stream().map(GamePacket::id).toList());
        roster.remove(1);assertEquals(List.of(64),roster.reconcile(color.known(),0).packets().stream().map(GamePacket::id).toList());
    }
    @Test void plansAreFrozenAndOwnerIsExcludedFromRemoteCatchup() {
        var roster=new PlayerRoster();roster.create(player(0),0);roster.create(player(1),0x123456);var plan=roster.reconcile(Map.of(),0);assertEquals(1,plan.known().size());assertFalse(plan.known().containsKey(0));
        roster.color(1,0xffffff);assertEquals(0x123456,plan.known().get(1).color());assertThrows(UnsupportedOperationException.class,()->plan.known().clear());
    }
    @Test void deathIsReplayedOnceWithoutBannersAndDeadUnknownLivesStayHidden(){
        var roster=new PlayerRoster();roster.create(player(1),0x123456);var initial=roster.reconcile(Map.of(),0);var death=new LifePackets.KillAction(1,2,3,2,5,true,true);roster.death(death);
        assertTrue(roster.reconcile(Map.of(),0).packets().isEmpty());var caught=roster.reconcile(initial.known(),0);assertEquals(List.of(death.quiet()),caught.packets());assertTrue(roster.reconcile(caught.known(),0).packets().isEmpty());
        roster.create(player(1),0x123456);assertEquals(List.of(28,11),roster.reconcile(caught.known(),0).packets().stream().map(GamePacket::id).toList());assertFalse(initial.known().get(1).dead());
    }
    @Test void reusedIdRetiresTheOldIdentityBeforeCreatingOrHidingItsReplacement(){
        var roster=new PlayerRoster();roster.create(player(1),0);var initial=roster.reconcile(Map.of(),0);roster.remove(1);roster.create(player(1),0);var replaced=roster.reconcile(initial.known(),0);
        assertEquals(List.of(64,28,11),replaced.packets().stream().map(GamePacket::id).toList());assertNotEquals(initial.known().get(1).identity(),replaced.known().get(1).identity());
        roster.death(new LifePackets.KillAction(1,1,0,0,0,false,false));assertEquals(List.of(64),roster.reconcile(initial.known(),0).packets().stream().map(GamePacket::id).toList());
    }
}
