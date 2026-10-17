// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;

/** Source direct-hit victim scaling and frontal shield mitigation. Accepted health mutation belongs to LifeRuntime. */
public final class DirectDamage {
    private DirectDamage() {}
    public record Victim(int classId,int pack,boolean flying,boolean hardcore) {}
    public record Shield(int tool,boolean displayed,Vector position,Vector orientation) {}
    public record ShieldImpact(Vector position,boolean melee) {}
    private static final Set<Integer> HARDCORE;
    private static final Map<Integer,Double> PACK_DAMAGE;
    private static final int HEAVY,SHIELD;
    public static final int HEADSHOT_KILL,ENTITY_PART;
    public static final double MELEE_RANGE,MOUNTED_GUN_INTERVAL;
    private static final double ABSORPTION,KNOCKBACK;
    static {
        var data=SourceCatalog.load("direct-damage");var settings=data.getArray("settings").getTable(0);HARDCORE=Set.copyOf(SourceCatalog.integers(settings.getArray("hardcore_classes")));MOUNTED_GUN_INTERVAL=((Number)settings.get("mounted_gun_interval")).doubleValue();MELEE_RANGE=((Number)settings.get("melee_range")).doubleValue();HEAVY=Math.toIntExact(settings.getLong("heavy"));HEADSHOT_KILL=Math.toIntExact(settings.getLong("headshot_kill"));ENTITY_PART=Math.toIntExact(settings.getLong("entity_part"));SHIELD=Math.toIntExact(settings.getLong("shield_tool"));ABSORPTION=Math.max(0,Math.min(1,((Number)settings.get("shield_absorption")).doubleValue()/100));KNOCKBACK=((Number)settings.get("shield_knockback")).doubleValue();
        var packs=new HashMap<Integer,Double>();var rows=data.getArray("pack");for(int i=0;i<rows.size();i++){var r=rows.getTable(i);packs.put(Math.toIntExact(r.getLong("id")),((Number)r.get("damage")).doubleValue());}PACK_DAMAGE=Map.copyOf(packs);
    }
    /** Hardcore identity is trusted mode state; this does not enable the unported Hardcore lifecycle. */
    public static Victim victim(PlayerLife player,boolean hardcore){return new Victim(player.selection().classId(),player.jetpack(),player.flight().snapshot().active(),hardcore);}
    public static boolean riotShield(int tool){return tool==SHIELD;}
    public static Shield shield(PlayerLife player){return new Shield(player.inventory().tool(),(player.motion().snapshot().actions()&16)!=0,player.position(),player.aim());}
    public static double amount(CombatCatalog.Profile profile,boolean headshot,Integer part,Victim victim){
        int hitPart=part==null?(headshot?0:1):part;double damage=profile.damageForPart(hitPart);
        if(victim!=null){
            double scale;
            if(victim.hardcore&&HARDCORE.contains(victim.classId))scale=victim.classId==HEAVY&&!headshot&&!profile.melee()?.8:1.;
            else{
                var row=ClassCatalog.definitions().get(victim.classId);scale=row==null?1:row.damage();scale*=victim.flying?PACK_DAMAGE.getOrDefault(victim.pack,1.):1.;
                if(headshot&&!profile.melee())scale*=row==null?1:row.headshot();
            }
            damage*=scale;
        }
        return damage<=0?0:damage;
    }
    public static double entityAmount(CombatCatalog.Profile profile){return profile.entityDamage()>0?profile.entityDamage():profile.damage();}
    /** The impact callback occurs before health admission, including protected victims, exactly as the source shield helper. */
    public static double mitigate(Shield shield,Vector attacker,double damage,Vector impact,boolean melee,Consumer<ShieldImpact> effects){
        if(shield.tool!=SHIELD||!shield.displayed)return damage;
        var source=CombatGeometry.normalize(CombatGeometry.subtract(attacker,shield.position));var facing=CombatGeometry.normalize(shield.orientation);if(source==null||facing==null||CombatGeometry.dot(facing,source)<=0)return damage;
        if(impact!=null&&ABSORPTION>0&&damage>0)Objects.requireNonNull(effects).accept(new ShieldImpact(impact,melee));return Math.max(0,damage*(1-ABSORPTION));
    }
    /** Source facade velocity is the input; the caller commits the result through the real body/facade store. */
    public static Vector shieldKnockback(Vector attacker,Vector attackerAim,Vector target,Vector velocity){
        double dx=target.x()-attacker.x(),dy=target.y()-attacker.y(),length=SpawnMath.hypot(dx,dy);
        if(length<=1e-6){dx=attackerAim.x();dy=attackerAim.y();length=SpawnMath.hypot(dx,dy);}if(length<=1e-6)return velocity;
        return new Vector(velocity.x()+dx/length*KNOCKBACK,velocity.y()+dy/length*KNOCKBACK,velocity.z());
    }
}
