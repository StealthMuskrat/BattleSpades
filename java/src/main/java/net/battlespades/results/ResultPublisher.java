// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.results;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Bounded owner reservation, worker-only persistence and serialized acknowledge-after-accept delivery. */
public final class ResultPublisher {
    @FunctionalInterface public interface Sender {CompletionStage<Boolean> post(ResultOutbox.Event event);}
    private final Thread owner=Thread.currentThread();private final ResultOutbox outbox;private final String server;private final Executor io,resume;private final Sender sender;private final Consumer<Throwable> failure;
    private final LinkedHashMap<String,ResultOutbox.Event> pending=new LinkedHashMap<>();private CompletableFuture<Void> active,shutdown;private boolean closing;private double retryAt;
    public ResultPublisher(ResultOutbox outbox,String server,Executor io,Executor resume,Sender sender,Consumer<Throwable> failure){this.outbox=Objects.requireNonNull(outbox);ResultOutbox.server(server);this.server=server;this.io=Objects.requireNonNull(io);this.resume=Objects.requireNonNull(resume);this.sender=Objects.requireNonNull(sender);this.failure=Objects.requireNonNull(failure);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Result publisher outside owner");}
    public void reserveSlot(){owned();if(closing)throw new IllegalStateException("Result publisher is closing");if(pending.size()>=256)throw new IllegalStateException("Round result memory queue is full; check durable outbox");}
    public void offer(ResultOutbox.Event event){owned();if(closing)throw new IllegalStateException("Result publisher is closing");if(!event.serverId().equals(server))throw new IllegalArgumentException("Result belongs to another server");var old=pending.get(event.eventId());if(old!=null){if(!old.equals(event))throw new IllegalArgumentException("Result identity reused");return;}reserveSlot();pending.put(event.eventId(),event);}
    public String serverId(){owned();return server;}
    public int pendingMemory(){owned();return pending.size();}public boolean flushing(){owned();return active!=null;}
    @FunctionalInterface private interface Disk<T>{T run()throws IOException;}
    private <T>CompletionStage<T> disk(Disk<T> operation){try{return CompletableFuture.supplyAsync(()->{try{return operation.run();}catch(IOException error){throw new CompletionException(error);}},io);}catch(RuntimeException error){return CompletableFuture.failedFuture(error);}}
    private CompletionStage<Void> persist(){var events=List.copyOf(pending.values());if(events.isEmpty())return CompletableFuture.completedFuture(null);return disk(()->{outbox.putMany(events);return null;}).thenAcceptAsync(unused->{owned();for(var event:events)pending.remove(event.eventId(),event);},resume);}
    /** Concurrent callers share the transaction; cancelling a returned dependent future cannot cancel disk IO. */
    public CompletionStage<Void> flush(){owned();if(active!=null)return active.minimalCompletionStage();if(closing)return CompletableFuture.failedFuture(new IllegalStateException("Result publisher is closing"));var result=new CompletableFuture<Void>();active=result;cycle(result);result.whenComplete((unused,error)->{owned();if(active==result)active=null;});return result.minimalCompletionStage();}
    private void finish(CompletableFuture<Void> result,Throwable error){owned();if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));}
    private void cycle(CompletableFuture<Void> result){
        persist().thenComposeAsync(unused->{owned();return closing?CompletableFuture.completedFuture(List.<ResultOutbox.Event>of()):disk(()->outbox.pending(server,64));},resume).whenCompleteAsync((events,error)->{
            owned();if(error!=null){finish(result,error);return;}if(events.isEmpty()){if(!pending.isEmpty())cycle(result);else finish(result,null);return;}
            var operations=events.stream().<Supplier<CompletionStage<Void>>>map(event->()->{if(closing)return CompletableFuture.completedFuture(null);return sender.post(event).thenComposeAsync(accepted->{owned();if(!accepted)throw new CompletionException(new IOException("Master rejected round result; retained for retry"));if(closing)return CompletableFuture.completedFuture(null);return disk(()->{outbox.acknowledge(event.eventId());return null;});},resume);}).iterator();
            new OwnerSequence(resume).run(operations).whenCompleteAsync((unused,failed)->{owned();if(failed!=null)finish(result,failed);else if(closing)persist().whenCompleteAsync((saved,saveError)->finish(result,saveError),resume);else cycle(result);},resume);
        },resume);
    }
    public void pulse(double now){owned();if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("Result retry clock");if(closing||active!=null||now<retryAt)return;retryAt=now+5;flush().whenComplete((unused,error)->{owned();if(error!=null)failure.accept(OwnerSequence.cause(error));});}
    /** Stop new sends, let any in-flight IO settle, then persist reservations without requiring master availability. */
    public CompletionStage<Void> shutdown(){owned();if(shutdown!=null&&!shutdown.isCompletedExceptionally())return shutdown.minimalCompletionStage();closing=true;shutdown=new CompletableFuture<>();var before=active==null?CompletableFuture.<Void>completedFuture(null):active;before.handleAsync((unused,error)->null,resume).thenComposeAsync(unused->persist(),resume).whenCompleteAsync((unused,error)->finish(shutdown,error),resume);return shutdown.minimalCompletionStage();}
}
