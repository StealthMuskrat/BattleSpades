// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Optional;
import net.battlespades.protocol.ProtocolException;

/** Portable, nonblocking datagrams. Single network-owner thread; no authoritative state. */
public final class UdpEndpoint implements AutoCloseable {
    private final DatagramChannel channel;
    private final int limit;
    private final ByteBuffer input;
    public record Datagram(SocketAddress source, byte[] payload) {
        public Datagram { payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    public UdpEndpoint(InetSocketAddress bind, int limit) throws IOException {
        if (limit < 1 || limit > 65507) throw new IllegalArgumentException("Invalid UDP limit");
        this.limit = limit;
        input = ByteBuffer.allocate(limit + 1);
        channel = DatagramChannel.open();
        try { channel.configureBlocking(false); channel.bind(bind); }
        catch (IOException | RuntimeException exception) { channel.close(); throw exception; }
    }
    public SocketAddress address() throws IOException { return channel.getLocalAddress(); }
    public Optional<Datagram> receive() throws IOException {
        input.clear();
        SocketAddress source = channel.receive(input);
        if (source == null) return Optional.empty();
        if (input.position() > limit) throw new ProtocolException("Oversized UDP datagram");
        input.flip(); byte[] payload = new byte[input.remaining()]; input.get(payload);
        return Optional.of(new Datagram(source, payload));
    }
    /** false means socket backpressure; retain the message in a bounded owner queue. */
    public boolean send(SocketAddress destination, byte[] payload) throws IOException {
        if (payload.length > limit) throw new ProtocolException("Oversized outbound UDP datagram");
        int sent = channel.send(ByteBuffer.wrap(payload), destination);
        return sent == payload.length;
    }
    @Override public void close() throws IOException { channel.close(); }
}
