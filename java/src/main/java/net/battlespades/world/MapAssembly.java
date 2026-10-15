// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import net.battlespades.player.SpawnGeometry;

/** Validates a complete map before its caller replaces a live scene. No fallback silently replaces a failed load. */
public record MapAssembly(VxlMap terrain,MapMetadata metadata) {
    public MapAssembly {Objects.requireNonNull(terrain);Objects.requireNonNull(metadata);if((int)terrain.sourceCrc()!=metadata.environment().sourceCrc())throw new IllegalArgumentException("Metadata CRC differs from map");}
    public static MapAssembly load(Path path,String mode) throws IOException {
        // A loading bound, not a wire limit. The existing codec retains its own structural bounds.
        if(Files.size(path)>64*1024*1024L)throw new IOException("VXL exceeds 64 MiB loading bound");
        byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(64*1024*1024+1);}if(bytes.length>64*1024*1024)throw new IOException("VXL exceeds 64 MiB loading bound");
        var crc=new java.util.zip.CRC32();crc.update(bytes);
        var metadata=MapMetadata.load(path,mode,(int)crc.getValue());
        return new MapAssembly(VxlMap.load(bytes,metadata.vxlFormat()),metadata);
    }
    public MutableWorld world(long epoch,int journalLimit){return new MutableWorld(terrain,epoch,journalLimit);}
    public SpawnGeometry spawns(MutableWorld world){
        if(world.sourceCrc()!=metadata.environment().sourceCrc())throw new IllegalArgumentException("Spawn layout belongs to another map");
        var geometry=new SpawnGeometry(world,metadata.spawnLayout());geometry.prewarm();return geometry;
    }
}
