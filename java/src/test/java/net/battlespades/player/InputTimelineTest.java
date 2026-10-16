// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputTimelineTest {
    private static LiveClientInput input(int label,int movement,int action){return new LiveClientInput(label,127,false,255,0,1,0,255,movement,action,0);}
    @Test void sessionIdentityAndFreshnessPrecedeImmediatePortsButNeverAuthorizeToolsOrHits(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var before=p.body().state();
            h.runtime.input(0,input(100,1,4),10);h.runtime.input(0,input(102,32,0),11);h.runtime.input(0,input(101,16,128),12);
            assertEquals(2,h.inputCount);assertEquals(new Vector(0,1,0),p.aim());assertEquals(before,p.body().state());
            assertEquals(List.of(100,101,102),p.inputTimeline().state().frames().stream().map(InputTimeline.Frame::label).toList());
            assertEquals(100,p.health());assertEquals(0,h.runtime.score(0).kills());assertTrue(p.inputTimeline().zoomForAction(101,false));
            var timeline=p.inputTimeline();h.runtime.die(0,null,12);h.runtime.input(0,input(103,255,255),13);
            assertEquals(3,timeline.state().frames().size());h.runtime.respawn(0);assertSame(timeline,p.inputTimeline());
            assertTrue(timeline.state().frames().isEmpty());assertEquals(103,timeline.state().newest());assertEquals(10,timeline.labelArrivalTick(100));
            h.runtime.disconnected(0);h.admit(0,2);assertNotSame(timeline,h.runtime.player(0).inputTimeline());assertNull(h.runtime.player(0).inputTimeline().state().newest());
        }
    }
    @Test void frozenRowsAndSnapshotsCannotBeChangedAndOwnerAccessIsEnforced()throws InterruptedException {
        var timeline=new InputTimeline();timeline.receive(input(1,1,4),true,true,new Vector(1,0,0),1,0,false);
        var snapshot=timeline.state();assertThrows(UnsupportedOperationException.class,()->snapshot.frames().clear());
        timeline.receive(input(2,1,0),true,true,new Vector(1,0,0),2,0,false);assertEquals(1,snapshot.frames().size());
        var failure=new AtomicReference<Throwable>();var thread=new Thread(()->{try{timeline.state();}catch(Throwable t){failure.set(t);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,failure.get());
    }
    @Test void absentFramesDoNotAdvanceTheCursorAndWideClockGapsDoNotInventTicks(){
        var timeline=new InputTimeline();var aim=new Vector(1,0,0);assertTrue(timeline.consumeOne(8,aim).isEmpty());
        timeline.receive(input(100,1,0),true,true,aim,1,0,false);assertEquals(100,timeline.consumeOne(8,aim).orElseThrow().label());
        timeline.receive(input(1000,1,0),true,true,aim,2,0,false);var next=timeline.consumeOne(8,aim).orElseThrow();assertEquals(1000,next.label());assertFalse(next.synthesized());
    }
}
