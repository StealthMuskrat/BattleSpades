// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import org.junit.jupiter.api.Test;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import static org.junit.jupiter.api.Assertions.*;
final class MatchBoundaryTest {
    @Test void objectiveProtectionConfigurationAndObserverFaultsPreserveActualLife(){
        try(var h=new LifeHarness(GameRules.serverDefaults().apply(java.util.Map.of("RULE_SPAWN_PROTECTION_TIME",3)))){
            h.admit(0,2);var p=h.runtime.player(0);var disabled=new ObjectiveGuard(h.world,new ObjectiveGuard.Settings(Double.NaN,0,true,false),()->h.monotonic,life->{throw new IllegalStateException("observer fault");});assertEquals(0,disabled.entombSeconds());assertTrue(disabled.presenceEligible(p,100));disabled.endProtection(p);assertEquals(3,p.protectionRemaining(h.monotonic));
            var failure=new ObjectiveGuard(null,ObjectiveGuard.Settings.defaults(),()->{throw new IllegalStateException("clock fault");},life->false);assertTrue(failure.pickupVisible(p,null,false,.5));assertNull(failure.trapped(null,false));failure.endProtection(p);assertEquals(3,p.protectionRemaining(h.monotonic));new ObjectiveGuard(h.world,ObjectiveGuard.Settings.defaults(),()->h.monotonic,life->false).endProtection(p);assertEquals(0,p.protectionRemaining(h.monotonic));
        }
    }
    @Test void untrustedGameStatsCountIsBoundedByTheActualFrame(){
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new Wire.Writer().u8(67).i32(Integer.MAX_VALUE).i32(2).toByteArray()));assertThrows(ProtocolException.class,()->PacketCodec.decode(new Wire.Writer().u8(67).i32(1).i32(2).i32(0).toByteArray()));var negative=(ScorePackets.GameStats)PacketCodec.decode(new Wire.Writer().u8(67).i32(-3).i32(2).toByteArray());assertEquals(-3,negative.count());assertTrue(negative.rows().isEmpty());
    }
}
