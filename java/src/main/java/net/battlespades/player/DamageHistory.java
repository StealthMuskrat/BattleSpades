// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.IntFunction;

/** Accepted enemy HP removed in this victim life. This ledger does not award scores or validate hits. */
public final class DamageHistory {
    public record Contribution(PlayerLife player,int team,long generation,double damage,double touched){}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Contribution> entries=new LinkedHashMap<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Damage history accessed outside owner");}
    public List<Contribution> snapshot(){owned();return List.copyOf(entries.values());}
    public void clear(){owned();entries.clear();}
    void record(PlayerLife source,int removed,double now,IntFunction<PlayerLife> current){
        owned();entries.values().removeIf(c->now-c.touched()>LifeCatalog.INTERACTION_WINDOW||current.apply(c.player().id())!=c.player());
        var previous=entries.get(source.id());double amount=removed;
        if(previous!=null&&previous.player()==source&&previous.generation()==source.generation()&&previous.team()==source.team())amount+=previous.damage();
        entries.put(source.id(),new Contribution(source,source.team(),source.generation(),Math.min(LifeCatalog.HEALTH,amount),now));
    }
    void heal(int restored){
        owned();double total=0;for(var c:entries.values())total+=c.damage();if(total<=0||restored<=0)return;
        double retained=Math.max(0,total-restored)/total;entries.replaceAll((id,c)->new Contribution(c.player(),c.team(),c.generation(),c.damage()*retained,c.touched()));
    }
    List<Contribution> consume(){owned();var result=snapshot();clear();return result;}
}
