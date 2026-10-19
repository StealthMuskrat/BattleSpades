// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.*;
import net.battlespades.session.HostedZoneRuntime;
import org.tomlj.TomlTable;

/** TC and Multi-Hill lifecycle; objective state belongs to the attached match owner. */
public final class ZoneMode extends BaseRoundMode {
    public record Settings(String code,double timeLimit,boolean scoreboard,int maxActive,double captureRate,double activeSeconds,double shellInterval,boolean resupply,double refillSeconds){
        public boolean territory(){return code.equals("tc");}
        public static Settings from(TomlTable raw,GameRules rules,String code){
            boolean tc=code.equals("tc");if(!tc&&!code.equals("mh"))throw new IllegalArgumentException("Zone mode code");String prefix="modes."+code+".";
            double time=CtfMode.Settings.number(raw,prefix+"time_limit",ModeCatalog.get(code).timeLimit());
            if(raw.get(prefix+"time_limit")==null&&raw.get("lobby.match_length_minutes")!=null){long minutes=raw.getLong("lobby.match_length_minutes");if(!Set.of(5L,10L,15L,20L,25L,30L,35L,40L,45L,50L,55L,60L,90L).contains(minutes))throw new IllegalArgumentException("Unsupported lobby match length");time=minutes*60.0;}
            int count=(int)CtfMode.Settings.number(raw,prefix+"max_active_bases",rules.number(tc?"RULE_TC_MAX_ACTIVE_BASES":"RULE_MULTIHILL_MAX_ACTIVE_BASES"));
            return new Settings(code,Math.max(0,time),CtfMode.Settings.flag(raw,"lobby.end_round_scoreboard",true),tc?Math.max(2,Math.min(5,count)):Math.max(1,count),Math.max(.1,CtfMode.Settings.number(raw,prefix+"capture_rate",rules.number("RULE_CAPTURE_RATE"))),Math.max(1,CtfMode.Settings.number(raw,prefix+"base_active_time",rules.number("RULE_BASE_ACTIVE_TIME"))),Math.max(.15,CtfMode.Settings.number(raw,prefix+"airstrike_shell_interval",.65)),CtfMode.Settings.flag(raw,prefix+"capture_point_resupply",true),Math.max(.5,CtfMode.Settings.number(raw,prefix+"capture_point_refill_time",10)));
        }
    }
    private int territoryCount=1;private final Settings settings;private final Supplier<HostedZoneRuntime> objectives;
    public ZoneMode(Settings settings,Supplier<HostedZoneRuntime> objectives,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        super(settings.code(),0,settings.timeLimit(),settings.scoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.objectives=Objects.requireNonNull(objectives);
    }
    public Settings settings(){return settings;}
    public void start(){objectives.get().cancelShells();super.start();for(var team:teams.get())team.reset();objectives.get().start();territoryCount=Math.max(1,objectives.get().states().size());}
    public ScenePackets.State sceneState(){var s=super.sceneState();return new ScenePackets.State(s.team1(),s.team2(),settings.territory()?territoryCount:0,s.fogOverride());}
    public CompletionStage<Void> tick(int loop){
        if(!settings.territory()&&!clock.ended)objectives.get().tick();
        return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->super.tick(loop),()->{if(!clock.ended){if(settings.territory())objectives.get().tick();else if(started()&&clock.timeLimit>0&&wall.getAsDouble()>=clock.startTime+clock.timeLimit+1)endByTime();}return CompletableFuture.completedFuture(null);}).iterator());
    }
    public void kill(Scoreboard.Player killer,Scoreboard.Player victim,int type){super.kill(killer,victim,type);objectives.get().kill(killer,victim,type);}
    public void endByScore(int team){if(settings.territory())super.endByScore(team);}
    public void end(Integer winner){objectives.get().cancelShells();super.end(winner);}
    public void beginRetirement(boolean endRound){objectives.get().cancelShells();super.beginRetirement(endRound);}
    public CompletionStage<Void> deactivate(){objectives.get().deactivate();return super.deactivate();}
    public CompletionStage<Void> deactivateForTransition(boolean ownEnding){if(ownEnding)objectives.get().deactivate();return super.deactivateForTransition(ownEnding);}
    public CompletionStage<Void> handle(ModeEvents.Event event){if(event.name().equals("on_player_leave")||event.name().equals("on_player_team_change"))objectives.get().forget(((Scoreboard.Player)event.arguments().getFirst()).life);return super.handle(event);}
    public void reveal(Scoreboard.Peer target,Runnable escapeReplay){super.reveal(target,escapeReplay);objectives.get().reveal(target::send);}
}
