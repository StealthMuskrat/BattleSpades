// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.transport.enet.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnetTest {
    @Test void networkEndianReliableCommand() {
        var datagram=new EnetDatagram(0x123,2,0xabcd,List.of(EnetDatagram.reliable(0,0x1234,new byte[]{0x31,0,55})));
        byte[] expected=HexFormat.of().parseHex("a123abcd860012340003310037");
        assertArrayEquals(expected,datagram.encode());
        assertArrayEquals(expected,EnetDatagram.decode(expected).encode());
        assertEquals(0x123,EnetDatagram.decode(expected).peerId());
        for(int n=0;n<expected.length;n++) {
            byte[] truncated=Arrays.copyOf(expected,n);
            assertThrows(ProtocolException.class,()->EnetDatagram.decode(truncated));
        }
    }
    @Test void ackFormatAndDefensiveCopies() {
        var ack=EnetDatagram.acknowledge(0,12,65535,0x1234);
        assertArrayEquals(HexFormat.of().parseHex("0100000cffff1234"),new EnetDatagram(1,0,-1,List.of(ack)).encode().length==10 ?
            Arrays.copyOfRange(new EnetDatagram(1,0,-1,List.of(ack)).encode(),2,10):new byte[0]);
        var reliable=EnetDatagram.reliable(0,1,new byte[]{42});
        reliable.payload()[0]=0;assertEquals(42,reliable.payload()[0]);
    }
    @Test void invalidCompressionCannotSilentlyBecomeOrdinaryUdp() {
        assertThrows(ProtocolException.class,()->EnetDatagram.decode(new byte[]{0x40,1,0,0}));
    }
    @Test void compressedEnvelopeRestoresCommandStream() {
        byte[] data=new byte[1000];Arrays.fill(data,(byte)42);
        var packet=new EnetDatagram(1,2,123,List.of(EnetDatagram.reliable(0,1,data)));
        byte[] compressed=packet.encodeCompressed();assertTrue(compressed.length<packet.encode().length);
        assertEquals(0x40,compressed[0]&0x40);assertArrayEquals(packet.encode(),EnetDatagram.decode(compressed).encode());
    }
    @Test void modularSequenceWrap() {
        assertEquals(0,Sequence.next(65535)); assertTrue(Sequence.after(0,65535));
        assertFalse(Sequence.after(65535,0));assertFalse(Sequence.after(1,1));assertFalse(Sequence.after(32768,0));
    }
    @Test void fragmentsReorderDeduplicateAndRejectOverlap() {
        var assembly=new FragmentAssembly(6,3,64,16);
        assertTrue(assembly.accept(2,4,new byte[]{5,6}).isEmpty());
        assertTrue(assembly.accept(0,0,new byte[]{1,2}).isEmpty());
        assertTrue(assembly.accept(0,0,new byte[]{1,2}).isEmpty());
        assertThrows(ProtocolException.class,()->assembly.accept(0,0,new byte[]{7,8}));
        assertThrows(ProtocolException.class,()->assembly.accept(1,1,new byte[]{3,4}));
        assertArrayEquals(new byte[]{1,2,3,4,5,6},assembly.accept(1,2,new byte[]{3,4}).orElseThrow());
    }
    @Test void fragmentBoundsBeforeAllocation() {
        assertThrows(ProtocolException.class,()->new FragmentAssembly(Integer.MAX_VALUE,3,1024,64));
        assertThrows(ProtocolException.class,()->new FragmentAssembly(10,11,1024,64));
        var gap=new FragmentAssembly(4,2,16,16);gap.accept(0,0,new byte[]{1});
        assertThrows(ProtocolException.class,()->gap.accept(1,3,new byte[]{1}));
    }
}
