// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original grave fuse uses a settled blast center while retaining the airborne wire position. */
public final class GraveBehavior extends EntityBehavior implements DeployableRuntime.Explosive {
    private final DeviceBlast blast;private final double fuse;private final Vector center;private Double detonateAt;
    public GraveBehavior(DeviceBlast blast,double fuse,Vector center){this.blast=Objects.requireNonNull(blast);this.fuse=fuse;this.center=center;}
    @Override public DeviceBlast blast(){return blast;}
    @Override public PlayerLife triggeredBy(){return null;}
    @Override public Vector explosionCenter(MapEntity entity){return center==null?new Vector(entity.x,entity.y,entity.z):center;}
    @Override public void onTick(MapEntity entity,double dt,EntityContext context){if(detonateAt==null){detonateAt=context.now()+fuse;return;}if(context.now()>=detonateAt)DeployableRuntime.detonate(this,entity,context);}
}
