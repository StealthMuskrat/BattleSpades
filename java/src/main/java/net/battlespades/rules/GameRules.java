// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.rules;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import org.tomlj.TomlTable;

/** Source game_rules.py catalog, typed coercion, aliases and selection gates. */
public final class GameRules {
    public record Definition(String key,String category,Object defaultValue,List<Object> choices,
                             List<String> modes,int toolId,int classId,boolean visible) {
        public Definition {choices=List.copyOf(choices);modes=List.copyOf(modes);}
    }
    private static final Map<String,Definition> CATALOG=load();
    private final Map<String,Object> values;
    private final Set<String> explicit;
    private GameRules(Map<String,Object> values,Set<String> explicit){this.values=Map.copyOf(values);this.explicit=Set.copyOf(explicit);}
    private static Map<String,Definition> load(){
        var definitions=new LinkedHashMap<String,Definition>();var rows=SourceCatalog.load("rules").getArray("rule");
        for(int i=0;i<rows.size();i++){
            var r=rows.getTable(i);String key=r.getString("key");
            definitions.put(key,new Definition(key,r.getString("category"),r.get("default"),r.getArray("choices").toList(),SourceCatalog.strings(r.getArray("modes")),Math.toIntExact(r.getLong("tool")),Math.toIntExact(r.getLong("class_id")),r.getBoolean("visible")));
        }
        return Collections.unmodifiableMap(definitions);
    }
    public static Map<String,Definition> definitions(){return CATALOG;}
    public static GameRules retailDefaults(){
        var values=new LinkedHashMap<String,Object>();CATALOG.forEach((k,d)->values.put(k,d.defaultValue));return new GameRules(values,Set.of());
    }
    public static GameRules serverDefaults(){return retailDefaults().compatibility(Map.of("RULE_RESPAWN_TIMES",5L,"RULE_SPAWN_PROTECTION_TIME",0.0));}
    /** Compatibility fields may have values outside the Match Lobby's menu choices. */
    public GameRules compatibility(Map<String,?> overrides){
        var resolved=new LinkedHashMap<>(values);
        overrides.forEach((k,v)->{if(!CATALOG.containsKey(k))throw new IllegalArgumentException("Unknown rule "+k);if(!explicit.contains(k))resolved.put(k,v);});
        return new GameRules(resolved,explicit);
    }
    public GameRules apply(TomlTable table){return apply(table.toMap());}
    public GameRules apply(Map<String,?> overrides){
        var resolved=new LinkedHashMap<>(values);var requested=new HashSet<>(explicit);
        for(var entry:overrides.entrySet()){
            String key=canonical(entry.getKey());Definition definition=CATALOG.get(key);
            resolved.put(key,coerce(definition,entry.getValue()));requested.add(key);
        }
        return new GameRules(resolved,requested);
    }
    private static String canonical(String raw){
        String text=raw.strip().toUpperCase(Locale.ROOT);if(!CATALOG.containsKey(text))text="RULE_"+text;
        if(!CATALOG.containsKey(text))throw new IllegalArgumentException("Unknown Match Lobby game rule: "+raw);return text;
    }
    private static Object coerce(Definition definition,Object raw){
        if(raw instanceof String value){
            String text=value.strip().toUpperCase(Locale.ROOT);
            if(text.equals("ON")||text.equals("TRUE"))raw=true;
            else if(text.equals("OFF")||text.equals("FALSE"))raw=definition.choices.contains(Boolean.FALSE)?Boolean.FALSE:Double.valueOf(0);
            else {
                try{
                    if(text.endsWith("%"))raw=Double.parseDouble(text.substring(0,text.length()-1))/100.0;
                    else if(text.contains("."))raw=Double.valueOf(text);else raw=Long.valueOf(text);
                }catch(NumberFormatException ignored){/* Report against the complete allowed set below. */}
            }
        }
        for(Object allowed:definition.choices){
            if(allowed instanceof Boolean){if(allowed.equals(raw))return allowed;}
            else if(allowed instanceof Long){if((raw instanceof Long||raw instanceof Integer)&&((Number)raw).longValue()==((Long)allowed).longValue())return allowed;}
            else if(allowed instanceof Double&&raw instanceof Number number&&number.doubleValue()==((Double)allowed).doubleValue())return allowed;
        }
        throw new IllegalArgumentException(definition.key+" must be one of "+definition.choices+"; received "+raw);
    }
    public Object get(String key){String canonical=key.toUpperCase(Locale.ROOT);if(!CATALOG.containsKey(canonical))throw new IllegalArgumentException("Unknown rule "+key);return values.get(canonical);}
    public double number(String key){Object value=get(key);return value instanceof Boolean b?(b?1:0):((Number)value).doubleValue();}
    public boolean enabled(String key){Object value=get(key);return value instanceof Boolean b?b:((Number)value).doubleValue()!=0;}
    public boolean explicit(String key){return explicit.contains(key);}
    public Set<String> explicit(){return explicit;}
    public boolean toolEnabled(int tool){return CATALOG.values().stream().filter(d->d.toolId>=0&&d.toolId==tool).allMatch(d->enabled(d.key));}
    public boolean classEnabled(int id){return CATALOG.values().stream().filter(d->d.classId>=0&&d.classId==id).allMatch(d->enabled(d.key));}
    public List<Integer> disabledTools(){return CATALOG.values().stream().filter(d->d.toolId>=0&&!enabled(d.key)).map(Definition::toolId).distinct().sorted().toList();}
    public List<Integer> disabledClasses(){return CATALOG.values().stream().filter(d->d.classId>=0&&!enabled(d.key)).map(Definition::classId).distinct().sorted().toList();}
}
