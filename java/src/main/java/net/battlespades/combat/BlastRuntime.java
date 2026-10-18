// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import java.util.function.Supplier;
import net.battlespades.entities.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;

/** Source shared blast commit. Concrete mode, stimulus and achievement services remain required domain ports. */
public final class BlastRuntime {
    public static final class Request {
        public Vector origin;public double damage,blockDamage,radius=16,minimum,maximum;public int killType,craterRadius=1;
        public PlayerLife thrower;public boolean forceDestroy=true,ignorePlayerLos;public Double selfMinimum,selfMaximum;public Integer predictionFrames,nativeType,causerEntity,terrainType;
        public Request(Vector origin,double damage,double blockDamage,int killType,PlayerLife thrower){this.origin=Objects.requireNonNull(origin);this.damage=damage;this.blockDamage=blockDamage;this.killType=killType;this.thrower=thrower;}
    }
    public interface Timing {double monotonic();}
    public interface Domain {
        boolean buildDamage();boolean friendlyFire();boolean hardcore(PlayerLife victim);
        void stimulus(Vector position,int source,int team,double radius,double lifetime);
        Object begin(PlayerLife thrower,int killType,Vector origin);void end(Object scope);void scopeFault(String phase);
    }
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final EntityRegistry registry;private final BlastTerrain terrain;private final Timing timing;private final Domain domain;private final Supplier<EntityContext> context;private final CollisionMap map;
    public BlastRuntime(LifeRuntime lives,EntityRegistry registry,BlastTerrain terrain,Timing timing,Domain domain,Supplier<EntityContext> context){this.lives=Objects.requireNonNull(lives);this.registry=Objects.requireNonNull(registry);this.terrain=Objects.requireNonNull(terrain);this.timing=Objects.requireNonNull(timing);this.domain=Objects.requireNonNull(domain);this.context=Objects.requireNonNull(context);map=CollisionMap.of(lives.world());}
    public void apply(Request request){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Blast outside owner");Object scope=null;try{scope=domain.begin(request.thrower,request.killType,request.origin);}catch(RuntimeException failure){domain.scopeFault("blast_begin");}
        try{commit(request);}finally{if(scope!=null)try{domain.end(scope);}catch(RuntimeException failure){domain.scopeFault("blast_end");}}
    }
    private void commit(Request r){
        var source=r.thrower;domain.stimulus(r.origin,source==null?-1:source.id(),source==null?-1:source.team(),Math.max(48,r.radius*5),2);
        boolean sent=false;if(domain.buildDamage()&&r.blockDamage>0)sent=terrain.apply(r.origin,r.blockDamage,source,r.craterRadius,r.nativeType!=null?r.nativeType:r.terrainType,r.nativeType!=null?r.causerEntity:null);
        Integer type=r.nativeType!=null?r.nativeType:r.terrainType,frames=r.predictionFrames;if(frames==null&&sent&&type!=null&&ExplosionGeometry.STOCK_DAMAGE_TYPES.contains(type))frames=ExplosionGeometry.PREDICTION_FRAMES;
        var stock=ExplosionGeometry.stockFor(r.killType,r.damage);double radius=stock==null?r.radius:stock.radius(),minimum=stock==null?r.minimum:stock.knockbackMin(),maximum=stock==null?r.maximum:stock.knockbackMax();boolean classic=stock!=null&&stock.classic();
        var blocked=r.ignorePlayerLos?null:ExplosionGeometry.segmentBlocker((at,direction,length)->HitTrace.terrainRay(map,at,direction,length)!=null);
        for(var target:lives.players()){
            if(!target.alive()||!target.spawned())continue;boolean crouched=target.body().hitboxCrouched();var position=target.position();if(ExplosionGeometry.falloff(r.origin,position,radius,crouched?1.25:.75)<=0)continue;
            double los=ExplosionGeometry.losFraction(blocked,r.origin,position,crouched);if(los<=0)continue;double low=target==source&&r.selfMinimum!=null?r.selfMinimum:minimum,high=target==source&&r.selfMaximum!=null?r.selfMaximum:maximum;var scaled=ExplosionGeometry.scaleKnockback(low,high,los);low=scaled[0];high=scaled[1];var impulse=ExplosionGeometry.impulse(r.origin,target.position(),radius,low,high,crouched);
            if(target==source&&impulse!=null)target.motion().noteOwnBlast(timing.monotonic(),r.killType);
            if(frames!=null&&impulse!=null)target.motion().queueExplosion(target,frames,r.origin,radius,low,high);
            else if(impulse!=null){var v=PlayerMotion.clipped(target.body().state().velocity());target.body().velocity(new Vector(v.x()+impulse.x(),v.y()+impulse.y(),v.z()+impulse.z()));}
            if(source!=null&&target!=source&&target.team()==source.team()&&!domain.friendlyFire())continue;
            double amount=ExplosionGeometry.playerDamage(r.origin,position,radius,r.damage,crouched,los,target==source&&source!=null,target.team(),classic)*DirectDamage.victimMultiplier(DirectDamage.victim(target,domain.hardcore(target)));
            if(amount>0)lives.damage(target.id(),amount,source==null?null:source.id(),r.killType);
        }
        var ctx=context.get();for(var entity:registry.all()){
            var behavior=entity.behavior;if(!entity.alive||behavior==null||!behavior.takesDamage)continue;var center=behavior.hitCenter(entity);double amount=ExplosionGeometry.entityDamage(r.origin,center,radius,r.damage);if(amount<=0||blocked!=null&&ExplosionGeometry.rayBlocked(blocked,r.origin,center))continue;registry.damageEntity(entity.id(),amount,source,ctx);
        }
    }
}
