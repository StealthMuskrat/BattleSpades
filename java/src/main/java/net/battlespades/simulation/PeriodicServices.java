// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.simulation;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Source 1 Hz service order, immediately before respawns. Ordinary failures cannot skip later services or respawns. */
public final class PeriodicServices {
    private final OwnerSequence owner;private final DoubleFunction<CompletionStage<Boolean>> balance;private final DoubleConsumer skill,report;private final BiConsumer<String,Throwable> failure;private boolean active;
    public PeriodicServices(DoubleFunction<CompletionStage<Boolean>> balance,DoubleConsumer skill,DoubleConsumer report,BiConsumer<String,Throwable> failure,Executor resume){this.balance=Objects.requireNonNull(balance);this.skill=Objects.requireNonNull(skill);this.report=Objects.requireNonNull(report);this.failure=Objects.requireNonNull(failure);owner=new OwnerSequence(resume);}
    /** Bind this whole operation as SimulationFrame.RESPAWNS, rather than running a separate timer. */
    public CompletionStage<Void> respawns(int loop,int tickRate,double now,Supplier<CompletionStage<Void>> respawns){owner.owned();Objects.requireNonNull(respawns);if(active)throw new IllegalStateException("Periodic services already suspended");active=true;var steps=new ArrayList<Supplier<CompletionStage<Void>>>();if(loop%Math.max(1,tickRate)==0){steps.add(()->owner.isolate(()->balance.apply(now).thenAccept(unused->{}),e->failure.accept("team balance",e)));steps.add(()->owner.isolate(()->{skill.accept(now);return CompletableFuture.completedFuture(null);},e->failure.accept("bot skill balance",e)));steps.add(()->owner.isolate(()->{report.accept(now);return CompletableFuture.completedFuture(null);},e->failure.accept("anticheat report",e)));}steps.add(respawns);var result=new CompletableFuture<Void>();owner.run(steps.iterator()).whenComplete((unused,error)->owner.resume(()->{active=false;if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));},result));return result.minimalCompletionStage();}
}
