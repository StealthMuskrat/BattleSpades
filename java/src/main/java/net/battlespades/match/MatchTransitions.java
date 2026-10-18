// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.NameUnicode;
import net.battlespades.runtime.OwnerSequence;
import net.battlespades.scene.*;
import net.battlespades.world.WorldPreflight;

/** Owner-serialized admin and round-ending transitions with isolated worker preflight and retained requests. */
public final class MatchTransitions<C> implements RoundEndSequence.Transition {
    public interface Services<C> extends SceneRollover.Services<C> {
        CompletionStage<Void> restartRound(TransitionMode mode);
        /** Optional resource ownership: called only after a completed change or completed rollback recovery. */
        default void transitionFinished(){}
    }
    private static final String BUSY="Another match transition is already in progress";
    private final OwnerSequence owner;
    private final Services<C> services;
    private final SceneRollover<C> rollover;
    private final BiFunction<String,String,CompletionStage<C>> load;
    private final Executor schedule;
    private boolean preparing,inProgress;
    private CompletableFuture<RoundEndSequence.Result> request;
    public MatchTransitions(Services<C> services,BiFunction<String,String,CompletionStage<C>> load,Executor resume){
        this.services=Objects.requireNonNull(services);this.load=Objects.requireNonNull(load);schedule=Objects.requireNonNull(resume);owner=new OwnerSequence(resume);rollover=new SceneRollover<>(services,resume);
    }
    private static RoundEndSequence.Result result(boolean ok,String message){return new RoundEndSequence.Result(ok,false,message);}
    public boolean busy(){owner.owned();return preparing||inProgress||request!=null&&!request.isDone();}
    public boolean preparing(){owner.owned();return preparing;}public boolean inProgress(){owner.owned();return inProgress;}
    public boolean busyAllowingCurrentRequest(){return busy();}
    public Optional<CompletionStage<RoundEndSequence.Result>> requestCompletion(){owner.owned();return request==null?Optional.empty():Optional.of(request.minimalCompletionStage());}
    private boolean busy(CompletableFuture<RoundEndSequence.Result> permit){return preparing||inProgress||request!=null&&!request.isDone()&&request!=permit;}
    private static String canonical(String value){try{return ModeCatalog.resolve(NameUnicode.strip(value).toLowerCase(Locale.ROOT));}catch(IllegalArgumentException invalid){return null;}}
    private boolean currentMap(String map){return NameUnicode.casefold(WorldPreflight.normalize(services.state().map())).equals(NameUnicode.casefold(map));}
    private boolean currentMode(String code){String old=NameUnicode.strip(services.state().code()).toLowerCase(Locale.ROOT);return Objects.equals(Optional.ofNullable(canonical(old)).orElse(old),code);}
    public RoundEndSequence.Result requestMap(String map,Consumer<String> privateFailure){
        owner.owned();if(busy())return result(false,BUSY);String name=WorldPreflight.normalize(map);if(name.isEmpty())return result(false,"Invalid or empty map name");if(currentMap(name))return result(true,"Map "+name+" is already loaded");
        return retain(permit->change(name,null,null,false,permit,false),privateFailure,"Preparing map "+name);
    }
    public RoundEndSequence.Result requestMode(String mode,Consumer<String> privateFailure){
        owner.owned();if(busy())return result(false,BUSY);String value=NameUnicode.strip(mode).toLowerCase(Locale.ROOT),code=canonical(value);if(code==null)return result(false,"Unknown mode: "+value);if(currentMode(code))return result(true,"Mode "+code.toUpperCase(Locale.ROOT)+" is already active");
        return retain(permit->changeMode(code,permit),privateFailure,"Preparing mode "+code.toUpperCase(Locale.ROOT));
    }
    private RoundEndSequence.Result retain(Function<CompletableFuture<RoundEndSequence.Result>,CompletionStage<RoundEndSequence.Result>> operation,Consumer<String> notice,String accepted){
        var retained=new CompletableFuture<RoundEndSequence.Result>();request=retained;
        try{schedule.execute(()->owner.resume(()->{
            try{operation.apply(retained).whenComplete((outcome,error)->owner.resume(()->{
                try{if(error!=null)retained.completeExceptionally(OwnerSequence.cause(error));else{if(!outcome.ok()&&notice!=null)notice.accept(outcome.message());retained.complete(outcome);}}finally{if(request==retained)request=null;}
            },retained));}catch(Throwable error){if(request==retained)request=null;retained.completeExceptionally(error);}
        },retained));}catch(RuntimeException rejected){request=null;retained.completeExceptionally(rejected);return result(false,"Failed to schedule match transition");}
        return result(true,accepted);
    }
    public boolean supportsEndScreen(){return true;}
    public CompletionStage<RoundEndSequence.Result> changeAfterScores(String map,double seconds,Integer headline){return change(map,seconds,headline,false,null,false);}
    public CompletionStage<RoundEndSequence.Result> changeAfterScoresFromEnding(String map,double seconds,Integer headline){return change(map,seconds,headline,true,null,false);}
    public CompletionStage<RoundEndSequence.Result> changeMap(String map){return change(map,null,null,false,null,false);}
    public CompletionStage<RoundEndSequence.Result> changeMapFromEnding(String map){return change(map,null,null,true,null,false);}
    public CompletionStage<RoundEndSequence.Result> changeMode(String mode){return changeMode(mode,null);}
    private CompletionStage<RoundEndSequence.Result> changeMode(String mode,CompletableFuture<RoundEndSequence.Result> permit){
        owner.owned();if(busy(permit))return CompletableFuture.completedFuture(result(false,BUSY));String value=NameUnicode.strip(mode).toLowerCase(Locale.ROOT),code=canonical(value);
        if(code==null)return CompletableFuture.completedFuture(result(false,"Unknown mode: "+value));if(currentMode(code))return CompletableFuture.completedFuture(result(true,"Mode "+code.toUpperCase(Locale.ROOT)+" is already active"));
        return change(WorldPreflight.normalize(services.state().map()),null,null,false,permit,true,code);
    }
    private CompletionStage<RoundEndSequence.Result> change(String map,Double seconds,Integer headline,boolean ownEnding,CompletableFuture<RoundEndSequence.Result> permit,boolean modeChange){
        owner.owned();String current=NameUnicode.strip(services.state().code()).toLowerCase(Locale.ROOT);return change(map,seconds,headline,ownEnding,permit,modeChange,Optional.ofNullable(canonical(current)).orElse(current));
    }
    private CompletionStage<RoundEndSequence.Result> change(String map,Double seconds,Integer headline,boolean ownEnding,CompletableFuture<RoundEndSequence.Result> permit,boolean modeChange,String code){
        owner.owned();if(busy(permit))return CompletableFuture.completedFuture(result(false,BUSY));String name=WorldPreflight.normalize(map);
        if(!modeChange){if(name.isEmpty())return CompletableFuture.completedFuture(result(false,"Invalid or empty map name"));if(currentMap(name))return CompletableFuture.completedFuture(result(true,"Map "+name+" is already loaded"));}
        preparing=true;var done=new CompletableFuture<RoundEndSequence.Result>();
        var returned=done.handle((value,error)->{try{if(error!=null)throw new CompletionException(OwnerSequence.cause(error));return value;}finally{preparing=false;inProgress=false;}});
        CompletionStage<C> candidate;try{candidate=Objects.requireNonNull(load.apply(name,code));}catch(Throwable error){preflightFailure(done,error,name,code,modeChange);return returned.minimalCompletionStage();}
        candidate.whenComplete((world,error)->owner.resume(()->{
            if(error!=null){preflightFailure(done,OwnerSequence.cause(error),name,code,modeChange);return;}
            var actions=new ArrayList<Supplier<CompletionStage<Void>>>();
            if(seconds!=null){double dwell=TransitionPackets.bound(seconds,120);actions.add(()->{
                if(TransitionPackets.supportsStatistics(ScenePackets.displayName(services.state().map()))){for(var packet:TransitionPackets.statistics(dwell,headline))services.broadcast(packet);services.flush();}
                return dwell>0?services.sleep(dwell):CompletableFuture.completedFuture(null);
            });}
            actions.add(()->{inProgress=true;return rollover.run(name,code,world,ownEnding).thenAccept(value->{services.transitionFinished();done.complete(value);});});
            owner.run(actions.iterator()).whenComplete((unused,fault)->owner.resume(()->{if(fault!=null)done.completeExceptionally(OwnerSequence.cause(fault));},done));
        },done));return returned.minimalCompletionStage();
    }
    private void preflightFailure(CompletableFuture<RoundEndSequence.Result> done,Throwable error,String map,String code,boolean modeChange){
        var cause=OwnerSequence.cause(error);if(cause instanceof Error||cause instanceof CancellationException){done.completeExceptionally(cause);return;}
        if(cause instanceof IOException||cause instanceof IllegalArgumentException){done.complete(result(false,String.valueOf(cause.getMessage())));return;}
        services.failure("map preflight",cause);done.complete(result(false,modeChange?"Failed to prepare current map for mode "+code.toUpperCase(Locale.ROOT):"Failed to load map: "+map));
    }
    public CompletionStage<RoundEndSequence.Result> restart(){return restart(false);}
    public CompletionStage<RoundEndSequence.Result> restartFromEnding(){return restart(true);}
    private CompletionStage<RoundEndSequence.Result> restart(boolean ownEnding){
        owner.owned();if(busy())return CompletableFuture.completedFuture(result(false,BUSY));inProgress=true;var done=new CompletableFuture<RoundEndSequence.Result>();var mode=services.state().mode();
        if(mode==null){inProgress=false;return CompletableFuture.completedFuture(result(false,"No active game mode"));}
        var actions=List.<Supplier<CompletionStage<Void>>>of(()->mode.cancelForTransition(ownEnding),()->{services.resetVote();mode.beginRetirement(false);return services.drainLeaves(mode);},()->{services.discardTimeline();return services.restartRound(mode);});
        owner.run(actions.iterator()).whenComplete((unused,error)->owner.resume(()->{inProgress=false;if(error==null)done.complete(result(true,"Match restarted"));else{var cause=OwnerSequence.cause(error);if(cause instanceof Error||cause instanceof CancellationException)done.completeExceptionally(cause);else{services.failure("same-map round restart",cause);done.complete(result(false,"Match restart failed; see server log"));}}},done));return done.minimalCompletionStage();
    }
}
