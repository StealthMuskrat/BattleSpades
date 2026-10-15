// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.battlespades.protocol.*;
import net.battlespades.world.MutableWorld;
import net.battlespades.world.VxlMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MutableWorldTest {
    private VxlMap base(String name) throws IOException {try(var in=getClass().getResourceAsStream("/golden/world/"+name+".vxl")) {assertNotNull(in);return VxlMap.load(in.readAllBytes(),"auto");}}
    @Test void committedCellsExplicitSurfacesHealthAndColumnsMatchOriginal() throws IOException {
        var points=GoldenPacketTest.lines("mutations.points.tsv");var columns=GoldenPacketTest.lines("mutations.columns.tsv");
        try(var world=new MutableWorld(base("cave"),1,32)) {
            int index=0;
            for(var op:GoldenPacketTest.lines("mutations.ops.tsv")) {
                var p=op.split("\t");world.set(new MutableWorld.Cell(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])),p[3].equals("1"),Integer.parseInt(p[4]),p[5].equals("none")?null:Double.parseDouble(p[5]));
                for(var line:points) {
                    var v=line.split("\t");if(Integer.parseInt(v[0])!=index) continue;var cell=new MutableWorld.Cell(Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]));
                    assertEquals(v[4].equals("1"),world.solid(cell),line);assertEquals((int)Long.parseLong(v[5]),world.color(cell),line);assertEquals(v[6].equals("1"),world.explicit(cell),line);assertEquals(Double.parseDouble(v[7]),world.initialHealth(cell),line);
                }
                for(var line:columns) {var v=line.split("\t");if(Integer.parseInt(v[0])==index) assertArrayEquals(HexFormat.of().parseHex(v[3]),world.serializeColumn(Integer.parseInt(v[1]),Integer.parseInt(v[2])),line);}
                assertEquals(++index,world.revision());assertEquals(0,world.journalSize());
            }
        }
    }
    @Test void replayCoalescesByFirstOccurrenceAndReadsFinalState() throws IOException {
        try(var world=new MutableWorld(base("empty"),1,16)) {
            var capture=world.capture(false,false);var a=new MutableWorld.Cell(1,2,3);var b=new MutableWorld.Cell(2,2,3);
            world.set(a,true,0x123456,9.0);world.set(b,true,0xabcdef,null);world.set(a,false,0,null);world.set(a,true,0x654321,3.0);
            var packets=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.COMPLETE,capture.lease().pump(7,100,99,p->{packets.add(p);return true;}));
            assertEquals(List.of(33,7,38,33,7,38),packets.stream().map(GamePacket::id).toList());
            assertEquals(0x654321,((SimplePackets.BlockBuildColored)packets.getFirst()).color());assertEquals(3,((BlockManagerState)packets.get(2)).users().getFirst().health());
            assertEquals(4,capture.lease().watermark());assertEquals(1,world.activeLeases());
            // Keep the lease until gameplay admission: edits while roster packets drain must still be retained.
            world.set(a,false,0,null);packets.clear();assertEquals(MutableWorld.Replay.COMPLETE,capture.lease().pump(7,101,99,p->{packets.add(p);return true;}));assertEquals(37,packets.getFirst().id());
            capture.lease().close();assertEquals(0,world.activeLeases());assertEquals(0,world.journalSize());
        }
    }
    @Test void partialCellRetryTracksChangedCellWithoutStarvingOnUnrelatedEdits() throws IOException {
        try(var world=new MutableWorld(base("empty"),1,64)) {
            var lease=world.capture(false,false).lease();var a=new MutableWorld.Cell(1,1,1);world.set(a,true,0x123456,9.0);
            var packets=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.WAITING,lease.pump(0,0,1,p->{packets.add(p);return true;}));
            world.set(new MutableWorld.Cell(2,2,2),true,0x444444,null);
            lease.pump(0,1,1,p->{packets.add(p);return true;});assertEquals(List.of(33,7),packets.stream().map(GamePacket::id).toList());
            world.set(a,false,0,null);lease.pump(0,2,1,p->{packets.add(p);return true;});assertEquals(37,packets.getLast().id());
            while(lease.pump(0,3,1,p->{packets.add(p);return true;})==MutableWorld.Replay.WAITING) {}
            assertEquals(0,world.journalSize());lease.close();
        }
    }
    @Test void frozenAirSnapshotBudgetOverflowAndEpochRetirement() throws IOException {
        var world=new MutableWorld(base("empty"),1,2);var a=new MutableWorld.Cell(1,1,1);var b=new MutableWorld.Cell(2,2,2);
        world.set(a,false,0,null);world.set(b,false,0,null);var capture=world.capture(false,true);world.set(a,true,0x123456,9.0);
        var sent=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.WAITING,capture.lease().pumpAir(0,0,1,p->{sent.add(p);return true;}));assertEquals(33,sent.getFirst().id());
        assertEquals(MutableWorld.Replay.WAITING,capture.lease().pumpAir(0,0,1,p->false));
        world.set(new MutableWorld.Cell(3,3,3),true,0,null);world.set(new MutableWorld.Cell(4,4,4),true,0,null);
        assertEquals(MutableWorld.Replay.OVERFLOW,capture.lease().pump(0,0,99,p->true));assertEquals(2,world.journalSize());
        world.close();assertEquals(MutableWorld.Replay.STALE,capture.lease().pump(0,0,99,p->true));
    }
    @Test void captureDoesNotReadLaterMutationsAndDoesNotCrossOwners() throws Exception {
        try(var world=new MutableWorld(base("empty"),1,8)) {
            var capture=world.capture(false,false);world.set(new MutableWorld.Cell(1,1,1),true,0x123456,null);assertTrue(capture.snapshot().buildChunks().isEmpty());
            try(var worker=java.util.concurrent.Executors.newSingleThreadExecutor()) {assertInstanceOf(IllegalStateException.class,worker.submit(()->{try {world.revision();return null;}catch(Exception e) {return e;}}).get());}
            capture.lease().close();assertThrows(IllegalArgumentException.class,()->world.serializeColumn(512,0));
        }
    }
}
