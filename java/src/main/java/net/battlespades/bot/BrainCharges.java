// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.entities.ProjectileSpec;
import net.battlespades.catalog.SourceCatalog;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Owned charges retain retreat, visible trap triggers, live site safety and finite placement backoff. */
final class BrainCharges {
    private static final int[][] NORMALS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    private final BotNavigationWorld world;private final BrainIntents intents;private final BrainCombat.Navigation navigation;private final BrainBlockWork blocks;
    BrainCharges(BotNavigationWorld world,BrainIntents intents,BrainCombat.Navigation navigation,BrainBlockWork blocks){this.world=world;this.intents=intents;this.navigation=navigation;this.blocks=blocks;}
    Intent own(Perception f,Player p,BotBrainState s,double now,boolean fighting){
        boolean detonator=p.loadout().contains(59);var charges=f.entities().stream().filter(e->e.alive()&&e.ownerId()==p.id()&&(e.toolId()==21||e.toolId()==59)&&e.blastRadius()>0&&(detonator||e.toolId()!=59)).toList();if(charges.isEmpty()){s.chargeRetreat=null;s.chargeTrap=false;return null;}s.chargeAttempts=0;
        var nearest=charges.stream().min(Comparator.comparingDouble(e->distance(e.position(),p.position())-e.blastRadius())).orElseThrow();double margin=distance(nearest.position(),p.position())-nearest.blastRadius();
        if(margin<2.5){if(s.chargeRetreat==null||s.chargeEntity!=nearest.id()||distance(p.position(),s.chargeRetreat)<1.5){s.chargeEntity=nearest.id();s.chargeRetreat=retreat(p,nearest);}return navigation.navigate(f,p,s,new BotGoal(List.of("own_charge",nearest.id()),s.chargeRetreat,"clear_own_charge",1,true,null),now);}
        s.chargeRetreat=null;var remote=charges.stream().filter(e->e.toolId()==59).toList();boolean friends=f.players().stream().anyMatch(q->q.alive()&&q.spawned()&&q.team()==p.team()&&q.id()!=p.id()&&remote.stream().anyMatch(e->distance(q.position(),e.position())<=e.blastRadius()+1.5));
        if(s.chargeTrap){boolean sprung=f.players().stream().anyMatch(q->q.alive()&&q.spawned()&&q.team()!=p.team()&&distance(q.eye(),p.eye())<=160&&world.lineOfSight(p.eye(),q.eye())&&remote.stream().anyMatch(e->distance(q.position(),e.position())<=e.blastRadius()-2.5));if(!sprung||friends||now<s.nextChargeAt)return null;}
        else if(fighting||!p.grounded())return null;
        if(!remote.isEmpty()&&!friends&&now>=s.nextChargeAt){s.nextChargeAt=now+1;var at=remote.getFirst().position();return emit(f,s,Movement.still(),at,59,new Action(ActionKind.DEPLOY,59,null,null,"detonate",-1,0,0,0,0),at,"detonate_c4");}
        if(margin<8.5&&!(!remote.isEmpty()&&friends))return emit(f,s,Movement.still(),nearest.position(),BrainInventory.weapon(p,null),Action.none(),nearest.position(),"await_own_charge");return null;
    }
    Vector retreat(Player p,Entity charge){var away=unit(p.position().x()-charge.position().x(),p.position().y()-charge.position().y());if(Math.hypot(away.x(),away.y())<.5){double angle=(p.id()*2.39996)%Math.TAU;away=new Vector(Math.cos(angle),Math.sin(angle),0);}double reach=charge.blastRadius()+4.5,base=Math.atan2(away.y(),away.x());for(double turn:new double[]{0,.6,-.6,1.2,-1.2,1.9,-1.9}){double x=charge.position().x()+Math.cos(base+turn)*reach,y=charge.position().y()+Math.sin(base+turn)*reach;var surface=world.surface((int)Math.floor(x),(int)Math.floor(y),p.position().z(),4,false);if(surface!=null)return surface.position();}return new Vector(charge.position().x()+away.x()*reach,charge.position().y()+away.y()*reach,p.position().z());}
    record Target(Cell cell,int face,String purpose){}
    private record Trap(Target target,Intent intent){}
    Intent plant(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        if(now<s.nextChargeAt||!p.grounded()||p.wade()||p.carriedEntityId()>=0)return null;Integer tool=null;for(int item:new int[]{21,59})if(p.loadout().contains(item)&&p.deployableStock().stream().anyMatch(stock->stock.tool()==item&&stock.count()>0)){tool=item;break;}if(tool==null)return null;
        var target=target(f,p,s,now,decision);if(target==null&&tool==59){var trap=trap(f,p,s,now,decision);if(trap!=null){if(trap.intent()!=null)return trap.intent();target=trap.target();}}if(target==null)return null;
        var center=centre(target.cell());if(!safe(f,p,center,tool)){s.nextChargeAt=now+2;return null;}s.chargeAttempts++;s.nextChargeAt=now+Math.min(30,3*s.chargeAttempts);s.chargeTrap=target.purpose().equals("trap");s.trapCell=null;
        return emit(f,s,new Movement(ZERO,false,true,false,false,Affordance.WALK,true,null,null,null,1),center,tool,new Action(ActionKind.DEPLOY,tool,center,null,"",target.face(),0,0,0,0),center,"plant_"+(tool==21?"dynamite":"c4")+"_"+target.purpose());
    }
    private Trap trap(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        if(decision==null||decision.posture()!=BotModeDecision.Posture.DEFEND||s.contactPosition!=null&&now<s.contactUntil||now-p.lastDamageAt()<=6){s.trapCell=null;return null;}var post=decision.position();
        if(s.trapCell==null){
            if(now<s.nextTrapAt||distance(p.position(),post)>decision.arrivalRadius()+2)return null;s.nextTrapAt=now+30;var threat=decision.watchPosition();if(threat==null)threat=f.objectives().stream().filter(o->o.kind().equals("team_anchor")&&o.team()!=p.team()).map(Objective::position).findFirst().orElse(null);if(threat==null)return null;var heading=unit(threat.x()-post.x(),threat.y()-post.y());if(Math.hypot(heading.x(),heading.y())<.5)return null;double clear=11,height=p.eye().z()-p.position().z();
            for(double along:new double[]{clear+1,clear,clear+2.5}){var spot=world.surface((int)Math.floor(post.x()+heading.x()*along),(int)Math.floor(post.y()+heading.y()*along),post.z(),3,false);var stand=world.surface((int)Math.floor(post.x()+heading.x()*(along-2.5)),(int)Math.floor(post.y()+heading.y()*(along-2.5)),post.z(),3,false);if(spot==null||stand==null)continue;var center=new Vector(spot.x()+.5,spot.y()+.5,spot.supportZ()+.5);if(distance(spot.position(),post)<clear||distance(stand.position(),center)>4.5||f.objectives().stream().anyMatch(o->!o.kind().equals("team_anchor")&&distance(o.position(),spot.position())<3)||!world.lineOfSight(new Vector(post.x(),post.y(),post.z()+height),new Vector(spot.position().x(),spot.position().y(),spot.position().z()+height)))continue;s.trapCell=new Cell(spot.x(),spot.y(),spot.supportZ());s.trapStand=stand.position();s.trapUntil=now+20;break;}
            if(s.trapCell==null)return null;
        }
        var cell=s.trapCell;var center=centre(cell);if(now>=s.trapUntil||!world.solid(cell.x(),cell.y(),cell.z())||world.solid(cell.x(),cell.y(),cell.z()-1)){s.trapCell=null;return null;}if(distance(p.position(),center)<=4.5)return new Trap(new Target(cell,4,"trap"),null);
        return new Trap(null,navigation.navigate(f,p,s,new BotGoal(List.of("c4_trap",List.of(cell.x(),cell.y(),cell.z())),s.trapStand,"lay_c4_trap",1,false,null),now));
    }
    static boolean safe(Perception f,Player p,Vector at,int tool){double radius=Math.max(tool==21?5:tool==59?8:0,Optional.ofNullable(ProjectileSpec.TOOLS.get(tool)).map(ProjectileSpec::radius).orElse(0.));if(radius<=0)return false;if(f.players().stream().anyMatch(q->q.alive()&&q.spawned()&&q.team()==p.team()&&q.id()!=p.id()&&distance(q.position(),at)<=radius+1.5))return false;return f.entities().stream().noneMatch(e->e.alive()&&e.hazardous()&&(e.team()==p.team()||e.kind().equals("projectile"))&&distance(e.position(),at)<=radius+Math.max(0,e.blastRadius())+1);}
    Target target(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        Cell cell=null;String purpose="";
        if(decision!=null&&decision.directive().equals("demolish")){var base=f.objectives().stream().filter(o->o.kind().equals("dem_base")&&o.team()!=p.team()).findFirst().orElse(null);if(base!=null){var hit=blocks.demolishCell(p.eye(),base,s.blockWorkSkip.keySet());if(hit!=null){var bounds=base.bounds();boolean inside=bounds.size()==6&&hit.x()>=bounds.get(0)-1&&hit.x()<=bounds.get(1)+1&&hit.y()>=bounds.get(2)-1&&hit.y()<=bounds.get(3)+1&&hit.z()>=bounds.get(4)-1&&hit.z()<=bounds.get(5)+1;if(base.cells().contains(hit)||inside){cell=hit;purpose="objective";}}}}
        var contact=s.contactPosition!=null&&now<s.contactUntil?s.contactPosition:null;if(cell==null&&contact!=null){cell=blocks.coverCell(p,contact);purpose="cover";}
        double gap=decision==null?Double.POSITIVE_INFINITY:distance(p.position(),decision.position());if(cell==null&&decision!=null&&decision.posture()==BotModeDecision.Posture.ASSAULT&&gap<=16){var hit=blocks.firstOnRay(p.eye(),new Vector(decision.position().x(),decision.position().y(),p.eye().z()),4.5);if(hit!=null&&world.blockHealth(hit.x(),hit.y(),hit.z())!=SourceCatalog.load("bot-foundations").getDouble("map_block_health")&&distance(decision.position(),centre(hit))<=12){cell=hit;purpose="fortification";}}
        if(cell==null)return null;var center=centre(cell);if(distance(p.position(),center)>4.5)return null;var outward=new Vector(p.eye().x()-center.x(),p.eye().y()-center.y(),p.eye().z()-center.z());var faces=new ArrayList<Integer>();for(int i=0;i<NORMALS.length;i++)if(!world.solid(cell.x()+NORMALS[i][0],cell.y()+NORMALS[i][1],cell.z()+NORMALS[i][2]))faces.add(i);faces.sort(Comparator.comparingDouble(i->-(NORMALS[i][0]*outward.x()+NORMALS[i][1]*outward.y()+NORMALS[i][2]*outward.z())));return faces.isEmpty()?null:new Target(cell,faces.getFirst(),purpose);
    }
    private static Vector centre(Cell c){return new Vector(c.x()+.5,c.y()+.5,c.z()+.5);}
    private Intent emit(Perception f,BotBrainState s,Movement movement,Vector look,int tool,Action action,Vector goal,String role){return intents.emit(f,s,movement,new Look(look,false,-1,0,0,false),tool,action,Priority.ROUTINE,false,false,goal,List.of(),role,true);}
}
