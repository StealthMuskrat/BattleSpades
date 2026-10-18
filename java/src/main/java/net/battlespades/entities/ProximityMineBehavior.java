// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original armed mine: horizontal trigger radius and vertical feet/layer test; own team never trips it. */
public final class ProximityMineBehavior extends DamageableEntityBehavior implements DeployableRuntime.Explosive {
    private final DeviceBlast blast;private final int team,layers;private final double triggerRadius,armDelay,verticalOffset;private Double armedAt;private PlayerLife triggeredBy;
    public ProximityMineBehavior(DeviceBlast blast,double health,int team,double triggerRadius,double armDelay,int layers,double verticalOffset){super(health);Objects.requireNonNull(blast);this.blast=new DeviceBlast(blast.throwerId(),blast.damage(),blast.blockDamage(),blast.craterRadius(),blast.killType(),blast.radius(),blast.forceDestroy(),blast.minimum(),blast.maximum(),blast.damageType(),true);this.team=team;this.triggerRadius=triggerRadius;this.armDelay=armDelay;this.layers=layers;this.verticalOffset=verticalOffset;}
    @Override public DeviceBlast blast(){return blast;}
    @Override public PlayerLife triggeredBy(){return triggeredBy;}
    @Override public Vector explosionCenter(MapEntity entity){return new Vector(entity.x+.5,entity.y+.5,entity.z+verticalOffset);}
    @Override public Vector hitCenter(MapEntity entity){return explosionCenter(entity);}
    @Override public void onTick(MapEntity entity,double dt,EntityContext context){
        if(armedAt==null){armedAt=context.now()+armDelay;return;}if(context.now()<armedAt)return;double squared=triggerRadius*triggerRadius;var center=explosionCenter(entity);
        for(var player:context.players()){if(player.team()==team)continue;var at=player.position();double x=at.x()-center.x(),y=at.y()-center.y(),feet=at.z()+((player.motion().snapshot().movement()&32)!=0?1.35:2.25);if(x*x+y*y<=squared&&Math.abs(feet-center.z())<=layers){DeployableRuntime.detonate(this,entity,context);return;}}
    }
    @Override public void onDestroyed(MapEntity entity,PlayerLife source,EntityContext context){if(source!=null)triggeredBy=source;DeployableRuntime.detonate(this,entity,context);}
    @Override public void onSupportLost(MapEntity entity,EntityContext context){DeployableRuntime.detonate(this,entity,context);}
}
