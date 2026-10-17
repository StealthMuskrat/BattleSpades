// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.replication;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;

/** Owner-thread WorldUpdate cadence and recipient routing, ported from server/replication.py.
 * Entity/corpse, tool and effect authorities are explicit inputs, never inferred from client flags.
 */
public final class ReplicationService {
    public record Settings(boolean enabled,int interval,int selfInterval,int airborneInterval,int retailAirborneInterval,
        boolean includeSelf,int loopOffset,boolean split,boolean reorderGuard) {
        public Settings {interval=Math.max(1,interval);selfInterval=Math.max(interval,selfInterval);}
        public static Settings from(org.tomlj.TomlTable raw){
            Object delivery=raw.get("network.worldupdate_delivery");String mode=delivery==null?"split":delivery instanceof String s?s.toLowerCase(Locale.ROOT):"";
            if(!mode.equals("split")&&!mode.equals("sequenced"))throw new IllegalArgumentException("network.worldupdate_delivery must be split or sequenced");
            return new Settings(flag(raw,"debug.broadcast_world_updates",true),integer(raw,"debug.worldupdate_broadcast_interval",2),
                integer(raw,"debug.worldupdate_self_row_interval",2),Math.max(1,integer(raw,"debug.worldupdate_airborne_self_row_interval",6)),
                Math.max(1,integer(raw,"debug.worldupdate_retail_airborne_self_row_interval",2)),flag(raw,"debug.worldupdate_include_self",true),
                integer(raw,"debug.worldupdate_loop_offset",0),mode.equals("split"),flag(raw,"network.worldupdate_reorder_guard",true));
        }
        private static int integer(org.tomlj.TomlTable raw,String key,int fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Long n)return Math.toIntExact(n);throw new IllegalArgumentException(key+" must be an integer");}
        private static boolean flag(org.tomlj.TomlTable raw,String key,boolean fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
    }
    public record State(int id,String name,boolean bot,boolean visible,Integer applied,boolean synthesized,
        Boolean flightCapable,boolean airborne,Integer jumpLabel,Integer newestInput,double rtt,
        boolean pack,boolean packPhysics,int defer,int tail,boolean canopy,boolean canopyPhysics,boolean canopyPending,
        int reorderSpread,WorldUpdate.PlayerRow row,boolean toolAuthorized) {}
    public interface Player {
        State state(int serverLoop,double now);
        void acknowledge(int stamp);
        /** Queue acceptance is the causal event; this is not a client application ACK. */
        void ownerRowQueued(int stamp,int serverLoop,boolean transition);
    }
    public interface World {
        List<? extends Player> players();
        List<? extends WorldUpdateLink> connections();
        List<EntityRecord> entities();
        List<WorldUpdate.TurretRow> turrets();
        void delivered(int packets,long bytes);
    }
    private record Recovery(int launch,long through,long expires) {}
    private record ObserverDelivery(boolean unsequenced,int spacing) {}
    private record Group(boolean self,boolean transition,Integer excluded) {}
    private final Thread owner=Thread.currentThread();
    private final World world;
    private final Settings settings;
    private Integer bucket;
    private final Map<Integer,Integer> lastSelf=new HashMap<>(),ack=new HashMap<>();
    private final Map<Integer,Boolean> lastPack=new HashMap<>(),lastCanopy=new HashMap<>();
    private final Map<Integer,Recovery> recovery=new HashMap<>();
    public ReplicationService(World world,Settings settings){this.world=Objects.requireNonNull(world);this.settings=Objects.requireNonNull(settings);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Replication outside owner");}
    public void forgetPlayer(int id){owned();lastSelf.remove(id);lastPack.remove(id);lastCanopy.remove(id);recovery.remove(id);ack.remove(id);}
    private static long crc(String value){var crc=new CRC32();crc.update(value.getBytes(StandardCharsets.UTF_8));return crc.getValue();}
    public static int ping(State s,int loop){
        if(!s.bot)return !Double.isFinite(s.rtt)||s.rtt<=0?0:(int)Math.min(999,Math.rint(s.rtt));
        String seed=s.name+":"+s.id;long base=24+crc(seed)%49,drift=crc(seed+"#"+(Math.max(0,loop)/120));
        return (int)Math.max(1,Math.min(999,base+drift%7-3));
    }
    private WorldUpdate.PlayerRow row(State s,int loop,Integer local){
        var r=s.row;boolean mounted=r.tool()==15&&s.toolAuthorized;
        int tool=s.toolAuthorized&&!mounted?r.tool():255,action=r.action()&255;
        if(!s.toolAuthorized)action&=~0xd3;
        if(Objects.equals(local,s.id))tool=255;
        return new WorldUpdate.PlayerRow(s.id,r.position(),r.orientation(),r.velocity(),ping(s,loop),ack.getOrDefault(s.id,r.pong()),
            r.health(),r.input(),action,r.state(),tool,r.pickup(),r.jetpackFuel(),r.protectionTimer(),r.deploymentYaw());
    }
    private byte[] build(Collection<State> states,int loop,Integer excluded,Integer local){
        var rows=new ArrayList<WorldUpdate.PlayerRow>();
        for(var s:states)if(s.visible&&!Objects.equals(s.id,excluded))rows.add(row(s,Math.max(0,loop),local));
        return new WorldUpdate(Math.max(0,loop),rows,world.entities(),world.turrets()).encode();
    }
    /** Does not advance cadence or fabricate owner-history sends. */
    public byte[] build(int loop,double now,Integer excluded,Integer local){
        owned();return build(world.players().stream().map(p->p.state(loop,now)).toList(),loop,excluded,local);
    }
    private boolean urgent(State s){return settings.includeSelf&&s!=null&&s.applied!=null&&
        (s.pack!=lastPack.getOrDefault(s.id,false)||s.canopy!=lastCanopy.getOrDefault(s.id,false));}
    private static boolean recoveryAllowed(State s){return Boolean.FALSE.equals(s.flightCapable)&&!s.bot&&!s.pack&&!s.packPhysics&&s.defer==0&&s.tail==0&&!s.canopy&&!s.canopyPhysics&&!s.canopyPending;}
    private boolean ownerDue(State s,Set<Integer> urgent,int loop){
        if(!settings.includeSelf||s==null||s.applied==null||s.synthesized)return false;
        if(urgent.contains(s.id))return true;
        var r=recovery.get(s.id);
        if(r!=null&&s.airborne&&recoveryAllowed(s)&&r.launch<=s.applied&&s.applied<=r.through&&loop<r.expires)return false;
        int interval=settings.selfInterval;
        if(s.airborne)interval=Math.max(interval,Boolean.TRUE.equals(s.flightCapable)?settings.airborneInterval:settings.retailAirborneInterval);
        Integer last=lastSelf.get(s.id);return last==null||(long)loop-last>=interval;
    }
    private void settle(State s,int loop){lastSelf.put(s.id,loop);lastPack.put(s.id,s.pack);lastCanopy.put(s.id,s.canopy);}
    private void record(Player player,State s,int stamp,int loop,boolean transition){
        if(!recoveryAllowed(s))recovery.remove(s.id);
        else if(s.jumpLabel!=null){
            var old=recovery.get(s.id);
            if(!s.airborne)recovery.put(s.id,new Recovery(s.jumpLabel,(long)stamp-1,loop));
            else if(old==null||s.jumpLabel>old.launch){
                long newest=Math.max(stamp,s.newestInput==null?stamp:s.newestInput);
                double rtt=s.bot||!Double.isFinite(s.rtt)||s.rtt<0?0:Math.min(1000,s.rtt);
                long through=newest+(long)Math.ceil(rtt*60/1000)+3;
                recovery.put(s.id,new Recovery(s.jumpLabel,through,(long)loop+Math.min(120,Math.max(15,2*(through-stamp)))));
            }
        }
        settle(s,loop);player.ownerRowQueued(stamp,loop,transition);
    }
    private static void flush(WorldUpdateLink link){try{link.flushSnapshots();}catch(UncheckedIOException|IllegalStateException e){/* source tolerates a disconnect between queue and flush */}}
    private ObserverDelivery observerDelivery(WorldUpdateLink link,State s,int loop){
        boolean unsequenced=true;int spacing=settings.interval;
        if(settings.reorderGuard&&s!=null&&s.reorderSpread>=2){spacing=Math.max(spacing,s.reorderSpread+1);if(spacing>6)unsequenced=false;}
        var observer=link.observerState();
        if(unsequenced&&observer.sequenced()&&observer.orderedBarrier()!=null&&link.reliableUnackedThrough(observer.orderedBarrier(),loop))return null;
        if(!unsequenced&&observer.sequenced())spacing=settings.interval;
        return new ObserverDelivery(unsequenced,spacing);
    }
    private boolean observerDue(WorldUpdateLink link,int spacing,int loop){
        Integer last=link.observerState().loop();long elapsed=last==null?-1:(long)loop-last;
        if(spacing>settings.interval&&last!=null&&elapsed>=0&&elapsed<spacing)return false;
        link.observerState().loop(loop);return true;
    }
    public void broadcast(int loop,double now){
        owned();var connections=world.connections();if(connections.isEmpty()||!settings.enabled)return;
        var links=connections.stream().filter(WorldUpdateLink::inGame).toList();
        var players=new LinkedHashMap<Integer,Player>();var states=new LinkedHashMap<Integer,State>();
        for(var player:world.players()){var s=player.state(loop,now);if(states.put(s.id,s)!=null)throw new IllegalArgumentException("Duplicate player id");players.put(s.id,player);}
        var urgent=new LinkedHashSet<Integer>();for(var link:links){var s=states.get(link.localPlayerId());if(urgent(s))urgent.add(s.id);}
        int nextBucket=Math.floorDiv(loop,settings.interval);
        if(bucket==null?Math.floorMod(loop,settings.interval)!=0:nextBucket<=bucket){urgent(links,players,states,urgent,loop);return;}
        bucket=nextBucket;
        for(var s:states.values())if(s.bot||s.applied!=null){int stamp=s.bot?Math.max(0,loop):Math.max(0,Math.addExact(s.applied,settings.loopOffset));ack.put(s.id,stamp);players.get(s.id).acknowledge(stamp);}
        if(settings.split){split(links,players,states,urgent,loop);return;}
        var groups=new LinkedHashMap<Group,List<WorldUpdateLink>>();
        for(var link:links){var s=states.get(link.localPlayerId());boolean due=ownerDue(s,urgent,loop);
            var key=new Group(due,due&&urgent.contains(s.id),due?null:link.localPlayerId());groups.computeIfAbsent(key,k->new ArrayList<>()).add(link);}
        for(var entry:groups.entrySet()){
            var group=entry.getKey();byte[] data=build(states.values(),loop,group.excluded,null);
            var offsets=group.self?SnapshotWire.toolOffsets(data):Map.<Integer,Integer>of();int sends=0;
            for(var link:entry.getValue()){
                var s=states.get(link.localPlayerId());byte[] payload=group.self?SnapshotWire.localOverride(data,offsets,s.id):data;
                if(!link.snapshot(payload,group.transition?Delivery.RELIABLE:Delivery.UNRELIABLE,loop))continue;
                sends++;if(group.transition)flush(link);if(group.self)record(players.get(s.id),s,ack.getOrDefault(s.id,s.row.pong()),loop,group.transition);
            }
            world.delivered(sends,(long)data.length*sends);
        }
    }
    private void split(List<? extends WorldUpdateLink> links,Map<Integer,Player> players,Map<Integer,State> states,Set<Integer> urgent,int loop){
        var snapshot=SnapshotWire.parse(build(states.values(),loop,null,null));int sends=0;long bytes=0;
        for(var link:links){
            var s=states.get(link.localPlayerId());var delivery=observerDelivery(link,s,loop);
            if(delivery!=null&&observerDue(link,delivery.spacing,loop)){
                var held=link.heldSnapshotRows(loop);var rows=new ArrayList<byte[]>();var entities=new HashSet<Integer>();
                for(var hold:held)if(hold.entity())entities.add(hold.id());
                for(var entry:snapshot.rows().entrySet())if(!Objects.equals(entry.getKey(),link.localPlayerId())&&!held.contains(new net.battlespades.session.SnapshotHolds.Row(false,entry.getKey())))rows.add(entry.getValue());
                byte[] tail=entities.isEmpty()?snapshot.tail():SnapshotWire.tailWithout(snapshot.tail(),entities);
                for(byte[] payload:SnapshotWire.observerParts(snapshot.prefix(),rows,tail,link.snapshotPayloadLimit())){
                    boolean unsequenced=delivery.unsequenced&&link.snapshotUnsequenced();
                    if(!link.snapshot(payload,unsequenced?Delivery.UNSEQUENCED:Delivery.UNRELIABLE,loop))continue;
                    link.observerState().sent(unsequenced,link.reliableSendIndex());sends++;bytes+=payload.length;
                }
            }
            if(!ownerDue(s,urgent,loop))continue;
            byte[] row=snapshot.rows().get(s.id);if(row==null){settle(s,loop);continue;}
            boolean transition=urgent.contains(s.id);
            byte[] payload=SnapshotWire.ownerPayload(SnapshotWire.ownerPrefix(link.observerState().loop(),snapshot.prefix()),row);
            if(!link.snapshot(payload,transition?Delivery.RELIABLE:Delivery.UNRELIABLE,loop))continue;
            if(transition)flush(link);record(players.get(s.id),s,ack.getOrDefault(s.id,s.row.pong()),loop,transition);sends++;bytes+=payload.length;
        }
        world.delivered(sends,bytes);
    }
    private void urgent(List<? extends WorldUpdateLink> links,Map<Integer,Player> players,Map<Integer,State> states,Set<Integer> urgent,int loop){
        for(var link:links){var s=states.get(link.localPlayerId());if(s==null||!urgent.contains(s.id))continue;
            int stamp=Math.max(0,Math.addExact(s.applied,settings.loopOffset));ack.put(s.id,stamp);players.get(s.id).acknowledge(stamp);
            byte[] data=build(states.values(),loop,null,s.id);
            if(settings.split){var parsed=SnapshotWire.parse(data);byte[] row=parsed.rows().get(s.id);if(row==null){settle(s,loop);continue;}
                data=SnapshotWire.ownerPayload(SnapshotWire.ownerPrefix(link.observerState().loop(),parsed.prefix()),row);}
            else data=SnapshotWire.localOverride(data,SnapshotWire.toolOffsets(data),s.id);
            if(!link.snapshot(data,Delivery.RELIABLE,loop))continue;
            flush(link);record(players.get(s.id),s,stamp,loop,true);world.delivered(1,data.length);
        }
    }
}
