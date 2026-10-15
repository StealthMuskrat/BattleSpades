// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;
import net.battlespades.protocol.*;
import net.battlespades.world.MapSyncSnapshot;
import net.battlespades.world.VxlMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class MapSyncTest {
    static Stream<String> snapshots() throws IOException { return GoldenPacketTest.lines("map-sync.tsv").stream(); }
    private byte[] raw(String name) throws IOException {
        if(name.equals("full_surface")) {
            byte[] column=new Wire.Writer().u8(0).u8(63).u8(63).u8(0).i32(0xff123456).toByteArray();
            byte[] data=new byte[column.length*512*512];for(int i=0;i<data.length;i+=column.length) System.arraycopy(column,0,data,i,column.length);return data;
        }
        try(var in=getClass().getResourceAsStream("/golden/world/"+name+".vxl")) {assertNotNull(in);return in.readAllBytes();}
    }
    private byte[] overlay() { return new Wire.Writer().u8(0).u8(238).u8(239).u8(0).i32(0xff123456).i32(0xffabcdef).toByteArray(); }
    private Map<MapSyncSnapshot.Column,byte[]> overlays(boolean changed) {
        if(!changed) return Map.of();return Map.of(new MapSyncSnapshot.Column(511,0),overlay(),new MapSyncSnapshot.Column(0,511),overlay(),new MapSyncSnapshot.Column(255,255),overlay());
    }
    @ParameterizedTest @MethodSource("snapshots")
    void originalUncompressedRecordDigest(String row) throws Exception {
        var p=row.split("\t");var map=VxlMap.load(raw(p[0]),"auto");
        var snapshot=new MapSyncSnapshot(map,overlays(p[2].equals("1")),17,p[1].equals("1"));
        var chunks=snapshot.buildChunks();var compressed=new ByteArrayOutputStream();
        for(int i=0;i<chunks.size();i++) {assertTrue(chunks.get(i).length<=1024);if(i<chunks.size()-1) assertEquals(1024,chunks.get(i).length);compressed.write(chunks.get(i));}
        byte[] records;
        if(chunks.isEmpty()) records=new byte[0];else try(var input=new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) {records=input.readAllBytes();}
        assertEquals(Integer.parseInt(p[3]),records.length,row);assertEquals(p[4],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(records)),row);
        assertEquals(17,snapshot.revision());
    }
    @Test void captureIsImmutableAndCompressionIsBounded() throws IOException {
        var map=VxlMap.load(raw("small_grid"),"auto");byte[] column=overlay();var mutable=new HashMap<MapSyncSnapshot.Column,byte[]>();mutable.put(new MapSyncSnapshot.Column(0,0),column);
        var snapshot=new MapSyncSnapshot(map,mutable,1,false);var expected=snapshot.buildChunks();column[0]=99;mutable.clear();
        assertEquals(1,snapshot.dirtyColumnCount());assertArrayEquals(expected.getFirst(),snapshot.buildChunks().getFirst());
        assertThrows(ProtocolException.class,()->snapshot.buildChunks(8,1024));assertThrows(ProtocolException.class,()->snapshot.buildChunks(1024,1));
        assertThrows(ProtocolException.class,()->new MapSyncSnapshot(map,Map.of(new MapSyncSnapshot.Column(0,0),new byte[]{1,1,1,0}),0,true));
        assertThrows(IllegalArgumentException.class,()->new MapSyncSnapshot.Column(512,0));
    }
    @Test void validationDelayBackpressureAndEmptyDeltaStillFinalize() {
        var transfer=new MapSyncTransfer(4,0,-1,MapSyncTransfer.Mode.AUTO,false,false);var frames=new ArrayList<MapSyncTransfer.Frame>();
        transfer.validation(-1,0);assertEquals(0,transfer.pump(0,10,f->false));assertEquals(MapSyncTransfer.State.REPLY,transfer.state());
        assertEquals(1,transfer.pump(10,10,f->{frames.add(f);return true;}));
        assertEquals(-1,((SimplePackets.MapDataValidation)frames.getFirst().packet()).crc());
        transfer.pump(109,10,f->{fail("No map data before delay");return false;});assertEquals(MapSyncTransfer.State.DELAY,transfer.state());
        transfer.pump(110,10,f->true);assertFalse(transfer.preparation().full());assertFalse(transfer.supplyChunks(3,List.of()));assertTrue(transfer.supplyChunks(4,List.of()));
        assertEquals(0,transfer.pump(110,2,f->false));
        assertEquals(1,transfer.pump(110,1,f->{frames.add(f);return true;}));assertEquals(55,frames.getLast().packet().id());assertEquals(0x32,frames.getLast().prefix());
        assertEquals(1,transfer.pump(111,1,f->{frames.add(f);return true;}));assertEquals(59,frames.getLast().packet().id());assertEquals(0x31,frames.getLast().prefix());
        assertEquals(MapSyncTransfer.State.COMPLETE,transfer.state());assertEquals(0,transfer.pump(112,10,f->true));
    }
    @Test void timeoutCrcRulesCancellationAndPercentRounding() {
        var required=new MapSyncTransfer(1,0,123,MapSyncTransfer.Mode.AUTO,true,false);assertEquals(0,required.pump(5000,100,f->true));assertEquals(MapSyncTransfer.State.TIMED_OUT,required.state());
        var ordinary=new MapSyncTransfer(2,0,123,MapSyncTransfer.Mode.AUTO,false,false);ordinary.pump(5000,1,f->true);ordinary.pump(5100,1,f->true);assertTrue(ordinary.preparation().full());
        for(int scenario=0;scenario<4;scenario++) {
            var transfer=new MapSyncTransfer(3,0,scenario==0?0:123,scenario==1?MapSyncTransfer.Mode.FULL:MapSyncTransfer.Mode.AUTO,false,scenario==2);
            transfer.validation(scenario==3?124:scenario==0?0:123,0);transfer.pump(0,1,f->true);transfer.pump(100,1,f->true);assertTrue(transfer.preparation().full());
        }
        assertTrue(ordinary.supplyChunks(2,java.util.Collections.nCopies(100,new byte[]{1,2,3})));
        var frames=new ArrayList<MapSyncTransfer.Frame>();ordinary.pump(5100,102,f->{frames.add(f);return true;});
        assertEquals(102,frames.size());assertEquals(29,((LoadingPackets.MapChunk)frames.get(30).packet()).percent());
        for(var frame:frames) {byte[] encoded=frame.encode();assertEquals(frame.prefix(),encoded[0]&255);assertArrayEquals(frame.packet().encode(),Lzf.decode(java.util.Arrays.copyOfRange(encoded,1,encoded.length),32768));}
        var stale=new MapSyncTransfer(9,0,1,MapSyncTransfer.Mode.FULL,false,false);stale.cancel();assertFalse(stale.supplyChunks(9,List.of()));assertEquals(0,stale.pump(100,99,f->true));
        assertThrows(IllegalArgumentException.class,()->ordinary.pump(5000,1,f->true));
        var late=new MapSyncTransfer(10,0,123,MapSyncTransfer.Mode.AUTO,true,false);late.validation(123,5000);assertEquals(MapSyncTransfer.State.TIMED_OUT,late.state());
    }
}
