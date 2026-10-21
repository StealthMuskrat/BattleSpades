// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Exact ordinary-route executor, including validated lookahead, drop authority and takeoff alignment. */
final class BrainWalk {
    private final BotNavigationWorld world;private final BrainIntents intents;
    BrainWalk(BotNavigationWorld world,BrainIntents intents){this.world=world;this.intents=intents;}
    Intent follow(Perception f,Player p,BotBrainState s,BotGoal goal,RouteStep step,double now,boolean water){
        BrainRoutingState.resetBreach(s,now);double gap=flat(step.waypoint(),p.position());
        if(gap+.35<s.waypointBestDistance){s.waypointBestDistance=gap;s.waypointProgressAt=now;}
        else if(now-s.waypointProgressAt>=1.75){if(s.lookaheadActive)s.lookaheadOffUntil=now+8;BrainRoutingState.invalidate(world,s,p.position(),now,null);BrainRoutingState.clear(s,now);return emit(f,p,s,Movement.still(),null,goal,List.of(),goal.role()+":edge_blocked");}
        var direction=unit(step.waypoint().x()-p.position().x(),step.waypoint().y()-p.position().y());var affordance=step.affordance();
        if(affordance==Affordance.JETPACK){s.flightStep=step;s.flightSource=p.position();s.flightStartedAt=now;s.flightDeparted=false;s.flightWatch=null;var flight=BrainMobility.flight(p,s,now);if(flight==null)return null;return intents.emit(f,s,flight.movement(),flight.look(),flight.tool(),Action.none(),Priority.TRAVERSAL,false,false,flight.goal(),List.of(),flight.role(),true);}
        var motor=water&&!Set.of(Affordance.JUMP,Affordance.BUILD_STEP,Affordance.BREACH).contains(affordance)?Affordance.SWIM:affordance;
        if(motor==Affordance.WALK&&step.waypoint().z()-p.position().z()>1.5&&step.waypoint().z()-p.position().z()<=4)motor=Affordance.DROP;
        var steer=step.waypoint();s.lookaheadActive=false;
        s.stepNote=!Set.of(Affordance.WALK,Affordance.DROP).contains(motor)?"exact:"+motor.name().toLowerCase(Locale.ROOT):!p.grounded()?"airborne":water?"wading":now<s.lookaheadOffUntil?"off":"blocked:"+(PathFollowing.runEnd(s.route,s.routeIndex)-s.routeIndex)+":"+(s.routeIndex+1<s.route.size()?s.route.get(s.routeIndex+1).affordance().name().toLowerCase(Locale.ROOT):"end");
        boolean sprint=motor==Affordance.SWIM||motor==Affordance.WALK&&BrainRouteProgress.sprint(s,p);int drop=1;
        if(Set.of(Affordance.WALK,Affordance.DROP).contains(motor)&&!water&&now>=s.lookaheadOffUntil){
            int target=p.grounded()?PathFollowing.lookahead(world,s.route,s.routeIndex,p.position(),s.lookaheadTarget):PathFollowing.heldIndex(s.route,s.routeIndex,s.lookaheadTarget);
            s.lookaheadTarget=target>s.routeIndex?s.route.get(target).waypoint():null;
            if(target>s.routeIndex){
                int advanced=PathFollowing.passedIndex(s.route,s.routeIndex,target,p.position());if(advanced!=s.routeIndex){s.routeIndex=advanced;s.waypointBestDistance=Double.POSITIVE_INFINITY;s.waypointProgressAt=now;}
                steer=s.route.get(target).waypoint();s.lookaheadActive=true;s.stepNote="far";
                double level=p.position().z();for(int i=s.routeIndex;i<=target;i++){double next=s.route.get(i).waypoint().z();if(next-level>1.05)drop=(int)PathFollowing.SAFE_DROP;level=next;}
                motor=Affordance.WALK;direction=unit(steer.x()-p.position().x(),steer.y()-p.position().y());int last=PathFollowing.runEnd(s.route,s.routeIndex);sprint=(last+1>=s.route.size()||flat(s.route.get(last).waypoint(),p.position())>=BrainRouteProgress.sprintRoom(p))&&(BrainRouteProgress.uphill(p)||!BrainRouteProgress.rise(s.route,s.routeIndex,target,p.position()));
            }else if(motor==Affordance.DROP&&PathFollowing.runsOff(world,step,p.position())){s.lookaheadActive=true;s.stepNote="run_off";drop=(int)PathFollowing.SAFE_DROP;motor=Affordance.WALK;}
        }
        if(Set.of(Affordance.JUMP,Affordance.DROP).contains(motor)&&!water&&p.grounded()){
            var lineup=PathFollowing.takeoffAlignment(step,p.position());if(lineup!=null){var gaze=BrainIntents.gaze(s,p,now);if(gaze==null)gaze=step.waypoint();var move=new Movement(unit(lineup.x()-p.position().x(),lineup.y()-p.position().y()),false,false,false,false,Affordance.WALK,true,p.position(),lineup,gaze,1);return emit(f,p,s,move,new Look(BrainRouteProgress.look(p,direction),false,-1,0,0,false),goal,List.of(),goal.role()+":line_up");}
            var axis=PathFollowing.edgeAxis(step);if(axis!=null)direction=axis;
        }
        Vector gaze=null;if(motor==Affordance.WALK)s.travelHeading=direction;boolean travel=Set.of(Affordance.WALK,Affordance.CROUCH,Affordance.JUMP,Affordance.DROP).contains(motor);if(!water&&travel)gaze=BrainIntents.gaze(s,p,now);
        var movement=new Movement(direction,affordance==Affordance.JUMP,affordance==Affordance.CROUCH,false,goal.sprint()&&sprint,motor,true,travel?p.position():null,travel?steer:null,gaze,drop);
        var path=new ArrayList<Vector>();path.add(p.position());for(int i=s.routeIndex;i<Math.min(s.route.size(),s.routeIndex+8);i++)path.add(s.route.get(i).waypoint());
        return emit(f,p,s,movement,new Look(BrainRouteProgress.look(p,direction),false,-1,0,0,false),goal,path,goal.role());
    }
    private Intent emit(Perception f,Player p,BotBrainState s,Movement movement,Look look,BotGoal goal,List<Vector> path,String role){return intents.emit(f,s,movement,look,BrainInventory.weapon(p,null),Action.none(),Priority.TRAVERSAL,false,false,goal.position(),path,role,true);}
}
