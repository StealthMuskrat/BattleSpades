// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import org.tomlj.TomlTable;

/** Source data for life reset and inventory; does not implement firing or reload timing. */
public final class LifeCatalog {
    public record Weapon(int clip,int reserve,int initial,int restock,double reload,boolean clipReload){}
    public record Blocks(int start,int maximum){}
    public record Oriented(int magazineMax,int magazineInitial,int reserveMax,int reserveInitial,int restock){}
    public record Deployable(int initial,int maximum,int restock,double interval){}
    public record Launcher(int clip,double reload,double interval,boolean cycles){}
    public static final Map<Integer,Weapon> WEAPONS;
    public static final Map<Integer,Blocks> BLOCKS;
    public static final Map<Integer,Oriented> ORIENTED;
    public static final Map<Integer,Deployable> DEPLOYABLES;
    public static final Map<Integer,Double> CADENCE,PACK_FUEL;
    public static final Map<Integer,Launcher> LAUNCHERS;
    public static final Set<Integer> TRANSITIONS,DOMINATION_RESET,ONE_HIT,ZOMBIES,WEAPON_TOOLS;
    public static final double MULTIKILL_GAP,INTERACTION_WINDOW;
    public static final int HEALTH,DEFAULT_WEAPON,TURRET_INITIAL,TURRET_MAX,TURRET_RESTOCK,DISGUISE_INITIAL,DISGUISE_MAX,DISGUISE_RESTOCK,FALL_KILL,DOMINATION_KILLS,TRANSITION_CREDIT_KILL;
    static {
        var data=SourceCatalog.load("life");var settings=data.getArray("settings").getTable(0);
        HEALTH=i(settings,"health");DEFAULT_WEAPON=i(settings,"weapon");TURRET_INITIAL=i(settings,"turret_initial");TURRET_MAX=i(settings,"turret_max");TURRET_RESTOCK=i(settings,"turret_restock");DISGUISE_INITIAL=i(settings,"disguise_initial");DISGUISE_RESTOCK=i(settings,"disguise_restock");FALL_KILL=i(settings,"fall_kill");MULTIKILL_GAP=n(settings,"multikill_gap");DOMINATION_KILLS=i(settings,"domination_kills");
        TRANSITIONS=Set.copyOf(SourceCatalog.integers(settings.getArray("transitions")));DOMINATION_RESET=Set.copyOf(SourceCatalog.integers(settings.getArray("domination_reset")));ONE_HIT=Set.copyOf(SourceCatalog.integers(settings.getArray("one_hit")));ZOMBIES=Set.copyOf(SourceCatalog.integers(settings.getArray("zombies")));
        WEAPON_TOOLS=Set.copyOf(SourceCatalog.integers(settings.getArray("weapon_tools")));
        DISGUISE_MAX=i(settings,"disguise_max");
        INTERACTION_WINDOW=n(settings,"interaction_window");
        TRANSITION_CREDIT_KILL=i(settings,"transition_credit_kill");
        var weapons=new LinkedHashMap<Integer,Weapon>();for(var r:rows(data,"weapon"))weapons.put(i(r,"tool"),new Weapon(i(r,"clip"),i(r,"reserve"),i(r,"initial"),i(r,"restock"),n(r,"reload"),r.getBoolean("clip_reload")));WEAPONS=Collections.unmodifiableMap(weapons);
        var blocks=new TreeMap<Integer,Blocks>();for(var r:rows(data,"blocks"))blocks.put(i(r,"id"),new Blocks(i(r,"start"),i(r,"maximum")));BLOCKS=Map.copyOf(blocks);
        var oriented=new TreeMap<Integer,Oriented>();for(var r:rows(data,"oriented")){var v=SourceCatalog.integers(r.getArray("stock"));oriented.put(i(r,"tool"),new Oriented(v.get(0),v.get(1),v.get(2),v.get(3),v.get(4)));}ORIENTED=Collections.unmodifiableMap(oriented);
        var deployables=new TreeMap<Integer,Deployable>();for(var r:rows(data,"deployable"))deployables.put(i(r,"tool"),new Deployable(i(r,"initial"),i(r,"maximum"),i(r,"restock"),n(r,"interval")));DEPLOYABLES=Collections.unmodifiableMap(deployables);
        var cadence=new TreeMap<Integer,Double>();for(var r:rows(data,"cadence"))cadence.put(i(r,"tool"),n(r,"interval"));CADENCE=Map.copyOf(cadence);
        var fuel=new TreeMap<Integer,Double>();for(var r:rows(data,"pack"))fuel.put(i(r,"id"),n(r,"fuel"));PACK_FUEL=Map.copyOf(fuel);
        var launchers=new TreeMap<Integer,Launcher>();for(var r:rows(data,"launcher"))launchers.put(i(r,"tool"),new Launcher(i(r,"clip"),n(r,"reload"),n(r,"interval"),r.getBoolean("cycles")));LAUNCHERS=Map.copyOf(launchers);
    }
    private LifeCatalog(){}
    private static int i(TomlTable t,String k){return Math.toIntExact(t.getLong(k));}
    private static double n(TomlTable t,String k){return ((Number)t.get(k)).doubleValue();}
    private static List<TomlTable> rows(TomlTable t,String k){var a=t.getArray(k);var out=new ArrayList<TomlTable>();for(int j=0;j<a.size();j++)out.add(a.getTable(j));return out;}
}
