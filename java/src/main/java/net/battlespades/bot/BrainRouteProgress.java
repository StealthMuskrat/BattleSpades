// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.catalog.SourceCatalog;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Native landing contracts, sprint braking and retained physical-coverage watchdogs. */
final class BrainRouteProgress {
    private BrainRouteProgress(){}
    private static final Set<Integer> NO_UPHILL=Set.copyOf(SourceCatalog.integers(SourceCatalog.load("bot-live").getArray("no_sprint_uphill")));
    static boolean reached(RouteStep step,Vector at,boolean water,Vector previous,boolean last,boolean currentCell){
        var p=step.waypoint();boolean same=(int)Math.floor(p.x())==(int)Math.floor(at.x())&&(int)Math.floor(p.y())==(int)Math.floor(at.y());
        if(currentCell&&!same||step.affordance()==Affordance.BREACH)return false;
        if(step.affordance()==Affordance.WALK&&previous!=null&&distance(previous,at)<=4&&Math.abs(p.z()-at.z())<=.75&&Math.abs(p.z()-previous.z())<=.75){
            double dx=at.x()-previous.x(),dy=at.y()-previous.y(),squared=dx*dx+dy*dy;
            if(squared>1e-6){double projection=((p.x()-previous.x())*dx+(p.y()-previous.y())*dy)/squared;if(projection>0&&projection<1&&Math.hypot(previous.x()+dx*projection-p.x(),previous.y()+dy*projection-p.y())<=.45)return true;}
        }
        if(flat(p,at)>.9||step.affordance()==Affordance.JUMP&&water)return false;
        if((step.affordance()==Affordance.SWIM&&!water||last&&step.affordance()==Affordance.WALK)&&!same)return false;
        return step.affordance()==Affordance.SWIM&&water||Math.abs(p.z()-at.z())<=.75;
    }
    static Set<Edge> edges(Vector start,List<RouteStep> steps){var result=new HashSet<Edge>();var previous=start;for(var step:steps){if(step.breach()!=null)result.add(new Edge(step.breach().source(),step.breach().destination()));else if(step.entryEdge()!=null)result.add(step.entryEdge());else{int count=Math.max(1,(int)Math.ceil(distance(previous,step.waypoint())*4));var node=node(previous);for(int i=1;i<=count;i++){var target=step.waypoint();var point=new Vector(previous.x()+(target.x()-previous.x())*i/count,previous.y()+(target.y()-previous.y())*i/count,previous.z()+(target.z()-previous.z())*i/count);var next=node(point);if(!next.equals(node)){result.add(new Edge(node,next));node=next;}}}previous=step.waypoint();}return result;}
    private static Cell node(Vector p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.rint(p.z()+2.25));}
    static boolean advance(BotNavigationWorld.RoutePlan plan,Vector at){return plan.steps().stream().anyMatch(step->step.affordance()==Affordance.BREACH||!reached(step,at,false,null,true,false));}
    static boolean exactNext(List<RouteStep> route,int index){return index+1<route.size()&&route.get(index+1).affordance()!=Affordance.WALK;}
    static void catchUp(BotBrainState s,Vector at){int current=s.routeIndex;for(int i=current;i<Math.min(s.route.size(),current+8);i++){var step=s.route.get(i);if(step.affordance()!=Affordance.WALK&&step.affordance()!=Affordance.CROUCH)break;if(i>current&&reached(step,at,false,null,true,true))s.routeIndex=i;}}
    static double sprintRoom(Player p){return Math.min(9,Math.max(1.75,.65+Math.hypot(p.velocity().x(),p.velocity().y())*10));}
    static boolean uphill(Player p){return !NO_UPHILL.contains(p.classId());}
    static boolean rise(List<RouteStep> route,int index,int last,Vector at){double traveled=0;var previous=at;for(int i=index;i<=last;i++){var point=route.get(i).waypoint();traveled+=flat(point,previous);if(traveled>2.5)return false;if(point.z()<previous.z()-.25)return true;previous=point;}return false;}
    static boolean sprint(BotBrainState s,Player p){double needed=sprintRoom(p),traveled=0;boolean uphill=uphill(p);var previous=p.position();Vector direction=null;for(int i=s.routeIndex;i<Math.min(s.route.size(),s.routeIndex+8);i++){var step=s.route.get(i);if(step.affordance()!=Affordance.WALK)return false;var point=step.waypoint();double dx=point.x()-previous.x(),dy=point.y()-previous.y(),length=Math.hypot(dx,dy),change=point.z()-previous.z();if(Math.abs(change)>.25&&(Math.abs(change)>Math.max(1,length)*1.05||change<0&&!uphill))return false;if(length>1e-6){var heading=new Vector(dx/length,dy/length,0);if(direction==null)direction=heading;else if(direction.x()*heading.x()+direction.y()*heading.y()<.94)return false;traveled+=length;if(traveled>=needed)return true;}previous=point;}return false;}
    static Vector look(Player p,Vector direction){return new Vector(p.eye().x()+direction.x()*6,p.eye().y()+direction.y()*6,p.eye().z());}
    static boolean revisits(BotBrainState s,Vector at,BotGoal goal,double now){
        if(now-s.navigationCoverageLastAt>1)s.navigationCoverageAt=now;s.navigationCoverageLastAt=now;var cell=new Cell((int)Math.floor(at.x()/2),(int)Math.floor(at.y()/2),(int)Math.floor(at.z()/2));boolean fresh=!s.navigationVisited.containsKey(cell);s.navigationVisited.remove(cell);s.navigationVisited.put(cell,now);if(s.navigationVisited.size()>128)s.navigationVisited.remove(s.navigationVisited.keySet().iterator().next());double gap=distance(at,goal.position());boolean changed=!Objects.equals(s.navigationCoverageGoal,goal.key())||s.navigationCoverageTarget==null||distance(s.navigationCoverageTarget,goal.position())>=3;if(changed){s.navigationCoverageGoal=goal.key();s.navigationCoverageTarget=goal.position();s.navigationCoverageBest=gap;}boolean improved=gap+4<s.navigationCoverageBest;if(improved)s.navigationCoverageBest=gap;if(fresh||improved){s.navigationCoverageAt=now;if(s.escapeGoal==null&&improved)s.escapeAttempts=0;}return now-s.navigationCoverageAt>=8;
    }
    static Movement coast(BotNavigationWorld world,Player p,BotBrainState s,BotGoal goal,double now){
        if(!p.grounded()||p.wade()||s.deadEnd||now-s.navigationWindowAt>=3)return Movement.still();var toward=unit(goal.position().x()-p.position().x(),goal.position().y()-p.position().y());for(var heading:new Vector[]{s.travelHeading,toward}){if(heading==null||Math.hypot(heading.x(),heading.y())<.5)continue;var ahead=new Vector(p.position().x()+heading.x()*4,p.position().y()+heading.y()*4,p.position().z());if(PathFollowing.straightWalkable(world,p.position(),ahead))return new Movement(heading,false,false,false,false,Affordance.WALK,true,p.position(),ahead,null,1);}return Movement.still();
    }
}
