// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFlightTest {
    @Test void negotiationUsesTheSameProfileForInitialInfoAndTheCharacterAndSurvivesRespawn(){
        try(var h=new LifeHarness()){
            h.runtime.admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"pilot"),new ClassCatalog.Selection(0,List.of(5,6,68),List.of(),List.of()),2);
            var p=h.runtime.player(0);assertEquals(FlightProfile.BALANCED_V2,p.flight().profile());
            assertEquals("425346500207400080074002e0010005000500050001a000",HexFormat.of().formatHex(p.flight().profile().encode()));
            h.runtime.respawn(0);assertEquals(FlightProfile.BALANCED_V2,p.flight().profile());assertEquals(100,p.flight().snapshot().fuel());assertFalse(p.flight().snapshot().physicsActive());
        }
    }
    @Test void teleportRetainsLifeAndInputIntentWhileClearingWaterAndPerFallLatch(){
        try(var h=new LifeHarness()){
            h.runtime.admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"pilot"),new ClassCatalog.Selection(0,List.of(5,6,72),List.of(),List.of()),0);var p=h.runtime.player(0);long life=p.generation();
            p.body().update(1.0/60,1,List.of());p.motion().controls(0,128);p.flight().beforeMove(p,1.0/60,h.wall);assertTrue(p.flight().snapshot().usedThisFall());
            var flight=p.flight().snapshot();var timeline=p.inputTimeline().state();p.body().velocity(new net.battlespades.physics.PlayerPhysics.Vector(.2,.3,.4));var at=new net.battlespades.physics.PlayerPhysics.Vector(40,42,80);p.teleport(at,false);assertEquals(at,p.position());assertFalse(p.flight().snapshot().usedThisFall());assertEquals(flight.deployPending(),p.flight().snapshot().deployPending());assertEquals(flight.canopy(),p.flight().snapshot().canopy());assertEquals(timeline,p.inputTimeline().state());assertEquals(life,p.generation());assertEquals(.2,p.body().state().velocity().x());
            p.body().position(new net.battlespades.physics.PlayerPhysics.Vector(40,42,237.1));p.body().velocity(new net.battlespades.physics.PlayerPhysics.Vector(0,0,1));for(int i=0;i<4&&!p.body().state().wade();i++)p.body().update(1.0/60,1,List.of());assertTrue(p.body().state().wade(),"Establish native water contact before teleporting");p.teleport(at,true);assertFalse(p.body().state().wade());assertEquals(new net.battlespades.physics.PlayerPhysics.Vector(0,0,0),p.body().state().velocity());assertEquals(at,p.positionReports().state().reported());
            assertThrows(IllegalArgumentException.class,()->p.teleport(new net.battlespades.physics.PlayerPhysics.Vector(512,0,0),true));assertEquals(at,p.position());
        }
    }
    @Test void ownerRowIdentityAndThreadOwnershipAreEnforcedAndSnapshotsCannotBeMutated(){
        try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,3);var flight=h.runtime.player(0).flight();var snapshot=flight.snapshot();
            assertThrows(IllegalArgumentException.class,()->flight.ownerRowQueued(h.runtime.player(1)));
            CompletableFuture.runAsync(()->assertThrows(IllegalStateException.class,flight::snapshot)).join();
            assertThrows(UnsupportedOperationException.class,()->snapshot.schedule().add(new PlayerFlight.CanopyPhase(1,true)));
            assertThrows(UnsupportedOperationException.class,()->flight.profile().drain().clear());
        }
    }
    @Test void oneActorFailureDoesNotSkipTheNextPlayersOwnedStep(){
        try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,3);var failed=new ArrayList<Integer>();
            for(int id=0;id<2;id++)h.runtime.input(id,new LiveClientInput(100,id,false,6,1,0,0,0,1,24,0),1);
            var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new InfantrySimulation.Hooks(){
                public void lagHistory(PlayerLife p,int tick){}public void housekeeping(PlayerLife p){}
                public boolean burdened(PlayerLife p){if(p.id()==0)throw new IllegalStateException("Controlled mode failure");return false;}
                public int pendingTerrainMutations(){return 0;}public void report(PlayerLife p,String kind,boolean enforced){}
                public void disconnect(PlayerLife p,int reason){}public void afterMove(PlayerLife p,double dt){}public void afterTick(PlayerLife p,double dt){}
                public void failed(PlayerLife p,RuntimeException failure){failed.add(p.id());}
            });
            simulation.tick(1);assertEquals(List.of(0),failed);assertEquals(100,h.runtime.player(1).inputTimeline().appliedLabel());assertNotNull(h.runtime.player(1).motion().eyeAt(100));
        }
    }
}
