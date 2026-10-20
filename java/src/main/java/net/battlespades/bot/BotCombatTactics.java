// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotVectors.*;

/** Situational combat choices over detached perception and the worker's collision map. */
public final class BotCombatTactics {
    private BotCombatTactics() {}
    public static double mix(long... values) {
        long state=0x9e3779b9L;
        for(long value:values) {state^=(value+0x7f4a7c15L+(state<<6)+(state>>>2))&0xffff_ffffL;state=(state*2654435761L)&0xffff_ffffL;}
        state^=state>>>15;return (state&65535)/65535.;
    }
    public static final class Engagement {
        private final Thread owner=Thread.currentThread();
        CombatFootwork.Identity target;Double wantingFireSince;double blockedUntil,headAimUntil,spotSince,spotHold,spotPressureAt,relocateUntil;
        Vector spot,relocateHeading=ZERO,cover,evadeHeading=ZERO;int relocations,evadeEntity;double coverUntil,nextCoverSearchAt,evadeUntil;boolean coverDuck;
        void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Combat engagement outside worker");}
        public record State(CombatFootwork.Identity target,Double wantingFireSince,double blockedUntil,double headAimUntil,Vector spot,
            double spotSince,double spotHold,double spotPressureAt,double relocateUntil,Vector relocateHeading,int relocations,Vector cover,
            double coverUntil,boolean coverDuck,double nextCoverSearchAt,double evadeUntil,Vector evadeHeading,int evadeEntity) {}
        public State snapshot() {owned();return new State(target,wantingFireSince,blockedUntil,headAimUntil,spot,spotSince,spotHold,spotPressureAt,relocateUntil,relocateHeading,relocations,cover,coverUntil,coverDuck,nextCoverSearchAt,evadeUntil,evadeHeading,evadeEntity);}
        public void retarget(Player player) {
            owned();var identity=new CombatFootwork.Identity(player.id(),player.generation(),player.lifeId());
            if(!identity.equals(target)) {target=identity;wantingFireSince=null;blockedUntil=headAimUntil=0;}
        }
    }
    public record Cover(Vector position,boolean duck) {}
    public static double aimOffset(BotNavigationWorld world,Player observer,Player target,BotProfile profile,Engagement e,double now) {
        e.owned();if(profile.skill()>=.82||now<e.headAimUntil)return 0;
        var torso=new Vector(target.eye().x(),target.eye().y(),target.eye().z()+1);
        return world.lineOfSight(observer.eye(),torso)?1:0;
    }
    public static boolean fireBlocked(Engagement e,Player observer,boolean wantsFire,double now,double cadence) {
        e.owned();if(!wantsFire||observer.reloading()) {e.wantingFireSince=null;return now<e.blockedUntil;}
        boolean fired=observer.lastActionKind().equals("fire")&&observer.lastActionAccepted()&&now-observer.lastActionAt()>=0&&now-observer.lastActionAt()<=Math.max(1,cadence);
        if(fired||e.wantingFireSince==null)e.wantingFireSince=now;
        else if(now-e.wantingFireSince>=Math.max(1.5,cadence+1)) {e.blockedUntil=e.headAimUntil=now+2.5;e.wantingFireSince=now;}
        return now<e.blockedUntil;
    }
    public static boolean walkableHeading(BotNavigationWorld world,Player observer,Vector heading,double reach) {
        double z=observer.position().z();
        for(double distance:new double[]{1,reach}) {
            double x=observer.position().x()+heading.x()*distance,y=observer.position().y()+heading.y()*distance;
            var surface=world.surface((int)Math.floor(x),(int)Math.floor(y),z,2,false);
            if(surface==null||Math.abs(surface.position().z()-z)>1.1)return false;z=surface.position().z();
        }
        return true;
    }
    public static Vector relocationHeading(BotNavigationWorld world,Player observer,Player target,BotProfile profile,Engagement e) {
        e.owned();double dx=target.position().x()-observer.position().x(),dy=target.position().y()-observer.position().y(),distance=Math.hypot(dx,dy);
        if(distance<1e-6)return null;
        double ux=dx/distance,uy=dy/distance,side=mix(observer.id(),observer.lifeId(),e.relocations)<.5?1:-1,radial=.35*(profile.aggression()-profile.caution());
        for(double sign:new double[]{side,-side})for(double blend:new double[]{radial,0,-.4}) {
            double hx=-uy*sign+ux*blend,hy=ux*sign+uy*blend,length=Math.hypot(hx,hy);var heading=new Vector(hx/length,hy/length,0);
            if(walkableHeading(world,observer,heading,3))return heading;
        }
        return null;
    }
    public static Vector stationaryRelocation(BotNavigationWorld world,Player observer,Player target,BotProfile profile,Engagement e,double now) {
        e.owned();if(now<e.relocateUntil)return e.relocateHeading;
        if(e.spot==null||flat(observer.position(),e.spot)>1.5) {
            e.spot=observer.position();e.spotSince=now;e.spotPressureAt=observer.lastDamageAt();
            e.spotHold=4+7*profile.caution()+4*mix(observer.id(),observer.lifeId(),e.relocations,7);return null;
        }
        boolean pressured=observer.lastDamageAt()>e.spotPressureAt&&now-observer.lastDamageAt()>=0&&now-observer.lastDamageAt()<=1&&now-e.spotSince>=.5;
        if(!pressured&&now-e.spotSince<e.spotHold)return null;
        var heading=relocationHeading(world,observer,target,profile,e);e.relocations++;e.spot=null;
        if(heading==null)return null;
        double variation=mix(observer.id(),observer.lifeId(),e.relocations,11);e.relocateHeading=heading;e.relocateUntil=now+(pressured?.9+.9*variation:1.2+1.2*variation);return heading;
    }
    public static Cover findCover(BotNavigationWorld world,Player observer,Vector threat,Engagement e,double now) {
        e.owned();if(now<e.coverUntil&&(e.cover!=null||e.coverDuck))return new Cover(e.cover,e.coverDuck);
        if(now<e.nextCoverSearchAt)return new Cover(null,false);
        e.nextCoverSearchAt=now+1;e.cover=null;e.coverDuck=false;
        var ducked=new Vector(observer.eye().x(),observer.eye().y(),observer.eye().z()+.9);
        if(!world.lineOfSight(ducked,threat)) {e.coverDuck=true;e.coverUntil=now+3;return new Cover(null,true);}
        double away=Math.atan2(observer.position().y()-threat.y(),observer.position().x()-threat.x());int rays=0;
        for(double radius:new double[]{2,3.5,5,7})for(int index=0;index<12;index++) {
            int step=(index+1)/2*(index%2!=0?1:-1);if(Math.abs(step)>4)continue;
            double angle=away+step*(2*Math.PI/12);var heading=new Vector(Math.cos(angle),Math.sin(angle),0);
            var surface=world.surface((int)Math.floor(observer.position().x()+heading.x()*radius),(int)Math.floor(observer.position().y()+heading.y()*radius),observer.position().z(),2,false);
            if(surface==null||Math.abs(surface.position().z()-observer.position().z())>1.6||!walkableHeading(world,observer,heading,Math.min(radius,3)))continue;
            rays++;if(!world.lineOfSight(surface.position(),threat)) {e.cover=surface.position();e.coverUntil=now+4;return new Cover(e.cover,false);}
            if(rays>=14)return new Cover(null,false);
        }
        return new Cover(null,false);
    }
    public static List<Vector> firingLine(Vector observer,Vector threat,double spread) {
        double dx=threat.x()-observer.x(),dy=threat.y()-observer.y(),length=Math.hypot(dx,dy);
        if(length<1e-6)return List.of(threat);double x=-dy/length*spread,y=dx/length*spread;
        return List.of(threat,new Vector(threat.x()+x,threat.y()+y,threat.z()),new Vector(threat.x()-x,threat.y()-y,threat.z()));
    }
    public static boolean exposed(BotNavigationWorld world,Vector eye,List<Vector> threats) {return threats.stream().anyMatch(t->world.lineOfSight(eye,t));}
    public static Vector findShelter(BotNavigationWorld world,Player observer,List<Vector> threats,double reach,double minGap,double side,int maxRays) {
        if(threats.isEmpty())return null;var primary=threats.getFirst();double away=Math.atan2(observer.position().y()-primary.y(),observer.position().x()-primary.x());int first=side>=0?1:-1,rays=0;
        for(double radius:new double[]{2.5,4,6,8,10.5}) {
            if(radius<minGap||radius>reach)continue;
            for(int index=0;index<12;index++) {
                int step=(index+1)/2*(index%2!=0?first:-first);if(Math.abs(step)>4)continue;
                double angle=away+step*(2*Math.PI/12);var heading=new Vector(Math.cos(angle),Math.sin(angle),0);
                var surface=world.surface((int)Math.floor(observer.position().x()+heading.x()*radius),(int)Math.floor(observer.position().y()+heading.y()*radius),observer.position().z(),2,false);
                if(surface==null||Math.abs(surface.position().z()-observer.position().z())>2.1||!walkableHeading(world,observer,heading,Math.min(radius,3)))continue;
                boolean hidden=true;for(var threat:threats) {rays++;if(world.lineOfSight(surface.position(),threat)) {hidden=false;break;}}
                if(hidden&&PathFollowing.straightWalkable(world,observer.position(),surface.position()))return surface.position();
                if(rays>=maxRays)return null;
            }
        }
        return null;
    }
    public static Vector findDeadGround(BotNavigationWorld world,Player observer,List<Vector> threats,double side,int maxRays) {
        if(threats.isEmpty())return null;var primary=threats.getFirst();double away=Math.atan2(observer.position().y()-primary.y(),observer.position().x()-primary.x());int first=side>=0?1:-1,rays=0;
        for(double radius:new double[]{14,20,27})for(int index=0;index<8;index++) {
            int step=(index+1)/2*(index%2!=0?first:-first);if(Math.abs(step)>2)continue;
            double angle=away+step*(2*Math.PI/8);
            var surface=world.surface((int)Math.floor(observer.position().x()+Math.cos(angle)*radius),(int)Math.floor(observer.position().y()+Math.sin(angle)*radius),observer.position().z(),6,false);
            if(surface==null)continue;
            boolean hidden=true;for(var threat:threats) {rays++;if(world.lineOfSight(surface.position(),threat)) {hidden=false;break;}}
            if(hidden)return surface.position();if(rays>=maxRays)return null;
        }
        return null;
    }
    public static Vector hazardEscape(BotNavigationWorld world,Perception frame,Player observer,BotProfile profile,Engagement e,double now) {
        e.owned();if(now<e.evadeUntil)return e.evadeHeading;
        Entity threat=null;Vector at=null;double closest=Double.POSITIVE_INFINITY;
        for(var entity:frame.entities()) {
            if(!entity.alive()||!entity.hazardous()||entity.blastRadius()<=0)continue;
            boolean projectile=entity.kind().equals("projectile");
            if(!projectile&&entity.team()!=observer.team())continue;
            if(!projectile&&!(entity.detonateAt()-now>0&&entity.detonateAt()-now<=4))continue;
            if(projectile&&Math.hypot(Math.hypot(entity.velocity().x(),entity.velocity().y()),entity.velocity().z())>45)continue;
            double remaining=entity.detonateAt()>0?entity.detonateAt()-now:.4,lead=Math.max(0,Math.min(.4,remaining));
            var point=new Vector(entity.position().x()+entity.velocity().x()*lead,entity.position().y()+entity.velocity().y()*lead,entity.position().z());
            double gap=distance(point,observer.position());
            if(gap>entity.blastRadius()+3||mix(observer.id(),observer.lifeId(),entity.id())>.3+.75*profile.skill())continue;
            if(threat==null||gap<closest) {threat=entity;at=point;closest=gap;}
        }
        if(threat==null)return null;
        double dx=observer.position().x()-at.x(),dy=observer.position().y()-at.y(),length=Math.hypot(dx,dy);
        if(length<1e-3) {double angle=mix(observer.id(),threat.id(),3)*2*Math.PI;dx=Math.cos(angle);dy=Math.sin(angle);length=1;}
        double base=Math.atan2(dy/length,dx/length);
        for(double turn:new double[]{0,.7,-.7,1.4,-1.4}) {
            var heading=new Vector(Math.cos(base+turn),Math.sin(base+turn),0);
            if(walkableHeading(world,observer,heading,3)) {e.evadeHeading=heading;e.evadeUntil=now+.6;e.evadeEntity=threat.id();return heading;}
        }
        return null;
    }
}
