// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.io.IOException;
import java.util.*;
import net.battlespades.world.PrefabModels;
import static net.battlespades.bot.BotState.*;

/** Bounded authored prefab facts prepared at setup, never loaded during decisions. */
public final class BotPrefabPolicy {
    private BotPrefabPolicy(){}
    public record Bounds(Cell minimum,Cell maximum){}
    public record Geometry(String name,List<List<Cell>> cellsByYaw,List<Bounds> boundsByYaw,int blockCount){public Geometry{cellsByYaw=cellsByYaw.stream().map(List::copyOf).toList();boundsByYaw=List.copyOf(boundsByYaw);}}
    private static final org.tomlj.TomlParseResult CATALOG=net.battlespades.catalog.SourceCatalog.load("bot-modes");
    public static Integer blockCount(String name){var v=CATALOG.getLong("prefabs."+name.toLowerCase(Locale.ROOT));return v==null?null:Math.toIntExact(v);}
    public static boolean suitable(String name,String purpose){var count=blockCount(name);int limit=switch(purpose){case "climb","variety"->160;case "traversal"->256;default->128;};return count!=null&&count<=limit;}
    public static boolean zombie(String name){return name.toLowerCase(Locale.ROOT).startsWith("prefab_zombie");}
    public static List<Geometry> load(Iterable<String> names,PrefabModels models)throws IOException{var result=new ArrayList<Geometry>();var seen=new HashSet<String>();int count=0;for(var value:names){if(count++>=20)break;String name=value.toLowerCase(Locale.ROOT);if(!seen.add(name))continue;var model=models.get(name);if(model.isEmpty()||model.get().voxels().isEmpty()||model.get().voxels().size()>256)continue;var rotations=new ArrayList<List<Cell>>();var bounds=new ArrayList<Bounds>();for(int yaw=0;yaw<4;yaw++){var cells=PrefabModels.expand(model.get(),new net.battlespades.world.BlockDamageModel.Cell(0,0,0),yaw,0,0,null).stream().map(b->new Cell(b.cell().x(),b.cell().y(),b.cell().z())).toList();rotations.add(cells);bounds.add(new Bounds(new Cell(cells.stream().mapToInt(Cell::x).min().orElseThrow(),cells.stream().mapToInt(Cell::y).min().orElseThrow(),cells.stream().mapToInt(Cell::z).min().orElseThrow()),new Cell(cells.stream().mapToInt(Cell::x).max().orElseThrow(),cells.stream().mapToInt(Cell::y).max().orElseThrow(),cells.stream().mapToInt(Cell::z).max().orElseThrow())));}result.add(new Geometry(name,rotations,bounds,model.get().voxels().size()));}return List.copyOf(result);}
}
