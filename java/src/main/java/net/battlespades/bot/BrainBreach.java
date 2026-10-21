// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** One shared excavation owner and finite, geometry-specific follower queues. */
final class BrainBreach {
    private final BotNavigationWorld world;private final BrainIntents intents;
    BrainBreach(BotNavigationWorld world,BrainIntents intents){this.world=world;this.intents=intents;}
    static Player active(Perception f,Player p,double now){return f.players().stream().filter(q->q.bot()&&q.alive()&&q.spawned()&&q.team()==p.team()&&q.id()!=p.id()&&q.lastActionKind().equals("melee")&&q.lastActionAccepted()&&q.lastActionPosition()!=null&&now-q.lastActionAt()>=0&&now-q.lastActionAt()<=.8&&flat(q.lastActionPosition(),p.position())<=5).min(Comparator.comparingInt(Player::id)).orElse(null);}
    boolean waits(Perception f,Player p,BotBrainState s,BotGoal goal,Player digger,double now){
        var target=digger.lastActionPosition();if(target==null||now<s.breachWaitReleasedUntil)return false;if(f.objectives().stream().anyMatch(o->o.kind().equals("zombie_order")&&o.carrierId()==p.id()&&!o.cells().isEmpty()))return false;
        var ahead=s.route.subList(s.routeIndex,Math.min(s.route.size(),s.routeIndex+8));var shared=ahead.stream().filter(step->step.breach()!=null&&distance(step.breach().target(),target)<=2.25).findFirst().orElse(null);
        boolean onWay=shared!=null||ahead.stream().anyMatch(step->flat(step.waypoint(),target)<=1.25&&Math.abs(step.waypoint().z()-target.z())<=3);
        if(ahead.isEmpty()){double dx=goal.position().x()-p.position().x(),dy=goal.position().y()-p.position().y(),length=Math.hypot(dx,dy);if(length>1e-6){double rx=target.x()-p.position().x(),ry=target.y()-p.position().y(),along=(rx*dx+ry*dy)/length,across=Math.abs(ry*dx-rx*dy)/length;onWay=along>=0&&along<=Math.min(length,6)&&across<=1.5;}}
        if(!onWay){s.breachWaitDigger=null;return false;}if(!Objects.equals(s.breachWaitDigger,digger.id())){s.breachWaitDigger=digger.id();s.breachWaitSince=now;}
        else if(now-s.breachWaitSince>=2){s.breachWaitDigger=null;s.breachWaitReleasedUntil=now+6;if(shared!=null){BrainRoutingState.remember(s,new Edge(shared.breach().source(),shared.breach().destination()),now,60);BrainRoutingState.clear(s,now);}return false;}return true;
    }
    static Vector pairAxis(int left,int right){int low=Math.min(left,right),high=Math.max(left,right);long mixed=((long)low*73856093)^((long)high*19349663)^0x9e3779b9L;double angle=(mixed%360)*Math.PI/180,sign=left==low?1:-1;return new Vector(Math.cos(angle)*sign,Math.sin(angle)*sign,0);}
    Intent assist(Perception f,Player p,BotBrainState s,BotGoal goal,Player digger){
        var target=Objects.requireNonNull(digger.lastActionPosition());var ids=f.players().stream().filter(q->q.bot()&&q.alive()&&q.spawned()&&q.team()==p.team()&&q.id()!=digger.id()&&flat(q.position(),target)<=6).map(Player::id).sorted().toList();int rank=Math.max(0,ids.indexOf(p.id()));
        double dx=p.position().x()-target.x(),dy=p.position().y()-target.y(),gap=Math.hypot(dx,dy);if(gap<=1e-6){var axis=pairAxis(p.id(),digger.id());dx=axis.x();dy=axis.y();gap=1;}double desired=2+Math.min(3,rank)*1.15;var direction=gap+.2<desired?new Vector(dx/gap,dy/gap,0):ZERO;var queue=new Vector(target.x()+dx/gap*desired,target.y()+dy/gap*desired,p.position().z());
        return emit(f,p,s,goal,new Movement(direction,false,false,false,false,Affordance.WALK,true,null,null,null,1),target,BrainInventory.weapon(p,null),Action.none(),false,List.of(p.position(),queue,target),"breach_assist_queue",false);
    }
    static List<Player> queue(Perception f,Player p,Vector target){
        var candidates=new ArrayList<>(f.players().stream().filter(q->q.bot()&&q.alive()&&q.spawned()&&q.team()==p.team()&&(q.id()==p.id()||q.lastActionKind().equals("melee")&&q.lastActionPosition()!=null&&f.createdAt()-q.lastActionAt()>=0&&f.createdAt()-q.lastActionAt()<=.8&&flat(q.lastActionPosition(),target)<=3.25&&Math.abs(q.lastActionPosition().z()-target.z())<=2)&&Math.abs(q.position().z()-p.position().z())<=2.25&&flat(q.position(),p.position())<=2.25).toList());if(candidates.stream().noneMatch(q->q.id()==p.id()))candidates.add(p);candidates.sort(Comparator.comparingInt(Player::id));return List.copyOf(candidates);
    }
    Intent dig(Perception f,Player p,BotBrainState s,BotGoal goal,RouteStep step,double now){
        var breach=step.breach();if(!p.canShoot()||breach==null||!world.solid(breach.targetCell().x(),breach.targetCell().y(),breach.targetCell().z())){BrainRoutingState.clear(s,now);return stopped(f,p,s,goal,"breach_replan",true);}
        var nearby=queue(f,p,breach.target());var owner=nearby.isEmpty()?p:nearby.getFirst();
        if(owner.id()!=p.id()){
            BrainRoutingState.resetBreach(s,now);var edge=new Edge(breach.source(),breach.destination());if(!edge.equals(s.yieldedBreachEdge)){s.yieldedBreachEdge=edge;s.yieldedBreachStartedAt=now;}
            else if(now-s.yieldedBreachStartedAt>=1.25){BrainRoutingState.remember(s,edge,now,60);BrainRoutingState.clear(s,now);s.yieldedBreachEdge=null;return stopped(f,p,s,goal,"breach_detour",false);}
            int rank=0;for(int i=0;i<nearby.size();i++)if(nearby.get(i).id()==p.id())rank=i;double dx=p.position().x()-breach.target().x(),dy=p.position().y()-breach.target().y(),gap=Math.hypot(dx,dy);if(gap<=1e-6){var axis=pairAxis(p.id(),owner.id());dx=axis.x();dy=axis.y();gap=1;}double desired=1+Math.min(3,rank)*1.15;var direction=gap+.2>=desired?ZERO:new Vector(dx/gap,dy/gap,0);var queue=new Vector(breach.target().x()+direction.x()*desired,breach.target().y()+direction.y()*desired,p.position().z());return emit(f,p,s,goal,new Movement(direction,false,false,false,false,Affordance.WALK,true,null,null,null,1),breach.target(),breach.toolId(),Action.none(),false,List.of(p.position(),queue,breach.target()),"breach_yield",false);
        }
        s.yieldedBreachEdge=null;s.yieldedBreachStartedAt=now;var key=List.<Object>of(breach.targetCell(),breach.toolId(),breach.secondary());if(!key.equals(s.breachKey)){boolean same=s.breachKey!=null&&s.breachKey.subList(1,s.breachKey.size()).equals(key.subList(1,key.size()));s.breachKey=key;s.breachStartedAt=now;s.nextBreachAt=same?Math.max(s.nextBreachAt,now):now;}
        double timeout=Math.max(3,breach.estimatedSwings()*breach.fireInterval()*3+1.5);if(now-s.breachStartedAt>timeout){BrainRoutingState.remember(s,new Edge(breach.source(),breach.destination()),now,60);BrainRoutingState.clear(s,now);return stopped(f,p,s,goal,"breach_failed",true);}
        var target=breach.target();Action action=Action.none();if(now+1e-9>=s.nextBreachAt){action=new Action(ActionKind.MELEE,breach.toolId(),target,null,"",-1,0,0,0,0);s.nextBreachAt=now+Math.max(.05,breach.fireInterval());}
        return emit(f,p,s,goal,new Movement(ZERO,false,target.z()>=p.position().z(),false,false,Affordance.BREACH,true,null,null,null,1),target,breach.toolId(),action,breach.secondary(),List.of(p.position(),target,step.waypoint()),"route_breach",true);
    }
    private Intent stopped(Perception f,Player p,BotBrainState s,BotGoal goal,String role,boolean adjust){return emit(f,p,s,goal,Movement.still(),null,BrainInventory.weapon(p,null),Action.none(),false,List.of(),role,adjust);}
    private Intent emit(Perception f,Player p,BotBrainState s,BotGoal goal,Movement movement,Vector look,int tool,Action action,boolean secondary,List<Vector> path,String role,boolean adjust){return intents.emit(f,s,movement,look==null?null:new Look(look,false,-1,0,0,false),tool,action,Priority.TRAVERSAL,secondary,false,goal.position(),path,goal.role()+":"+role,adjust);}
}
