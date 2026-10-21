// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Exclusive water locomotion, finite failed-bank memory and ordinary build/breach proposals. */
final class BrainWater {
    private final BotNavigationWorld world;private final BrainIntents intents;private final BrainBreach breach;
    BrainWater(BotNavigationWorld world,BrainIntents intents,BrainBreach breach){this.world=world;this.intents=intents;this.breach=breach;}
    private static Vector point(Cell c){return new Vector(c.x(),c.y(),c.z());}
    private static Cell rounded(Vector v){return new Cell((int)Math.rint(v.x()),(int)Math.rint(v.y()),(int)Math.rint(v.z()));}
    Intent bridge(Perception f,Player p,BotBrainState s,BotGoal goal,double now){
        if(!p.loadout().contains(5)||p.blocks()<1||!p.grounded())return null;var direction=unit(goal.position().x()-p.position().x(),goal.position().y()-p.position().y());
        var line=world.water().bridgeLine(p.position(),direction,Math.min(6,p.blocks()),p.blocks()+1);String role="bridge_builder";if(line==null){line=world.water().shoulderLine(p.position(),direction,Math.min(6,p.blocks()));if(line!=null)role="bridge_widener";}if(line==null)return null;var start=line.first();var end=line.last();
        if(p.lastActionKind().equals("build_line")&&!p.lastActionAccepted()&&p.lastActionPosition()!=null&&rounded(p.lastActionPosition()).equals(start)&&now-p.lastActionAt()<10)return null;
        int cost=Math.max(Math.max(Math.abs(end.x()-start.x()),Math.abs(end.y()-start.y())),Math.abs(end.z()-start.z()))+1;if(p.blocks()<cost)return null;
        var movement=new Movement(ZERO,false,true,false,false,Affordance.BUILD_BRIDGE,true,null,null,null,1);if(now<s.nextWaterBuildAt)return intents.emit(f,s,movement,look(point(end)),5,Action.none(),Priority.TRAVERSAL,false,false,goal.position(),List.of(),goal.role()+":"+role+"_wait",true);
        s.nextWaterBuildAt=now+.8;return intents.emit(f,s,movement,look(point(end)),5,new Action(ActionKind.BUILD_LINE,5,point(start),point(end),"",-1,0,0,0,0),Priority.TRAVERSAL,false,false,goal.position(),List.of(p.position(),point(start),point(end)),goal.role()+":"+role,true);
    }
    static Set<Edge> exclusions(BotBrainState s,double now){s.waterFailedShores.removeIf(e->e.until()<=now);var result=new HashSet<>(s.blockedEdges.keySet());for(var entry:s.waterFailedShores)result.addAll(entry.edges());return Set.copyOf(result);}
    void block(BotBrainState s,Vector position,RouteStep step,double now){
        var current=world.surface((int)Math.floor(position.x()),(int)Math.floor(position.y()),position.z(),8,true);var p=step.waypoint();var target=world.surface((int)Math.floor(p.x()),(int)Math.floor(p.y()),p.z(),8,true);var edge=step.entryEdge();if(edge==null&&current!=null&&target!=null)edge=new Edge(new Cell(current.x(),current.y(),current.supportZ()),new Cell(target.x(),target.y(),target.supportZ()));if(edge!=null)BrainRoutingState.remember(s,edge,now,20);s.waterStepKey=null;s.waterLandingStep=null;s.waterBestDistance=Double.POSITIVE_INFINITY;s.waterProgressAt=now;
    }
    Intent search(Perception f,Player p,BotBrainState s){
        var step=world.water().roamStep(p.position(),s.waterSearchHeading==null?p.orientation():s.waterSearchHeading,s.blockedEdges.keySet());if(step==null)return null;s.waterSearchHeading=unit(step.waypoint().x()-p.position().x(),step.waypoint().y()-p.position().y());return emit(f,p,s,new Movement(s.waterSearchHeading,false,false,false,true,Affordance.SWIM,true,null,null,null,1),step.waypoint(),BrainInventory.weapon(p,null),Action.none(),step.waypoint(),List.of(),"water_search_shore");
    }
    Intent exit(Perception f,Player p,BotBrainState s,RouteStep step,double now,boolean force){
        if(s.waterSearchOrigin!=null&&flat(p.position(),s.waterSearchOrigin)>=6){s.waterSearchUntil=0;s.waterSearchOrigin=null;}
        var failedHeading=force&&now<s.waterSearchUntil?s.waterSearchHeading:null;
        if(now<s.waterSearchUntil&&!force){var search=search(f,p,s);if(search!=null)return search;}
        if(force){
            s.waterSearchUntil=now+8;s.waterSearchOrigin=p.position();s.waterSearchHeading=step==null?null:unit(p.position().x()-step.waypoint().x(),p.position().y()-step.waypoint().y());
            if(step!=null&&Set.of(Affordance.WALK,Affordance.SWIM).contains(step.affordance())){double side=(p.id()+p.generation())%2!=0?1:-1;s.waterSearchHeading=new Vector(-s.waterSearchHeading.y()*side,s.waterSearchHeading.x()*side,0);}
            if(failedHeading!=null)s.waterSearchHeading=new Vector(-failedHeading.y(),failedHeading.x(),0);
            var failedBank=s.waterLastBank!=null&&now-s.waterLastBank.at()<30?s.waterLastBank.position():p.position();s.waterFailedShores.add(new BotBrainState.FailedShore(now+30,world.water().shoreEdges(failedBank,4)));while(s.waterFailedShores.size()>4)s.waterFailedShores.removeFirst();
        }
        if(step==null){
            s.waterLandingStep=null;s.waterStepKey=null;s.waterBestDistance=Double.POSITIVE_INFINITY;s.waterProgressAt=now;
            var preferred=s.goal!=null&&!s.waterGoalReached&&!s.waterRecovery?s.goal.position():null;
            var bank=world.water().assistedStep(p.position(),preferred,force?Set.copyOf(s.blockedEdges.keySet()):exclusions(s,now));if(bank!=null)s.waterLastBank=new BotBrainState.Bank(now,bank.waypoint());
            if(force&&bank!=null){if(failedHeading==null)s.waterSearchHeading=unit(p.position().x()-bank.waypoint().x(),p.position().y()-bank.waypoint().y());block(s,p.position(),bank,now);return stopped(f,p,s,bank.waypoint(),"water_exit:cycle_blocked");}
            var build=bank!=null&&p.loadout().contains(5)&&p.blocks()>0?world.water().jumpBuildCell(p.position()):null;
            if(build!=null&&now>=s.nextWaterBuildAt){s.nextWaterBuildAt=now+.8;var target=point(build);var direction=unit(bank.waypoint().x()-p.position().x(),bank.waypoint().y()-p.position().y());return emit(f,p,s,new Movement(direction,true,false,false,false,Affordance.BUILD_STEP,true,null,null,null,1),target,5,new Action(ActionKind.BUILD,5,target,null,"",-1,0,0,0,0),bank.waypoint(),List.of(),"water_bank_build_step");}
            var profile=BrainInventory.dig(p);var dig=bank!=null&&profile!=null?world.water().bankBreach(p.position(),profile,preferred,exclusions(s,now)):null;
            if(dig!=null){
                var goal=s.goal==null?new BotGoal(List.of("water_bank"),bank.waypoint(),"water_bank",1,false,null):s.goal;var plan=dig.breach();var rejected=p.lastActionPosition();
                if(plan!=null&&p.lastActionKind().equals("melee")&&!p.lastActionAccepted()&&rejected!=null&&now-p.lastActionAt()>=0&&now-p.lastActionAt()<=.8&&distance(plan.target(),rejected)<=.25){BrainRoutingState.remember(s,new Edge(plan.source(),plan.destination()),now,20);BrainRoutingState.clear(s,now);s.waterStepKey=null;s.waterBestDistance=Double.POSITIVE_INFINITY;s.waterProgressAt=now;s.waterRecovery=true;return stopped(f,p,s,bank.waypoint(),goal.role()+":water_breach_rejected");}
                s.waterBreachTarget=plan==null?null:plan.targetCell();return breach.dig(f,p,s,goal,dig,now);
            }
            if(bank!=null){block(s,p.position(),bank,now);return stopped(f,p,s,bank.waypoint(),"water_bank:unusable_edge");}
            var search=search(f,p,s);if(search!=null){s.waterSearchUntil=now+8;s.waterSearchOrigin=p.position();return search;}
            var direction=preferred==null?ZERO:unit(preferred.x()-p.position().x(),preferred.y()-p.position().y());return emit(f,p,s,new Movement(direction,false,false,false,true,Affordance.SWIM,true,null,null,null,1),preferred,BrainInventory.weapon(p,null),Action.none(),preferred,List.of(),"water_no_route");
        }
        var key=List.<Object>of((int)Math.floor(step.waypoint().x()),(int)Math.floor(step.waypoint().y()),(int)Math.rint(step.waypoint().z()+2.25),step.affordance().code());double gap=flat(step.waypoint(),p.position());
        if(force){block(s,p.position(),step,now);return stopped(f,p,s,step.waypoint(),"water_exit:cycle_blocked");}
        if(!key.equals(s.waterStepKey)){s.waterStepKey=key;s.waterBestDistance=gap;s.waterProgressAt=now;}else if(gap+.35<s.waterBestDistance){s.waterBestDistance=gap;s.waterProgressAt=now;}else if(now-s.waterProgressAt>=1.75){block(s,p.position(),step,now);return stopped(f,p,s,step.waypoint(),"water_exit:edge_blocked");}
        var direction=unit(step.waypoint().x()-p.position().x(),step.waypoint().y()-p.position().y());s.waterLandingStep=step.affordance()==Affordance.JUMP?step:null;if(s.waterLandingStep!=null)s.waterLastBank=new BotBrainState.Bank(now,step.waypoint());boolean jump=step.affordance()==Affordance.JUMP;return emit(f,p,s,new Movement(direction,jump,false,false,true,jump?Affordance.JUMP:Affordance.SWIM,true,null,null,null,1),step.waypoint(),BrainInventory.weapon(p,null),Action.none(),step.waypoint(),List.of(p.position(),step.waypoint()),"water_exit");
    }
    private Intent stopped(Perception f,Player p,BotBrainState s,Vector goal,String role){return emit(f,p,s,Movement.still(),null,BrainInventory.weapon(p,null),Action.none(),goal,List.of(),role);}
    private Intent emit(Perception f,Player p,BotBrainState s,Movement movement,Vector at,int tool,Action action,Vector goal,List<Vector> path,String role){return intents.emit(f,s,movement,look(at),tool,action,Priority.SURVIVAL,false,false,goal,path,role,true);}
    private static Look look(Vector at){return at==null?null:new Look(at,false,-1,0,0,false);}
}
