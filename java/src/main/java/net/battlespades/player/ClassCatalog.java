// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.MovementProfile;
import net.battlespades.rules.GameRules;

/** Stock class tables and atomic selection from class_data.py / class_selection.py. */
public final class ClassCatalog {
    public record Definition(int id,List<List<Integer>> slots,List<Integer> common,Set<String> prefabs,
                             Set<Integer> ugc,List<Double> movement,boolean uphill,double damage,double headshot){
        public Definition {slots=slots.stream().map(List::copyOf).toList();common=List.copyOf(common);prefabs=Set.copyOf(prefabs);ugc=Set.copyOf(ugc);movement=List.copyOf(movement);}
    }
    public record Selection(int classId,List<Integer> loadout,List<String> prefabs,List<Integer> ugcTools){
        public Selection{loadout=List.copyOf(loadout);prefabs=List.copyOf(prefabs);ugcTools=List.copyOf(ugcTools);}
    }
    private static final Map<Integer,Definition> CLASSES=load();
    private ClassCatalog(){}
    private static Map<Integer,Definition> load(){
        var result=new TreeMap<Integer,Definition>();var rows=SourceCatalog.load("classes").getArray("class");
        for(int i=0;i<rows.size();i++){
            var r=rows.getTable(i);int id=Math.toIntExact(r.getLong("id"));var slots=new ArrayList<List<Integer>>();
            for(int j=0;j<4;j++)slots.add(SourceCatalog.integers(r.getArray("slots").getArray(j)));
            var movement=r.getArray("movement").toList().stream().map(v->((Number)v).doubleValue()).toList();
            result.put(id,new Definition(id,slots,SourceCatalog.integers(r.getArray("common")),new HashSet<>(SourceCatalog.strings(r.getArray("prefabs"))),new HashSet<>(SourceCatalog.integers(r.getArray("ugc"))),movement,r.getBoolean("uphill"),r.getDouble("damage"),r.getDouble("headshot")));
        }
        return Collections.unmodifiableMap(result);
    }
    public static Map<Integer,Definition> definitions(){return CLASSES;}
    public static Definition get(int id){return CLASSES.getOrDefault(id,CLASSES.get(0));}
    public static Selection normalize(int requested,Collection<Integer> selected,Collection<String> prefabs,
                                      Collection<Integer> ugc,Integer fallback,Collection<Integer> disabled){
        int id=CLASSES.containsKey(requested)?requested:fallback!=null&&CLASSES.containsKey(fallback)?fallback:5;
        Definition d=CLASSES.get(id);var blocked=new HashSet<>(disabled);var requestedTools=new LinkedHashSet<>(selected);var chosen=new ArrayList<Integer>();
        for(var slot:d.slots){
            var options=slot.stream().filter(t->!blocked.contains(t)).toList();
            if(!options.isEmpty())chosen.add(options.stream().filter(requestedTools::contains).findFirst().orElse(options.getFirst()));
        }
        var common=d.common.stream().filter(t->!blocked.contains(t)).toList();
        if(common.contains(5))chosen.addFirst(5);
        for(int tool:common)if(tool!=5&&tool!=22)chosen.add(tool);
        if(common.contains(22)&&(requestedTools.isEmpty()||requestedTools.contains(22)))chosen.add(22);
        if(!blocked.contains(23))chosen.add(23);
        var normalizedPrefabs=new LinkedHashSet<String>();for(String name:prefabs)if(d.prefabs.contains(name.toLowerCase(Locale.ROOT)))normalizedPrefabs.add(name);
        var normalizedUgc=new LinkedHashSet<Integer>();for(int tool:ugc)if(d.ugc.contains(tool))normalizedUgc.add(tool);
        return new Selection(id,List.copyOf(new LinkedHashSet<>(chosen)),List.copyOf(normalizedPrefabs),List.copyOf(normalizedUgc));
    }
    public static Selection normalize(int id,Collection<Integer> selected,GameRules rules){return normalize(id,selected,List.of(),List.of(),null,rules.disabledTools());}
    /** The advertised rule scale is rounded with Python's ties-to-even before native float stores. */
    public static double speedScale(int id,String mode,GameRules rules){
        double scale=rules.number("RULE_CHARACTER_SPEED");
        if((id==4||id==14||id==15)&&(mode.equals("zom")||mode.equals("zombie")))scale*=rules.number("RULE_CLASS_SPEED");
        return Math.rint(scale*64.0)/64.0;
    }
    public static MovementProfile movement(int id,String mode,GameRules rules){
        var d=get(id);var m=d.movement;double scale=speedScale(id,mode,rules);
        return new MovementProfile((float)(m.get(0)*scale),(float)(m.get(1)*scale),(float)(m.get(2)*scale),m.get(3).floatValue(),m.get(4).floatValue(),m.get(5).floatValue(),m.get(6).floatValue(),m.get(7).floatValue(),rules.enabled("RULE_ENABLE_FALL_ON_WATER_DAMAGE")?m.get(8).floatValue():0,d.uphill);
    }
    /** Stock inventory gate only; active-tool, mounted-entity and mode authorization remain caller policies. */
    public static boolean inventoryToolAuthorized(boolean alive,boolean spawned,int tool,Selection selection,GameRules rules){
        return alive&&spawned&&tool>=0&&tool<66&&rules.toolEnabled(tool)&&selection.loadout.contains(tool);
    }
}
