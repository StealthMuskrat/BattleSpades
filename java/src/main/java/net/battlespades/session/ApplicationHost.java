// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.battlespades.protocol.GamePacket;
import net.battlespades.replication.WorldUpdateLink;
import net.battlespades.transport.enet.*;
import net.battlespades.world.SnapshotCompression;

/** Actual ENet-to-loader composition. All domain callbacks run on this owner's event-loop thread. */
public final class ApplicationHost implements AutoCloseable {
    private record PeerKey(int id,long generation) {}
    private final Thread owner=Thread.currentThread();
    private final EnetHost transport;
    private final Map<PeerKey,JoinSession> sessions=new LinkedHashMap<>();
    private final PlayerSlots slots;
    private final JoinSettings settings;
    private final JoinPasswordGate passwords;
    private final SnapshotCompression compression;
    private final int eventBudget;
    private JoinScene scene;
    private net.battlespades.match.MatchTransitions<?> matchTransitions;
    /** Bind the real retained transition owner once, after scene attachment and before connections. */
    public void matchTransitions(net.battlespades.match.MatchTransitions<?> service){owned();if(matchTransitions!=null||!sessions.isEmpty())throw new IllegalStateException("Match transitions bind once before connections");matchTransitions=java.util.Objects.requireNonNull(service);}
    public java.util.Optional<net.battlespades.match.MatchTransitions<?>> matchTransitions(){owned();return java.util.Optional.ofNullable(matchTransitions);}
    private HostedRoundResults results;
    public void results(HostedRoundResults service){owned();if(results!=null||!sessions.isEmpty())throw new IllegalStateException("Results bind once before connections");results=java.util.Objects.requireNonNull(service);}
    public java.util.Optional<HostedRoundResults> results(){owned();return java.util.Optional.ofNullable(results);}
    public java.util.concurrent.CompletionStage<Void> shutdownResults(){owned();return results==null?java.util.concurrent.CompletableFuture.completedFuture(null):results.shutdown();}
    private HostedAdministration administration;
    public void administration(HostedAdministration service){owned();if(administration!=null||!sessions.isEmpty())throw new IllegalStateException("Administration binds once before connections");administration=java.util.Objects.requireNonNull(service);}
    public java.util.Optional<HostedAdministration> administration(){owned();return java.util.Optional.ofNullable(administration);}
    private java.util.function.Function<String,Integer> admissionGate;
    public void admissionGate(java.util.function.Function<String,Integer> gate){owned();if(admissionGate!=null)throw new IllegalStateException("Admission gate already bound");admissionGate=java.util.Objects.requireNonNull(gate);}
    public java.util.Optional<String> addressHost(int id){owned();for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return java.util.Optional.of(session.addressHost());return java.util.Optional.empty();}
    public void disconnectPlayer(int id,int reason){owned();for(var session:sessions.values())if(session.playerId().orElse(-1)==id){session.fail(reason);return;}}
    private record QueuedSession(JoinSession session,PeerKey key,long epoch,long identity) {}
    private final Map<JoinSession,QueuedSession> queuedSessions=new java.util.IdentityHashMap<>();
    private net.battlespades.runtime.IngamePackets<QueuedSession> packets;
    private java.util.concurrent.Executor resumePackets;
    private int packetLoop;
    /** Opt into the source tick-start queue before joining players; network service continues while handlers await. */
    public void serialPackets(net.battlespades.runtime.IngamePackets.Settings limits,java.util.concurrent.Executor resume,java.util.function.Consumer<Throwable> failure){
        owned();if(packets!=null||!sessions.isEmpty())throw new IllegalStateException("Packet queue must bind before connections");
        resumePackets=java.util.Objects.requireNonNull(resume);
        packets=new net.battlespades.runtime.IngamePackets<>(limits,q->sessions.get(q.key())==q.session()&&q.session().inGame()&&q.session().metrics().generation()==q.epoch()&&q.session().playerIdentity()==q.identity(),
            (q,data)->q.session().receiveAsync(data,lastNow,packetLoop,resumePackets),failure,resume);
    }
    public int queuedPackets(){owned();return packets==null?0:packets.size();}
    public long droppedPackets(){owned();return packets==null?0:packets.dropped();}
    public java.util.concurrent.CompletionStage<Void> drainPackets(int loop){owned();if(packets==null)throw new IllegalStateException("Serial packets not bound");if(packets.draining())throw new IllegalStateException("Packet drain already suspended");packetLoop=loop;return packets.drain();}

