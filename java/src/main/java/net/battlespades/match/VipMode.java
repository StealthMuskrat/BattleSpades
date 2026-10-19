// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.session.*;
import org.tomlj.TomlTable;
/** VIP kills score immediately; independent sub-rounds retain the answering team's final minute. */
public final class VipMode extends BaseRoundMode {
    public record Settings(long target,double seconds,boolean scoreboard,double selection,double intermission,int resetBudget,int minimum,boolean sudden,double health,String leave){
        public static Settings from(TomlTable raw,GameRules rules){String p="modes.vip.";String leave=raw.getString(p+"leave_policy",()->"reassign").toLowerCase(Locale.ROOT);return new Settings(Math.max(0,(long)CtfMode.Settings.number(raw,p+"score_limit",rules.number("RULE_VIP_NOOF_ROUNDS"))),ObjectiveModeSettings.time(raw,"vip"),CtfMode.Settings.flag(raw,"lobby.end_round_scoreboard",true),Math.max(0,CtfMode.Settings.number(raw,p+"selection_delay",10)),Math.max(0,CtfMode.Settings.number(raw,p+"round_intermission",7)),Math.max(1,(int)CtfMode.Settings.number(raw,p+"round_respawns_per_tick",4)),Math.max(1,(int)CtfMode.Settings.number(raw,p+"minimum_team_size",1)),CtfMode.Settings.flag(raw,p+"sudden_death",rules.enabled("RULE_ENABLE_SUDDEN_DEATH")),CtfMode.Settings.number(raw,p+"vip_health_multiplier",rules.number("RULE_VIP_HEALTH")),leave.equals("death")?"death":"reassign");}
    }
    private final Settings settings;private final Supplier<HostedVipRuntime> runtime;
    public VipMode(Settings settings,Supplier<HostedVipRuntime> runtime,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){super("vip",settings.target(),settings.seconds(),settings.scoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.runtime=runtime;}
    public Settings settings(){return settings;}
    public void start(){runtime.get().deactivate();super.start();for(var t:teams.get())t.reset();runtime.get().start();}
    public CompletionStage<Void> tick(int tick){return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->clock.tickRemaining(wall.getAsDouble(),queued.getAsInt(),runtime.get().remaining(),runtime.get().active(),false),()->{if(!clock.ended&&!clock.retiring)runtime.get().tick();return CompletableFuture.completedFuture(null);}).iterator());}
    public void endByTime(){if(!clock.ended&&!clock.retiring)runtime.get().timeout();}
    public void kill(Scoreboard.Player k,Scoreboard.Player v,int type){super.kill(k,v,type);runtime.get().kill(k,v,type);}
    public void death(Scoreboard.Player v,Scoreboard.Player k,int type){super.death(v,k,type);runtime.get().death(v.life,k==null?null:k.life);}
    public CompletionStage<Void> handle(ModeEvents.Event e){runtime.get().event(e);return super.handle(e);}
    public CompletionStage<Void> deactivate(){runtime.get().deactivate();return super.deactivate();}
    public void beginRetirement(boolean end){runtime.get().cancelReset();super.beginRetirement(end);}
    public CompletionStage<Void> deactivateForTransition(boolean own){if(own)runtime.get().deactivate();return super.deactivateForTransition(own);}
    public void reveal(Scoreboard.Peer p,Runnable replay){super.reveal(p,replay);runtime.get().reveal(p::send);}
}
