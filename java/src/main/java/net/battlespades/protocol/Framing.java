// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.Arrays;

/** server/connection.py framing. Ticket XOR is inbound-only and follows LZF. */
public final class Framing {
    private Framing() {}
    public static byte[] outbound(int prefix, byte[] body) {
        checkPrefix(prefix);
        if (body.length == 0) throw new ProtocolException("Empty packet");
        byte[] chunks = Lzf.encode(body);
        byte[] frame = new byte[chunks.length + 1];
        frame[0] = (byte) prefix;
        System.arraycopy(chunks, 0, frame, 1, chunks.length);
        return frame;
    }
    public static byte[] inbound(byte[] frame, byte[] ticket, int limit) {
        if (limit < 1) throw new IllegalArgumentException("Packet limit must be positive");
        if (frame.length < 2) throw new ProtocolException("Empty frame");
        int prefix = frame[0] & 255;
        checkPrefix(prefix);
        byte[] body;
        if (prefix == 0x31) body = Lzf.decode(Arrays.copyOfRange(frame, 1, frame.length), limit);
        else {
            if (frame.length - 1 > limit) throw new ProtocolException("Packet exceeds limit");
            body = Arrays.copyOfRange(frame, 1, frame.length);
        }
        if (body.length == 0) throw new ProtocolException("Empty decoded packet");
        if (ticket.length > 0) for (int i = 0; i < body.length; i++) body[i] ^= ticket[i % ticket.length];
        return body;
    }
    private static void checkPrefix(int prefix) {
        if (prefix < 0x30 || prefix > 0x32) throw new ProtocolException("Unknown frame prefix " + prefix);
    }
}
