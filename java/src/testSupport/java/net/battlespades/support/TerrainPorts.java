// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import net.battlespades.combat.TerrainDamage;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;
import net.battlespades.world.BlockDamageModel.Cell;

/** Explicit observed terrain policy/effect ports; excluded from the application JAR. */
public final class TerrainPorts implements TerrainDamage.Domain {
    public java.util.function.Function<PlayerLife,Integer> activeTool;
    public net.battlespades.world.TerrainRepair repair;
    public java.util.function.LongSupplier loop;
    public boolean allows=true,build=true,active,failAchievement;
    public final List<String> events=new ArrayList<>(),wire=new ArrayList<>();
    public java.util.function.Consumer<GamePacket> reliableSink;
    public static String cells(List<Cell> cells){return cells.isEmpty()?"-":String.join(";",cells.stream().map(c->c.x()+","+c.y()+","+c.z()).toList());}
    private static String chunks(List<List<Cell>> chunks){return chunks.isEmpty()?"-":String.join("|",chunks.stream().map(TerrainPorts::cells).toList());}
    public boolean allowsDig(PlayerLife p,Cell c){return allows;}public boolean buildDamage(){return build;}
    public void repair(List<Cell> cells){events.add("repair:"+cells(cells));if(repair!=null)repair.record(cells,loop.getAsLong());}
    public void reliable(GamePacket packet){wire.add(HexFormat.of().formatHex(packet.encode()));if(reliableSink!=null)reliableSink.accept(packet);}
    public void sound(int sound,Cell cell,PlayerLife p){events.add("sound:"+sound+":"+cells(List.of(cell)));}
    public void reject(PlayerLife p,String reason){events.add("reject:"+reason);}
    public void profileBlocks(PlayerLife p,int count){events.add("profile:"+count);}
    public void scoreBlocks(PlayerLife p,int count,List<List<Cell>> chunks){events.add("score:"+count+":"+chunks(chunks));}
    public Integer activeShotTool(PlayerLife p){return activeTool!=null?activeTool.apply(p):active&&p!=null?p.inventory().tool():null;}
    public void achievedBlocks(PlayerLife p,List<Cell> removed,List<Cell> collapsed,List<List<Cell>> chunks,Integer tool){events.add("achieve:"+cells(removed)+":"+cells(collapsed)+":"+chunks(chunks)+":"+(tool==null?"None":tool));if(failAchievement)throw new IllegalStateException("Controlled achievement fault");}
    public void collapsedRepair(List<Cell> cells){events.add("collapsed:"+cells(cells));if(repair!=null)repair.recordCollapse(cells,loop.getAsLong());}
    public void modeDestroyed(PlayerLife p,List<Cell> cells,boolean mined){events.add("mode:"+(mined?1:0)+":"+cells(cells));}
}
