// SPDX-License-Identifier: AGPL-3.0-or-later
// Layout derived from ENet protocol.h (MIT, Lee Salzman); see notices.
package net.battlespades.transport.enet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.battlespades.protocol.ProtocolException;

/** ENet 1.3 wire envelope. Session/reliability policy belongs to a separate peer engine. */
public record EnetDatagram(int peerId, int sessionId, int sentTime, List<Command> commands) {
    private static final int[] SIZES = {0, 8, 48, 44, 8, 4, 6, 8, 24, 8, 12, 16, 24};
    public static final int MAX_MTU = 4096;
    public EnetDatagram {
        if (peerId < 0 || peerId > 4095 || sessionId < 0 || sessionId > 3 || sentTime < -1 || sentTime > 65535) {
            throw new IllegalArgumentException("Invalid ENet header");
        }
        commands = List.copyOf(commands);
        if (commands.isEmpty() || commands.size() > 32) throw new ProtocolException("Invalid ENet command count");
    }
    public record Command(int flags, int channel, int reliableSequence, byte[] parameters, byte[] payload) {
        public Command {
            int id = flags & 15;
            if (id < 1 || id >= SIZES.length || (flags & 0x30) != 0 || flags < 0 || flags > 255) throw new ProtocolException("Invalid ENet command");
            if (channel < 0 || channel > 255 || reliableSequence < 0 || reliableSequence > 65535) throw new ProtocolException("Invalid ENet command header");
            parameters = parameters.clone(); payload = payload.clone();
            if (parameters.length != SIZES[id] - 4) throw new ProtocolException("Invalid ENet parameter length");
            int lengthOffset = switch(id) { case 6 -> 0; case 7, 8, 9, 12 -> 2; default -> -1; };
            int expected = lengthOffset < 0 ? 0 : ((parameters[lengthOffset] & 255) << 8) | (parameters[lengthOffset + 1] & 255);
            if (payload.length != expected) throw new ProtocolException("ENet payload length mismatch");
        }
        public int id() { return flags & 15; }
        @Override public byte[] parameters() { return parameters.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    public byte[] encode() {
        int size = sentTime < 0 ? 2 : 4;
        for (Command command : commands) size += SIZES[command.id()] + command.payload.length;
        if (size > MAX_MTU) throw new ProtocolException("ENet datagram exceeds MTU limit");
        var b = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        b.putShort((short)(peerId | sessionId << 12 | (sentTime < 0 ? 0 : 0x8000)));
        if (sentTime >= 0) b.putShort((short) sentTime);
        for (Command c : commands) b.put((byte)c.flags).put((byte)c.channel).putShort((short)c.reliableSequence).put(c.parameters).put(c.payload);
        return b.array();
    }
    /** Compress only the command region, and only when it saves bytes, as ENet does. */
    public byte[] encodeCompressed() {
        byte[] raw=encode();int headerLength=sentTime<0?2:4;
        byte[] coded=RangeCoder.encode(Arrays.copyOfRange(raw,headerLength,raw.length));
        if(coded.length>=raw.length-headerLength) return raw;
        byte[] result=Arrays.copyOf(raw,headerLength+coded.length);result[0]|=0x40;
        System.arraycopy(coded,0,result,headerLength,coded.length);return result;
    }
    public static EnetDatagram decode(byte[] data) {
        if (data.length < 2 || data.length > MAX_MTU) throw new ProtocolException("Invalid ENet datagram size");
        var b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int header = Short.toUnsignedInt(b.getShort());
        if ((header & 0x4000) != 0) {
            int headerLength=(header & 0x8000)==0?2:4;
            if(data.length<=headerLength) throw new ProtocolException("Empty compressed ENet envelope");
            byte[] commands=RangeCoder.decode(Arrays.copyOfRange(data,headerLength,data.length),MAX_MTU-headerLength);
            byte[] decoded=Arrays.copyOf(data,headerLength+commands.length);decoded[0]&=(byte)~0x40;
            System.arraycopy(commands,0,decoded,headerLength,commands.length);return decode(decoded);
        }
        int sent = -1;
        if ((header & 0x8000) != 0) {
            if (b.remaining() < 2) throw new ProtocolException("Truncated ENet sent time");
            sent = Short.toUnsignedInt(b.getShort());
        }
        List<Command> commands = new ArrayList<>();
        while (b.hasRemaining()) {
            if (commands.size() == 32 || b.remaining() < 4) throw new ProtocolException("Truncated/excess ENet commands");
            int flags = Byte.toUnsignedInt(b.get());
            int id = flags & 15;
            if (id < 1 || id >= SIZES.length) throw new ProtocolException("Unknown ENet command " + id);
            int channel = Byte.toUnsignedInt(b.get());
            int sequence = Short.toUnsignedInt(b.getShort());
            int count = SIZES[id] - 4;
            if (b.remaining() < count) throw new ProtocolException("Truncated ENet command parameters");
            byte[] parameters = new byte[count]; b.get(parameters);
            int offset = switch(id) { case 6 -> 0; case 7, 8, 9, 12 -> 2; default -> -1; };
            int payloadSize = offset < 0 ? 0 : ((parameters[offset] & 255) << 8) | (parameters[offset + 1] & 255);
            if (payloadSize > b.remaining()) throw new ProtocolException("Truncated ENet command data");
            byte[] payload = new byte[payloadSize]; b.get(payload);
            commands.add(new Command(flags, channel, sequence, parameters, payload));
        }
        return new EnetDatagram(header & 4095, (header >>> 12) & 3, sent, commands);
    }
    public static Command reliable(int channel, int sequence, byte[] payload) {
        if (payload.length > 65535) throw new ProtocolException("ENet payload too large");
        return new Command(0x86, channel, sequence, new byte[]{(byte)(payload.length >>> 8), (byte)payload.length}, payload);
    }
    public static Command acknowledge(int channel, int outgoingSequence, int receivedSequence, int sentTime) {
        var b = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putShort((short)receivedSequence).putShort((short)sentTime);
        return new Command(1, channel, outgoingSequence, b.array(), new byte[0]);
    }
}
