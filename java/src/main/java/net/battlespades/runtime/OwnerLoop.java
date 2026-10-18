// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.runtime;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Bounded host-driven owner queue and monotonic timers. No timer thread mutates gameplay. */
public final class OwnerLoop implements Executor,AutoCloseable {
    private record Timer(long deadline,long order,CompletableFuture<Void> result){}
    private final Thread owner=Thread.currentThread();
    private final Object lock=new Object();
    private final ArrayDeque<Runnable> ready=new ArrayDeque<>();
    private final PriorityQueue<Timer> timers=new PriorityQueue<>(Comparator.comparingLong(Timer::deadline).thenComparingLong(Timer::order));
    private final int capacity,budget;
    private final Consumer<Throwable> failure;
    private long now,order;
    private boolean closed,pumping;
    public OwnerLoop(long initialNanos,int capacity,int budget,Consumer<Throwable> failure){
        if(initialNanos<0||capacity<1||budget<1)throw new IllegalArgumentException("Owner loop bounds");
        now=initialNanos;this.capacity=capacity;this.budget=budget;this.failure=Objects.requireNonNull(failure);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Owner loop outside owner");}
    private void available(){if(closed)throw new RejectedExecutionException("Owner loop closed");if(ready.size()+timers.size()>=capacity)throw new RejectedExecutionException("Owner loop full");}
    /** Always queued, including calls made by the owner: retained admin requests cannot run inline. */
    @Override public void execute(Runnable work){Objects.requireNonNull(work);synchronized(lock){available();ready.addLast(work);}}
    public CompletionStage<Void> sleep(double seconds){
        owned();if(!Double.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Timer duration");
        double nanos=Math.ceil(seconds*1_000_000_000.0);if(nanos>=Long.MAX_VALUE||nanos>Long.MAX_VALUE-now)throw new IllegalArgumentException("Timer overflow");
        var result=new CompletableFuture<Void>();Timer timer;
        synchronized(lock){available();timer=new Timer(Math.addExact(now,(long)nanos),order++,result);timers.add(timer);}
        // Expose the actual cancellable future: minimalCompletionStage would only cancel a detached copy.
        result.whenComplete((unused,error)->{synchronized(lock){timers.remove(timer);}});return result;
    }
    public int pending(){synchronized(lock){return ready.size()+timers.size();}}
    public long nowNanos(){owned();return now;}
    /** Run one fixed ready budget. Work appended by callbacks or workers waits for the next host pump. */
    public int pump(long nanos){
        owned();if(pumping)throw new IllegalStateException("Owner loop pump reentered");if(nanos<now)throw new IllegalArgumentException("Owner clock moved backwards");
        now=nanos;int count;synchronized(lock){while(!timers.isEmpty()&&timers.peek().deadline()<=now){var timer=timers.remove();ready.addLast(()->timer.result().complete(null));}count=Math.min(budget,ready.size());}
        pumping=true;int ran=0;
        try{while(ran<count){Runnable work;synchronized(lock){work=ready.removeFirst();}ran++;try{work.run();}catch(RuntimeException error){failure.accept(error);}}return ran;}
        finally{pumping=false;}
    }
    /** Reject new work and cancel waits. Already queued continuations remain available to pump. */
    @Override public void close(){
        owned();List<Timer> cancelled;synchronized(lock){if(closed)return;closed=true;cancelled=List.copyOf(timers);timers.clear();}
        for(var timer:cancelled)timer.result().cancel(false);
    }
}
