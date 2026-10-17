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
    public int occupiedSlots() {owned();return slots.occupied();}
    public boolean inGame(int id) {owned();if(closing)return false;for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return session.inGame();return false;}
    public void service(long nowMillis,int loopCount) throws IOException {
        owned();if(nowMillis<0 || nowMillis<lastNow) throw new IllegalArgumentException("Application clock moved backwards");lastNow=nowMillis;
        transport.service(nowMillis);
        for(int i=0;i<eventBudget;i++) {
            var event=transport.poll().orElse(null);if(event==null) break;var peer=event.peer();var key=new PeerKey(peer.id(),peer.generation());
            switch(event.type()) {
                case CONNECT -> sessions.put(key,new JoinSession(peer.address(),new JoinSession.WireSink() {
                    @Override public boolean send(SessionFrame frame) {return peer.send(frame.encode(),frame.delivery());}
                    @Override public void disconnect(int reason) {peer.disconnect(reason);}
                    @Override public Double roundTripMillis(){return (double)peer.metrics().roundTripTime();}
                    @Override public net.battlespades.player.ActionClock.Link actionLink(){var metrics=peer.metrics();return new net.battlespades.player.ActionClock.Link(metrics.roundTripTime(),metrics.roundTripVariance());}
                    @Override public boolean releaseTracking(){return true;}
                    @Override public JoinSession.Sent sendTracked(SessionFrame frame){var receipt=peer.sendTrackedReliable(frame.encode());return new JoinSession.Sent(receipt.isPresent(),receipt.orElse(null));}
                    @Override public int unfragmentedWireLimit(){return Math.max(64,peer.mtu()-28);}
                    @Override public boolean snapshotUnsequenced(){return true;}
                    @Override public void flushSnapshots(){try{transport.flush(Math.max(0,lastNow));}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}
                },scene,slots,settings,passwords,compression));
                case RECEIVE -> {var session=sessions.get(key);if(session!=null) session.receive(event.data(),nowMillis,loopCount);}
                case DISCONNECT -> {var session=sessions.remove(key);if(session!=null) session.close();}
            }
        }
        for(var session:sessions.values()) session.pulse(nowMillis,loopCount);
    }
    public int broadcast(GamePacket packet,Delivery delivery) {owned();if(closing)return 0;int accepted=0;for(var session:sessions.values()) if(session.gameplay(packet,delivery)) accepted++;return accepted;}
    /** Source remote firearm feedback excludes the predicting owner while retaining sequenced-unreliable delivery. */
    public int broadcastExcept(GamePacket packet,Delivery delivery,int excludedPlayer){owned();if(closing)return 0;int accepted=0;for(var session:sessions.values())if(session.playerId().orElse(-1)!=excludedPlayer&&session.gameplay(packet,delivery))accepted++;return accepted;}
    public List<WorldUpdateLink> snapshotLinks(){owned();return closing?List.of():sessions.values().stream().map(session->(WorldUpdateLink)session).toList();}
    public boolean sendPlayer(int id,GamePacket packet) {owned();if(closing)return false;for(var session:sessions.values())if(session.playerId().orElse(-1)==id)return session.direct(packet);return false;}
    public void replaceScene(JoinScene replacement) {owned();if(replacement==scene) throw new IllegalArgumentException("Scene must change");for(var session:sessions.values()) session.replaceScene(replacement);scene=replacement;}
    @Override public void close() throws IOException {
        if(Thread.currentThread()!=owner) throw new IllegalStateException("Application host closed outside owner");if(closed||closing) return;
        closing=true;try {for(var session:sessions.values()) session.close();sessions.clear();compression.close();} finally {closed=true;transport.close();}
    }
}
