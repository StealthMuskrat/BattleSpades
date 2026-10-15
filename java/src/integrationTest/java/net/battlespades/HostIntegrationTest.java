// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.transport.enet.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HostIntegrationTest {
    private EnetDatagram connect(int protocol) {
        var c=new EnetDatagram.Command(0x82,255,1,ByteBuffer.allocate(44).putShort((short)0).put((byte)255).put((byte)255)
                .putInt(1400).putInt(65536).putInt(1).putInt(0).putInt(0).putInt(5000).putInt(2).putInt(2).putInt(1234).putInt(protocol).array(),new byte[0]);
        return new EnetDatagram(4095,0,100,List.of(c));
    }
    @Test void udpConnectReliableReceiveAndReply() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->{
            var bind=new InetSocketAddress(InetAddress.getLoopbackAddress(),0);
            EnetPeer closedPeer;
            try(var host=new EnetHost(bind,EnetSettings.defaults());var client=new UdpEndpoint(bind,4096)) {
                client.send(host.address(),connect(168).encodeCompressed());
                EnetDatagram verify=null;
                while(verify==null) {host.service();var d=client.receive();if(d.isPresent()) verify=EnetDatagram.decode(d.orElseThrow().payload());}
                var fields=ByteBuffer.wrap(verify.commands().getFirst().parameters());int id=Short.toUnsignedInt(fields.getShort());int session=fields.get()&255;
                client.send(host.address(),new EnetDatagram(id,session,-1,List.of(EnetDatagram.acknowledge(255,1,1,verify.sentTime()))).encode());
                EnetEvent connected=null;
                while(connected==null) {host.service();connected=host.poll().orElse(null);}
                assertEquals(EnetEvent.Type.CONNECT,connected.type());assertEquals(168,connected.reason());
                client.send(host.address(),new EnetDatagram(id,session,123,List.of(EnetDatagram.reliable(0,1,new byte[]{1,2,3}))).encodeCompressed());
                EnetEvent received=null;
                while(received==null) {host.service();received=host.poll().orElse(null);}
                assertArrayEquals(new byte[]{1,2,3},received.data());assertTrue(received.peer().send(new byte[]{4,5,6},Delivery.RELIABLE));
                boolean replied=false;
                while(!replied) {
                    host.service();var d=client.receive();if(d.isEmpty()) continue;
                    for(var command:EnetDatagram.decode(d.orElseThrow().payload()).commands()) if(command.id()==6) {
                        assertArrayEquals(new byte[]{4,5,6},command.payload());replied=true;
                    }
                }
                closedPeer=connected.peer();assertTrue(closedPeer.send(new byte[1024],Delivery.RELIABLE));
            }
            assertEquals(EnetPeer.State.DISCONNECTED,closedPeer.state());
            assertEquals(0,closedPeer.metrics().outgoingBytes());assertEquals(0,closedPeer.metrics().waitingBytes());
        });
    }
    @Test void protocolMismatchAllocatesNoPeerAndSendsCorrectReason() throws Exception {
        for(int protocol:new int[]{167,169}) {
            var bind=new InetSocketAddress(InetAddress.getLoopbackAddress(),0);
            try(var host=new EnetHost(bind,EnetSettings.defaults());var client=new UdpEndpoint(bind,4096)) {
                client.send(host.address(),connect(protocol).encode());
                var answer=assertTimeout(Duration.ofSeconds(5),()->{
                    long deadline=System.nanoTime()+4_000_000_000L;
                    while(System.nanoTime()<deadline) {host.service();var p=client.receive();if(p.isPresent()) return EnetDatagram.decode(p.orElseThrow().payload());}
                    throw new AssertionError("No protocol rejection received");
                });
                assertEquals(4,answer.commands().getFirst().id());assertEquals(protocol<168?10:3,ByteBuffer.wrap(answer.commands().getFirst().parameters()).getInt());
                assertEquals(0,host.metrics().activePeers());assertEquals(1,host.metrics().refusedConnections());assertTrue(host.poll().isEmpty());
            }
        }
    }
}
