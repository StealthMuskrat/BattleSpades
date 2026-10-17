// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import net.battlespades.protocol.*;
import net.battlespades.world.MutableWorld.Cell;

/** Owned original block health, compounded crack shade and client-owned-color replay ledger. */
public final class TerrainHealth {
    public enum UserMode { NORMAL,CLASSIC,UGC }
    public record Damage(double total,boolean destroyed) {}
    public record Shade(Cell cell,int rgb) {}
    public record Replay(Cell cell,List<Double> amounts){public Replay{amounts=List.copyOf(amounts);}}
    private final MutableWorld world;
    private final Map<Cell,Double> damage=new LinkedHashMap<>();
    private final Map<Cell,Long> shade=new LinkedHashMap<>();
    private final Map<Cell,byte[]> hits=new LinkedHashMap<>();
    private double scale=1.;private UserMode mode=UserMode.NORMAL;
    TerrainHealth(MutableWorld world){this.world=world;}
    private void owned(){world.revision();}
    public void configure(double healthScale,UserMode userMode){owned();double next=healthScale>0?healthScale:1.;boolean changed=Double.doubleToLongBits(scale)!=Double.doubleToLongBits(next);scale=next;mode=Objects.requireNonNull(userMode);if(changed)for(var cell:damage.keySet())world.recordCell(cell);}
    public double scale(){owned();return scale;}
    Double userHealth(double amount){return switch(mode){case NORMAL->amount;case CLASSIC->5.;case UGC->null;};}
    void beforeSet(Cell cell,boolean recolor,int rgb){
        if(recolor&&damage.containsKey(cell)){
            Long packed=shade.get(cell);long original=packed==null?world.color(cell)&0xffffff:packed>>>24;hits.remove(cell);shade.put(cell,(original<<24)|(rgb&0xffffff));
        }else if(!recolor)clear(cell);
    }
    private void clear(Cell cell){damage.remove(cell);shade.remove(cell);hits.remove(cell);}
    public double accumulated(Cell cell){owned();return damage.getOrDefault(cell,0.);}
    public Damage apply(BlockDamageModel.Cell position,double amount,double defaultHealth){
        owned();if(amount<=0)return new Damage(0,false);int x=position.x(),y=position.y(),z=position.z();if(x<0||x>=512||y<0||y>=512||z<0||z>=240)return new Damage(0,false);var cell=new Cell(x,y,z);if(z>BlockDamageModel.MAX_DAMAGEABLE_Z)return new Damage(0,false);
        if(!world.solid(cell)){clear(cell);world.forgetHealth(cell);return new Damage(0,false);}
        double threshold=world.healthOverride(cell,defaultHealth)*scale;Double previous=damage.get(cell);double total=(previous==null?0:previous)+amount;
        if(total>=threshold){world.set(cell,false,0,null);return new Damage(total,true);}
        damage.put(cell,total);recordShade(cell,amount,previous==null);world.recordCell(cell);return new Damage(total,false);
    }
    private void recordShade(Cell cell,double amount,boolean first){
        if(first){shade.remove(cell);hits.remove(cell);int rgb=world.color(cell)&0xffffff;if(!world.explicit(cell)||rgb==0)hits.put(cell,new byte[0]);else shade.put(cell,((long)rgb<<24)|rgb);}
        if(hits.containsKey(cell)){
            var history=hits.get(cell);if(history==null)return;long quarters=(long)Math.rint(amount*4);if(quarters>0&&quarters<=255&&Math.abs(quarters/4.-amount)<1e-9&&history.length<64){var next=Arrays.copyOf(history,history.length+1);next[history.length]=(byte)quarters;hits.put(cell,next);}else hits.put(cell,null);return;
        }
        long packed=shade.getOrDefault(cell,((long)(world.color(cell)&0xffffff)<<24)|(world.color(cell)&0xffffff));shade.put(cell,((packed>>>24)<<24)|BlockDamageModel.dimRgb((int)(packed&0xffffff),amount));
    }
    private byte[] history(Cell cell){var value=hits.get(cell);if(value==null||value.length==0)return null;int sum=0;for(byte n:value)sum+=Byte.toUnsignedInt(n);return Math.abs(sum/4.-damage.getOrDefault(cell,0.))>1e-6?null:value;}
    /** Null cells requests the complete reveal; explicit cells also receive default-health user rows. */
    public BlockManagerState rows(List<Cell> cells,boolean replayHits){
        owned();var users=new ArrayList<BlockManagerState.User>();var damaged=new ArrayList<BlockManagerState.Damaged>();var index=new HashMap<Cell,Integer>();
        var healthCells=cells==null?new ArrayList<>(world.healthCells()):cells;var damageCells=cells==null?new ArrayList<>(damage.keySet()):cells;
        for(var cell:healthCells){if(!world.solid(cell)){world.forgetHealth(cell);continue;}index.put(cell,users.size());users.add(user(cell,world.initialHealth(cell)));}
        for(var cell:damageCells){if(!damage.containsKey(cell))continue;if(!world.solid(cell)){clear(cell);continue;}double initial=world.initialHealth(cell),remaining=initial*scale-damage.get(cell);if(remaining<=0)continue;
            Double row=null;if(replayHits&&history(cell)!=null)row=initial;else if(!world.explicit(cell)||hits.containsKey(cell))row=remaining/scale;
            if(row!=null){Integer slot=index.get(cell);if(slot!=null)users.set(slot,user(cell,row));else users.add(user(cell,row));continue;}
            Long packed=shade.get(cell);int rgb=packed==null?world.color(cell)&0xffffff:(int)(packed>>>24)&0xffffff;damaged.add(new BlockManagerState.Damaged(cell.x(),cell.y(),cell.z(),remaining,rgb));
        }
        return new BlockManagerState(users,damaged);
    }
    private static BlockManagerState.User user(Cell cell,double health){return new BlockManagerState.User(cell.x(),cell.y(),cell.z(),health);}
    public List<Shade> shades(List<Cell> cells){
        owned();var output=new ArrayList<Shade>();var wanted=cells==null?new ArrayList<>(shade.keySet()):cells;
        for(var cell:wanted){Long packed=shade.get(cell);if(packed==null)continue;Double accumulated=damage.get(cell);if(accumulated==null||!world.solid(cell)){shade.remove(cell);continue;}double remaining=world.initialHealth(cell)*scale-accumulated;if(remaining<=0)continue;double rowHealth=Math.max(1,Math.min(255,Math.floor(remaining*4+.5)))/4.;Double health=world.healthOverrideOrNull(cell);double initial=health==null?5.*scale:Math.max(1,Math.min(255,Math.ceil(health*4-1e-9)))/4.*scale;int shown=(int)(packed&0xffffff),predicted=BlockDamageModel.dimRgb((int)(packed>>>24)&0xffffff,initial-rowHealth);if(predicted!=shown)output.add(new Shade(cell,shown));}
        return List.copyOf(output);
    }
    public List<Replay> replays(List<Cell> cells){
        owned();var output=new ArrayList<Replay>();var wanted=cells==null?new ArrayList<>(hits.keySet()):cells;
        for(var cell:wanted){if(!hits.containsKey(cell))continue;if(!world.solid(cell)){clear(cell);continue;}var history=history(cell);if(history==null)continue;var amounts=new ArrayList<Double>(history.length);for(byte b:history)amounts.add(Byte.toUnsignedInt(b)/4.);output.add(new Replay(cell,amounts));}return List.copyOf(output);
    }
    /** Source prefab reveal: all initial-health batches, then damaged rows, shades and client-color hits. */
    public List<GamePacket> reveal(int actor,int loop,int requestedBatch){
        owned();int batch=Math.max(1,Math.min(4096,requestedBatch));var rows=rows(null,true);var packets=new ArrayList<GamePacket>();
        for(int start=0;start<rows.users().size();start+=batch)packets.add(new BlockManagerState(rows.users().subList(start,Math.min(start+batch,rows.users().size())),List.of()));
        for(int start=0;start<rows.damaged().size();start+=batch)packets.add(new BlockManagerState(List.of(),rows.damaged().subList(start,Math.min(start+batch,rows.damaged().size()))));
        for(var shade:shades(null)){var cell=shade.cell;packets.add(new SimplePackets.PaintBlockPacket(Math.max(0,loop),cell.x(),cell.y(),cell.z(),shade.rgb));}
        for(var replay:replays(null)){var cell=replay.cell;for(double amount:replay.amounts)packets.add(new DamagePacket(actor,6,(float)amount,0,0,0,actor,new Vector3f(cell.x(),cell.y(),cell.z())));}
        return List.copyOf(packets);
    }
    void close(){damage.clear();shade.clear();hits.clear();}
}
