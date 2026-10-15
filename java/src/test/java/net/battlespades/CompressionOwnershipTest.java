// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.List;
import java.util.Map;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompressionOwnershipTest {
    private VxlMap map() {return VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto");}
    @Test void completedButUncollectedJobsAreBoundedAndResultsReleaseCapacity() {
        var snapshot=new MapSyncSnapshot(map(),Map.of(),0,false);
        try(var compression=new BoundedSnapshotCompression(1,1)) {
            var a=compression.submit(snapshot).orElseThrow();var b=compression.submit(snapshot).orElseThrow();assertTrue(compression.submit(snapshot).isEmpty());assertFalse(compression.available());
            long until=System.nanoTime()+4_000_000_000L;boolean done=false;
            while(System.nanoTime()<until) {var result=a.poll();if(result.isPresent()) {assertEquals(List.of(),result.orElseThrow());done=true;break;}Thread.onSpinWait();}
            assertTrue(done);assertEquals(1,compression.outstanding());assertThrows(IllegalStateException.class,a::poll);b.close();assertEquals(0,compression.outstanding());
            var c=compression.submit(snapshot).orElseThrow();c.close();assertEquals(0,compression.outstanding());
        }
    }
    @Test void mutableHandlesNeverCrossOwnersAndCompressionHonorsCancellation() throws Exception {
        var snapshot=new MapSyncSnapshot(map(),Map.of(),0,true);
        try(var compression=new BoundedSnapshotCompression(1,1);var worker=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            assertInstanceOf(IllegalStateException.class,worker.submit(()->{try {compression.submit(snapshot);return null;}catch(Exception e) {return e;}}).get());
            var result=worker.submit(()->{Thread.currentThread().interrupt();try {snapshot.buildChunks();return null;}catch(RuntimeException e) {return e;}}).get();
            assertInstanceOf(java.util.concurrent.CancellationException.class,result);assertEquals(0,compression.outstanding());
        }
    }
}
