// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Source-equivalent literal encoder and full, bounded overlapping LZF decoder. */
public final class Lzf {
    private Lzf() {}
    public static byte[] encode(byte[] input) {
        var out = new ByteArrayOutputStream();
        for (int offset = 0; offset < input.length; offset += 32) {
            int size = Math.min(32, input.length - offset);
            out.write(size - 1); out.write(input, offset, size);
        }
        return out.toByteArray();
    }
    public static byte[] decode(byte[] input, int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative output limit");
        byte[] output = new byte[Math.min(limit, Math.max(32, input.length))];
        int offset = 0, size = 0;
        while (offset < input.length) {
            int control = input[offset++] & 255;
            int length = control >>> 5;
            int distance = control & 31;
            boolean literal = length == 0;
            if (literal) {
                length = distance + 1;
                if (length > input.length - offset) throw new ProtocolException("Truncated LZF literal");
            } else {
                if (length == 7) {
                    if (offset == input.length) throw new ProtocolException("Truncated LZF length");
                    length += input[offset++] & 255;
                }
                if (offset == input.length) throw new ProtocolException("Truncated LZF reference");
                distance = (distance << 8) + (input[offset++] & 255) + 1;
                if (distance > size) throw new ProtocolException("LZF reference before output");
                length += 2;
            }
            if (length > limit - size) throw new ProtocolException("LZF output exceeds limit");
            int needed = size + length;
            if (needed > output.length) output = Arrays.copyOf(output, (int)Math.min(limit, Math.max((long)needed, output.length * 2L)));
            if (literal) {
                System.arraycopy(input, offset, output, size, length);
                size += length; offset += length;
            } else {
                for (int i = 0; i < length; i++) { output[size] = output[size - distance]; size++; }
            }
        }
        return Arrays.copyOf(output, size);
    }
}
