// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.List;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Live gameplay-thread blast and muzzle checks; tactical worker checks cannot replace these. */
public final class BotActionSafety {
    private BotActionSafety(){}
    public record Actor(int id,int team,boolean alive,boolean spawned,Vector position,Vector eye){}
    public record Explosive(int type,boolean alive,Vector position,double radius){}
    @FunctionalInterface public interface Raycast {boolean blocked(Vector origin,Vector direction,double distance);}
    public static boolean deploySafe(Actor owner,Vector target,double radius,List<Actor> players,List<Explosive> explosives){
        if(!finite(target)||!Double.isFinite(radius)||radius<=0)return false;
        for(var p:players){
            if(p.id()==owner.id()||p.team()!=owner.team()||!p.alive()||!p.spawned())continue;
            if(!finite(p.position())||distanceSquared(p.position(),target)<=square(radius+1.5))return false;
        }
        for(var e:explosives){
            if(!e.alive()||(e.type()!=10&&e.type()!=9&&e.type()!=38)||e.radius()<=0)continue;
            if(!finite(e.position())||!Double.isFinite(e.radius())||distanceSquared(e.position(),target)<=square(radius+e.radius()+1))return false;
        }
        return true;
    }
    /** Direction must be normalized from the current authoritative aim, not the proposal. */
    public static boolean launchSafe(Actor owner,Vector direction,double radius,Vector target,double clearance,List<Actor> players,Raycast rays){
        if(!finite(owner.eye())||!finite(direction)||!Double.isFinite(radius)||!Double.isFinite(clearance))return false;
        double length=distanceSquared(direction,new Vector(0,0,0));
        if(Math.abs(length-1)>1e-6)return false;
        radius=Math.max(0,radius);
        if(rays!=null&&rays.blocked(owner.eye(),direction,radius+clearance))return false;
        if(target!=null){
            if(!finite(target)||rays==null)return false;
            double distance=Math.sqrt(distanceSquared(owner.eye(),target));
            if(distance<=radius+3)return false;
            Vector ray=new Vector((target.x()-owner.eye().x())/distance,(target.y()-owner.eye().y())/distance,(target.z()-owner.eye().z())/distance);
            if(rays.blocked(owner.eye(),ray,Math.max(0,distance-1)))return false;
        }
        for(var p:players){
            if(p.id()==owner.id()||p.team()!=owner.team()||!p.alive()||!p.spawned())continue;
            if(!finite(p.eye()))return false;
            double dx=p.eye().x()-owner.eye().x(),dy=p.eye().y()-owner.eye().y(),dz=p.eye().z()-owner.eye().z();
            double along=dx*direction.x()+dy*direction.y()+dz*direction.z();
            if(!(along>.75&&along<96))continue;
            Vector closest=new Vector(owner.eye().x()+direction.x()*along,owner.eye().y()+direction.y()*along,owner.eye().z()+direction.z()*along);
            if(distanceSquared(p.eye(),closest)<=square(1.75))return false;
            if(target!=null&&distanceSquared(p.eye(),target)<=square(radius+1.5))return false;
        }
        return true;
    }
    public static Vector normalized(Vector value){
        if(!finite(value))return null;
        double length=Math.sqrt(value.x()*value.x()+value.y()*value.y()+value.z()*value.z());
        return !Double.isFinite(length)||length<=1e-6?null:new Vector(value.x()/length,value.y()/length,value.z()/length);
    }
    private static boolean finite(Vector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static double distanceSquared(Vector a,Vector b){return square(a.x()-b.x())+square(a.y()-b.y())+square(a.z()-b.z());}
    private static double square(double n){return n*n;}
}
