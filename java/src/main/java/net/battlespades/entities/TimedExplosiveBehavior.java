// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original timed charge: first tick starts its fuse; damage/support loss detonates early. */
public final class TimedExplosiveBehavior extends DamageableEntityBehavior implements DeployableRuntime.Explosive {
    private final DeviceBlast blast;private final double fuse;private Double detonateAt;private PlayerLife triggeredBy;
    public TimedExplosiveBehavior(DeviceBlast blast,double fuse,double health){super(health);this.blast=Objects.requireNonNull(blast);this.fuse=fuse;hitRadius=.65;}
    @Override public DeviceBlast blast(){return blast;}
    @Override public PlayerLife triggeredBy(){return triggeredBy;}
    @Override public Vector explosionCenter(MapEntity entity){return DeployableRuntime.attachmentCenter(entity);}
    @Override public Vector hitCenter(MapEntity entity){return explosionCenter(entity);}
    @Override public void onTick(MapEntity entity,double dt,EntityContext context){if(detonateAt==null){detonateAt=context.now()+fuse;return;}if(context.now()>=detonateAt)DeployableRuntime.detonate(this,entity,context);}
    @Override public void onDestroyed(MapEntity entity,PlayerLife source,EntityContext context){if(source!=null)triggeredBy=source;DeployableRuntime.detonate(this,entity,context);}
    @Override public void onSupportLost(MapEntity entity,EntityContext context){DeployableRuntime.detonate(this,entity,context);}
}
