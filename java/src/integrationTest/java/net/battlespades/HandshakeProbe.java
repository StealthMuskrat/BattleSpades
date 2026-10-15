// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.transport.enet.ConnectionHandshake;
import net.battlespades.transport.enet.EnetDatagram;

/** Development probe only: demonstrates native ENet handshake, then exits. No gameplay service. */
public final class HandshakeProbe {
    private HandshakeProbe() {}
    public static void main(String[] args) throws Exception {
        try(var udp=new UdpEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(),Integer.parseInt(args[0])),4096)) {
            System.out.println("READY");System.out.flush();
            long deadline=System.nanoTime()+10_000_000_000L;ConnectionHandshake handshake=null;
            while(System.nanoTime()<deadline) {
                var incoming=udp.receive();if(incoming.isEmpty()) {Thread.sleep(1);continue;}
                var d=incoming.orElseThrow();var packet=EnetDatagram.decode(d.payload());
                if(handshake==null) {
                    for(var command:packet.commands()) if(command.id()==2) {
                        handshake=ConnectionHandshake.accept(command,0,255,255);
                        if(handshake.protocolError()!=0) throw new IllegalArgumentException("Protocol mismatch");
                        udp.send(d.source(),handshake.verification(1234).encodeCompressed());break;
                    }
                } else if(handshake.acknowledges(packet)) {System.out.println("CONNECTED_ACKNOWLEDGED");return;}
            }
            throw new IllegalStateException("Handshake timed out");
        }
    }
}
