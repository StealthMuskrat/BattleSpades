// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.battlespades.combat.BlastRuntime;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.world.MutableWorld;

/** Concrete synchronous charge lifecycle and terrain support binding over owned lives, registry and blast authority. */
public final class DeployableRuntime implements AutoCloseable {
    public interface Explosive {
        DeviceBlast blast();Vector explosionCenter(MapEntity entity);PlayerLife triggeredBy();
    }
    private final java.util.Map<Integer,Integer> radarCounts=new java.util.HashMap<>();
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final BlastRuntime blasts;private final MutableWorld.RemovalLease removalLease;
    public DeployableRuntime(LifeRuntime lives,EntityRegistry registry,BlastRuntime blasts,Supplier<EntityContext> context,Consumer<RuntimeException> listenerFault){
        this.lives=Objects.requireNonNull(lives);this.blasts=Objects.requireNonNull(blasts);Objects.requireNonNull(registry);Objects.requireNonNull(context);Objects.requireNonNull(listenerFault);
        removalLease=lives.world().observeRemovals(cell->{try{var c=new EntityRegistry.Cell(cell.x(),cell.y(),cell.z());if(registry.hasSupportAt(c))registry.supportRemoved(c,context.get());}catch(RuntimeException failure){listenerFault.accept(failure);}});
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Deployables outside owner");}
    public PlayerLife player(int id){owned();return lives.players().stream().filter(p->p.id()==id).findFirst().orElse(null);}
    public void radarAdded(int team){owned();radarCounts.put(team,radarCount(team)+1);}
    public void radarRemoved(int team){owned();radarCounts.put(team,Math.max(0,radarCount(team)-1));}
    public void reset(){owned();radarCounts.clear();}
    public int radarCount(int team){owned();return radarCounts.getOrDefault(team,0);}
    public static Vector attachmentCenter(MapEntity e){return switch(e.face){case 0->new Vector(e.x,e.y+.5,e.z+.5);case 1->new Vector(e.x+1,e.y+.5,e.z+.5);case 2->new Vector(e.x+.5,e.y,e.z+.5);case 3->new Vector(e.x+.5,e.y+1,e.z+.5);case 4->new Vector(e.x+.5,e.y+.5,e.z);case 5->new Vector(e.x+.5,e.y+.5,e.z+1);default->new Vector(e.x+.5,e.y+.5,e.z+.5);};}
    public static void detonate(Explosive behavior,MapEntity entity,EntityContext context){
        var service=context.devices();var spec=behavior.blast();var thrower=service==null?null:service.player(spec.throwerId());entity.alive=false;
        if(service!=null){
            var at=behavior.explosionCenter(entity);var request=new BlastRuntime.Request(at,spec.damage(),spec.blockDamage(),spec.killType(),thrower);request.craterRadius=spec.craterRadius();request.forceDestroy=spec.forceDestroy();request.radius=spec.radius();request.minimum=spec.minimum();request.maximum=spec.maximum();request.nativeType=spec.damageType();request.causerEntity=entity.id();request.ignorePlayerLos=spec.ignorePlayerLos();
            var trigger=behavior.triggeredBy();var scope=service.lives.blastSource(trigger==null?thrower:trigger);try{service.blasts.apply(request);}finally{scope.close();}
        }
        if(context.destroy()!=null)context.destroy().accept(entity.id());if(context.registry()!=null)context.registry().remove(entity.id());
    }
    public static void forgetCharge(MapEntity entity,EntityContext context){var owner=context.devices()==null?null:context.devices().player(entity.playerId);if(owner!=null)owner.forgetCharge(entity.id());}
    @Override public void close(){owned();removalLease.close();}
}
