// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;
import net.battlespades.simulation.FixedStepRuntime;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InfantrySimulationTest {
    static final class Ports implements InfantrySimulation.Hooks {
        final List<Integer> failures=new ArrayList<>(),lag=new ArrayList<>();
        int moves;
        public void lagHistory(PlayerLife p,int tick){lag.add(p.id());}
        public void housekeeping(PlayerLife p){}
        public boolean burdened(PlayerLife p){return false;}
        public int pendingTerrainMutations(){return 0;}
        public void report(PlayerLife p,String kind,boolean enforced){}
        public void disconnect(PlayerLife p,int reason){}
        public void afterMove(PlayerLife p,double dt){moves++;}
        public void afterTick(PlayerLife p,double dt){}
        public void failed(PlayerLife p,RuntimeException failure){failures.add(p.id());}
    }
    private static LiveClientInput input(int label,int flags){return new LiveClientInput(label,0,false,6,1,0,0,0,flags,24,0);}
    @Test void fixedOwnerStepsConsumeQueuedInputAndTheLoadingGateRetainsIt(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var ports=new Ports();var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),ports);
            int[] tick={0};var loop=new FixedStepRuntime(16,16,()->simulation.tick(++tick[0]),()->{},failure->{throw new AssertionError(failure);});
            h.loading.add(0);for(int i=100;i<106;i++){int label=i;assertTrue(loop.offer(()->h.runtime.input(0,input(label,1),tick[0])));}
            var before=p.body().state();loop.advance(FixedStepRuntime.DELTA*2);assertEquals(before,p.body().state());assertEquals(6,p.inputTimeline().size());assertTrue(ports.lag.isEmpty());
            h.loading.remove(0);loop.advance(FixedStepRuntime.DELTA);assertEquals(100,p.inputTimeline().appliedLabel());assertEquals(before.position().x(),p.position().x());
            loop.advance(FixedStepRuntime.DELTA);assertEquals(101,p.inputTimeline().appliedLabel());assertTrue(p.position().x()>before.position().x());assertEquals(2,ports.moves);
        }
    }
    @Test void unsupportedFlightFailsVisiblyAndDoesNotFreezeTheFollowingActor(){
        try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,3);var first=h.runtime.player(0);first.apply(new ClassCatalog.Selection(0,List.of(5,6,66),List.of(),List.of()));first.spawn(first.position(),h.monotonic,1,first.color());
            h.runtime.input(0,input(100,1),1);h.runtime.input(1,input(100,1),1);var ports=new Ports();var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),ports);simulation.tick(1);
            assertEquals(List.of(0),ports.failures);assertNull(first.inputTimeline().appliedLabel());assertEquals(100,h.runtime.player(1).inputTimeline().appliedLabel());assertEquals(1,ports.moves);
        }
    }
    @Test void eyeSnapshotsAreFrozenAndFacadeClippingPreservesRawMoverVelocity(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new Ports());
            p.body().velocity(new Vector(.00003,0,0));assertEquals(0,PlayerMotion.clipped(p.body().state().velocity()).x());assertEquals(.00003,p.body().state().velocity().x());
            h.runtime.input(0,input(100,0),1);simulation.tick(1);var frozen=p.motion().snapshot();assertNotNull(frozen.eyes().get(100));assertThrows(UnsupportedOperationException.class,()->frozen.eyes().clear());
            h.runtime.respawn(0);assertTrue(p.motion().snapshot().eyes().isEmpty());assertEquals(1,frozen.eyes().size());
        }
    }
}
