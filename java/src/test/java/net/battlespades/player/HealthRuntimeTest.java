// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.battlespades.protocol.GamePacket;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealthRuntimeTest {
    @Test void healingScalesContributionsAndDeathConsumesOneFrozenLedger(){try(var h=new LifeHarness()){
        h.admit(0,2);h.admit(1,3);h.admit(2,3);h.runtime.damage(0,30,1,0);h.runtime.damage(0,20,2,0);var original=h.runtime.player(0).damageHistory().snapshot();h.runtime.heal(0,10);
        assertEquals(List.of(30.0,20.0),original.stream().map(DamageHistory.Contribution::damage).toList());assertEquals(List.of(24.0,16.0),h.runtime.player(0).damageHistory().snapshot().stream().map(DamageHistory.Contribution::damage).toList());assertThrows(UnsupportedOperationException.class,original::clear);
        h.runtime.damage(0,1000,1,0);assertEquals(List.of(84.0,16.0),h.deathEvidence.getFirst().contributions().stream().map(DamageHistory.Contribution::damage).toList());assertTrue(h.runtime.player(0).damageHistory().snapshot().isEmpty());assertEquals(1,h.runtime.score(1).kills());
    }}
    @Test void observerAndHealthPacketOrderPrecedesDeathBroadcast(){try(var h=new LifeHarness()){
        h.admit(0,2);h.admit(1,3);h.events.clear();h.destination=new LifeRuntime.Messages(){public void owner(int id,GamePacket p){h.events.add("wire:"+p.id());}public void broadcast(GamePacket p){h.events.add("wire:"+p.id());}public boolean inGame(int id){return true;}};
        h.runtime.damage(0,100,1,0);assertEquals(List.of("damage:0","wire:5","before-death:0","delay:false","wire:46","death:0"),h.events);
    }}
    @Test void blastScopesRestoreOnExceptionAndRejectOutOfOrderClose(){try(var h=new LifeHarness()){
        h.admit(0,2);h.admit(1,3);h.admit(2,2);var outer=h.runtime.blast(2);var inner=h.runtime.blast(1);assertThrows(IllegalStateException.class,outer::close);h.runtime.damage(0,1,0,3);assertEquals(2,h.runtime.player(0).lastDamageSource());inner.close();outer.close();outer.close();
        assertThrows(IllegalStateException.class,()->{try(var scope=h.runtime.blast(2)){assertNotNull(scope);throw new IllegalStateException("Controlled action failure");}});h.runtime.damage(0,1,0,3);assertEquals(0,h.runtime.player(0).lastDamageSource());
    }}
    @Test void healthAndHistoryRejectAccessOutsideTheirOwner()throws Exception{try(var h=new LifeHarness()){
        h.admit(0,2);var p=h.runtime.player(0);var history=p.damageHistory();var error=new AtomicReference<Throwable>();var worker=new Thread(()->{try{p.health(50);}catch(Throwable failure){error.set(failure);}});worker.start();worker.join();assertInstanceOf(IllegalStateException.class,error.get());assertEquals(100,p.health());
        error.set(null);var other=new Thread(()->{try{history.snapshot();}catch(Throwable failure){error.set(failure);}});other.start();other.join();assertInstanceOf(IllegalStateException.class,error.get());
    }}
}
