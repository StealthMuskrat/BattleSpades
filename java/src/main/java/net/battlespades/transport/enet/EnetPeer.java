// SPDX-License-Identifier: AGPL-3.0-or-later
// ENet protocol.c/peer.c state and wire semantics; MIT attribution in root notices.
package net.battlespades.transport.enet;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.battlespades.protocol.ProtocolException;

/** A network-owner-confined, single-channel ENet 1.3 server peer. */
public final class EnetPeer {
    public enum State { VERIFYING, CONNECTED, DRAINING, DISCONNECTING, ACKNOWLEDGING_DISCONNECT, DISCONNECTED }
    @FunctionalInterface interface Sender { boolean send(SocketAddress address, byte[] data) throws IOException; }
    public record Metrics(long sentDatagrams, long retransmissions, long receivedPackets,
            long unreliableDrops, long backpressure, int roundTripTime, int roundTripVariance,
            int throttle, long outgoingBytes, long waitingBytes, int queuedCommands, int inFlightBytes) {}
    private static final class Pending {
        final EnetDatagram.Command command;
        final int key, size;
        int attempts;
        long sentAt, timeout, timeoutLimit;
        boolean inFlight,throttleChecked;
        Pending(EnetDatagram.Command command) {
            this.command=command;key=command.channel()<<16|command.reliableSequence();size=command.payload().length;
        }
    }
    private static final class Incoming {
        final int sequence, span, total, fragmentCount;
        final Delivery delivery;
        final FragmentAssembly fragments;
        byte[] data;
        Incoming(int sequence, int span, int total, int fragmentCount, Delivery delivery, FragmentAssembly fragments, byte[] data) {
            this.sequence=sequence;this.span=span;this.total=total;this.fragmentCount=fragmentCount;
            this.delivery=delivery;this.fragments=fragments;this.data=data;
        }
    }
    private final Thread owner=Thread.currentThread();
    private final ConnectionHandshake handshake;
    private final SocketAddress address;
    private final long generation;
    private final EnetSettings limits;
    private final Predicate<EnetEvent> events;
    private final ArrayDeque<Pending> outgoing=new ArrayDeque<>();
    private final Map<Integer,Pending> outstanding=new HashMap<>();
    private final TreeSet<Pending> timers=new TreeSet<>(Comparator.<Pending>comparingLong(p->p.sentAt+p.timeout).thenComparingInt(p->p.key));
    private final LinkedHashMap<Integer,EnetDatagram.Command> acknowledgements=new LinkedHashMap<>();
    private final Map<Integer,Incoming> reliable=new HashMap<>();
    private final Map<Long,Incoming> unreliable=new HashMap<>();
    private final BitSet unsequencedWindow=new BitSet(1024);
    private final int[] reliableWindows=new int[16];
    private State state=State.VERIFYING;
    private int outgoingReliable, outgoingUnreliable, outgoingControl=1, outgoingUnsequenced;
    private int incomingReliable, incomingUnreliable, incomingUnsequencedBase, usedReliableWindows;
    private int inFlightBytes, sendWindow=65536, throttle=32, throttleLimit=32, throttleCounter;
    private int rtt=500, variance, lastRtt=500, lastVariance, lowestRtt=500, highestVariance;
    private long throttleInterval=5000, throttleAcceleration=2, throttleDeceleration=2, throttleEpoch;
    private long outgoingBytes, waitingBytes, lastAckAt, lastSentAt, earliestTimeout=-1;
    private long sentDatagrams, retransmissions, receivedPackets, unreliableDrops, backpressure;
    private boolean connectPending, disconnectPending, disconnectedNotified, timedOut;
    private int disconnectReason;

