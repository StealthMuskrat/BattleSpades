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
/** Build phase, frozen live-terrain objective, and impact-delayed outcome. */
public final class DemolitionMode extends BaseRoundMode {
    public record Settings(double buildSeconds,double seconds,boolean scoreboard){
        public static Settings from(TomlTable raw,GameRules rules){return new Settings(Math.max(0,CtfMode.Settings.number(raw,"modes.dem.build_state_length",rules.number("RULE_BUILD_STATE_LENGTH"))),ObjectiveModeSettings.time(raw,"dem"),CtfMode.Settings.flag(raw,"lobby.end_round_scoreboard",true));}
    }
    private final Settings settings;private final Supplier<HostedDemolitionRuntime> runtime;
    public DemolitionMode(Settings settings,Supplier<HostedDemolitionRuntime> runtime,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){super("dem",1,settings.seconds(),settings.scoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.runtime=runtime;}
    @Override public int endMessageId(Integer winning){sequence.owned();return winning==null?8:2;}
    public Settings settings(){return settings;}
    public void start(){runtime.get().deactivate();super.start();for(var t:teams.get())t.reset();runtime.get().start();}
    public CompletionStage<Void> tick(int tick){return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->super.tick(tick),()->{if(!clock.ended&&!clock.retiring)runtime.get().tick();return CompletableFuture.completedFuture(null);}).iterator());}
    public void kill(Scoreboard.Player k,Scoreboard.Player v,int type){super.kill(k,v,type);runtime.get().kill(k,v,type);}
    public void endByTime(){if(clock.ended||clock.retiring)return;Integer winner=runtime.get().timeWinner();reliable.accept(Announcements.localised(winner==null?"GAME_DRAWN":"TEAM_DEFEAT",winner==null?List.of():List.of(teams.get().stream().filter(t->t.id==winner).findFirst().orElseThrow().name),winner!=null,false));end(winner);}
    public CompletionStage<Void> handle(ModeEvents.Event e){runtime.get().event(e);return super.handle(e);}
    public CompletionStage<Void> deactivate(){runtime.get().deactivate();return super.deactivate();}
    public CompletionStage<Void> deactivateForTransition(boolean own){if(own)runtime.get().deactivate();return super.deactivateForTransition(own);}
    public void reveal(Scoreboard.Peer p,Runnable replay){super.reveal(p,replay);runtime.get().reveal(p::send);}
}
