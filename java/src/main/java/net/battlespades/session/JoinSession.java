// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;

/** Network/application owner state machine for one real ENet peer. No worker touches this object. */
public final class JoinSession implements AutoCloseable {
    public enum State { TICKET, PASSWORD, INITIAL, MAP, STATE, SELECT, ADMITTING, INPUT, LIVE, RETIRING, CLOSED }
    public interface WireSink {
        boolean send(SessionFrame frame);
        void disconnect(int reason);
    }
    public record Metrics(State state,long generation,int capability,int queuedControls,long controlBytes,
            long malformedPackets,long domainFailures,long droppedClocks) {}
    private final Thread owner=Thread.currentThread();
    private final WireSink sink;
    private final JoinSettings settings;
    private final PlayerSlots slots;
    private final SnapshotCompression compression;
    private final JoinPasswordGate passwords;
    private final JoinPasswordGate.Session password;
    private final String address;
    private final ArrayDeque<SessionFrame> controls=new ArrayDeque<>();
    private java.util.Map<Integer,PlayerRoster.Known> knownRoster=java.util.Map.of(),pendingRoster;
    private JoinScene scene;
    private State state=State.TICKET;
    private byte[] key=new byte[0];
    private int capability;
    private long generation,lastNow=-1,controlBytes,passwordAsked=-1,retireUntil=-1;
    private long malformed,domainFailures,droppedClocks;
    private PlayerSlots.Reservation slot;
    private MapSyncTransfer transfer;
    private MutableWorld.Capture capture;
    private SnapshotCompression.Task task;
    private PlayerPackets.SetClassLoadout selection;
    private boolean ticketReceived,stateSent,admitted,requireValidation,airDone;
    public JoinSession(SocketAddress address,WireSink sink,JoinScene scene,PlayerSlots slots,
            JoinSettings settings,JoinPasswordGate passwords,SnapshotCompression compression) {
        this.address=address instanceof InetSocketAddress inet?inet.getAddress().getHostAddress():address.toString();
        this.sink=sink;this.scene=scene;this.slots=slots;this.settings=settings;this.passwords=passwords;
        this.password=passwords.session();this.compression=compression;
    }
    private boolean current(long epoch,JoinScene expected) {return state!=State.CLOSED && generation==epoch && scene==expected;}
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Session accessed outside owner");}
    private void time(long now) {owned();if(now<0 || now<lastNow) throw new IllegalArgumentException("Session clock moved backwards");lastNow=now;}
    public State state() {owned();return state;}
    public boolean inGame() {owned();return state==State.LIVE;}
    public OptionalInt playerId() {owned();return slot==null?OptionalInt.empty():OptionalInt.of(slot.id());}
    public Metrics metrics() {owned();return new Metrics(state,generation,capability,controls.size(),controlBytes,malformed,domainFailures,droppedClocks);}
    public void receive(byte[] wire,long now,int serverLoop) {
        time(now);if(state==State.CLOSED) return;
        if(state==State.PASSWORD && passwordAsked>=0 && now-passwordAsked>=passwords.timeoutMillis()) {fail(11);return;}
        try {
            long maxWire=1L+settings.maxInboundBytes()+(settings.maxInboundBytes()+31L)/32;
            if(wire.length>maxWire) throw new ProtocolException("Application frame exceeds limit");
            byte[] body=Framing.inbound(wire,key,settings.maxInboundBytes());int id=body[0]&255;
            if(id==0) {
                var clock=(SimplePackets.ClockSync)PacketCodec.decode(body);
                var response=new SimplePackets.ClockSync(clock.client_time(),serverLoop+settings.clockLoopBias());
                if(!sink.send(new SessionFrame(0x30,response.encode(),Delivery.UNSEQUENCED))) droppedClocks++;
                return;
            }
            if(state==State.RETIRING) return;
            if(id==105) {
                if(ticketReceived) return;
                var ticket=(LoadingPackets.SteamSessionTicket)PacketCodec.decode(body);
                if(ticket.data().length>settings.maxTicketBytes()) throw new ProtocolException("Ticket exceeds configured limit");
                key=ticket.data();capability=ticket.capability();ticketReceived=true;
                if(!password.cleared()) {
                    if(passwords.locked(address,now)) {fail(2);return;}
                    state=State.PASSWORD;enqueue(List.of(new SessionFrame(new SimplePackets.PasswordNeeded())));
                } else begin();
                return;
            }
            if(id==111 || id==113) {
                if(state!=State.PASSWORD || passwordAsked<0) return;
                String candidate;
                try {var r=new Wire.Reader(body);r.u8();candidate=r.string();}
                catch(ProtocolException bad) {candidate=null;}
                switch(password.answer(address,candidate,now)) {
                    case ACCEPTED -> begin();
                    case RETRY -> enqueue(List.of(new SessionFrame(new SimplePackets.PasswordNeeded())));
                    case REFUSED -> fail(2);
                }
                return;
            }
            if(id==60) {if(state==State.MAP) transfer.validation(((SimplePackets.MapDataValidation)PacketCodec.decode(body)).crc(),now);return;}
            if(id==13) {selection=PlayerPackets.SetClassLoadout.receive(body);if(admitted) scene.packet(slot.id(),selection);return;}
            if(id==110) {PacketCodec.decode(body);return;}
            if(id==15) {
                if(!stateSent || admitted || slot==null) return;
                var request=(SimplePackets.NewPlayerConnection)PacketCodec.decode(body);
                long epoch=generation;var source=scene;admitted=true;var result=source.admit(slot.id(),request,selection,capability);
                if(!current(epoch,source)) return;
                if(result==null || result.player().playerId()!=slot.id()) throw new IllegalStateException("Admission must keep reserved identity");
                var packets=new ArrayList<SessionFrame>();packets.add(new SessionFrame(result.player()));packets.add(new SessionFrame(new SimplePackets.SetColor(slot.id(),result.color())));
                for(var packet:result.afterCreate()) packets.add(new SessionFrame(packet));
                if(enqueue(packets)) state=State.ADMITTING;
                return;
            }
            if(id==4) {
                if(!admitted || state!=State.INPUT && state!=State.LIVE) return;
                var input=LiveClientInput.decode(body);
                if(state==State.INPUT) {
                    if(!controls.isEmpty()) return;
                    int[] remaining={settings.replayBudget()};
                    java.util.function.Predicate<GamePacket> send=p->{if(remaining[0]==0 || !sink.send(new SessionFrame(p))) return false;remaining[0]--;return true;};
                    if(!airDone) {
                        var air=capture.lease().pumpAir(slot.id(),serverLoop,remaining[0],send);
                        if(air==MutableWorld.Replay.OVERFLOW || air==MutableWorld.Replay.STALE) {fail(13);return;}
                        if(air!=MutableWorld.Replay.COMPLETE) return;
                    }
                    if(!syncRoster()) return;
                    if(!controls.isEmpty()) return;
                    var replay=capture.lease().pump(slot.id(),serverLoop,remaining[0],send);
                    if(replay==MutableWorld.Replay.OVERFLOW || replay==MutableWorld.Replay.STALE) {fail(13);return;}
                    if(replay!=MutableWorld.Replay.COMPLETE) return;
                    if(!airDone) {
                        long epoch=generation;var source=scene;var packets=source.reveal(slot.id()).stream().map(SessionFrame::new).toList();
                        if(!current(epoch,source)) return;airDone=true;if(!enqueue(packets)) return;
                        if(!controls.isEmpty()) return;
                    }
                    capture.lease().close();state=State.LIVE;
                }
                scene.input(slot.id(),input,serverLoop);return;
            }
            if(admitted && PacketCodec.supports(id)) scene.packet(slot.id(),PacketCodec.decode(body));
        } catch(ProtocolException invalid) {malformed++;fail(13);}
        catch(RuntimeException domain) {domainFailures++;fail(13);}
    }
    private void begin() {
        if(state==State.CLOSED) return;
        if(slot==null) {slot=slots.reserve().orElse(null);if(slot==null) {fail(4);return;}}
        if(capability<scene.requiredCapability()) {fail(10);return;}
        long epoch=++generation;var source=scene;var info=source.initialInfo(capability);
        if(!current(epoch,source)) return;
        if(info.map().checksum()!=scene.world().sourceCrc()) throw new IllegalStateException("InitialInfo CRC differs from active source");
        byte[] body=info.encode();if(capability>0) body=new Wire.Writer().bytes(body).bytes(flightProfile(capability)).toByteArray();
        if(enqueue(List.of(new SessionFrame(0x30,body,Delivery.RELIABLE)))) state=State.INITIAL;
    }
    private static byte[] flightProfile(int capability) {
        int version=capability>=2?2:1;var w=new Wire.Writer().bytes(new byte[]{'B','S','F','P',(byte)version}).u8(version==2?7:3);
        for(int value:new int[]{64,1920,576,480,1280,1280,1280}) w.i16(value);
        if(version==2) w.i16(256).i16(160);return w.toByteArray();
    }
    private boolean enqueue(List<SessionFrame> frames) {
        long bytes=frames.stream().mapToLong(SessionFrame::memoryBytes).sum();
        if(frames.size()>settings.controlCapacity()-controls.size() || bytes>settings.controlBytes()-controlBytes) {fail(13);return false;}
        controls.addAll(frames);controlBytes+=bytes;return true;
    }
    private int drain(int budget,long now) {
        int count=0;
        while(count<budget && !controls.isEmpty()) {
            var frame=controls.getFirst();if(!sink.send(frame)) break;
            controls.removeFirst();controlBytes-=frame.memoryBytes();count++;
            if(frame.id()==112 && passwordAsked<0) passwordAsked=now;
            if(frame.id()==45) stateSent=true;
        }
        if(controls.isEmpty() && pendingRoster!=null) {knownRoster=pendingRoster;pendingRoster=null;}
        return count;
    }
    private boolean syncRoster() {
        var plan=scene.roster().reconcile(knownRoster,slot.id());
        if(!enqueue(plan.packets().stream().map(SessionFrame::new).toList())) return false;
        if(plan.packets().isEmpty()) knownRoster=plan.known();else pendingRoster=plan.known();
        return true;
    }
    public void pulse(long now,int loop) {
        time(now);if(state==State.CLOSED) return;
        try {
            if(state==State.PASSWORD && passwordAsked>=0 && now-passwordAsked>=passwords.timeoutMillis()) {fail(11);return;}
            int budget=settings.packetBudget();budget-=drain(budget,now);if(!controls.isEmpty()) return;
            if(state==State.RETIRING) {
                if(retireUntil<0) retireUntil=now+settings.transitionGraceMillis();
                if(now>=retireUntil) begin();return;
            }
            if(state==State.INITIAL) {transfer=new MapSyncTransfer(generation,now,scene.world().sourceCrc(),settings.mapMode(),requireValidation,false);state=State.MAP;}
            if(state==State.MAP) {
                budget-=transfer.pump(now,budget,f->sink.send(new SessionFrame(f.prefix(),f.packet().encode(),Delivery.RELIABLE)));
                if(transfer.state()==MapSyncTransfer.State.TIMED_OUT) {fail(requireValidation?18:11);return;}
                if(transfer.state()==MapSyncTransfer.State.PREPARING) {
                    if(capture==null) {
                        if(!compression.available()) return;
                        capture=scene.world().capture(transfer.preparation().full(),settings.repairAir());
                    }
                    if(task==null) task=compression.submit(capture.snapshot()).orElse(null);
                    if(task==null) return;
                    var chunks=task.poll();if(chunks.isEmpty()) return;
                    task=null;transfer.supplyChunks(generation,chunks.orElseThrow());
                    budget-=transfer.pump(now,budget,f->sink.send(new SessionFrame(f.prefix(),f.packet().encode(),Delivery.RELIABLE)));
                }
                if(transfer.state()==MapSyncTransfer.State.COMPLETE) {
                    long epoch=generation;var source=scene;var data=source.stateData(slot.id(),capability);
                    if(!current(epoch,source)) return;if(data.playerId()!=slot.id()) throw new IllegalStateException("StateData must keep reserved identity");
                    var frames=new ArrayList<SessionFrame>();frames.add(new SessionFrame(0x31,data.encode(),Delivery.RELIABLE));frames.add(new SessionFrame(new SimplePackets.SkyboxData(scene.skybox())));
                    if(!current(epoch,source)) return;
                    if(enqueue(frames) && syncRoster()) state=State.STATE;
                }
            }
            if(state==State.STATE && controls.isEmpty()) state=State.SELECT;
            if(state==State.ADMITTING) state=State.INPUT;
        } catch(RuntimeException domain) {domainFailures++;fail(13);}
    }
    /** Broadcasts are admitted only after the first-input replay boundary. */
    public boolean gameplay(GamePacket packet,Delivery delivery) {
        owned();if(state!=State.LIVE) return false;var frame=new SessionFrame(0x30,packet.encode(),delivery);return delivery==Delivery.RELIABLE?reliable(frame):sink.send(frame);
    }
    /** One reliable order for live owner controls and broadcasts, including transport backpressure. */
    private boolean reliable(SessionFrame frame){if(controls.isEmpty()&&sink.send(frame))return true;return enqueue(List.of(frame));}
    /** Source Connection.send path: reliable owner control packets may arrive after admission while loading. */
    public boolean direct(GamePacket packet) {
        owned();if(!admitted||state==State.CLOSED)return false;var frame=new SessionFrame(packet);return state==State.LIVE?reliable(frame):enqueue(List.of(frame));
    }
    public void replaceScene(JoinScene replacement) {
        owned();if(state==State.CLOSED) return;
        boolean carry=stateSent;
        releaseScene();controls.clear();controlBytes=0;generation++;scene=replacement;selection=null;stateSent=false;airDone=false;knownRoster=java.util.Map.of();pendingRoster=null;requireValidation=true;retireUntil=-1;
        if(!carry) {fail(18);return;}
        state=State.RETIRING;enqueue(List.of(new SessionFrame(new SimplePackets.MapEnded())));
    }
    private void releaseScene() {
        if(transfer!=null) transfer.cancel();transfer=null;
        if(task!=null) task.close();task=null;
        if(capture!=null) capture.lease().close();capture=null;
        if(slot!=null) {
            var previous=slot;var source=scene;boolean hadPlayer=admitted;slot=null;admitted=false;
            try {if(hadPlayer) source.disconnected(previous.id());}
            catch(RuntimeException failed) {domainFailures++;}
            finally {previous.close();}
        }
    }
    public void fail(int reason) {owned();if(state==State.CLOSED) return;try {sink.disconnect(reason);} finally {close();}}
    @Override public void close() {owned();if(state==State.CLOSED) return;state=State.CLOSED;releaseScene();controls.clear();controlBytes=0;key=new byte[0];generation++;}
}
