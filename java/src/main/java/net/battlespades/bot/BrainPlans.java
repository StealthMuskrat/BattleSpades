// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;

/** Completed first queries survive a deferred fallback; remembered geometry is bounded by version and TTL. */
final class BrainPlans {
    record Dry(Vector goal,double until,int failures){static Dry capture(BotBrainState s){return new Dry(s.dryDetourGoal,s.dryDetourUntil,s.dryRouteFailures);}void restore(BotBrainState s){s.dryDetourGoal=goal;s.dryDetourUntil=until;s.dryRouteFailures=failures;}}
    private final BotNavigationWorld world;private final BotBrainState state;private final double now;private final long topology;
    BrainPlans(BotNavigationWorld world,BotBrainState state,List<Object> context,long topology,double now){this.world=world;this.state=state;this.now=now;this.topology=topology;if(!context.equals(state.planningContext)){state.planningContext=Collections.unmodifiableList(new ArrayList<>(context));state.planningResults.clear();}}
    BotNavigationWorld.RoutePlan request(Vector start,Vector target,Set<Affordance> abilities,BotDigProfile dig,boolean water,Set<Edge> blocked,Boolean prefer){
        if(world.budget()==null){state.corridorYieldLocal=false;return world.plan(start,target,abilities,dig,water,blocked,true,prefer==null||prefer);}
        var key=Collections.unmodifiableList(Arrays.<Object>asList(start,target,Set.copyOf(abilities),dig,water,Set.copyOf(blocked),prefer));var completed=state.planningResults.get(key);if(completed!=null)return completed;
        var memory=new ArrayList<Object>();memory.add(topology);memory.addAll(key);var remembered=state.planMemory.get(memory);BotNavigationWorld.RoutePlan result;
        if(remembered!=null&&now<remembered.until())result=remembered.plan();else{result=world.plan(start,target,abilities,dig,water,blocked,true,prefer==null||prefer);if(!result.deferred()){state.planMemory.remove(memory);while(state.planMemory.size()>=6)state.planMemory.remove(state.planMemory.keySet().iterator().next());state.planMemory.put(Collections.unmodifiableList(new ArrayList<>(memory)),new BotBrainState.RememberedPlan(now+3,result));}}
        if(!result.deferred()){state.corridorYieldLocal=false;if(state.planningResults.size()<4)state.planningResults.put(key,result);}return result;
    }
}
