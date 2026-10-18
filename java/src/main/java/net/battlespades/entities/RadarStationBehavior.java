// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Original radar lifetime/fuse and exactly-once team/owner teardown. Detection belongs to the retail client. */
public final class RadarStationBehavior extends DamageableEntityBehavior {
    private final int team;private final double lifetime;private Double expiresAt;
    public RadarStationBehavior(int team,double lifetime,double health){super(health);this.team=team;this.lifetime=lifetime;hitRadius=.9;hitCenterOffset=new Vector(.5,.5,.55);}
    @Override public void onTick(MapEntity entity,double dt,EntityContext context){if(expiresAt==null){expiresAt=context.now()+lifetime;return;}if(context.now()<expiresAt){double remaining=expiresAt-context.now();entity.fuse=remaining>0?remaining:0;return;}onDestroyed(entity,null,context);}
    @Override public void onDestroyed(MapEntity entity,PlayerLife source,EntityContext context){
        if(!entity.alive)return;
        if(context.devices()!=null){context.devices().radarRemoved(team);var owner=context.devices().player(entity.playerId);if(owner!=null&&owner.radarEntityId()!=null&&owner.radarEntityId()==entity.id())owner.radarEntityId(null);}
        super.onDestroyed(entity,source,context);
    }
    @Override public void onSupportLost(MapEntity entity,EntityContext context){onDestroyed(entity,null,context);}
}
