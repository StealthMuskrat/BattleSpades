// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import java.util.function.Supplier;
import net.battlespades.protocol.GamePacket;
import net.battlespades.world.BlockDamageModel.Cell;

/** Original bounded regular and collapse-confirmation lanes. Every send reads current canonical state. */
public final class TerrainRepair {
    public record Settings(boolean enabled,int queueLimit,int delayTicks,int collapseDelayTicks,int intervalTicks,int batchLimit,int collapseBatchLimit){
        public static Settings defaults(){return new Settings(true,8192,120,18,3,8,8);}
    }
    public record Recipient(int playerId,boolean inGame,boolean hasPlayer) {}
    @FunctionalInterface public interface Sender {void send(Recipient recipient,GamePacket packet);}
    public record Metrics(long dropped,long queuePeak,long cells,long sends,long failedSends) {}
    private final Thread owner=Thread.currentThread();private final MutableWorld world;private final Supplier<Settings> settings;private final Supplier<List<Recipient>> recipients;private final Sender sender;
    private final LinkedHashMap<Cell,Long> regular=new LinkedHashMap<>(),collapse=new LinkedHashMap<>();
    private long dropped,peak,cells,sends,failed;
    private static final int[][] NEIGHBORS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    private static final Comparator<Cell> ORDER=Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z);
    public TerrainRepair(MutableWorld world,Supplier<Settings> settings,Supplier<List<Recipient>> recipients,Sender sender){this.world=Objects.requireNonNull(world);this.settings=Objects.requireNonNull(settings);this.recipients=Objects.requireNonNull(recipients);this.sender=Objects.requireNonNull(sender);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Terrain repair outside owner");}
    private static boolean valid(Cell cell){return cell.x()>=0&&cell.x()<512&&cell.y()>=0&&cell.y()<512&&cell.z()>=0&&cell.z()<240;}
    private boolean solid(Cell cell){return valid(cell)&&world.solid(new MutableWorld.Cell(cell.x(),cell.y(),cell.z()));}
    public int pending(){owned();return regular.size()+collapse.size();}
    public Metrics metrics(){owned();return new Metrics(dropped,peak,cells,sends,failed);}
    public void reset(){owned();regular.clear();collapse.clear();}
    public static List<Cell> surfaceFirst(Collection<Cell> cells){
        var unique=new HashSet<>(cells);var boundary=new ArrayList<Cell>();
        for(var cell:unique)for(var d:NEIGHBORS)if(!unique.contains(new Cell(cell.x()+d[0],cell.y()+d[1],cell.z()+d[2]))){boundary.add(cell);break;}
        boundary.sort(ORDER);var queue=new ArrayDeque<>(boundary);var seen=new HashSet<>(boundary);var output=new ArrayList<Cell>(unique.size());
        while(!queue.isEmpty()){var cell=queue.removeFirst();output.add(cell);for(var d:NEIGHBORS){var neighbor=new Cell(cell.x()+d[0],cell.y()+d[1],cell.z()+d[2]);if(unique.contains(neighbor)&&seen.add(neighbor))queue.addLast(neighbor);}}
        return List.copyOf(output);
    }
    private void bound(Settings s){int limit=Math.max(64,s.queueLimit);while(pending()>limit){if(!regular.isEmpty())regular.pollFirstEntry();else collapse.pollLastEntry();dropped++;}}
    public void record(Collection<Cell> positions,long loop){
        owned();var s=Objects.requireNonNull(settings.get());if(!s.enabled)return;long due=loop+Math.max(1,s.delayTicks);
        for(var cell:positions){if(!valid(cell))continue;if(collapse.containsKey(cell)){if(!solid(cell))continue;collapse.remove(cell);}regular.remove(cell);regular.put(cell,due);bound(s);}peak=Math.max(peak,pending());
    }
    public void recordCollapse(Collection<Cell> positions,long loop){
        owned();var s=Objects.requireNonNull(settings.get());if(!s.enabled)return;long due=loop+Math.max(1,s.collapseDelayTicks);
        for(var cell:surfaceFirst(positions)){if(!valid(cell))continue;regular.remove(cell);collapse.remove(cell);collapse.put(cell,due);bound(s);}peak=Math.max(peak,pending());
    }
    public int tick(long loop){
        owned();if(pending()==0)return 0;var s=Objects.requireNonNull(settings.get());if(!s.enabled){reset();return 0;}if(loop%Math.max(1,s.intervalTicks)!=0)return 0;
        var eligible=Objects.requireNonNull(recipients.get()).stream().filter(r->r.inGame&&r.hasPlayer).toList();if(eligible.isEmpty()){reset();return 0;}
        var selected=new ArrayList<Cell>();int collapseCount=0;var iterator=collapse.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();if(entry.getValue()>loop)break;var cell=entry.getKey();iterator.remove();if(solid(cell))continue;selected.add(cell);if(++collapseCount>=Math.max(1,s.collapseBatchLimit))break;}
        int regularCount=0;iterator=regular.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(entry.getValue()>loop)break;selected.add(entry.getKey());iterator.remove();if(++regularCount>=Math.max(1,s.batchLimit))break;}
        if(selected.isEmpty())return 0;long successful=0;
        for(var recipient:eligible){var packets=new ArrayList<GamePacket>();for(var cell:selected)packets.addAll(world.canonical(new MutableWorld.Cell(cell.x(),cell.y(),cell.z()),recipient.playerId,(int)loop));for(var packet:packets)try{sender.send(recipient,packet);successful++;}catch(RuntimeException failure){failed++;}}
        cells+=selected.size();sends+=successful;return selected.size();
    }
}
