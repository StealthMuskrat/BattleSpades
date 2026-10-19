// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import java.util.function.IntSupplier;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;

/** Owned source bullet/native/melee terrain commit. Concrete repair, mode, score and achievement domains are required. */
public final class TerrainDamage {
    public interface Domain {
        boolean allowsDig(PlayerLife player,Cell cell);boolean buildDamage();
        void repair(List<Cell> cells);void reliable(GamePacket packet);void sound(int sound,Cell cell,PlayerLife source);void reject(PlayerLife player,String reason);
        default void causerPacket(int causer,GamePacket nativePacket,GamePacket exactPacket){reliable(exactPacket);}
        void profileBlocks(PlayerLife player,int count);void scoreBlocks(PlayerLife player,int count,List<List<Cell>> chunks);
        Integer activeShotTool(PlayerLife player);
        void achievedBlocks(PlayerLife player,List<Cell> removed,List<Cell> collapsed,List<List<Cell>> chunks,Integer tool);
        void collapsedRepair(List<Cell> cells);void modeDestroyed(PlayerLife player,List<Cell> cells,boolean mined);
    }
    public record NativeResult(int seed,double wireAmount,List<Cell> destroyed,List<BlockDamageModel.Hit> damaged){public NativeResult{destroyed=List.copyOf(destroyed);damaged=List.copyOf(damaged);}}
    private record Melee(int type,double amount,String pattern) {}
    private static final Map<Integer,Melee> MELEE;
    private static final Map<Integer,Integer> SOUNDS;
    private static final double RANGE,EYE_SLACK,HALF_DIAGONAL,KILL_DAMAGE,UGC_SECONDARY_DAMAGE;
    private static final int UGC_SUPERSPADE,UGC_SECONDARY_TYPE;
    static {
        var data=SourceCatalog.load("block-damage");var settings=data.getArray("combat_settings").getTable(0);RANGE=((Number)settings.get("melee_range")).doubleValue();EYE_SLACK=((Number)settings.get("eye_slack")).doubleValue();HALF_DIAGONAL=((Number)settings.get("cell_half_diagonal")).doubleValue();KILL_DAMAGE=((Number)settings.get("kill_damage")).doubleValue();UGC_SUPERSPADE=Math.toIntExact(settings.getLong("ugc_superspade"));UGC_SECONDARY_TYPE=Math.toIntExact(settings.getLong("ugc_secondary_type"));UGC_SECONDARY_DAMAGE=((Number)settings.get("ugc_secondary_damage")).doubleValue();
        var profiles=new HashMap<Integer,Melee>();var rows=data.getArray("melee");for(int i=0;i<rows.size();i++){var row=rows.getTable(i);profiles.put(Math.toIntExact(row.getLong("tool")),new Melee(Math.toIntExact(row.getLong("type")),((Number)row.get("amount")).doubleValue(),row.getString("pattern")));}MELEE=Map.copyOf(profiles);
        var sounds=new HashMap<Integer,Integer>();rows=data.getArray("sound");for(int i=0;i<rows.size();i++){var row=rows.getTable(i);sounds.put(Math.toIntExact(row.getLong("type")),Math.toIntExact(row.getLong("id")));}SOUNDS=Map.copyOf(sounds);
    }
    private final Thread owner=Thread.currentThread();private final MutableWorld world;private final CollisionMap map;private final Domain domain;private final IntSupplier randomSeed;
    public TerrainDamage(MutableWorld world,Domain domain,IntSupplier randomSeed){this.world=Objects.requireNonNull(world);map=CollisionMap.of(world);this.domain=Objects.requireNonNull(domain);this.randomSeed=Objects.requireNonNull(randomSeed);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Terrain combat outside owner");}
    public static DamagePacket packet(PlayerLife player,Cell cell,double amount,Integer type,int seed,Integer causer){int actor=player==null?255:player.id();return new DamagePacket(actor,type==null?6:type,(float)Math.min(amount,KILL_DAMAGE),0,1,seed&255,causer==null?(player==null?0:player.id()):causer,new Vector3f(cell.x(),cell.y(),cell.z()));}
    private void broadcast(PlayerLife player,Cell cell,double amount,Integer type,int seed,Integer causer){var packet=packet(player,cell,amount,type,seed,causer);if(causer==null)domain.reliable(packet);else domain.causerPacket(causer,packet,packet(player,cell,amount,6,0,null));Integer sound=SOUNDS.get(packet.type());if(sound!=null)domain.sound(sound,cell,player);}
    private void collapse(PlayerLife player,List<Cell> removed){
        domain.profileBlocks(player,removed.size());var chunks=TerrainCollapse.find(world,removed);var collapsed=new ArrayList<Cell>();
        for(var chunk:chunks)for(var cell:world.destroyBlocks(chunk.stream().map(c->new MutableWorld.Cell(c.x(),c.y(),c.z())).toList()))collapsed.add(new Cell(cell.x(),cell.y(),cell.z()));
        domain.profileBlocks(player,collapsed.size());domain.scoreBlocks(player,removed.size()+collapsed.size(),chunks);try{domain.achievedBlocks(player,List.copyOf(removed),List.copyOf(collapsed),chunks,domain.activeShotTool(player));}catch(RuntimeException failure){/* Original achievement hooks isolate engine faults. */}if(!collapsed.isEmpty())domain.collapsedRepair(List.copyOf(collapsed));
    }
    public boolean gun(PlayerLife player,Cell cell,double amount){return gun(player,cell,amount,null,null);}
    public boolean gun(PlayerLife player,Cell cell,double amount,Integer type,Integer causer){
        owned();if(player!=null&&!domain.allowsDig(player,cell)){domain.repair(List.of(cell));return false;}if(!domain.buildDamage())return false;double wire=BlockDamageModel.wireDamage(amount);var result=world.terrainHealth().apply(cell,wire,5);
        if(result.destroyed()){broadcast(player,cell,KILL_DAMAGE,type,0,causer);collapse(player,List.of(cell));if(player!=null)domain.modeDestroyed(player,List.of(cell),player.fire().spade());}
        else if(result.total()>0)broadcast(player,cell,wire,type,0,causer);return result.total()>0;
    }
    public NativeResult nativeDamage(Vector position,int type,double amount,Integer seed){
        owned();double wire=BlockDamageModel.wireDamage(amount);var kind=BlockDamageModel.kind(type);int selected=seed==null?(kind!=null&&(kind.kind()==BlockDamageModel.Kind.CUBE||kind.kind()==BlockDamageModel.Kind.RADIUS)?randomSeed.getAsInt():0):seed;selected&=255;var destroyed=new ArrayList<Cell>();var damaged=new ArrayList<BlockDamageModel.Hit>();
        if(wire>0)for(var hit:BlockDamageModel.footprint(type,position,wire,selected)){if(!BlockDamageModel.damageable(hit)||!map.solid(hit.cell().x(),hit.cell().y(),hit.cell().z()))continue;var result=world.terrainHealth().apply(hit.cell(),hit.damage(),5);if(result.destroyed())destroyed.add(hit.cell());else if(result.total()>0)damaged.add(hit);}
        return new NativeResult(selected,wire,destroyed,damaged);
    }
    /** Complete an already-applied native footprint; radius-causer calls retain even the empty source callback sequence. */
    public void completeNative(PlayerLife player,List<Cell> removed,boolean radiusCauser){owned();if(radiusCauser||!removed.isEmpty()){collapse(player,removed);if(player!=null&&!removed.isEmpty())domain.modeDestroyed(player,removed,false);}}
    private static List<Cell> positions(Cell c,String pattern){var out=new ArrayList<Cell>();switch(pattern){case "cube"->{for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)out.add(new Cell(c.x()+x,c.y()+y,c.z()+z));}case "column"->{for(int z=-1;z<=1;z++)out.add(new Cell(c.x(),c.y(),c.z()+z));}case "machete_vertical_pair"->{out.add(c);out.add(new Cell(c.x(),c.y(),c.z()+1));}default->out.add(c);}return List.copyOf(out);}
    public boolean dig(PlayerLife player,Vector origin,Vector direction,ShootPacket packet,List<Vector> eyes,double slack){
        owned();if(direction==null)return false;var hit=HitTrace.terrainRay(map,origin,direction,RANGE);if(hit==null)return false;var cell=new Cell(hit.x(),hit.y(),hit.z());var center=new Vector(cell.x()+.5,cell.y()+.5,cell.z()+.5);
        if(eyes!=null&&eyes.stream().noneMatch(eye->CombatGeometry.distance(eye,center)<=RANGE+EYE_SLACK+HALF_DIAGONAL+slack)){domain.reject(player,"dig_out_of_reach");domain.repair(List.of(cell));return false;}
        var profile=player.inventory().tool()==UGC_SUPERSPADE&&packet.secondary()?new Melee(UGC_SECONDARY_TYPE,UGC_SECONDARY_DAMAGE,"cube"):MELEE.getOrDefault(player.inventory().tool(),MELEE.get(-1));var positions=positions(cell,profile.pattern);
        if(!domain.allowsDig(player,cell)||profile.amount<=0){domain.repair(positions);return false;}
        if(profile.pattern.equals("single")){
            double wire=BlockDamageModel.wireDamage(profile.amount);var result=world.terrainHealth().apply(cell,wire,5);if(result.destroyed()){player.inventory().addBlocks(1);broadcast(player,cell,KILL_DAMAGE,profile.type,0,null);collapse(player,List.of(cell));domain.modeDestroyed(player,List.of(cell),true);return true;}if(result.total()>0){broadcast(player,cell,wire,profile.type,0,null);return true;}return false;
        }
        List<Cell> destroyed;
        if(profile.pattern.equals("machete_vertical_pair")){
            var removed=new ArrayList<Cell>();boolean affected=false;for(var p:positions){var result=world.terrainHealth().apply(p,profile.amount,5);affected|=result.total()>0||result.destroyed();if(result.destroyed())removed.add(p);}if(!affected){domain.repair(positions);return false;}destroyed=List.copyOf(removed);player.inventory().addBlocks(destroyed.size());broadcast(player,cell,profile.amount,profile.type,0,null);
        }else{
            if(!BlockDamageModel.nativeType(profile.type)){domain.repair(positions);return false;}var result=nativeDamage(new Vector(cell.x(),cell.y(),cell.z()),profile.type,profile.amount,null);if(result.destroyed.isEmpty()&&result.damaged.isEmpty()){domain.repair(positions);return false;}destroyed=result.destroyed;if(!destroyed.isEmpty())player.inventory().addBlocks(destroyed.size());broadcast(player,cell,result.wireAmount,profile.type,result.seed,null);
        }
        if(!destroyed.isEmpty()){collapse(player,destroyed);domain.modeDestroyed(player,destroyed,true);}return true;
    }
}
