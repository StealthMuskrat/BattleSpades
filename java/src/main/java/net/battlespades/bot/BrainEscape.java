// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Finite retained recovery candidates; successful geometry is always queried on the current map. */
final class BrainEscape {
    private final BotNavigationWorld world;BrainEscape(BotNavigationWorld world){this.world=world;}
    private static Set<Edge> recent(BotBrainState s,double now){var result=new HashSet<Edge>();s.blockedEdges.forEach((edge,expiry)->{if(now-s.blockedEdgeSince.getOrDefault(edge,expiry-60)<4)result.add(edge);});return Set.copyOf(result);}
    void find(Perception f,Player p,BotBrainState s,double now){
        s.escapeRetryAt=now+1;var goal=s.goal;if(goal==null){s.escapeSearch=null;return;}
        var abilities=new HashSet<>(BrainInventory.movementAbilities(p));abilities.add(Affordance.DROP);var dig=BrainInventory.dig(p);
        var context=new BotBrainState.EscapeContext(p.position(),goal.key(),goal.position(),f.topologyVersion(),Set.copyOf(abilities),dig,p.wade(),Set.copyOf(s.blockedEdges.keySet()));
        var previous=s.escapeSearch==null?null:s.escapeSearch.key();boolean resume=previous!=null&&previous.goalKey().equals(goal.key())&&distance(previous.start(),p.position())<1&&distance(previous.goal(),goal.position())<16&&previous.abilities().equals(context.abilities())&&Objects.equals(previous.dig(),dig)&&previous.wade()==p.wade();
        if(!resume){
            double bearing=Math.atan2(goal.position().y()-p.position().y(),goal.position().x()-p.position().x()),sign=p.id()%2!=0?1:-1;
            var restrictions=new ArrayList<Set<Edge>>();restrictions.add(context.blocked());var recent=recent(s,now);if(!recent.equals(context.blocked()))restrictions.add(recent);
            double[] offsets={Math.PI/2,-Math.PI/2,Math.PI,Math.PI/4,-Math.PI/4,3*Math.PI/4,-3*Math.PI/4,0};int rotation=(s.escapeAttempts*3)%offsets.length;double radius=8+Math.min(s.escapeAttempts,4)*2;
            var candidates=new ArrayList<Vector>();for(int i=0;i<offsets.length;i++){double angle=bearing+offsets[(i+rotation)%offsets.length]*sign;candidates.add(new Vector(p.position().x()+radius*Math.cos(angle),p.position().y()+radius*Math.sin(angle),p.position().z()));}
            var options=p.wade()?List.of(true):List.of(false,true);var queries=new ArrayList<BotBrainState.EscapeQuery>();for(boolean water:options)for(var blocked:restrictions)for(var candidate:candidates)queries.add(new BotBrainState.EscapeQuery(candidate,water,null,blocked));queries.add(new BotBrainState.EscapeQuery(goal.position(),true,null,recent));
            if(dig!=null){var targets=new ArrayList<Vector>();targets.add(goal.position());targets.addAll(candidates);for(boolean water:options)for(var candidate:targets)queries.add(new BotBrainState.EscapeQuery(candidate,water,dig,recent));}
            s.escapeSearch=new BotBrainState.EscapeSearch(context,List.copyOf(queries),0);
        }
        context=s.escapeSearch.key();var queries=s.escapeSearch.choices();int index=s.escapeSearch.index();BotNavigationWorld.RoutePlan chosen=null;Set<Edge> chosenRestrictions=Set.of();
        while(index<queries.size()){
            var query=queries.get(index);var current=s.blockedEdges.keySet();var blocked=new HashSet<>(query.blocked());blocked.retainAll(current);for(var edge:current)if(!context.blocked().contains(edge))blocked.add(edge);blocked.addAll(recent(s,now));
            var plan=world.plan(p.position(),query.target(),abilities,query.dig(),query.water(),blocked,true,query.dig()==null);
            if(plan.deferred()){s.escapeSearch=new BotBrainState.EscapeSearch(context,queries,index);s.escapeRetryAt=now;return;}index++;
            boolean useful=query.dig()!=null?plan.steps().stream().anyMatch(step->step.affordance()==Affordance.BREACH)&&BrainRouteProgress.advance(plan,p.position()):!plan.steps().isEmpty()&&distance(p.position(),plan.steps().getLast().waypoint())>=6;
            if(useful){chosen=plan;chosenRestrictions=blocked;break;}
        }
        s.escapeSearch=null;s.escapeAttempts++;if(goal.role().equals("tdm_squad_support"))s.supportEscapeAttempted=true;if(chosen==null)return;
        var edges=BrainRouteProgress.edges(p.position(),chosen.steps());for(var edge:new ArrayList<>(s.blockedEdges.keySet()))if(!chosenRestrictions.contains(edge)&&edges.contains(edge)){s.blockedEdges.remove(edge);s.blockedEdgeSince.remove(edge);}
        s.escapeGoal=chosen.steps().getLast().waypoint();s.escapeAllowWater=p.wade()||chosen.steps().stream().anyMatch(step->step.affordance()==Affordance.SWIM);s.escapeUntil=now+8;s.route=chosen.steps();s.routeIndex=0;s.routeTopologyVersion=f.topologyVersion();s.waypointBestDistance=Double.POSITIVE_INFINITY;s.waypointProgressAt=now;s.navigationProgressPosition=p.position();s.navigationProgressAt=now;s.navigationWindowPosition=p.position();s.navigationWindowAt=now;
    }
}
