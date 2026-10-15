// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.battlespades.protocol.*;
import net.battlespades.session.*;
import net.battlespades.support.FixtureScene;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JoinSessionTest {
    static final class Compression implements SnapshotCompression {
        final List<Job> jobs=new ArrayList<>();boolean ready=true,capacity=true,closed;
        final class Job implements Task {
            final MapSyncSnapshot snapshot;boolean cancelled;List<byte[]> chunks;
            Job(MapSyncSnapshot snapshot) {this.snapshot=snapshot;}
            @Override public Optional<List<byte[]>> poll() {if(!ready) return Optional.empty();if(chunks==null) chunks=snapshot.buildChunks();return Optional.of(chunks);}
            @Override public void close() {cancelled=true;}
        }
        @Override public boolean available() {return capacity;}
        @Override public Optional<Task> submit(MapSyncSnapshot snapshot) {if(!capacity) return Optional.empty();var j=new Job(snapshot);jobs.add(j);return Optional.of(j);}
        @Override public void close() {closed=true;jobs.forEach(Job::close);}
    }
    static final class Wire implements JoinSession.WireSink {
        final List<SessionFrame> frames=new ArrayList<>();int reason=-1;boolean accepted=true;int blockedId=-1;
        @Override public boolean send(SessionFrame frame) {if(!accepted || frame.id()==blockedId) return false;frames.add(frame);return true;}
        @Override public void disconnect(int reason) {this.reason=reason;}
        List<Integer> ids() {return frames.stream().map(SessionFrame::id).toList();}
    }
    static final class Flow implements AutoCloseable {
        final FixtureScene scene;final Compression compression=new Compression();final Wire wire=new Wire();final PlayerSlots slots=new PlayerSlots(2);
        final JoinSession session;byte[] key=new byte[0];long now;
        Flow() {this(FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)));}
        Flow(JoinSettings settings) {
            scene=new FixtureScene(1,64);session=new JoinSession(new InetSocketAddress("127.0.0.1",1234),wire,scene,slots,settings,new JoinPasswordGate(settings.password()),compression);
        }
        void client(GamePacket packet) {receive(packet.encode(),false);}
        void receive(byte[] body,boolean compressed) {
            byte[] data=body.clone();if(key.length>0) for(int i=0;i<data.length;i++) data[i]^=key[i%key.length];
            byte[] framed=compressed?Framing.outbound(0x31,data):new WireWriter().frame(data);
            session.receive(framed,now,(int)(now*60/1000));
        }
        void ticket(byte[] ticket,int capability) {client(new LoadingPackets.SteamSessionTicket(ticket,capability==0?new byte[0]:new byte[]{'B','S','C','F',(byte)capability}));key=ticket.clone();}
        void pulse(long advance) {now+=advance;session.pulse(now,(int)(now*60/1000));}
        void validate() {client(new SimplePackets.MapDataValidation(scene.world().sourceCrc()));pulse(0);pulse(100);pulse(0);}
        void load() {ticket(new byte[0],0);pulse(0);validate();assertEquals(JoinSession.State.SELECT,session.state());}
        void select() {client(new SimplePackets.NewPlayerConnection(1,5,0,1,"Source"));for(int i=0;i<8 && session.state()==JoinSession.State.ADMITTING;i++) pulse(0);assertEquals(JoinSession.State.INPUT,session.state());}
        void input() {client(new ClientInput(100,session.playerId().orElse(0),false,7,1,0,0,0,0,0,0));}
        void game() {for(int i=0;i<32 && !session.inGame();i++) {input();pulse(0);}assertTrue(session.inGame());}
        @Override public void close() {session.close();scene.close();compression.close();}
    }
    private static final class WireWriter {byte[] frame(byte[] data) {return new net.battlespades.protocol.Wire.Writer().u8(0x30).bytes(data).toByteArray();}}
    @Test void realLoaderOrderPromisedIdentityAndFirstInputBroadcastGate() {
        try(var h=new Flow()) {
            h.client(new SimplePackets.NewPlayerConnection(1,5,0,1,"forged"));assertEquals(0,h.slots.occupied());assertTrue(h.scene.players.isEmpty());
            h.load();assertEquals(List.of(114,60,55,59,45,51),h.wire.ids());assertEquals(1,h.slots.occupied());
            var state=(StateData)PacketCodec.decode(h.wire.frames.stream().filter(f->f.id()==45).findFirst().orElseThrow().body());assertEquals(h.session.playerId().orElseThrow(),state.playerId());
            assertFalse(h.session.gameplay(new SimplePackets.ChatMessage(0,0,"early"),Delivery.RELIABLE));h.select();assertEquals(state.playerId(),h.scene.players.get(0).player().playerId());
            h.client(new SimplePackets.NewPlayerConnection(2,7,0,0,"duplicate"));assertEquals(1,h.scene.players.size());
            h.input();assertFalse(h.session.inGame());assertTrue(h.scene.inputs.isEmpty());h.pulse(0);h.input();assertTrue(h.session.inGame());assertEquals(1,h.scene.inputs.size());assertEquals(6,h.scene.receivedLoops.getFirst());
            assertTrue(h.session.gameplay(new SimplePackets.ChatMessage(0,0,"live"),Delivery.RELIABLE));assertEquals(0,h.scene.world().activeLeases());
        }
    }
    @Test void privatePasswordControlsReleaseAndTicketXorIncludesCompressedInput() {
        var policy=new JoinPasswordGate.Policy("café",3,1000,60000);
        try(var h=new Flow(FixtureScene.copy(JoinSettings.defaults(),Map.of("password",policy,"mapMode",MapSyncTransfer.Mode.AUTO)))) {
            h.ticket(new byte[]{7,19,(byte)255},0);assertTrue(h.wire.frames.isEmpty());h.pulse(0);assertEquals(List.of(112),h.wire.ids());assertEquals(0,h.slots.occupied());
            h.client(new LoadingPackets.SteamSessionTicket(h.key));h.pulse(0);assertEquals(List.of(112),h.wire.ids());
            h.client(new SimplePackets.PasswordProvided("wrong"));h.pulse(0);h.receive(new SimplePackets.Password("café").encode(),true);h.pulse(0);assertEquals(JoinSession.State.PASSWORD,h.session.state());
            h.pulse(500);h.receive(new SimplePackets.PasswordProvided("café").encode(),true);h.pulse(0);assertTrue(h.wire.ids().contains(114));h.validate();assertEquals(JoinSession.State.SELECT,h.session.state());
        }
    }
    @Test void passwordDeadlineAppliesBeforeLateAnswerAndMalformedFramesStayPeerLocal() {
        try(var h=new Flow(FixtureScene.copy(JoinSettings.defaults(),Map.of("password",new JoinPasswordGate.Policy("secret",3,1000,0))))) {
            h.ticket(new byte[0],0);h.pulse(0);h.now=1000;h.client(new SimplePackets.PasswordProvided("secret"));assertEquals(11,h.wire.reason);assertEquals(0,h.slots.occupied());
        }
        try(var h=new Flow()) {h.session.receive(new byte[]{0x31,31,0},0,0);assertEquals(13,h.wire.reason);assertEquals(1,h.session.metrics().malformedPackets());assertTrue(h.wire.frames.isEmpty());}
    }
    @Test void asyncSnapshotJournalAndRosterDrainGapUseFinalCells() {
        try(var h=new Flow()) {
            h.compression.ready=false;h.ticket(new byte[0],0);h.pulse(0);h.validate();assertEquals(JoinSession.State.MAP,h.session.state());assertEquals(1,h.scene.world().activeLeases());
            var a=new MutableWorld.Cell(1,2,3);h.scene.world().set(a,true,0x111111,9.0);h.scene.world().set(a,true,0x222222,null);
            h.compression.ready=true;h.pulse(0);h.pulse(0);h.select();h.input();assertFalse(h.session.inGame());assertEquals(1,h.scene.world().activeLeases());
            h.scene.world().set(a,false,0,null);h.scene.world().set(a,true,0x654321,3.0);h.pulse(0);h.input();assertTrue(h.session.inGame());
            var repairs=h.wire.frames.stream().filter(f->f.id()==33).toList();assertEquals(2,repairs.size());assertEquals(0x654321,((SimplePackets.BlockBuildColored)PacketCodec.decode(repairs.getLast().body())).color());
            int reveal=h.wire.ids().indexOf(49),repair=h.wire.ids().indexOf(33);assertTrue(repair<reveal);assertEquals(0,h.scene.world().journalSize());
        }
    }
    @Test void outgoingBackpressureAndBoundedReplayNeverOpenGameplayEarly() {
        try(var h=new Flow(FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO,"packetBudget",1,"replayBudget",1)))) {
            h.wire.accepted=false;h.ticket(new byte[0],0);h.pulse(0);assertEquals(JoinSession.State.INITIAL,h.session.state());assertEquals(1,h.slots.occupied());h.wire.accepted=true;
            h.pulse(0);h.client(new SimplePackets.MapDataValidation(h.scene.world().sourceCrc()));
            h.pulse(0);h.pulse(100);for(int i=0;i<8;i++) h.pulse(0);
            assertEquals(JoinSession.State.SELECT,h.session.state());h.select();h.pulse(0);assertEquals(JoinSession.State.INPUT,h.session.state());
            var a=new MutableWorld.Cell(1,2,3);h.scene.world().set(a,true,0x123456,9.0);h.input();h.pulse(0);h.wire.blockedId=7;
            h.input();assertFalse(h.session.inGame());h.input();assertFalse(h.session.inGame());h.wire.blockedId=-1;h.game();assertEquals(1,h.scene.reveals);
        }
    }
    @Test void pressureBeforeCompressionDoesNotPinUnsubmittedWorldSnapshots() {
        try(var h=new Flow()) {
            h.compression.capacity=false;h.ticket(new byte[0],0);h.pulse(0);h.validate();assertEquals(JoinSession.State.MAP,h.session.state());assertEquals(0,h.scene.world().activeLeases());
            h.scene.world().set(new MutableWorld.Cell(1,2,3),true,0,null);h.compression.capacity=true;h.pulse(0);assertEquals(1,h.compression.jobs.getFirst().snapshot.revision());
        }
    }
    @Test void sceneReplacementKeepsTicketAndPasswordButRequiresFreshValidationWithoutPacket110() {
        try(var h=new Flow();var replacement=new FixtureScene(2,64)) {
            h.ticket(new byte[]{1,2,3},0);h.pulse(0);h.validate();assertEquals(JoinSession.State.SELECT,h.session.state());
            h.session.replaceScene(replacement);h.client(new SimplePackets.MapDataValidation(replacement.world().sourceCrc()));h.pulse(0);assertEquals(52,h.wire.frames.getLast().id());
            h.pulse(1250);h.pulse(0);assertEquals(JoinSession.State.MAP,h.session.state());h.client(new SimplePackets.MapDataValidation(replacement.world().sourceCrc()));h.pulse(0);h.pulse(100);h.pulse(0);assertEquals(JoinSession.State.SELECT,h.session.state());
            assertEquals(1,h.slots.occupied());assertEquals(0,h.scene.world().activeLeases());
        }
    }
    @Test void loadingSceneReplacementCancelsWorkerAndReleasesLeaseAndSlot() {
        try(var h=new Flow();var replacement=new FixtureScene(2,64)) {
            h.compression.ready=false;h.ticket(new byte[0],0);h.pulse(0);h.validate();h.session.replaceScene(replacement);
            assertEquals(18,h.wire.reason);assertTrue(h.compression.jobs.getFirst().cancelled);assertEquals(0,h.slots.occupied());assertEquals(0,h.scene.world().activeLeases());
        }
    }
    @Test void transportClockBypassesReliableQueueAndUnknownPacketsDoNotResetJoin() {
        try(var h=new Flow()) {
            h.client(new SimplePackets.ClockSync(12345,0));var frame=h.wire.frames.getFirst();assertEquals(Delivery.UNSEQUENCED,frame.delivery());assertEquals(12345,((SimplePackets.ClockSync)PacketCodec.decode(frame.body())).client_time());
            h.receive(new byte[]{(byte)200,7,8,9},false);assertEquals(JoinSession.State.TICKET,h.session.state());h.load();
        }
    }
    @Test void capabilityProfilesMatchSourceAndNeverEnterXorKey() throws IOException {
        for(String row:GoldenPacketTest.lines("session.tsv")) {
            if(!row.startsWith("Flight/")) continue;var parts=row.split("\t");int cap=Integer.parseInt(parts[0].split("/")[1]);byte[] profile=HexFormat.of().parseHex(parts[1]);
            try(var h=new Flow()) {
                h.ticket(new byte[]{3,19},cap);h.pulse(0);byte[] body=h.wire.frames.getFirst().body();assertArrayEquals(profile,Arrays.copyOfRange(body,body.length-profile.length,body.length));
                assertInstanceOf(InitialInfo.class,PacketCodec.decode(Arrays.copyOf(body,body.length-profile.length)));h.validate();assertEquals(JoinSession.State.SELECT,h.session.state());h.select();assertEquals(cap,h.scene.capabilities.get(0));
            }
        }
    }
    @Test void rosterRespawnDuringBlockedRevealIsReconciledBeforeAdmission() {
        try(var h=new Flow()) {
            h.load();h.select();var request=new SimplePackets.NewPlayerConnection(1,5,0,0,"other");h.scene.admit(1,request,null);
            h.input();h.wire.blockedId=28;h.pulse(0);assertFalse(h.session.inGame());h.scene.admit(1,request,null);
            h.wire.blockedId=-1;h.pulse(0);h.game();
            var creates=h.wire.frames.stream().filter(f->f.id()==28).map(f->(PlayerPackets.CreatePlayer)PacketCodec.decode(f.body())).filter(p->p.playerId()==1).toList();
            assertEquals(2,creates.size());assertFalse(h.wire.ids().contains(14));
        }
    }
    @Test void boundedControlQueueRefusesRosterBeforeSendingPartialState() {
        try(var h=new Flow(FixtureScene.copy(JoinSettings.defaults(),Map.of("controlCapacity",4,"mapMode",MapSyncTransfer.Mode.AUTO)))) {
            for(int id=10;id<13;id++) h.scene.admit(id,new SimplePackets.NewPlayerConnection(1,5,0,0,"other"),null);
            h.ticket(new byte[0],0);h.pulse(0);h.validate();assertEquals(13,h.wire.reason);assertEquals(0,h.slots.occupied());assertFalse(h.wire.ids().contains(45));
        }
    }
    @Test void mutationOverflowFailsClosedAndCleansEveryReservation() {
        try(var h=new Flow()) {
            h.load();h.select();for(int i=0;i<65;i++) h.scene.world().set(new MutableWorld.Cell(i,1,1),true,0,null);
            h.input();assertEquals(13,h.wire.reason);assertEquals(0,h.slots.occupied());assertEquals(0,h.scene.world().activeLeases());assertFalse(h.session.inGame());
        }
    }
    @Test void admissionCallbackFailureRollsBackPlayerAndCapabilityGatePrecedesMetadata() {
        try(var h=new Flow()) {
            h.load();h.scene.failAdmission=true;h.client(new SimplePackets.NewPlayerConnection(1,5,0,0,"Source"));
            assertEquals(13,h.wire.reason);assertTrue(h.scene.players.isEmpty());assertEquals(0,h.slots.occupied());assertEquals(1,h.session.metrics().domainFailures());
        }
        try(var h=new Flow()) {h.scene.requiredCapability=3;h.ticket(new byte[0],2);assertEquals(10,h.wire.reason);assertTrue(h.wire.frames.isEmpty());assertEquals(0,h.slots.occupied());}
    }
    @Test void reloadValidationTimeoutUsesMatchEndedReasonFromReferenceLifecycle() {
        try(var h=new Flow();var replacement=new FixtureScene(2,64)) {
            h.load();h.session.replaceScene(replacement);h.pulse(0);h.pulse(1250);h.pulse(0);assertEquals(JoinSession.State.MAP,h.session.state());
            h.pulse(5000);assertEquals(18,h.wire.reason);assertEquals(0,h.slots.occupied());
        }
    }
    @Test void reentrantSceneChangeCannotPublishOldAdmissionPackets() {
        try(var h=new Flow();var replacement=new FixtureScene(2,64)) {
            h.load();h.scene.onAdmission=()->h.session.replaceScene(replacement);
            h.client(new SimplePackets.NewPlayerConnection(1,5,0,0,"Source"));
            assertEquals(JoinSession.State.RETIRING,h.session.state());assertFalse(h.wire.ids().contains(28));assertEquals(0,h.slots.occupied());assertTrue(h.scene.players.isEmpty());
        }
    }
    @Test void disconnectionReleasesAdmission() {
        try(var h=new Flow()) {h.load();h.select();h.session.close();assertEquals(List.of(0),h.scene.departed);assertEquals(0,h.slots.occupied());assertEquals(0,h.scene.world().activeLeases());assertFalse(h.session.inGame());}
    }
}
