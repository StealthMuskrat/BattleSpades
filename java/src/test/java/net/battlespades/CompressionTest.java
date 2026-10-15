// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class CompressionTest {
    static Stream<String> lzfVectors() throws IOException { return GoldenPacketTest.lines("lzf.tsv").stream(); }
    @ParameterizedTest @MethodSource("lzfVectors")
    void literalEncodingMatchesPython(String row) {
        var p = row.split("\t",-1);
        var original = HexFormat.of().parseHex(p[1]);
        var compressed = HexFormat.of().parseHex(p[2]);
        assertArrayEquals(compressed,Lzf.encode(original));
        assertArrayEquals(original,Lzf.decode(compressed,original.length));
        if (original.length>0) assertThrows(ProtocolException.class,()->Lzf.decode(compressed,original.length-1));
    }
    @Test void overlappingReferencesUseDistancePlusOne() {
        assertArrayEquals("abcabcabc".getBytes(java.nio.charset.StandardCharsets.US_ASCII), Lzf.decode(new byte[]{2,97,98,99,(byte)0x80,2},9));
        assertArrayEquals(new byte[]{65,65,65,65,65},Lzf.decode(new byte[]{0,65,0x40,0},5));
    }
    @Test void rejectsTruncatedInvalidAndExplosiveInput() {
        for(byte[] input:new byte[][]{{0},{31,1},{(byte)0xe0},{(byte)0xe0,1},{0x20,0},{0,65,(byte)0xe0,(byte)255,0}}) {
            assertThrows(ProtocolException.class,()->Lzf.decode(input,10));
        }
        assertThrows(IllegalArgumentException.class,()->Lzf.decode(new byte[0],-1));
    }
    @Test void deterministicMalformedInputFuzzIsBounded() {
        var random = new Random(168);
        for(int i=0;i<10_000;i++) {
            byte[] input=new byte[random.nextInt(128)];random.nextBytes(input);
            try { assertTrue(Lzf.decode(input,4096).length<=4096); } catch(ProtocolException expected) { /* invalid peer input */ }
        }
    }
    @Test void sourceFramingIsAsymmetricAndXorFollowsDecompression() {
        byte[] packet = {0,1,2,3};
        assertArrayEquals(new byte[]{0x30,3,0,1,2,3},Framing.outbound(0x30,packet));
        assertArrayEquals(packet,Framing.inbound(Framing.outbound(0x31,packet),new byte[0],4));
        assertArrayEquals(new byte[]{3,0,1,2,3},Framing.inbound(Framing.outbound(0x30,packet),new byte[0],5));
        assertArrayEquals(new byte[]{1,3,3,1},Framing.inbound(Framing.outbound(0x31,packet),new byte[]{1,2},4));
        assertThrows(ProtocolException.class,()->Framing.inbound(new byte[]{0x31,0},new byte[0],10));
    }
}
