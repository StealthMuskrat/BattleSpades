// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import static net.battlespades.bot.BotState.Cell;

/** Source melee footprints and throughput estimates, shared by excavation proposals. */
public record BotDigProfile(int toolId, int damageType, double blockDamage, String pattern,
                            double fireInterval, boolean secondary, double randomExtra) {
    private static final Map<Integer,BotDigProfile> PRIMARY = load("primary");
    private static final Map<Integer,BotDigProfile> NAVIGATION = load("navigation");
    private static Map<Integer,BotDigProfile> load(String table) {
        var rows=SourceCatalog.load("bot-navigation").getArray(table);
        var result=new LinkedHashMap<Integer,BotDigProfile>();
        for(int i=0;i<rows.size();i++) {
            var r=rows.getTable(i);
            var p=new BotDigProfile(Math.toIntExact(r.getLong("tool_id")),Math.toIntExact(r.getLong("damage_type")),
                r.getDouble("block_damage"),r.getString("pattern"),r.getDouble("fire_interval"),r.getBoolean("secondary"),r.getDouble("random_extra"));
            result.put(p.toolId(),p);
        }
        return Collections.unmodifiableMap(result);
    }
    public static Optional<BotDigProfile> primary(int tool) { return Optional.ofNullable(PRIMARY.get(tool)); }
    public static Optional<BotDigProfile> navigation(int tool) { return Optional.ofNullable(NAVIGATION.get(tool)); }
    public int swingsForHealth(double health,boolean worstCase) {
        if(blockDamage<=0)return 0;
        return Math.max(1,(int)Math.ceil(health/(blockDamage+(worstCase?0:.5*randomExtra))-1e-9));
    }
    public int swingsForHealth(double health) { return swingsForHealth(health,false); }
    public int swingsPerBlock() { return swingsForHealth(5); }
    public static Optional<BotDigProfile> best(Collection<Integer> tools) {
        var order=Comparator.comparingDouble((BotDigProfile p)->(p.pattern.equals("single")?1:2)/Math.max(.05,p.fireInterval*p.swingsPerBlock()))
            .thenComparingInt(p->p.pattern.equals("cube")?1:0).thenComparingDouble(BotDigProfile::blockDamage).thenComparingInt(p->-p.toolId);
        return tools.stream().map(NAVIGATION::get).filter(Objects::nonNull).max(order);
    }
    public List<Cell> footprint(Cell at) {
        var cells=new ArrayList<Cell>();
        if(pattern.equals("cube"))for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)cells.add(new Cell(at.x()+dx,at.y()+dy,at.z()+dz));
        else if(pattern.equals("column"))for(int dz=-1;dz<=1;dz++)cells.add(new Cell(at.x(),at.y(),at.z()+dz));
        else {cells.add(at);if(pattern.equals("machete_vertical_pair"))cells.add(new Cell(at.x(),at.y(),at.z()+1));}
        return List.copyOf(cells);
    }
}
