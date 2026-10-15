// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.scene;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;

/** Wire metadata only. A catalog entry does not implement a mode's lifecycle. */
public final class ModeCatalog {
    public record Definition(String code,int id,String title,String description,List<String> info,
                             int scoreLimit,double timeLimit,boolean classic,boolean mafia,List<Integer> classes){
        public Definition{info=List.copyOf(info);classes=List.copyOf(classes);}
    }
    private static final Map<String,Definition> MODES;
    private static final Map<String,String> ALIASES;
    static {
        var data=SourceCatalog.load("modes");var modes=new LinkedHashMap<String,Definition>();var rows=data.getArray("mode");
        for(int i=0;i<rows.size();i++){
            var r=rows.getTable(i);String code=r.getString("code");modes.put(code,new Definition(code,Math.toIntExact(r.getLong("id")),r.getString("title"),r.getString("description"),SourceCatalog.strings(r.getArray("info")),Math.toIntExact(r.getLong("score")),((Number)r.get("time")).doubleValue(),r.getBoolean("classic"),r.getBoolean("mafia"),SourceCatalog.integers(r.getArray("classes"))));
        }
        MODES=Collections.unmodifiableMap(modes);var aliases=new HashMap<String,String>();
        aliases.putAll(Map.ofEntries(Map.entry("zombie","zom"),Map.entry("classic_ctf","cctf"),Map.entry("classic-ctf","cctf"),Map.entry("multihill","mh"),Map.entry("multi-hill","mh"),Map.entry("territory_control","tc"),Map.entry("territory-control","tc"),Map.entry("diamond","dia"),Map.entry("diamond_mine","dia"),Map.entry("demolition","dem"),Map.entry("occupation","oc"),Map.entry("tutorial","tut")));
        rows=data.getArray("alias");for(int i=0;i<rows.size();i++){var r=rows.getTable(i);aliases.put(r.getString("name"),r.getString("target"));}
        ALIASES=Map.copyOf(aliases);
    }
    private ModeCatalog(){}
    public static Map<String,Definition> definitions(){return MODES;}
    public static Definition get(String requested){
        String code=requested==null?"":requested.strip().toLowerCase(Locale.ROOT);
        if(code.startsWith("hardcore_")&&MODES.containsKey("hc_"+code.substring(9)))code="hc_"+code.substring(9);
        code=ALIASES.getOrDefault(code,code);return MODES.getOrDefault(code,MODES.get("nor"));
    }
}
