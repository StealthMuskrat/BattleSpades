// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.world.Kv6;
import net.battlespades.world.VxlMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldFormatTest {
    private byte[] resource(String name) throws IOException {
        try(var in=getClass().getResourceAsStream("/golden/world/"+name)) { assertNotNull(in);return in.readAllBytes(); }
    }
    @Test void vxlMatchesOriginalSolidityColorsAndMapSyncColumns() throws IOException {
        Map<String,VxlMap> maps=new HashMap<>();
        for(String row:new String(resource("points.tsv"),StandardCharsets.UTF_8).lines().toList()) {
            var p=row.split("\t");VxlMap map=maps.get(p[0]);
            if(map==null) { map=VxlMap.load(resource(p[0]+".vxl"),"auto");maps.put(p[0],map); }
            int x=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]),z=Integer.parseInt(p[3]);
            assertEquals(p[4].equals("1"),map.solid(x,y,z),row);
            assertEquals((int)Long.parseLong(p[5]),map.color(x,y,z),row);
        }
        for(var entry:maps.entrySet()) {
            for(String row:new String(resource(entry.getKey()+".columns.tsv"),StandardCharsets.UTF_8).lines().toList()) {
                var p=row.split("\t");
                assertArrayEquals(HexFormat.of().parseHex(p[2]),entry.getValue().serializeColumn(Integer.parseInt(p[0]),Integer.parseInt(p[1])),entry.getKey()+" "+row);
            }
            assertArrayEquals(resource(entry.getKey()+".vxl"),entry.getValue().originalBytes());
        }
        assertTrue(maps.get("classic_water").classic());assertEquals(176,maps.get("classic_water").zShift());
    }
    @Test void vxlMalformedSpansRejectedBeforeWorldAllocation() {
        for(byte[] input:List.of(new byte[0],new byte[]{0},new byte[]{0,(byte)240,(byte)240,0},
                new byte[]{0,1,1,0},new byte[]{2,1,1,0,0,0,0,0},new byte[]{1,1,1,0,0,0,0,0})) {
            assertThrows(ProtocolException.class,()->VxlMap.load(input,"auto"));
        }
        assertThrows(IllegalArgumentException.class,()->VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"unknown"));
    }
    @Test void kv6StandardAndLegacyRoundTripAndColorOrder() throws IOException {
        for(String name:List.of("standard","legacy")) {
            byte[] original=resource(name+".kv6");var model=Kv6.decode(original);
            assertEquals(2,model.xSize());assertEquals(2,model.ySize());assertEquals(8,model.zSize());
            assertEquals(name.equals("legacy"),model.legacy());assertEquals(2,model.voxels().size());
            var first=model.voxels().getFirst();assertEquals(0x12,first.r());assertEquals(0x34,first.g());assertEquals(0x56,first.b());
            assertEquals(3,first.z());assertEquals(-2f,model.pivotY());assertArrayEquals(original,model.encode());
            for(int size=0;size<28;size++) {
                byte[] shortInput=Arrays.copyOf(original,size);assertThrows(ProtocolException.class,()->Kv6.decode(shortInput));
            }
        }
    }
    @Test void kv6OverflowDimensionsCannotAllocateUnboundedTables() {
        byte[] malicious=new byte[32];malicious[0]='K';malicious[1]='v';malicious[2]='x';malicious[3]='l';
        Arrays.fill(malicious,4,12,(byte)255);
        assertThrows(ProtocolException.class,()->Kv6.decode(malicious));
    }
}
