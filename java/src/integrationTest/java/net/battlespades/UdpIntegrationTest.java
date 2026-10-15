// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import net.battlespades.transport.UdpEndpoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UdpIntegrationTest {
    @Test void loopbackDatagramsAndResourceShutdown() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->{
            try(var server=new UdpEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),4096);
                var client=new UdpEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),4096)) {
                assertTrue(client.send(server.address(),new byte[]{1,2,3}));
                Optional<UdpEndpoint.Datagram> received=Optional.empty();
                while(received.isEmpty()) { received=server.receive();Thread.onSpinWait(); }
                assertArrayEquals(new byte[]{1,2,3},received.orElseThrow().payload());
                assertEquals(client.address(),received.orElseThrow().source());
            }
        });
    }
}
