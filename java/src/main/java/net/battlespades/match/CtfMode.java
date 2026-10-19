// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.session.HostedCtfRuntime;
import org.tomlj.TomlTable;

/** CTF and Classic CTF share the source lifecycle; their live objectives belong to the attached match. */
public final class CtfMode extends BaseRoundMode {
    public record Settings(String code,long scoreLimit,double timeLimit,boolean endRoundScoreboard,boolean autoReturn,boolean touchReturn,boolean ownIntelRequired,boolean shootWithIntel){
        public boolean classic(){return code.equals("cctf");}
        public static Settings from(TomlTable raw,GameRules rules,String code){
            if(!Set.of("ctf","cctf").contains(code))throw new IllegalArgumentException("CTF code");
            String prefix="modes."+code+".";boolean classic=code.equals("cctf");
            long score=integer(raw,prefix+"score_limit",classic&&!rules.explicit("RULE_CTF_SCORE_TARGET")?5:(long)rules.number("RULE_CTF_SCORE_TARGET"));
            double time=raw.get(prefix+"time_limit")!=null?Math.max(0,number(raw,prefix+"time_limit",0)):ModeCatalog.get(code).timeLimit();
            if(raw.get(prefix+"time_limit")==null&&raw.get("lobby.match_length_minutes")!=null){long minutes=integer(raw,"lobby.match_length_minutes",0);if(!Set.of(5L,10L,15L,20L,25L,30L,35L,40L,45L,50L,55L,60L,90L).contains(minutes))throw new IllegalArgumentException("Unsupported lobby match length");time=minutes*60.0;}
            return new Settings(code,score,time,flag(raw,"lobby.end_round_scoreboard",true),flag(raw,prefix+"intel_auto_return",rules.explicit("RULE_CTF_ENABLE_INTEL_AUTO_RETURN")?rules.enabled("RULE_CTF_ENABLE_INTEL_AUTO_RETURN"):!classic),flag(raw,prefix+"intel_return_on_touch",rules.enabled("RULE_CTF_ENABLE_INTEL_RETURN_ON_TOUCH")),flag(raw,prefix+"intel_in_own_base_to_score",rules.enabled("RULE_CTF_ENABLE_INTEL_IN_OWN_BASE_TO_SCORE")),flag(raw,prefix+"shoot_with_intel",rules.explicit("RULE_CTF_ENABLE_SHOOT_WITH_INTEL")?rules.enabled("RULE_CTF_ENABLE_SHOOT_WITH_INTEL"):classic));
        }
        public static boolean flag(TomlTable raw,String key,boolean fallback){var v=raw.get(key);if(v==null)return fallback;if(v instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
        public static double number(TomlTable raw,String key,double fallback){var v=raw.get(key);if(v==null)return fallback;if(v instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();throw new IllegalArgumentException(key+" must be finite numeric");}
        private static long integer(TomlTable raw,String key,long fallback){var v=raw.get(key);if(v==null)return fallback;if(v instanceof Long n)return n;throw new IllegalArgumentException(key+" must be integer");}
    }
    private final Settings settings;private final Supplier<HostedCtfRuntime> objectives;
    public CtfMode(Settings settings,Supplier<HostedCtfRuntime> objectives,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        super(settings.code(),settings.scoreLimit(),settings.timeLimit(),settings.endRoundScoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;this.objectives=Objects.requireNonNull(objectives);
    }
    public Settings settings(){return settings;}
    @Override public void start(){super.start();for(var team:teams.get())team.reset();objectives.get().start();}
    @Override public CompletionStage<Void> tick(int loop){return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->super.tick(loop),()->{if(!clock.ended)objectives.get().tick();return CompletableFuture.completedFuture(null);}).iterator());}
    @Override public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){super.death(victim,killer,type);objectives.get().death(victim,killer,type);}
    @Override public CompletionStage<Void> handle(ModeEvents.Event event){
        sequence.owned();if(event.name().equals("on_player_leave")||event.name().equals("on_player_team_change"))objectives.get().retire(((Scoreboard.Player)event.arguments().getFirst()).life);
        return super.handle(event);
    }
    @Override public void reveal(Scoreboard.Peer target,Runnable escapeReplay){super.reveal(target,escapeReplay);objectives.get().reveal(target::send);}
}
