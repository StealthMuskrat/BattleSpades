// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;

/** ClientData chronology from server/player.py, separate from movement/equipment/action policy.
 * Receiving never steps a body. consumeOne selects one fixed-duration movement phase;
 * its caller must run the actual equipment, impulse, movement and eye-history work.
 * Labels are signed source integers, not transport sequence numbers or elapsed time.
 */
public final class InputTimeline {
    public record Frame(int label,int movement,Vector aim,Integer actions,int receivedTick,
                        long ownerSequence,long inputSequence,int unknown,long topology) {}
    /** A null action mask means retain action state (a synthesized frame), not release all actions.
     * The ninth action bit is the ClientData palette flag. Aim is the pre-normalization source value.
     */
    public record Step(int label,int movement,Vector aim,Integer actions,boolean synthesized,
                       Integer sourceLabel,Integer sourceTick,Long sourceOwner,Integer sourceUnknown,
                       long inputSequence,Long ownerSequence) {}
    public record Arrival(boolean fresh,boolean buffered,boolean rejectedAhead) {}
    public record Counters(long dropped,long stale,long overflow,long applied,long synthesized,
                           long reordered,long salvaged,long presses,long rejectedAhead) {}
    public record State(Integer newest,Integer applied,boolean fresh,boolean synthesized,
                        long ownerSequence,long inputSequence,long currentInputSequence,Long currentOwnerSequence,
                        int pendingMovement,Integer pendingLabel,Integer pendingTick,Long pendingOwner,Integer pendingUnknown,
                        Integer sourceLabel,Integer sourceTick,Long sourceOwner,Integer sourceUnknown,
                        Integer appliedMovement,Vector appliedAim,Integer appliedActions,
                        int pressMovement,int pressActions,boolean orientationAfterSynth,
                        List<Frame> frames,Map<Integer,List<Integer>> guesses,Counters counters) {}
    private record Reorder(int tick,long late) {}
    private record Guess(int movement,Integer actions) {}
    private final Thread owner=Thread.currentThread();
    private final boolean latch;
    private final NavigableMap<Integer,Frame> frames=new TreeMap<>();
    private final NavigableMap<Integer,Guess> guesses=new TreeMap<>();
    private final LinkedHashMap<Integer,Boolean> zoom=new LinkedHashMap<>();
    private final LinkedHashMap<Integer,Integer> arrivals=new LinkedHashMap<>();
    private final ArrayDeque<Reorder> reorder=new ArrayDeque<>();
    private Integer newest,applied,pendingLabel,pendingTick,pendingUnknown,sourceLabel,sourceTick,sourceUnknown,appliedMovement,appliedActions;
    private Long pendingOwner,sourceOwner,currentOwner;
    private Vector appliedAim;
    private int pendingMovement,pressMovement,pressActions;
    private long ownerSequence,inputSequence,currentInputSequence,dropped,stale,overflow,appliedCount,synthesizedCount,reordered,salvaged,presses,rejectedAhead;
    private boolean fresh=true,synthesized,orientationAfterSynth;

