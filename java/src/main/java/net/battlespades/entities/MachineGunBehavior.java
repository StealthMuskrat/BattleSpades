// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.combat.BlastRuntime;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.ChangeEntity;
import net.battlespades.protocol.Vector3f;
import net.battlespades.session.StandardMatchDomain;

/** Actual server-owned MG mount lifecycle, relocation wallet, movement release and explosive retirement. */
public final class MachineGunBehavior extends EntityBehavior {
    private final StandardMatchDomain match;public final int ownerId,team;public double health=100;public int ammo=999;private Integer carrier;private double mountedAt;
    public MachineGunBehavior(StandardMatchDomain match,int ownerId,int team){this.match=Objects.requireNonNull(match);this.ownerId=ownerId;this.team=team;takesDamage=true;hitRadius=1.5;hitCenterOffset=new Vector(0,0,-.75);}
    public Integer carrier(){return carrier;}
    private PlayerLife player(int id){return match.lives().players().stream().filter(p->p.id()==id).findFirst().orElse(null);}
    private void announce(MapEntity e,int id){match.entities().known(e.id(),new ChangeEntity(e.id(),3,0,new Vector3f(0,0,0),id,-1,0));}
    public boolean mount(MapEntity e,PlayerLife p){if(carrier!=null)return false;carrier=p.id();mountedAt=match.wall();e.playerId=p.id();p.mountedEntityId(e.id());announce(e,p.id());return true;}
    public boolean unmount(MapEntity e){
        if(carrier==null)return false;var p=player(carrier);boolean mounted=p!=null&&Objects.equals(p.mountedEntityId(),e.id());if(mounted)p.mountedEntityId(null);carrier=null;e.playerId=255;announce(e,255);if(mounted)match.inputs().unmounted(p);return true;
    }
    @Override public void onTick(MapEntity e,double dt,EntityContext ctx){
        if(carrier==null)return;var p=player(carrier);if(p==null||!p.alive()||!p.spawned()){unmount(e);return;}var at=p.position();double x=at.x()-e.x,y=at.y()-e.y,z=at.z()-e.z;if(x*x+y*y+z*z>16||ctx.now()-mountedAt>=.25&&p.motion().snapshot().movement()!=0)unmount(e);
    }
    @Override public void onDamage(MapEntity e,double amount,PlayerLife source,EntityContext ctx){health-=Math.max(0,amount);if(health<=0&&e.alive)destroy(e,source,ctx);}
    @Override public void onSupportLost(MapEntity e,EntityContext ctx){destroy(e,null,ctx);}
    private void destroy(MapEntity e,PlayerLife source,EntityContext ctx){
        if(!e.alive)return;if(carrier!=null)unmount(e);e.alive=false;var request=new BlastRuntime.Request(new Vector(e.x,e.y,e.z),100,5,11,source);request.craterRadius=1;request.forceDestroy=true;request.radius=3;request.minimum=.2;request.maximum=1;match.entities().blasts().apply(request);if(ctx.destroy()!=null)ctx.destroy().accept(e.id());if(ctx.registry()!=null)ctx.registry().remove(e.id());
    }
}
