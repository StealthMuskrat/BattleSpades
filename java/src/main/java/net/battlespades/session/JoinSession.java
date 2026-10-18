// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.battlespades.protocol.*;
import net.battlespades.player.FlightProfile;
import net.battlespades.replication.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.transport.enet.DeliveryReceipt;
import net.battlespades.world.*;

/** Network/application owner state machine for one real ENet peer. No worker touches this object. */
public final class JoinSession implements WorldUpdateLink,AutoCloseable {
    public enum State { TICKET, PASSWORD, INITIAL, MAP, STATE, SELECT, ADMITTING, INPUT, LIVE, RETIRING, CLOSED }
    public interface WireSink {
        boolean send(SessionFrame frame);
        void disconnect(int reason);
        default Double roundTripMillis(){return null;}
        default net.battlespades.player.ActionClock.Link actionLink(){return null;}
        default boolean releaseTracking(){return false;}
        default Sent sendTracked(SessionFrame frame){return new Sent(send(frame),null);}
        default int unfragmentedWireLimit(){return 1372;}
        default boolean snapshotUnsequenced(){return false;}
        default void flushSnapshots(){}
    }
    public record Sent(boolean accepted,DeliveryReceipt receipt) {}
    private static final class ReceiptBridge implements DeliveryReceipt {
        private final Thread owner=Thread.currentThread();
        private boolean released,bound,registered;
        private Runnable observer;
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Queued receipt accessed outside owner");}
        @Override public boolean released(){owned();return released;}
        @Override public boolean onRelease(Runnable value){owned();if(registered)throw new IllegalStateException("Queued receipt observer already registered");registered=true;observer=java.util.Objects.requireNonNull(value);if(released)observer.run();return true;}
        void finish(){owned();if(released)return;released=true;if(observer!=null)observer.run();}
        void bind(DeliveryReceipt receipt){owned();if(bound)throw new IllegalStateException("Queued receipt already bound");bound=true;if(receipt==null||!receipt.onRelease(this::finish))finish();}
    }
    private static final class Queued {
        final SessionFrame frame;
        final ReceiptBridge receipt;
        boolean registered;
        Queued(SessionFrame frame,boolean track){this.frame=frame;receipt=track&&frame.delivery()==Delivery.RELIABLE?new ReceiptBridge():null;}
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
    private final ArrayDeque<Queued> controls=new ArrayDeque<>();
    private final SnapshotHolds snapshotHolds=new SnapshotHolds();
    private final SnapshotObserver observerState=new SnapshotObserver();
    private java.util.Map<Integer,PlayerRoster.Known> knownRoster=java.util.Map.of(),pendingRoster;
    private JoinScene scene;
    private State state=State.TICKET;
    private byte[] key=new byte[0];
    private int capability,currentLoop;
    private long generation,lastNow=-1,controlBytes,passwordAsked=-1,retireUntil=-1;
    private long malformed,domainFailures,droppedClocks;
    private PlayerSlots.Reservation slot;
    private MapSyncTransfer transfer;
    private MutableWorld.Capture capture;
    private SnapshotCompression.Task task;
    private PlayerPackets.SetClassLoadout selection;
    private boolean ticketReceived,stateSent,admitted,requireValidation,airDone;
    private boolean inMenu=true,spectatorPending;private long spectatorRetry;private int spectatorAttempts;
    private boolean sceneArmed,sceneGated;
    private java.util.concurrent.CompletableFuture<Boolean> reloadResult;
    public JoinSession(SocketAddress address,WireSink sink,JoinScene scene,PlayerSlots slots,
            JoinSettings settings,JoinPasswordGate passwords,SnapshotCompression compression) {
        this.address=addressHost(address);
        this.sink=sink;this.scene=scene;this.slots=slots;this.settings=settings;this.passwords=passwords;
        this.password=passwords.session();this.compression=compression;
    }
    public static String addressHost(SocketAddress address){return address instanceof InetSocketAddress inet?inet.getAddress().getHostAddress():address.toString();}
    public String addressHost(){owned();return address;}
    private boolean current(long epoch,JoinScene expected) {return state!=State.CLOSED && generation==epoch && scene==expected;}
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Session accessed outside owner");}
    private void time(long now) {owned();if(now<0 || now<lastNow) throw new IllegalArgumentException("Session clock moved backwards");lastNow=now;}
    private void refreshLink(){if(admitted&&slot!=null){scene.roundTripMillis(slot.id(),sink.roundTripMillis());scene.actionLink(slot.id(),sink.actionLink());}}
    public State state() {owned();return state;}
    public boolean inMenu(){owned();return inMenu;}
    public boolean inGame() {owned();return state==State.LIVE&&!sceneGated;}
    public OptionalInt playerId() {owned();return slot==null?OptionalInt.empty():OptionalInt.of(slot.id());}
    @Override public Integer localPlayerId(){owned();return slot==null?null:slot.id();}
    @Override public SnapshotObserver observerState(){owned();return observerState;}
    @Override public boolean snapshotUnsequenced(){owned();return sink.snapshotUnsequenced();}
    @Override public void flushSnapshots(){owned();sink.flushSnapshots();}
    public Metrics metrics() {owned();return new Metrics(state,generation,capability,controls.size(),controlBytes,malformed,domainFailures,droppedClocks);}
    public void receive(byte[] wire,long now,int serverLoop) {
        owned();var done=new java.util.concurrent.CompletableFuture<Void>();
        receiveCore(wire,now,serverLoop,Runnable::run,false,done);done.join();
    }
    public long playerIdentity(){owned();return slot==null?0:scene.roster().identity(slot.id());}
    /** The outer frame must await this result before consuming its next packet. */
    public java.util.concurrent.CompletionStage<Void> receiveAsync(byte[] wire,long now,int serverLoop,java.util.concurrent.Executor resume) {
        owned();var done=new java.util.concurrent.CompletableFuture<Void>();
        receiveCore(wire,now,serverLoop,resume,true,done);return done.minimalCompletionStage();
    }
    private void receiveCore(byte[] wire,long now,int serverLoop,java.util.concurrent.Executor resume,boolean awaitPackets,java.util.concurrent.CompletableFuture<Void> done) {
        time(now);if(state==State.CLOSED||sceneArmed){done.complete(null);return;}currentLoop=serverLoop;
        if(state==State.PASSWORD && passwordAsked>=0 && now-passwordAsked>=passwords.timeoutMillis()){fail(11);done.complete(null);return;}
        java.util.concurrent.CompletionStage<Void> deferred=null;long receiveEpoch=generation;var receiveScene=scene;
        try {
            refreshLink();
            long maxWire=1L+settings.maxInboundBytes()+(settings.maxInboundBytes()+31L)/32;
            if(wire.length>maxWire) throw new ProtocolException("Application frame exceeds limit");
            byte[] body=Framing.inbound(wire,key,settings.maxInboundBytes());int id=body[0]&255;
            if(id==0) {
                var clock=(SimplePackets.ClockSync)PacketCodec.decode(body);
                var response=new SimplePackets.ClockSync(clock.client_time(),serverLoop+settings.clockLoopBias());
                if(!sendFrame(new SessionFrame(0x30,response.encode(),Delivery.UNSEQUENCED))) droppedClocks++;
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
            if(id==13) {selection=PlayerPackets.SetClassLoadout.receive(body);if(admitted){if(awaitPackets)deferred=java.util.Objects.requireNonNull(scene.packetAsync(slot.id(),selection));else scene.packet(slot.id(),selection);}return;}
            if(id==110) {inMenu=((SimplePackets.ClientInMenu)PacketCodec.decode(body)).in_menu()!=0;if(!inMenu&&admitted&&slot!=null&&scene.spectator(slot.id())&&state!=State.LIVE){spectatorPending=true;spectatorAttempts=0;spectatorRetry=now;}return;}
            if(id==15) {
                if(!stateSent || slot==null)return;
                if(admitted){if(awaitPackets)deferred=java.util.Objects.requireNonNull(scene.packetAsync(slot.id(),PacketCodec.decode(body)));else scene.packet(slot.id(),PacketCodec.decode(body));return;}
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
                if(state==State.INPUT&&!settle(serverLoop))return;
                scene.input(slot.id(),input,serverLoop);return;
            }
            if(admitted && PacketCodec.supports(id)){var packet=PacketCodec.decode(body);if(awaitPackets)deferred=java.util.Objects.requireNonNull(scene.packetAsync(slot.id(),packet));else scene.packet(slot.id(),packet);}
        } catch(ProtocolException invalid) {malformed++;fail(13);}
        catch(RuntimeException domain) {domainFailures++;fail(13);}
        finally {
            if(deferred==null)done.complete(null);
            else {
                var continuation=new net.battlespades.runtime.OwnerSequence(resume);
                deferred.whenComplete((unused,error)->continuation.resume(()->{
                    if(error!=null){
                        var root=net.battlespades.runtime.OwnerSequence.cause(error);
                        if(root instanceof Error||root instanceof java.util.concurrent.CancellationException){done.completeExceptionally(root);return;}
                        if(current(receiveEpoch,receiveScene)){domainFailures++;fail(13);}
                    }
                    done.complete(null);
                },done));
            }
        }
    }
    /** Shared terrain/roster/reveal barrier for first-input humans and settled spectators. */
    private boolean settle(int serverLoop){
        if(!controls.isEmpty()) return false;
        int[] remaining={settings.replayBudget()};
        java.util.function.Predicate<GamePacket> send=p->{if(remaining[0]==0 || !sendFrame(new SessionFrame(p))) return false;remaining[0]--;return true;};
        if(!airDone) {
            var air=capture.lease().pumpAir(slot.id(),serverLoop,remaining[0],send);
            if(air==MutableWorld.Replay.OVERFLOW || air==MutableWorld.Replay.STALE) {fail(13);return false;}
            if(air!=MutableWorld.Replay.COMPLETE) return false;
        }
        if(!syncRoster()) return false;
        if(!controls.isEmpty()) return false;
        var replay=capture.lease().pump(slot.id(),serverLoop,remaining[0],send);
        if(replay==MutableWorld.Replay.OVERFLOW || replay==MutableWorld.Replay.STALE) {fail(13);return false;}
        if(replay!=MutableWorld.Replay.COMPLETE) return false;
        if(!airDone) {
            long epoch=generation;var source=scene;var packets=source.reveal(slot.id(),serverLoop).stream().map(SessionFrame::new).toList();
            if(!current(epoch,source)) return false;airDone=true;if(!enqueue(packets)) return false;
            if(!controls.isEmpty()) return false;
        }
        capture.lease().close();state=State.LIVE;spectatorPending=false;
        return state==State.LIVE;
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
        return FlightProfile.forCapability(capability).encode();
    }
    private boolean enqueue(List<SessionFrame> frames) {
        long bytes=frames.stream().mapToLong(SessionFrame::memoryBytes).sum();
        if(frames.size()>settings.controlCapacity()-controls.size() || bytes>settings.controlBytes()-controlBytes) {fail(13);return false;}
        for(var frame:frames){var queued=new Queued(frame,sink.releaseTracking());track(queued);controls.addLast(queued);}controlBytes+=bytes;return true;
    }
    private void track(Queued queued){if(queued.registered)return;queued.registered=true;if(queued.receipt!=null)snapshotHolds.queued(queued.frame.body(),queued.receipt,currentLoop);}
    private boolean sendQueued(Queued queued){
        if(queued.receipt==null)return sink.send(queued.frame);
        var sent=sink.sendTracked(queued.frame);if(!sent.accepted)return false;queued.receipt.bind(sent.receipt);track(queued);return true;
    }
    private boolean sendFrame(SessionFrame frame){return sendQueued(new Queued(frame,sink.releaseTracking()));}
    private void discardControls(){for(var queued:controls)if(queued.receipt!=null)queued.receipt.finish();controls.clear();controlBytes=0;}
    private int drain(int budget,long now) {
        int count=0;
        while(count<budget && !controls.isEmpty()) {
            var queued=controls.getFirst();if(!sendQueued(queued)) break;
            controls.removeFirst();var frame=queued.frame;controlBytes-=frame.memoryBytes();count++;
            if(frame.id()==112 && passwordAsked<0) passwordAsked=now;
            if(frame.id()==45){stateSent=true;finishReload(true);}
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
        time(now);if(state==State.CLOSED) return;currentLoop=loop;
        try {
            if(sceneArmed){drain(settings.packetBudget(),now);return;}
            refreshLink();
            if(state==State.PASSWORD && passwordAsked>=0 && now-passwordAsked>=passwords.timeoutMillis()) {fail(11);return;}
            int budget=settings.packetBudget();budget-=drain(budget,now);if(!controls.isEmpty()) return;
            if(state==State.RETIRING) {
                if(retireUntil<0) retireUntil=now+settings.transitionGraceMillis();
                if(now>=retireUntil) begin();return;
            }
            if(state==State.INITIAL) {transfer=new MapSyncTransfer(generation,now,scene.world().sourceCrc(),settings.mapMode(),requireValidation,false);state=State.MAP;}
            if(state==State.MAP) {
                budget-=transfer.pump(now,budget,f->sendFrame(new SessionFrame(f.prefix(),f.packet().encode(),Delivery.RELIABLE)));
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
                    budget-=transfer.pump(now,budget,f->sendFrame(new SessionFrame(f.prefix(),f.packet().encode(),Delivery.RELIABLE)));
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
            if(spectatorPending&&state==State.INPUT&&now>=spectatorRetry){
                if(slot==null||!scene.spectator(slot.id()))spectatorPending=false;
                else{boolean complete;try{complete=settle(loop);}catch(RuntimeException failure){domainFailures++;spectatorPending=false;return;}if(complete||state==State.CLOSED||spectatorAttempts++>=2000)spectatorPending=false;else spectatorRetry=now+50;}
            }
        } catch(RuntimeException domain) {domainFailures++;fail(13);}
    }
    /** Broadcasts are admitted only after the first-input replay boundary. */
    public boolean gameplay(GamePacket packet,Delivery delivery) {
        owned();if(state!=State.LIVE||sceneGated) return false;var frame=new SessionFrame(0x30,packet.encode(),delivery);return delivery==Delivery.RELIABLE?ordered(frame):sendFrame(frame);
    }
    /** One reliable order for live owner controls and broadcasts, including transport backpressure. */
    private boolean ordered(SessionFrame frame){if(controls.isEmpty()&&sendFrame(frame))return true;return enqueue(List.of(frame));}
    public long reliableSendIndex(){owned();return snapshotHolds.reliableSendIndex();}
    public boolean reliableUnackedThrough(long index,int loop){owned();currentLoop=loop;return snapshotHolds.reliableUnackedThrough(index,loop);}
    public Set<SnapshotHolds.Row> heldSnapshotRows(int loop){owned();currentLoop=loop;return snapshotHolds.heldRows(loop);}
    public int snapshotPayloadLimit(){
        owned();int budget=Math.max(64,sink.unfragmentedWireLimit()),low=0,high=budget;
        while(low<high){int middle=(int)(low+(high-low+1L)/2);long encoded=(long)middle+(middle+31L)/32+1;if(encoded<=budget)low=middle;else high=middle-1;}return low;
    }
    /** WorldUpdate-only path: sequenced snapshots retain the reliable controls' application order. */
    public boolean snapshot(byte[] body,Delivery delivery,int loop){
        owned();currentLoop=loop;if(state!=State.LIVE||sceneGated)return false;
        if(body.length==0||(body[0]&255)!=2||!(PacketCodec.decode(body) instanceof WorldUpdate))throw new ProtocolException("Expected WorldUpdate");
        var frame=new SessionFrame(0x30,body,delivery);
        if(delivery!=Delivery.RELIABLE&&frame.wireBytes()>Math.max(64,sink.unfragmentedWireLimit()))frame=new SessionFrame(0x30,body,Delivery.UNRELIABLE_FRAGMENT);
        return frame.delivery()==Delivery.UNSEQUENCED?sendFrame(frame):ordered(frame);
    }
    /** Source Connection.send path: reliable owner control packets may arrive after admission while loading. */
    public boolean direct(GamePacket packet) {
        owned();if(!admitted||state==State.CLOSED)return false;var frame=new SessionFrame(packet);return state==State.LIVE?ordered(frame):enqueue(List.of(frame));
    }
    private void finishReload(boolean loaded){if(reloadResult!=null){var result=reloadResult;reloadResult=null;result.complete(loaded);}}
    public boolean carriesScene(){owned();return state!=State.CLOSED&&(inGame()||stateSent);}
    /** Invalidate suspended old admissions before the freeze is sent; retain the old life for awaited leave. */
    public void armSceneTransition(){owned();if(sceneArmed||!carriesScene())throw new IllegalStateException("Peer cannot carry this scene");generation++;sceneArmed=true;discardControls();}
    public void gateSceneTransition(){owned();sceneGated=true;}
    public boolean transitionPacket(GamePacket packet){owned();if(state==State.CLOSED)return false;return ordered(new SessionFrame(packet));}
    /** Mode ownership must already be settled; release the domain without broadcasting PlayerLeft. */
    public void detachScenePlayer(){owned();if(state==State.CLOSED||!admitted)return;if(!sceneArmed||!sceneGated)throw new IllegalStateException("Old scene is not gated");if(admitted&&slot!=null){scene.transitionDetach(slot.id());admitted=false;}}
    public java.util.concurrent.CompletionStage<Boolean> reloadScene(JoinScene replacement){
        owned();java.util.Objects.requireNonNull(replacement);if(!sceneArmed||!sceneGated||admitted)throw new IllegalStateException("Old scene must be armed, gated and detached");
        releaseScene();generation++;scene=replacement;selection=null;stateSent=false;airDone=false;spectatorPending=false;inMenu=true;spectatorAttempts=0;knownRoster=java.util.Map.of();pendingRoster=null;requireValidation=true;retireUntil=0;
        sceneArmed=false;sceneGated=false;state=State.RETIRING;reloadResult=new java.util.concurrent.CompletableFuture<>();return reloadResult.minimalCompletionStage();
    }
    public void replaceScene(JoinScene replacement) {
        owned();if(state==State.CLOSED) return;
        if(sceneArmed)throw new IllegalStateException("Staged transition must use reloadScene");
        boolean carry=stateSent;
        releaseScene();discardControls();generation++;scene=replacement;selection=null;stateSent=false;airDone=false;spectatorPending=false;inMenu=true;spectatorAttempts=0;knownRoster=java.util.Map.of();pendingRoster=null;requireValidation=true;retireUntil=-1;
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
    @Override public void close() {owned();if(state==State.CLOSED) return;state=State.CLOSED;finishReload(false);releaseScene();discardControls();snapshotHolds.clear();key=new byte[0];generation++;}
}
