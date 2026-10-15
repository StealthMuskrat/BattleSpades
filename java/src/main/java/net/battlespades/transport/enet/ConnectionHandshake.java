// SPDX-License-Identifier: AGPL-3.0-or-later
// ENet protocol.c state/field semantics; ENet MIT attribution in notices.
package net.battlespades.transport.enet;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.battlespades.protocol.ProtocolException;

/** Server-side CONNECT -> VERIFY_CONNECT -> ACK phase. Not a complete ENet peer. */
public final class ConnectionHandshake {
    public enum State { VERIFYING, CONNECTED }
    private final int localPeer,remotePeer,incomingSession,outgoingSession,protocol;
    private final EnetDatagram.Command verify;
    private State state=State.VERIFYING;
    private ConnectionHandshake(int localPeer,int remotePeer,int incomingSession,int outgoingSession,int protocol,EnetDatagram.Command verify) {
        this.localPeer=localPeer;this.remotePeer=remotePeer;this.incomingSession=incomingSession;this.outgoingSession=outgoingSession;
        this.protocol=protocol;this.verify=verify;
    }
    public static ConnectionHandshake accept(EnetDatagram.Command command,int localPeer,int previousIncoming,int previousOutgoing) {
        if(command.id()!=2 || command.channel()!=255 || command.reliableSequence()!=1 || (command.flags()&128)==0) throw new ProtocolException("Invalid ENet CONNECT");
        if(localPeer<0 || localPeer>=4095) throw new IllegalArgumentException("Invalid local peer id");
        var r=ByteBuffer.wrap(command.parameters()).order(ByteOrder.BIG_ENDIAN);
        int remote=Short.toUnsignedInt(r.getShort()),remoteIncoming=Byte.toUnsignedInt(r.get()),remoteOutgoing=Byte.toUnsignedInt(r.get());
        long mtu=Integer.toUnsignedLong(r.getInt()),window=Integer.toUnsignedLong(r.getInt()),channels=Integer.toUnsignedLong(r.getInt());
        r.getInt();r.getInt();int interval=r.getInt(),acceleration=r.getInt(),deceleration=r.getInt(),connectId=r.getInt(),protocol=r.getInt();
        if(remote>=4095 || channels<1 || channels>255) throw new ProtocolException("Invalid ENet connect parameters");
        int outgoing=((remoteIncoming==255?previousOutgoing:remoteIncoming)+1)&3;
        if(outgoing==previousOutgoing) outgoing=(outgoing+1)&3;
        int incoming=((remoteOutgoing==255?previousIncoming:remoteOutgoing)+1)&3;
        if(incoming==previousIncoming) incoming=(incoming+1)&3;
        var w=ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN);
        w.putShort((short)localPeer).put((byte)outgoing).put((byte)incoming).putInt((int)Math.max(576,Math.min(4096,mtu)))
            .putInt((int)Math.max(4096,Math.min(65536,window))).putInt(1).putInt(0).putInt(0)
            .putInt(interval).putInt(acceleration).putInt(deceleration).putInt(connectId);
        var verify=new EnetDatagram.Command(0x83,255,1,w.array(),new byte[0]);
        return new ConnectionHandshake(localPeer,remote,incoming,outgoing,protocol,verify);
    }
    public EnetDatagram verification(int sentTime) { return new EnetDatagram(remotePeer,outgoingSession,sentTime,java.util.List.of(verify)); }
    /** Expected protocol-168 rejection reason, or zero when the connect data matches. */
    public int protocolError() { return protocol==168?0:Integer.compareUnsigned(protocol,168)<0?10:3; }
    public boolean acknowledges(EnetDatagram datagram) {
        if(datagram.peerId()!=localPeer || datagram.sessionId()!=incomingSession) return false;
        for(var command:datagram.commands()) if(command.id()==1 && command.channel()==255) {
            var r=ByteBuffer.wrap(command.parameters()).order(ByteOrder.BIG_ENDIAN);
            if(Short.toUnsignedInt(r.getShort())==1) { state=State.CONNECTED;return true; }
        }
        return false;
    }
    public int localPeerId() { return localPeer; }
    public int remotePeerId() { return remotePeer; }
    public int incomingSessionId() { return incomingSession; }
    public int outgoingSessionId() { return outgoingSession; }
    public int mtu() { return ByteBuffer.wrap(verify.parameters()).getInt(4); }
    public int connectId() { return ByteBuffer.wrap(verify.parameters()).getInt(36); }
    public int connectData() { return protocol; }
    public State state() { return state; }
}
