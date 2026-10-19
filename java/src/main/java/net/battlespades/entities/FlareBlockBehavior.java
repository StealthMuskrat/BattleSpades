// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;

/** A connected flare component survives only while a path reaches real terrain. */
public final class FlareBlockBehavior extends DamageableEntityBehavior {
    private static final int[][] NEIGHBORS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    public FlareBlockBehavior(){super(5);hitRadius=.75;hitCenterOffset=new Vector(.5,.5,.5);}
    public static EntityRegistry.Cell cell(MapEntity e){return new EntityRegistry.Cell((int)e.x,(int)e.y,(int)e.z);}
    public static boolean supported(EntityRegistry registry,EntityRegistry.Cell cell,java.util.function.Predicate<EntityRegistry.Cell> solid,Integer ignore){
        var flares=new HashSet<EntityRegistry.Cell>();for(var e:registry.all())if(e.alive&&e.kind.equals("flare_block")&&!Objects.equals(e.id(),ignore))flares.add(cell(e));
        var pending=new ArrayDeque<EntityRegistry.Cell>();var seen=new HashSet<EntityRegistry.Cell>();pending.add(cell);
        while(!pending.isEmpty()){var c=pending.removeLast();if(!seen.add(c))continue;for(var d:NEIGHBORS){var n=new EntityRegistry.Cell(c.x()+d[0],c.y()+d[1],c.z()+d[2]);if(solid.test(n))return true;if(flares.contains(n)&&!seen.contains(n))pending.addLast(n);}}return false;
    }
    @Override public void onTick(MapEntity e,double dt,EntityContext ctx){if(ctx.world()!=null&&ctx.registry()!=null&&!supported(ctx.registry(),cell(e),c->ctx.world().solid(c.x(),c.y(),c.z()),e.id()))onDestroyed(e,null,ctx);}
}
