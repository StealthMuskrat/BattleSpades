// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import net.battlespades.player.PlayerLife;

/** Bounded source post-physics queue. Captured actor identity protects reservations from compact ID reuse. */
public final class WorldMutations {
    public record Settings(int limit,int batch,int cells,int timeout){public Settings{if(limit<1||batch<1||cells<1||timeout<1)throw new IllegalArgumentException("Mutation settings");}}
    public record Mutation(PlayerLife owner,long generation,int loop,int enqueued,int cells,Runnable apply,Runnable cancel){public Mutation{Objects.requireNonNull(owner);Objects.requireNonNull(apply);Objects.requireNonNull(cancel);if(cells<0)throw new IllegalArgumentException("Mutation cell count");}}
    public record Metrics(int pending,long peak,long committed,long rejected,long expired){}
    private final ArrayDeque<Mutation> pending=new ArrayDeque<>();private final Settings settings;private long peak,committed,rejected,expired;
    public WorldMutations(Settings settings){this.settings=Objects.requireNonNull(settings);}
    public Metrics metrics(){return new Metrics(pending.size(),peak,committed,rejected,expired);}
    public boolean enqueue(Mutation mutation){if(pending.size()>=settings.limit){mutation.cancel.run();rejected++;return false;}pending.addLast(mutation);peak=Math.max(peak,pending.size());return true;}
    public int commit(int tick,List<PlayerLife> players){int scan=pending.size(),done=0,cells=0;for(int i=0;i<scan;i++){var m=pending.removeFirst();boolean current=players.stream().anyMatch(p->p==m.owner)&&m.owner.generation()==m.generation;if(!current||(long)tick-m.enqueued>=settings.timeout){m.cancel.run();expired++;continue;}var applied=m.owner.inputTimeline().appliedLabel();if(!(m.owner.bot()||applied!=null&&applied>=m.loop)||done>=settings.batch||cells+m.cells>settings.cells){pending.addLast(m);continue;}m.apply.run();done++;cells+=m.cells;committed++;}return done;}
    public void cancel(PlayerLife owner){var keep=new ArrayDeque<Mutation>();while(!pending.isEmpty()){var m=pending.removeFirst();if(m.owner==owner)m.cancel.run();else keep.addLast(m);}pending.addAll(keep);}
    public void clear(){while(!pending.isEmpty())pending.removeFirst().cancel.run();}
}
