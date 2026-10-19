// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SessionProtocolTest {
    @Test void helpDelayUsesBigEndianAndRetainsTheStringListBoundary(){
        var packet=new HelpMessage(1.5f,List.of("DIG","CASH"));byte[] bytes={109,0x3f,(byte)0xc0,0,0,2,'D','I','G',0,'C','A','S','H',0};assertArrayEquals(bytes,packet.encode());assertEquals(packet,PacketCodec.decode(bytes));assertThrows(ProtocolException.class,()->PacketCodec.decode(Arrays.copyOf(bytes,bytes.length-1)));assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{109,0,0,0,0,1}));
    }
    @Test void territoryStateUsesRetailShortActionsAndFixedPercentageWithoutTrailingBytes(){
        for(int action:new int[]{3,4,6,7}){byte[] bytes={(byte)106,2,(byte)action};assertArrayEquals(bytes,new TerritoryBaseState(2,action,3,2,75).encode());assertEquals(new TerritoryBaseState(2,action,0,0,.5),PacketCodec.decode(bytes));}
        byte[] bytes={(byte)106,2,5,3,2,(byte)0xc0,0x12};assertArrayEquals(bytes,new TerritoryBaseState(2,5,3,2,75).encode());assertEquals(new TerritoryBaseState(2,5,3,2,75),PacketCodec.decode(bytes));
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{106,2,3,0}));assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{106,2,5,3,2,0}));
    }
    static Stream<String> packets() throws IOException {return GoldenPacketTest.lines("session.tsv").stream().filter(s->!s.startsWith("Flight/"));}
    @ParameterizedTest @MethodSource("packets") void sourceJoinAndActualRetailHealthPackets(String row) {
        var p=row.split("\t");byte[] expected=HexFormat.of().parseHex(p[1]);
        GamePacket packet=switch(p[0]) {
            case "CreatePlayer" -> new PlayerPackets.CreatePlayer(127,0,5,2,0,1,new Vector3f(255.5f,256.25f,237.5f),new Vector3f(-1,0.5f,0),"café 🂡",List.of(0,128,255),List.of("one","café"));
            case "ExistingPlayer" -> new PlayerPackets.ExistingPlayer(3,0,1,4,7,255,1,-123,2,1,0x123456,"Source",List.of(1,255),List.of("one"));
            case "SetClassLoadout" -> new PlayerPackets.SetClassLoadout(255,5,1,List.of(0,128,255),List.of("one","café"),List.of(255,0));
            case "BlockManagerState" -> new BlockManagerState(List.of(new BlockManagerState.User(1,2,3,9),new BlockManagerState.User(511,511,238,1.00001),new BlockManagerState.User(0,0,0,100)),List.of(new BlockManagerState.Damaged(4,5,6,2.125,0x123456)));
            default -> throw new AssertionError();
        };
        assertArrayEquals(expected,packet.encode());assertEquals(packet.id(),PacketCodec.decode(expected).id());
        for(int n=0;n<expected.length;n++) {byte[] shortBody=Arrays.copyOf(expected,n);assertThrows(ProtocolException.class,()->PacketCodec.decode(shortBody));}
    }
    static Stream<String> inputs() throws IOException {return GoldenPacketTest.lines("live-input.tsv").stream();}
    @ParameterizedTest @MethodSource("inputs") void originalRuntimeClientDataVariants(String row) {
        var p=row.split("\t");var values=p[1].substring(1,p[1].length()-1).split(",");var input=LiveClientInput.decode(HexFormat.of().parseHex(p[0]));
        assertEquals(Integer.parseInt(values[0].trim()),input.playerId());assertEquals(Boolean.parseBoolean(values[1].trim()),input.paletteEnabled());
        assertEquals(Double.parseDouble(values[2]),input.x());assertEquals(Double.parseDouble(values[3]),input.y());assertEquals(Double.parseDouble(values[4]),input.z());assertEquals(Double.parseDouble(values[5]),input.deploymentYaw());
        assertEquals(0xb1,input.movementFlags());assertEquals(0xc5,input.actionFlags());
    }
    @Test void capabilityTrailerIsOutsideTicketAndUnknownTrailersStayUnnegotiated() {
        byte[] key={1,2,3};
        for(int cap=1;cap<=4;cap++) {
            var ticket=new LoadingPackets.SteamSessionTicket(key,new byte[]{'B','S','C','F',(byte)cap});var parsed=(LoadingPackets.SteamSessionTicket)PacketCodec.decode(ticket.encode());
            assertArrayEquals(key,parsed.data());assertEquals(cap,parsed.capability());
        }
        var unknown=(LoadingPackets.SteamSessionTicket)PacketCodec.decode(new LoadingPackets.SteamSessionTicket(key,new byte[]{'B','S','C','F',5}).encode());assertEquals(0,unknown.capability());
        assertEquals(0,new LoadingPackets.SteamSessionTicket(new byte[2049],new byte[]{'B','S','C','F',4}).capability());
    }
    @Test void optionalLoadoutTailAndMalformedCounts() {
        byte[] empty={13,0,5,0,0,0};var parsed=PlayerPackets.SetClassLoadout.receive(empty);assertTrue(parsed.ugcTools().isEmpty());
        assertThrows(ProtocolException.class,()->PacketCodec.decode(empty));
        assertThrows(ProtocolException.class,()->PlayerPackets.SetClassLoadout.receive(new byte[]{13,0,5,0,65}));
        assertThrows(ProtocolException.class,()->PlayerPackets.SetClassLoadout.receive(new byte[]{13,0,5,0,0,0,0,1}));
        assertThrows(ProtocolException.class,()->PacketCodec.decode(new byte[]{38,-1,-1,-1,127}));
        assertThrows(ProtocolException.class,()->LiveClientInput.decode(new byte[15]));
    }
}
