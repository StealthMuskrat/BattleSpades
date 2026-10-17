// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.math.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.ShootPacket;

/** Source hard origin/LOS/aim gates and opt-in soft checks. Does not consume ammunition or resolve damage. */
public final class ShotValidation {
    private ShotValidation() {}
    public record Settings(double originTolerance,double aimTolerance,boolean enforceOrigin,boolean enforceAim) {
        public static Settings defaults(){return new Settings(1.5,10,false,false);}
        public static Settings from(org.tomlj.TomlTable raw){return new Settings(number(raw,"anticheat.shot_origin_tolerance",1.5),number(raw,"anticheat.aim_direction_tolerance_deg",10),flag(raw,"anticheat.enforce_shot_origin"),flag(raw,"anticheat.enforce_aim_direction"));}
        private static double number(org.tomlj.TomlTable raw,String key,double fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return n.doubleValue();throw new IllegalArgumentException(key+" must be finite numeric");}
        private static boolean flag(org.tomlj.TomlTable raw,String key){Object value=raw.get(key);if(value==null)return false;if(value instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
    }
    public record Claims(Integer label,double x,double y,double z,double aimX,double aimY,double aimZ) {
        public static Claims from(ShootPacket packet){var p=packet.origin();var d=packet.direction();return new Claims(packet.loopCount(),p.x(),p.y(),p.z(),d.x(),d.y(),d.z());}
    }
    public record View(Vector eye,Vector aim,Vector velocity,Integer appliedLabel,boolean bot,int tool,IntFunction<PlayerMotion.Eye> history) {}
    public record Reference(Vector atLoop,List<Vector> eyes){public Reference{eyes=List.copyOf(eyes);}}
    public record Event(String kind,boolean enforced,Map<String,Object> detail){public Event{detail=Map.copyOf(detail);}}
    public record Accepted(List<Vector> eyes,double slack,Vector origin,Vector direction){public Accepted{eyes=List.copyOf(eyes);}}
    public record Result(Accepted accepted,String reason){public boolean allowed(){return accepted!=null;}}
    /** Per connection aggregates survive life changes, as original Player.anticheat_stats does. */
    public static final class Statistics {
        private final Thread owner=Thread.currentThread();private final Map<String,Map<String,Long>> histograms=new LinkedHashMap<>();private final Map<String,Long> violations=new LinkedHashMap<>();
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Shot statistics outside owner");}
        public Map<String,Map<String,Long>> histograms(){owned();var copy=new LinkedHashMap<String,Map<String,Long>>();histograms.forEach((k,v)->copy.put(k,Map.copyOf(v)));return Map.copyOf(copy);}
        public Map<String,Long> violations(){owned();return Map.copyOf(violations);}
        private void count(String key,String bucket){owned();histograms.computeIfAbsent(key,k->new LinkedHashMap<>()).merge(bucket,1L,Long::sum);}
        private void report(Event event,Consumer<Event> observer){owned();violations.merge(event.kind+(event.enforced?"":":observed"),1L,Long::sum);observer.accept(event);}
    }
    public static View view(PlayerLife player){return new View(player.position(),player.aim(),player.generation()>0?PlayerMotion.clipped(player.body().state().velocity()):new Vector(0,0,0),player.inputTimeline().appliedLabel(),player.bot(),player.inventory().tool(),player.motion()::eyeAt);}
    public static Reference references(View player,Integer label){
        var at=label==null||player.history==null?null:player.history.apply(label);Vector atLoop=at==null?null:at.position();var eyes=new ArrayList<Vector>();if(atLoop!=null)eyes.add(atLoop);if(player.eye!=null&&!eyes.contains(player.eye))eyes.add(player.eye);
        if(player.history!=null&&player.appliedLabel!=null)for(int back:new int[]{6,12,24}){var past=player.history.apply(player.appliedLabel-back);if(past!=null&&past.position()!=null&&!eyes.contains(past.position()))eyes.add(past.position());}return new Reference(atLoop,eyes);
    }
    private static double round(double value,int digits){double rounded=new BigDecimal(value).setScale(digits,RoundingMode.HALF_EVEN).doubleValue();return rounded==0?Math.copySign(rounded,value):rounded;}
    private static String formatted(double value,int digits){var rounded=new BigDecimal(value).setScale(digits,RoundingMode.HALF_EVEN);return (rounded.signum()==0&&Double.doubleToRawLongBits(value)<0?"-":"")+rounded.toPlainString();}
    private static String bucket(double value,double[] bounds){for(double bound:bounds)if(value<=bound)return "<="+label(bound);return ">"+label(bounds[bounds.length-1]);}
    private static String label(double value){return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    private static void reject(View player,Statistics stats,Consumer<Event> observer,String kind,Map<String,Object> detail){stats.report(new Event(kind,true,detail),observer);if(!player.bot)stats.count("rejected",kind);}
    private static Result drop(String reason){return new Result(null,reason);}
    public static Result validate(Settings settings,View player,Claims packet,CombatGeometry.Terrain terrain,Statistics stats,Consumer<Event> observer){
        Objects.requireNonNull(settings);Objects.requireNonNull(player);Objects.requireNonNull(stats);Objects.requireNonNull(observer);
        if(packet==null)return drop("malformed_packet");
        for(double v:new double[]{packet.x,packet.y,packet.z,packet.aimX,packet.aimY,packet.aimZ})if(!Double.isFinite(v))return drop("non_finite_values");
        var origin=new Vector(packet.x,packet.y,packet.z);var direction=CombatGeometry.normalize(new Vector(packet.aimX,packet.aimY,packet.aimZ));if(direction==null)return drop("zero_orientation");
        var reference=references(player,packet.label);if(reference.eyes.isEmpty())return drop("no_server_eye");
        double nearest=reference.eyes.stream().mapToDouble(eye->CombatGeometry.distance(origin,eye)).min().orElseThrow();
        if(!(nearest<=8)){reject(player,stats,observer,"shot_origin_far",Map.of("distance",round(nearest,2)));return drop("shot_origin_far("+formatted(nearest,2)+")");}
        if(reference.eyes.stream().noneMatch(eye->CombatGeometry.segmentClear(terrain,eye,origin))){reject(player,stats,observer,"shot_origin_occluded",Map.of("distance",round(nearest,2)));return drop("shot_origin_occluded");}
        double slack=reference.atLoop!=null||player.velocity==null?0:Math.min(4,Math.sqrt(SpawnMath.sumSquares(player.velocity.x(),player.velocity.y(),player.velocity.z()))*32*.3);
        double error=Double.POSITIVE_INFINITY;if(reference.atLoop!=null)error=CombatGeometry.distance(origin,reference.atLoop);if(player.eye!=null)error=Math.min(error,CombatGeometry.distance(origin,player.eye));if(error==Double.POSITIVE_INFINITY)error=nearest;double tolerance=settings.originTolerance+slack;
        if(!player.bot){stats.count(reference.atLoop!=null?"origin_error":"origin_error_fallback",bucket(error,new double[]{.1,.25,.5,1,1.5,2,4,8}));if(error>tolerance){stats.report(new Event("shot_origin_drift",settings.enforceOrigin,Map.of("error",round(error,3),"allowed",round(tolerance,3),"ref",reference.atLoop!=null?"loop":"current")),observer);if(settings.enforceOrigin){stats.count("rejected","shot_origin_drift");return drop("shot_origin_drift("+formatted(error,2)+")");}}}
        var history=packet.label==null||player.history==null?null:player.history.apply(packet.label);var atAim=history==null||history.aim()==null?null:CombatGeometry.normalize(history.aim());var currentAim=player.aim==null?null:CombatGeometry.normalize(player.aim);var aims=new ArrayList<Vector>();if(atAim!=null)aims.add(atAim);if(currentAim!=null)aims.add(currentAim);if(aims.isEmpty())return drop("no_server_orientation");
        double referenceDot=CombatGeometry.dot(direction,aims.getFirst()),maximum=referenceDot;for(var aim:aims)maximum=Math.max(maximum,CombatGeometry.dot(direction,aim));
        if(!(maximum>=.25)){reject(player,stats,observer,"shot_direction_mismatch",Map.of("dot",round(maximum,3)));return drop("shot_direction_mismatch("+formatted(maximum,2)+")");}
        if(!player.bot){double angle=Math.acos(Math.max(-1,Math.min(1,referenceDot)))*(180/Math.PI);stats.count(atAim!=null?"aim_angle":"aim_angle_fallback",bucket(angle,new double[]{.5,1,2,5,10,20,45,90}));if(angle>settings.aimTolerance){stats.report(new Event("aim_direction_mismatch",settings.enforceAim,Map.of("angle_deg",round(angle,2),"allowed_deg",settings.aimTolerance,"ref",atAim!=null?"loop":"current","tool",player.tool)),observer);if(settings.enforceAim){stats.count("rejected","aim_direction_mismatch");return drop("aim_direction_mismatch("+formatted(angle,1)+"deg)");}}}
        return new Result(new Accepted(reference.eyes,slack,origin,direction),null);
    }
}
