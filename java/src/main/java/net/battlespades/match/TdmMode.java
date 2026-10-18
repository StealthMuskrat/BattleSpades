// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.tomlj.TomlTable;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;
import net.battlespades.protocol.AudioPackets;
import net.battlespades.protocol.Vector3f;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.runtime.OwnerSequence;

/** Concrete original TDM mode. Audio, achievements, resources and the delayed transition sequence are required services. */
public final class TdmMode implements ModeEvents.Mode {
    private static final org.tomlj.TomlParseResult DATA=net.battlespades.catalog.SourceCatalog.load("tdm-runtime");
    public record Settings(long scoreLimit,double timeLimit,long killPoints,long headshotBonus,boolean endRoundScoreboard){
        public Settings(long scoreLimit,double timeLimit,long killPoints,long headshotBonus){this(scoreLimit,timeLimit,killPoints,headshotBonus,true);}
        public Settings{if(!Double.isFinite(timeLimit))throw new IllegalArgumentException("Nonfinite TDM clock");}
        private static double number(TomlTable raw,String key,double fallback){Object v=raw.get(key);if(v==null)return fallback;if(v instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();throw new IllegalArgumentException(key+" must be finite numeric");}
        private static long integer(TomlTable raw,String key,long fallback){Object v=raw.get(key);if(v==null)return fallback;if(v instanceof Long n)return n;throw new IllegalArgumentException(key+" must be an integer");}
        public static Settings from(TomlTable raw,GameRules rules){
            double time=raw.get("modes.tdm.time_limit")!=null?Math.max(0,number(raw,"modes.tdm.time_limit",0)):
                raw.get("lobby.match_length_minutes")!=null?integer(raw,"lobby.match_length_minutes",0)*60.0:ModeCatalog.get("tdm").timeLimit();
            if(raw.get("lobby.match_length_minutes")!=null&&!Set.of(5L,10L,15L,20L,25L,30L,35L,40L,45L,50L,55L,60L,90L).contains(integer(raw,"lobby.match_length_minutes",0)))throw new IllegalArgumentException("Unsupported lobby match length");
            Object forced=raw.get("lobby.end_round_scoreboard");if(forced!=null&&!(forced instanceof Boolean))throw new IllegalArgumentException("end_round_scoreboard must be boolean");
            return new Settings(integer(raw,"modes.tdm.score_limit",(long)rules.number("RULE_TDM_SCORE_TARGET")),time,integer(raw,"modes.tdm.kill_points",DATA.getLong("killPoints")),integer(raw,"modes.tdm.headshot_bonus",0),!Boolean.FALSE.equals(forced));
        }
    }
    public interface Effects {
        void resetLifeCounters();void matchStarted();void gameplayMusic();void rebuildResources();
        void matchEnded(Integer winner);void endVote(double wall);void scheduleEnd(Integer winner);
        CompletionStage<Void> cancelEnd();
        void recordDeath(Scoreboard.Player victim);
        void escapeWatch();void escapeFailure(RuntimeException failure);void timeoutMusic();void vote(double wall);
        void cosmeticFailure(RuntimeException failure);
        boolean stopping();
    }
    private final OwnerSequence sequence;
    private final Settings settings;
    private final Supplier<List<TeamState>> teams;
    private final Supplier<Map<Integer,Scoreboard.Player>> players;
    private final Function<PlayerLife,Optional<Scoreboard.Peer>> peer;
    private final Scoreboard board;
    private final GenericEconomy economy;
    private final Effects effects;
    private final Consumer<GamePacket> reliable;
    private final DoubleSupplier wall;
    private final IntSupplier queued;
    private final RoundClock clock;
    private boolean started,endSequenceRunning;
    private Integer winner;
    public TdmMode(Settings settings,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,
            Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,
            IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        this.settings=Objects.requireNonNull(settings);this.teams=Objects.requireNonNull(teams);this.players=Objects.requireNonNull(players);this.peer=Objects.requireNonNull(peer);this.board=Objects.requireNonNull(board);this.wall=Objects.requireNonNull(wall);this.queued=Objects.requireNonNull(queued);this.reliable=Objects.requireNonNull(reliable);this.effects=Objects.requireNonNull(effects);sequence=new OwnerSequence(resume);
        clock=new RoundClock(new RoundClock.Effects(){
            public void escapeWatch(){effects.escapeWatch();}public void escapeFailure(RuntimeException e){effects.escapeFailure(e);}
            public void timeoutMusic(){effects.timeoutMusic();}public void announce(FlowCatalog.Cue cue){reliable.accept(Announcements.localised(cue.stringId(),cue.parameters(),false,true));}
            public void ensureVote(double now){effects.vote(now);}public CompletionStage<Void> endByTime(){TdmMode.this.endByTime();return CompletableFuture.completedFuture(null);}
        });clock.timeLimit=settings.timeLimit();
        economy=new GenericEconomy(()->new GenericEconomy.Context(new ProfileTracker.Context(false,"tdm",clock.ended),true,true),players,board,monotonic);
    }
    public RoundClock clock(){sequence.owned();return clock;}public boolean started(){sequence.owned();return started;}public Integer winner(){sequence.owned();return winner;}public boolean endSequenceRunning(){sequence.owned();return endSequenceRunning;}
    /** Wire HUD limit and team bars come from this mode, never the generic CTF-era config limit. */
    public net.battlespades.scene.ScenePackets.State sceneState(){
        sequence.owned();var roster=teams.get();var blue=roster.stream().filter(t->t.id==2).findFirst().orElseThrow();var green=roster.stream().filter(t->t.id==3).findFirst().orElseThrow();
        return new net.battlespades.scene.ScenePackets.State(new net.battlespades.scene.ScenePackets.Team(blue.name,blue.color(),Math.toIntExact(blue.score)),new net.battlespades.scene.ScenePackets.Team(green.name,green.color(),Math.toIntExact(green.score)),Math.toIntExact(settings.scoreLimit()),null);
    }
    public Settings settings(){return settings;}
    public void start(){
        sequence.owned();started=true;winner=null;endSequenceRunning=false;clock.reset(wall.getAsDouble());
        effects.resetLifeCounters();board.resetRound();effects.matchStarted();effects.gameplayMusic();effects.rebuildResources();
        for(var team:teams.get())team.reset();
        for(var p:List.copyOf(players.get().values()))if(!p.life.bot())peer.apply(p.life).filter(Scoreboard.Peer::inGame).ifPresent(this::startCue);
    }
    private void startCue(Scoreboard.Peer target){try{target.send(Announcements.localised("TEAM_DEATHMATCH_START",List.of(),false,true));}catch(RuntimeException failure){effects.cosmeticFailure(failure);}}
    public void reveal(Scoreboard.Peer target,Runnable escapeReplay){
        sequence.owned();try{if(clock.ended&&endSequenceRunning&&!clock.retiring&&settings.endRoundScoreboard())boardForce(target);}catch(RuntimeException failure){effects.cosmeticFailure(failure);}
        try{escapeReplay.run();}catch(RuntimeException failure){effects.cosmeticFailure(failure);}startCue(target);
    }
    private static void boardForce(Scoreboard.Peer target){target.send(new net.battlespades.protocol.SimplePackets.ForceShowScores(1));}
    public void beginRetirement(boolean endRound){sequence.owned();clock.retiring=true;if(endRound)clock.ended=true;}
    public CompletionStage<Void> cancelEndSequence(){
        sequence.owned();return sequence.run(List.<Supplier<CompletionStage<Void>>>of(effects::cancelEnd,()->{endSequenceRunning=false;return CompletableFuture.completedFuture(null);}).iterator());
    }
    public CompletionStage<Void> deactivate(){
        sequence.owned();clock.retiring=true;return sequence.run(List.<Supplier<CompletionStage<Void>>>of(this::cancelEndSequence,()->{started=false;clock.ended=true;return CompletableFuture.completedFuture(null);}).iterator());
    }
    public void kill(Scoreboard.Player killer,Scoreboard.Player victim,int type){
        sequence.owned();if(clock.ended||killer.life==victim.life||killer.team()==victim.team()||!ObjectiveScores.playable(killer)||!ObjectiveScores.playable(victim))return;
        var team=teams.get().stream().filter(t->t.id==killer.team()).findFirst().orElse(null);if(team==null)return;
        long points=settings.killPoints();if(type==FlowCatalog.value("headshotType"))points=Math.addExact(points,settings.headshotBonus());team.addScore(points);
        economy.kill(killer,victim,type,false);sting(killer,true);sting(victim,false);
        board.teamScore(team,null);if(settings.scoreLimit()>0&&team.score>=settings.scoreLimit())endByScore(killer.team());
    }
    private void sting(Scoreboard.Player player,boolean positive){
        peer.apply(player.life).filter(Scoreboard.Peer::inGame).ifPresent(target->target.send(new AudioPackets.PlaySound(Math.toIntExact(DATA.getLong(positive?"positiveSound":"negativeSound")),false,false,.6f,0,0,new Vector3f(0,0,0),0)));
    }
    public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){sequence.owned();economy.deathPenalty(victim,killer,type);effects.recordDeath(victim);}
    public CompletionStage<Void> handle(ModeEvents.Event event){
        sequence.owned();var a=event.arguments();switch(event.name()){
            case "on_player_kill"->kill((Scoreboard.Player)a.get(0),(Scoreboard.Player)a.get(1),(Integer)a.get(2));
            case "on_player_death"->death((Scoreboard.Player)a.get(0),(Scoreboard.Player)a.get(1),(Integer)a.get(2));
            default->{ }
        }return CompletableFuture.completedFuture(null);
    }
    public CompletionStage<Void> tick(int loop){sequence.owned();return clock.tick(wall.getAsDouble(),queued.getAsInt());}
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
