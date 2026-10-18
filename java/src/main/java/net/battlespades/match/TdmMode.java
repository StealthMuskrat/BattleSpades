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
public final class TdmMode extends BaseRoundMode {
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
    public interface Effects extends BaseRoundMode.Effects {}
    private final Settings settings;
    public TdmMode(Settings settings,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,
            Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,
            IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        super("tdm",settings.scoreLimit(),settings.timeLimit(),settings.endRoundScoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;
    }
    public Settings settings(){return settings;}
    @Override public void start(){super.start();for(var team:teams.get())team.reset();for(var p:List.copyOf(players.get().values()))if(!p.life.bot())peer.apply(p.life).filter(Scoreboard.Peer::inGame).ifPresent(this::startCue);}
    private void startCue(Scoreboard.Peer target){try{target.send(Announcements.localised("TEAM_DEATHMATCH_START",List.of(),false,true));}catch(RuntimeException failure){effects.cosmeticFailure(failure);}}
    @Override public void reveal(Scoreboard.Peer target,Runnable escapeReplay){super.reveal(target,escapeReplay);startCue(target);}
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
}
