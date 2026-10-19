// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.protocol.GamePacket;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.session.HostedCarryRuntime;
import org.tomlj.TomlTable;

/** Shared source lifecycle for Diamond Mine and Occupation's real carried objectives. */
public final class CarryMode extends BaseRoundMode {
    public record Settings(String code,long target,double seconds,boolean scoreboard){
        public static Settings from(TomlTable raw,GameRules rules,String code){boolean diamond=code.equals("dia");if(!diamond&&!code.equals("oc"))throw new IllegalArgumentException("Carry mode");String p="modes."+code+".";Object score=raw.get(p+"score_limit");long target=Boolean.FALSE.equals(score)&&!diamond?0:(long)CtfMode.Settings.number(raw,p+"score_limit",rules.number(diamond?"RULE_DIA_SCORE_TARGET":"RULE_OCC_SCORE_TARGET"));double time=CtfMode.Settings.number(raw,p+"time_limit",ModeCatalog.get(code).timeLimit());if(raw.get(p+"time_limit")==null&&raw.get("lobby.match_length_minutes")!=null){long minutes=raw.getLong("lobby.match_length_minutes");if(!Set.of(5L,10L,15L,20L,25L,30L,35L,40L,45L,50L,55L,60L,90L).contains(minutes))throw new IllegalArgumentException("Unsupported lobby match length");time=minutes*60.0;}return new Settings(code,Math.max(diamond?1:0,target),Math.max(0,time),CtfMode.Settings.flag(raw,"lobby.end_round_scoreboard",true));}
    }
    private final Supplier<HostedCarryRuntime> objectives;private final Settings settings;
    public CarryMode(Settings settings,Supplier<HostedCarryRuntime> objectives,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){super(settings.code(),settings.target(),settings.seconds(),settings.scoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.objectives=objectives;}
    public Settings settings(){return settings;}
    public void start(){objectives.get().clear();super.start();for(var t:teams.get())t.reset();objectives.get().start();}
    public CompletionStage<Void> tick(int loop){return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->super.tick(loop),()->{if(!clock.ended&&!clock.retiring)objectives.get().tick();return CompletableFuture.completedFuture(null);}).iterator());}
    public void death(Scoreboard.Player v,Scoreboard.Player k,int type){super.death(v,k,type);objectives.get().death(v,k,type);}
    public CompletionStage<Void> handle(ModeEvents.Event e){var a=e.arguments();switch(e.name()){case "on_player_leave"->objectives.get().retire(((Scoreboard.Player)a.getFirst()).life,null);case "on_player_team_change"->objectives.get().retire(((Scoreboard.Player)a.getFirst()).life,(Integer)a.get(1));case "on_player_spawn"->objectives.get().spawned(((Scoreboard.Player)a.getFirst()).life);case "on_blocks_destroyed"->{var cells=new ArrayList<net.battlespades.world.MutableWorld.Cell>();for(var value:(List<?>)a.get(1)){var c=(net.battlespades.world.BlockDamageModel.Cell)value;cells.add(new net.battlespades.world.MutableWorld.Cell(c.x(),c.y(),c.z()));}objectives.get().mined(((Scoreboard.Player)a.getFirst()).life,cells,(Boolean)a.get(2));}default->{}}return super.handle(e);}
    public CompletionStage<Void> deactivate(){objectives.get().clear();return super.deactivate();}
    public CompletionStage<Void> deactivateForTransition(boolean own){if(own)objectives.get().clear();return super.deactivateForTransition(own);}
    public void reveal(Scoreboard.Peer target,Runnable replay){super.reveal(target,replay);objectives.get().reveal(target::send);objectives.get().revealCarriers(target::send);}
}
