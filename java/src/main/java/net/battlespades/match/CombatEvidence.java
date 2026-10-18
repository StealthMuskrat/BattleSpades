// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
/** Accepted combat evidence; damage validation and the accepted-damage ledger remain LifeRuntime domains. */
public final class CombatEvidence {
    public record Context(boolean active,boolean genericAssist,boolean solo,Integer zombieTeam){}
    private final Supplier<Context> context;
    private final Supplier<Map<Integer,Scoreboard.Player>> players;
    private final BiPredicate<PlayerLife,PlayerLife> opponents;
    private final Scoreboard board;
    public CombatEvidence(Supplier<Context> context,Supplier<Map<Integer,Scoreboard.Player>> players,BiPredicate<PlayerLife,PlayerLife> opponents,Scoreboard board){this.context=Objects.requireNonNull(context);this.players=Objects.requireNonNull(players);this.opponents=Objects.requireNonNull(opponents);this.board=Objects.requireNonNull(board);}
    private static int c(String n){return MatchCatalog.constant(n);}
    private boolean active(Scoreboard.Player p){return context.get().active&&ObjectiveScores.playable(p);}
    public void damageTaken(Scoreboard.Player victim,Scoreboard.Player source,double damage,int type,double now){
        if(!context.get().active||damage<=0||!ObjectiveScores.playable(victim))return;var state=victim.roundStats;state.increment(c("MOST_DAMAGE_TAKEN"),ProfileStats.integer(Math.rint(damage)));
        if(type==c("HEADSHOT_KILL")&&source!=null&&source.life!=victim.life&&source.team()!=victim.team())state.increment(c("MOST_HEADSHOTS_RECEIVED"),1);
        if(type==c("AIRSTRIKE_KILL")&&victim.life.health()>0){if(now-state.lastAirstrikeSurvivedAt>=3)state.increment(c("MOST_AIRSTRIKES_SURVIVED"),1);state.lastAirstrikeSurvivedAt=now;}
    }
    public void shot(Scoreboard.Player p){if(active(p))p.roundStats.measures.merge("shots",1.0,Double::sum);}
    public void blocksPlaced(Scoreboard.Player p,List<Integer> z){if(z.isEmpty()||!active(p))return;p.roundStats.increment(c("MOST_BLOCKS_PLACED"),z.size());long highest=Long.MIN_VALUE;for(int cell:z)highest=Math.max(highest,(long)c("MAP_Z")-1-cell);p.roundStats.raiseTo(c("HIGHEST_BLOCK"),highest);}
    public void blocksDestroyed(Scoreboard.Player p,long count,List<Integer> chunkSizes){if(!active(p))return;if(count>0)p.roundStats.increment(c("MOST_BLOCKS_DESTROYED"),count);for(int size:chunkSizes)p.roundStats.raiseTo(c("BIGGEST_COLLAPSING_OBJECT"),size);}
    public void crate(Scoreboard.Player p,int award){if(active(p))p.roundStats.increment(award,1);}
    public void tick(Scoreboard.Player p,double dt,boolean airborne,boolean onFire){
        if(!(dt>0&&dt<=1)||!context.get().active)return;var state=p.roundStats;var at=p.life.position();var last=state.lastPosition;state.lastPosition=at;if(!p.life.alive()||!ObjectiveScores.playable(p))return;
        if(airborne)state.accumulate(c("MOST_TIME_IN_AIR"),dt);else if(last!=null){double dx=at.x()-last.x(),dy=at.y()-last.y(),dz=at.z()-last.z();for(double v:new double[]{dx,dy,dz})if(Double.isFinite(v)&&Double.isInfinite(v*v))throw new ArithmeticException("Source squared-distance overflow");double step=Math.pow(SpawnMath.sumSquares(dx,dy,dz),.5);if(step>0&&step<=2)state.accumulate(c("MOST_DISTANCE_RAN"),step);}
        if(onFire)state.accumulate(c("MOST_TIME_ON_FIRE"),dt);
    }
    /** Bind to LifeRuntime.beforeDeath: contributions must be the already-consumed immutable life ledger. */
    public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type,boolean domination,List<DamageHistory.Contribution> contributions,double now){
        long generation=victim.life.generation();if(Objects.equals(victim.scoredDeathGeneration,generation))return;victim.scoredDeathGeneration=generation;var mode=context.get();if(!mode.active||LifeCatalog.TRANSITIONS.contains(type))return;
        if(killer==null||killer.life==victim.life){if(type!=c("AIRSTRIKE_KILL")&&type!=c("BOMB_KILL")&&(killer!=null||type==c("FALL_KILL")))victim.roundStats.increment(c("MOST_SUICIDES"),1);return;}
        if(!opponents.test(killer.life,victim.life)||!ObjectiveScores.playable(killer)||!ObjectiveScores.playable(victim))return;var state=killer.roundStats;
        state.increment(c(mode.zombieTeam!=null&&mode.zombieTeam==killer.team()?"MOST_BRAINS_EATEN":"MOST_KILLS"),1);if(type==c("HEADSHOT_KILL"))state.increment(c("MOST_HEADSHOTS"),1);else if(type==c("MELEE_KILL"))state.increment(c("MOST_MELEE_KILLS"),1);
        state.awards.put(c("BIGGEST_KILL_STREAK"),Math.max(state.awards.getOrDefault(c("BIGGEST_KILL_STREAK"),0L),killer.killStreak));if(killer.life.health()>0&&killer.life.health()<=20)state.increment(c("MOST_KILLS_AT_LOW_HEALTH"),1);
        if(type!=c("MELEE_KILL")){var a=killer.life.position();var b=victim.life.position();double dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();for(double v:new double[]{dx,dy,dz})if(Double.isFinite(v)&&Double.isInfinite(v*v))throw new ArithmeticException("Source squared-distance overflow");state.raiseTo(c("LONGEST_RANGED_KILL"),ProfileStats.integer(Math.pow(SpawnMath.sumSquares(dx,dy,dz),.5)));}
        if(Set.of(c("SNIPER_TOOL"),c("SNIPER2_TOOL")).contains(victim.life.inventory().tool()))state.increment(c("MOST_SNIPERS_KILLED"),1);
        if(domination){state.increment(c("MOST_DOMINATIONS"),1);victim.roundStats.increment(c("MOST_DOMINATED"),1);}if(mode.solo)return;
        double threshold=MatchCatalog.constant("ASSIST_THRESHOLD"),own=0;for(var entry:contributions)if(entry.player().id()==killer.id()&&entry.player()==killer.life&&now-entry.touched()<=LifeCatalog.INTERACTION_WINDOW)own=entry.damage();
        if(own<threshold&&contributions.stream().anyMatch(e->e.player()!=killer.life&&e.team()==killer.team()&&now-e.touched()<=LifeCatalog.INTERACTION_WINDOW&&e.damage()>=threshold))state.increment(c("MOST_KILL_STEALS"),1);
        for(var entry:contributions){var assistant=players.get().get(entry.player().id());if(entry.player()==killer.life||assistant==null||assistant.life!=entry.player()||assistant.team()!=killer.team()||entry.team()!=killer.team()||entry.generation()!=assistant.life.generation()||now-entry.touched()>LifeCatalog.INTERACTION_WINDOW||entry.damage()<threshold)continue;assistant.roundStats.increment(c("MOST_ASSISTS"),1);if(mode.genericAssist){assistant.score=Math.addExact(assistant.score,c("ASSIST_SCORE"));board.playerScore(assistant,c("KILL_SCORE_ASSIST_REASON"));}}
    }
}
