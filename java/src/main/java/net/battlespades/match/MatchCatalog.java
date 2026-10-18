// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import net.battlespades.catalog.SourceCatalog;
/** Immutable profile mappings exported from the unchanged source; policies remain handwritten. */
public final class MatchCatalog {
    private MatchCatalog(){}
    private static final org.tomlj.TomlParseResult DATA=SourceCatalog.load("match");
    public static int constant(String name){return Math.toIntExact(Objects.requireNonNull(DATA.getTable("constants").getLong(name),name));}
    public static final Map<Integer,List<Integer>> AGGREGATES;
    static {var result=new LinkedHashMap<Integer,List<Integer>>();var rows=DATA.getArray("aggregate");for(int i=0;i<rows.size();i++){var row=rows.getTable(i);result.put(Math.toIntExact(row.getLong("id")),SourceCatalog.integers(row.getArray("members")));}AGGREGATES=Collections.unmodifiableMap(result);}
    public static Integer classKill(int classId,int tool){var table=DATA.getTable("class_kill");Long result=table.getLong(classId+"_"+tool);return result==null?null:Math.toIntExact(result);}
    public static Integer killTool(int type){Long result=DATA.getTable("kill_tool").getLong("k"+type);return result==null?null:Math.toIntExact(result);}
    public static Integer mapStat(String name){Long result=DATA.getTable("map_stat").getLong(List.of(name));return result==null?null:Math.toIntExact(result);}
}
