// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Player façade input/movement state; raw native-equivalent velocity remains in PlayerPhysics. */
public final class PlayerMotion {
    public record Eye(Vector position,Vector aim) {}
    record Impulse(int label,Vector velocity) {}
    public record Snapshot(int movement,int actions,long starved,long synthesizedGravity,long catchup,long backlogDropped,
        int starvedStreak,int backlogOver,boolean backlogCatchup,boolean timeoutFlagged,Double lastPacket,Double lastSimulated,
        double starvationBaseline,double movementTime,double airTime,double airborneSince,Double ownBlast,
        int fallResult,boolean landed,boolean triggerJump,Integer retailJumpLabel,int queuedImpulses,
        long housekeeping,Map<Integer,Eye> eyes,List<Integer> queueDelays) {}
    private final Thread owner=Thread.currentThread();
    int movement,actions,starvedStreak,backlogOver,fallResult;
    long starved,gravitySteps,catchup,backlogDropped,housekeeping;
    boolean backlogCatchup,timeoutFlagged,landed,triggerJump;
    Boolean flightCapable;
    Double lastPacket,lastSimulated,ownBlast;
    double starvationBaseline,movementTime,airTime,airborneSince;
    Integer retailJumpLabel;
    final LinkedHashMap<Integer,Eye> eyes=new LinkedHashMap<>();
    final ArrayDeque<Impulse> impulses=new ArrayDeque<>();
    final ArrayDeque<Integer> queueDelays=new ArrayDeque<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Movement façade accessed outside owner");}
    void resetLife(){owned();movement=actions=starvedStreak=backlogOver=fallResult=0;backlogCatchup=timeoutFlagged=landed=triggerJump=false;lastSimulated=null;movementTime=0;retailJumpLabel=null;eyes.clear();impulses.clear();}
    void received(double now){owned();lastPacket=now;}
    void controls(int flags,Integer action){owned();movement=flags&255;if(action!=null)actions=action&511;}
    void died(){owned();impulses.clear();}
    public void flightCapability(Boolean value){owned();flightCapable=value;}
    public Boolean flightCapability(){owned();return flightCapable;}
    public void noteOwnBlast(double now,int type){owned();if(type==4||type==5||type==27)ownBlast=now;}
    public Eye eyeAt(int label){owned();return eyes.get(label);}
    void eye(int label,Vector position,Vector aim){eyes.put(label,new Eye(position,aim));while(eyes.size()>128)eyes.remove(eyes.keySet().iterator().next());}
    void delay(int ticks){if(queueDelays.size()==600)queueDelays.removeFirst();queueDelays.addLast(Math.max(0,ticks));}
    public static Vector clipped(Vector velocity){return new Vector(clip(velocity.x()),clip(velocity.y()),clip(velocity.z()));}
    private static double clip(double value){return Math.abs(value)<1e-4?0.0:value;}
    public Snapshot snapshot(){owned();return new Snapshot(movement,actions,starved,gravitySteps,catchup,backlogDropped,starvedStreak,backlogOver,backlogCatchup,timeoutFlagged,lastPacket,lastSimulated,starvationBaseline,movementTime,airTime,airborneSince,ownBlast,fallResult,landed,triggerJump,retailJumpLabel,impulses.size(),housekeeping,Collections.unmodifiableMap(new LinkedHashMap<>(eyes)),List.copyOf(queueDelays));}
}
