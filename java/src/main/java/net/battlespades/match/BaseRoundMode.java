// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;
import net.battlespades.runtime.OwnerSequence;

/** Shared original BaseMode lifecycle, clock, personal economy and reveal state. Domain effects remain required. */
public abstract class BaseRoundMode implements TransitionMode {
    public interface Effects {
        void resetLifeCounters();void matchStarted();void gameplayMusic();void rebuildResources();
        void matchEnded(Integer winner);void endVote(double wall);void scheduleEnd(Integer winner);
        CompletionStage<Void> cancelEnd();
        void recordDeath(Scoreboard.Player victim);
        void escapeWatch();void escapeFailure(RuntimeException failure);void timeoutMusic();void vote(double wall);
        void cosmeticFailure(RuntimeException failure);
        boolean stopping();
    }
    protected final OwnerSequence sequence;
    protected final long scoreLimit;
    private final boolean endRoundScoreboard;
    protected final Supplier<List<TeamState>> teams;
    protected final Supplier<Map<Integer,Scoreboard.Player>> players;
    protected final Function<PlayerLife,Optional<Scoreboard.Peer>> peer;
    protected final Scoreboard board;
    protected final GenericEconomy economy;
    protected final Effects effects;
    protected final Consumer<GamePacket> reliable;
    protected final DoubleSupplier wall;
    protected final IntSupplier queued;
    protected final RoundClock clock;
    private boolean started,endSequenceRunning;
    private Integer winner;
    protected BaseRoundMode(String code,long scoreLimit,double timeLimit,boolean endRoundScoreboard,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,
            Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,
            IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        this.scoreLimit=scoreLimit;this.endRoundScoreboard=endRoundScoreboard;this.teams=Objects.requireNonNull(teams);this.players=Objects.requireNonNull(players);this.peer=Objects.requireNonNull(peer);this.board=Objects.requireNonNull(board);this.wall=Objects.requireNonNull(wall);this.queued=Objects.requireNonNull(queued);this.reliable=Objects.requireNonNull(reliable);this.effects=Objects.requireNonNull(effects);sequence=new OwnerSequence(resume);
        clock=new RoundClock(new RoundClock.Effects(){
            public void escapeWatch(){effects.escapeWatch();}public void escapeFailure(RuntimeException e){effects.escapeFailure(e);}
            public void timeoutMusic(){effects.timeoutMusic();}public void announce(FlowCatalog.Cue cue){reliable.accept(Announcements.localised(cue.stringId(),cue.parameters(),false,true));}
            public void ensureVote(double now){effects.vote(now);}public CompletionStage<Void> endByTime(){BaseRoundMode.this.endByTime();return CompletableFuture.completedFuture(null);}
        });clock.timeLimit=timeLimit;
        economy=new GenericEconomy(()->new GenericEconomy.Context(new ProfileTracker.Context(false,code,clock.ended),true,true),players,board,monotonic);
    }
    public boolean scoreboardEnabled(){sequence.owned();return endRoundScoreboard;}
    public RoundClock clock(){sequence.owned();return clock;}public boolean started(){sequence.owned();return started;}public Integer winner(){sequence.owned();return winner;}public boolean endSequenceRunning(){sequence.owned();return endSequenceRunning;}
    /** Wire HUD limit and team bars come from this mode, never the generic CTF-era config limit. */
    public net.battlespades.scene.ScenePackets.State sceneState(){
        sequence.owned();var roster=teams.get();var blue=roster.stream().filter(t->t.id==2).findFirst().orElseThrow();var green=roster.stream().filter(t->t.id==3).findFirst().orElseThrow();
        return new net.battlespades.scene.ScenePackets.State(new net.battlespades.scene.ScenePackets.Team(blue.name,blue.color(),Math.toIntExact(blue.score)),new net.battlespades.scene.ScenePackets.Team(green.name,green.color(),Math.toIntExact(green.score)),Math.toIntExact(scoreLimit),null);
    }
    public CompletionStage<Void> startMode(){start();return CompletableFuture.completedFuture(null);}
    public void start(){
        sequence.owned();started=true;winner=null;endSequenceRunning=false;clock.reset(wall.getAsDouble());
        effects.resetLifeCounters();board.resetRound();effects.matchStarted();effects.gameplayMusic();effects.rebuildResources();
    }
    public void reveal(Scoreboard.Peer target,Runnable escapeReplay){
        sequence.owned();try{if(clock.ended&&endSequenceRunning&&!clock.retiring&&endRoundScoreboard)boardForce(target);}catch(RuntimeException failure){effects.cosmeticFailure(failure);}
        try{escapeReplay.run();}catch(RuntimeException failure){effects.cosmeticFailure(failure);}
    }
    private static void boardForce(Scoreboard.Peer target){target.send(new net.battlespades.protocol.SimplePackets.ForceShowScores(1));}
    public CompletionStage<Void> cancelForTransition(boolean ownEnding){sequence.owned();if(!ownEnding)return cancelEndSequence();endSequenceRunning=false;return CompletableFuture.completedFuture(null);}
    public CompletionStage<Void> deactivateForTransition(boolean ownEnding){sequence.owned();if(!ownEnding)return deactivate();clock.retiring=true;clock.ended=true;started=false;endSequenceRunning=false;return CompletableFuture.completedFuture(null);}
    public void beginRetirement(boolean endRound){sequence.owned();clock.retiring=true;if(endRound)clock.ended=true;}
    /** The retained ending service clears this before attempting source-compatible failure recovery. */
    public void endSequenceFailed(){sequence.owned();endSequenceRunning=false;}
    public CompletionStage<Void> cancelEndSequence(){
        sequence.owned();return sequence.run(List.<Supplier<CompletionStage<Void>>>of(effects::cancelEnd,()->{endSequenceRunning=false;return CompletableFuture.completedFuture(null);}).iterator());
    }
    public CompletionStage<Void> deactivate(){
        sequence.owned();clock.retiring=true;return sequence.run(List.<Supplier<CompletionStage<Void>>>of(this::cancelEndSequence,()->{started=false;clock.ended=true;return CompletableFuture.completedFuture(null);}).iterator());
    }
    public void kill(Scoreboard.Player killer,Scoreboard.Player victim,int type){sequence.owned();economy.kill(killer,victim,type,false);}
    public String joinMusicTrack(MusicService music){sequence.owned();return music.joinTrack(started,clock.ended,endSequenceRunning,clock.retiring,clock.timeoutMusicPlayed);}
    public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){sequence.owned();economy.deathPenalty(victim,killer,type);effects.recordDeath(victim);}
    public CompletionStage<Void> handle(ModeEvents.Event event){
        sequence.owned();var a=event.arguments();switch(event.name()){
            case "on_player_kill"->kill((Scoreboard.Player)a.get(0),(Scoreboard.Player)a.get(1),(Integer)a.get(2));
            case "on_player_death"->death((Scoreboard.Player)a.get(0),(Scoreboard.Player)a.get(1),(Integer)a.get(2));
            default->{ }
        }return CompletableFuture.completedFuture(null);
    }
    public CompletionStage<Void> tick(int loop){sequence.owned();return clock.tick(wall.getAsDouble(),queued.getAsInt());}
    public int endMessageId(Integer winning){sequence.owned();var roster=teams.get();return RoundClock.headline(winning,roster.stream().filter(t->t.id==2).findFirst().orElseThrow().score,roster.stream().filter(t->t.id==3).findFirst().orElseThrow().score);}
    public void endByScore(int team){sequence.owned();if(clock.ended||clock.retiring)return;var winning=teams.get().stream().filter(t->t.id==team).findFirst().orElseThrow();reliable.accept(Announcements.localised("TEAM_DEFEAT",List.of(winning.name),true,false));end(team);}
    public void endByTime(){
        sequence.owned();if(clock.ended||clock.retiring)return;Integer winning=RoundClock.timeWinner(teams.get());
        var name=winning==null?List.<String>of():List.of(teams.get().stream().filter(t->t.id==winning).findFirst().orElseThrow().name);
        reliable.accept(Announcements.localised(winning==null?"GAME_DRAWN":"TEAM_DEFEAT",name,winning!=null,false));end(winning);
    }
    public void end(Integer winning){
        sequence.owned();clock.ended=true;if(clock.retiring)return;effects.matchEnded(winning);effects.endVote(wall.getAsDouble());winner=winning;
        if(!endSequenceRunning&&!effects.stopping()){endSequenceRunning=true;effects.scheduleEnd(winning);}
    }
}
