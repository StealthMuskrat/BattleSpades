// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.Objects;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.MutableWorld;

/** Live raw voxel queries and original dry_surface_anchor ring ordering over the canonical owned map. */
public final class PickupWorld {
    private final MutableWorld world;private final CollisionMap map;
    public PickupWorld(MutableWorld world){this.world=Objects.requireNonNull(world);map=CollisionMap.of(world);}
    public boolean solid(int x,int y,int z){return map.solid(x,y,z);}
    public Vector drySurfaceAnchor(double x,double y,int search){
        int bx=(int)x,by=(int)y;
        for(int r=0;r<=search;r++)for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++){
            if(Math.max(Math.abs(dx),Math.abs(dy))!=r)continue;int cx=bx+dx,cy=by+dy;
            if(cx<0||cx>=512||cy<0||cy>=512)continue;int z=world.surfaceZ(cx,cy);if(z<=238)return new Vector(cx+.5,cy+.5,z);
        }
        return new Vector(x,y,world.surfaceZ(bx,by));
    }
}
