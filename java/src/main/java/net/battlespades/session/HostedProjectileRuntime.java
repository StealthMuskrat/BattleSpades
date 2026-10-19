// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.entities.ProjectileEngine.Projectile;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;

/** Match-owned oriented actions, projectile visuals, attachment transitions and impact commits. */
public final class HostedProjectileRuntime {
    /** Shared projectile services; competitive matches and the creator bind their own concrete policies. */
    public interface Domain {
        MutableWorld world();LifeRuntime lives();EntityRegistry registry();BlastTerrain blastTerrain();BlastRuntime blasts();
        double wall();double monotonic();int frameLoop();boolean authorized(PlayerLife player,int tool);boolean infiniteBlocks(PlayerLife player);
        void create(MapEntity entity);void destroy(int id);void known(int id,GamePacket packet);void sweep();
        void impact(Vector at,PlayerLife player,boolean chemical);void breakDisguise(PlayerLife player);void report(PlayerLife player,String kind,Map<String,Double> detail);
    }
    private final Domain domain;private final ApplicationHost host;
    private final ProjectileEngine engine=new ProjectileEngine();
    public HostedProjectileRuntime(Domain domain,ApplicationHost host){this.domain=Objects.requireNonNull(domain);this.host=Objects.requireNonNull(host);}
    public HostedProjectileRuntime(StandardMatchDomain match,HostedEntityRuntime entities,ApplicationHost host){this(new Domain(){
        public MutableWorld world(){return match.world();}public LifeRuntime lives(){return match.lives();}public EntityRegistry registry(){return entities.registry();}public BlastTerrain blastTerrain(){return entities.blastTerrain();}public BlastRuntime blasts(){return entities.blasts();}
        public double wall(){return match.wall();}public double monotonic(){return match.monotonic();}public int frameLoop(){return match.frameLoop();}public boolean authorized(PlayerLife p,int tool){return match.inputs().authorized(p,tool);}public boolean infiniteBlocks(PlayerLife p){return false;}
        public void create(MapEntity e){entities.create(e);}public void destroy(int id){entities.destroy(id);}public void known(int id,GamePacket packet){entities.known(id,packet);}public void sweep(){entities.shields().sweep();}
        public void impact(Vector at,PlayerLife p,boolean chemical){entities.effects().impact(at,p,chemical);}public void breakDisguise(PlayerLife p){match.inputs().breakDisguise(p);}public void report(PlayerLife p,String kind,Map<String,Double> detail){match.inputs().report(p,kind,detail);}
    },host);}
    public ProjectileEngine engine(){return engine;}
    private static Vector vector(Vector3f p){return new Vector(p.x(),p.y(),p.z());}
    private static Vector3f wire(Vector p){return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());}
    public boolean packet(PlayerLife p,GamePacket packet){if(!(packet instanceof OrientedItemPacket v))return false;use(p,v.tool(),v.position(),v.velocity(),v.fuse(),v.loop());return true;}
    public boolean use(PlayerLife p,int tool,Vector3f at,Vector3f speed,double fuse,Integer label){
        if(!domain.authorized(p,tool)||p.inventory().tool()!=tool||!ProjectileSpec.TOOLS.containsKey(tool))return false;
        for(double n:new double[]{at.x(),at.y(),at.z(),speed.x(),speed.y(),speed.z(),fuse})if(!Double.isFinite(n)||Math.abs(n)>1e6){rejected(p,tool);return false;}
        var origin=vector(at);var velocity=vector(speed);var view=ShotValidation.view(p);var reference=ShotValidation.references(view,label);double slack=reference.atLoop()!=null?0:Math.min(4,Math.sqrt(view.velocity().x()*view.velocity().x()+view.velocity().y()*view.velocity().y()+view.velocity().z()*view.velocity().z())*32*.3);
        double nearest=reference.eyes().stream().mapToDouble(eye->CombatGeometry.distance(origin,eye)).min().orElse(Double.POSITIVE_INFINITY);
        if(nearest>3+slack||nearest>1&&reference.eyes().stream().noneMatch(eye->CombatGeometry.segmentClear(domain.world()::solid,eye,origin,.3,.6,Set.of()))){rejected(p,tool);return false;}
        double limit=launchSpeed(tool)*1.5+20,length=Math.sqrt(velocity.x()*velocity.x()+velocity.y()*velocity.y()+velocity.z()*velocity.z());if(length>limit){double scale=limit/length;velocity=new Vector(velocity.x()*scale,velocity.y()*scale,velocity.z()*scale);}
        fuse=Math.max(0,Math.min(fuse,switch(tool){case 11,32->2.5;case 31->3;case 57->5;default->10;}));double now=domain.monotonic();
        if(!p.inventory().orientedReady(tool,now,p.bot(),domain.infiniteBlocks(p))){if(Objects.equals(p.inventory().launcherClipLeft(tool,now,p.bot()),0))domain.report(p,"launcher_reload_skip",Map.of("tool",(double)tool));return false;}
        var shot=engine.spawn(tool,origin,velocity,fuse,p.id(),domain.wall());if(shot==null)return false;
        if(tool==29||tool==48){shot.blockColor=p.color();shot.sourceLoop=Math.max(0,domain.frameLoop());}
        // Source visual creation precedes debit; rejected preflights cannot consume a wallet.
        visual(shot,p,fuse);p.inventory().consumeOriented(tool,now,p.bot(),domain.infiniteBlocks(p));domain.breakDisguise(p);p.endProtection(now);return true;
    }
    private void rejected(PlayerLife p,int tool){domain.report(p,"launch_rejected",Map.of("tool",(double)tool));}
    private static double launchSpeed(int tool){return switch(tool){case 11,32,29,48,54,57->50;case 31->35;case 33,14,47->40;case 12,55,58->75;case 13->150;default->150;};}
    public Projectile serverShot(ProjectileSpec spec,Vector at,Vector velocity,PlayerLife owner){var p=engine.spawn(spec,at,velocity,owner==null?-1:owner.id(),domain.wall());visual(p,owner,0);return p;}
    private void visual(Projectile p,PlayerLife owner,double fuse){
        if(p.spec.entityType()==0){if(owner==null)throw new IllegalArgumentException("Ownerless projectile requires an entity visual");host.broadcastExcept(new OrientedItemPacket(domain.frameLoop(),owner.id(),p.tool,fuse,wire(p.position),wire(p.velocity)),Delivery.RELIABLE,owner.id());return;}
        var v=new EntityRegistry.Placement();v.state=owner==null?1:owner.team();v.playerId=owner==null?255:owner.id();v.kind="projectile";v.velocity=p.velocity;v.radius=.02;v.fuse=p.tool==57?5:fuse;
        if(p.blockColor!=null)v.color=new Vector(p.blockColor>>16&255,p.blockColor>>8&255,p.blockColor&255);
        var e=domain.registry().place(p.spec.entityType(),p.position.x(),p.position.y(),p.position.z(),v);p.entityId=e.id();domain.create(e);
    }
    private void removeVisual(Projectile p){if(p.entityId!=null){var e=domain.registry().remove(p.entityId);if(e!=null){e.alive=false;domain.destroy(e.id());}}}
    public void forget(int id){for(var p:engine.detach(id))detach(p);for(var p:engine.removeByThrower(id))removeVisual(p);}
    public void clear(){engine.clear();}
    public void tick(){
        domain.sweep();var events=engine.update(1.0/60,domain.world()::solid,domain.wall(),domain.lives().players());
        var active=Collections.newSetFromMap(new IdentityHashMap<Projectile,Boolean>());active.addAll(engine.projectiles());
        for(var p:engine.projectiles()){var e=domain.registry().get(p.entityId);if(e!=null){e.x=p.position.x();e.y=p.position.y();e.z=p.position.z();e.velocity=p.velocity;}}
        for(var event:events)if(active.contains(event.projectile()))switch(event.kind()){case ATTACH->attach(event.projectile());case DETACH->detach(event.projectile());default->{}}
        for(var event:events)switch(event.kind()){case DRILL->drill(event.projectile());case DEPLOY->deploy(event.projectile());case EXPLOSION,DESTROYED->explode(event.projectile(),event.kind()==ProjectileEngine.Kind.DESTROYED);default->{}}
    }
    private void target(int entity,Integer player){for(var session:host.transitionSessions())if(session.inGame()&&session.knownEntities().contains(entity)&&(player==null||session.knownPlayers().contains(player)))session.gameplay(new ChangeEntity(entity,5,0,new Vector3f(0,0,0),0,player==null?-1:player,0),Delivery.RELIABLE);}
    private void attach(Projectile p){
        var old=domain.registry().get(p.entityId);if(old==null||old.type!=34)return;int state=old.state,owner=old.playerId;double radius=old.radius;removeVisual(p);
        var v=new EntityRegistry.Placement();v.state=state;v.playerId=owner;v.kind="projectile";v.radius=radius;v.fuse=p.explodeAt==null?5:Math.max(0,Math.min(5,p.explodeAt-domain.wall()));var e=domain.registry().place(35,p.position.x(),p.position.y(),p.position.z(),v);p.entityId=e.id();domain.create(e);if(p.attachedPlayerId!=null)target(e.id(),p.attachedPlayerId);
    }
    private void detach(Projectile p){var e=domain.registry().get(p.entityId);if(e==null||e.type!=35)return;e.x=p.position.x();e.y=p.position.y();e.z=p.position.z();target(e.id(),null);domain.known(e.id(),ChangeEntity.position(e.id(),wire(p.position)));}
    private void drill(Projectile p){var owner=domain.lives().players().stream().filter(v->v.id()==p.throwerId).findFirst().orElse(null);if(owner==null||p.contactBlock==null)return;var at=new Vector(p.contactBlock.x(),p.contactBlock.y(),p.contactBlock.z());var e=domain.registry().get(p.entityId);domain.blastTerrain().apply(at,20,owner,1,10,e==null?null:e.id());}
    private void deploy(Projectile p){
        removeVisual(p);var owner=domain.lives().players().stream().filter(v->v.id()==p.throwerId).findFirst().orElse(null);var c=p.contactBlock;if(owner==null||c==null||!domain.world().solid(c.x(),c.y(),c.z()))return;
        var v=new EntityRegistry.Placement();v.playerId=owner.id();v.state=owner.team();v.kind="deployable";v.supportCell=c;v.behavior=new ProximityMineBehavior(new DeviceBlast(owner.id(),100,15,1,35,3,false,.75,.75,40,false),1,owner.team(),2.5,4,3,-.5);var at=p.position;domain.create(domain.registry().place(9,at.x(),at.y(),at.z(),v));
    }
    private void explode(Projectile p,boolean destroyed){
        var owner=domain.lives().players().stream().filter(v->v.id()==p.throwerId).findFirst().orElse(null);var e=domain.registry().get(p.entityId);if(p.entityId!=null&&e==null)return;
        boolean snow=p.spec.name().equals("snowball");if(snow&&e!=null){blockCannon(p,owner);domain.known(e.id(),BlastTerrain.packet(owner,p.position,p.spec.blockDamage(),p.spec.damageType(),0,e.id(),0));}
        removeVisual(p);if(p.spec.name().equals("chemical_bomb")){domain.impact(p.position,owner,true);return;}
        var s=p.spec;boolean terminal=destroyed&&s.destroyedDamage()>0;var request=new BlastRuntime.Request(p.position,terminal?s.destroyedDamage():s.damage(),terminal?s.destroyedBlockDamage():s.blockDamage(),s.killType(),owner);request.radius=terminal&&s.destroyedRadius()>0?s.destroyedRadius():s.radius();request.minimum=terminal?s.destroyedMinimum():s.minimum();request.maximum=terminal?s.destroyedMaximum():s.maximum();request.terrainType=s.damageType();request.forceDestroy=s.motion()!=ProjectileSpec.Motion.CONTACT;if(snow)request.predictionFrames=3;domain.blasts().apply(request);if(s.name().equals("molotov"))domain.impact(p.position,owner,false);
    }
    private void blockCannon(Projectile p,PlayerLife owner){
        if(owner==null||p.contactBlock==null)return;int x=(int)p.position.x(),y=(int)p.position.y(),z=(int)p.position.z();if(x<0||x>=512||y<0||y>=512||z<1||z>238||domain.world().solid(x,y,z))return;
        boolean support=false;for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})support|=domain.world().solid(x+d[0],y+d[1],z+d[2]);if(!support)return;
        int color=p.blockColor==null?owner.color():p.blockColor;domain.world().set(new MutableWorld.Cell(x,y,z),true,color,3.0);host.broadcast(new SimplePackets.BlockBuildColored(p.sourceLoop==null?Math.max(0,domain.frameLoop()):p.sourceLoop,owner.id(),x,y,z,color),Delivery.RELIABLE);
    }
}
