// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;

/** All 77 retail definitions and source constants; definitions alone do not evaluate gameplay rules. */
public final class AchievementCatalog {
    public record Definition(String token,String apiName,String displayName,String description,String stat,Long threshold){}
    private static final org.tomlj.TomlParseResult DATA=SourceCatalog.load("achievements");
    private static final Map<String,Definition> DEFINITIONS;private static final Map<String,List<Definition>> TIERS;
    static{var definitions=new LinkedHashMap<String,Definition>();var tiers=new LinkedHashMap<String,List<Definition>>();var rows=DATA.getArray("achievement");for(int i=0;i<rows.size();i++){var r=rows.getTable(i);var row=new Definition(r.getString("token"),r.getString("api_name"),r.getString("display_name"),r.getString("description"),r.getString("stat"),r.getLong("threshold"));definitions.put(row.apiName,row);if(row.stat!=null)tiers.computeIfAbsent(row.stat,k->new ArrayList<>()).add(row);}tiers.replaceAll((stat,list)->list.stream().sorted(Comparator.comparingLong(Definition::threshold)).toList());DEFINITIONS=Collections.unmodifiableMap(definitions);TIERS=Collections.unmodifiableMap(tiers);}
    private AchievementCatalog(){}
    public static Map<String,Definition> definitions(){return DEFINITIONS;}public static Map<String,List<Definition>> tiers(){return TIERS;}
    public static double number(String name){return ((Number)Objects.requireNonNull(DATA.get("constants."+name),name)).doubleValue();}
    public static int constant(String name){return Math.toIntExact(((Number)Objects.requireNonNull(DATA.get("constants."+name),name)).longValue());}
    public static Set<String> icons(){return Set.copyOf(SourceCatalog.strings(DATA.getArray("icons")));}
}
