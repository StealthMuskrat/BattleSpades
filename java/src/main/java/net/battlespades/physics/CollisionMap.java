// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.physics;

import net.battlespades.world.MutableWorld;
import net.battlespades.world.VxlMap;

/** Raw voxel lookup. PlayerPhysics owns the retail boundary and water-row rules. */
@FunctionalInterface
public interface CollisionMap {
    boolean solid(int x, int y, int z);

    static CollisionMap of(VxlMap map) { return map::solid; }
    static CollisionMap of(MutableWorld world) {
        return (x,y,z) -> x>=0 && x<512 && y>=0 && y<512 && z>=0 && z<240
                && world.solid(new MutableWorld.Cell(x,y,z));
    }
}
