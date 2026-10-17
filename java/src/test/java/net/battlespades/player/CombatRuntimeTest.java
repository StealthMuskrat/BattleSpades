// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import net.battlespades.combat.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatRuntimeTest {
    static ShootPacket shot(int label){return new ShootPacket(label,254,0,new Vector3f(36,36,97),new Vector3f(1,0,0),32767,32767,true,true,0);}
    static void setup(PlayerLife p,int tool,int label){p.body().position(new Vector(36,36,97));p.fire().tool(tool,true);p.inventory().ammo(8,100);p.inputTimeline().receive(new LiveClientInput(label,0,false,tool,1,0,0,0,0,0,0),false,false,p.aim(),1000,0,false);}
    @Test void statisticalAndAchievementFaultsDoNotInterruptRealDamage(){try(var h=new LifeHarness()){
        var ports=new CombatPorts(h);ports.failObserve=ports.failResolved=true;var combat=ports.bind();h.admit(0,2);h.admit(1,3);var p=h.runtime.player(0);var victim=h.runtime.player(1);setup(p,6,100);victim.body().position(new Vector(39,36,96.5));
        assertTrue(combat.shot(p,shot(100)));assertEquals(30,victim.health());assertEquals(7,p.inventory().ammo().clip());assertEquals(1,p.combatStatistics().snapshot().shots());assertEquals(1,p.combatStatistics().snapshot().hits());
    }}
    @Test void resolutionExceptionClearsTallyAndNextRequestStillCommits(){try(var h=new LifeHarness()){
        var ports=new CombatPorts(h);var combat=ports.bind();h.admit(0,2);h.admit(1,3);var p=h.runtime.player(0);var victim=h.runtime.player(1);setup(p,6,100);victim.body().position(new Vector(39,36,96.5));ports.failPlayerHit=true;
        assertThrows(IllegalStateException.class,()->combat.shot(p,shot(100)));assertEquals(30,victim.health());assertEquals(1,p.combatStatistics().snapshot().shots());assertEquals(0,p.combatStatistics().snapshot().hits());
        ports.failPlayerHit=false;h.monotonic+=3;h.wall+=3;p.inputTimeline().receive(new LiveClientInput(280,0,false,6,1,0,0,0,0,0,0),false,false,p.aim(),1180,0,false);assertTrue(combat.shot(p,shot(280)));assertEquals(0,victim.health());assertEquals(2,p.combatStatistics().snapshot().shots());assertEquals(1,p.combatStatistics().snapshot().hits());
    }}
    @Test void respawnRetainsBurstButReleasedIdentityCannotInheritIt(){try(var h=new LifeHarness()){
        var ports=new CombatPorts(h);var combat=ports.bind();h.admit(0,2);var first=h.runtime.player(0);setup(first,60,100);combat.shot(first,shot(100));assertEquals(100,first.fire().state().lastShot());
        first.spawn(new Vector(36,36,97),h.monotonic,1,first.color());setup(first,60,106);combat.shot(first,shot(106));assertEquals(0,first.fire().state().lastShot(),"source burst continuation survives ordinary life reset");
        h.runtime.disconnected(0);h.admit(0,2);var replacement=h.runtime.player(0);assertNotSame(first,replacement);setup(replacement,60,112);combat.shot(replacement,shot(112));assertEquals(100,replacement.fire().state().lastShot(),"released numeric identity gets a fresh cadence group");assertEquals(1,replacement.combatStatistics().snapshot().shots());
    }}
}
