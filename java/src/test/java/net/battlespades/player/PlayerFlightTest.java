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
