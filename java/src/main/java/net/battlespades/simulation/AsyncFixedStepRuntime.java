// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.simulation;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Awaited 60 Hz catch-up batches. The owner executor and yield port keep network service runnable. */
public final class AsyncFixedStepRuntime {
    private final OwnerSequence owner;
    private final BooleanSupplier running;
    private final LongFunction<CompletionStage<Void>> step;
    private final Runnable replicate;
    private final Supplier<CompletionStage<Void>> yield;
    private final BiConsumer<String,Throwable> failure;
    private double accumulator;
    private long ticks;
    private boolean advancing;
    public AsyncFixedStepRuntime(BooleanSupplier running,LongFunction<CompletionStage<Void>> step,Runnable replicate,
            Supplier<CompletionStage<Void>> yield,BiConsumer<String,Throwable> failure,Executor resume){
        this.running=Objects.requireNonNull(running);this.step=Objects.requireNonNull(step);this.replicate=Objects.requireNonNull(replicate);
        this.yield=Objects.requireNonNull(yield);this.failure=Objects.requireNonNull(failure);owner=new OwnerSequence(resume);
    }
    public long ticks(){owner.owned();return ticks;}
    public double debt(){owner.owned();return accumulator;}
    public CompletionStage<Integer> advance(double elapsedSeconds){
        owner.owned();if(advancing)throw new IllegalStateException("Catch-up batch already suspended");
        if(!Double.isFinite(elapsedSeconds)||elapsedSeconds<0)throw new IllegalArgumentException("Invalid elapsed time");
        accumulator=Math.min(2,accumulator+elapsedSeconds);advancing=true;int[] count={0};
        var operations=new ArrayList<Supplier<CompletionStage<Void>>>();
        for(int i=0;i<FixedStepRuntime.MAX_CATCH_UP_STEPS;i++)operations.add(()->{
            if(!running.getAsBoolean()||accumulator<FixedStepRuntime.DELTA)return CompletableFuture.completedFuture(null);
            accumulator-=FixedStepRuntime.DELTA;ticks++;count[0]++;
            return owner.run(List.<Supplier<CompletionStage<Void>>>of(
                ()->owner.isolate(()->step.apply(ticks),e->failure.accept("step",e)),
                ()->owner.isolate(()->{replicate.run();return CompletableFuture.completedFuture(null);},e->failure.accept("world_updates",e)),
                yield).iterator());
        });
        var result=new CompletableFuture<Integer>();owner.run(operations.iterator()).whenComplete((unused,error)->owner.resume(()->{
            advancing=false;if(error==null)result.complete(count[0]);else result.completeExceptionally(OwnerSequence.cause(error));
        },result));return result.minimalCompletionStage();
    }
}
