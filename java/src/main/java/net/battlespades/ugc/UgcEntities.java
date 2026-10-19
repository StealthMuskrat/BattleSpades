// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.util.*;
import java.util.function.*;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.session.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;

/** Creator flare, projectile, blast and entity ownership over the shared production engines. */
public final class UgcEntities implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private final EntityRegistry registry;
    private final LifeRuntime lives;
    private final UgcConstruction construction;
    private final ApplicationHost host;
    private final LifeRuntime.Clock clock;
    private final IntSupplier frame;
    private final Runnable dirty;
    private final HostedProjectileRuntime projectiles;
    private final MutableWorld.RemovalLease removals;
    private final BlastTerrain terrain;
    private final BlastRuntime blasts;
    private final BooleanSupplier capacity;
    private boolean closed;

    public UgcEntities(EntityRegistry registry,LifeRuntime lives,UgcConstruction construction,
            ApplicationHost host,MapMetadata metadata,LifeRuntime.Clock clock,IntSupplier frame,Runnable dirty,BooleanSupplier capacity) {
        this.registry=Objects.requireNonNull(registry);this.lives=Objects.requireNonNull(lives);
        this.construction=Objects.requireNonNull(construction);this.host=Objects.requireNonNull(host);
        this.clock=Objects.requireNonNull(clock);this.frame=Objects.requireNonNull(frame);this.dirty=Objects.requireNonNull(dirty);this.capacity=Objects.requireNonNull(capacity);
        terrain=new BlastTerrain(lives.world(),construction.terrain(),new BlastTerrain.Delivery(){
            public List<BlastTerrain.Recipient> recipients(){return host.transitionSessions().stream().filter(s->s.playerId().isPresent()).map(s->new BlastTerrain.Recipient(s.playerId().orElseThrow(),s.inGame(),s.knownEntities())).toList();}
            public void reliable(BlastTerrain.Recipient r,GamePacket packet){host.sendPlayer(r.id(),packet);}
            public void reliable(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}
        });
        blasts=new BlastRuntime(lives,registry,terrain,clock::monotonic,new BlastRuntime.Domain(){
            public boolean buildDamage(){return true;}public boolean friendlyFire(){return false;}public boolean hardcore(PlayerLife p){return false;}
            // Creator sessions have no bots, competitive scoring, or achievement scopes.
            public void stimulus(Vector at,int source,int team,double radius,double lifetime){}
            public Object begin(PlayerLife p,int kill,Vector at){return null;}public void end(Object scope){}public void scopeFault(String phase){}
        },this::context);
        projectiles=new HostedProjectileRuntime(new HostedProjectileRuntime.Domain(){
            public MutableWorld world(){return lives.world();}public LifeRuntime lives(){return lives;}public EntityRegistry registry(){return registry;}
            public BlastTerrain blastTerrain(){return terrain;}public BlastRuntime blasts(){return blasts;}
            public double wall(){return clock.wall();}public double monotonic(){return clock.monotonic();}public int frameLoop(){return frame.getAsInt();}
            public boolean authorized(PlayerLife p,int tool){return (tool==47||tool==48)&&construction.authorized(p,tool)&&(tool!=48||capacityAvailable())&&projectiles.engine().projectiles().size()<2048;}
            public boolean infiniteBlocks(PlayerLife p){return true;}
            public void create(MapEntity e){UgcEntities.this.create(e);}public void destroy(int id){UgcEntities.this.destroy(id);}
            public void known(int id,GamePacket packet){for(var s:host.transitionSessions())if(s.inGame()&&s.knownEntities().contains(id))s.gameplay(packet,Delivery.RELIABLE);}
            public void sweep(){} // No shields can be selected by builder class 13.
            public void impact(Vector at,PlayerLife p,boolean chemical){throw new IllegalStateException("Builder cannot equip chemical or molotov tools");}
            public void breakDisguise(PlayerLife p){p.disguise().clear();}
            public void report(PlayerLife p,String kind,Map<String,Double> detail){p.shotStatistics().reject(p,kind,new LinkedHashMap<String,Object>(detail),event->{});}
        },host);
        restore(metadata);
        removals=lives.world().observeRemovals(cell->{for(var e:registry.all())if(e.alive&&e.kind.equals("map_flare")&&(int)e.x==cell.x()&&(int)e.y==cell.y()&&(int)e.z==cell.z()){registry.remove(e.id());e.alive=false;destroy(e.id());}});
    }
    private boolean capacityAvailable(){return capacity.getAsBoolean();}
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Creator entities outside active owner");}
    public HostedProjectileRuntime projectiles(){owned();return projectiles;}
    public EntityRegistry registry(){owned();return registry;}
    public EntityContext context(){owned();return new EntityContext(1./60,clock.wall(),lives.players(),registry,this::create,this::destroy,null,new PickupWorld(lives.world()),null);}
    private void create(MapEntity e){host.broadcastCreateEntity(e);if(e.type==13)dirty.run();}
    private void destroy(int id){var e=registry.get(id);host.broadcastDestroyEntity(id);if(e!=null&&e.type==13)dirty.run();}
    private void restore(MapMetadata metadata){
        var cells=new LinkedHashMap<EntityRegistry.Cell,Integer>();
        for(var e:metadata.entities())if(e.type()==13&&e.color()!=null){var p=e.position();cells.put(new EntityRegistry.Cell((int)p.x(),(int)p.y(),(int)p.z()+lives.world().sourceZShift()),e.color());}
        for(var marker:lives.world().markers()){Integer color=metadata.staticLightColors().get(marker.family());if(color!=null)cells.putIfAbsent(new EntityRegistry.Cell(marker.x(),marker.y(),marker.z()),color);}
        if(cells.size()>2048)throw new IllegalArgumentException("Creator light limit is 2048");
        for(var row:cells.entrySet()){
            var c=row.getKey();if(c.x()<0||c.x()>=512||c.y()<0||c.y()>=512||c.z()<1||c.z()>238)continue;
            var p=new EntityRegistry.Placement();p.state=2;p.kind=lives.world().solid(c.x(),c.y(),c.z())?"map_flare":"flare_block";
            p.color=new Vector(row.getValue()>>16&255,row.getValue()>>8&255,row.getValue()&255);
            if(p.kind.equals("map_flare"))lives.world().restoreStaticLight(c.x(),c.y(),c.z(),row.getValue());else p.behavior=new FlareBlockBehavior();
            registry.place(13,c.x(),c.y(),c.z(),p);
        }
    }
    public boolean packet(PlayerLife p,GamePacket packet){owned();if(packet instanceof LivePlacement v&&v.id()==104){flare(p,v);return true;}return projectiles.packet(p,packet);}
    public boolean flare(PlayerLife p,LivePlacement packet){
        owned();int x=packet.x(),y=packet.y(),z=packet.z();
        if(!construction.authorized(p,22)||x<0||x>=512||y<0||y>=512||z<1||z>238||lives.world().solid(x,y,z)||registry.all().stream().filter(e->e.type==13).count()>=2048)return false;
        var cell=new EntityRegistry.Cell(x,y,z);
        if(CombatGeometry.distance(p.position(),new Vector(x,y,z))>10||registry.all().stream().anyMatch(e->e.alive&&!e.kind.equals("projectile")&&(int)e.x==x&&(int)e.y==y&&(int)e.z==z))return false;
        if(!FlareBlockBehavior.supported(registry,cell,c->lives.world().solid(c.x(),c.y(),c.z()),null))return false;
        if(!p.bot()&&!CombatGeometry.cellVisible(lives.world()::solid,ShotValidation.references(ShotValidation.view(p),packet.loop()).eyes(),new CombatGeometry.Cell(x,y,z),Set.of()))return false;
        for(var target:lives.players())if(target.alive()&&target.spawned()){var at=target.position();if(Math.abs(x-(int)at.x())<=1&&Math.abs(y-(int)at.y())<=1&&z>=(int)at.z()&&z<=(int)at.z()+2)return false;}
        if(!construction.placementCadence(p))return false;
        var placement=new EntityRegistry.Placement();placement.playerId=p.id();placement.state=2;placement.kind="flare_block";placement.color=new Vector(p.color()>>16&255,p.color()>>8&255,p.color()&255);placement.behavior=new FlareBlockBehavior();
        create(registry.place(13,x,y,z,placement));return true;
    }
    public List<GamePacket> reveal(){owned();return registry.all().stream().filter(e->e.alive&&e.wireVisible&&!e.kind.equals("ugc_marker")).map(e->(GamePacket)new CreateEntity(e.toWireEntity())).toList();}
    public List<EntityRecord> moving(){owned();return registry.all().stream().filter(e->e.alive&&e.kind.equals("projectile")).map(MapEntity::toWireEntity).toList();}
    public void tick(){owned();projectiles.tick();registry.tick(context(),8192);}
    public void forget(PlayerLife p){owned();projectiles.forget(p.id());}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Creator entities outside owner");if(closed)return;removals.close();projectiles.clear();closed=true;}
}
