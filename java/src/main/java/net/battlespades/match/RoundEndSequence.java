// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** Delayed BaseMode ending policy. Map preflight, vote and timer implementations are required services. */
public final class RoundEndSequence {
    public record Result(boolean ok,boolean reconnectRequired,String message){public Result{Objects.requireNonNull(message);}}
    public interface Transition {
        boolean supportsEndScreen();
        CompletionStage<Result> changeAfterScores(String map,double seconds,Integer headline);
        CompletionStage<Result> changeMap(String map);
        CompletionStage<Result> restart();
        boolean busyAllowingCurrentRequest();
    }
    public interface Services {
        void endingMusic();boolean scoreboard();void forceScores(boolean forced);void botsEnd();
        CompletionStage<Void> sleep(double seconds);void statistics(Integer winner);
        CompletionStage<Void> waitForVote();String consumeMap();String currentMap();double screenSeconds();
        Integer headline(Integer winner);Transition transition();CompletionStage<Void> restartInPlace();
        /** Retire any nested restart/transition task before releasing this ending request. */
        CompletionStage<Void> cancelPending();
        boolean recoverable();void failed();void failure(String label,Throwable error);
    }
    private final OwnerSequence owner;
    private final Services services;
    private Run active;
    private CompletableFuture<Void> cancellation;
    private final class Run {
        final CompletableFuture<Void> result=new CompletableFuture<>();
        CompletableFuture<?> pending;
        boolean cancelled;
        void check(){if(cancelled)throw new CancellationException("Round ending retired");}
        <T> CompletionStage<T> await(Supplier<CompletionStage<T>> operation){
            check();var stage=Objects.requireNonNull(operation.get());var observed=stage.toCompletableFuture();pending=observed;var done=new CompletableFuture<T>();
            observed.whenComplete((value,error)->owner.resume(()->{check();pending=null;if(error==null)done.complete(value);else done.completeExceptionally(OwnerSequence.cause(error));},done));
            if(cancelled)observed.cancel(false);return done.minimalCompletionStage();
        }
        CompletionStage<Void> actions(List<Supplier<CompletionStage<Void>>> actions){return owner.run(actions.stream().<Supplier<CompletionStage<Void>>>map(action->()->{check();return action.get();}).iterator());}
    }
    public RoundEndSequence(Services services,Executor resume){this.services=Objects.requireNonNull(services);owner=new OwnerSequence(resume);}
    public boolean running(){owner.owned();return active!=null||cancellation!=null;}
    public CompletionStage<Void> run(Integer winner){
        owner.owned();if(running())throw new IllegalStateException("Round ending already running");var task=new Run();active=task;
        task.result.whenComplete((unused,error)->owner.resume(()->{if(active==task)active=null;},new CompletableFuture<>()));
        task.actions(List.of(
            ()->{services.endingMusic();if(services.scoreboard())services.forceScores(true);services.botsEnd();return task.await(()->services.sleep(5));},
            ()->{services.statistics(winner);return task.await(services::waitForVote);},
            ()->{String next=services.consumeMap();String current=services.currentMap();if(next!=null&&(next.isEmpty()||current!=null&&!current.isEmpty()&&net.battlespades.player.NameUnicode.casefold(next).equals(net.battlespades.player.NameUnicode.casefold(current))))next=null;
                double requested=services.screenSeconds();double dwell=Double.isNaN(requested)?0:Math.min(120,Math.max(0,requested));var transition=services.transition();String target=next;
                if(transition==null)return task.actions(List.of(()->task.await(()->services.sleep(dwell)),()->task.await(services::restartInPlace)));
                if(target==null)return task.actions(List.of(()->task.await(()->services.sleep(dwell)),()->checked(task,()->transition.restart())));
                CompletionStage<Result> change=transition.supportsEndScreen()?task.await(()->transition.changeAfterScores(target,dwell,services.headline(winner))):
                    task.actions(List.of(()->task.await(()->services.sleep(dwell)))).thenCompose(unused->task.await(()->transition.changeMap(target)));
                var done=new CompletableFuture<Void>();change.whenComplete((result,error)->owner.resume(()->{
                    task.check();if(error!=null){done.completeExceptionally(OwnerSequence.cause(error));return;}
                    CompletionStage<Void> finish=!result.ok()&&!result.reconnectRequired()?checked(task,transition::restart):requireOk(result);
                    finish.whenComplete((unused,fault)->owner.resume(()->{if(fault==null)done.complete(null);else done.completeExceptionally(OwnerSequence.cause(fault));},done));
                },done));return done.minimalCompletionStage();}
        )).whenComplete((unused,error)->owner.resume(()->{
            if(error==null){finish(task,null);return;}var cause=OwnerSequence.cause(error);
            if(cause instanceof CancellationException||cause instanceof Error){finish(task,cause);return;}
            services.failure("end sequence",cause);services.failed();
            recover(task).whenComplete((ignored,fault)->owner.resume(()->finish(task,fault==null?null:OwnerSequence.cause(fault)),task.result));
        },task.result));return task.result.minimalCompletionStage();
    }
    private CompletionStage<Void> checked(Run task,Supplier<CompletionStage<Result>> operation){
        return task.await(operation).thenCompose(RoundEndSequence::requireOk);
    }
    private static CompletionStage<Void> requireOk(Result result){return result.ok()?CompletableFuture.completedFuture(null):CompletableFuture.failedFuture(new IllegalStateException(result.message()));}
    private CompletionStage<Void> recover(Run task){
        task.check();if(!services.recoverable())return CompletableFuture.completedFuture(null);var transition=services.transition();
        if(transition!=null&&transition.busyAllowingCurrentRequest())return CompletableFuture.completedFuture(null);
        var done=new CompletableFuture<Void>();owner.isolate(()->task.await(services::restartInPlace),error->{
            services.failure("fallback in-place restart",error);if(services.scoreboard())try{services.forceScores(false);}catch(RuntimeException release){services.failure("release forced scoreboard",release);}
        }).whenComplete((unused,error)->owner.resume(()->{if(error==null)done.complete(null);else done.completeExceptionally(OwnerSequence.cause(error));},done));return done.minimalCompletionStage();
    }
    private void finish(Run task,Throwable error){if(active==task)active=null;if(error==null)task.result.complete(null);else task.result.completeExceptionally(error);}
    /** Cancel the retained wait and await its owner continuation before a replacement can be started. */
    public CompletionStage<Void> cancel(){
        owner.owned();if(cancellation!=null)return cancellation.minimalCompletionStage();var task=active;if(task==null)return CompletableFuture.completedFuture(null);task.cancelled=true;
        var done=new CompletableFuture<Void>();cancellation=done;CompletionStage<Void> cleanup;
        try{cleanup=Objects.requireNonNull(services.cancelPending());}catch(RuntimeException error){cleanup=CompletableFuture.failedFuture(error);}
        if(task.pending!=null)task.pending.cancel(false);var retirement=cleanup;
        owner.run(List.<Supplier<CompletionStage<Void>>>of(()->retirement,()->task.result.<Void>handle((unused,error)->{
            var cause=error==null?null:OwnerSequence.cause(error);if(cause!=null&&!(cause instanceof CancellationException))throw new CompletionException(cause);return null;
        })).iterator()).whenComplete((unused,error)->owner.resume(()->{cancellation=null;if(error==null)done.complete(null);else done.completeExceptionally(OwnerSequence.cause(error));},done));return done.minimalCompletionStage();
    }
}
