// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.battlespades.protocol.*;

/** Owned committed-cell store and late-join journal. Gameplay validation/collapse are separate domains. */
public final class MutableWorld implements AutoCloseable {
    public record Cell(int x,int y,int z) {
        public Cell { if(x<0 || x>=512 || y<0 || y>=512 || z<0 || z>=240) throw new IllegalArgumentException("Cell outside world"); }
        int column() { return x+y*512; }
    }
    public record Capture(MapSyncSnapshot snapshot,Lease lease) {}
    public enum Replay { WAITING, COMPLETE, OVERFLOW, STALE }
    private record Mutation(long sequence,Cell cell) {}
    private record AirColumn(int column,BitSet mask) {}
    private record CellState(boolean solid,int color,boolean explicit,double health,double damage,double healthScale) {}
    private static final class ColumnState {
        final BitSet solid=new BitSet(240),explicit=new BitSet(240);
        final int[] colors=new int[240];
    }
    private final Thread owner=Thread.currentThread();
    private final VxlMap base;
    private final long epoch;
    private final int journalLimit;
    private final int sourceCrc;
    private final Map<Integer,ColumnState> columns=new HashMap<>();
    private final LinkedHashSet<Integer> dirtyColumns=new LinkedHashSet<>();
    private final Map<Cell,Integer> staticLights=new java.util.LinkedHashMap<>();
    private record LightListener(java.util.function.BiConsumer<Cell,Integer> action,java.util.function.Consumer<RuntimeException> fault){}
    private final List<LightListener> lightListeners=new ArrayList<>();
    public RemovalLease observeStaticLights(java.util.function.BiConsumer<Cell,Integer> listener,java.util.function.Consumer<RuntimeException> fault){active();var item=new LightListener(java.util.Objects.requireNonNull(listener),java.util.Objects.requireNonNull(fault));lightListeners.add(item);return ()->{active();lightListeners.remove(item);};}
    public Map<Cell,Integer> staticLightCells(){active();return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(staticLights));}
    public List<VxlMap.Marker> markers(){active();return base.markers();}
    public int sourceZShift(){active();return base.zShift();}
    /** Load-derived flare state; intentionally leaves mutation/air journals unchanged. */
    public boolean restoreStaticLight(double x,double y,double z,int rgb) {
        active();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Nonfinite light coordinate");
        int ax=(int)x,ay=(int)y,az=(int)z;
        if(ax<0||ax>=512||ay<0||ay>=512||az<0||az>=240)return false;
        var cell=new Cell(ax,ay,az);var column=mutable(cell.column());
        column.solid.set(az);column.explicit.set(az);column.colors[az]=0x80000000|(rgb&0xffffff);surfaces[cell.column()]=-1;
        staticLights.put(cell,rgb&0xffffff);
        for(var listener:List.copyOf(lightListeners))try{listener.action.accept(cell,rgb&0xffffff);}catch(RuntimeException failure){listener.fault.accept(failure);}
        // Source observers see the restored voxel/ledger before damage is cleared.
        terrainHealth.clearLoad(cell);health.remove(cell);return true;
    }
    private final Map<Integer,BitSet> air=new HashMap<>();
    private final Map<Cell,Double> health=new java.util.LinkedHashMap<>();
    private final TerrainHealth terrainHealth=new TerrainHealth(this);
    private final int[] surfaces=new int[512*512];
    private final ArrayDeque<Mutation> journal=new ArrayDeque<>();
    private final LinkedHashSet<Lease> leases=new LinkedHashSet<>();
    private long revision;
    private java.util.function.Consumer<Cell> removalObserver;
    public interface RemovalLease extends AutoCloseable {@Override void close();}
    /** The canonical server removal hook runs before the per-cell catch-up cursor is recorded. */
    public RemovalLease observeRemovals(java.util.function.Consumer<Cell> observer){active();if(removalObserver!=null)throw new IllegalStateException("Canonical removal observer already installed");removalObserver=java.util.Objects.requireNonNull(observer);return ()->{active();if(removalObserver==observer)removalObserver=null;};}

    private boolean closed;
    public MutableWorld(VxlMap base,long epoch,int journalLimit) {
        this.base=java.util.Objects.requireNonNull(base);this.epoch=epoch;this.journalLimit=journalLimit;sourceCrc=(int)base.sourceCrc();
        java.util.Arrays.fill(surfaces,-1);
        if(epoch<0 || journalLimit<1) throw new IllegalArgumentException("World epoch/journal limit");
    }
    private void owned() { if(Thread.currentThread()!=owner) throw new IllegalStateException("World accessed outside owner"); }
    private void active() { owned();if(closed) throw new IllegalStateException("World retired"); }
    public long epoch() { return epoch; }
    public long revision() { active();return revision; }
    public long topologyVersion() { return revision(); }
    public int surfaceZ(int x,int y) {
        active();if(x<0||x>=512||y<0||y>=512)return 239;int key=x+y*512;
        if(surfaces[key]<0){var column=columns.get(key);int z=column==null?base.surfaceZ(x,y):column.solid.nextSetBit(0);surfaces[key]=z<0?239:z;}
        return surfaces[key];
    }
    public int journalSize() { active();return journal.size(); }
    public int activeLeases() { active();return leases.size(); }
    public int sourceCrc() { return sourceCrc; }
    /** Identity, rather than a checksum, protects worker-prepared geometry from another terrain. */
    public boolean pristineSource(VxlMap source) { active();return base==source&&revision==0&&columns.isEmpty(); }
    public boolean solid(Cell cell) { active();var c=columns.get(cell.column());return c==null?base.solid(cell.x,cell.y,cell.z):c.solid.get(cell.z); }
    public int color(Cell cell) { active();var c=columns.get(cell.column());return c==null?base.color(cell.x,cell.y,cell.z):c.colors[cell.z]; }
    public boolean explicit(Cell cell) { active();var c=columns.get(cell.column());return c==null?base.hasExplicitColor(cell.x,cell.y,cell.z):c.explicit.get(cell.z); }
    public double initialHealth(Cell cell) { active();return health.getOrDefault(cell,5.0); }
    public TerrainHealth terrainHealth() { active();return terrainHealth; }
    Double healthOverrideOrNull(Cell cell) { active();return health.get(cell); }
    double healthOverride(Cell cell,double fallback) { active();return health.getOrDefault(cell,fallback); }
    List<Cell> healthCells() { active();return List.copyOf(health.keySet()); }
    void forgetHealth(Cell cell) { active();health.remove(cell); }
    private ColumnState mutable(int key) {
        return columns.computeIfAbsent(key,k->{
            int x=k%512,y=k/512;var column=new ColumnState();
            for(int z=0;z<240;z++) {
                column.solid.set(z,base.solid(x,y,z));column.explicit.set(z,base.hasExplicitColor(x,y,z));column.colors[z]=base.color(x,y,z);
            }
            return column;
        });
    }
    /** Commits exact topology/RGB, preserving health on paint; dynamic alpha uses the source's0x80 byte. */
    public void set(Cell cell,boolean solid,int rgb,Double initialHealth) {set(cell,solid,rgb,initialHealth,true);}
    private void set(Cell cell,boolean solid,int rgb,Double initialHealth,boolean publish) {
        active();
        if(!solid && cell.z==239) throw new IllegalArgumentException("Canonical seabed cannot be removed");
        if(initialHealth!=null && (!Double.isFinite(initialHealth) || initialHealth<=0)) throw new IllegalArgumentException("Block health");
        boolean previous=solid(cell),recolor=solid && initialHealth==null && previous;
        terrainHealth.beforeSet(cell,recolor,rgb);
        if(initialHealth!=null)initialHealth=terrainHealth.userHealth(initialHealth);
        var column=mutable(cell.column());
        dirtyColumns.add(cell.column());
        if(previous!=solid)surfaces[cell.column()]=-1;
        column.solid.set(cell.z,solid);column.explicit.set(cell.z,solid);column.colors[cell.z]=solid?0x80000000|(rgb&0xffffff):0;
        if(solid && initialHealth!=null) health.put(cell,initialHealth);else if(!recolor) health.remove(cell);
        if(solid) {
            var mask=air.get(cell.column());if(mask!=null) {mask.clear(cell.z);if(mask.isEmpty()) air.remove(cell.column());}
        } else {
            air.computeIfAbsent(cell.column(),k->new BitSet(240)).set(cell.z);
            int[][] offsets={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
            for(var d:offsets) {
                int x=cell.x+d[0],y=cell.y+d[1],z=cell.z+d[2];if(x<0 || x>=512 || y<0 || y>=512 || z<0 || z>=240) continue;
                var neighbor=new Cell(x,y,z);
                if(solid(neighbor) && !explicit(neighbor)) {
                    int color=color(neighbor);var c=mutable(neighbor.column());c.colors[z]=0x80000000|(color&0xffffff);c.explicit.set(z);
                    dirtyColumns.add(neighbor.column());
                }
            }
        }
        if(publish){if(!solid&&removalObserver!=null)removalObserver.accept(cell);recordCell(cell);}
    }
    /** Source destroy_blocks commits the whole component before support-loss callbacks can start another blast. */
    public List<Cell> destroyBlocks(List<Cell> positions){
        active();var removed=new java.util.ArrayList<Cell>();var seen=new java.util.HashSet<Cell>();for(var cell:positions)if(seen.add(cell)&&cell.z<239&&solid(cell)){set(cell,false,0,null,false);removed.add(cell);}
        var observer=removalObserver;for(var cell:removed){if(observer!=null)observer.accept(cell);recordCell(cell);}return List.copyOf(removed);
    }
    void recordCell(Cell cell) {
        active();revision++;
        if(!leases.isEmpty()) {
            for(var lease:leases) lease.complete=false;
            journal.addLast(new Mutation(revision,cell));
            while(journal.size()>journalLimit) {
                long dropped=journal.removeFirst().sequence;for(var lease:leases) if(lease.watermark<dropped) lease.overflow=true;
            }
        }
    }
    public byte[] serializeColumn(int x,int y) {
        active();new MapSyncSnapshot.Column(x,y);var c=columns.get(x+y*512);if(c==null) return base.serializeColumn(x,y);
        var w=new Wire.Writer();int z=0,previousAir=0;
        while(z<240) {
            while(z<240 && !c.solid.get(z)) z++;if(z==240) break;
            int start=z;while(z+1<240 && c.solid.get(z+1)) z++;int end=z,topEnd=start,bottomStart=end;
            if(c.solid.nextSetBit(end+1)<0) {
                for(int v=end;v>start;v--) if(c.explicit.get(v)) {topEnd=v;break;}
                w.u8(0).u8(start).u8(topEnd).u8(previousAir);for(int v=start;v<=topEnd;v++) w.i32(c.colors[v]);
            } else {
                while(bottomStart>start+1 && c.explicit.get(bottomStart-1)) bottomStart--;
                for(int v=bottomStart-1;v>start;v--) if(c.explicit.get(v)) {topEnd=v;break;}
                int bottomLength=bottomStart>topEnd?end-bottomStart+1:0;
                w.u8(1+topEnd-start+1+bottomLength).u8(start).u8(topEnd).u8(previousAir);
                for(int v=start;v<=topEnd;v++) w.i32(c.colors[v]);if(bottomLength>0) for(int v=bottomStart;v<=end;v++) w.i32(c.colors[v]);
            }
            previousAir=end+1;z++;
        }
        byte[] bytes=w.toByteArray();return bytes.length==0?new byte[]{0,(byte)240,(byte)239,0}:bytes;
    }
    /** The lease/watermark and all dirty column bytes are frozen in this one owner turn. */
    public Capture capture(boolean full,boolean repairAir) {
        active();var overlays=new HashMap<MapSyncSnapshot.Column,byte[]>();
        var selected=new LinkedHashSet<Integer>(dirtyColumns);for(var marker:base.markers())selected.add(marker.x()+marker.y()*512);for(int key:selected) overlays.put(new MapSyncSnapshot.Column(key%512,key/512),serializeColumn(key%512,key/512));
        var masks=new ArrayList<AirColumn>();
        if(repairAir) air.entrySet().stream().sorted(java.util.Comparator.comparingInt((Map.Entry<Integer,BitSet> e)->e.getKey()%512).thenComparingInt(e->e.getKey()/512))
                .forEach(e->masks.add(new AirColumn(e.getKey(),(BitSet)e.getValue().clone())));
        var snapshot=new MapSyncSnapshot(base,overlays,revision,full);var lease=new Lease(revision,List.copyOf(masks));leases.add(lease);prune();
        return new Capture(snapshot,lease);
    }
    public List<GamePacket> canonical(Cell cell,int actor,int loop) {
        active();if(!solid(cell)) return List.of(new DamagePacket(actor,6,31.75f,0,0,0,actor,new Vector3f(cell.x,cell.y,cell.z)));
        var packets=new ArrayList<GamePacket>();int rgb=color(cell)&0xffffff;
        packets.add(new SimplePackets.BlockBuildColored(loop,actor,cell.x,cell.y,cell.z,rgb));
        if(explicit(cell)) packets.add(new SimplePackets.PaintBlockPacket(loop,cell.x,cell.y,cell.z,rgb));
        var rows=terrainHealth.rows(List.of(cell),false);
        if(!rows.users().isEmpty())packets.add(new BlockManagerState(rows.users(),List.of()));
        if(!rows.damaged().isEmpty()) {
            packets.add(new BlockManagerState(List.of(),rows.damaged()));
            for(var shade:terrainHealth.shades(List.of(cell)))packets.add(new SimplePackets.PaintBlockPacket(loop,cell.x,cell.y,cell.z,shade.rgb()));
        }
        return List.copyOf(packets);
    }
    private void prune() {
        if(leases.isEmpty()) {journal.clear();return;}
        long oldest=leases.stream().mapToLong(l->l.watermark).min().orElse(revision);
        while(!journal.isEmpty() && journal.getFirst().sequence<=oldest) journal.removeFirst();
    }
    public final class Lease implements AutoCloseable {
        private long watermark,target;
        private CellState pendingState;
        private final List<AirColumn> airColumns;
        private int airIndex,airZ,cellIndex,packetIndex;
        private List<Cell> cells=List.of();
        private Cell pendingCell;
        private List<GamePacket> pendingPackets=List.of();
        private boolean overflow,complete,released;
        private Lease(long watermark,List<AirColumn> airColumns) {this.watermark=watermark;target=watermark;this.airColumns=airColumns;}
        public long watermark() {owned();return watermark;}
        public Replay pumpAir(int actor,int loop,int budget,Predicate<GamePacket> sink) {return replay(actor,loop,budget,sink,true);}
        public Replay pump(int actor,int loop,int budget,Predicate<GamePacket> sink) {
            return replay(actor,loop,budget,sink,false);
        }
        private Replay replay(int actor,int loop,int budget,Predicate<GamePacket> sink,boolean onlyAir) {
            owned();if(closed || released && !complete) return Replay.STALE;if(complete) return Replay.COMPLETE;
            if(budget<0) throw new IllegalArgumentException("Replay budget");if(overflow) return Replay.OVERFLOW;
            int sent=0;
            while(true) {
                if(overflow) return Replay.OVERFLOW;
                if(pendingCell!=null) {
                    // A cell changed during a partial enqueue: restart its idempotent current-state repair.
                    if(!stateOf(pendingCell).equals(pendingState)) {pendingPackets=canonical(pendingCell,actor,loop);pendingState=stateOf(pendingCell);packetIndex=0;}
                    while(packetIndex<pendingPackets.size()) {
                        if(sent>=budget || !sink.test(pendingPackets.get(packetIndex))) return Replay.WAITING;
                        packetIndex++;sent++;
                    }
                    pendingCell=null;pendingPackets=List.of();packetIndex=0;
                }
                while(airIndex<airColumns.size()) {
                    var col=airColumns.get(airIndex);int z=col.mask.nextSetBit(airZ);
                    if(z<0) {airIndex++;airZ=0;continue;}
                    pendingCell=new Cell(col.column%512,col.column/512,z);airZ=z+1;break;
                }
                if(pendingCell==null && onlyAir) return Replay.COMPLETE;
                if(pendingCell==null && cellIndex<cells.size()) pendingCell=cells.get(cellIndex++);
                if(pendingCell!=null) {pendingPackets=canonical(pendingCell,actor,loop);pendingState=stateOf(pendingCell);continue;}
                watermark=target;prune();
                if(watermark==revision) {complete=true;return Replay.COMPLETE;}
                target=revision;
                if(!journal.isEmpty() && journal.getFirst().sequence>watermark+1) {overflow=true;return Replay.OVERFLOW;}
                var unique=new LinkedHashSet<Cell>();for(var entry:journal) if(entry.sequence>watermark && entry.sequence<=target) unique.add(entry.cell);
                cells=List.copyOf(unique);cellIndex=0;
            }
        }
        @Override public void close() {owned();if(!released) {released=true;leases.remove(this);pendingPackets=List.of();cells=List.of();pendingCell=null;prune();}}
    }
    private CellState stateOf(Cell cell) {return new CellState(solid(cell),color(cell),explicit(cell),initialHealth(cell),terrainHealth.accumulated(cell),terrainHealth.scale());}
    @Override public void close() {owned();if(closed) return;closed=true;for(var l:List.copyOf(leases)) l.close();journal.clear();columns.clear();dirtyColumns.clear();staticLights.clear();lightListeners.clear();air.clear();health.clear();terrainHealth.close();}
}
