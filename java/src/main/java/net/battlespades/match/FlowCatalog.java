// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import net.battlespades.catalog.SourceCatalog;
/** Immutable original combat/clock data; behavior is implemented separately. */
public final class FlowCatalog {
    private FlowCatalog(){}
    private static final org.tomlj.TomlParseResult DATA=SourceCatalog.load("match-flow");
    public static long value(String key){return Objects.requireNonNull(DATA.getLong(key),key);}
    public static double number(String key){return DATA.getDouble(key);}
    public record Cue(double threshold,String stringId,List<String> parameters){}
    public static final List<Cue> CUES;
    static {var list=new ArrayList<Cue>();var rows=DATA.getArray("cue");for(int i=0;i<rows.size();i++){var r=rows.getTable(i);list.add(new Cue(r.getDouble("threshold"),r.getString("string"),SourceCatalog.strings(r.getArray("parameters"))));}CUES=List.copyOf(list);}
}
