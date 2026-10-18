// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Actual serial SimulationRuntime mode-event drain, including fixed timeout remainder and isolated mode/plugin faults. */
public final class ModeEvents {
    public record Event(String name,List<Object> arguments){
        public Event{Objects.requireNonNull(name);arguments=Collections.unmodifiableList(new ArrayList<>(arguments));}
        public static Event of(String name,Object... args){return new Event(name,Arrays.asList(args));}
    }
    public interface Mode {
        RoundClock clock();
        CompletionStage<Void> handle(Event event);
        CompletionStage<Void> tick(int loop);
    }
    private final OwnerSequence sequence;
    private final Supplier<Mode> mode;
    private final Function<Event,CompletionStage<Void>> plugins;
    private final BiConsumer<String,Throwable> failure;
    private final Deque<Event> events=new ArrayDeque<>();
    private final int capacity,budget;
    private boolean draining;
    private long dropped;
    public ModeEvents(int capacity,int budget,Supplier<Mode> mode,Function<Event,CompletionStage<Void>> plugins,BiConsumer<String,Throwable> failure,Executor resume){
        if(capacity<1||budget<1)throw new IllegalArgumentException("Mode queue limits");this.capacity=capacity;this.budget=budget;this.mode=Objects.requireNonNull(mode);this.plugins=Objects.requireNonNull(plugins);this.failure=Objects.requireNonNull(failure);sequence=new OwnerSequence(resume);
    }
    public boolean offer(Event event){sequence.owned();Objects.requireNonNull(event);if(events.size()>=capacity){dropped++;return false;}events.addLast(event);return true;}
    public boolean hasMode(){sequence.owned();return mode.get()!=null;}
    public int size(){sequence.owned();return events.size();}public long dropped(){sequence.owned();return dropped;}
    public List<Event> snapshot(){sequence.owned();return List.copyOf(events);}public void clear(){sequence.owned();events.clear();}
    public CompletionStage<Void> tick(int loop){
        sequence.owned();if(draining)throw new IllegalStateException("Mode drain already suspended");if(mode.get()==null)return CompletableFuture.completedFuture(null);draining=true;
        var initial=mode.get().clock();Integer remaining=initial.timeoutRemaining();int count=initial.drainBudget(events.size(),budget);
        // Pop each event only when its turn begins: handlers may append or purge the shared deque.
        var operations=new ArrayList<Supplier<CompletionStage<Void>>>();Event[] current={null};
        for(int i=0;i<count;i++){
            operations.add(()->{current[0]=events.pollFirst();if(current[0]==null)return CompletableFuture.completedFuture(null);var active=mode.get();if(active==null)return CompletableFuture.completedFuture(null);var event=current[0];return sequence.isolate(()->active.handle(event),e->failure.accept("mode_event:"+event.name(),e));});
            operations.add(()->{var event=current[0];return event==null?CompletableFuture.completedFuture(null):sequence.isolate(()->plugins.apply(event),e->failure.accept("plugin_event:"+event.name(),e));});
        }
        operations.add(()->{var active=Objects.requireNonNull(mode.get(),"Mode removed during frame");if(remaining!=null&&!active.clock().ended)active.clock().timeoutRemainder(Math.max(0,remaining-count));return active.tick(loop);});
        var result=new CompletableFuture<Void>();sequence.run(operations.iterator()).whenComplete((unused,error)->sequence.resume(()->{draining=false;if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));},result));
        return result.minimalCompletionStage();
    }
}
