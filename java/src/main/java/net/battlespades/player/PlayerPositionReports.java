// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LivePositionReport;

/** Accepted PositionData telemetry and the source's optional post-movement client-authority pin. */
public final class PlayerPositionReports {
    public record State(Vector reported,long received,double updated,Vector drift,double distance){}
    private final Thread owner=Thread.currentThread();private Vector reported,drift=new Vector(0,0,0);
    private long received;private double updated,distance;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Position reports outside owner");}
    public State state(){owned();return new State(reported,received,updated,drift,distance);}
    /** Source spawn replaces the report/drift but retains report count and last update timestamp. */
    void spawned(Vector at){owned();reported=Objects.requireNonNull(at);drift=new Vector(0,0,0);distance=0;}
    public boolean receive(PlayerLife p,LivePositionReport at,double wall){
        owned();if(!Double.isFinite(at.x())||!Double.isFinite(at.y())||!Double.isFinite(at.z()))return false;
        reported=new Vector(at.x(),at.y(),at.z());received++;updated=wall;record(p);return true;
    }
    private void record(PlayerLife p){var at=p.position();drift=new Vector(reported.x()-at.x(),reported.y()-at.y(),reported.z()-at.z());distance=Math.sqrt(drift.x()*drift.x()+drift.y()*drift.y()+drift.z()*drift.z());}
    void pin(PlayerLife p,double wall){
        owned();if(reported==null||updated<=0||wall-updated>1)return;
        p.body().position(reported);record(p);
    }
}
