// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.match.GameplaySounds;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.session.*;

/** Shared human/bot placement commits over the actual match registry, stock and support authority. */
public final class DeployableActions {
    private static final class Sucker {long generation=-1;Double warm;int loop;Integer relayed;double due;}
    private final Map<PlayerLife,Sucker> suckers=new IdentityHashMap<>();
    public void forget(PlayerLife p){suckers.remove(p);}
    public void clear(){suckers.clear();}
    private final StandardMatchDomain match;private final HostedEntityRuntime entities;private final double radarLifetime;
    public DeployableActions(StandardMatchDomain match,HostedEntityRuntime entities,double radarLifetime){this.match=Objects.requireNonNull(match);this.entities=Objects.requireNonNull(entities);this.radarLifetime=radarLifetime;}
    public boolean authorized(PlayerLife p,int tool){
        if(!match.inputs().authorized(p,tool)||p.inventory().tool()!=tool)return false;
        return switch(tool){case 21,59,63->p.selection().classId()==3;case 20,56->p.selection().classId()==1;case 51->p.selection().classId()==17;case 16->p.selection().classId()==2||p.selection().classId()==12;case 64->p.selection().classId()==12;case 15->p.selection().classId()==0;default->false;};
    }
    public boolean packet(PlayerLife p,GamePacket packet){
        if(packet instanceof SimplePackets.BlockSuckerPacket v){suck(p,v);return true;}
        if(packet instanceof SimplePackets.UseCommand){use(p);return true;}
        if(packet instanceof SimplePackets.DisguisePacket d){if(d.active()==0)p.disguise().clear();else if(authorized(p,64))p.disguise().activate(p,d.loop_count(),match.monotonic());return true;}
        if(packet instanceof SimplePackets.DetonateC4){detonate(p);return true;}
        if(!(packet instanceof LivePlacement v)||!Set.of(1,87,88,89,90,91,92).contains(v.id()))return false;
        int tool=switch(v.id()){case 1->21;case 87->15;case 88->16;case 89->20;case 90->51;case 91->56;case 92->59;default->throw new IllegalStateException();};
        if(v.id()==87||v.id()==88){if(v.id()==87?machineGun(p,new Vector(v.x(),v.y(),v.z()),v.yaw()):turret(p,new Vector(v.x(),v.y(),v.z()),v.yaw())){match.inputs().breakDisguise(p);p.endProtection(match.monotonic());}return true;}
        if(place(p,tool,new Vector(v.x(),v.y(),v.z()),v.face())){match.inputs().breakDisguise(p);p.endProtection(match.monotonic());}return true;
    }
    private EntityRegistry.Cell support(Vector at,int depth){int x=(int)Math.floor(at.x()),y=(int)Math.floor(at.y()),z=(int)Math.floor(at.z());if(x<0||x>=512||y<0||y>=512||z<0||z>=240)return null;for(int i=z;i<Math.min(240,z+depth+1);i++)if(match.world().solid(x,y,i))return new EntityRegistry.Cell(x,y,i);return null;}
    private boolean visible(PlayerLife p,Vector at,EntityRegistry.Cell support){
        if(p.bot())return true;var eyes=ShotValidation.references(ShotValidation.view(p),null).eyes();var cell=new CombatGeometry.Cell((int)Math.floor(at.x()),(int)Math.floor(at.y()),(int)Math.floor(at.z()));var base=new CombatGeometry.Cell(support.x(),support.y(),support.z());
        if(CombatGeometry.cellVisible(match.world()::solid,eyes,cell,Set.of(base))||CombatGeometry.cellVisible(match.world()::solid,eyes,base,Set.of()))return true;
        match.inputs().report(p,"placement_occluded",Map.of());return false;
    }
    public boolean place(PlayerLife p,int tool,Vector at,int face){
        if(!authorized(p,tool)||!Set.of(21,20,51,56,59).contains(tool)||face<0||face>5)return false;
        double now=match.monotonic(),range=tool==56?10:5;if(!p.inventory().deployableReady(tool,now)||Math.abs(at.x())>1e6||Math.abs(at.y())>1e6||Math.abs(at.z())>1e6||CombatGeometry.distance(at,p.position())>range)return false;
        var base=support(at,tool==21||tool==59?0:4);if(base==null||!visible(p,at,base))return false;
        var registry=entities.registry();var liveCharges=p.chargeIds().stream().filter(id->{var e=registry.get(id);return e!=null&&e.alive;}).toList();if(tool==59&&liveCharges.size()>=2)return false;
        EntityBehavior behavior;int type;String kind="deployable";
        switch(tool){
            case 51->{type=30;kind="medpack";behavior=new MedpackBehavior(p.team(),25,3,1,new MedpackBehavior.Care(){public boolean hardcore(){return match.hardcore();}public double wall(){return match.wall();}public void heal(PlayerLife player,int amount){match.lives().heal(player.id(),amount);}});}
            case 21->{type=10;var stock=ExplosionGeometry.stock().stream().filter(s->s.name().equals("dynamite")).findFirst().orElseThrow();behavior=new TimedExplosiveBehavior(new DeviceBlast(p.id(),300,7,2,15,stock.radius(),true,.1,.15,16,false),7,1);}
            case 20->{type=9;var stock=ExplosionGeometry.stock().stream().filter(s->s.name().equals("landmine")).findFirst().orElseThrow();behavior=new ProximityMineBehavior(new DeviceBlast(p.id(),100,15,1,14,stock.radius(),false,0,0,15,true),1,p.team(),2.5,4,3,-.5);}
            case 59->{type=38;behavior=new RemoteChargeBehavior(new DeviceBlast(p.id(),300,7,2,36,8,true,.1,.15,41,false),1);}
            case 56->{type=36;var old=registry.get(p.radarEntityId());if(old!=null&&old.alive&&old.behavior instanceof RadarStationBehavior radar)radar.onDestroyed(old,null,entities.context());behavior=new RadarStationBehavior(p.team(),radarLifetime,45);}
            default->throw new IllegalStateException();
        }
        var placement=new EntityRegistry.Placement();placement.playerId=p.id();placement.state=p.team();placement.kind=kind;placement.face=face;placement.supportCell=base;placement.behavior=behavior;if(tool==56)placement.fuse=radarLifetime;
        var entity=registry.place(type,at.x(),at.y(),at.z(),placement);
        if(tool==59){var ids=new ArrayList<>(liveCharges);ids.add(entity.id());p.chargeIds(ids);}if(tool==56){p.radarEntityId(entity.id());entities.devices().radarAdded(p.team());}
        p.inventory().commitDeployable(tool,now);entities.create(entity);
        if(tool==21||tool==20)try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(tool==21?21:31,1,new GameplaySounds.Position(at.x(),at.y(),at.z()),1),null,true,null);}catch(RuntimeException ignored){/* Source placement audio is isolated. */}
        return true;
    }
    public boolean detonate(PlayerLife p){if(!authorized(p,59))return false;boolean fired=false;for(int id:List.copyOf(p.chargeIds())){var e=entities.registry().get(id);if(e!=null&&e.alive&&e.playerId==p.id()&&e.behavior instanceof RemoteChargeBehavior charge){charge.detonate(e,entities.context());fired=true;}}p.chargeIds(List.of());return fired;}
    public void suck(PlayerLife p,SimplePackets.BlockSuckerPacket packet){
        if(!authorized(p,63)||packet.state()<0||packet.state()>2)return;var state=suckers.computeIfAbsent(p,key->new Sucker());if(state.generation!=p.generation()){state.generation=p.generation();state.warm=null;state.relayed=null;state.due=0;}
        double now=match.monotonic();int power=packet.state();boolean warmed=false;
        if(power==0)state.warm=null;else{if(state.warm==null){state.warm=now;state.loop=packet.loop_count();}double elapsed=now-state.warm;warmed=power==2&&(elapsed>=.65||elapsed>=.25&&((long)packet.loop_count()-state.loop)/60.0>=1-2.0/60);if(power==2&&!warmed&&packet.shot()!=0)match.inputs().report(p,"block_sucker_warmup",Map.of("elapsed",elapsed,"required",1.0));}
        boolean accepted=packet.shot()!=0&&power==2&&warmed&&now>=state.due;if(accepted)state.due=now+.2;if(!accepted&&Objects.equals(state.relayed,power))return;state.relayed=power;entities.reliable(new SimplePackets.BlockSuckerPacket(match.frameLoop(),p.id(),power,accepted?1:0));if(!accepted)return;
        var hit=HitTrace.terrainRay(net.battlespades.physics.CollisionMap.of(match.world()),p.position(),p.aim(),10);if(hit==null)return;boolean before=match.world().solid(hit.x(),hit.y(),hit.z());entities.terrain().gun(p,new net.battlespades.world.BlockDamageModel.Cell(hit.x(),hit.y(),hit.z()),9,42,null);if(before&&!match.world().solid(hit.x(),hit.y(),hit.z()))p.inventory().addBlocks(1);
    }
    public boolean machineGun(PlayerLife p,Vector at,double yaw){
        if(!authorized(p,15)||!Double.isFinite(yaw)||!p.inventory().deployableReady(15,match.monotonic())||CombatGeometry.distance(at,p.position())>5)return false;
        var base=support(at,4);if(base==null||!visible(p,at,base))return false;
        var previous=entities.registry().all().stream().filter(e->e.alive&&e.behavior instanceof MachineGunBehavior gun&&gun.ownerId==p.id()).toList();
        if(previous.stream().anyMatch(e->((MachineGunBehavior)e.behavior).carrier()!=null))return false;
        var behavior=new MachineGunBehavior(match,p.id(),p.team());
        for(var old:previous){var gun=(MachineGunBehavior)old.behavior;behavior.health=Math.min(behavior.health,gun.health);behavior.ammo=Math.min(behavior.ammo,gun.ammo);}
        var placement=new EntityRegistry.Placement();placement.playerId=255;placement.state=p.team();placement.yaw=yaw;placement.kind="machine_gun";placement.supportCell=base;placement.behavior=behavior;
        var entity=entities.registry().place(7,at.x(),at.y(),at.z(),placement);p.inventory().commitDeployable(15,match.monotonic());
        for(var old:previous){entities.registry().remove(old.id());old.alive=false;entities.destroy(old.id());}entities.create(entity);return true;
    }
    public boolean turret(PlayerLife p,Vector at,double yaw){
        double now=match.monotonic();if(!authorized(p,16)||!Double.isFinite(yaw)||!p.inventory().deployableReady(16,now)||CombatGeometry.distance(at,p.position())>10)return false;
        var base=support(at,4);if(base==null||!visible(p,at,base))return false;
        var behavior=new RocketTurretBehavior(match,p.id(),p.team(),yaw,now);var v=new EntityRegistry.Placement();v.state=p.team();v.playerId=p.id();v.kind="rocket_turret";v.behavior=behavior;v.supportCell=base;
        var e=entities.registry().place(8,at.x(),at.y(),at.z(),v);p.inventory().commitDeployable(16,now);p.inventory().consumeTurret();entities.create(e);behavior.announce(e);
        try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(30,1,new GameplaySounds.Position(at.x(),at.y(),at.z()),1),null,true,null);}catch(RuntimeException ignored){/* Source isolates placement sound. */}return true;
    }
    public boolean use(PlayerLife p){
        if(!p.alive()||!p.spawned())return false;
        var mounted=entities.registry().get(p.mountedEntityId());if(mounted!=null&&mounted.behavior instanceof MachineGunBehavior gun&&Objects.equals(gun.carrier(),p.id()))return gun.unmount(mounted);
        var nearest=entities.registry().all().stream().filter(e->e.alive&&e.behavior instanceof MachineGunBehavior gun&&gun.carrier()==null&&CombatGeometry.distance(p.position(),new Vector(e.x,e.y,e.z))<=3).min(Comparator.<MapEntity>comparingDouble(e->CombatGeometry.distance(p.position(),new Vector(e.x,e.y,e.z))).thenComparingInt(MapEntity::id));
        return nearest.isPresent()&&((MachineGunBehavior)nearest.get().behavior).mount(nearest.get(),p);
    }
    /** Retire allegiance and compact owner identities before team change or slot reuse. */
    public void retire(PlayerLife p){
        for(var e:entities.registry().all()){
            if(e.behavior instanceof MachineGunBehavior gun){boolean owned=gun.ownerId==p.id();if(Objects.equals(gun.carrier(),p.id())&&!owned){gun.unmount(e);continue;}if(!owned)continue;gun.unmount(e);}
            else if(!Set.of("deployable","medpack","grave","rocket_turret").contains(e.kind)||e.playerId!=p.id())continue;
            if(e.behavior instanceof RadarStationBehavior)entities.devices().radarRemoved(e.state);
            if(entities.registry().remove(e.id())!=null){e.alive=false;entities.destroy(e.id());}
        }
        p.mountedEntityId(null);p.chargeIds(List.of());p.radarEntityId(null);
    }
}
