// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.player.SpawnMath;

/** Actual object identity, ordered membership and original 3D radius/hysteresis policy. */
public final class EscortTracker {
    private final Thread owner=Thread.currentThread();
    private final double radius,hysteresis;
    private final IdentityHashMap<PlayerLife,Set<PlayerLife>> members=new IdentityHashMap<>();
    public EscortTracker(double radius,double hysteresis){this.radius=radius;this.hysteresis=Double.isNaN(hysteresis)?0:Math.max(0,hysteresis);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Escorts outside owner");}
    public void reset(){owned();members.clear();}
    public void forget(PlayerLife carrier){owned();members.remove(carrier);}
    public List<PlayerLife> escorts(PlayerLife carrier,List<PlayerLife> candidates){
        owned();var previous=members.getOrDefault(carrier,Set.of());var current=Collections.newSetFromMap(new IdentityHashMap<PlayerLife,Boolean>());var ordered=new ArrayList<PlayerLife>();
        for(var other:candidates){
            if(other==carrier||!other.alive()||!other.spawned()||other.team()!=carrier.team())continue;
            double limit=radius+(previous.contains(other)?hysteresis:0);var a=other.position();var b=carrier.position();double dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();
            for(double d:new double[]{dx,dy,dz,limit})if(Double.isFinite(d)&&Double.isInfinite(d*d))throw new ArithmeticException("Escort squared distance overflow");
            if(SpawnMath.sumSquares(dx,dy,dz)<=limit*limit&&current.add(other))ordered.add(other);
        }
        members.put(carrier,current);return List.copyOf(ordered);
    }
}
