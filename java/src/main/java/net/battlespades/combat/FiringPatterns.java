// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.player.*;

/** Original assault grouping and minigun spin cadence. Caller must first authorize tool and shot geometry. */
public final class FiringPatterns {
    public record Burst(int count,int lastLabel,int firstLabel,double startedAt) {}
    public record Spin(double lastPacketAt,double interval,Integer label) {}
    private final Thread owner=Thread.currentThread();
    private final Map<Integer,Burst> bursts=new LinkedHashMap<>();private final Map<Integer,Spin> spins=new LinkedHashMap<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Firing pattern outside owner");}
    public Map<Integer,Burst> bursts(){owned();return Map.copyOf(bursts);}public Map<Integer,Spin> spins(){owned();return Map.copyOf(spins);}
    public void forgetPlayer(int id){owned();bursts.remove(id);spins.remove(id);}
    public boolean assault(PlayerLife player,int label,double now){
        owned();var burst=bursts.get(player.id());boolean continuation=burst!=null&&player.actionClock().labelPlausible(label)&&0<(long)label-burst.firstLabel&&(long)label-burst.firstLabel<=12;
        if(burst!=null&&burst.count<3&&(long)label-burst.lastLabel>=6&&(now-burst.startedAt<=.30||continuation)){
            var inv=player.inventory();var ammo=inv.ammo();if(ammo.clip()<=0||inv.reloading())return false;
            inv.ammo(ammo.clip()-1,ammo.reserve());bursts.put(player.id(),new Burst(burst.count+1,label,burst.firstLabel,burst.startedAt));player.fire().noteAfterBurstRound(label,player.fire().profile().interval());return true;
        }
        if(!player.fire().consumeShot(now,null,label))return false;bursts.put(player.id(),new Burst(1,label,label,now));return true;
    }
    public boolean minigun(PlayerLife player,double now,Integer label){
        owned();var run=spins.get(player.id());int actions=player.motion().snapshot().actions();boolean preSpun=(actions&2)!=0,stillFiring=(actions&1)!=0;double interval;
        if(run==null)interval=preSpun?.10:.28;
        else{
            double gap=Math.max(0,now-run.lastPacketAt);
            if(player.actionClock().labelPlausible(label)&&run.label!=null){double labelGap=((long)label-run.label)/60.0;if(0<=labelGap&&labelGap<=ActionClock.linkStallSeconds(player.fire().link())+gap)gap=labelGap;}
            interval=run.interval;if(gap<=interval+.1||(stillFiring&&gap<=1))interval-=.15*gap;else if(preSpun)interval=.1;else interval=Math.min(.28,interval+.075*gap);
        }
        interval=Math.min(.30,Math.max(.10,interval));double nextGap=Math.max(.10,interval/(1+.15));
        if(!player.fire().consumeShot(now,nextGap,label))return false;spins.put(player.id(),new Spin(now,interval,label));return true;
    }
}
