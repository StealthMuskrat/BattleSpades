// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.transport.UdpEndpoint;

/** Nonblocking ENet server host, explicitly owned and serviced by one network thread. */
public final class EnetHost implements AutoCloseable {
    public record Metrics(long receivedDatagrams,long malformedDatagrams,long refusedConnections,
            long socketBackpressure,int queuedEvents,long queuedEventBytes,int activePeers) {}
    private final Thread owner=Thread.currentThread();
    private final UdpEndpoint udp;
    private final EnetSettings limits;
    private final EnetPeer[] peers;
    private final int[] incomingSessions,outgoingSessions;
    private final ArrayDeque<EnetEvent> events=new ArrayDeque<>();
    private long generation,queuedBytes,received,malformed,refused,socketBackpressure;
    /** null passes through to ENet; an empty reply consumes a recognized query without responding. */
    @FunctionalInterface public interface Connectionless {byte[] reply(byte[] request);}
    private Connectionless connectionless;
    public AutoCloseable connectionless(Connectionless handler){
        owned();if(connectionless!=null)throw new IllegalStateException("Connectionless handler already bound");
        connectionless=java.util.Objects.requireNonNull(handler);
        return ()->{if(Thread.currentThread()!=owner)throw new IllegalStateException("Connectionless lease outside owner");if(connectionless==handler)connectionless=null;};
    }
    private int sendCursor;
    private boolean closed;
    private final long originNanos=System.nanoTime();
    private long lastNow=-1;
    public EnetHost(InetSocketAddress bind,EnetSettings limits) throws IOException {
        this.limits=limits;peers=new EnetPeer[limits.peerCount()];
        incomingSessions=new int[peers.length];outgoingSessions=new int[peers.length];
        Arrays.fill(incomingSessions,255);Arrays.fill(outgoingSessions,255);
        udp=new UdpEndpoint(bind,4096,65507);
    }
    private void owned() {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("ENet host accessed outside network owner");
        if(closed) throw new IllegalStateException("ENet host is closed");
    }
    public SocketAddress address() throws IOException {owned();return udp.address();}
    public Metrics metrics() {
        owned();int active=0;for(var peer:peers) if(peer!=null) active++;
        return new Metrics(received,malformed,refused,socketBackpressure,events.size(),queuedBytes,active);
    }
    private boolean offer(EnetEvent event) {
        if(events.size()==limits.eventCapacity() || event.dataLength()>limits.eventBytes()-queuedBytes) return false;
        events.addLast(event);queuedBytes+=event.dataLength();return true;
    }
    public Optional<EnetEvent> poll() {
        owned();EnetEvent event=events.pollFirst();if(event==null) return Optional.empty();
        queuedBytes-=event.dataLength();return Optional.of(event);
    }
    public void service() throws IOException { service((System.nanoTime()-originNanos)/1_000_000); }
    /** Bounded receive, timer, dispatch and send work. nowMillis must be nonnegative and monotonic. */
    public void service(long nowMillis) throws IOException {
        owned();
        if(nowMillis<0 || nowMillis<lastNow) throw new IllegalArgumentException("ENet clock moved backwards");
        lastNow=nowMillis;
        for(int n=0;n<limits.receiveBudget();n++) {
            Optional<UdpEndpoint.Datagram> incoming;
            try {incoming=udp.receive();} catch(ProtocolException invalid) {malformed++;continue;}
            if(incoming.isEmpty()) break;
            received++;var d=incoming.orElseThrow();
            if(connectionless!=null){byte[] reply=connectionless.reply(d.payload());if(reply!=null){if(reply.length>65507){malformed++;continue;}if(reply.length>0)send(d.source(),reply);continue;}}
            try {receive(d.source(),EnetDatagram.decode(d.payload()),nowMillis);}
            catch(ProtocolException invalid) {malformed++;}
        }
        for(int i=0;i<peers.length;i++) {
            EnetPeer peer=peers[i];if(peer==null) continue;
            peer.maintain(nowMillis);
            if(peer.finished()) peers[i]=null;
        }
        flush(nowMillis);
    }
    /** Bounded outgoing work only; does not receive, dispatch or wait for acknowledgements. */
    public void flush(long nowMillis)throws IOException {
        owned();if(nowMillis<0||nowMillis<lastNow)throw new IllegalArgumentException("ENet clock moved backwards");lastNow=nowMillis;
        int remaining=limits.sendBudget(),visited=0;
        while(remaining>0 && visited<peers.length) {
            int i=sendCursor++%peers.length;visited++;EnetPeer peer=peers[i];if(peer==null) continue;
            remaining-=peer.flush(nowMillis,Math.min(4,remaining),this::send);
        }
        // Avoid integer overflow while preserving round-robin fairness.
        sendCursor%=peers.length;
    }
    private boolean send(SocketAddress address,byte[] bytes) throws IOException {
        boolean sent=udp.send(address,bytes);if(!sent) socketBackpressure++;return sent;
    }
    private void receive(SocketAddress source,EnetDatagram packet,long now) throws IOException {
        if(packet.peerId()!=4095) {
            if(packet.peerId()>=peers.length) return;
            EnetPeer peer=peers[packet.peerId()];
            if(peer!=null && peer.address().equals(source)) peer.receive(packet,now);
            return;
        }
        for(var command:packet.commands()) {
            if(command.id()!=2 || packet.sentTime()<0) return;
            int connectId=ByteBuffer.wrap(command.parameters()).getInt(36);
            for(var peer:peers) if(peer!=null && peer.address().equals(source) && peer.connectId()==connectId) return;
            int free=-1;
            for(int i=0;i<peers.length;i++) if(peers[i]==null) {free=i;break;}
            var h=ConnectionHandshake.accept(command,free<0?0:free,free<0?255:incomingSessions[free],free<0?255:outgoingSessions[free]);
            int error=limits.requireProtocol168()?h.protocolError():0;
            if(free<0 || error!=0) {
                refused++;int reason=error!=0?error:4;
                var reject=new EnetDatagram.Command(0x44,255,1,ByteBuffer.allocate(4).putInt(reason).array(),new byte[0]);
                send(source,new EnetDatagram(h.remotePeerId(),h.outgoingSessionId(),(int)(now&65535),List.of(reject)).encode());return;
            }
            incomingSessions[free]=h.incomingSessionId();outgoingSessions[free]=h.outgoingSessionId();
            peers[free]=new EnetPeer(h,source,++generation,limits,this::offer,command);return;
        }
    }
    @Override public void close() throws IOException {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("ENet host closed outside network owner");
        if(closed) return;
        try {
            for(var peer:peers) if(peer!=null) {
                var c=new EnetDatagram.Command(0x44,255,1,new byte[4],new byte[0]);
                udp.send(peer.address(),new EnetDatagram(peer.remoteId(),
                        peer.outgoingSession(),-1,List.of(c)).encode());
            }
        } finally {
            for(var peer:peers) if(peer!=null) peer.abort(0,false);
            Arrays.fill(peers,null);events.clear();queuedBytes=0;
            connectionless=null;closed=true;udp.close();
        }
    }
}
