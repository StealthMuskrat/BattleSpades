// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.*;
import net.battlespades.simulation.*;
import net.battlespades.world.TerrainRepair;

/** Host-driven asynchronous 60 Hz assembly. Every pump services UDP even while an authoritative phase awaits.
 * Remaining simulation phases, replication and metrics must be bound to their actual domain owners.
 * The caller keeps pumping until stop completes, then closes host, scene state and owner loop in that order.
 */
public final class HostedSimulation {
    private final Thread owner=Thread.currentThread();
    private final ApplicationHost host;private final OwnerLoop loop;private final Supplier<StandardMatchDomain> match;
    private final OwnerSequence sequence;private final AsyncFixedStepRuntime runtime;
    private CompletionStage<Integer> batch=CompletableFuture.completedFuture(0);
    private CompletionStage<Void> stopping;private Throwable failed;
    private long previous;private double pending;
    private boolean started,running,advancing,pumping;
    public HostedSimulation(ApplicationHost host,OwnerLoop loop,Supplier<StandardMatchDomain> match,
            Supplier<PeriodicServices> periodic,Supplier<TerrainRepair> repair,SimulationFrame.Services other,
            Runnable replicate,LongSupplier measuredNanos,IngamePackets.Settings packets){
        this.host=Objects.requireNonNull(host);this.loop=Objects.requireNonNull(loop);this.match=Objects.requireNonNull(match);Objects.requireNonNull(periodic);Objects.requireNonNull(repair);Objects.requireNonNull(other);sequence=new OwnerSequence(loop);previous=loop.nowNanos();
        host.serialPackets(packets,loop,error->other.failure("packet",error));
        var frame=new SimulationFrame(new SimulationFrame.Services(){
            public boolean enabled(SimulationFrame.Phase phase){return other.enabled(phase);}
            public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int tick){return switch(phase){
                case PACKET_DRAIN->host.drainPackets(tick);
                case MODE->match.get().events().tick(tick);
                case RESPAWNS->periodic.get().respawns(tick,60,loop.nowNanos()/1e9,()->{match.get().lives().processRespawns();return CompletableFuture.completedFuture(null);});
                default->other.asynchronous(phase,tick);
            };}
            public void synchronous(SimulationFrame.Phase phase,int tick){if(phase==SimulationFrame.Phase.TERRAIN_REPAIR)repair.get().tick(tick);else other.synchronous(phase,tick);}
            public void subsystem(SimulationFrame.Phase phase,double ms){other.subsystem(phase,ms);}public void failure(String name,Throwable error){other.failure(name,error);}public void tickMetrics(double ms){other.tickMetrics(ms);}public void health(double ms){other.health(ms);}
        },measuredNanos,loop);
        runtime=new AsyncFixedStepRuntime(()->running,tick->frame.step((int)tick),replicate,()->loop.sleep(0),other::failure,loop);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Hosted simulation outside owner");}
    public void start(){owned();if(started||stopping!=null)throw new IllegalStateException("Simulation can start once");started=true;match.get().mode().start();running=true;previous=loop.nowNanos();}
    public long ticks(){owned();return runtime.ticks();}public boolean advancing(){owned();return advancing;}public CompletionStage<Integer> batch(){owned();return batch;}
    /** Monotonic absolute time. Elapsed time accumulated during awaits is submitted once to the next bounded batch. */
    public void pump(long nanos)throws IOException {
        owned();if(pumping)throw new IllegalStateException("Hosted simulation pump reentered");if(nanos<previous)throw new IllegalArgumentException("Host clock moved backwards");
        pumping=true;
        try{
            if(running)pending=Math.min(2,pending+(nanos-previous)/1e9);previous=nanos;
            host.service(nanos/1_000_000,(int)runtime.ticks());loop.pump(nanos);
            if(failed!=null){if(failed instanceof Error fatal)throw fatal;if(failed instanceof RuntimeException ordinary)throw ordinary;throw new CompletionException(failed);}
            if(running&&!advancing){double elapsed=pending;pending=0;advancing=true;batch=runtime.advance(elapsed);batch.whenComplete((count,error)->sequence.resume(()->{advancing=false;if(error!=null){failed=OwnerSequence.cause(error);running=false;}},new CompletableFuture<>()));}
        }finally{pumping=false;}
    }
    /** Stops new frames; an in-flight frame and its network/owner continuations finish before mode deactivation. */
    public CompletionStage<Void> stop(){
        owned();if(stopping!=null)return stopping;running=false;pending=0;
        var result=new CompletableFuture<Void>();stopping=result.minimalCompletionStage();
        batch.whenComplete((count,error)->sequence.resume(()->{
            Throwable prior=error==null?null:OwnerSequence.cause(error);
            CompletionStage<Void> deactivate;try{deactivate=match.get().mode().deactivate();}catch(RuntimeException|Error failure){if(prior!=null)failure.addSuppressed(prior);result.completeExceptionally(failure);return;}
            deactivate.whenComplete((unused,cleanup)->sequence.resume(()->{Throwable root=cleanup==null?prior:OwnerSequence.cause(cleanup);if(cleanup!=null&&prior!=null&&root!=prior)root.addSuppressed(prior);if(root==null)result.complete(null);else result.completeExceptionally(root);},result));
        },result));return stopping;
    }
}