    private boolean closed,closing;
    private long lastNow=-1;
    public ApplicationHost(InetSocketAddress bind,EnetSettings transportSettings,int maxPlayers,
            JoinSettings settings,JoinScene scene,SnapshotCompression compression) throws IOException {
        this.settings=settings;this.scene=scene;this.compression=compression;slots=new PlayerSlots(maxPlayers);passwords=new JoinPasswordGate(settings.password());
        eventBudget=(int)Math.min(8192L,(long)transportSettings.receiveBudget()*32);transport=new EnetHost(bind,transportSettings);
    }
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Application host accessed outside owner");if(closed) throw new IllegalStateException("Application host closed");}
    public SocketAddress address() throws IOException {owned();return transport.address();}
    public List<JoinSession.Metrics> sessions() {owned();return sessions.values().stream().map(JoinSession::metrics).toList();}
    public int playerLimit(){owned();return slots.limit();}
    public int occupiedSlots() {owned();return slots.occupied();}
    public boolean inGame(int id) {owned();if(closing)return false;for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return session.inGame();return false;}
    public void service(long nowMillis,int loopCount) throws IOException {
        owned();if(nowMillis<0 || nowMillis<lastNow) throw new IllegalArgumentException("Application clock moved backwards");lastNow=nowMillis;
        transport.service(nowMillis);
        for(int i=0;i<eventBudget;i++) {
            var event=transport.poll().orElse(null);if(event==null) break;var peer=event.peer();var key=new PeerKey(peer.id(),peer.generation());
            switch(event.type()) {
                case CONNECT -> {String address=JoinSession.addressHost(peer.address());Integer denied=administration==null?null:administration.admissionReason(address);if(denied==null&&admissionGate!=null)denied=admissionGate.apply(address);if(denied!=null){peer.disconnect(denied);break;}sessions.put(key,new JoinSession(peer.address(),new JoinSession.WireSink() {
                    @Override public boolean send(SessionFrame frame) {return peer.send(frame.encode(),frame.delivery());}
                    @Override public void disconnect(int reason) {peer.disconnect(reason);}
                    @Override public Double roundTripMillis(){return (double)peer.metrics().roundTripTime();}
                    @Override public net.battlespades.player.ActionClock.Link actionLink(){var metrics=peer.metrics();return new net.battlespades.player.ActionClock.Link(metrics.roundTripTime(),metrics.roundTripVariance());}
                    @Override public boolean releaseTracking(){return true;}
                    @Override public JoinSession.Sent sendTracked(SessionFrame frame){var receipt=peer.sendTrackedReliable(frame.encode());return new JoinSession.Sent(receipt.isPresent(),receipt.orElse(null));}
                    @Override public int unfragmentedWireLimit(){return Math.max(64,peer.mtu()-28);}
                    @Override public boolean snapshotUnsequenced(){return true;}
                    @Override public void flushSnapshots(){try{transport.flush(Math.max(0,lastNow));}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}
                },scene,slots,settings,passwords,compression));}
                case RECEIVE -> {var session=sessions.get(key);if(session!=null){
                    if(packets!=null&&session.inGame()){
                        var queued=queuedSessions.computeIfAbsent(session,s->new QueuedSession(s,key,s.metrics().generation(),s.playerIdentity()));packets.offer(queued,event.data());
                    }else session.receive(event.data(),nowMillis,loopCount);
                }}
                case DISCONNECT -> {var session=sessions.remove(key);if(session!=null){var queued=queuedSessions.remove(session);if(packets!=null&&queued!=null)packets.forget(queued);session.close();}}
            }
        }
        for(var session:sessions.values()) session.pulse(nowMillis,loopCount);if(administration!=null)administration.prune();if(results!=null)results.pulse(nowMillis/1000.);
    }
    public int broadcast(GamePacket packet,Delivery delivery) {owned();if(closing)return 0;int accepted=0;for(var session:sessions.values()) if(session.gameplay(packet,delivery)) accepted++;return accepted;}
    /** Source known_player_lives gate; reliable queue acceptance establishes or forgets visibility. */
    public int broadcastKnown(GamePacket packet,Delivery delivery,int playerId){owned();if(closing)return 0;int accepted=0;for(var session:sessions.values())if(session.gameplayKnown(packet,delivery,playerId))accepted++;return accepted;}
    public int broadcastKnownExcept(GamePacket packet,Delivery delivery,int playerId,int excludedPlayer){owned();if(closing)return 0;int accepted=0;for(var session:sessions.values())if(session.playerId().orElse(-1)!=excludedPlayer&&session.gameplayKnown(packet,delivery,playerId))accepted++;return accepted;}
    public java.util.Optional<net.battlespades.match.Scoreboard.Peer> scorePeer(int playerId){
        owned();if(closing)return java.util.Optional.empty();
        for(var session:sessions.values())if(session.playerId().orElse(-1)==playerId){
            long epoch=session.metrics().generation(),identity=session.playerIdentity();
            return java.util.Optional.of(new net.battlespades.match.Scoreboard.Peer(){
                private boolean current(){owned();return !closing&&sessions.containsValue(session)&&session.metrics().generation()==epoch&&session.playerIdentity()==identity;}
                public boolean inGame(){return current()&&session.inGame();}
                public java.util.Optional<java.util.Set<Integer>> knownPlayers(){return java.util.Optional.of(current()?session.knownPlayers():java.util.Set.of());}
                public void send(GamePacket packet){if(current())session.direct(packet);}
            });
        }return java.util.Optional.empty();
    }
    public List<net.battlespades.match.Scoreboard.Peer> scorePeers(){owned();return sessions.values().stream().filter(s->s.playerId().isPresent()).flatMap(s->scorePeer(s.playerId().orElseThrow()).stream()).toList();}
    /** Source remote firearm feedback excludes the predicting owner while retaining sequenced-unreliable delivery. */
    public int broadcastExcept(GamePacket packet,Delivery delivery,int excludedPlayer){owned();if(closing)return 0;int accepted=0;for(var session:sessions.values())if(session.playerId().orElse(-1)!=excludedPlayer&&session.gameplay(packet,delivery))accepted++;return accepted;}
    /** Source per-GameScene create knowledge follows reliable acceptance, including ordered backpressure. */
    public java.util.Set<Integer> knownEntities(int playerId){owned();if(closing)return java.util.Set.of();for(var session:sessions.values())if(session.playerId().orElse(-1)==playerId)return session.knownEntities();return java.util.Set.of();}
    public int broadcastCreateEntity(net.battlespades.entities.MapEntity entity){
        owned();if(closing||!entity.alive||!entity.wireVisible)return 0;int accepted=0;var packet=new net.battlespades.protocol.CreateEntity(entity.toWireEntity());
        for(var session:sessions.values())if(!session.knownEntities().contains(entity.id())&&session.gameplay(packet,Delivery.RELIABLE))accepted++;return accepted;
    }
    public int broadcastDestroyEntity(int id){
        owned();if(closing)return 0;int accepted=0;var packet=new net.battlespades.protocol.SimplePackets.DestroyEntity(id);
        for(var session:sessions.values())if(session.knownEntities().contains(id&65535)&&session.gameplay(packet,Delivery.RELIABLE))accepted++;return accepted;
    }
    public List<WorldUpdateLink> snapshotLinks(){owned();return closing?List.of():sessions.values().stream().map(session->(WorldUpdateLink)session).toList();}
    /** Reliable controls retain their loading queue; cosmetic unreliable cues require a live scene. */
    public boolean sendPlayer(int id,GamePacket packet,Delivery delivery){owned();if(delivery==Delivery.RELIABLE)return sendPlayer(id,packet);if(closing)return false;for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return session.gameplay(packet,delivery);return false;}
    public boolean sendPlayer(int id,GamePacket packet) {owned();if(closing)return false;for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return session.direct(packet);return false;}
    public List<JoinSession> transitionSessions(){owned();return List.copyOf(sessions.values());}
    public JoinScene transitionScene(){owned();return scene;}
    public void discardTransitionPackets(){owned();if(packets!=null)packets.clear();queuedSessions.clear();}
    /** Commit the next scene after the transition service has gated/detached its snapshot of peers. */
    public void installTransitionScene(JoinScene replacement){owned();scene=java.util.Objects.requireNonNull(replacement);discardTransitionPackets();}
    public void flushTransition(){owned();try{transport.flush(Math.max(0,lastNow));}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}
    public void replaceScene(JoinScene replacement) {owned();if(replacement==scene) throw new IllegalArgumentException("Scene must change");if(packets!=null)packets.clear();queuedSessions.clear();for(var session:sessions.values()) session.replaceScene(replacement);scene=replacement;}
    @Override public void close() throws IOException {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("Application host closed outside owner");if(closed||closing) return;
        if(results!=null&&!results.stopped())throw new IllegalStateException("Await shutdownResults before closing the host");closing=true;if(packets!=null)packets.clear();queuedSessions.clear();try {for(var session:sessions.values()) session.close();sessions.clear();if(administration!=null)administration.clear();compression.close();} finally {closed=true;transport.close();}
    }
}
