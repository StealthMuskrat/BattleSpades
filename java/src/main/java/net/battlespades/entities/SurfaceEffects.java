// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.session.*;
import net.battlespades.world.BlockDamageModel;

/** Server-owned Molotov/fire clusters and Chemical Bomb goo, with bounded emitters and fractional HP cadence. */
public final class SurfaceEffects {
    private static final class Cluster {int left=5,members;}
    private static final class Patch {
        final int id,owner;final EntityRegistry.Cell cell;final boolean goo;final Cluster cluster;final double expires;
        double blockDue,charDue,spreadDue;boolean pending;
        Patch(int id,int owner,EntityRegistry.Cell cell,boolean goo,Cluster cluster,double now,double life,boolean pending){this.id=id;this.owner=owner;this.cell=cell;this.goo=goo;this.cluster=cluster;expires=now+life;blockDue=now+.4;charDue=now;spreadDue=now+.5;this.pending=pending;}
    }
    private static final class Contact {int owner;double expires,due,fraction;Contact(int owner,double expires,double due){this.owner=owner;this.expires=expires;this.due=due;}}
    private final StandardMatchDomain match;private final HostedEntityRuntime entities;private final boolean friendlyFire;private final SourceRandom random;
    private final Map<Integer,Patch> fire=new LinkedHashMap<>(),goo=new LinkedHashMap<>();private final Map<EntityRegistry.Cell,Patch> fireCells=new HashMap<>(),gooCells=new HashMap<>();
    private final Map<Integer,Contact> burns=new LinkedHashMap<>(),contacts=new LinkedHashMap<>();
    public SurfaceEffects(StandardMatchDomain match,HostedEntityRuntime entities,boolean friendlyFire,SourceRandom random){this.match=Objects.requireNonNull(match);this.entities=Objects.requireNonNull(entities);this.friendlyFire=friendlyFire;this.random=Objects.requireNonNull(random);}
    private PlayerLife player(int id){return entities.devices().player(id);}
    private boolean solid(EntityRegistry.Cell c){return match.world().solid(c.x(),c.y(),c.z());}
    private static boolean inBounds(EntityRegistry.Cell c){return c.x()>=0&&c.x()<512&&c.y()>=0&&c.y()<512&&c.z()>=0&&c.z()<=238;}
    private Vector anchor(EntityRegistry.Cell c){int x=c.x(),y=c.y(),z=c.z();int[][] steps={{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1}};double[][] points={{x+.5,y+.5,z-.01},{x+1.01,y+.5,z+.5},{x-.01,y+.5,z+.5},{x+.5,y+1.01,z+.5},{x+.5,y-.01,z+.5},{x+.5,y+.5,z+1.01}};for(int i=0;i<steps.length;i++){var d=steps[i];if(!match.world().solid(x+d[0],y+d[1],z+d[2]))return new Vector(points[i][0],points[i][1],points[i][2]);}return null;}
    public boolean onFire(PlayerLife p){return burns.containsKey(p.id());}public boolean touchingGoo(PlayerLife p){return contacts.containsKey(p.id());}
    public void impact(Vector at,PlayerLife owner,boolean chemical){
        if(owner==null)return;var candidates=new ArrayList<EntityRegistry.Cell>();int x=(int)Math.floor(at.x()),y=(int)Math.floor(at.y()),z=(int)Math.floor(at.z());
        for(int dx=-3;dx<=3;dx++)for(int dy=-3;dy<=3;dy++)for(int dz=-3;dz<=3;dz++){
            if(chemical?dx*dx+dy*dy+dz*dz>=12.25:Math.abs(dx)>2||Math.abs(dy)>2||Math.abs(dz)>1)continue;
            var c=new EntityRegistry.Cell(x+dx,y+dy,z+dz);if(!inBounds(c)||!solid(c)||anchor(c)==null)continue;
            if(!chemical&&distance(c,at,false)>9)continue;if(chemical&&gooCells.containsKey(c))continue;candidates.add(c);
        }
        candidates.sort(Comparator.<EntityRegistry.Cell>comparingDouble(c->distance(c,at,chemical)).thenComparingInt(EntityRegistry.Cell::x).thenComparingInt(EntityRegistry.Cell::y).thenComparingInt(EntityRegistry.Cell::z));
        var cluster=chemical?null:new Cluster();int cap=chemical?48:5;for(var c:candidates.subList(0,Math.min(cap,candidates.size())))add(c,owner,chemical,cluster,match.wall(),4,true,true);
    }
    private static double distance(EntityRegistry.Cell c,Vector at,boolean chemical){double dx=c.x()+.5-at.x(),dy=c.y()+.5-at.y(),dz=c.z()+(chemical?.5:0)-at.z();return dx*dx+dy*dy+dz*dz;}
    private void add(EntityRegistry.Cell c,PlayerLife owner,boolean chemical,Cluster cluster,double now,double life,boolean replace,boolean pending){
        var cells=chemical?gooCells:fireCells;var patches=chemical?goo:fire;if(cells.containsKey(c)||!inBounds(c)||!solid(c))return;
        if(patches.size()>=(chemical?192:96)){if(!replace)return;remove(patches.values().stream().min(Comparator.<Patch>comparingDouble(p->p.expires).thenComparingInt(p->p.id)).orElseThrow());}
        var at=anchor(c);if(at==null)at=new Vector(c.x()+.5,c.y()+.5,c.z()-.01);
        var spec=new EntityRegistry.Placement();spec.state=owner.team();spec.playerId=owner.id();spec.face=4;spec.fuse=Math.max(.05,life);spec.kind=chemical?"blockgoo":"blockfire";
        var e=entities.registry().place(chemical?31:28,at.x(),at.y(),at.z(),spec);entities.create(e);var p=new Patch(e.id(),owner.id(),c,chemical,cluster,now,spec.fuse,pending);patches.put(p.id,p);cells.put(c,p);if(cluster!=null)cluster.members++;
    }
    private void remove(Patch p){(p.goo?goo:fire).remove(p.id);(p.goo?gooCells:fireCells).remove(p.cell);if(p.cluster!=null)p.cluster.members--;if(entities.registry().remove(p.id)!=null)entities.destroy(p.id);}
    private boolean friendly(PlayerLife target,int owner){var source=player(owner);return target.id()!=owner&&!friendlyFire&&source!=null&&source.team()==target.team();}
    private static double feet(PlayerLife p){return (p.motion().snapshot().movement()&32)!=0&&!p.body().state().wade()?1.35:2.25;}
    public void ignite(PlayerLife p,int owner,double now){if(!p.alive()||!p.spawned()||friendly(p,owner))return;var state=burns.get(p.id());if(state==null)burns.put(p.id(),new Contact(owner,now+10,now+.3));else{state.owner=owner;state.expires=Math.max(state.expires,now+10);}}
    public void extinguish(int id){burns.remove(id);contacts.remove(id);}
    public void forget(int id){for(var p:new ArrayList<>(fire.values()))if(p.owner==id)remove(p);for(var p:new ArrayList<>(goo.values()))if(p.owner==id)remove(p);burns.entrySet().removeIf(e->e.getKey()==id||e.getValue().owner==id);contacts.entrySet().removeIf(e->e.getKey()==id||e.getValue().owner==id);}
    public void clear(){fire.clear();goo.clear();fireCells.clear();gooCells.clear();burns.clear();contacts.clear();}
    public void fireTick(){double now=match.wall();patches(false,now);for(var entry:new ArrayList<>(burns.entrySet())){var p=player(entry.getKey());var state=entry.getValue();if(p==null||!p.alive()||!p.spawned()||p.body().state().wade()||now>=state.expires){burns.remove(entry.getKey());continue;}damage(p,state,now,25);}}
    public void gooTick(){double now=match.wall();patches(true,now);for(var p:match.lives().players()){Integer owner=p.alive()&&p.spawned()?touchOwner(p):null;if(owner!=null&&friendly(p,owner))owner=null;if(owner==null){contacts.remove(p.id());continue;}var state=contacts.get(p.id());if(state==null){state=new Contact(owner,Double.POSITIVE_INFINITY,now);contacts.put(p.id(),state);}state.owner=owner;damage(p,state,now,31);if(!p.alive())contacts.remove(p.id());}}
    private void damage(PlayerLife p,Contact state,double now,int kill){while(now>=state.due&&p.alive()){state.due+=.3;state.fraction+=2.5;int whole=(int)state.fraction;state.fraction-=whole;if(whole>0)match.lives().damage(p.id(),whole,player(state.owner)==null?null:state.owner,kill,3);}}
    private Integer touchOwner(PlayerLife p){var at=p.position();for(int x=(int)Math.floor(at.x()-.55);x<=(int)Math.floor(at.x()+.55);x++)for(int y=(int)Math.floor(at.y()-.55);y<=(int)Math.floor(at.y()+.55);y++)for(int z=(int)Math.floor(at.z()-.5);z<=(int)Math.floor(at.z()+feet(p)+.1);z++){var patch=gooCells.get(new EntityRegistry.Cell(x,y,z));if(patch!=null)return patch.owner;}return null;}
    private void patches(boolean chemical,double now){
        for(var p:new ArrayList<>((chemical?goo:fire).values())){
            var owner=player(p.owner);if(now>=p.expires){remove(p);continue;}
            if(!solid(p.cell)){var below=new EntityRegistry.Cell(p.cell.x(),p.cell.y(),p.cell.z()+1);if(chemical)remove(p);if(owner!=null&&inBounds(below))add(below,owner,chemical,p.cluster,now,p.expires-now,chemical,false);if(!chemical)remove(p);continue;}
            var e=entities.registry().get(p.id);if(e!=null)e.fuse=Math.max(0,p.expires-now);
            if(!chemical&&now>=p.charDue){p.charDue=now+.3;for(var target:match.lives().players()){if(!target.alive()||!target.spawned())continue;var at=target.position();double z=Math.min(Math.max(p.cell.z()+.5,at.z()),at.z()+feet(target)),dx=at.x()-(p.cell.x()+.5),dy=at.y()-(p.cell.y()+.5),dz=z-(p.cell.z()+.5);if(dx*dx+dy*dy+dz*dz<=9)ignite(target,p.owner,now);}}
            if(now>=p.blockDue){p.blockDue=now+.4;if(owner!=null)entities.terrain().gun(owner,new BlockDamageModel.Cell(p.cell.x(),p.cell.y(),p.cell.z()),.7,chemical?43:25,p.id);}
            if(!chemical&&p.pending&&p.cluster.left>0&&now>=p.spreadDue){p.pending=false;p.cluster.left--;if(owner!=null)spread(p,owner,now);}
        }
    }
    private void spread(Patch p,PlayerLife owner,double now){var choices=new ArrayList<EntityRegistry.Cell>();for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++){if(dx==0&&dy==0||dx*dx+dy*dy>4)continue;var c=new EntityRegistry.Cell(p.cell.x()+dx,p.cell.y()+dy,p.cell.z());if(inBounds(c)&&!fireCells.containsKey(c)&&solid(c)&&anchor(c)!=null)choices.add(c);}if(!choices.isEmpty()&&random.random()<=.3)add(random.choice(choices),owner,false,p.cluster,now,4,false,true);}
}
