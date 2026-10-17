// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Actual queued owner outputs from Player.record_owner_anchor. Causal eligibility is not delivery acknowledgement. */
public final class OwnerAnchors {
    public record Anchor(int stamp,Vector position,Vector velocity,Integer serverTick,long ownerSequence) {}
    private final Thread owner=Thread.currentThread();
    private final InputTimeline timeline;
    private final ArrayDeque<Anchor> history=new ArrayDeque<>();
    private Vector spawn=new Vector(0,0,0),last=spawn;
    OwnerAnchors(InputTimeline timeline){this.timeline=Objects.requireNonNull(timeline);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Owner history accessed outside owner");}
    void resetLife(Vector at){owned();spawn=last=Objects.requireNonNull(at);history.clear();}
    public Anchor record(int stamp,Vector position,Vector velocity,Integer serverTick,Long suppliedSequence){
        owned();var row=new Anchor(stamp,Objects.requireNonNull(position),Objects.requireNonNull(velocity),serverTick,timeline.claimOwnerSequence(suppliedSequence));
        if(history.size()==128)history.removeFirst();history.addLast(row);last=position;return row;
    }
    /** The server tick remains diagnostic; only strictly earlier labels and owner event sequences exclude impossible rows. */
    public Optional<Anchor> entryBeforeInput(Integer sourceLoop,Integer receivedServerTick,Long receivedOwnerSequence){
        owned();if(sourceLoop==null)return Optional.empty();Anchor best=null;
        for(var row:history)if(row.stamp<sourceLoop&&(receivedOwnerSequence==null||row.ownerSequence<receivedOwnerSequence)&&(best==null||row.ownerSequence>best.ownerSequence))best=row;
        return Optional.ofNullable(best);
    }
    public Vector beforeInput(Integer sourceLoop,Integer receivedServerTick,Long receivedOwnerSequence){
        owned();return sourceLoop==null?last:entryBeforeInput(sourceLoop,receivedServerTick,receivedOwnerSequence).map(Anchor::position).orElse(spawn);
    }
    public Vector lastAdvertised(){owned();return last;}
    public Vector spawnAnchor(){owned();return spawn;}
    public List<Anchor> snapshot(){owned();return List.copyOf(history);}
}
