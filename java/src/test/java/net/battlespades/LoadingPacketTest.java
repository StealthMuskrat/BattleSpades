// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class LoadingPacketTest {
    private InitialInfo info() {
        return new InitialInfo(new InitialInfo.Identity(-1,-1062731519,32887,65535),
                new InitialInfo.Mode("CTF","café 🂡","one","","three",5),new InitialInfo.MapInfo("island","island.vxl",-1,1),
                new InitialInfo.Features(0,1,0,255,1,1,0,1,1,0,1,"default",1,1,0),1.25f,-1,
                List.of(0,128,255),List.of(1,5),List.of(1.0,1.25,-0.5,0.0078124),List.of(0,255),1,"Source oracle",
                List.of(new InitialInfo.Rgba(18,52,86,255),new InitialInfo.Rgba(0,255,128,64)),
                List.of(new InitialInfo.Loadout(255,128,List.of(0,127,255)),new InitialInfo.Loadout(1,2,List.of())),1,1,
                List.of(new InitialInfo.Rule("key","value"),new InitialInfo.Rule("café","🂡")),1,7);
    }
    private StateData state(boolean catalog) {
        var zero=new StateData.Vector(0,0,0);
        if(!catalog) {
            var team=new StateData.Team("",0,0,false,false,false,false,false,false,false,List.of());
            return new StateData(0,0,0,new StateData.Lighting(0,zero,0,zero,0,0),1,0,0,0,team,team,false,false,List.of(),List.of(),List.of(),List.of(),0);
        }
        var entity=new EntityRecord(-25536,38,2,3,new Vector3f(10.25f,20.5f,200.75f),new Vector3f(-1,0.5f,0),-2,new Vector3f(18,52,86),1.5f,4,3.25f,1,List.of(-1,Integer.MAX_VALUE),List.of(-1.0,0.5,2.0));
        return new StateData(255,0x123456,-9.81f,
                new StateData.Lighting(0xabcdef,new StateData.Vector(1.25,-2.5,3.75),0x010203,new StateData.Vector(-1,0.125,511),0x040506,0.75f),1.25f,255,7,2,
                new StateData.Team("Blue",0x0040ff,-123,true,false,true,false,true,false,true,List.of(0,128,255)),
                new StateData.Team("café",0xff2000,Integer.MAX_VALUE,false,true,false,true,false,true,false,List.of()),true,true,
                IntStream.range(0,373).mapToObj(i->"prefab/"+i).toList(),List.of(entity),
                List.of(new StateData.Vector(1.25,2.5,230.75),new StateData.Vector(-1,0.0078124,511)),List.of(new StateData.Vector(-0.25,0.5,0)),1);
    }
    static Stream<String> vectors() throws IOException { return GoldenPacketTest.lines("loading.tsv").stream(); }
    @ParameterizedTest @MethodSource("vectors")
    void sourceLoadingBytesAndBounds(String row) {
        String[] p=row.split("\t");byte[] expected=HexFormat.of().parseHex(p[1]);
        GamePacket packet=switch(p[0].split("/")[0]) {
            case "InitialInfo" -> info();
            case "StateDataEmpty" -> state(false);
            case "StateDataCatalog" -> state(true);
            case "MapSyncStart" -> new LoadingPackets.MapSyncStart();
            case "SteamSessionTicket" -> new LoadingPackets.SteamSessionTicket(bytes(256,1));
            case "MapDataChunk","MapSyncChunk" -> new LoadingPackets.MapChunk(p[0].startsWith("MapData")?56:57,73,bytes(Integer.parseInt(p[0].split("/")[1]),17));
            default -> throw new AssertionError(p[0]);
        };
        assertArrayEquals(expected,packet.encode(),p[0]);var decoded=PacketCodec.decode(expected);assertEquals(packet.id(),decoded.id());
        // Fixed-point negatives have the source's asymmetric rounding, so decode/encode is not an identity.
        if(decoded instanceof InitialInfo i) {
            assertEquals(65535,i.identity().queryPort());assertEquals(-1L,i.identity().steamId());assertEquals(-63/64f,i.blockHealthMultiplier());
            assertEquals(255,i.loadouts().getFirst().classId());assertEquals(128,i.loadouts().getFirst().slotId());
            assertEquals(new InitialInfo.Rgba(18,52,86,255),i.groundColors().getFirst());assertEquals("🂡",i.customRules().getLast().value());
        } else if(decoded instanceof StateData s && p[0].equals("StateDataCatalog")) {
            assertEquals(373,s.prefabs().size());assertEquals("prefab/372",s.prefabs().getLast());assertEquals(-25536,s.entities().getFirst().entityId());
            assertEquals(new StateData.Vector(1.25,-159/64.0,3.75),s.lighting().direction());assertTrue(s.team1().lockedScore());assertFalse(s.team2().lockedScore());
        }
        var cuts=expected.length<5000?IntStream.range(0,expected.length):IntStream.of(0,1,2,3,expected.length/2,expected.length-1);
        cuts.forEach(n->assertThrows(ProtocolException.class,()->PacketCodec.decode(Arrays.copyOf(expected,n)),p[0]+" at "+n));
        assertThrows(ProtocolException.class,()->PacketCodec.decode(Arrays.copyOf(expected,expected.length+1)));
    }
    private byte[] bytes(int count,int multiplier) { byte[] data=new byte[count];for(int i=0;i<count;i++) data[i]=(byte)(i*multiplier);return data; }
    @Test void immutableCollectionsAndSignedCountValidation() {
        var tools=new ArrayList<>(List.of(1));var loadout=new InitialInfo.Loadout(1,2,tools);tools.set(0,9);assertEquals(List.of(1),loadout.tools());
        assertThrows(UnsupportedOperationException.class,()->loadout.tools().add(3));
        assertThrows(IllegalArgumentException.class,()->new InitialInfo.Loadout(1,2,java.util.Collections.nCopies(256,0)));
        byte[] wire=state(false).encode();
        // Both counts directly precede empty points/rotation/map-ended bytes.
        int prefabs=wire.length-7;wire[prefabs]=(byte)255;wire[prefabs+1]=(byte)255;
        assertThrows(ProtocolException.class,()->PacketCodec.decode(wire));
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{57,0,-1,-1}));
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{105,-1,-1,-1,127}));
        assertThrows(IllegalArgumentException.class,()->new LoadingPackets.MapChunk(57,1,new byte[32768]));
        byte[] payload={1,2,3};var chunk=new LoadingPackets.MapChunk(57,1,payload);payload[0]=9;chunk.data()[1]=9;assertArrayEquals(new byte[]{1,2,3},chunk.data());
    }
}
