// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class GoldenPacketTest {
    static List<String> lines(String name) throws IOException {
        try (var input = GoldenPacketTest.class.getResourceAsStream("/golden/" + name)) {
            assertNotNull(input);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }
    static Stream<String> packetVectors() throws IOException { return lines("packets.tsv").stream(); }
    @ParameterizedTest(name="{index}: source packet vector") @MethodSource("packetVectors")
    void sourcePacketBytesDecodeAndEncodeExactly(String row) {
        var parts = row.split("\t");
        byte[] expected = HexFormat.of().parseHex(parts[1]);
        GamePacket decoded = PacketCodec.decode(expected);
        assertEquals(parts[0].split("/")[0], decoded.getClass().getSimpleName());
        assertArrayEquals(expected, decoded.encode());
        byte[] trailing = Arrays.copyOf(expected, expected.length + 1);
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(trailing));
        for (int i = 0; i < expected.length; i++) {
            byte[] truncated = Arrays.copyOf(expected, i);
            assertThrows(ProtocolException.class, () -> PacketCodec.decode(truncated));
        }
    }
    static Stream<String> clientVectors() throws IOException { return lines("client-input.tsv").stream(); }
    @ParameterizedTest @MethodSource("clientVectors")
    void clientDataSourceEncoding(String row) {
        var p = row.split("\t");
        byte[] expected = HexFormat.of().parseHex(p[4]);
        var input = new ClientInput(12345, 3, true, 7, Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]),
            5, 0xb1, 0xc5, Double.parseDouble(p[3]));
        assertArrayEquals(expected, input.encode());
        var decoded = (ClientInput)PacketCodec.decode(expected);
        assertTrue(decoded.paletteEnabled()); assertEquals(3, decoded.playerId());
        assertTrue(decoded.primary()); assertTrue(decoded.zoom()); assertTrue(decoded.deployed()); assertTrue(decoded.hover());
    }
    @Test void unknownEmptyAndUnterminatedPacketsAreRejected() {
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(new byte[0]));
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(new byte[]{(byte)255}));
        assertThrows(ProtocolException.class, () -> PacketCodec.decode(new byte[]{49,1,0,65}));
    }
    @Test void signedMagnitudeHasSourceAsymmetricNegativeRounding() {
        assertEquals(0x803f, Fixed.encodePosition(-1));
        assertEquals(-63/64.0, Fixed.decodePosition(0x803f));
        assertEquals(0, Fixed.encodePosition(-0.01));
        assertEquals(0x7fff, Fixed.encodePosition(1000));
        assertEquals(0xffff, Fixed.encodePosition(-1000));
        assertEquals(0x5ff7, Fixed.encodeOrientation(7));
        assertEquals(0xdff7, Fixed.encodeOrientation(-7));
    }
    @Test void byteOrderAndUnsignedWidths() {
        var w = new Wire.Writer().u8(255).i16(-32768).i32(Integer.MIN_VALUE).u32(0xffff_ffffL).u64(-1L).f32(-0.0f).color(0x123456).string("café");
        var r = new Wire.Reader(w.toByteArray());
        assertEquals(255,r.u8()); assertEquals(-32768,r.i16()); assertEquals(Integer.MIN_VALUE,r.i32());
        assertEquals(0xffff_ffffL,r.u32()); assertEquals(-1L,r.u64());
        assertEquals(0x8000_0000,Float.floatToRawIntBits(r.f32())); assertEquals(0x123456,r.color()); assertEquals("café",r.string());r.end();
    }
}
