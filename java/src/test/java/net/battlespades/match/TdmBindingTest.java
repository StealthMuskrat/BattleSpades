// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.support.TdmHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TdmBindingTest {
    @Test void deadAdmissionPreservesSourceRosterOrderAndCanonicalRowsInScoreReset(){
        try(var h=new TdmHarness(new TdmMode.Settings(10,0,1,0),512)){
            h.life.denied.add(0);h.life.admit(0,2);h.life.admit(1,3);
            var live=h.binding.row(h.life.runtime.player(1));var players=h.binding.players();var dead=players.get(0);
            assertEquals(List.of(0,1),new ArrayList<>(players.keySet()));assertSame(live,players.get(1));h.delivery=(recipient,packet)->{if(Objects.equals(recipient,0))h.packets.add(packet);};h.packets.clear();h.mode.start();
            assertEquals(List.of(0,1),h.packets.stream().filter(net.battlespades.protocol.SimplePackets.SetScore.class::isInstance).map(net.battlespades.protocol.SimplePackets.SetScore.class::cast).filter(p->p.typeValue()==1).map(net.battlespades.protocol.SimplePackets.SetScore::specifier).toList());
            assertSame(dead,h.binding.row(dead.life));assertSame(live,h.binding.row(live.life));
        }
    }
    @Test void acceptedDeathCapturesReloadCountersAssistsAndQueuesPersonalBeforeTeamScoring(){
        try(var h=new TdmHarness(new TdmMode.Settings(10,0,1,0),512)){
            var assistant=h.admit(0,2);var killer=h.admit(1,2);var victim=h.admit(2,3);h.mode.start();h.life.runtime.damage(2,50,0,0);victim.life.inventory().reloadState(true,999);h.life.runtime.damage(2,500,1,0);
            assertTrue(victim.diedReloading);assertFalse(victim.life.inventory().reloading());assertEquals(1,killer.kills);assertEquals(1,victim.deaths);assertEquals(50,assistant.score);assertEquals(0,killer.score);assertEquals(List.of("on_player_death","on_player_kill"),h.events.snapshot().stream().map(ModeEvents.Event::name).toList());
            assertSame(killer,h.binding.row(killer.life));h.tick(1);assertEquals(150,killer.score);assertEquals(1,h.teams.getFirst().score);assertTrue(killer.killBonuses.isEmpty());assertEquals(1,killer.roundStats.awards.get(5));
            h.life.runtime.die(2,1,0);assertEquals(0,h.events.size());h.life.runtime.respawn(2);assertSame(victim,h.binding.row(victim.life));h.life.runtime.damage(2,500,1,0);h.tick(2);assertEquals(250,killer.score);assertEquals(50,assistant.score);h.mode.start();assertEquals(0,killer.kills);assertEquals(0,h.life.runtime.score(1).kills());assertNotNull(killer.profileStats);
        }
    }
    @Test void boundedFinalEventRemainderEndsBeforeLateKillsAndCanonicalRowsSurviveReconnect(){
        try(var h=new TdmHarness(new TdmMode.Settings(0,1,1,0),1)){
            var killer=h.admit(0,2);var victim=h.admit(1,3);var late=h.admit(2,3);h.mode.start();h.events.offer(ModeEvents.Event.of("backlog"));h.events.offer(ModeEvents.Event.of("backlog"));h.life.runtime.damage(1,1000,0,0);h.life.wall+=2;h.tick(1);assertEquals(3,h.mode.clock().timeoutRemaining());
            for(int i=2;i<=4;i++){h.events.offer(ModeEvents.Event.of("on_player_kill",late,killer,0));h.events.offer(ModeEvents.Event.of("on_player_kill",late,killer,0));h.tick(i);assertEquals(i==4,h.mode.clock().ended);}
            assertEquals(100,killer.score);assertEquals(0,late.score);assertEquals(2,h.mode.winner());assertEquals(6,h.events.size());assertEquals(1,h.teams.getFirst().score);
            h.events.clear();h.life.runtime.disconnected(1);var replacement=h.admit(1,3);assertNotSame(victim,replacement);assertSame(replacement,h.binding.players().get(1));h.mode.start();assertNull(h.mode.clock().timeoutRemaining());assertFalse(h.mode.clock().ended);
        }
    }
    @Test void retirementCancelsTheRetainedEndRequestBeforeReplacementAndLateRevealKeepsCueOrder(){
        try(var h=new TdmHarness(new TdmMode.Settings(1,0,1,0),512)){
            var killer=h.admit(0,2);var victim=h.admit(1,3);h.mode.start();h.stopping=false;h.life.runtime.damage(1,1000,0,0);h.tick(1);assertEquals(2,h.scheduledWinner);assertTrue(h.mode.endSequenceRunning());assertFalse(h.endTask.isDone());h.packets.clear();h.mode.reveal(h.peer(0),()->h.effects.add("replay"));assertInstanceOf(SimplePackets.ForceShowScores.class,h.packets.getFirst());assertInstanceOf(MatchPackets.LocalisedMessage.class,h.packets.getLast());
            h.mode.deactivate().toCompletableFuture().join();assertTrue(h.endTask.isCancelled());assertFalse(h.mode.started());assertTrue(h.mode.clock().retiring);assertFalse(h.mode.endSequenceRunning());int count=h.effects.size();h.mode.endByTime();assertEquals(count,h.effects.size());assertTrue(killer.score>0);assertFalse(victim.life.alive());
        }
    }
}