    public InputTimeline(){this(true);}
    public InputTimeline(boolean latch){this.latch=latch;}

    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Input timeline accessed outside owner");}
    public Integer newestLabel(){owned();return newest;}
    public Integer appliedLabel(){owned();return applied;}
    public int size(){owned();return frames.size();}
    public Frame firstFrame(){owned();return frames.isEmpty()?null:frames.firstEntry().getValue();}
    public Frame frame(int label){owned();return frames.get(label);}
    public int pendingMovement(){owned();return pendingMovement;}
    public Integer appliedActions(){owned();return appliedActions;}
    public boolean hasPresses(){owned();return pressMovement!=0||pressActions!=0;}
    public Vector appliedAim(){owned();return appliedAim;}
    public void pruneStale(){owned();while(applied!=null&&!frames.isEmpty()&&frames.firstKey()<=applied){frames.pollFirstEntry();stale++;dropped++;}}
    /** Enforced backlog discard: no movement step or fresh receive witness is fabricated. */
    public Optional<Frame> dropOldest(){
        owned();if(frames.isEmpty())return Optional.empty();var f=frames.pollFirstEntry().getValue();applied=f.label;pendingMovement=f.movement;pendingLabel=f.label;pendingTick=f.receivedTick;pendingOwner=f.ownerSequence;pendingUnknown=f.unknown;appliedAim=f.aim;dropped++;return Optional.of(f);
    }
    /** Source spawn clears life cursors, not the connection's label, zoom/arrival or owner-send history. */
    public void resetLife(){
        owned();frames.clear();guesses.clear();applied=null;pendingLabel=null;pendingTick=null;pendingUnknown=null;pendingOwner=null;
        sourceLabel=null;sourceTick=null;sourceOwner=null;sourceUnknown=null;appliedMovement=null;appliedAim=null;appliedActions=null;
        inputSequence=0;currentInputSequence=0;currentOwner=null;pendingMovement=0;pressMovement=0;pressActions=0;
        // last_applied_input_synthesized and _orientation_after_synth also survive source spawn.
    }
    /** Owner WorldUpdate sends and receives must claim this same causal clock; it is not an ACK. */
    public long claimOwnerSequence(Long supplied){owned();if(supplied==null)return ++ownerSequence;ownerSequence=Math.max(ownerSequence,supplied);return supplied;}
    public Arrival receive(LiveClientInput input,boolean alive,boolean spawned,Vector cachedAim,
                           int tick,long topology,boolean enforceAhead){
        owned();Objects.requireNonNull(input);Objects.requireNonNull(cachedAim);
        int label=input.loopCount(),actions=(input.actionFlags()&255)|(input.paletteEnabled()?256:0);
        zoom.put(label,(actions&4)!=0);trim(zoom,256);
        arrivals.putIfAbsent(label,tick);trim(arrivals,256);
        fresh=observe(label,tick);
        if(!alive||!spawned)return new Arrival(fresh,false,false);
        long sequence=claimOwnerSequence(null);
        Vector aim=Double.isFinite(input.x())&&Double.isFinite(input.y())&&Double.isFinite(input.z())
            ?new Vector(input.x(),input.y(),input.z()):appliedAim!=null?appliedAim:cachedAim;
        if(applied!=null&&label<=applied){salvage(label,input.movementFlags()&255,aim,actions,latch);stale++;dropped++;return new Arrival(fresh,false,false);}
        if(frames.containsKey(label)){stale++;dropped++;return new Arrival(fresh,false,false);}
        boolean ahead=(long)label>(long)tick+256&&(applied==null||(long)label>(long)applied+64);
        if(ahead&&enforceAhead){rejectedAhead++;return new Arrival(fresh,false,true);}
        frames.put(label,new Frame(label,input.movementFlags()&255,aim,actions,tick,sequence,++inputSequence,input.ooo()&255,topology));
        if(frames.size()>128){frames.pollFirstEntry();overflow++;dropped++;}
        return new Arrival(fresh,true,false);
    }
    private static <V> void trim(LinkedHashMap<Integer,V> map,int limit){while(map.size()>limit)map.remove(map.keySet().iterator().next());}
    private boolean observe(int label,int tick){
        if(newest==null||label>newest){newest=label;return true;}
        long late=(long)newest-label;
        if(late>10){newest=label;return true;}
        if(late==0)return false;
        reordered++;var previous=reorder.peekLast();
        if(previous!=null&&(long)tick-previous.tick>=0&&(long)tick-previous.tick<60){
            if(late>previous.late){reorder.removeLast();reorder.addLast(new Reorder(previous.tick,late));}
        }else{if(reorder.size()==22)reorder.removeFirst();reorder.addLast(new Reorder(tick,late));}
        return false;
    }
    public int reorderSpread(int tick){owned();while(!reorder.isEmpty()&&(long)tick-reorder.getFirst().tick>1200)reorder.removeFirst();return (int)reorder.stream().mapToLong(Reorder::late).max().orElse(0);}
    public Integer labelArrivalTick(Integer label){owned();return label==null?null:arrivals.get(label);}
    /** Evidence for later shot validation only; this method never authorizes a shot or damage. */
    public boolean zoomForAction(Integer label,boolean current){
        owned();if(label==null||zoom.isEmpty())return current;
        for(int back=0;back<=3;back++)if(Boolean.TRUE.equals(zoom.get((long)label-back>=Integer.MIN_VALUE?label-back:null)))return true;
        if(!zoom.containsKey(label)&&!zoom.containsKey((long)label-1>=Integer.MIN_VALUE?label-1:null))return true;
        for(int back=1;back<=12;back++){Boolean state=(long)label-back<Integer.MIN_VALUE?null:zoom.get(label-back);if(state!=null)return state;}
        return zoom.getOrDefault(label,current);
    }
    /** Original _consume_input_frame/_synthesize_missing_frame phase selection only.
     * The server loop owns pacing, starvation, backlog policies and the subsequent physics call.
     * Call once per selected fixed step, never once per raw packet arrival.
     */
    public Optional<Step> consumeOne(int gapLimit,Vector currentAim){
        owned();Objects.requireNonNull(currentAim);
        while(applied!=null&&!frames.isEmpty()&&frames.firstKey()<=applied){frames.pollFirstEntry();stale++;dropped++;}
        if(frames.isEmpty())return Optional.empty();
        int loop=frames.firstKey();long distance=applied==null?0:(long)loop-applied;
        if(gapLimit>0&&applied!=null&&distance>1&&distance<=(long)gapLimit+1){
            int label=applied+1;Vector aim=appliedAim!=null?appliedAim:currentAim;
            applied=label;synthesized=true;orientationAfterSynth=true;guesses.put(label,new Guess(pendingMovement,appliedActions));
            while(guesses.size()>16)guesses.pollFirstEntry();appliedMovement=pendingMovement;synthesizedCount++;
            return Optional.of(new Step(label,pendingMovement,aim,null,true,sourceLabel,sourceTick,sourceOwner,sourceUnknown,currentInputSequence,currentOwner));
        }
        Frame frame=frames.pollFirstEntry().getValue();currentInputSequence=frame.inputSequence;currentOwner=frame.ownerSequence;
        int movement=latch?(pendingMovement&~32)|(frame.movement&32):frame.movement;
        Integer fromLabel=latch?pendingLabel:Integer.valueOf(loop),fromTick=latch?pendingTick:Integer.valueOf(frame.receivedTick),fromUnknown=latch?pendingUnknown:Integer.valueOf(frame.unknown);
        Long fromOwner=latch?pendingOwner:Long.valueOf(frame.ownerSequence);
        Vector aim=latch&&appliedAim!=null?appliedAim:frame.aim;
        if(latch&&orientationAfterSynth&&appliedAim!=null){
            double x=appliedAim.x()+frame.aim.x(),y=appliedAim.y()+frame.aim.y(),z=appliedAim.z()+frame.aim.z();
            double norm=Math.sqrt(x*x+y*y+z*z);if(norm>1e-6)aim=new Vector(x/norm,y/norm,z/norm);
        }
        orientationAfterSynth=false;applied=loop;synthesized=false;movement|=pressMovement;pressMovement=0;
        Integer actions=frame.actions;if(actions!=null){actions|=pressActions;pressActions=0;}
        appliedActions=frame.actions;appliedMovement=movement;appliedAim=frame.aim;
        sourceLabel=fromLabel;sourceTick=fromTick;sourceOwner=fromOwner;sourceUnknown=fromUnknown;
        pendingMovement=frame.movement;pendingLabel=loop;pendingTick=frame.receivedTick;pendingOwner=frame.ownerSequence;pendingUnknown=frame.unknown;appliedCount++;
        return Optional.of(new Step(loop,movement,aim,actions,false,sourceLabel,sourceTick,sourceOwner,sourceUnknown,currentInputSequence,currentOwner));
    }
    private void salvage(int label,int movement,Vector aim,int actions,boolean latch){
        Guess assumed=guesses.remove(label);if(assumed==null)return;salvaged++;
        if(latch&&Objects.equals(applied,label)){appliedAim=aim;orientationAfterSynth=false;pendingMovement=movement;pendingLabel=label;}
        else if((movement&16)!=0&&(assumed.movement&16)==0&&(pendingMovement&16)==0&&frames.values().stream().noneMatch(f->(f.movement&16)!=0)){pressMovement|=16;presses++;}
        if((actions&128)!=0&&(assumed.actions==null||(assumed.actions&128)==0)&&(appliedActions==null||(appliedActions&128)==0)
            &&frames.values().stream().noneMatch(f->f.actions!=null&&(f.actions&128)!=0)){pressActions|=128;presses++;}
    }
    public Counters counters(){owned();return new Counters(dropped,stale,overflow,appliedCount,synthesizedCount,reordered,salvaged,presses,rejectedAhead);}
    public State state(){
        owned();var frozen=new LinkedHashMap<Integer,List<Integer>>();guesses.forEach((key,value)->frozen.put(key,Collections.unmodifiableList(Arrays.asList(value.movement,value.actions))));
        return new State(newest,applied,fresh,synthesized,ownerSequence,inputSequence,currentInputSequence,currentOwner,
            pendingMovement,pendingLabel,pendingTick,pendingOwner,pendingUnknown,sourceLabel,sourceTick,sourceOwner,sourceUnknown,
            appliedMovement,appliedAim,appliedActions,pressMovement,pressActions,orientationAfterSynth,List.copyOf(frames.values()),Collections.unmodifiableMap(frozen),counters());
    }
}
