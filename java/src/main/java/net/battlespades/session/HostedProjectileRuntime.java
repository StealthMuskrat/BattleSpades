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
    private final StandardMatchDomain match;private final HostedEntityRuntime entities;private final ApplicationHost host;
    private final ProjectileEngine engine=new ProjectileEngine();
    public HostedProjectileRuntime(StandardMatchDomain match,HostedEntityRuntime entities,ApplicationHost host){this.match=Objects.requireNonNull(match);this.entities=Objects.requireNonNull(entities);this.host=Objects.requireNonNull(host);}
    public ProjectileEngine engine(){return engine;}
    private static Vector vector(Vector3f p){return new Vector(p.x(),p.y(),p.z());}
    private static Vector3f wire(Vector p){return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());}
    public boolean packet(PlayerLife p,GamePacket packet){if(!(packet instanceof OrientedItemPacket v))return false;use(p,v.tool(),v.position(),v.velocity(),v.fuse(),v.loop());return true;}
    public boolean use(PlayerLife p,int tool,Vector3f at,Vector3f speed,double fuse,Integer label){
        if(!match.inputs().authorized(p,tool)||p.inventory().tool()!=tool||!ProjectileSpec.TOOLS.containsKey(tool))return false;
        for(double n:new double[]{at.x(),at.y(),at.z(),speed.x(),speed.y(),speed.z(),fuse})if(!Double.isFinite(n)||Math.abs(n)>1e6){rejected(p,tool);return false;}
        var origin=vector(at);var velocity=vector(speed);var view=ShotValidation.view(p);var reference=ShotValidation.references(view,label);double slack=reference.atLoop()!=null?0:Math.min(4,Math.sqrt(view.velocity().x()*view.velocity().x()+view.velocity().y()*view.velocity().y()+view.velocity().z()*view.velocity().z())*32*.3);
        double nearest=reference.eyes().stream().mapToDouble(eye->CombatGeometry.distance(origin,eye)).min().orElse(Double.POSITIVE_INFINITY);
        if(nearest>3+slack||nearest>1&&reference.eyes().stream().noneMatch(eye->CombatGeometry.segmentClear(match.world()::solid,eye,origin,.3,.6,Set.of()))){rejected(p,tool);return false;}
        double limit=launchSpeed(tool)*1.5+20,length=Math.sqrt(velocity.x()*velocity.x()+velocity.y()*velocity.y()+velocity.z()*velocity.z());if(length>limit){double scale=limit/length;velocity=new Vector(velocity.x()*scale,velocity.y()*scale,velocity.z()*scale);}
        fuse=Math.max(0,Math.min(fuse,switch(tool){case 11,32->2.5;case 31->3;case 57->5;default->10;}));double now=match.monotonic();
        if(!p.inventory().orientedReady(tool,now,p.bot(),false)){if(Objects.equals(p.inventory().launcherClipLeft(tool,now,p.bot()),0))match.inputs().report(p,"launcher_reload_skip",Map.of("tool",(double)tool));return false;}
        var shot=engine.spawn(tool,origin,velocity,fuse,p.id(),match.wall());if(shot==null)return false;
        if(tool==29||tool==48){shot.blockColor=p.color();shot.sourceLoop=Math.max(0,match.frameLoop());}
        // Source visual creation precedes debit; rejected preflights cannot consume a wallet.
        visual(shot,p,fuse);p.inventory().consumeOriented(tool,now,p.bot(),false);match.inputs().breakDisguise(p);p.endProtection(now);return true;
    }
    private void rejected(PlayerLife p,int tool){match.inputs().report(p,"launch_rejected",Map.of("tool",(double)tool));}
    private static double launchSpeed(int tool){return switch(tool){case 11,32,29,48,54,57->50;case 31->35;case 33,14,47->40;case 12,55,58->75;case 13->150;default->150;};}
    public Projectile serverShot(ProjectileSpec spec,Vector at,Vector velocity,PlayerLife owner){var p=engine.spawn(spec,at,velocity,owner==null?-1:owner.id(),match.wall());visual(p,owner,0);return p;}
    private void visual(Projectile p,PlayerLife owner,double fuse){
        if(p.spec.entityType()==0){if(owner==null)throw new IllegalArgumentException("Ownerless projectile requires an entity visual");host.broadcastExcept(new OrientedItemPacket(match.frameLoop(),owner.id(),p.tool,fuse,wire(p.position),wire(p.velocity)),Delivery.RELIABLE,owner.id());return;}
        var v=new EntityRegistry.Placement();v.state=owner==null?1:owner.team();v.playerId=owner==null?255:owner.id();v.kind="projectile";v.velocity=p.velocity;v.radius=.02;v.fuse=p.tool==57?5:fuse;
        if(p.blockColor!=null)v.color=new Vector(p.blockColor>>16&255,p.blockColor>>8&255,p.blockColor&255);
        var e=entities.registry().place(p.spec.entityType(),p.position.x(),p.position.y(),p.position.z(),v);p.entityId=e.id();entities.create(e);
    }
    private void removeVisual(Projectile p){if(p.entityId!=null){var e=entities.registry().remove(p.entityId);if(e!=null){e.alive=false;entities.destroy(e.id());}}}
    public void forget(int id){for(var p:engine.detach(id))detach(p);for(var p:engine.removeByThrower(id))removeVisual(p);}
    public void clear(){engine.clear();}
    public void tick(){
        entities.shields().sweep();var events=engine.update(1.0/60,match.world()::solid,match.wall(),match.lives().players());
        var active=Collections.newSetFromMap(new IdentityHashMap<Projectile,Boolean>());active.addAll(engine.projectiles());
        for(var event:events)if(active.contains(event.projectile()))switch(event.kind()){case ATTACH->attach(event.projectile());case DETACH->detach(event.projectile());default->{}}
        for(var event:events)switch(event.kind()){case DRILL->drill(event.projectile());case DEPLOY->deploy(event.projectile());case EXPLOSION,DESTROYED->explode(event.projectile(),event.kind()==ProjectileEngine.Kind.DESTROYED);default->{}}
    }
    private void target(int entity,Integer player){for(var session:host.transitionSessions())if(session.inGame()&&session.knownEntities().contains(entity)&&(player==null||session.knownPlayers().contains(player)))session.gameplay(new ChangeEntity(entity,5,0,new Vector3f(0,0,0),0,player==null?-1:player,0),Delivery.RELIABLE);}
    private void attach(Projectile p){
        var old=entities.registry().get(p.entityId);if(old==null||old.type!=34)return;int state=old.state,owner=old.playerId;double radius=old.radius;removeVisual(p);
        var v=new EntityRegistry.Placement();v.state=state;v.playerId=owner;v.kind="projectile";v.radius=radius;v.fuse=p.explodeAt==null?5:Math.max(0,Math.min(5,p.explodeAt-match.wall()));var e=entities.registry().place(35,p.position.x(),p.position.y(),p.position.z(),v);p.entityId=e.id();entities.create(e);if(p.attachedPlayerId!=null)target(e.id(),p.attachedPlayerId);
    }
    private void detach(Projectile p){var e=entities.registry().get(p.entityId);if(e==null||e.type!=35)return;e.x=p.position.x();e.y=p.position.y();e.z=p.position.z();target(e.id(),null);entities.known(e.id(),ChangeEntity.position(e.id(),wire(p.position)));}
    private void drill(Projectile p){var owner=entities.devices().player(p.throwerId);if(owner==null||p.contactBlock==null)return;var at=new Vector(p.contactBlock.x(),p.contactBlock.y(),p.contactBlock.z());var e=entities.registry().get(p.entityId);entities.blastTerrain().apply(at,20,owner,1,10,e==null?null:e.id());}
    private void deploy(Projectile p){
        removeVisual(p);var owner=entities.devices().player(p.throwerId);var c=p.contactBlock;if(owner==null||c==null||!match.world().solid(c.x(),c.y(),c.z()))return;
        var v=new EntityRegistry.Placement();v.playerId=owner.id();v.state=owner.team();v.kind="deployable";v.supportCell=c;v.behavior=new ProximityMineBehavior(new DeviceBlast(owner.id(),100,15,1,35,3,false,.75,.75,40,false),1,owner.team(),2.5,4,3,-.5);var at=p.position;entities.create(entities.registry().place(9,at.x(),at.y(),at.z(),v));
    }
    private void explode(Projectile p,boolean destroyed){
        var owner=entities.devices().player(p.throwerId);var e=entities.registry().get(p.entityId);if(p.entityId!=null&&e==null)return;
        boolean snow=p.spec.name().equals("snowball");if(snow&&e!=null){blockCannon(p,owner);entities.known(e.id(),BlastTerrain.packet(owner,p.position,p.spec.blockDamage(),p.spec.damageType(),0,e.id(),0));}
        removeVisual(p);if(p.spec.name().equals("chemical_bomb")){entities.effects().impact(p.position,owner,true);return;}
        var s=p.spec;boolean terminal=destroyed&&s.destroyedDamage()>0;var request=new BlastRuntime.Request(p.position,terminal?s.destroyedDamage():s.damage(),terminal?s.destroyedBlockDamage():s.blockDamage(),s.killType(),owner);request.radius=terminal&&s.destroyedRadius()>0?s.destroyedRadius():s.radius();request.minimum=terminal?s.destroyedMinimum():s.minimum();request.maximum=terminal?s.destroyedMaximum():s.maximum();request.terrainType=s.damageType();request.forceDestroy=s.motion()!=ProjectileSpec.Motion.CONTACT;if(snow)request.predictionFrames=3;entities.blasts().apply(request);if(s.name().equals("molotov"))entities.effects().impact(p.position,owner,false);
    }
    private void blockCannon(Projectile p,PlayerLife owner){
        if(owner==null||p.contactBlock==null)return;int x=(int)p.position.x(),y=(int)p.position.y(),z=(int)p.position.z();if(x<0||x>=512||y<0||y>=512||z<1||z>238||match.world().solid(x,y,z))return;
        boolean support=false;for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})support|=match.world().solid(x+d[0],y+d[1],z+d[2]);if(!support)return;
        int color=p.blockColor==null?owner.color():p.blockColor;match.world().set(new MutableWorld.Cell(x,y,z),true,color,3.0);entities.reliable(new SimplePackets.BlockBuildColored(p.sourceLoop==null?Math.max(0,match.frameLoop()):p.sourceLoop,owner.id(),x,y,z,color));
    }
}
