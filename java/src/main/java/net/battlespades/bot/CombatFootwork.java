// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.Player;
import static net.battlespades.bot.BotVectors.*;

/** Encounter-owned strides, firing pauses and range hysteresis; no global random stream. */
public final class CombatFootwork {
    private final Thread owner=Thread.currentThread();
    private Identity target;private String phase="",rangeMode="hold";private double until,startedAt,strideLength,side=1,lastPressureAt;
    private Vector origin=ZERO,heading=ZERO;private int sequence;private boolean wasReloading;
    public record Identity(int playerId,int generation,int lifeId) {}
    public record State(Identity target,String phase,double until,double startedAt,Vector origin,Vector heading,
                        double strideLength,int sequence,double side,String rangeMode,double lastPressureAt,boolean wasReloading) {}
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Combat footwork outside worker");}
    public State snapshot() {owned();return new State(target,phase,until,startedAt,origin,heading,strideLength,sequence,side,rangeMode,lastPressureAt,wasReloading);}
    public String spacing(double distance,double minimum,double maximum) {
        owned();double retreatAt=Math.max(2,minimum*.65);
        if(rangeMode.equals("retreat")&&distance<Math.max(3,minimum*.95))return "retreat";
        if(distance<retreatAt)rangeMode="retreat";
        else if(rangeMode.equals("approach")&&distance>maximum*.88)return "approach";
        else if(distance>maximum)rangeMode="approach";else rangeMode="hold";
        return rangeMode;
    }
    public Vector direction(Player observer,Player enemy,BotProfile profile,double now,int blockedStage) {
        owned();var identity=new Identity(enemy.id(),enemy.generation(),enemy.lifeId());
        double dx=enemy.position().x()-observer.position().x(),dy=enemy.position().y()-observer.position().y(),distance=Math.hypot(dx,dy);
        if(distance<1e-6)return ZERO;
        if(!identity.equals(target)) {target=identity;phase="";sequence=0;side=(observer.id()&1)!=0?-1:1;}
        if(observer.reloading()&&!wasReloading)phase="";wasReloading=observer.reloading();
        if(blockedStage!=0) {phase="recovery";return new Vector(-dy*side/distance,dx*side/distance,0);}
        boolean pressure=observer.lastDamageAt()>lastPressureAt&&now-observer.lastDamageAt()>=0&&now-observer.lastDamageAt()<=.8&&now-startedAt>=.6;
        if(pressure) {lastPressureAt=observer.lastDamageAt();phase="";}
        double moved=flat(observer.position(),origin);
        if(phase.equals("stride")&&(now>=until||moved>=strideLength)) {
            phase="settle";double duration=.22+.5*profile.burstDiscipline()+.25*variation(observer,profile);
            if(observer.reloading()||now-observer.lastDamageAt()<1.2)duration*=.45;
            until=now+duration;return ZERO;
        }
        if(phase.equals("settle")&&now<until)return ZERO;
        if(!phase.equals("stride")) {
            sequence++;double variation=variation(observer,profile);
            if(sequence>1&&variation<.3+.2*profile.caution())side=-side;
            double desired=Math.max(3,profile.preferredRange()),radial=Math.max(-.25,Math.min(.25,(distance-desired)/desired));
            if(observer.reloading()||pressure||observer.health()<45)radial=-.45-.2*profile.caution();
            double ux=dx/distance,uy=dy/distance,hx=-uy*side+ux*radial,hy=ux*side+uy*radial,length=Math.hypot(hx,hy);
            heading=new Vector(hx/length,hy/length,0);origin=observer.position();strideLength=2+2.4*profile.aggression()+1.4*variation;
            startedAt=now;until=now+.8+.6*profile.caution()+.5*variation;phase="stride";
        }
        return heading;
    }
    private double variation(Player p,BotProfile profile) {
        long value=(((long)p.id()+1)*73856093L ^ (target==null?1L:(long)target.playerId()+1)*19349663L ^ (long)sequence*83492791L ^ (long)(profile.creativity()*65535))&0xffff_ffffL;
        value^=value>>>13;return (value&65535)/65535.;
    }
}
