// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original insertion-ordered uint16 registry, support callbacks and budgeted tick/touch/respawn driver. */
public final class EntityRegistry {
    public record Cell(int x,int y,int z) { }
    private record Bucket(long x,long y) { }
    public static final class Placement {
        public int state=1,playerId,face=4;
        public Vector color,velocity=new Vector(0,0,0);
        public String kind="crate";
        public EntityBehavior behavior;
        public double radius,fuse,yaw;
        public boolean wireVisible=true;
        public Cell supportCell;
    }
    private final Thread owner=Thread.currentThread();
    private final LinkedHashMap<Integer,MapEntity> entities=new LinkedHashMap<>();
    private final Map<Cell,SourceEntityIds> support=new HashMap<>();
    private int nextId,tickCursor;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Entity registry outside owner");}
    public int allocateId(){owned();for(int i=0;i<65536;i++){int id=nextId;nextId=(nextId+1)&65535;if(!entities.containsKey(id))return id;}throw new IllegalStateException("entity id space exhausted");}
    public MapEntity place(int type,double x,double y,double z){return place(type,x,y,z,new Placement());}
    public MapEntity place(int type,double x,double y,double z,Placement p){
        owned();Objects.requireNonNull(p);var e=new MapEntity(allocateId(),type,x,y,z);
        e.state=p.state;e.playerId=p.playerId;e.face=p.face;e.color=p.color;e.velocity=p.velocity;e.kind=p.kind;e.behavior=p.behavior;e.radius=p.radius;e.fuse=p.fuse;e.yaw=p.yaw;e.wireVisible=p.wireVisible;e.supportCell=p.supportCell;e.home=new Vector(x,y,z);
        entities.put(e.id(),e);if(e.supportCell!=null)support.computeIfAbsent(e.supportCell,key->new SourceEntityIds()).add(e.id());return e;
    }
    public MapEntity remove(int id){owned();var e=entities.remove(id);if(e!=null&&e.supportCell!=null){var ids=support.get(e.supportCell);if(ids!=null){ids.discard(e.id());if(ids.size()==0)support.remove(e.supportCell);}}return e;}
    public MapEntity get(Integer id){owned();return id==null?null:entities.get(id);}
    public List<MapEntity> all(){owned();return List.copyOf(entities.values());}
    public List<MapEntity> staticEntities(){owned();return entities.values().stream().filter(e->e.alive&&!e.kind.equals("projectile")&&e.wireVisible).toList();}
    public void clear(){owned();entities.clear();support.clear();nextId=0;}
    public boolean hasSupportAt(Cell cell){owned();var ids=support.get(cell);return ids!=null&&ids.size()>0;}
    public int supportRemoved(Cell cell,EntityContext context){
        owned();var ids=support.remove(cell);int handled=0;if(ids==null)return 0;
        for(int id:ids.values()){var e=entities.get(id);if(e==null||!e.alive||!cell.equals(e.supportCell))continue;e.supportCell=null;var b=e.behavior;if(b!=null){b.onSupportLost(e,context);handled++;}}return handled;
    }
    public List<MapEntity> dueRespawns(double now){owned();return entities.values().stream().filter(e->!e.alive&&e.respawnAt>0&&now>=e.respawnAt).toList();}
    public long tick(EntityContext context,Integer cap){
        owned();var tickers=entities.values().stream().filter(e->e.alive&&e.behavior!=null&&e.behavior.ticks()).toList();long skipped=0;
        if(cap!=null&&tickers.size()>cap){int n=tickers.size(),start=Math.floorMod(tickCursor,n);skipped=(long)n-cap;var ordered=new ArrayList<MapEntity>(n);for(int i=0;i<n;i++)ordered.add(tickers.get((start+i)%n));int end=cap<0?Math.max(0,n+cap):cap;tickers=ordered.subList(0,end);tickCursor=Math.floorMod((long)start+cap,n);}else tickCursor=0;
        for(var e:tickers){var b=e.behavior;if(e.alive&&b!=null)b.onTick(e,context.dt(),context);}
        var touchers=entities.values().stream().filter(e->e.alive&&e.behavior!=null&&e.behavior.touchRadius>0).toList();
        if(!touchers.isEmpty()&&!context.players().isEmpty()){
            var buckets=new HashMap<Bucket,List<MapEntity>>();double maxRadius=0;
            for(var e:touchers){buckets.computeIfAbsent(bucket(e.x,e.y),key->new ArrayList<>()).add(e);maxRadius=Math.max(maxRadius,e.behavior.touchRadius);}
            int range=Math.max(1,(int)Math.ceil(maxRadius/8));
            for(var player:context.players()){
                var position=player.position();var center=bucket(position.x(),position.y());
                for(int dx=-range;dx<=range;dx++)for(int dy=-range;dy<=range;dy++)for(var e:buckets.getOrDefault(new Bucket(center.x+dx,center.y+dy),List.of())){
                    if(!e.alive)continue;double radius=e.behavior.touchRadius;position=player.position();double x=position.x()-e.x,y=position.y()-e.y,z=position.z()-e.z;
                    if(x*x+y*y+z*z<=radius*radius)e.behavior.onTouch(e,player,context);
                }
            }
        }
        for(var e:dueRespawns(context.now())){e.alive=true;e.respawnAt=0;if(e.behavior instanceof EntityBehavior.Respawning b)b.onRespawn(e,context);if(context.create()!=null)context.create().accept(e);}return skipped;
    }
    private static Bucket bucket(double x,double y){return new Bucket((long)Math.floor(x/8),(long)Math.floor(y/8));}
    public void damageEntity(int id,double amount,PlayerLife source,EntityContext context){owned();var e=entities.get(id);if(e!=null&&e.alive&&e.behavior!=null&&e.behavior.takesDamage)e.behavior.onDamage(e,amount,source,context);}
}
