// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.math.*;
import java.util.*;
import java.util.function.Consumer;
import net.battlespades.player.PlayerLife;

/** Source accepted-trigger aggregation and client-chosen pellet seed observations. */
public final class ShotTally {
    public record Weapon(long shots,long hits,long headshots) {}
    public record Snapshot(long shots,long hits,long headshots,long pelletHits,Map<Integer,Weapon> weapons,Map<Integer,Long> seeds){public Snapshot{weapons=Map.copyOf(weapons);seeds=Map.copyOf(seeds);}}
    public record Resolved(PlayerLife player,int tool,boolean hit) {}
    public static final class Statistics {
        private final Thread owner=Thread.currentThread();private long shots,hits,headshots,pelletHits;
        private final Map<Integer,Weapon> weapons=new LinkedHashMap<>();private final Map<Integer,Long> seeds=new LinkedHashMap<>();
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Shot aggregates outside owner");}
        public Snapshot snapshot(){owned();return new Snapshot(shots,hits,headshots,pelletHits,weapons,seeds);}
    }
    private static final class Tally {final PlayerLife player;boolean hit,headshot;long pellets;Tally(PlayerLife p){player=p;}}
    private final Thread owner=Thread.currentThread();private Tally current;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Shot tally outside owner");}
    public void begin(PlayerLife player){owned();current=new Tally(Objects.requireNonNull(player));}
    public void notePlayerHit(PlayerLife player,boolean headshot){owned();if(current==null||current.player!=player)return;current.hit=true;current.headshot|=headshot;current.pellets++;}
    public boolean hadHit(PlayerLife player){owned();return current!=null&&current.player==player&&current.hit;}
    public void end(PlayerLife player,Consumer<Resolved> achievement){
        owned();var tally=current;current=null;if(tally==null||tally.player!=player||player.bot())return;
        var stats=player.combatStatistics();stats.owned();int tool=player.inventory().tool();var weapon=stats.weapons.getOrDefault(tool,new Weapon(0,0,0));
        stats.shots++;if(tally.hit)stats.hits++;if(tally.headshot)stats.headshots++;stats.pelletHits+=tally.pellets;
        stats.weapons.put(tool,new Weapon(weapon.shots+1,weapon.hits+(tally.hit?1:0),weapon.headshots+(tally.headshot?1:0)));achievement.accept(new Resolved(player,tool,tally.hit));
    }
    public void observeSeed(PlayerLife player,int claimedSeed,Consumer<ShotValidation.Event> report){
        owned();if(player.bot())return;var stats=player.combatStatistics();stats.owned();int seed=claimedSeed&255;stats.seeds.merge(seed,1L,Long::sum);
        long total=0,top=0;for(long count:stats.seeds.values()){total+=count;top=Math.max(top,count);}if(total<32||top<Math.max(6,.2*total))return;
        BigInteger squares=BigInteger.ZERO;for(long count:stats.seeds.values()){var value=BigInteger.valueOf(count);squares=squares.add(value.multiply(value));}
        double chi=squares.doubleValue()/(total/256.0)-total;double rounded=new BigDecimal(chi).setScale(1,RoundingMode.HALF_EVEN).doubleValue();if(rounded==0)rounded=Math.copySign(rounded,chi);
        player.shotStatistics().report(new ShotValidation.Event("pellet_seed_skew",false,Map.of("shots",total,"top_seed_count",top,"chi_square",rounded)),report);
    }
}
