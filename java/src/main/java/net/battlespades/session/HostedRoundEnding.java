// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import net.battlespades.match.*;
import net.battlespades.runtime.*;
import org.tomlj.TomlTable;

/** Host-owned ending task. Scene retirement never disposes the ballot or a nested scene transaction. */
public final class HostedRoundEnding {
    public record Settings(double screenSeconds,boolean headline){
        public Settings{if(!Double.isFinite(screenSeconds))throw new IllegalArgumentException("End screen duration must be finite");screenSeconds=Math.clamp(screenSeconds,0,120);}
        public static Settings from(TomlTable raw){
            Object seconds=raw.get("lobby.end_screen_seconds"),enabled=raw.get("lobby.end_round_headline");
            if(seconds!=null&&!(seconds instanceof Number)||enabled!=null&&!(enabled instanceof Boolean))throw new IllegalArgumentException("Invalid end screen settings");
            return new Settings(seconds==null?12:((Number)seconds).doubleValue(),!Boolean.FALSE.equals(enabled));
        }
    }
    @FunctionalInterface public interface Bots {void end(StandardMatchDomain match);}
    private final ApplicationHost host;private final HostedSceneState state;private final HostedTransitionServices restarts;
    private final OwnerLoop loop;private final OwnerSequence owner;private final Settings settings;private final Bots bots;
    private final BiConsumer<String,Throwable> failure;
    private Active active;private boolean closing;private CompletableFuture<Void> shutdown;
    private static final class Active {
        final StandardMatchDomain match;final RoundEndSequence sequence;
        CompletionStage<Void> completion;boolean fallback;
        Active(StandardMatchDomain match,RoundEndSequence sequence){this.match=match;this.sequence=sequence;}
    }
    /** Bind after the transition service attaches the scene, before any connection or simulation start. */
    public HostedRoundEnding(ApplicationHost host,HostedSceneState state,HostedTransitionServices restarts,OwnerLoop loop,
            Settings settings,Bots bots,BiConsumer<String,Throwable> failure){
        this.host=Objects.requireNonNull(host);this.state=Objects.requireNonNull(state);this.restarts=Objects.requireNonNull(restarts);this.loop=Objects.requireNonNull(loop);owner=new OwnerSequence(loop);this.settings=Objects.requireNonNull(settings);this.bots=Objects.requireNonNull(bots);this.failure=Objects.requireNonNull(failure);
        if(!(state.domain() instanceof StandardMatchDomain match)||!match.attached())throw new IllegalArgumentException("Ending requires an attached standard domain");
        if(host.matchTransitions().isEmpty())throw new IllegalArgumentException("Ending requires the retained transition owner");host.roundEnding(this);
    }
    public boolean running(){owner.owned();return active!=null;}
    public Optional<CompletionStage<Void>> completion(){owner.owned();return active==null?Optional.empty():Optional.of(active.completion);}
    void schedule(StandardMatchDomain match,Integer winner){
        owner.owned();if(closing||match.stopping())return;if(state.domain()!=match||!match.mode().clock().ended)throw new IllegalStateException("Ending requires the current finished round");
        if(active!=null)throw new IllegalStateException("Another round ending is still retained");
        var mode=match.mode();var board=match.scoreboard();var media=match.media();String map=state.snapshot().map();
        Active[] holder={null};var presentation=new RoundPresentation(media.music(),media.ballot(),loop,()->map,packet->host.broadcast(packet,net.battlespades.transport.enet.Delivery.RELIABLE),new RoundPresentation.Bindings(){
            public boolean scoreboard(){return mode.scoreboardEnabled();}public void botsEnd(){bots.end(match);}public void statistics(Integer result){board.gameStats(result);}
            public double screenSeconds(){return settings.screenSeconds();}public Integer headline(Integer result){return settings.headline()?mode.endMessageId(result):null;}
            public RoundEndSequence.Transition transition(){return host.matchTransitions().orElseThrow();}
            public CompletionStage<Void> restartInPlace(){if(state.domain()!=match||closing)return CompletableFuture.failedFuture(new CancellationException("Round replaced"));holder[0].fallback=true;return restarts.restartRound(mode);}
            public CompletionStage<Void> cancelPending(){
                var transition=host.matchTransitions().orElseThrow();
                return owner.run(List.<java.util.function.Supplier<CompletionStage<Void>>>of(transition::retireEnding,()->holder[0].fallback?restarts.cancelRestart():CompletableFuture.completedFuture(null)).iterator());
            }
            public boolean recoverable(){return !closing&&state.domain()==match&&!match.stopping();}public void failed(){mode.endSequenceFailed();}
            public void failure(String label,Throwable error){failure.accept(label,error);}
        });
        var task=new Active(match,new RoundEndSequence(presentation,loop));holder[0]=task;active=task;
        try{task.completion=task.sequence.run(winner);task.completion.whenComplete((unused,error)->owner.resume(()->{if(active==task)active=null;if(error!=null&&!(OwnerSequence.cause(error) instanceof CancellationException))failure.accept("hosted round ending",OwnerSequence.cause(error));},new CompletableFuture<>()));}
        catch(RuntimeException|Error error){if(active==task)active=null;throw error;}
    }
    CompletionStage<Void> cancel(StandardMatchDomain match){owner.owned();return active!=null&&active.match==match?active.sequence.cancel():CompletableFuture.completedFuture(null);}
    /** Await before deactivating the current scene or shutting down results: an owned rollover may replace it. */
    public CompletionStage<Void> shutdown(){
        owner.owned();if(shutdown!=null)return shutdown.minimalCompletionStage();closing=true;shutdown=new CompletableFuture<>();
        var pending=active==null?CompletableFuture.<Void>completedFuture(null):active.sequence.cancel();pending.whenComplete((unused,error)->owner.resume(()->{if(error==null)shutdown.complete(null);else shutdown.completeExceptionally(OwnerSequence.cause(error));},shutdown));return shutdown.minimalCompletionStage();
    }
    boolean stopped(){owner.owned();return shutdown!=null&&shutdown.isDone()&&!shutdown.isCompletedExceptionally();}
}
