// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.plugin.*;
import net.battlespades.runtime.OwnerSequence;
import net.battlespades.service.*;
import net.battlespades.transport.enet.Delivery;
/** Host-lifetime plugin binding. Prepare the catalog on a worker, then await ready before starting frames. */
public final class HostedPlugins {
    private final OwnerSequence sequence;private final PluginManager manager;private final PluginCatalog catalog;private final Executor worker;private final double budget;
    private final CompletionStage<Void> ready;private CompletionStage<Void> stopping;
    public HostedPlugins(ApplicationHost host,Supplier<StandardMatchDomain> match,PluginCatalog.Settings settings,PluginCatalog catalog,Executor worker,Executor owner,DoubleSupplier measured,DoubleSupplier monotonic,BiConsumer<String,Throwable> failure){
        this.catalog=Objects.requireNonNull(catalog);this.worker=Objects.requireNonNull(worker);budget=settings.tickBudgetMillis();sequence=new OwnerSequence(owner);
        var context=new RuntimePlugin.Context(){public ApplicationHost host(){return host;}public StandardMatchDomain match(){sequence.owned();return match.get();}public Executor owner(){return owner;}public void broadcast(String message){sequence.owned();host.broadcast(Announcements.overlay(message),Delivery.RELIABLE);}public void log(String message){sequence.owned();host.telemetry().flatMap(t->t.logging()).ifPresent(l->l.log(LoggingRuntime.Level.INFO,"plugin",()->message,null));}};
        manager=new PluginManager(context,measured,monotonic,failure,message->host.telemetry().flatMap(t->t.logging()).ifPresent(l->l.log(LoggingRuntime.Level.WARNING,"plugin",()->message,null)),count->host.telemetry().ifPresent(t->t.metrics().add(RuntimeMetrics.Counter.skipped_plugin_callbacks,count)));
        host.plugins(this);var operations=new ArrayList<Supplier<CompletionStage<Void>>>();if(settings.enabled())for(var candidate:catalog.candidates())operations.add(()->manager.load(candidate.factory(),p->settings.permits(candidate.stem(),p.name())).thenApply(_->null));ready=sequence.run(operations.iterator());
    }
    public CompletionStage<Void> ready(){sequence.owned();return ready;}
    public PluginManager manager(){sequence.owned();return manager;}
    public CompletionStage<Void> event(ModeEvents.Event event){sequence.owned();return stopping==null?manager.event(event,null):CompletableFuture.completedFuture(null);}
    public CompletionStage<Void> tick(int tick){sequence.owned();return stopping==null?manager.event(ModeEvents.Event.of("on_tick",tick),budget):CompletableFuture.completedFuture(null);}
    public boolean stopped(){sequence.owned();return stopping!=null&&stopping.toCompletableFuture().isDone();}
    public CompletionStage<Void> shutdown(){sequence.owned();if(stopping!=null)return stopping;var result=new CompletableFuture<Void>();stopping=result.minimalCompletionStage();ready.whenComplete((unused,boot)->sequence.resume(()->manager.stop().whenComplete((ignored,error)->sequence.resume(()->{
        try{worker.execute(()->{Throwable problem=error==null?boot:error;try{catalog.close();}catch(Exception close){if(problem==null)problem=close;else problem.addSuppressed(close);}Throwable cause=problem;sequence.resume(()->{if(cause==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(cause));},result);});}catch(RuntimeException rejected){result.completeExceptionally(rejected);}
    },result)),result));return stopping;}
}
