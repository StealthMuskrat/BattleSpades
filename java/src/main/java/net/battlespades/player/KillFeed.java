// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;

/** Source kill_feed.py counters; relation cleanup must precede numeric-id reuse. */
public final class KillFeed {
    public record Flags(boolean domination,boolean revenge){}
    private static final class State {Double last;int count;final Map<Integer,Integer> unanswered=new HashMap<>();final Set<Integer> dominating=new HashSet<>();}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,State> states=new HashMap<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Kill feed accessed outside owner");}
    private State state(int id){return states.computeIfAbsent(id,k->new State());}
    public int registerMultikill(int id,double now){owned();var s=state(id);if(s.last==null||s.count<=0||now-s.last>LifeCatalog.MULTIKILL_GAP)s.count=0;s.count=Math.min(255,s.count+1);s.last=now;return s.count;}
    public void resetMultikill(int id){owned();var s=state(id);s.count=0;s.last=null;}
    public Flags registerDomination(int killer,int victim){owned();var k=state(killer);var v=state(victim);boolean revenge=v.dominating.remove(killer);v.unanswered.remove(killer);int count=k.unanswered.merge(victim,1,Integer::sum);boolean domination=count>=LifeCatalog.DOMINATION_KILLS&&k.dominating.add(victim);return new Flags(domination,revenge);}
    public boolean dominating(int killer,int victim){owned();return state(killer).dominating.contains(victim);}
    public void clearPlayer(int id){owned();var s=state(id);s.unanswered.clear();s.dominating.clear();for(var other:states.values()){other.unanswered.remove(id);other.dominating.remove(id);}}
    public void forget(int id){owned();clearPlayer(id);states.remove(id);}
    public void reset(){owned();states.clear();}
}
