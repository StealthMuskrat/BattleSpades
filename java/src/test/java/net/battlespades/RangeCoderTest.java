// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Random;
import java.util.stream.Stream;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.transport.enet.RangeCoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class RangeCoderTest {
    static Stream<String> vectors() throws IOException {return GoldenPacketTest.lines("range.tsv").stream();}
    @ParameterizedTest @MethodSource("vectors")
    void originalNativeEnetBytes(String row) {
        var p=row.split("\t");byte[] source=HexFormat.of().parseHex(p[1]),compressed=HexFormat.of().parseHex(p[2]);
        assertArrayEquals(compressed,RangeCoder.encode(source),p[0]);
        assertArrayEquals(source,RangeCoder.decode(compressed,source.length),p[0]);
        assertThrows(ProtocolException.class,()->RangeCoder.decode(compressed,source.length-1));
    }
    @Test void boundedMalformedInputFuzz() {
        var random=new Random(168);
        for(int i=0;i<1000;i++) {
            byte[] input=new byte[1+random.nextInt(64)];random.nextBytes(input);
            try {assertTrue(RangeCoder.decode(input,4096).length<=4096);}catch(ProtocolException expected) {/* bounded rejection */}
        }
    }
}
