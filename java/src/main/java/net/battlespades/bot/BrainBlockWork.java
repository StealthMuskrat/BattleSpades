// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Retained objective excavation and repair cells; both source ray samplers retain their distinct reach contracts. */
final class BrainBlockWork {
    private final BotNavigationWorld world;private final BrainIntents intents;
    private static final Comparator<Cell> CELLS=Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z);
    private static final int[][] FACES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    BrainBlockWork(BotNavigationWorld world,BrainIntents intents){this.world=world;this.intents=intents;}
    private static Vector centre(Cell c){return new Vector(c.x()+.5,c.y()+.5,c.z()+.5);}
    private static Cell cell(Vector p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    private boolean solid(Cell c){return world.solid(c.x(),c.y(),c.z());}
    Cell coverCell(Player p,Vector contact){
        var eye=p.eye();var target=new Vector(contact.x(),contact.y(),contact.z()+eye.z()-p.position().z());double dx=target.x()-eye.x(),dy=target.y()-eye.y(),dz=target.z()-eye.z(),length=distance(eye,target),flat=Math.hypot(dx,dy);if(flat<3||flat>14||Math.abs(dz)>4||world.lineOfSight(eye,target))return null;Cell first=null;
        for(int index=1;index<=(int)(Math.min(length,8)/.25);index++){double fraction=index*.25/length;var c=cell(new Vector(eye.x()+dx*fraction,eye.y()+dy*fraction,eye.z()+dz*fraction));if(solid(c)){if(first==null){if(index*.25>4.5)return null;first=c;}}else if(first!=null)return first;}return null;
    }
    boolean nearSpawn(Perception f,Player p){return f.objectives().stream().anyMatch(o->o.kind().equals("team_anchor")&&o.team()==p.team()&&flat(p.position(),o.position())<12);}
    static boolean mineSafe(Player p,Cell c){var tool=BrainInventory.melee(p);var profile=tool==null?null:BotDigProfile.primary(tool).orElse(null);if(profile==null)return false;int x=(int)Math.floor(p.position().x()),y=(int)Math.floor(p.position().y());return profile.footprint(c).stream().allMatch(v->(v.x()!=x||v.y()!=y)&&v.z()<238);}
    record Hit(Cell cell,double distance){}
    Hit firstHit(Vector origin,Vector target,double limit){double length=distance(origin,target);if(length<=1e-6)return null;int count=Math.max(1,(int)Math.ceil(limit/.05));Cell last=null;for(int i=1;i<=count;i++){double travel=limit*i/count;var c=cell(new Vector(origin.x()+(target.x()-origin.x())/length*travel,origin.y()+(target.y()-origin.y())/length*travel,origin.z()+(target.z()-origin.z())/length*travel));if(c.equals(last))continue;last=c;if(solid(c))return new Hit(c,travel);}return null;}
    Cell firstOnRay(Vector origin,Vector target,double limit){double length=distance(origin,target);if(length<=1e-6)return null;int count=Math.max(1,(int)Math.ceil(Math.min(length,limit)/.05));double scale=Math.min(length,limit)/length;Cell last=null;for(int i=1;i<=count;i++){double f=scale*i/count;var c=cell(new Vector(origin.x()+(target.x()-origin.x())*f,origin.y()+(target.y()-origin.y())*f,origin.z()+(target.z()-origin.z())*f));if(c.equals(last))continue;last=c;if(solid(c))return c;}return null;}
    Cell mineCell(Player p,Vector eye,Set<Cell> skip){int x=(int)Math.floor(p.position().x()),y=(int)Math.floor(p.position().y()),z=(int)Math.rint(p.position().z()+2.25);var ring=new ArrayList<Cell>();for(int radius:new int[]{1,2})for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))==radius)ring.add(new Cell(dx,dy,0));int start=p.id()*3%ring.size();for(int i=0;i<ring.size();i++){var offset=ring.get((start+i)%ring.size());var hit=firstHit(eye,new Vector(x+offset.x()+.5,y+offset.y()+.5,z+.5),3.6);if(hit!=null&&!skip.contains(hit.cell())&&mineSafe(p,hit.cell()))return hit.cell();}return null;}
    Intent mine(Perception f,Player p,BotBrainState s,double now){
        var melee=BrainInventory.melee(p);if(melee==null||!p.grounded()||nearSpawn(f,p))return null;s.blockWorkSkip.entrySet().removeIf(e->e.getValue()<=now);var c=s.blockWorkCell;
        if(c!=null&&!s.blockWorkSkip.containsKey(c)&&solid(c)&&mineSafe(p,c)){if(now-s.blockWorkSince>4){s.blockWorkSkip.put(c,now+20);c=null;}}else c=null;
        if(c==null){c=mineCell(p,p.eye(),s.blockWorkSkip.keySet());s.blockWorkCell=c;s.blockWorkSince=now;if(c==null)return null;}
        var center=centre(c);BrainRoutingState.setGoal(s,null,p.position(),now);boolean swing=now+1e-9>=s.nextBreachAt;if(swing)s.nextBreachAt=now+BrainInventory.swingInterval(melee);
        return emit(f,s,new Movement(ZERO,false,true,false,false,Affordance.BREACH,true,null,null,null,1),center,melee,swing?new Action(ActionKind.MELEE,melee,center,null,"",-1,0,0,0,0):Action.none(),center,"diamond_mine_blocks");
    }
    Vector mineSite(Perception f,Player p,BotBrainState s,double now){
        if(s.mineSite!=null&&now<s.mineSiteUntil&&flat(p.position(),s.mineSite)>1.5)return s.mineSite;s.mineSiteIndex++;var toward=f.objectives().stream().filter(o->o.kind().equals("dia_dropoff")&&o.state()>0).map(Objective::position).min(Comparator.comparingDouble(v->flat(p.position(),v))).orElse(new Vector(256,256,p.position().z()));double heading=Math.atan2(toward.y()-p.position().y(),toward.x()-p.position().x()),fan=(p.id()*.618+s.mineSiteIndex*.37)%1;heading+=(fan-.5)*1.8;s.mineSite=new Vector(Math.clamp(p.position().x()+Math.cos(heading)*6,8,503),Math.clamp(p.position().y()+Math.sin(heading)*6,8,503),p.position().z());s.mineSiteUntil=now+12;return s.mineSite;
    }
    Intent work(Perception f,Player p,BotBrainState s,double now,BotModeDecision decision){
        boolean siege=decision.directive().equals("siege"),demolish=siege||decision.directive().equals("demolish");var base=f.objectives().stream().filter(o->siege?o.kind().equals("zombie_order")&&o.carrierId()==p.id()&&!o.cells().isEmpty():o.kind().equals("dem_base")&&(demolish?o.team()!=p.team():o.team()==p.team())).findFirst().orElse(null);if(base==null||!p.grounded())return null;s.blockWorkSkip.entrySet().removeIf(e->e.getValue()<=now);
        Integer tool;Cell c;double cooldown;
        if(demolish){tool=BrainInventory.melee(p);c=tool==null?null:demolishCell(p.eye(),base,s.blockWorkSkip.keySet());cooldown=BrainInventory.swingInterval(tool);}else{tool=5;if(!p.loadout().contains(5)||p.blocks()<=0)return null;c=repairCell(f,p,p.eye(),base,s.blockWorkSkip.keySet());cooldown=.5;}
        if(tool==null||c==null){s.blockWorkCell=null;return null;}if(!c.equals(s.blockWorkCell)){s.blockWorkCell=c;s.blockWorkSince=now;}else if(now-s.blockWorkSince>4){s.blockWorkSkip.put(c,now+20);s.blockWorkCell=null;return null;}
        String role=siege?decision.role():demolish?"demolition_dig_base":"demolition_repair_block";var center=centre(c);BrainRoutingState.setGoal(s,null,p.position(),now);
        if(now+1e-9<s.nextBlockWorkAt)return emit(f,s,new Movement(ZERO,false,demolish,false,false,Affordance.WALK,true,null,null,null,1),center,tool,Action.none(),center,role);
        s.nextBlockWorkAt=now+cooldown;if(demolish)return emit(f,s,new Movement(ZERO,false,true,false,false,Affordance.BREACH,true,null,null,null,1),center,tool,new Action(ActionKind.MELEE,tool,center,null,"",-1,0,0,0,0),center,role);
        var target=new Vector(c.x(),c.y(),c.z());return emit(f,s,Movement.still(),target,tool,new Action(ActionKind.BUILD,tool,target,null,"",-1,0,0,0,0),center,role);
    }
    Cell demolishCell(Vector eye,Objective base,Set<Cell> skip){var candidates=new HashSet<>(base.cells());var bounds=base.bounds();if(bounds.size()==6){int ex=(int)Math.floor(eye.x()),ey=(int)Math.floor(eye.y()),ez=(int)Math.floor(eye.z()),radius=3;for(int x=Math.max(bounds.get(0),ex-radius);x<=Math.min(bounds.get(1),ex+radius);x++)for(int y=Math.max(bounds.get(2),ey-radius);y<=Math.min(bounds.get(3),ey+radius);y++)for(int z=Math.max(bounds.get(4),ez-radius);z<=Math.min(bounds.get(5),ez+radius);z++)candidates.add(new Cell(x,y,z));}
        var ordered=candidates.stream().filter(c->distance(eye,centre(c))<=2.85&&!skip.contains(c)).sorted(Comparator.comparingDouble((Cell c)->distance(eye,centre(c))).thenComparing(CELLS)).limit(48).toList();for(var c:ordered){if(!solid(c))continue;var hit=firstOnRay(eye,centre(c),2.85);if(hit!=null&&!skip.contains(hit)&&distance(eye,centre(hit))<=2.85)return hit;}return null;
    }
    Cell repairCell(Perception f,Player p,Vector eye,Objective base,Set<Cell> skip){var ordered=base.repairCells().stream().sorted(Comparator.comparingDouble((Cell c)->distance(eye,centre(c))).thenComparing(CELLS)).toList();for(var c:ordered){if(distance(eye,centre(c))>4.5||skip.contains(c)||solid(c))continue;boolean support=false;for(var d:FACES)support|=world.solid(c.x()+d[0],c.y()+d[1],c.z()+d[2]);if(!support)continue;var center=centre(c);if(f.players().stream().anyMatch(q->q.alive()&&q.spawned()&&Math.abs(q.position().x()-center.x())<.9&&Math.abs(q.position().y()-center.y())<.9&&center.z()-q.position().z()>=-.5&&center.z()-q.position().z()<=3.2)||!world.lineOfSight(eye,center))continue;return c;}return null;}
    private Intent emit(Perception f,BotBrainState s,Movement movement,Vector look,int tool,Action action,Vector goal,String role){return intents.emit(f,s,movement,new Look(look,false,-1,0,0,false),tool,action,Priority.ROUTINE,false,false,goal,List.of(),role,true);}
}
