// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import java.util.function.BiPredicate;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Original retail blast geometry and impulse arithmetic; terrain/lifecycle/player commit is separate. */
public final class ExplosionGeometry {
    public record Stock(String name,int killType,double radius,double knockbackMin,double knockbackMax,double damage,boolean classic) { }
    public record Segment(Vector start,Vector end) { }
    private static final double SELF,TEAM,STANDING,CROUCHING,RAY_START;
    private static final int NEUTRAL;
    public static final int PREDICTION_FRAMES,IMPULSE_LIMIT;
    public static final Set<Integer> STOCK_DAMAGE_TYPES;
    private static final double[] WEIGHTS,STANDING_OFFSETS,CROUCHING_OFFSETS;
    private static final List<Stock> STOCK;
    static {
        var data=SourceCatalog.load("explosions");var s=data.getArray("settings").getTable(0);SELF=number(s.get("self_reduction"));TEAM=number(s.get("team_reduction"));NEUTRAL=Math.toIntExact(s.getLong("neutral_team"));STANDING=number(s.get("body_standing"));CROUCHING=number(s.get("body_crouching"));RAY_START=number(s.get("ray_start"));
        PREDICTION_FRAMES=Math.toIntExact(s.getLong("prediction_frames"));IMPULSE_LIMIT=Math.toIntExact(s.getLong("impulse_limit"));STOCK_DAMAGE_TYPES=Set.copyOf(SourceCatalog.integers(s.getArray("stock_damage_types")));
        WEIGHTS=array(s.getArray("weights"));STANDING_OFFSETS=array(s.getArray("offsets_standing"));CROUCHING_OFFSETS=array(s.getArray("offsets_crouching"));var stock=new ArrayList<Stock>();var rows=data.getArray("stock");for(int i=0;i<rows.size();i++){var r=rows.getTable(i);stock.add(new Stock(r.getString("name"),Math.toIntExact(r.getLong("kill_type")),number(r.get("radius")),number(r.get("knockback_min")),number(r.get("knockback_max")),number(r.get("damage")),r.getBoolean("classic")));}STOCK=List.copyOf(stock);
    }
    private ExplosionGeometry() { }
    private static double number(Object value){return ((Number)value).doubleValue();}
    private static double[] array(org.tomlj.TomlArray values){var out=new double[values.size()];for(int i=0;i<out.length;i++)out[i]=number(values.get(i));return out;}
    public static List<Stock> stock(){return STOCK;}
    public static Stock stockFor(int killType,double damage){for(var s:STOCK)if(s.killType==killType&&Math.abs(s.damage-damage)<=1e-6)return s;return null;}
    public static double falloff(Vector origin,Vector target,double radius,double bodyOffset){
        if(radius<=0)return 0;double x=target.x()-origin.x(),y=target.y()-origin.y(),z=target.z()+bodyOffset-origin.z(),distance=x*x+y*y+z*z,squared=radius*radius;if(distance>=squared)return 0;return (squared-distance)/squared;
    }
    public static List<Vector> sightPoints(Vector position,boolean crouched){var out=new ArrayList<Vector>(3);for(double offset:crouched?CROUCHING_OFFSETS:STANDING_OFFSETS)out.add(new Vector(position.x(),position.y(),position.z()+offset));return List.copyOf(out);}
    public static Segment raySegment(Vector origin,Vector point){double x=point.x()-origin.x(),y=point.y()-origin.y(),z=point.z()-origin.z();if(x*x+y*y+z*z<=1e-18)return null;var start=new Vector(origin.x()+x*RAY_START,origin.y()+y*RAY_START,origin.z()+z*RAY_START);return new Segment(start,new Vector(start.x()+x,start.y()+y,start.z()+z));}
    @FunctionalInterface public interface Raycast {boolean hit(Vector origin,Vector direction,double length);}
    public static BiPredicate<Vector,Vector> segmentBlocker(Raycast raycast){Objects.requireNonNull(raycast);return (start,end)->{double x=end.x()-start.x(),y=end.y()-start.y(),z=end.z()-start.z(),length=StrictMath.sqrt(x*x+y*y+z*z);if(length<=1e-9)return false;return raycast.hit(start,new Vector(x/length,y/length,z/length),length);};}
    public static boolean rayBlocked(BiPredicate<Vector,Vector> blocked,Vector origin,Vector point){var segment=raySegment(origin,point);return segment!=null&&blocked.test(segment.start,segment.end);}
    public static double losFraction(BiPredicate<Vector,Vector> blocked,Vector origin,Vector position,boolean crouched){if(blocked==null)return 1;double fraction=0;var points=sightPoints(position,crouched);for(int i=0;i<WEIGHTS.length;i++)if(!rayBlocked(blocked,origin,points.get(i)))fraction+=WEIGHTS[i];return fraction;}
    public static double playerDamage(Vector origin,Vector position,double radius,double damage,boolean crouched,double los,boolean self,Integer team,boolean classic){double falloff=falloff(origin,position,radius,crouched?CROUCHING:STANDING);if(falloff<=0||los<=0)return 0;double amount=damage*falloff*los;if(!classic){if(self)amount*=SELF;else if(team!=null&&team==NEUTRAL)amount*=TEAM;}return amount;}
    public static double entityDamage(Vector origin,Vector position,double radius,double damage){return damage*falloff(origin,position,radius,0);}
    public static double[] scaleKnockback(double minimum,double maximum,double los){double fraction=los>0?los:0;return new double[]{minimum*fraction,maximum*fraction};}
    public static Vector impulse(Vector origin,Vector position,double radius,double minimum,double maximum,boolean crouched){
        double falloff=falloff(origin,position,radius,crouched?CROUCHING:STANDING);if(falloff<=0)return null;double x=position.x()-origin.x(),y=position.y()-origin.y(),z=position.z()-origin.z(),length=StrictMath.sqrt(x*x+y*y+z*z);if(length<=1e-9)return null;double magnitude=minimum+falloff*(maximum-minimum);if(magnitude==0)return null;double scale=magnitude/length;return new Vector(x*scale,y*scale,z*scale);
    }
}
