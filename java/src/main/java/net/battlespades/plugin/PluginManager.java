// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.plugin;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.ModeEvents;
import net.battlespades.runtime.OwnerSequence;
/** Source lifecycle ordering, enabled gates and serial per-event callback budgets. */
public final class PluginManager {
    public record Info(String name,String version,String author,String description,boolean enabled){}
    private static final class Entry{final RuntimePlugin plugin;boolean enabled=true;Entry(RuntimePlugin p){plugin=p;}}
    private final LinkedHashMap<String,Entry> plugins=new LinkedHashMap<>();private final Set<String> loading=new HashSet<>();
    private final Set<CompletionStage<?>> pending=Collections.newSetFromMap(new IdentityHashMap<>());
    private final RuntimePlugin.Context context;private final OwnerSequence sequence;private final DoubleSupplier measured,monotonic;private final BiConsumer<String,Throwable> failure;private final Consumer<String> warning;private final LongConsumer skipped;
    private double lastWarning;private CompletionStage<Void> stopping;
    public PluginManager(RuntimePlugin.Context context,DoubleSupplier measured,DoubleSupplier monotonic,BiConsumer<String,Throwable> failure,Consumer<String> warning,LongConsumer skipped){this.context=Objects.requireNonNull(context);sequence=new OwnerSequence(context.owner());this.measured=Objects.requireNonNull(measured);this.monotonic=Objects.requireNonNull(monotonic);this.failure=Objects.requireNonNull(failure);this.warning=Objects.requireNonNull(warning);this.skipped=Objects.requireNonNull(skipped);}
    private void accepting(){sequence.owned();if(stopping!=null)throw new IllegalStateException("Plugins stopping");}
    public List<Info> all(){sequence.owned();return plugins.values().stream().map(e->new Info(e.plugin.name(),e.plugin.version(),e.plugin.author(),e.plugin.description(),e.enabled)).toList();}
    public Optional<RuntimePlugin> get(String name){sequence.owned();var entry=plugins.get(name);return entry==null?Optional.empty():Optional.of(entry.plugin);}
    private <T> CompletionStage<T> retain(CompletionStage<T> stage){pending.add(stage);stage.whenComplete((unused,error)->sequence.resume(()->pending.remove(stage),new CompletableFuture<>()));return stage;}
    public CompletionStage<Boolean> load(Supplier<? extends RuntimePlugin> factory){return load(factory,_ ->true);}
    public CompletionStage<Boolean> load(Supplier<? extends RuntimePlugin> factory,Predicate<RuntimePlugin> allowed){
        accepting();var result=new CompletableFuture<Boolean>();RuntimePlugin plugin;String name;
        try{plugin=Objects.requireNonNull(factory.get());name=Objects.requireNonNull(plugin.name());if(!allowed.test(plugin)||plugins.containsKey(name)||!loading.add(name))return CompletableFuture.completedFuture(false);}
        catch(RuntimeException|ServiceConfigurationError|LinkageError invalid){failure.accept("plugin construction",invalid);return CompletableFuture.completedFuture(false);}
        boolean[] loaded={true};CompletionStage<Void> stage;try{stage=sequence.isolate(()->plugin.onLoad(context),error->{loaded[0]=false;failure.accept("plugin load:"+name,error);});}catch(Error fatal){loading.remove(name);throw fatal;}
        stage.whenComplete((unused,error)->sequence.resume(()->{loading.remove(name);if(error!=null)result.completeExceptionally(OwnerSequence.cause(error));else{if(loaded[0])plugins.put(name,new Entry(plugin));result.complete(loaded[0]);}},result));return retain(result.minimalCompletionStage());
    }
    public CompletionStage<Boolean> enable(String name,boolean enabled){accepting();var entry=plugins.get(name);if(entry==null)return CompletableFuture.completedFuture(false);entry.enabled=enabled;var result=new CompletableFuture<Boolean>();CompletionStage<Void> stage;try{stage=Objects.requireNonNull(enabled?entry.plugin.onEnable():entry.plugin.onDisable());}catch(RuntimeException error){return CompletableFuture.failedFuture(error);}stage.whenComplete((unused,error)->sequence.resume(()->{if(error==null)result.complete(true);else result.completeExceptionally(OwnerSequence.cause(error));},result));return retain(result.minimalCompletionStage());}
    public CompletionStage<Boolean> unload(String name){accepting();return remove(name);}
    private CompletionStage<Boolean> remove(String name){var entry=plugins.get(name);if(entry==null)return CompletableFuture.completedFuture(false);var result=new CompletableFuture<Boolean>();var stage=sequence.isolate(entry.plugin::onUnload,error->failure.accept("plugin unload:"+name,error));stage.whenComplete((unused,error)->sequence.resume(()->{if(error!=null)result.completeExceptionally(OwnerSequence.cause(error));else{plugins.remove(name,entry);result.complete(true);}},result));return retain(result.minimalCompletionStage());}
    public CompletionStage<Void> event(ModeEvents.Event event,Double budgetMillis){
        accepting();double start=measured.getAsDouble(),budget=budgetMillis==null?Double.POSITIVE_INFINITY:Math.max(0,budgetMillis)/1000.;
        var operations=new ArrayList<Supplier<CompletionStage<Void>>>();for(var entry:List.copyOf(plugins.values()))operations.add(()->{
            if(!entry.enabled||plugins.get(entry.plugin.name())!=entry)return CompletableFuture.completedFuture(null);
            if(budgetMillis!=null&&measured.getAsDouble()-start>=budget){skipped.accept(1);double now=monotonic.getAsDouble();if(now-lastWarning>=1){lastWarning=now;warning.accept("Plugin event "+event.name()+" exceeded "+budgetMillis+"ms budget; skipping remaining callbacks");}return CompletableFuture.completedFuture(null);}
            return sequence.isolate(()->entry.plugin.onEvent(event),error->failure.accept("plugin "+entry.plugin.name()+":"+event.name(),error));
        });return retain(sequence.run(operations.iterator()));
    }
    public boolean stopped(){sequence.owned();return stopping!=null&&stopping.toCompletableFuture().isDone();}
    /** Wait for accepted lifecycle/events, then unload every plugin in registration order. */
    public CompletionStage<Void> stop(){sequence.owned();if(stopping!=null)return stopping;var result=new CompletableFuture<Void>();stopping=result.minimalCompletionStage();var accepted=List.copyOf(pending);var all=CompletableFuture.allOf(accepted.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new));all.whenComplete((unused,error)->sequence.resume(()->{
        var operations=new ArrayList<Supplier<CompletionStage<Void>>>();for(String name:List.copyOf(plugins.keySet()))operations.add(()->remove(name).thenApply(_->null));sequence.run(operations.iterator()).whenComplete((ignored,cleanup)->sequence.resume(()->{if(cleanup!=null)result.completeExceptionally(OwnerSequence.cause(cleanup));else if(error!=null)result.completeExceptionally(OwnerSequence.cause(error));else result.complete(null);},result));
    },result));return stopping;}
}
