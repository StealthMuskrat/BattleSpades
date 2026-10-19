// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Owned flight list and source-ordered timers, swept contact, legacy bounce and sticky transitions. */
public final class ProjectileEngine {
    public interface Terrain {boolean solid(int x,int y,int z);}
    public enum Kind {EXPLOSION,DESTROYED,DRILL,DEPLOY,ATTACH,DETACH}
    public record Event(Kind kind,Projectile projectile){}
    public static final class Projectile {
        public final ProjectileSpec spec;public final int tool,throwerId;public final double spawnedAt,lifespanAt;
        public Vector position,velocity;public Double explodeAt;public Integer entityId,attachedPlayerId,contactPlayerId,sourceLoop,blockColor,turretId,turretTargetId;
        public EntityRegistry.Cell contactBlock;public boolean stuck;
        private Projectile(ProjectileSpec spec,int tool,Vector position,Vector velocity,Double fuse,int thrower,double now){this.spec=Objects.requireNonNull(spec);this.tool=tool;this.position=position;this.velocity=velocity;throwerId=thrower;spawnedAt=now;explodeAt=fuse==null?null:now+Math.max(0,fuse);lifespanAt=spec.lifespan()>0?now+spec.lifespan():0;}
    }
    private final List<Projectile> live=new ArrayList<>();
    public List<Projectile> projectiles(){return List.copyOf(live);}
    public Projectile spawn(int tool,Vector position,Vector velocity,double fuse,int thrower,double now){var spec=ProjectileSpec.TOOLS.get(tool);if(spec==null)return null;Double timer=spec.motion()==ProjectileSpec.Motion.CONTACT||spec.motion()==ProjectileSpec.Motion.DEPLOY||spec.motion()==ProjectileSpec.Motion.STICK&&fuse<=0?null:Double.valueOf(fuse);var p=new Projectile(spec,tool,position,velocity,timer,thrower,now);live.add(p);return p;}
    public Projectile spawn(ProjectileSpec spec,Vector position,Vector velocity,int thrower,double now){var p=new Projectile(spec,-1,position,velocity,null,thrower,now);live.add(p);return p;}
    public List<Projectile> removeByThrower(int id){var removed=live.stream().filter(p->p.throwerId==id).toList();live.removeAll(removed);return removed;}
    public List<Projectile> detach(int id){var detached=live.stream().filter(p->Objects.equals(p.attachedPlayerId,id)).toList();for(var p:detached)p.attachedPlayerId=null;return detached;}
    public void clear(){live.clear();}
    public List<Event> update(double dt,Terrain terrain,double now,List<PlayerLife> players){
        var events=new ArrayList<Event>();var keep=new ArrayList<Projectile>();
        for(var p:List.copyOf(live)){
            if(p.explodeAt!=null&&now>=p.explodeAt){events.add(new Event(Kind.EXPLOSION,p));continue;}
            if(p.lifespanAt!=0&&now>=p.lifespanAt){events.add(new Event(Kind.DESTROYED,p));continue;}
            if(p.stuck){
                if(p.attachedPlayerId!=null){var target=players.stream().filter(t->t.id()==p.attachedPlayerId&&t.alive()&&t.spawned()).findFirst().orElse(null);if(target!=null){var at=target.position();p.position=new Vector(at.x(),at.y(),at.z()+1);}else{p.attachedPlayerId=null;events.add(new Event(Kind.DETACH,p));}}
                else if(p.contactBlock!=null&&!terrain.solid(p.contactBlock.x(),p.contactBlock.y(),p.contactBlock.z())){events.add(new Event(Kind.EXPLOSION,p));continue;}
                keep.add(p);continue;
            }
            if(p.spec.motion()==ProjectileSpec.Motion.CONTACT||p.spec.motion()==ProjectileSpec.Motion.DEPLOY){
                String contact=contact(p,dt,terrain,p.spec.motion()==ProjectileSpec.Motion.CONTACT?players:List.of());
                if(contact!=null){if(p.spec.name().equals("drill")&&contact.equals("world")){events.add(new Event(Kind.DRILL,p));keep.add(p);continue;}events.add(new Event(p.spec.motion()==ProjectileSpec.Motion.DEPLOY?Kind.DEPLOY:Kind.EXPLOSION,p));continue;}
            }else if(bounce(p,dt,terrain,p.spec.motion()==ProjectileSpec.Motion.STICK?players:List.of())&&p.spec.motion()==ProjectileSpec.Motion.STICK){p.velocity=new Vector(0,0,0);p.stuck=true;if(p.explodeAt==null)p.explodeAt=now+5;events.add(new Event(Kind.ATTACH,p));}
            if(p.explodeAt==null&&p.lifespanAt==0&&now-p.spawnedAt>10){events.add(new Event(Kind.EXPLOSION,p));continue;}keep.add(p);
        }
        live.clear();live.addAll(keep);return List.copyOf(events);
    }
    private static void accelerate(Projectile p,double dt){var v=p.velocity;double x=v.x(),y=v.y(),z=v.z()+30*p.spec.gravity()*dt,speed=Math.sqrt(x*x+y*y+z*z);if(speed>511.98999){double k=511.98999/speed;x*=k;y*=k;z*=k;}p.velocity=new Vector(x,y,z);}
    private static boolean solid(Terrain t,double x,double y,double z){return t.solid((int)x,(int)y,(int)z);}
    private static boolean bounce(Projectile p,double dt,Terrain terrain,List<PlayerLife> players){
        accelerate(p,dt);var at=p.position;double x=at.x(),y=at.y(),z=at.z(),vx=p.velocity.x(),vy=p.velocity.y(),vz=p.velocity.z(),nx=x+vx*dt,ny=y+vy*dt,nz=z+vz*dt;boolean hit=solid(terrain,nx,ny,nz);
        if(hit){p.contactBlock=new EntityRegistry.Cell((int)nx,(int)ny,(int)nz);boolean bounced=false;if(solid(terrain,nx,y,z)){vx=-vx;nx=x;bounced=true;}if(solid(terrain,x,ny,z)){vy=-vy;ny=y;bounced=true;}if(solid(terrain,x,y,nz)){vz=-vz;nz=z;bounced=true;}if(!bounced){vz=-vz;nx=x;ny=y;nz=z;}p.velocity=new Vector(vx*.36,vy*.36,vz*.36);}
        if(!hit){var end=new Vector(nx,ny,nz);var c=first(at,end,players,p.throwerId);if(c!=null){nx=x+(nx-x)*c.fraction;ny=y+(ny-y)*c.fraction;nz=z+(nz-z)*c.fraction;p.attachedPlayerId=c.player.id();hit=true;}}
        p.position=new Vector(nx,ny,nz);return hit;
    }
    private static String contact(Projectile p,double dt,Terrain terrain,List<PlayerLife> players){
        accelerate(p,dt);p.contactBlock=null;var v=p.velocity;int steps=Math.max(1,(int)(Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z())*dt/.5)+1);double sx=v.x()*dt/steps,sy=v.y()*dt/steps,sz=v.z()*dt/steps;
        for(int i=0;i<steps;i++){var at=p.position;var end=new Vector(at.x()+sx,at.y()+sy,at.z()+sz);if(solid(terrain,end.x(),end.y(),end.z())){p.contactBlock=new EntityRegistry.Cell((int)end.x(),(int)end.y(),(int)end.z());return "world";}var c=first(at,end,players,p.throwerId);if(c!=null){p.contactPlayerId=c.player.id();p.position=new Vector(at.x()+sx*c.fraction,at.y()+sy*c.fraction,at.z()+sz*c.fraction);return "player";}p.position=end;}
        var at=p.position;return at.x()>=0&&at.x()<512&&at.y()>=0&&at.y()<512&&at.z()>-64&&at.z()<300?null:"bounds";
    }
    private record Contact(double fraction,PlayerLife player){}
    private static Contact first(Vector start,Vector end,List<PlayerLife> players,int thrower){Contact best=null;for(var p:players){if(p.id()==thrower||!p.alive()||!p.spawned())continue;var at=p.position();double height=(p.motion().snapshot().movement()&32)!=0?1.8:2.7;Double t=entry(start,end,new Vector(at.x()-.95,at.y()-.95,at.z()-.5),new Vector(at.x()+.95,at.y()+.95,at.z()+height+.5));if(t!=null&&(best==null||t<best.fraction))best=new Contact(t,p);}return best;}
    private static Double entry(Vector start,Vector end,Vector low,Vector high){double enter=0,leave=1;double[] s={start.x(),start.y(),start.z()},e={end.x(),end.y(),end.z()},lo={low.x(),low.y(),low.z()},hi={high.x(),high.y(),high.z()};for(int i=0;i<3;i++){double d=e[i]-s[i];if(Math.abs(d)<1e-12){if(s[i]<lo[i]||s[i]>hi[i])return null;continue;}double a=(lo[i]-s[i])/d,b=(hi[i]-s[i])/d;if(a>b){double tmp=a;a=b;b=tmp;}enter=Math.max(enter,a);leave=Math.min(leave,b);if(enter>leave)return null;}return enter;}
}
