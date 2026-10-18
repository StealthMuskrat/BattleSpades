// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerSequence;

/** Serialized service owns its lock; this policy performs the destructive boundary and guarded restoration. */
public final class SceneRollover<C> {
    public interface Peer {
        boolean inGame();boolean carriesScene();void arm();void send(GamePacket packet);void gate();
        CompletionStage<Void> detach(TransitionMode oldMode);CompletionStage<Boolean> reload();void disconnect(int reason);
    }
    public record State(String map,String code,TransitionMode mode,Object world,Object fog){}
    public interface Services<C> {
        State state();List<Peer> peers();List<TeamState> teams();boolean hasMode(String code);TransitionMode createMode(String code);
        void notice(String message);void resetVote();void broadcast(GamePacket packet);void flush();
        CompletionStage<Void> prepareBots();CompletionStage<Void> drainLeaves(TransitionMode mode);void discardTimeline();
        double graceSeconds();CompletionStage<Void> sleep(double seconds);void resetRuntime();void resetRepair();void resetJournal();void resetHud();
        void notePlayed(String map);void installWorld(String map,String code,C candidate);void setMode(TransitionMode mode);
        CompletionStage<Void> rebindBots();void restore(State old);void failure(String label,Throwable error);
    }
    private final OwnerSequence owner;
    private final Services<C> services;
    public SceneRollover(Services<C> services,Executor resume){this.services=Objects.requireNonNull(services);owner=new OwnerSequence(resume);}
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    public CompletionStage<RoundEndSequence.Result> run(String map,String code,C candidate){return run(map,code,candidate,false);}
    public CompletionStage<RoundEndSequence.Result> run(String map,String code,C candidate,boolean ownEnding){
        owner.owned();if(!services.hasMode(code))return CompletableFuture.completedFuture(new RoundEndSequence.Result(false,false,"Unknown mode: "+code));
        var all=List.copyOf(services.peers());var carried=all.stream().filter(Peer::carriesScene).toList();var loading=all.stream().filter(p->!p.carriesScene()).toList();var pregame=carried.stream().filter(p->!p.inGame()).toList();var old=services.state();var failed=new ArrayList<>(loading);var result=new CompletableFuture<RoundEndSequence.Result>();
        var actions=new ArrayList<Supplier<CompletionStage<Void>>>();
        actions.add(()->{services.notice("Loading "+map+" ("+code.toUpperCase(Locale.ROOT)+")...");services.resetVote();for(var peer:carried)peer.arm();
            var flush=TransitionPackets.audioFlush();var ended=new SimplePackets.MapEnded();services.broadcast(flush);services.broadcast(ended);for(var peer:pregame){peer.send(flush);peer.send(ended);}services.flush();
            for(var peer:all)peer.gate();for(var peer:loading)peer.disconnect(18);return old.mode()==null?done():old.mode().cancelForTransition(ownEnding);});
        actions.add(()->{if(old.mode()!=null)old.mode().beginRetirement(true);return services.prepareBots();});
        actions.add(()->old.mode()==null?done():services.drainLeaves(old.mode()));
        for(var peer:all)actions.add(()->peer.detach(old.mode()));
        actions.add(()->old.mode()==null?done():old.mode().deactivateForTransition(ownEnding));
        actions.add(()->{services.resetVote();services.discardTimeline();double grace=TransitionPackets.bound(services.graceSeconds(),5);return grace==0?done():services.sleep(grace);});
        actions.add(()->{services.resetRuntime();services.resetRepair();services.resetJournal();services.resetHud();services.notePlayed(old.map());services.installWorld(map,code,candidate);
            for(var team:services.teams())team.reset();var mode=Objects.requireNonNull(services.createMode(code));services.setMode(mode);return mode.startMode();});
        actions.add(services::rebindBots);
        actions.add(()->reload(carried,failed));
        actions.add(()->{services.flush();return done();});
        owner.run(actions.iterator()).whenComplete((unused,error)->owner.resume(()->{
            if(error==null){result.complete(new RoundEndSequence.Result(true,!failed.isEmpty(),"Session changed to "+map+" ("+code.toUpperCase(Locale.ROOT)+")"));return;}
            var cause=OwnerSequence.cause(error);if(cause instanceof Error||cause instanceof CancellationException){result.completeExceptionally(cause);return;}
            services.failure("session rollover",cause);var failedMode=services.state().mode();services.restore(old);
            for(var peer:all){try{peer.gate();peer.disconnect(13);}catch(RuntimeException retire){services.failure("retire transition client",retire);}}
            recover(failedMode,old.mode()).whenComplete((ignored,fault)->owner.resume(()->{if(fault!=null)result.completeExceptionally(OwnerSequence.cause(fault));else result.complete(new RoundEndSequence.Result(false,!all.isEmpty(),"Session change failed safely; reconnect after checking server log"));},result));
        },result));return result.minimalCompletionStage();
    }
    private CompletionStage<Void> reload(List<Peer> peers,List<Peer> failed){
        // Start every peer before awaiting any result, matching gather(return_exceptions=True).
        var outcomes=new ArrayList<CompletableFuture<Boolean>>();
        for(var peer:peers){try{outcomes.add(Objects.requireNonNull(peer.reload()).toCompletableFuture());}catch(Throwable error){outcomes.add(CompletableFuture.failedFuture(error));}}
        var finished=CompletableFuture.allOf(outcomes.stream().map(f->f.handle((v,e)->null)).toArray(CompletableFuture<?>[]::new));var done=new CompletableFuture<Void>();
        finished.whenComplete((unused,error)->owner.resume(()->{for(int i=0;i<peers.size();i++){var outcome=outcomes.get(i);boolean ok=false;try{ok=Boolean.TRUE.equals(outcome.join());}catch(CompletionException|CancellationException failure){services.failure("scene reload",OwnerSequence.cause(failure));}
            if(!ok){failed.add(peers.get(i));peers.get(i).disconnect(18);}}done.complete(null);},done));return done.minimalCompletionStage();
    }
    private CompletionStage<Void> recover(TransitionMode failed,TransitionMode old){
        var actions=new ArrayList<Supplier<CompletionStage<Void>>>();if(failed!=null&&failed!=old)actions.add(()->owner.isolate(failed::deactivate,e->services.failure("retire aborted mode",e)));
        actions.add(()->{services.discardTimeline();return done();});if(old!=null)actions.add(()->owner.isolate(()->{for(var team:services.teams())team.reset();return old.startMode();},e->services.failure("restart restored mode",e)));
        actions.add(()->owner.isolate(services::rebindBots,e->services.failure("rebind bots after rollback",e)));return owner.run(actions.iterator());
    }
}
