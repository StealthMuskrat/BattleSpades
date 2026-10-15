// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import net.battlespades.transport.enet.*;

/** Development-only native interoperability echo harness, never the application launcher. */
public final class TransportProbe {
    private TransportProbe() {}
    public static void main(String[] args) throws Exception {
        try(var host=new EnetHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),Integer.parseInt(args[0])),EnetSettings.defaults())) {
            System.out.println("READY "+((InetSocketAddress)host.address()).getPort());System.out.flush();long deadline=System.nanoTime()+120_000_000_000L;
            long connections=0,packets=0,retransmissions=0;EnetPeer last=null;
            while(System.nanoTime()<deadline) {
                host.service();
                for(int n=0;n<4096;n++) {
                    var event=host.poll();if(event.isEmpty()) break;var e=event.orElseThrow();last=e.peer();
                    if(e.type()==EnetEvent.Type.CONNECT) {connections++;System.out.println("CONNECTED "+e.peer().id()+" "+e.peer().generation());System.out.flush();}
                    if(e.type()==EnetEvent.Type.RECEIVE) {
                        byte[] data=e.data();packets++;
                        if(data.length>=4 && ByteBuffer.wrap(data).getInt()==0x53544f50) {
                            retransmissions+=e.peer().metrics().retransmissions();
                            System.out.println("RESULT "+connections+" "+packets+" "+retransmissions+" "+host.metrics().malformedDatagrams());System.out.flush();return;
                        }
                        if(data.length>=4 && ByteBuffer.wrap(data).getInt()==0x44495343) {e.peer().disconnectLater(18);continue;}
                        if(!e.peer().send(data,e.delivery())) throw new IllegalStateException("Echo backpressure: test workload exceeds bound");
                    }
                    if(e.type()==EnetEvent.Type.DISCONNECT) {
                        retransmissions+=e.peer().metrics().retransmissions();System.out.println("DISCONNECTED "+e.reason());System.out.flush();
                    }
                }
                if(last!=null && last.state()==EnetPeer.State.DISCONNECTED) last=null;
                Thread.sleep(1);
            }
            throw new IllegalStateException("Transport probe timed out");
        }
    }
}
