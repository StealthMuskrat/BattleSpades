// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.runtime.OwnerSequence;

/** In-place BaseMode restart keeps the GameScene and uses the ordinary restock/respawn service. */
public final class RoundRestart<P> {
    public interface Services<P> {
        boolean scoreboard();void forceScores(boolean forced);CompletionStage<Void> prepareBots();
        void resetRuntime();List<TeamState> teams();CompletionStage<Void> startMode();void teamScore(TeamState team);
        List<P> players();boolean playable(P player);boolean connected(P player);void respawn(P player);
        void respawnFailure(P player,Throwable error);CompletionStage<Void> resetBots();
    }
    private final OwnerSequence owner;
    private final Services<P> services;
    private boolean restarting;
    private boolean cancelled;
    private CompletableFuture<?> pending;
    private CompletableFuture<Void> result;
    public RoundRestart(Services<P> services,Executor resume){this.services=Objects.requireNonNull(services);owner=new OwnerSequence(resume);}
    public CompletionStage<Void> run(){
        owner.owned();if(restarting)throw new IllegalStateException("Round restart already suspended");restarting=true;cancelled=false;result=new CompletableFuture<>();
        var actions=List.<Supplier<CompletionStage<Void>>>of(
            ()->{if(services.scoreboard())services.forceScores(false);return await(services::prepareBots);},
            ()->{services.resetRuntime();for(var team:services.teams())team.reset();return await(services::startMode);},
            ()->{for(var team:services.teams())services.teamScore(team);
                for(var player:List.copyOf(services.players()))try{if(services.playable(player)&&services.connected(player))services.respawn(player);}catch(RuntimeException error){services.respawnFailure(player,error);}
                return await(services::resetBots);});
        var done=result;owner.run(actions.stream().<Supplier<CompletionStage<Void>>>map(action->()->{check();return action.get();}).iterator()).whenComplete((unused,error)->owner.resume(()->{restarting=false;pending=null;if(error==null)done.complete(null);else done.completeExceptionally(OwnerSequence.cause(error));},done));return done.minimalCompletionStage();
    }
    private void check(){if(cancelled)throw new CancellationException("Round restart retired");}
    private CompletionStage<Void> await(Supplier<CompletionStage<Void>> operation){
        check();var observed=Objects.requireNonNull(operation.get()).toCompletableFuture();pending=observed;var done=new CompletableFuture<Void>();
        observed.whenComplete((unused,error)->owner.resume(()->{check();pending=null;if(error==null)done.complete(null);else done.completeExceptionally(OwnerSequence.cause(error));},done));if(cancelled)observed.cancel(false);return done.minimalCompletionStage();
    }
    /** Invalidate the restart continuation before the old preparation/start service can finish. */
    public CompletionStage<Void> cancel(){
        owner.owned();if(!restarting)return CompletableFuture.completedFuture(null);cancelled=true;if(pending!=null)pending.cancel(false);
        var done=new CompletableFuture<Void>();result.whenComplete((unused,error)->owner.resume(()->{var cause=error==null?null:OwnerSequence.cause(error);if(cause==null||cause instanceof CancellationException)done.complete(null);else done.completeExceptionally(cause);},done));return done.minimalCompletionStage();
    }
}