    EnetPeer(ConnectionHandshake handshake, SocketAddress address, long generation, EnetSettings limits,
            Predicate<EnetEvent> events, EnetDatagram.Command connect) {
        this.handshake=handshake;this.address=address;this.generation=generation;this.limits=limits;this.events=events;
        var r=ByteBuffer.wrap(connect.parameters());
        long incomingBandwidth=Integer.toUnsignedLong(r.getInt(16));
        updateSendWindow(incomingBandwidth);
        throttleInterval=Integer.toUnsignedLong(r.getInt(24));throttleAcceleration=Integer.toUnsignedLong(r.getInt(28));
        throttleDeceleration=Integer.toUnsignedLong(r.getInt(32));
        queue(handshake.verification(0).commands().getFirst());
    }
    private void owned() { if(Thread.currentThread()!=owner) throw new IllegalStateException("ENet peer accessed outside network owner"); }
    public int id() { return handshake.localPeerId(); }
    public long generation() { return generation; }
    public SocketAddress address() { return address; }
    public State state() { owned();return state; }
    public int connectData() { return handshake.connectData(); }
    int connectId() { return handshake.connectId(); }
    int incomingSession() { return handshake.incomingSessionId(); }
    int outgoingSession() { return handshake.outgoingSessionId(); }
    int remoteId() { return handshake.remotePeerId(); }
    public int mtu() { return handshake.mtu(); }
    public Metrics metrics() {
        owned();return new Metrics(sentDatagrams,retransmissions,receivedPackets,unreliableDrops,backpressure,
                rtt,variance,throttle,outgoingBytes,waitingBytes,outgoing.size(),inFlightBytes);
    }
    boolean finished() { return state==State.DISCONNECTED && disconnectedNotified; }
    private static ByteBuffer params(int size) { return ByteBuffer.allocate(size); }
    private static boolean isReliable(EnetDatagram.Command c) { return (c.flags()&128)!=0; }
    private void queue(EnetDatagram.Command c) {
        Pending p=new Pending(c);outgoing.addLast(p);outgoingBytes+=p.size;
        if(isReliable(c) && outstanding.putIfAbsent(p.key,p)!=null) throw new IllegalStateException("ENet sequence still outstanding");
    }
    /** Atomic enqueue: false leaves all sequence counters and queues unchanged. */
    public boolean send(byte[] payload, Delivery delivery) {
        owned();
        if(state!=State.CONNECTED) return false;
        if(payload.length>limits.maxPacketBytes()) throw new IllegalArgumentException("Packet exceeds configured ENet maximum");
        int fragmentLength=mtu()-28;
        int count=payload.length>fragmentLength?(int)((payload.length+(long)fragmentLength-1)/fragmentLength):1;
        if(count>limits.maxPendingCommands() || outgoing.size()+timers.size()+count>limits.maxPendingCommands()
                || payload.length>limits.maxOutgoingBytes()-outgoingBytes) {backpressure++;return false;}
        List<EnetDatagram.Command> commands=new ArrayList<>();
        int nextReliable=outgoingReliable,nextUnreliable=outgoingUnreliable,nextGroup=outgoingUnsequenced;
        boolean reliableDelivery=delivery==Delivery.RELIABLE || (count>1 && delivery!=Delivery.UNRELIABLE_FRAGMENT)
                || (delivery!=Delivery.UNSEQUENCED && nextUnreliable==65535);
        if(count>1) {
            int start=reliableDelivery?Sequence.next(nextReliable):Sequence.next(nextUnreliable);
            if(!reliableDelivery) nextUnreliable=start;
            for(int n=0,offset=0;n<count;n++,offset+=fragmentLength) {
                int length=Math.min(fragmentLength,payload.length-offset);
                int sequence=nextReliable;
                if(reliableDelivery) {nextReliable=Sequence.next(nextReliable);sequence=nextReliable;nextUnreliable=0;}
                byte[] body=java.util.Arrays.copyOfRange(payload,offset,offset+length);
                byte[] p=params(20).putShort((short)start).putShort((short)length).putInt(count).putInt(n).putInt(payload.length).putInt(offset).array();
                commands.add(new EnetDatagram.Command(reliableDelivery?0x88:12,0,sequence,p,body));
            }
        } else if(delivery==Delivery.UNSEQUENCED) {
            nextGroup=Sequence.next(nextGroup);
            commands.add(new EnetDatagram.Command(0x49,0,0,params(4).putShort((short)nextGroup).putShort((short)payload.length).array(),payload));
        } else if(reliableDelivery) {
            nextReliable=Sequence.next(nextReliable);nextUnreliable=0;
            commands.add(EnetDatagram.reliable(0,nextReliable,payload));
        } else {
            nextUnreliable=Sequence.next(nextUnreliable);
            commands.add(new EnetDatagram.Command(7,0,nextReliable,params(4).putShort((short)nextUnreliable).putShort((short)payload.length).array(),payload));
        }
        // Protect the shared 16-bit sequence namespace before committing any fragment.
        for(var c:commands) if(isReliable(c) && outstanding.containsKey(c.reliableSequence())) {backpressure++;return false;}
        outgoingReliable=nextReliable;outgoingUnreliable=nextUnreliable;outgoingUnsequenced=nextGroup;
        for(var c:commands) queue(c);
        return true;
    }
    public void disconnect(int reason) { owned();beginDisconnect(reason); }
    public void disconnectLater(int reason) {
        owned();
        if(state==State.CONNECTED || state==State.DRAINING) {
            disconnectReason=reason;state=State.DRAINING;
            if(outgoing.isEmpty() && outstanding.isEmpty()) beginDisconnect(reason);
        }
    }
    private void beginDisconnect(int reason) {
        if(state==State.DISCONNECTED || state==State.DISCONNECTING || state==State.ACKNOWLEDGING_DISCONNECT) return;
        clearQueues();disconnectReason=reason;state=State.DISCONNECTING;
        outgoingControl=Sequence.next(outgoingControl);
        queue(new EnetDatagram.Command(0x84,255,outgoingControl,params(4).putInt(reason).array(),new byte[0]));
    }
    private void clearQueues() {
        outgoing.clear();outstanding.clear();timers.clear();reliable.clear();unreliable.clear();acknowledgements.clear();
        java.util.Arrays.fill(reliableWindows,0);usedReliableWindows=0;outgoingBytes=waitingBytes=0;inFlightBytes=0;
    }
    void abort(int reason,boolean timeout) {
        clearQueues();state=State.DISCONNECTED;disconnectReason=reason;timedOut=timeout;
        connectPending=false;disconnectPending=true;dispatch();
    }
    void receive(EnetDatagram packet,long now) {
        owned();
        if(state==State.DISCONNECTED || packet.peerId()!=id() || packet.sessionId()!=incomingSession()) return;
        for(var c:packet.commands()) {
            if(isReliable(c) && packet.sentTime()<0) throw new ProtocolException("ENet reliable command has no sent time");
            if(c.payload().length>limits.maxPacketBytes()) throw new ProtocolException("ENet packet exceeds configured limit");
            if(c.channel()!=0 && c.channel()!=255) throw new ProtocolException("Unsupported ENet channel");
            if(c.id()==1) { acknowledge(c,now);dispatch();continue; }
            if(c.id()==4) {
                int reason=ByteBuffer.wrap(c.parameters()).getInt();
                clearQueues();disconnectReason=reason;
                if(isReliable(c)) {state=State.ACKNOWLEDGING_DISCONNECT;ack(c,packet.sentTime());}
                else abort(reason,false);
                break;
            }
            if(state==State.VERIFYING || state==State.DISCONNECTING || state==State.ACKNOWLEDGING_DISCONNECT) continue;
            if(c.channel()==255) {
                switch(c.id()) {
                    case 5 -> { }
                    case 10 -> updateSendWindow(Integer.toUnsignedLong(ByteBuffer.wrap(c.parameters()).getInt()));
                    case 11 -> {
                        var r=ByteBuffer.wrap(c.parameters());throttleInterval=Integer.toUnsignedLong(r.getInt());
                        throttleAcceleration=Integer.toUnsignedLong(r.getInt());throttleDeceleration=Integer.toUnsignedLong(r.getInt());
                    }
                    default -> throw new ProtocolException("Invalid ENet control command");
                }
            } else {
                boolean accepted=switch(c.id()) {
                    case 6 -> receiveReliable(c);
                    case 7 -> receiveUnreliable(c);
                    case 8,12 -> receiveFragment(c);
                    case 9 -> {receiveUnsequenced(c);yield true;}
                    default -> throw new ProtocolException("Invalid ENet data command");
                };
                if(!accepted) continue;
                dispatch();
            }
            if(isReliable(c)) ack(c,packet.sentTime());
        }
        dispatch();
    }
    private void ack(EnetDatagram.Command c,int time) {
        if(c.channel()==0) {
            int window=c.reliableSequence()/4096,current=incomingReliable/4096;
            if(c.reliableSequence()<incomingReliable) window+=16;
            if(window>=current+7 && window<=current+8) return;
        }
        int key=c.channel()<<16|c.reliableSequence();
        if(acknowledgements.size()>=limits.maxPendingCommands() && !acknowledgements.containsKey(key)) {backpressure++;return;}
        // ENet's ACK command repeats the acknowledged sequence in its own header.
        acknowledgements.put(key,EnetDatagram.acknowledge(c.channel(),c.reliableSequence(),c.reliableSequence(),time));
    }
    private boolean allowedWindow(int sequence) {
        int window=sequence/4096,current=incomingReliable/4096;
        if(sequence<incomingReliable) window+=16;
        return window>=current && window<current+7;
    }
    private boolean reserve(int bytes) {
        if(bytes>limits.maxWaitingBytes()-waitingBytes || reliable.size()+unreliable.size()>=limits.maxPendingCommands()) {backpressure++;return false;}
        waitingBytes+=bytes;return true;
    }
    private boolean receiveReliable(EnetDatagram.Command c) {
        int sequence=c.reliableSequence();
        if(!allowedWindow(sequence) || sequence==incomingReliable) return true;
        if(reliable.containsKey(sequence)) return true;
        byte[] body=c.payload();if(!reserve(body.length)) return false;
        reliable.put(sequence,new Incoming(sequence,1,body.length,0,Delivery.RELIABLE,null,body));return true;
    }
    private boolean receiveUnreliable(EnetDatagram.Command c) {
        int sequence=c.reliableSequence(),u=Short.toUnsignedInt(ByteBuffer.wrap(c.parameters()).getShort());
        if(!allowedWindow(sequence) || (sequence==incomingReliable && u<=incomingUnreliable)) return true;
        long key=((long)sequence<<16)|u;
        if(unreliable.containsKey(key)) return true;
        byte[] body=c.payload();if(!reserve(body.length)) {unreliableDrops++;return true;}
        unreliable.put(key,new Incoming(sequence,1,body.length,0,Delivery.UNRELIABLE,null,body));return true;
    }
    private boolean receiveFragment(EnetDatagram.Command c) {
        var r=ByteBuffer.wrap(c.parameters());int start=Short.toUnsignedInt(r.getShort());r.getShort();
        long count=Integer.toUnsignedLong(r.getInt()),number=Integer.toUnsignedLong(r.getInt()),total=Integer.toUnsignedLong(r.getInt()),offset=Integer.toUnsignedLong(r.getInt());
        byte[] body=c.payload();boolean ordered=c.id()==8;
        int base=ordered?start:c.reliableSequence();
        if(!allowedWindow(base) || (ordered?start==incomingReliable:base==incomingReliable && start<=incomingUnreliable)) return true;
        if(count<1 || count>limits.maxPendingCommands() || count>total || number>=count || total>limits.maxPacketBytes()
                || offset>=total || body.length<1 || body.length>total-offset) throw new ProtocolException("Invalid ENet fragment bounds");
        if(ordered && c.reliableSequence()!=((start+number)&65535)) throw new ProtocolException("Fragment sequence does not match number");
        long key=((long)base<<16)|start;
        Incoming group=ordered?reliable.get(start):unreliable.get(key);
        if(group==null) {
            if(!reserve((int)total)) return !ordered;
            group=new Incoming(base,ordered?(int)count:1,(int)total,(int)count,
                    ordered?Delivery.RELIABLE:Delivery.UNRELIABLE_FRAGMENT,
                    new FragmentAssembly((int)total,(int)count,limits.maxPacketBytes(),limits.maxPendingCommands()),null);
            if(ordered) reliable.put(start,group);else unreliable.put(key,group);
        } else if(group.fragments==null || group.total!=total || group.fragmentCount!=count) {
            throw new ProtocolException("Conflicting ENet fragment group");
        }
        if(group.data==null) {
            var completed=group.fragments.accept((int)number,(int)offset,body);
            if(completed.isPresent()) group.data=completed.orElseThrow();
        }
        return true;
    }
    private void receiveUnsequenced(EnetDatagram.Command c) {
        int group=Short.toUnsignedInt(ByteBuffer.wrap(c.parameters()).getShort()),index=group%1024;
        int expanded=group<incomingUnsequencedBase?group+65536:group;
        if(expanded>=incomingUnsequencedBase+32768) return;
        if(group-index!=incomingUnsequencedBase) {incomingUnsequencedBase=group-index;unsequencedWindow.clear();}
        else if(unsequencedWindow.get(index)) return;
        byte[] body=c.payload();
        if(!connectPending && events.test(new EnetEvent(EnetEvent.Type.RECEIVE,this,Delivery.UNSEQUENCED,body,0,false))) receivedPackets++;
        else {unreliableDrops++;backpressure++;}
        unsequencedWindow.set(index);
    }
    void dispatch() {
        if(connectPending) {
            if(!events.test(new EnetEvent(EnetEvent.Type.CONNECT,this,null,new byte[0],connectData(),false))) return;
            connectPending=false;
        }
        if(state==State.CONNECTED || state==State.DRAINING) {
            while(true) {
                int next=Sequence.next(incomingReliable);Incoming i=reliable.get(next);
                if(i==null || i.data==null || !deliver(i)) break;
                reliable.remove(next);incomingReliable=(next+i.span-1)&65535;incomingUnreliable=0;
            }
            var ready=unreliable.entrySet().stream().filter(e->e.getValue().sequence==incomingReliable)
                    .sorted(Map.Entry.comparingByKey()).toList();
            for(var e:ready) {
                Incoming i=e.getValue();int sequence=(int)(e.getKey()&65535);
                if(i.data==null) continue;
                if(sequence>incomingUnreliable) {
                    if(!deliver(i)) break;
                    incomingUnreliable=sequence;
                } else waitingBytes-=i.total;
                unreliable.remove(e.getKey());
            }
            var it=unreliable.entrySet().iterator();
            while(it.hasNext()) {
                var e=it.next();Incoming i=e.getValue();
                if(!allowedWindow(i.sequence) || (i.sequence==incomingReliable && (e.getKey()&65535)<=incomingUnreliable)) {
                    waitingBytes-=i.total;unreliableDrops++;it.remove();
                }
            }
        }
        if(disconnectPending && events.test(new EnetEvent(EnetEvent.Type.DISCONNECT,this,null,new byte[0],disconnectReason,timedOut))) {
            disconnectPending=false;disconnectedNotified=true;
        }
    }
    private boolean deliver(Incoming i) {
        if(!events.test(new EnetEvent(EnetEvent.Type.RECEIVE,this,i.delivery,i.data,0,false))) {backpressure++;return false;}
        waitingBytes-=i.total;receivedPackets++;return true;
    }
    private void updateSendWindow(long incomingBandwidth) {
        sendWindow=incomingBandwidth==0?65536:(int)Math.max(4096,Math.min(65536,(incomingBandwidth/65536)*4096));
    }
    private void acknowledge(EnetDatagram.Command c,long now) {
        var r=ByteBuffer.wrap(c.parameters());int sequence=Short.toUnsignedInt(r.getShort()),stamp=Short.toUnsignedInt(r.getShort());
        int key=c.channel()<<16|sequence;Pending p=outstanding.get(key);
        if(p==null || p.attempts==0) return;
        int sample=((int)now-stamp)&65535;if(sample>=32768) return;sample=Math.max(1,sample);
        timers.remove(p);outstanding.remove(key);outgoing.remove(p);
        if(p.inFlight) inFlightBytes-=p.size;
        outgoingBytes-=p.size;
        if(c.channel()==0 && --reliableWindows[sequence/4096]==0) usedReliableWindows&=~(1<<(sequence/4096));
        updateRtt(sample,now);earliestTimeout=-1;lastAckAt=Math.max(1,now);
        if(state==State.VERIFYING && p.command.id()==3) {state=State.CONNECTED;connectPending=true;}
        else if(state==State.DISCONNECTING && p.command.id()==4) abort(disconnectReason,false);
        else if(state==State.DRAINING && outgoing.isEmpty() && outstanding.isEmpty()) beginDisconnect(disconnectReason);
    }
    private void updateRtt(int sample,long now) {
        if(lastAckAt==0) {rtt=sample;variance=(sample+1)/2;}
        else {
            if(lastRtt<=lastVariance) throttle=throttleLimit;
            else if(sample<=lastRtt) throttle=(int)Math.min(throttleLimit,throttle+throttleAcceleration);
            else if(sample>lastRtt+2L*lastVariance) throttle=(int)Math.max(0,throttle-throttleDeceleration);
            variance-=variance/4;int diff=Math.abs(rtt-sample);variance+=diff/4;
            rtt+=sample>=rtt?diff/8:-diff/8;
        }
        lowestRtt=Math.min(lowestRtt,rtt);highestVariance=Math.max(highestVariance,variance);
        if(throttleEpoch==0 || now-throttleEpoch>=throttleInterval) {
            lastRtt=lowestRtt;lastVariance=Math.max(1,highestVariance);lowestRtt=rtt;highestVariance=variance;throttleEpoch=now;
        }
    }
    void maintain(long now) {
        owned();dispatch();
        if(state==State.DISCONNECTED) return;
        while(!timers.isEmpty()) {
            Pending p=timers.first();if(now-p.sentAt<p.timeout) break;
            if(earliestTimeout<0 || p.sentAt<earliestTimeout) earliestTimeout=p.sentAt;
            if(now-earliestTimeout>=30000 || (p.timeout>=p.timeoutLimit && now-earliestTimeout>=5000)) {abort(0,true);return;}
            timers.remove(p);p.inFlight=false;inFlightBytes-=p.size;p.timeout*=2;outgoing.addFirst(p);retransmissions++;
        }
        if(state==State.CONNECTED && outstanding.isEmpty() && now-lastAckAt>=500 && now-lastSentAt>=500) {
            outgoingControl=Sequence.next(outgoingControl);queue(new EnetDatagram.Command(0x85,255,outgoingControl,new byte[0],new byte[0]));
        }
        if(state==State.DRAINING && outgoing.isEmpty() && outstanding.isEmpty()) beginDisconnect(disconnectReason);
    }
    private void applyUnreliableThrottle() {
        var dropKeys=new java.util.HashSet<Long>();
        var it=outgoing.iterator();
        while(it.hasNext()) {
            Pending p=it.next();var c=p.command;
            if(isReliable(c)) continue;
            int unrel=c.id()==7 || c.id()==12?Short.toUnsignedInt(ByteBuffer.wrap(c.parameters()).getShort()):0;
            long key=((long)c.reliableSequence()<<16)|unrel;
            boolean first=c.id()!=12 || ByteBuffer.wrap(c.parameters()).getInt(8)==0;
            if(first && !p.throttleChecked) {throttleCounter=(throttleCounter+7)%32;if(throttleCounter>throttle) dropKeys.add(key);}
            p.throttleChecked=true;
            if(dropKeys.contains(key)) {outgoingBytes-=p.size;unreliableDrops++;it.remove();}
        }
    }
    int flush(long now,int budget,Sender sender) throws IOException {
        owned();int sent=0;
        applyUnreliableThrottle();
        while(sent<budget && state!=State.DISCONNECTED) {
            var commands=new ArrayList<EnetDatagram.Command>();var chosen=new ArrayList<Pending>();
            var ackKeys=new ArrayList<Integer>();int bytes=4;int draftBytes=inFlightBytes,draftWindows=usedReliableWindows;
            int[] counts=reliableWindows.clone();
            for(var e:acknowledgements.entrySet()) {
                if(commands.size()==32 || bytes+8>mtu()) break;
                commands.add(e.getValue());ackKeys.add(e.getKey());bytes+=8;
            }
            for(Pending p:outgoing) {
                if(commands.size()==32) break;
                var c=p.command;boolean reliableCommand=isReliable(c);
                if(reliableCommand) {
                    int window=c.reliableSequence()/4096;
                    if(c.channel()==0 && p.attempts==0 && c.reliableSequence()%4096==0) {
                        int mask=(1023<<window)|(1023>>>(16-window));
                        if(counts[(window+15)%16]>=4096 || (draftWindows&mask)!=0) continue;
                    }
                    if(p.size>0 && draftBytes+p.size>Math.max(mtu(),(throttle*sendWindow)/32)) continue;
                    if(c.channel()==0 && p.attempts==0) {counts[window]++;draftWindows|=1<<window;}
                }
                int commandBytes=4+c.parameters().length+p.size;
                if(bytes+commandBytes>mtu()) break;
                commands.add(c);chosen.add(p);bytes+=commandBytes;
                if(reliableCommand) draftBytes+=p.size;
            }
            if(commands.isEmpty()) break;
            byte[] data=new EnetDatagram(handshake.remotePeerId(),outgoingSession(),(int)(now&65535),commands).encodeCompressed();
            if(!sender.send(address,data)) {backpressure++;break;}
            sent++;sentDatagrams++;lastSentAt=now;
            for(int key:ackKeys) acknowledgements.remove(key);
            for(Pending p:chosen) {
                outgoing.remove(p);
                if(isReliable(p.command)) {
                    if(p.attempts==0) {
                        p.timeout=Math.max(1,rtt+4L*variance);p.timeoutLimit=32*p.timeout;
                        if(p.command.channel()==0) {int window=p.command.reliableSequence()/4096;reliableWindows[window]++;usedReliableWindows|=1<<window;}
                    }
                    p.attempts++;p.sentAt=now;p.inFlight=true;inFlightBytes+=p.size;timers.add(p);
                } else outgoingBytes-=p.size;
            }
            if(state==State.ACKNOWLEDGING_DISCONNECT && acknowledgements.isEmpty()) abort(disconnectReason,false);
        }
        return sent;
    }
}
