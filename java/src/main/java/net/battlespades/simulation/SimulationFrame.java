// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.simulation;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Source-ordered authoritative frame. Required ports bind real subsystems; optional ones are queried at their turn. */
public final class SimulationFrame {
    public enum Phase {
        PACKET_DRAIN(true,true,false), BOTS(true,true,true), BOT_ACTIONS(false,true,true),
        PROJECTILES(false,true,false), PLAYERS(true,true,false), WORLD_MUTATIONS(false,true,false),
        PREFABS(false,true,true), TERRAIN_REPAIR(false,true,false), A2S(false,true,false), MODE(true,true,false),
        PLUGINS(true,true,false), CORPSES(false,true,true), RESPAWNS(true,true,false), ENTITIES(false,true,false),
        TURRETS(false,true,false), FIRE(false,true,false), GOO(false,true,true),
        SECOND_SCHEDULERS(false,false,false), CONDUCT(false,false,false), ACHIEVEMENTS(false,false,false);
        final boolean asynchronous,measured,optional;
        Phase(boolean asynchronous,boolean measured,boolean optional){this.asynchronous=asynchronous;this.measured=measured;this.optional=optional;}
    }
    public interface Services {
        boolean enabled(Phase phase);
        CompletionStage<Void> asynchronous(Phase phase,int loop);
        void synchronous(Phase phase,int loop);
        void subsystem(Phase phase,double milliseconds);
        void failure(String name,Throwable error);
        void tickMetrics(double milliseconds);
        void health(double milliseconds);
    }
    private final OwnerSequence owner;
    private final Services services;
    private final LongSupplier nanos;
    private boolean stepping;
    public SimulationFrame(Services services,LongSupplier nanos,Executor resume){this.services=Objects.requireNonNull(services);this.nanos=Objects.requireNonNull(nanos);owner=new OwnerSequence(resume);}
    public CompletionStage<Void> step(int loop){
        owner.owned();if(stepping)throw new IllegalStateException("Frame already suspended");stepping=true;long start=nanos.getAsLong();
        var operations=new ArrayList<Supplier<CompletionStage<Void>>>();boolean[] bots={false};
        for(var phase:Phase.values())operations.add(()->{
            if(phase==Phase.BOTS)bots[0]=services.enabled(phase);
            if(phase==Phase.BOTS||phase==Phase.BOT_ACTIONS){if(!bots[0])return CompletableFuture.completedFuture(null);}
            else if(phase.optional&&!services.enabled(phase))return CompletableFuture.completedFuture(null);
            long before=phase.measured?nanos.getAsLong():0;
            CompletionStage<Void> stage;
            try{stage=owner.isolate(()->{if(phase.asynchronous)return services.asynchronous(phase,loop);services.synchronous(phase,loop);return CompletableFuture.completedFuture(null);},e->services.failure(phase.name().toLowerCase(Locale.ROOT),e));}
            catch(Error fatal){if(phase.measured)services.subsystem(phase,(nanos.getAsLong()-before)/1_000_000.0);throw fatal;}
            if(!phase.measured)return stage;
            var measured=new CompletableFuture<Void>();stage.whenComplete((unused,error)->owner.resume(()->{
                services.subsystem(phase,(nanos.getAsLong()-before)/1_000_000.0);
                if(error==null)measured.complete(null);else measured.completeExceptionally(OwnerSequence.cause(error));
            },measured));return measured.minimalCompletionStage();
        });
        operations.add(()->{double elapsed=(nanos.getAsLong()-start)/1_000_000.0;return owner.run(List.<Supplier<CompletionStage<Void>>>of(
            ()->owner.isolate(()->{services.tickMetrics(elapsed);return CompletableFuture.completedFuture(null);},e->services.failure("metrics",e)),
            ()->owner.isolate(()->{services.health(elapsed);return CompletableFuture.completedFuture(null);},e->services.failure("health",e))).iterator());});
        var result=new CompletableFuture<Void>();owner.run(operations.iterator()).whenComplete((unused,error)->owner.resume(()->{stepping=false;if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));},result));
        return result.minimalCompletionStage();
    }
}
