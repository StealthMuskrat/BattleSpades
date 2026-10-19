// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;

/** server.handlers.movement packet budgets; cadence/action admission remains a separate policy. */
public final class MovementPacketRates {
    public enum Kind {
        CLIENT_DATA("client_data",240,64),CLOCK_SYNC("clock_sync",4,16),POSITION_DATA("position_data",240,64);
        private final String key;private final double rate,burst;
        Kind(String key,double rate,double burst){this.key=key;this.rate=rate;this.burst=burst;}
        public String key(){return key;}public double rate(){return rate;}public double burst(){return burst;}
    }
    public record Lane(double tokens,double monotonic,double simulated,long accepted,long rejected){}
    private final Thread owner=Thread.currentThread();private final EnumMap<Kind,Lane> lanes=new EnumMap<>(Kind.class);
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Packet rates outside owner");}
    public Map<Kind,Lane> snapshot(){owned();return Collections.unmodifiableMap(new EnumMap<>(lanes));}
    public boolean allow(Kind kind,boolean bot,double now,int loop){return allow(kind,bot,now,loop,kind.rate,kind.burst);}
    /** Explicit effective policy settings; the stock ServerConfig has no packet-rate override fields. */
    public boolean allow(Kind kind,boolean bot,double now,int loop,double rate,double burst){
        owned();Objects.requireNonNull(kind);if(bot||rate<=0)return true;
        double sim=loop/60.0;var previous=lanes.get(kind);double tokens=burst;
        if(previous!=null){
            double elapsed=Math.max(0,Math.max(now-previous.monotonic,sim-previous.simulated));
            double capacity=Math.max(burst,rate*Math.min(elapsed,2));tokens=Math.max(previous.tokens,0);
            tokens+=Math.min(rate*elapsed,Math.max(0,capacity-tokens));
        }
        boolean accepted=tokens>=1;lanes.put(kind,new Lane(accepted?tokens-1:tokens,now,sim,
            (previous==null?0:previous.accepted)+(accepted?1:0),(previous==null?0:previous.rejected)+(accepted?0:1)));
        return accepted;
    }
}
