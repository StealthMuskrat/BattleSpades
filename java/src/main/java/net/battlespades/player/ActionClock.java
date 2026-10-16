// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;

/** server/action_clock.py cadence gate. Passing cadence does not authorize a tool, hit or action.
 * Callers must peek during validation and admit only at the source's actual action-commit boundary.
 */
public final class ActionClock {
    public static final double ARRIVAL_GRACE=1.0/60.0;
    public record Link(double roundTripMillis,double varianceMillis) {}
    public record Lane(Integer label,double interval,double due,double tokens,double tokenInterval,
                       double refilledAt,long admitted,long refused,long byLabel) {
        private static Lane empty(){return new Lane(null,0,0,-1,0,0,0,0,0);}
    }
    private record Decision(boolean allowed,Lane lane,boolean byLabel,double tokens) {}
    private final Thread owner=Thread.currentThread();
    private final InputTimeline input;
    private final LinkedHashMap<String,Lane> lanes=new LinkedHashMap<>();
    public ActionClock(InputTimeline input){this.input=Objects.requireNonNull(input);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Action clock accessed outside owner");}
    // Python's ordered min/max retain the first operand when comparison with NaN is false.
    private static double maximum(double first,double second){return second>first?second:first;}
    private static double minimum(double first,double second){return second<first?second:first;}
    public static double linkStallSeconds(Link link){
        if(link==null||!Double.isFinite(link.roundTripMillis)||!Double.isFinite(link.varianceMillis)||link.roundTripMillis<0)return 0.35;
        double timeout=(link.roundTripMillis+4.0*maximum(0,link.varianceMillis))/1000.0;
        return maximum(0.25,minimum(1.5,2.0*timeout+0.1));
    }
    public boolean labelPlausible(Integer label){
        owned();if(label==null||label<0)return false;Integer newest=input.newestLabel();if(newest==null)newest=input.appliedLabel();
        return newest!=null&&(long)label<=(long)newest+3;
    }
    public Map<String,Lane> lanes(){owned();return Collections.unmodifiableMap(new LinkedHashMap<>(lanes));}
    /** null resets all lanes on spawn; an explicit lane reset is only for source-authorized transitions. */
    public void reset(String key){owned();if(key==null)lanes.clear();else lanes.remove(key);}
    public boolean peek(String key,Integer label,double interval,double now,Integer minFrames,double grace,Link link){
        owned();Objects.requireNonNull(key);return decide(key,label,interval,now,minFrames,grace,link).allowed;
    }
    public boolean admit(String key,Integer label,double interval,double now,Integer minFrames,double grace,Link link){
        owned();Objects.requireNonNull(key);var d=decide(key,label,interval,now,minFrames,grace,link);var lane=d.lane;
        if(!d.allowed){lanes.put(key,new Lane(lane.label,lane.interval,lane.due,d.tokens,maximum(0,interval),now,lane.admitted,lane.refused+1,lane.byLabel));return false;}
        double due=lane.due<=0||now>=lane.due?now+interval:lane.due+interval;
        lanes.put(key,new Lane(labelPlausible(label)?label:null,interval,due,maximum(0,d.tokens-1),maximum(0,interval),now,lane.admitted+1,lane.refused,lane.byLabel+(d.byLabel?1:0)));
        return true;
    }
    private Decision decide(String key,Integer label,double interval,double now,Integer minFrames,double grace,Link link){
        var lane=lanes.getOrDefault(key,Lane.empty());interval=maximum(0,interval);
        double capacity=interval>0?maximum(2,minimum(32,1+Math.ceil(linkStallSeconds(link)/maximum(1e-3,interval)))):32;
        double tokens;
        if(lane.tokens<0)tokens=capacity;
        else{
            double elapsed=maximum(0,now-lane.refilledAt),earned=interval>0?elapsed/interval:capacity;
            double previous=lane.tokenInterval!=0?lane.tokenInterval:interval;
            double carried=interval>0?lane.tokens*previous/interval:capacity;
            tokens=minimum(capacity,carried+earned);
        }
        boolean arrival=now+grace>=lane.due,byLabel=false;
        if(!arrival&&lane.label!=null&&labelPlausible(label)){
            long gap=(long)label-lane.label;double spacing=lane.interval>0?lane.interval:interval;
            double frames;
            if(minFrames==null){frames=Math.floor(spacing*60.0+1e-6);if(!Double.isFinite(frames))throw new IllegalArgumentException("Nonfinite action frame interval");frames=maximum(1,frames);}
            else frames=minFrames;
            if(gap>=frames)byLabel=true;
            else if(gap< -10)byLabel=now+0.5*interval>=lane.due;
        }
        return new Decision((arrival||byLabel)&&tokens+1e-9>=1,lane,byLabel&&!arrival,tokens);
    }
}
