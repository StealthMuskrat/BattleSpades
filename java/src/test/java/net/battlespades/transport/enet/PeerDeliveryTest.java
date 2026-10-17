// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.battlespades.protocol.ProtocolException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerDeliveryTest {
    private static EnetDatagram.Command connect() {
        return new EnetDatagram.Command(0x82,255,1,ByteBuffer.allocate(44).putShort((short)0).put((byte)255).put((byte)255)
                .putInt(1400).putInt(65536).putInt(1).putInt(0).putInt(0).putInt(5000).putInt(2).putInt(2).putInt(12345).putInt(168).array(),new byte[0]);
    }
    private static class Harness {
        final List<EnetEvent> events=new ArrayList<>();
        final List<EnetDatagram> sent=new ArrayList<>();
        final AtomicBoolean consume=new AtomicBoolean(true);
        final EnetPeer peer;
        Harness() throws Exception { this(EnetSettings.defaults()); }
        Harness(EnetSettings settings) throws Exception {
            var c=connect();peer=new EnetPeer(ConnectionHandshake.accept(c,0,255,255),new java.net.InetSocketAddress("127.0.0.1",10000),1,settings,e->{
                if(e.type()==EnetEvent.Type.RECEIVE && !consume.get()) return false;
                events.add(e);return true;
            },c);
            flush(100);ack(255,1,100,110);sent.clear();events.clear();
        }
        void flush(long now) throws Exception {peer.flush(now,32,(a,d)->{sent.add(EnetDatagram.decode(d));return true;});}
        void ack(int channel,int seq,int stamp,long now) {input(now,EnetDatagram.acknowledge(channel,seq,seq,stamp));}
        void input(long now,EnetDatagram.Command... commands) {peer.receive(new EnetDatagram(0,0,(int)(now&65535),List.of(commands)),now);}
        List<Integer> data() {return events.stream().filter(e->e.type()==EnetEvent.Type.RECEIVE).map(e->e.data()[0]&255).toList();}
    }
    private static EnetDatagram.Command unreliable(int reliable,int sequence,int value) {
        return new EnetDatagram.Command(7,0,reliable,ByteBuffer.allocate(4).putShort((short)sequence).putShort((short)1).array(),new byte[]{(byte)value});
    }
    private static EnetDatagram.Command unsequenced(int group,int value) {
        return new EnetDatagram.Command(0x49,0,0,ByteBuffer.allocate(4).putShort((short)group).putShort((short)1).array(),new byte[]{(byte)value});
    }
    private static EnetDatagram.Command fragment(boolean reliable,int parent,int start,int count,int number,int total,int offset,byte[] data) {
        return new EnetDatagram.Command(reliable?0x88:12,0,reliable?(start+number)&65535:parent,
                ByteBuffer.allocate(20).putShort((short)start).putShort((short)data.length).putInt(count).putInt(number).putInt(total).putInt(offset).array(),data);
    }
    @Test void reliableLossRetransmitsAndAckReleasesMemory() throws Exception {
        var h=new Harness();assertTrue(h.peer.send(new byte[]{1},Delivery.RELIABLE));assertTrue(h.peer.send(new byte[]{2},Delivery.RELIABLE));h.flush(200);
        assertEquals(List.of(1,2),h.sent.getFirst().commands().stream().map(EnetDatagram.Command::reliableSequence).toList());
        h.peer.maintain(231);assertEquals(2,h.peer.metrics().retransmissions());h.flush(232);
        assertEquals(2,h.sent.size());h.ack(0,2,232,240);h.ack(0,1,232,240);
        assertEquals(0,h.peer.metrics().outgoingBytes());assertEquals(0,h.peer.metrics().inFlightBytes());
        h.peer.maintain(270);assertEquals(2,h.peer.metrics().retransmissions());
    }
    @Test void reliableReceiptWaitsForEveryFragmentAndIgnoresDuplicateAcks() throws Exception {
        var h=new Harness();var receipt=h.peer.sendTrackedReliable(new byte[3000]).orElseThrow();assertFalse(receipt.released());h.flush(200);
        int[] released={0};assertTrue(receipt.onRelease(()->released[0]++));
        h.ack(0,3,200,210);h.ack(0,1,200,210);h.ack(0,1,200,210);assertFalse(receipt.released());
        h.peer.maintain(241);h.flush(242);assertFalse(receipt.released());h.ack(0,2,242,250);assertTrue(receipt.released());
        assertEquals(1,released[0]);h.ack(0,2,242,251);assertEquals(1,released[0]);assertThrows(IllegalStateException.class,()->receipt.onRelease(()->{}));
        java.util.concurrent.CompletableFuture.runAsync(()->assertThrows(IllegalStateException.class,receipt::released)).join();
    }
    @Test void realFragmentReceiptsReleaseLifecycleHoldsOnlyAfterAllAcksOrDiscard()throws Exception {
        var h=new Harness();var holds=new net.battlespades.session.SnapshotHolds();byte[] create=new byte[3000];create[0]=28;create[1]=7;
        var receipt=h.peer.sendTrackedReliable(create).orElseThrow();holds.queued(create,receipt,100);h.flush(200);
        h.ack(0,1,200,210);h.ack(0,3,200,210);assertFalse(holds.heldRows(100).isEmpty());assertTrue(holds.reliableUnackedThrough(1,100));
        h.ack(0,2,200,211);assertTrue(holds.heldRows(100).isEmpty());assertFalse(holds.reliableUnackedThrough(1,100));
        var discarded=h.peer.sendTrackedReliable(create).orElseThrow();holds.queued(create,discarded,100);h.peer.disconnect(18);
        assertTrue(holds.heldRows(100).isEmpty());assertFalse(holds.reliableUnackedThrough(2,100));
        var other=new Harness();var alreadyReleased=other.peer.sendTrackedReliable(new byte[]{1}).orElseThrow();other.peer.disconnect(18);
        int[] late={0};assertTrue(alreadyReleased.onRelease(()->late[0]++));assertEquals(1,late[0]);
    }
    @Test void failedTrackedEnqueueIsAtomicAndDisconnectReleasesQueuedReceipts() throws Exception {
        var h=new Harness(new EnetSettings(1,4,8,8,32,4,8,32,32,true));
        var first=h.peer.sendTrackedReliable(new byte[4]).orElseThrow();var second=h.peer.sendTrackedReliable(new byte[4]).orElseThrow();assertTrue(h.peer.sendTrackedReliable(new byte[]{3}).isEmpty());
        assertFalse(first.released());assertFalse(second.released());h.flush(200);h.ack(0,1,200,210);assertTrue(first.released());assertFalse(second.released());
        var next=h.peer.sendTrackedReliable(new byte[]{3}).orElseThrow();h.flush(211);assertEquals(3,h.sent.getLast().commands().getFirst().reliableSequence());h.peer.disconnect(18);assertTrue(second.released());assertTrue(next.released());
    }
    @Test void reliableOrderingDuplicateSuppressionAndAckHeader() throws Exception {
        var h=new Harness();h.input(200,EnetDatagram.reliable(0,2,new byte[]{2}));assertTrue(h.data().isEmpty());
        h.input(201,EnetDatagram.reliable(0,1,new byte[]{1}));assertEquals(List.of(1,2),h.data());
        h.input(202,EnetDatagram.reliable(0,1,new byte[]{1}));assertEquals(List.of(1,2),h.data());h.flush(203);
        var acks=h.sent.getFirst().commands();assertEquals(2,acks.size());
        for(var ack:acks) assertEquals(ack.reliableSequence(),Short.toUnsignedInt(ByteBuffer.wrap(ack.parameters()).getShort()));
    }
    @Test void unreliableWaitsForReliableWhileUnsequencedBypasses() throws Exception {
        var h=new Harness();h.input(200,unreliable(1,1,10),unsequenced(1,99));assertEquals(List.of(99),h.data());
        h.input(201,EnetDatagram.reliable(0,1,new byte[]{1}));assertEquals(List.of(99,1,10),h.data());
        h.input(202,unsequenced(1,99),unreliable(1,1,10));assertEquals(3,h.data().size());
        h.input(203,unreliable(1,3,30));h.input(204,unreliable(1,2,20));assertEquals(List.of(99,1,10,30),h.data());
    }
    @Test void interleavedCommandsKeepNativeDispatchOrder() throws Exception {
        var h=new Harness();h.input(200,EnetDatagram.reliable(0,1,new byte[]{1}),unreliable(1,1,11),EnetDatagram.reliable(0,2,new byte[]{2}));
        assertEquals(List.of(1,11,2),h.data());
    }
    @Test void reliableFragmentGroupAdvancesByAllItsSequences() throws Exception {
        var h=new Harness();h.input(200,fragment(true,0,1,3,2,6,4,new byte[]{5,6}),fragment(true,0,1,3,0,6,0,new byte[]{1,2}));
        assertTrue(h.data().isEmpty());h.input(201,fragment(true,0,1,3,1,6,2,new byte[]{3,4}));
        assertArrayEquals(new byte[]{1,2,3,4,5,6},h.events.getFirst().data());
        h.input(202,EnetDatagram.reliable(0,4,new byte[]{7}));assertEquals(List.of(1,7),h.data());
        h.input(203,fragment(true,0,1,3,1,6,2,new byte[]{3,4}));assertEquals(2,h.data().size());assertEquals(0,h.peer.metrics().waitingBytes());
    }
    @Test void incompleteUnreliableFragmentsCannotBlockNewerCompletePacket() throws Exception {
        var h=new Harness();h.input(200,fragment(false,0,1,2,0,4,0,new byte[]{1,2}));
        h.input(201,unreliable(0,2,9));assertEquals(List.of(9),h.data());assertEquals(0,h.peer.metrics().waitingBytes());
        h.input(202,fragment(false,0,1,2,1,4,2,new byte[]{3,4}));assertEquals(List.of(9),h.data());
    }
    @Test void fullReceiveQueueRetainsReliableDataAndWithholdsNewAcks() throws Exception {
        var settings=new EnetSettings(1,4,8,8,32,4,8,32,32,true);var h=new Harness(settings);h.consume.set(false);
        h.input(200,EnetDatagram.reliable(0,1,new byte[]{1,0,0,0}),EnetDatagram.reliable(0,2,new byte[]{2,0,0,0}),EnetDatagram.reliable(0,3,new byte[]{3,0,0,0}));
        assertEquals(8,h.peer.metrics().waitingBytes());h.flush(201);assertEquals(2,h.sent.getFirst().commands().size());
        h.consume.set(true);h.peer.dispatch();h.input(202,EnetDatagram.reliable(0,3,new byte[]{3,0,0,0}));
        assertEquals(List.of(1,2,3),h.data());assertEquals(0,h.peer.metrics().waitingBytes());
    }
    @Test void outboundFragmentationPreservesEnetFlagSemantics() throws Exception {
        for(var delivery:Delivery.values()) {
            var h=new Harness();byte[] data=new byte[3000];Arrays.fill(data,(byte)42);assertTrue(h.peer.send(data,delivery));h.flush(200);
            List<EnetDatagram.Command> commands=h.sent.stream().flatMap(d->d.commands().stream()).toList();assertEquals(3,commands.size());
            for(int i=0;i<3;i++) {
                var c=commands.get(i);assertEquals(delivery==Delivery.UNRELIABLE_FRAGMENT?12:8,c.id());
                assertEquals(1,Short.toUnsignedInt(ByteBuffer.wrap(c.parameters()).getShort()));
                assertEquals(i,ByteBuffer.wrap(c.parameters()).getInt(8));
            }
        }
    }
    @Test void socketBackpressureDoesNotLoseQueuedReliableCommand() throws Exception {
        var h=new Harness();h.peer.send(new byte[]{1},Delivery.RELIABLE);
        assertEquals(0,h.peer.flush(200,1,(a,b)->false));assertEquals(0,h.peer.metrics().inFlightBytes());
        h.flush(201);assertEquals(1,h.sent.size());assertEquals(1,h.peer.metrics().outgoingBytes());
    }
    @Test void outgoingBudgetRejectsAtomicallyAndCanRecoverAfterAck() throws Exception {
        var h=new Harness(new EnetSettings(1,4,8,8,32,4,8,32,32,true));
        assertTrue(h.peer.send(new byte[4],Delivery.RELIABLE));assertTrue(h.peer.send(new byte[4],Delivery.RELIABLE));assertFalse(h.peer.send(new byte[]{3},Delivery.RELIABLE));
        h.flush(200);h.ack(0,1,200,201);h.ack(0,2,200,201);assertTrue(h.peer.send(new byte[]{3},Delivery.RELIABLE));h.flush(202);
        assertEquals(3,h.sent.getLast().commands().getFirst().reliableSequence());
    }
    @Test void localAndRemoteDisconnectAndTimeoutReleaseAllQueues() throws Exception {
        var local=new Harness();local.peer.send(new byte[10],Delivery.RELIABLE);local.peer.disconnect(18);local.flush(200);
        var command=local.sent.getFirst().commands().getFirst();assertEquals(4,command.id());local.ack(255,command.reliableSequence(),200,201);
        assertEquals(EnetPeer.State.DISCONNECTED,local.peer.state());assertEquals(18,local.events.getFirst().reason());assertEquals(0,local.peer.metrics().outgoingBytes());
        var remote=new Harness();remote.input(200,new EnetDatagram.Command(0x84,255,2,ByteBuffer.allocate(4).putInt(13).array(),new byte[0]));
        assertEquals(EnetPeer.State.ACKNOWLEDGING_DISCONNECT,remote.peer.state());remote.flush(201);
        assertEquals(EnetPeer.State.DISCONNECTED,remote.peer.state());assertEquals(13,remote.events.getFirst().reason());
        var timeout=new Harness();timeout.peer.send(new byte[20],Delivery.RELIABLE);timeout.flush(200);timeout.peer.maintain(30201);
        assertEquals(EnetPeer.State.DISCONNECTED,timeout.peer.state());assertTrue(timeout.events.getFirst().timedOut());assertEquals(0,timeout.peer.metrics().outgoingBytes());
    }
    @Test void malformedFragmentsSessionsAndUnsequencedWindowAreBounded() throws Exception {
        var h=new Harness();assertThrows(ProtocolException.class,()->h.input(200,fragment(true,0,1,Integer.MAX_VALUE,0,1,0,new byte[]{1})));
        h.peer.receive(new EnetDatagram(0,1,200,List.of(EnetDatagram.reliable(0,1,new byte[]{1}))),200);assertTrue(h.data().isEmpty());
        h.input(201,unsequenced(1023,1),unsequenced(1024,2),unsequenced(1023,3),unsequenced(1024,4));assertEquals(List.of(1,2),h.data());
    }
    @Test void deliverySurvivesReliableSequenceAndSentTimeWrap() throws Exception {
        var h=new Harness();
        for(int n=1;n<=66000;n++) {
            long now=200L+n*2;int sequence=n&65535;
            assertTrue(h.peer.send(new byte[]{(byte)n},Delivery.RELIABLE));h.flush(now);
            var command=h.sent.getLast().commands().getFirst();assertEquals(sequence,command.reliableSequence());
            h.ack(0,sequence,(int)(now&65535),now+1);
            h.input(now+1,EnetDatagram.reliable(0,sequence,new byte[]{(byte)n}));
            assertEquals(1,h.events.size());assertEquals(n&255,h.events.getFirst().data()[0]&255);
            h.flush(now+1);h.sent.clear();h.events.clear();
        }
        assertEquals(0,h.peer.metrics().outgoingBytes());assertEquals(0,h.peer.metrics().waitingBytes());assertEquals(0,h.peer.metrics().inFlightBytes());
    }
}
