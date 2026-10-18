// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.Objects;
import net.battlespades.player.SpawnGeometry;

/** Complete immutable preflight output; materialization creates fresh owner-bound mutable objects. */
public final class PreparedMap {
    public record Bound(MutableWorld world,SpawnGeometry spawns) implements AutoCloseable {
        public Bound {Objects.requireNonNull(world);Objects.requireNonNull(spawns);if(spawns.world()!=world)throw new IllegalArgumentException("Spawn/world identity");}
        @Override public void close(){world.close();}
    }
    private final MapAssembly assembly;
    private final SpawnGeometry.Prepared spawns;
    private PreparedMap(MapAssembly assembly,SpawnGeometry.Prepared spawns){this.assembly=assembly;this.spawns=spawns;}
    public static PreparedMap prepare(MapAssembly assembly){
        Objects.requireNonNull(assembly);
        try(var temporary=assembly.world(0,1)){
            var geometry=new SpawnGeometry(temporary,assembly.metadata().spawnLayout());
            return new PreparedMap(assembly,geometry.prepare(assembly.terrain()));
        }
    }
    public MapAssembly assembly(){return assembly;}
    public Bound bind(long epoch,int journalLimit){
        var world=assembly.world(epoch,journalLimit);
        try{return new Bound(world,spawns.bind(world,assembly.metadata().spawnLayout()));}
        catch(RuntimeException|Error failure){world.close();throw failure;}
    }
}
