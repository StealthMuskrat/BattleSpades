// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;

/** Scene-owned minimap and camera presentation; recipient capabilities never authorize gameplay. */
public final class HudState implements AutoCloseable {
    public static final int BILLBOARD_ID_BASE=0x7000;
    public static final boolean PROGRESS_BAR_SUPPORTED=false;
    public static final Set<String> ICONS=AchievementCatalog.icons();
    public interface Peer {Object token();boolean inGame();boolean knows(int entityId);boolean send(GamePacket packet);}
    private record Marker(MinimapBillboard packet,Set<Object> audience){}
    private boolean closed;private final Thread owner=Thread.currentThread();private final Supplier<List<Peer>> peers;private final LinkedHashMap<Integer,Marker> markers=new LinkedHashMap<>();
    public HudState(Supplier<List<Peer>> peers){this.peers=Objects.requireNonNull(peers);}
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("HUD outside owner");}
    private List<Peer> resolve(Collection<? extends Peer> recipients){var out=new ArrayList<Peer>();var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(var peer:recipients==null?peers.get():recipients)if(peer!=null&&peer.inGame()&&seen.add(peer.token()))out.add(peer);return out;}
    private static Set<Object> audience(Collection<? extends Peer> peers){var out=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());for(var peer:peers)out.add(peer.token());return Collections.unmodifiableSet(out);}
    private static Vector position(Vector at){if(at==null||!Double.isFinite(at.x())||!Double.isFinite(at.y())||!Double.isFinite(at.z()))return null;double max=32767./64;return new Vector(Math.max(-max,Math.min(max,at.x())),Math.max(-max,Math.min(max,at.y())),Math.max(-max,Math.min(max,at.z())));}
    private static boolean send(Peer peer,GamePacket packet){try{return peer.send(packet);}catch(RuntimeException failure){System.getLogger("hud").log(System.Logger.Level.DEBUG,"HUD send failed",failure);return false;}}
    public boolean add(int id,int key,String icon,Vector at,boolean tracking,Integer color,Collection<? extends Peer> recipients){owned();at=position(at);if(id<Short.MIN_VALUE||id>Short.MAX_VALUE||key<0||key>255||!ICONS.contains(icon==null?"":icon)||at==null)return false;var targets=resolve(recipients);var packet=new MinimapBillboard(id,key,color==null?0xffffff:color&0xffffff,(float)at.x(),(float)at.y(),(float)at.z(),icon,tracking);var marker=new Marker(packet,recipients==null?null:audience(targets));markers.put(id,marker);for(var peer:targets)if(!tracking||peer.knows(id))send(peer,packet);return true;}
    public boolean remove(int id,Collection<? extends Peer> recipients){owned();if(id<Short.MIN_VALUE||id>Short.MAX_VALUE)return false;var targets=resolve(recipients);var old=markers.get(id);if(recipients==null)markers.remove(id);else if(old!=null){var keep=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());keep.addAll(old.audience==null?audience(resolve(null)):old.audience);for(var peer:targets)keep.remove(peer.token());if(keep.isEmpty())markers.remove(id);else markers.put(id,new Marker(old.packet,Collections.unmodifiableSet(keep)));}for(var peer:targets)send(peer,new SimplePackets.MinimapBillboardClear(id));return true;}
    /** Collected after CreateEntity packets; replay can account for those preceding packets before queue admission. */
    public List<GamePacket> reveal(Peer peer,IntPredicate known){owned();var out=new ArrayList<GamePacket>();for(var marker:markers.values())if((marker.audience==null||marker.audience.contains(peer.token()))&&(!marker.packet.tracking()||known.test(marker.packet.entityId())))out.add(marker.packet);return List.copyOf(out);}
    public void forget(Object token){owned();for(var entry:new ArrayList<>(markers.entrySet())){var marker=entry.getValue();if(marker.audience==null||!marker.audience.contains(token))continue;var keep=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());keep.addAll(marker.audience);keep.remove(token);if(keep.isEmpty())markers.remove(entry.getKey());else markers.put(entry.getKey(),new Marker(marker.packet,Collections.unmodifiableSet(keep)));}}
    public void clear(){owned();for(var id:new ArrayList<>(markers.keySet()))remove(id,null);}
    public boolean focus(Vector at,Collection<? extends Peer> recipients){owned();at=position(at);if(at==null)return false;var packet=new PoiFocus((float)at.x(),(float)at.y(),(float)at.z());for(var peer:resolve(recipients))send(peer,packet);return true;}
    @Override public void close(){owned();markers.clear();closed=true;}
    public boolean progress(double amount,double rate){owned();return false;}public boolean clearProgress(){owned();return false;}
}
