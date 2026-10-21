// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.LifeCatalog;
import net.battlespades.entities.ProjectileSpec;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Medic support and stocked class gadgets use ordinary proposals at their measured tactical sites. */
final class BrainDeploy {
    private final BotNavigationWorld world;private final BrainIntents intents;private final BrainCombat.Navigation navigation;private final BrainCombat combat;private final BotModePolicies.Memory memory;
    BrainDeploy(BotNavigationWorld world,BrainIntents intents,BrainCombat.Navigation navigation,BrainCombat combat,BotModePolicies.Memory memory){this.world=world;this.intents=intents;this.navigation=navigation;this.combat=combat;this.memory=memory;}
    static boolean near(Perception f,int tool,Vector at,double radius){return f.entities().stream().anyMatch(e->e.alive()&&e.toolId()==tool&&distance(e.position(),at)<=radius);}
    Intent medic(Perception f,Player p,BotBrainState s,double now){
        if(!p.loadout().contains(51)||now<s.nextSupportAt)return null;var patient=f.players().stream().filter(q->q.team()==p.team()&&q.id()!=p.id()&&q.alive()&&q.spawned()&&q.health()<=70&&distance(p.position(),q.position())<=30).min(Comparator.comparingInt(Player::health).thenComparingDouble(q->distance(p.position(),q.position()))).orElse(null);if(patient==null)return null;if(near(f,51,patient.position(),7)){s.nextSupportAt=now+3;return null;}
        if(distance(p.position(),patient.position())<=4.25&&p.grounded()){BrainRoutingState.setGoal(s,null,p.position(),now);s.nextSupportAt=now+10;return emit(f,s,crouch(),patient.position(),51,new Action(ActionKind.DEPLOY,51,patient.position(),null,"",4,0,0,0,0),patient.position(),"medic_place_medpack");}
        return navigation.navigate(f,p,s,new BotGoal(List.of("support",patient.id(),patient.generation()),patient.position(),"medic_support",3.5,true,null),now);
    }
    Intent strategic(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        if(now<s.nextDeployAt||!p.grounded())return null;if(decision==null)decision=memory.decide(f,p);
        if(decision!=null&&decision.directive().equals("fortify")&&distance(p.position(),decision.position())<=14){var cover=combat.prefabCover(f,p,s,decision.position(),now);if(cover!=null)return cover;}
        Vector focus;String role;if(s.contactPosition!=null&&now<s.contactUntil){focus=s.contactPosition;role="recent_contact";}else if(decision!=null){focus=decision.position();role=decision.role();}else return null;
        double reach=distance(p.position(),focus),maximum,cooldown;int tool;
        if(p.loadout().contains(16)){tool=16;maximum=18;cooldown=24;}else if(p.loadout().contains(56)){tool=56;maximum=22;cooldown=35;}else if(p.loadout().contains(20)){tool=20;maximum=role.equals("recent_contact")?20:10;cooldown=14;}else if(p.loadout().contains(64)&&f.objectives().stream().anyMatch(o->o.team()>=0&&o.team()!=p.team())){tool=64;maximum=45;cooldown=45;}else return null;
        if(reach>maximum)return null;int limit=tool==16?2:tool==20?3:1;if(own(f,p,tool)>=limit){s.nextDeployAt=now+8;return null;}double spacing=tool==16?12:tool==56?18:tool==20?7:0;if(spacing>0&&near(f,tool,p.position(),spacing)){s.nextDeployAt=now+6;return null;}if(tool==20&&!BrainOriented.safe(f,p,p.position(),tool,true)){s.nextDeployAt=now+4;return null;}
        s.nextDeployAt=now+cooldown;return emit(f,s,crouch(),focus,tool,new Action(ActionKind.DEPLOY,tool,tool==64?null:p.position(),null,"",-1,Math.atan2(focus.y()-p.position().y(),focus.x()-p.position().x()),0,0,0),focus,"deploy_"+tool+"_"+role);
    }
    private static long own(Perception f,Player p,int tool){return f.entities().stream().filter(e->e.alive()&&e.toolId()==tool&&e.ownerId()==p.id()).count();}
    private static boolean crowded(Perception f,Player p,int tool,double radius){return f.entities().stream().anyMatch(e->e.alive()&&e.toolId()==tool&&e.team()==p.team()&&distance(e.position(),p.position())<=radius);}
    private static boolean carried(Player p,int tool){return p.loadout().contains(tool)&&p.deployableStock().stream().anyMatch(stock->stock.tool()==tool&&stock.count()>0);}
    Intent classGadget(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        if(!p.grounded()||p.wade()||p.carriedEntityId()>=0||LifeCatalog.ZOMBIES.contains(p.classId()))return null;if(!s.cannonCells.isEmpty()&&now<s.cannonUntil)return cannon(f,p,s,now);if(now<s.nextDeployAt)return null;
        double gap=decision==null?Double.POSITIVE_INFINITY:distance(p.position(),decision.position());if(decision!=null&&decision.directive().equals("fortify")&&gap<=14){var cover=combat.prefabCover(f,p,s,decision.position(),now);if(cover!=null)return cover;}
        boolean holding=decision!=null&&decision.posture()==BotModeDecision.Posture.DEFEND&&gap<=decision.arrivalRadius()+6;var contact=s.contactPosition!=null&&now<s.contactUntil?s.contactPosition:null;Vector focus;double reach;String role;
        if(contact!=null){focus=contact;reach=distance(p.position(),contact);role="contact";}else if(holding){focus=decision.watchPosition();if(focus==null)focus=f.objectives().stream().filter(o->o.kind().equals("team_anchor")&&o.team()!=p.team()).map(Objective::position).findFirst().orElse(null);reach=0;role="post";}else if(decision!=null&&decision.objectivePriority()<.85&&!BotModePolicies.committed(decision)){focus=decision.position();reach=gap;role="front";}else return null;
        var heading=focus==null?ZERO:unit(focus.x()-p.position().x(),focus.y()-p.position().y());if(Math.hypot(heading.x(),heading.y())<.5)heading=unit(p.orientation().x(),p.orientation().y());if(Math.hypot(heading.x(),heading.y())<.5)return null;var look=focus==null?new Vector(p.eye().x()+heading.x()*8,p.eye().y()+heading.y()*8,p.eye().z()):focus;
        boolean atPost=holding&&gap<=decision.arrivalRadius()+1,quiet=contact==null&&now-p.lastDamageAt()>6;Vector placement=p.position();int tool;double cooldown;
        if(carried(p,16)&&(holding||reach<=18)&&own(f,p,16)<2&&!crowded(f,p,16,12)){tool=16;cooldown=24;}
        else if(carried(p,56)&&(holding||reach<=22)&&own(f,p,56)==0&&!crowded(f,p,56,18)){tool=56;cooldown=35;}
        else if(carried(p,20)&&own(f,p,20)<3&&(holding||reach<=(contact!=null?20:10))){var spot=mineSpot(f,p,heading);if(spot==null){s.nextDeployAt=now+4;return null;}tool=20;cooldown=14;placement=spot;}
        else if(carried(p,64)&&atPost&&quiet&&Math.hypot(p.velocity().x(),p.velocity().y())<.2){tool=64;cooldown=30;}
        else if(p.loadout().contains(29)&&now>=s.nextCannonAt&&p.blocks()>=12&&(holding&&quiet||contact!=null&&reach>=14))return startCannon(f,p,s,now,heading);else return null;
        s.nextDeployAt=now+cooldown;return emit(f,s,crouch(),look,tool,new Action(ActionKind.DEPLOY,tool,tool==64?null:placement,null,"",-1,Math.atan2(heading.y(),heading.x()),0,0,0),look,"deploy_"+tool+"_"+role);
    }
    Vector mineSpot(Perception f,Player p,Vector heading){double base=Math.atan2(heading.y(),heading.x());for(double[] choice:new double[][]{{0,4},{.5,4},{-.5,4},{0,2.5},{1,3.5},{-1,3.5}}){double x=p.position().x()+Math.cos(base+choice[0])*choice[1],y=p.position().y()+Math.sin(base+choice[0])*choice[1];var surface=world.surface((int)Math.floor(x),(int)Math.floor(y),p.position().z(),1,false);if(surface==null||Math.abs(surface.position().z()-p.position().z())>1.25)continue;var at=surface.position();if(f.entities().stream().anyMatch(e->e.alive()&&e.toolId()==20&&e.team()==p.team()&&distance(e.position(),at)<=7)||f.objectives().stream().anyMatch(o->!o.kind().equals("team_anchor")&&distance(o.position(),at)<3)||!BrainOriented.safe(f,p,at,20,true))continue;return at;}return null;}
    Intent startCannon(Perception f,Player p,BotBrainState s,double now,Vector heading){
        s.nextCannonAt=now+40;for(double along:new double[]{6.5,7.5,8.5}){var cells=new ArrayList<Cell>();for(double across:new double[]{-1,0,1}){double x=p.position().x()+heading.x()*along-heading.y()*across,y=p.position().y()+heading.y()*along+heading.x()*across;var surface=world.surface((int)Math.floor(x),(int)Math.floor(y),p.position().z(),1,false);if(surface==null||Math.abs(surface.position().z()-p.position().z())>.5){cells.clear();break;}var cell=new Cell(surface.x(),surface.y(),surface.supportZ()-1);if(!cells.contains(cell))cells.add(cell);}if(cells.size()<2)continue;var wall=new ArrayList<>(cells);for(var c:cells)wall.add(new Cell(c.x(),c.y(),c.z()-1));boolean invalid=false;
            for(var c:wall){var centre=new Vector(c.x()+.5,c.y()+.5,c.z()+.5);if(world.solid(c.x(),c.y(),c.z())||distance(p.eye(),new Vector(c.x()+.5,c.y()+.5,c.z()+1))<ProjectileSpec.TOOLS.get(29).radius()+1.3||f.players().stream().anyMatch(q->q.alive()&&q.spawned()&&distance(q.position(),centre)<2)||f.objectives().stream().anyMatch(o->!o.kind().equals("team_anchor")&&distance(o.position(),centre)<4)){invalid=true;break;}}
            if(invalid||cells.stream().anyMatch(c->!world.lineOfSight(p.eye(),new Vector(c.x()+.5,c.y()+.5,c.z()+.9))))continue;s.cannonCells=List.copyOf(wall);s.cannonOrigin=p.position();s.cannonUntil=now+10;s.cannonShotAt=0;return cannon(f,p,s,now);
        }return null;
    }
    Intent cannon(Perception f,Player p,BotBrainState s,double now){
        if(s.cannonShotAt>0&&!p.lastActionAccepted()&&p.lastActionKind().equals("oriented")&&p.lastActionAt()>=s.cannonShotAt-.6-1e-6){s.cannonCells=List.of();return null;}s.cannonCells=s.cannonCells.stream().filter(c->!world.solid(c.x(),c.y(),c.z())).toList();var ready=s.cannonCells.stream().filter(c->world.solid(c.x(),c.y(),c.z()+1)).findFirst().orElse(null);
        if(ready==null||p.blocks()<=0||s.cannonOrigin==null||distance(p.position(),s.cannonOrigin)>1){s.cannonCells=List.of();return null;}var aim=new Vector(ready.x()+.5,ready.y()+.5,ready.z()+1);Action action=Action.none();if(now>=s.cannonShotAt){s.cannonShotAt=now+.6;action=new Action(ActionKind.ORIENTED,29,aim,null,"",-1,0,0,0,0);}return emit(f,s,Movement.still(),aim,29,action,aim,"block_cannon_cover");
    }
    private static Movement crouch(){return new Movement(ZERO,false,true,false,false,Affordance.WALK,true,null,null,null,1);}
    private Intent emit(Perception f,BotBrainState s,Movement movement,Vector look,int tool,Action action,Vector goal,String role){return intents.emit(f,s,movement,look==null?null:new Look(look,false,-1,0,0,false),tool,action,Priority.ROUTINE,false,false,goal,List.of(),role,true);}
}
