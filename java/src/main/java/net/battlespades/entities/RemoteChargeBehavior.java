// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original C4: a shot removes it, while remote detonation or support loss invokes its blast. */
public final class RemoteChargeBehavior extends DamageableEntityBehavior implements DeployableRuntime.Explosive {
    private final DeviceBlast blast;
    public RemoteChargeBehavior(DeviceBlast blast,double health){super(health);this.blast=Objects.requireNonNull(blast);hitRadius=.65;}
    @Override public DeviceBlast blast(){return blast;}
    @Override public PlayerLife triggeredBy(){return null;}
    @Override public Vector explosionCenter(MapEntity entity){return DeployableRuntime.attachmentCenter(entity);}
    @Override public Vector hitCenter(MapEntity entity){return explosionCenter(entity);}
    public void detonate(MapEntity entity,EntityContext context){if(entity.alive)DeployableRuntime.detonate(this,entity,context);}
    @Override public void onDestroyed(MapEntity entity,PlayerLife source,EntityContext context){DeployableRuntime.forgetCharge(entity,context);super.onDestroyed(entity,source,context);}
    @Override public void onSupportLost(MapEntity entity,EntityContext context){DeployableRuntime.forgetCharge(entity,context);detonate(entity,context);}
}
