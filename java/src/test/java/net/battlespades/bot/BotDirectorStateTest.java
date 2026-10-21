// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SourceRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static net.battlespades.bot.BotFoundationsTest.*;
import static net.battlespades.bot.BotSourceFixture.*;
import static net.battlespades.bot.BotMessages.*;

class BotDirectorStateTest {
    private static BotProfile profile(double skill){return new BotProfile("Bot","normal",skill,.55,.5,.55,.5,.32,.12,3.8,13,.6,.6,24,.055,List.of());}
    @Test void unchangedDirectorAimAndKeyTracesPreserveBoundedHumanMotion(){
        var fixture=read("bot-gateway");
        for(var item:list(fixture.get("aim"))){var trace=map(item);var motor=new BotAimMotor(profile(real(trace.get("skill"))),new SourceRandom(421),2.9);
            for(var sample:list(trace.get("rows"))){var row=map(sample);motor.update(new Vector(10,10,30),vector(row.get("target")),real(row.get("dt")),.7,string(trace.get("purpose")));var s=motor.state();matches(row.get("state"),Arrays.asList(s.yaw(),s.pitch(),s.yawVelocity(),s.pitchVelocity(),s.yawAcceleration(),s.pitchAcceleration(),s.yawNoise(),s.pitchNoise(),s.purpose(),s.gazeYaw(),s.gazePitch(),s.gazeAge()));}
        }
        var motor=new BotAimMotor(profile(.55),new SourceRandom(0),0);
        for(var item:list(fixture.get("keys"))){var row=map(item);var keys=motor.keys(real(row.get("forward")),real(row.get("side")));matches(row.get("keys"),List.of(keys.forward(),keys.side()));matches(row.get("residual"),List.of(motor.state().residualForward(),motor.state().residualSide()));}
    }
    private static Intent intent(long frame,long life,long topology,Priority priority,BotState.Affordance affordance,Action action){return new Intent(1,2,frame,3,4,topology,1,2,new Movement(new Vector(0,0,0),false,false,false,false,affordance,false,null,null,null,1),new Look(new Vector(10,0,0),true,5,6,0,false),6,action,priority,false,false,null,List.of(),"",(int)life,List.of());}
    @Test void staleLivesCannotCommitAndEvictedTaskRequestsCannotReplay(){
        var state=new BotIntentState(1,2);var swing=new Action(ActionKind.MELEE,0,new Vector(2,0,0),null,"",-1,0,0,0,7);
        var breach=intent(1,9,10,Priority.TRAVERSAL,BotState.Affordance.BREACH,swing);
        assertFalse(state.accept(breach,10,3,4,10,1));assertFalse(state.accept(breach,9,3,4,9,1));assertTrue(state.accept(breach,9,3,4,11,1));assertFalse(state.accept(breach,9,3,4,11,1));
        assertNull(state.takeAction());assertSame(swing,state.pending());assertEquals(2.15,state.pendingDeadline());assertTrue(state.lock(5,6,1.45));assertFalse(state.lock(5,7,1.1));assertFalse(state.lock(5,6,1.46));
        assertTrue(state.accept(intent(2,9,10,Priority.TRAVERSAL,BotState.Affordance.WALK,Action.none()),9,3,4,11,1.1));assertNull(state.pending());
        int[] commits={0};assertTrue(state.commit(9,swing,()->{commits[0]++;return true;}));assertTrue(state.commit(9,swing,()->{fail("duplicate commit");return false;}));assertEquals(1,commits[0]);
        for(int id=8;id<80;id++){var a=new Action(ActionKind.BUILD,5,null,null,"",-1,0,0,0,id);assertFalse(state.commit(9,a,()->false));}
        assertFalse(state.commit(9,swing,()->{fail("evicted request replay");return true;}));assertTrue(state.commit(10,swing,()->true));
        state.result(swing,true,1.2,false);state.result(new Action(ActionKind.FIRE,6,null,null,"",-1,0,0,0,0),true,1.3,false);assertEquals(7,state.feedback().request());assertEquals(1.2,state.feedback().taskAt());
        assertTrue(state.accept(intent(3,9,10,Priority.COMBAT,BotState.Affordance.WALK,swing),9,3,4,11,1.3));state.takeAction();state.expire(10,true,1.4);assertNull(state.pending());assertNull(state.intent());
    }
}
