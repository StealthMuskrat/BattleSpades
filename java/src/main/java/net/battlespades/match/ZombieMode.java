// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.session.HostedZombieRuntime;
import org.tomlj.TomlTable;
/** Timed outbreak rounds; generic combat score is opted in only for survivor kills. */
public final class ZombieMode extends BaseRoundMode {
    public record Settings(int rounds,double seconds,double infection,int first,int minimum,double respawn,double intermission,int resetBudget,boolean scoreboard){
        public static Settings from(TomlTable raw,GameRules rules){String p="modes.zom.";return new Settings(Math.max(1,(int)CtfMode.Settings.number(raw,p+"score_limit",rules.number("RULE_ZOMBIE_NOOF_ROUNDS"))),ObjectiveModeSettings.time(raw,"zom"),CtfMode.Settings.number(raw,p+"infection_delay",60),Math.max(1,(int)CtfMode.Settings.number(raw,p+"first_infected",rules.number("RULE_NOOF_FIRST_INFECTED_ZOMBIES"))),Math.max(2,(int)CtfMode.Settings.number(raw,p+"minimum_players",2)),Math.max(0,CtfMode.Settings.number(raw,p+"zombie_respawn_time",10)),Math.max(0,CtfMode.Settings.number(raw,p+"round_intermission",5)),Math.max(1,(int)CtfMode.Settings.number(raw,p+"round_respawns_per_tick",4)),CtfMode.Settings.flag(raw,"lobby.end_round_scoreboard",true));}
    }
    private final Settings settings;private final Supplier<HostedZombieRuntime> runtime;
    public ZombieMode(Settings settings,Supplier<HostedZombieRuntime> runtime,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){super("zom",settings.rounds(),settings.seconds(),settings.scoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.runtime=runtime;}
    public Settings settings(){return settings;}
    public void start(){runtime.get().deactivate();super.start();for(var t:teams.get())t.reset();runtime.get().start();}
    public CompletionStage<Void> tick(int tick){return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->{runtime.get().beforeClock();boolean escape=clock.escapeEnabled;try{clock.escapeEnabled=escape&&runtime.get().active()&&runtime.get().finalRound();return clock.tickRemaining(wall.getAsDouble(),queued.getAsInt(),runtime.get().remaining(),runtime.get().active(),runtime.get().finalRound());}finally{clock.escapeEnabled=escape;}},()->{if(!clock.ended&&!clock.retiring)runtime.get().tick();return CompletableFuture.completedFuture(null);}).iterator());}
    public void endByTime(){if(!clock.ended&&!clock.retiring)runtime.get().timeout();}
    public void kill(Scoreboard.Player k,Scoreboard.Player v,int type){if(runtime.get().active()&&!clock.ended&&k.team()==3&&v.team()==2){economy.kill(k,v,type,true);runtime.get().kill(k.life,v.life,type);}}
    public void death(Scoreboard.Player v,Scoreboard.Player k,int type){runtime.get().death(v.life,k==null?null:k.life,type);}
    public CompletionStage<Void> handle(ModeEvents.Event e){runtime.get().event(e);return super.handle(e);}
    public void end(Integer winner){runtime.get().clearMarker();super.end(winner);}
    public CompletionStage<Void> deactivate(){runtime.get().deactivate();return super.deactivate();}
    public void beginRetirement(boolean end){runtime.get().cancelReset();super.beginRetirement(end);}
    public CompletionStage<Void> deactivateForTransition(boolean own){if(own)runtime.get().deactivate();return super.deactivateForTransition(own);}
}
