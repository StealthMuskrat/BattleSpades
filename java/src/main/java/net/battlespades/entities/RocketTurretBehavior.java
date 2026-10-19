// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.session.StandardMatchDomain;

/** Rocket turret targeting, turn rate, wall-clock projectile launch and monotonic ammo cadence. */
public final class RocketTurretBehavior extends EntityBehavior {
    private final StandardMatchDomain match;public final int ownerId,team;private double health=100,yaw,pitch,nextShot;private int ammo=10;private Integer target;
    public RocketTurretBehavior(StandardMatchDomain match,int ownerId,int team,double yaw,double now){this.match=Objects.requireNonNull(match);this.ownerId=ownerId;this.team=team;this.yaw=yaw;nextShot=now+1.5;takesDamage=true;hitRadius=1.25;hitCenterOffset=new Vector(0,0,-.55);}
    public WorldUpdate.TurretRow row(MapEntity e){return new WorldUpdate.TurretRow(e.id(),yaw,pitch);}
    public List<GamePacket> properties(MapEntity e){return List.of(new ChangeEntity(e.id(),5,0,new Vector3f(0,0,0),0,target==null?-1:target,0),new ChangeEntity(e.id(),7,0,new Vector3f(0,0,0),0,-1,ammo));}
    public void announce(MapEntity e){for(var packet:properties(e))match.entities().known(e.id(),packet);}
    private Vector origin(MapEntity e){return new Vector(e.x-.5,e.y-.5,e.z-1.16);}
    private boolean valid(MapEntity e,PlayerLife p,double radius){
        if(p==null||!p.alive()||!p.spawned()||p.team()==team)return false;var delta=CombatGeometry.subtract(p.position(),origin(e));double distance=delta.x()*delta.x()+delta.y()*delta.y()+delta.z()*delta.z();if(!Double.isFinite(distance)||distance<=1e-12||distance>radius*radius)return false;
        if(-Math.toDegrees(Math.atan2(delta.z(),Math.hypot(delta.x(),delta.y()))) < -30)return false;var direction=CombatGeometry.normalize(delta);return direction!=null&&HitTrace.terrainRay(net.battlespades.physics.CollisionMap.of(match.world()),origin(e),direction,Math.sqrt(distance)-.5)==null;
    }
    public void update(MapEntity e,double dt){
        if(!e.alive)return;if(ammo<=0){if(target!=null){target=null;announce(e);}return;}
        var players=match.lives().players();var chosen=players.stream().filter(p->Objects.equals(p.id(),target)).findFirst().orElse(null);
        if(!valid(e,chosen,50))chosen=players.stream().filter(p->valid(e,p,30)).min(Comparator.comparingDouble(p->{var at=p.position();double dx=at.x()-e.x,dy=at.y()-e.y,dz=at.z()-e.z;return dx*dx+dy*dy+dz*dz;})).orElse(null);
        Integer next=chosen==null?null:chosen.id();if(!Objects.equals(next,target)){target=next;announce(e);}if(chosen==null)return;
        var delta=CombatGeometry.subtract(chosen.position(),origin(e));double desiredYaw=Math.toDegrees(Math.atan2(delta.x(),delta.y())),desiredPitch=-Math.toDegrees(Math.atan2(delta.z(),Math.hypot(delta.x(),delta.y()))),amount=180*Math.max(0,dt);
        double diff=angle(desiredYaw-yaw);yaw=Math.abs(diff)<=amount?desiredYaw:yaw+Math.copySign(amount,diff);pitch=Math.abs(desiredPitch-pitch)<=amount?desiredPitch:pitch+Math.copySign(amount,desiredPitch-pitch);
        double now=match.monotonic();if(now<nextShot||Math.max(Math.abs(angle(desiredYaw-yaw)),Math.abs(desiredPitch-pitch))>.1)return;
        var direction=CombatGeometry.normalize(delta);var owner=match.entities().devices().player(ownerId);if(direction==null||owner==null)return;var at=CombatGeometry.along(origin(e),direction,1);var velocity=new Vector(direction.x()*75,direction.y()*75,direction.z()*75);var projectile=match.entities().projectiles().serverShot(ProjectileSpec.TURRET,at,velocity,owner);if(projectile==null)return;projectile.turretId=e.id();projectile.turretTargetId=chosen.id();ammo--;nextShot=now+1.5;announce(e);
    }
    private static double angle(double value){return ((value+180)%360+360)%360-180;}
    @Override public void onDamage(MapEntity e,double amount,PlayerLife source,EntityContext ctx){health=Math.max(0,health-Math.max(0,amount));if(health<=0)destroy(e,source,ctx);}
    @Override public void onSupportLost(MapEntity e,EntityContext ctx){destroy(e,null,ctx);}
    private void destroy(MapEntity e,PlayerLife source,EntityContext ctx){if(!e.alive)return;e.alive=false;var r=new BlastRuntime.Request(new Vector(e.x,e.y,e.z),100,15,11,source);r.radius=3;r.minimum=.2;r.maximum=1;r.craterRadius=1;r.forceDestroy=true;match.entities().blasts().apply(r);if(ctx.registry()!=null&&ctx.registry().remove(e.id())!=null&&ctx.destroy()!=null)ctx.destroy().accept(e.id());}
}
