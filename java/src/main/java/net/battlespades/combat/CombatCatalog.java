// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import org.tomlj.TomlTable;

/** Original game_constants weapon profile data; a profile does not authorize selecting or using a tool. */
public final class CombatCatalog {
    public record Profile(int id,String name,String category,double damage,double headshotMultiplier,double blockDamage,
        double interval,double range,int clip,int reserve,double reload,int pellets,double spread,int killType,double headDamage,
        int damageType,boolean melee,boolean projectile,double blastRadius,double fuse,List<Double> partDamage,double entityDamage,
        int initialReserve,int restock,boolean clipReload,double shortRange,double accuracyMinimum,double accuracyMaximum,double secondaryInterval) {
        public Profile {partDamage=List.copyOf(partDamage);}
        public double damageForPart(int part){
            if(partDamage.isEmpty())return part==0&&headDamage!=0?headDamage:damage;
            int slot=switch(part){case 0->1;case 2->2;case 3->3;case 4->4;default->0;};return partDamage.get(slot);
        }
    }
    public static final Map<Integer,Profile> WEAPONS;
    public record Accuracy(double minimum,double maximum,double increase,double reduction) {}
    public static final Map<Integer,Accuracy> ACCURACY;
    public static final Map<Integer,Double> ZOOM;
    public static final Set<Integer> RAW_TOOLS,SPADES;
    public static final int DEFAULT_WEAPON,SPADE;
    public static final double FIRE_GRACE,RELOAD_GRACE,RELOAD_FRACTION;
    static {
        var data=SourceCatalog.load("combat");var settings=data.getArray("settings").getTable(0);
        DEFAULT_WEAPON=i(settings,"default_weapon");SPADE=i(settings,"spade");RAW_TOOLS=Set.copyOf(SourceCatalog.integers(settings.getArray("raw_tools")));SPADES=Set.copyOf(SourceCatalog.integers(settings.getArray("spades")));
        FIRE_GRACE=n(settings,"fire_grace");RELOAD_GRACE=n(settings,"reload_grace");RELOAD_FRACTION=n(settings,"reload_fraction");
        var profiles=new LinkedHashMap<Integer,Profile>();var rows=data.getArray("weapon");
        for(int index=0;index<rows.size();index++){
            var r=rows.getTable(index);int id=i(r,"tool_id");
            profiles.put(id,new Profile(id,r.getString("name"),r.getString("category"),n(r,"base_damage"),n(r,"headshot_multiplier"),n(r,"block_damage"),n(r,"fire_interval"),n(r,"max_range"),i(r,"clip_size"),i(r,"reserve_ammo"),n(r,"reload_time"),i(r,"pellet_count"),n(r,"spread"),i(r,"kill_type"),n(r,"head_damage"),i(r,"damage_type"),r.getBoolean("is_melee"),r.getBoolean("is_projectile"),n(r,"blast_radius"),n(r,"fuse_time"),r.getArray("part_damage").toList().stream().map(v->((Number)v).doubleValue()).toList(),n(r,"entity_damage"),i(r,"initial_reserve"),i(r,"restock_amount"),r.getBoolean("clip_reload"),n(r,"short_ranged_distance"),n(r,"accuracy_min"),n(r,"accuracy_max"),n(r,"secondary_fire_interval")));
        }
        WEAPONS=Collections.unmodifiableMap(profiles);
        var accuracy=new LinkedHashMap<Integer,Accuracy>();var curves=data.getArray("accuracy");
        for(int index=0;index<curves.size();index++){var r=curves.getTable(index);accuracy.put(i(r,"tool"),new Accuracy(n(r,"minimum"),n(r,"maximum"),n(r,"increase"),n(r,"reduction")));}ACCURACY=Collections.unmodifiableMap(accuracy);
        var zoom=new LinkedHashMap<Integer,Double>();var zooms=data.getArray("zoom");
        for(int index=0;index<zooms.size();index++){var r=zooms.getTable(index);zoom.put(i(r,"tool"),n(r,"accuracy"));}ZOOM=Collections.unmodifiableMap(zoom);
    }
    private CombatCatalog() {}
    private static int i(TomlTable r,String key){return Math.toIntExact(r.getLong(key));}
    private static double n(TomlTable r,String key){return ((Number)r.get(key)).doubleValue();}
    public static Profile get(int tool){return Objects.requireNonNull(WEAPONS.get(tool),"Unknown weapon profile");}
}
