// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.*;
/** BaseMode generic kill/penalty and TDM teamplay economy. Roster credit preserves source ghost-return semantics. */
public final class GenericEconomy {
    public record Context(ProfileTracker.Context scoring,boolean genericEnabled,boolean teamplay){public Context{Objects.requireNonNull(scoring);}}
    private final Supplier<Context> context;
    private final Supplier<Map<Integer,Scoreboard.Player>> players;
    private final Scoreboard board;
    private final DoubleSupplier monotonic;
    public GenericEconomy(Supplier<Context> context,Supplier<Map<Integer,Scoreboard.Player>> players,Scoreboard board,DoubleSupplier monotonic){this.context=Objects.requireNonNull(context);this.players=Objects.requireNonNull(players);this.board=Objects.requireNonNull(board);this.monotonic=Objects.requireNonNull(monotonic);}
    private static int v(String key){return Math.toIntExact(FlowCatalog.value(key));}
    private boolean blocked(int type,boolean opted,Scoreboard.Player... actors){var c=context.get();return !c.scoring.active()||!c.genericEnabled&&!opted||LifeCatalog.TRANSITIONS.contains(type)||Arrays.stream(actors).anyMatch(p->!ObjectiveScores.playable(p));}
    private void add(Scoreboard.Player p,long amount,int reason){var owner=players.get().get(p.id());if(owner==null||owner.life!=p.life)return;p.score=Math.addExact(p.score,amount);board.playerScore(p,reason);}
    public long kill(Scoreboard.Player killer,Scoreboard.Player victim,int type,boolean opted){
        if(killer==null||victim==null||killer.life==victim.life||blocked(type,opted,killer,victim)||killer.team()==victim.team())return 0;
        String kind=type==v("headshotType")?"headshot":type==v("meleeType")?"melee":"kill";long amount=FlowCatalog.value(kind+"Score");add(killer,amount,v(kind+"Reason"));
        var bonus=killer.killBonuses.remove(victim.id());if(bonus!=null){if(bonus.revenge()){add(killer,FlowCatalog.value("revengeScore"),v("revengeReason"));amount=Math.addExact(amount,FlowCatalog.value("revengeScore"));}if(bonus.payback()){add(killer,FlowCatalog.value("paybackScore"),v("paybackReason"));amount=Math.addExact(amount,FlowCatalog.value("paybackScore"));}}
        if(context.get().teamplay)amount=Math.addExact(amount,teamplay(killer,victim));return amount;
    }
    public long teamplay(Scoreboard.Player killer,Scoreboard.Player victim){
        long paid=0;if(victim.diedReloading){add(killer,FlowCatalog.value("reloadScore"),v("reloadReason"));paid+=FlowCatalog.value("reloadScore");}double now=monotonic.getAsDouble();var distracted=new ArrayList<Scoreboard.Player>();
        for(var mate:List.copyOf(players.get().values())){if(mate.life==killer.life||mate.life==victim.life||!mate.life.alive()||mate.team()!=killer.team())continue;for(var entry:mate.life.damageHistory().snapshot())if(entry.player().id()==victim.id()&&entry.player()==victim.life&&now-entry.touched()<=LifeCatalog.INTERACTION_WINDOW){distracted.add(mate);break;}}
        if(!distracted.isEmpty()){add(killer,FlowCatalog.value("defendScore"),v("defendReason"));paid+=FlowCatalog.value("defendScore");killer.roundStats.increment(v("defendAward"),1);for(var mate:distracted){add(mate,FlowCatalog.value("distractScore"),v("distractReason"));mate.roundStats.increment(v("distractAward"),1);}}return paid;
    }
    public long deathPenalty(Scoreboard.Player victim,Scoreboard.Player killer,int type){
        if(victim==null)return 0;if(victim.deathPenaltyExempt){victim.deathPenaltyExempt=false;return 0;}if(type==v("airstrikeType")||type==v("bombType"))return 0;if(killer==null&&type!=LifeCatalog.FALL_KILL)return 0;
        if(killer==null||killer.life==victim.life){if(blocked(type,false,victim))return 0;add(victim,FlowCatalog.value("suicideScore"),v("suicideReason"));return FlowCatalog.value("suicideScore");}
        if(blocked(type,false,victim,killer)||killer.team()!=victim.team())return 0;add(killer,FlowCatalog.value("teamkillScore"),v("teamkillReason"));return FlowCatalog.value("teamkillScore");
    }
}
