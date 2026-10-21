// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** One retained route owner: shared planning credit, finite physical progress and current terrain authority. */
final class BrainNavigation implements BrainCombat.Navigation {
    private static final Set<String> ERRANDS=Set.of("chase_last_seen","investigate_sound","team_assault_enemy_side","team_assault_search","tdm_flank_approach","tdm_squad_support","tdm_overwatch_lane","squad_advance");
    private final BotNavigationWorld world;private final BrainIntents intents;private final RecoveryDriver skills;private final BrainSkills skillIntents;private final BrainCorridor corridor;private final BrainEscape escape;private final BrainWalk walk;private final BrainCrowd crowd;private final BrainBreach breach;
    final BrainWater water;final BrainTraversal traversal;
    BrainNavigation(BotNavigationWorld world,BrainIntents intents,RecoveryDriver skills,BrainSkills skillIntents){this.world=world;this.intents=intents;this.skills=skills;this.skillIntents=skillIntents;corridor=new BrainCorridor(world,skills);escape=new BrainEscape(world);walk=new BrainWalk(world,intents);crowd=new BrainCrowd(world,intents);breach=new BrainBreach(world,intents);water=new BrainWater(world,intents,breach);traversal=new BrainTraversal(world,corridor);}
    @Override public Intent navigate(Perception f,Player p,BotBrainState s,BotGoal goal,double now){return navigate(f,p,s,goal,now,null);}
    Intent navigate(Perception f,Player p,BotBrainState s,BotGoal goal,double now,Boolean waterContext){
        if(s.flightStep!=null){var flight=BrainMobility.flight(p,s,now);if(flight!=null)return intents.emit(f,s,flight.movement(),flight.look(),flight.tool(),Action.none(),Priority.TRAVERSAL,false,false,flight.goal(),List.of(),flight.role(),true);}
        boolean wet=waterContext==null?p.wade():waterContext;var held=s.goal;
        if(held!=null&&!held.key().equals(goal.key())&&ERRANDS.contains(held.role())&&ERRANDS.contains(goal.role())&&now-s.goalSince<3&&flat(held.position(),p.position())>held.arrivalRadius()+2)goal=held;else if(held==null||!held.key().equals(goal.key()))s.goalSince=now;
        BrainRoutingState.setGoal(s,goal,p.position(),now);
        if(s.planningWaitAt!=null){double delay=Math.max(0,now-s.planningWaitAt);s.waypointProgressAt+=delay;s.navigationProgressAt+=delay;s.navigationWindowAt+=delay;s.navigationCoverageAt+=delay;if(s.breachKey!=null)s.breachStartedAt+=delay;s.planningWaitAt=null;}
        if(s.escapeGoal!=null&&(now>=s.escapeUntil||distance(p.position(),s.escapeGoal)<=1.25)){s.escapeGoal=null;BrainRoutingState.clear(s,now);}
        var previous=p.position();previous=s.navigationPreviousPosition;s.navigationPreviousPosition=p.position();if(s.routeIndex<s.route.size())BrainRoutingState.note(s,s.route.get(s.routeIndex),now);var active=s.goal;
        if(active==null)return emit(f,p,s,Movement.still(),null,null,Priority.ROUTINE,"idle_goal_reset");
        if(s.navigationProgressPosition==null||flat(p.position(),s.navigationProgressPosition)>=.5){s.navigationProgressPosition=p.position();s.navigationProgressAt=now;}if(s.navigationWindowPosition==null||flat(p.position(),s.navigationWindowPosition)>=4){s.navigationWindowPosition=p.position();s.navigationWindowAt=now;}
        double goalDistance=flat(active.position(),p.position());var goalPlace=BrainRoutingState.goalPlace(world,active.position());
        if(goalDistance<=active.arrivalRadius()&&!wet&&Math.abs(goalPlace.z()-p.position().z())<=3){if(active.role().equals("chase_last_seen")){s.contactUntil=0;s.contactPosition=null;}s.route=List.of();s.routeIndex=0;Look watch=null;if(active.watchPosition()!=null){var heading=unit(active.watchPosition().x()-p.position().x(),active.watchPosition().y()-p.position().y());if(Math.hypot(heading.x(),heading.y())>.1)watch=new Look(BrainRouteProgress.look(p,heading),false,-1,0,0,false);}return emit(f,p,s,Movement.still(),watch,active.position(),Priority.ROUTINE,active.role()+":arrived");}
        s.walkAnswerWanted=false;
        if(!wet){boolean walkless=s.walklessGoal!=null&&distance(s.walklessGoal,active.position())<3;var climb=skillIntents.result(f,p,s,skills.considerStuck(p,f.profile(),now,active.position(),walkless),now);if(climb!=null)return climb;s.walkAnswerWanted=!walkless&&skills.climbAwaitsWalkAnswer(p,now,active.position());}
        boolean repeated=BrainRouteProgress.revisits(s,p.position(),active,now);var current=s.routeIndex<s.route.size()?s.route.get(s.routeIndex):null;
        if(current!=null&&!Set.of(Affordance.WALK,Affordance.CROUCH,Affordance.JUMP,Affordance.DROP).contains(current.affordance())){s.navigationCoverageAt=now;if(current.affordance()==Affordance.BREACH)progress(s,p,now);}
        else if(repeated&&s.escapeSearch==null){BrainRoutingState.invalidate(world,s,p.position(),now,12.);BrainRoutingState.clear(s,now);s.escapeGoal=null;if(s.corridorSearch==null||s.corridorSearch.done()){s.corridorSearch=null;s.corridorJoinIndex=0;s.corridorYieldLocal=false;}s.corridorRetryAt=now;s.navigationCoverageAt=now;progress(s,p,now);escape.find(f,p,s,now);return stop(f,p,s,active,"route_cycle");}
        if(s.escapeSearch!=null){escape.find(f,p,s,now);if(s.escapeSearch!=null)return waiting(f,p,s,active,now);}
        if(s.routeIndex<s.route.size()&&s.route.get(s.routeIndex).affordance()!=Affordance.BREACH&&now-s.navigationWindowAt>=5){if(BrainRoutingState.stalled(s,now,5))BrainRoutingState.invalidate(world,s,p.position(),now,null);BrainRoutingState.clear(s,now);progress(s,p,now);escape.find(f,p,s,now);return stop(f,p,s,active,"physical_edge_blocked");}
        var detour=crowd.detour(f,p,s,active,goalDistance,now);if(detour!=null)return detour;var digger=BrainBreach.active(f,p,now);if(digger!=null&&breach.waits(f,p,s,active,digger,now))return breach.assist(f,p,s,active,digger);
        var corridorGoal=corridor.segment(s,p,active,now);if(goalDistance+1<s.goalBestDistance){s.goalBestDistance=goalDistance;s.goalProgressAt=now;}
        boolean changed=!s.route.isEmpty()&&s.routeTopologyVersion!=f.topologyVersion();
        if(changed&&!world.needsReplan(s.routeTopologyVersion,p.position(),s.route.subList(s.routeIndex,s.route.size()),now-s.navigationWindowAt>=3)){s.routeTopologyVersion=f.topologyVersion();changed=false;s.corridorYieldLocal=false;}
        if(changed&&s.routeIndex<s.route.size()&&Set.of(Affordance.WALK,Affordance.CROUCH).contains(s.route.get(s.routeIndex).affordance())&&s.escapeGoal==null&&!world.planningAvailable())changed=false;
        if(changed&&s.breachKey!=null)s.goalProgressAt=now;
        if(s.route.isEmpty()||s.routeIndex>=s.route.size()||changed){
            var oldStep=changed&&s.routeIndex<s.route.size()?s.route.get(s.routeIndex):null;double oldBest=s.waypointBestDistance,oldProgress=s.waypointProgressAt,oldBreachAt=s.breachStartedAt,oldNext=s.nextBreachAt;var oldBreach=s.breachKey;
            var personality=BrainTraversal.crossingPersonality(f,p);if(s.dryDetourGoal!=null&&(now>=s.dryDetourUntil||flat(s.dryDetourGoal,p.position())<=2.5)){s.dryDetourGoal=null;s.dryDetourUntil=0;}boolean crowded=s.crowdDetourGoal!=null&&now<s.crowdDetourUntil;
            Vector target=s.escapeGoal;if(target==null)target=crowded?s.crowdDetourGoal:s.dryDetourGoal!=null?s.dryDetourGoal:corridorGoal;if(target==null)target=BrainTraversal.lane(f,p,active.position());
            var abilities=new HashSet<>(BrainInventory.movementAbilities(p));if(now<s.nextFlightAt)abilities.remove(Affordance.JETPACK);var dig=BrainInventory.dig(p);var blocked=Set.copyOf(s.blockedEdges.keySet());var dry=BrainPlans.Dry.capture(s);
            var context=Arrays.<Object>asList(p.position(),f.topologyVersion(),active.position(),target,s.escapeGoal,dry,wet,personality.style(),Set.copyOf(abilities),dig,blocked);var plans=new BrainPlans(world,s,context,f.topologyVersion(),now);
            BotNavigationWorld.RoutePlan plan;
            if(s.escapeGoal!=null){abilities.add(Affordance.DROP);plan=plans.request(p.position(),target,abilities,dig,wet||s.escapeAllowWater,blocked,false);}
            else if(wet)plan=plans.request(p.position(),target,abilities,dig,true,blocked,null);
            else if(personality.style()==BrainTraversal.Style.SWIM){s.dryDetourGoal=null;s.dryDetourUntil=0;s.dryRouteFailures=0;plan=plans.request(p.position(),target,abilities,dig,true,blocked,null);if(plan.deferred())return waiting(f,p,s,active,now,dry);if(!BrainRouteProgress.advance(plan,p.position()))plan=plans.request(p.position(),target,abilities,dig,false,blocked,null);}
            else{
                var dryPlan=plans.request(p.position(),target,abilities,dig,false,blocked,null);if(dryPlan.deferred())return waiting(f,p,s,active,now,dry);
                if(BrainRouteProgress.advance(dryPlan,p.position())){plan=dryPlan;if(s.dryDetourGoal==null&&dryPlan.reachedSegmentGoal())s.dryRouteFailures=0;}
                else{
                    boolean longGap=personality.style()==BrainTraversal.Style.BRIDGE||s.dryRouteFailures>=1;var gap=crowded?null:skillIntents.result(f,p,s,skills.considerGap(p,f.profile(),now,active.position(),longGap?16:8),now);if(gap!=null)return gap;
                    var bridge=personality.style()==BrainTraversal.Style.BRIDGE&&!crowded?water.bridge(f,p,s,active,now):null;if(bridge!=null){s.route=List.of();s.routeIndex=0;return bridge;}
                    var detourPlan=new BotNavigationWorld.RoutePlan(List.of(),false,0,false);
                    if(!crowded&&s.dryRouteFailures<traversal.detoursBeforeSwim(p,s,active,now)){s.dryRouteFailures++;s.dryDetourGoal=BrainTraversal.detour(p,active.position(),personality,s.dryRouteFailures);s.dryDetourUntil=now+12;detourPlan=plans.request(p.position(),s.dryDetourGoal,abilities,dig,false,blocked,null);if(detourPlan.deferred())return waiting(f,p,s,active,now,dry);}
                    boolean advancing=BrainRouteProgress.advance(detourPlan,p.position());plan=advancing?detourPlan:plans.request(p.position(),target,abilities,dig,true,blocked,null);if(!advancing){s.dryDetourGoal=null;s.dryDetourUntil=0;}
                }
            }
            if(plan.deferred())return waiting(f,p,s,active,now,dry);
            if(!wet&&!BrainRouteProgress.advance(plan,p.position())){var gap=skillIntents.result(f,p,s,skills.considerGap(p,f.profile(),now,active.position(),16),now);if(gap!=null)return gap;}
            s.planningContext=null;s.planningResults.clear();BrainRoutingState.resetBreach(s,now);
            if(s.escapeGoal!=null&&!BrainRouteProgress.advance(plan,p.position())){s.escapeGoal=null;BrainRoutingState.clear(s,now);if(now>=s.escapeRetryAt)escape.find(f,p,s,now);plan=new BotNavigationWorld.RoutePlan(s.route,plan.reachedSegmentGoal(),plan.expansions(),plan.deferred());}
            s.route=plan.steps();s.nextExtensionAt=now+.5;double reach=PathFollowing.remainingDistance(plan.steps(),0,p.position());
            if(s.escapeGoal==null&&!plan.reachedSegmentGoal()&&distance(p.position(),goalPlace)>12&&!plan.steps().isEmpty()&&reach<=5&&plan.steps().stream().noneMatch(step->step.breach()!=null)){s.shortPlans=now-s.shortPlanAt<=6?s.shortPlans+1:1;s.shortPlanAt=now;}else if(plan.reachedSegmentGoal()||reach>8)s.shortPlans=0;s.deadEnd=s.shortPlans>=2;
            if(corridorGoal!=null&&target.equals(corridorGoal))s.corridorReach=plan.reachedSegmentGoal()?8:0;
            s.routeIndex=0;skip(s,p,wet,previous,now,false);s.routeTopologyVersion=f.topologyVersion();var next=s.routeIndex<s.route.size()?s.route.get(s.routeIndex):null;
            if(same(oldStep,next)){if(next!=null&&next.affordance()==Affordance.JUMP&&oldStep.entryEdge()!=null){var route=new ArrayList<>(s.route);route.set(s.routeIndex,new RouteStep(next.waypoint(),next.affordance(),next.breach(),oldStep.entryEdge()));s.route=List.copyOf(route);}s.waypointBestDistance=oldBest;s.waypointProgressAt=oldProgress;s.breachKey=oldBreach;s.breachStartedAt=oldBreachAt;s.nextBreachAt=oldNext;}
            else{s.waypointBestDistance=Double.POSITIVE_INFINITY;s.waypointProgressAt=now;}
        }
        BrainRouteProgress.catchUp(s,p.position());skip(s,p,wet,previous,now,true);if(!wet)traversal.extend(f,p,s,active,now);
        if(s.routeIndex>=s.route.size()&&now-s.navigationProgressAt>=2.5&&now>=s.escapeRetryAt)escape.find(f,p,s,now);
        if(s.routeIndex>=s.route.size()){s.route=List.of();return emit(f,p,s,BrainRouteProgress.coast(world,p,s,active,now),null,active.position(),Priority.ROUTINE,active.role()+":segment_complete");}
        var step=s.route.get(s.routeIndex);
        if(p.grounded()&&!wet&&step.affordance()==Affordance.DROP&&p.position().z()-step.waypoint().z()>1.6){BrainRoutingState.clear(s,now);return emit(f,p,s,BrainRouteProgress.coast(world,p,s,active,now),null,active.position(),Priority.ROUTINE,active.role()+":fell_past_step");}
        if(step.affordance()==Affordance.BREACH){if(wet){BrainRoutingState.clear(s,now);s.waterRecovery=true;return emit(f,p,s,Movement.still(),null,active.position(),Priority.SURVIVAL,active.role()+":water_breach_handoff");}return breach.dig(f,p,s,active,step,now);}
        BrainRoutingState.note(s,step,now);boolean noProgress=now-s.navigationProgressAt>=2.5,noTravel=now-s.navigationWindowAt>=5;
        if(noProgress||noTravel){if(noProgress&&BrainRoutingState.stalled(s,now,2.5)||noTravel&&BrainRoutingState.stalled(s,now,5))BrainRoutingState.invalidate(world,s,p.position(),now,null);BrainRoutingState.clear(s,now);progress(s,p,now);escape.find(f,p,s,now);return stop(f,p,s,active,"physical_edge_blocked");}
        s.yieldedBreachEdge=null;s.yieldedBreachStartedAt=now;return walk.follow(f,p,s,active,step,now,wet);
    }
    private static void progress(BotBrainState s,Player p,double now){s.navigationProgressPosition=p.position();s.navigationProgressAt=now;s.navigationWindowPosition=p.position();s.navigationWindowAt=now;}
    private static boolean same(RouteStep a,RouteStep b){return a!=null&&b!=null&&a.affordance()==b.affordance()&&distance(a.waypoint(),b.waypoint())<=.25&&Objects.equals(a.breach()==null?null:a.breach().targetCell(),b.breach()==null?null:b.breach().targetCell());}
    private static void skip(BotBrainState s,Player p,boolean wet,Vector previous,double now,boolean reset){while(s.routeIndex<s.route.size()&&BrainRouteProgress.reached(s.route.get(s.routeIndex),p.position(),wet,previous,s.routeIndex==s.route.size()-1,BrainRouteProgress.exactNext(s.route,s.routeIndex))){s.routeIndex++;if(reset){s.waypointBestDistance=Double.POSITIVE_INFINITY;s.waypointProgressAt=now;}}}
    private Intent waiting(Perception f,Player p,BotBrainState s,BotGoal goal,double now,BrainPlans.Dry dry){dry.restore(s);return waiting(f,p,s,goal,now);}
    private Intent waiting(Perception f,Player p,BotBrainState s,BotGoal goal,double now){if(s.planningWaitAt==null)s.planningWaitAt=now;return emit(f,p,s,BrainRouteProgress.coast(world,p,s,goal,now),null,goal.position(),Priority.ROUTINE,goal.role()+":planning_wait");}
    private Intent stop(Perception f,Player p,BotBrainState s,BotGoal goal,String role){return emit(f,p,s,Movement.still(),null,goal.position(),Priority.TRAVERSAL,goal.role()+":"+role);}
    private Intent emit(Perception f,Player p,BotBrainState s,Movement movement,Look look,Vector goal,Priority priority,String role){return intents.emit(f,s,movement,look,BrainInventory.weapon(p,null),Action.none(),priority,false,false,goal,List.of(),role,true);}
}
