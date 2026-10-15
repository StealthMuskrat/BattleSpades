// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.ByteBuffer;
import java.util.List;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.transport.enet.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandshakeTest {
    private EnetDatagram.Command connect(int protocol,int mtu,int channels) {
        var p=ByteBuffer.allocate(44).putShort((short)7).put((byte)255).put((byte)255)
            .putInt(mtu).putInt(65536).putInt(channels).putInt(0).putInt(0).putInt(5000).putInt(2).putInt(2).putInt(0x12345678).putInt(protocol);
        return new EnetDatagram.Command(0x82,255,1,p.array(),new byte[0]);
    }
    @Test void sessionNegotiationAndNonceAreSourceCompatible() {
        var h=ConnectionHandshake.accept(connect(168,1400,1),3,255,255);
        assertEquals(0,h.protocolError());assertEquals(ConnectionHandshake.State.VERIFYING,h.state());
        var verify=h.verification(1234);assertEquals(7,verify.peerId());assertEquals(0,verify.sessionId());
        var p=ByteBuffer.wrap(verify.commands().getFirst().parameters());
        assertEquals(3,p.getShort());assertEquals(0,p.get());assertEquals(0,p.get());assertEquals(1400,p.getInt());
        p.position(36);assertEquals(0x12345678,p.getInt());
        var ack=EnetDatagram.acknowledge(255,0,1,1234);
        assertFalse(h.acknowledges(new EnetDatagram(3,1,-1,List.of(ack))));
        assertTrue(h.acknowledges(new EnetDatagram(3,0,-1,List.of(ack))));assertEquals(ConnectionHandshake.State.CONNECTED,h.state());
    }
    @Test void versionDirectionAndChannelValidation() {
        assertEquals(10,ConnectionHandshake.accept(connect(167,1400,1),0,255,255).protocolError());
        assertEquals(3,ConnectionHandshake.accept(connect(169,1400,1),0,255,255).protocolError());
        assertThrows(ProtocolException.class,()->ConnectionHandshake.accept(connect(168,1400,0),0,255,255));
        assertThrows(ProtocolException.class,()->ConnectionHandshake.accept(connect(168,1400,256),0,255,255));
        var clipped=ConnectionHandshake.accept(connect(168,-1,1),0,255,255).verification(0);
        assertEquals(4096,ByteBuffer.wrap(clipped.commands().getFirst().parameters()).getInt(4));
    }
}
