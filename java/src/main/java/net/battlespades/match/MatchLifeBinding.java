// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.*;
import net.battlespades.player.*;

/** Canonical per-connection score rows and source-ordered accepted death observers/event pairs.
 * Decorates the required life-effects services; corpse/achievement IO stays in that delegate.
 */
public final class MatchLifeBinding implements LifeRuntime.Effects {
    private final Thread owner=Thread.currentThread();
    private final Supplier<LifeRuntime> runtime;
    private final LifeRuntime.Effects delegate;
    private final ProfileTracker profile;
    private final CombatEvidence combat;
    private final ModeEvents events;
    private final DoubleSupplier monotonic;
    private final Map<Integer,Scoreboard.Player> rows=new LinkedHashMap<>();
    public MatchLifeBinding(Supplier<LifeRuntime> runtime,LifeRuntime.Effects delegate,ProfileTracker profile,CombatEvidence combat,ModeEvents events,DoubleSupplier monotonic){this.runtime=Objects.requireNonNull(runtime);this.delegate=Objects.requireNonNull(delegate);this.profile=Objects.requireNonNull(profile);this.combat=Objects.requireNonNull(combat);this.events=Objects.requireNonNull(events);this.monotonic=Objects.requireNonNull(monotonic);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Match binding outside owner");}
    public Scoreboard.Player row(PlayerLife life){owned();if(life==null)return null;var p=rows.get(life.id());if(p!=null&&p.life==life)return p;var fresh=new Scoreboard.Player(life);if(runtime.get().players().stream().anyMatch(current->current==life))rows.put(life.id(),fresh);return fresh;}
    public Map<Integer,Scoreboard.Player> players(){
        owned();var lives=runtime.get().players();rows.entrySet().removeIf(e->lives.stream().noneMatch(p->p==e.getValue().life));
        // Rows may first be observed at spawn/death, after an earlier spectator or dead admission.
        // Preserve the actual runtime roster order, independently of that lazy cache order.
        var ordered=new LinkedHashMap<Integer,Scoreboard.Player>();for(var life:lives)ordered.put(life.id(),row(life));
        return Collections.unmodifiableMap(ordered);
    }
    public void resetCounters(){owned();runtime.get().resetRoundCounters();}
    private void counters(Scoreboard.Player p){if(p==null)return;var s=runtime.get().score(p.id());p.kills=s.kills();p.deaths=s.deaths();p.killStreak=s.streak();}
    public void beforeSpawn(PlayerLife p){owned();row(p);delegate.beforeSpawn(p);}public void spawned(PlayerLife p){owned();row(p);delegate.spawned(p);}
    public void damaged(LifeRuntime.DamageEvidence d){owned();combat.damageTaken(row(d.victim()),row(d.source()),d.removed(),d.killType(),monotonic.getAsDouble());delegate.damaged(d);}
    public void healed(LifeRuntime.HealingEvidence h){owned();delegate.healed(h);}
    public void beforeDeath(LifeRuntime.DeathEvidence d){
        owned();var victim=row(d.victim());var killer=row(d.killer());counters(victim);counters(killer);victim.diedReloading=d.victim().diedReloading();
        if(d.enemyKill()){var bonus=runtime.get().score(killer.id()).bonuses().get(victim.id());if(bonus!=null)killer.killBonuses.put(victim.id(),bonus);}
        profile.death(victim,killer,d.presentationType());combat.death(victim,killer,d.presentationType(),d.domination(),d.contributions(),monotonic.getAsDouble());delegate.beforeDeath(d);
    }
    public void died(PlayerLife victim,PlayerLife killer,int weapon,int presentation,boolean enemy){
        owned();delegate.died(victim,killer,weapon,presentation,enemy);if(!events.hasMode())return;
        var v=row(victim);var k=row(killer);events.offer(ModeEvents.Event.of("on_player_death",v,k,presentation));if(enemy)events.offer(ModeEvents.Event.of("on_player_kill",k,v,presentation));
    }
    public void transitionCredited(PlayerLife victim,PlayerLife attacker,int type){owned();delegate.transitionCredited(victim,attacker,type);}
    public void forget(PlayerLife p){owned();if(runtime.get().players().stream().anyMatch(current->current==p))events.leave(row(p));delegate.forget(p);var current=rows.get(p.id());if(current!=null&&current.life==p)rows.remove(p.id());}
    public void respawnFailed(PlayerLife p,RuntimeException failure){owned();delegate.respawnFailed(p,failure);}
}
