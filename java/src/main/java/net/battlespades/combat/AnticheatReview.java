// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.math.*;
import java.util.*;
import java.util.function.IntFunction;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerMotion;
import org.tomlj.TomlTable;

/** Statistical review of accepted gameplay. Reports never authorize enforcement. */
public final class AnticheatReview {
    private AnticheatReview(){}
    public static final Set<Integer> SINGLE_RAY=Set.of(6,7,8,15,17,18,19,35,36,60,61,62);
    public static boolean hitscan(int tool){return SINGLE_RAY.contains(tool)||tool==9||tool==10||tool==53;}
    private static final List<String> SUSTAINED=List.of("shot_origin_drift","aim_direction_mismatch","input_backlog","input_label_ahead");
    private static final Map<String,Double> DEFAULTS=Map.ofEntries(
        Map.entry("summary_interval_seconds",60.),Map.entry("flag_min_score",1.),Map.entry("headshot_kill_ratio",.60),Map.entry("headshot_kill_min_kills",30.),Map.entry("headshot_hit_ratio",.55),Map.entry("headshot_hit_min_hits",60.),
        Map.entry("accuracy_min_shots",100.),Map.entry("accuracy_min_population",20.),Map.entry("accuracy_percentile",99.),Map.entry("accuracy_min_margin",.15),
        Map.entry("snap_min_deg",20.),Map.entry("snap_frames",2.),Map.entry("snap_head_radius",.35),Map.entry("snap_margin_deg",.75),Map.entry("snap_min_events",4.),Map.entry("snap_min_engaged",20.),Map.entry("snap_ratio",.10),
        Map.entry("reaction_acquire_deg",10.),Map.entry("reaction_history_frames",60.),Map.entry("reaction_min_samples",15.),Map.entry("reaction_median_ms",90.),Map.entry("reaction_reengage_seconds",3.),Map.entry("engage_cone_deg",4.),
        Map.entry("pellet_seed_min_shots",64.),Map.entry("pellet_seed_top_share",.2),Map.entry("sustained_min_minutes",5.),Map.entry("sustained_min_count",20.),Map.entry("sustained_per_minute",3.));
    public record Settings(Map<String,Double> values){
        public Settings{values=Map.copyOf(values);for(var e:DEFAULTS.entrySet())if(!values.containsKey(e.getKey())||!Double.isFinite(values.get(e.getKey()))||values.get(e.getKey())<0)throw new IllegalArgumentException("Invalid anticheat review setting: "+e.getKey());
            for(var key:List.of("headshot_kill_ratio","headshot_hit_ratio","snap_ratio","pellet_seed_top_share","sustained_per_minute"))if(values.get(key)<=0)throw new IllegalArgumentException("anticheat."+key+" must be positive");
        }
        public static Settings defaults(){return new Settings(DEFAULTS);}
        public static Settings from(TomlTable raw){var values=new HashMap<>(DEFAULTS);for(var key:DEFAULTS.keySet()){Object v=raw.get("anticheat."+key);if(v!=null){if(!(v instanceof Number n))throw new IllegalArgumentException("anticheat."+key+" must be numeric");values.put(key,n.doubleValue());}}return new Settings(values);}
        public double get(String key){return values.get(key);}public int integer(String key){return (int)get(key);}
    }
    public record AimSnapshot(long shots,long engaged,long onHead,long snaps,List<Map<String,Object>> examples,List<Double> acquisition){
        public AimSnapshot{examples=examples.stream().map(Map::copyOf).toList();acquisition=List.copyOf(acquisition);}
        public static AimSnapshot empty(){return new AimSnapshot(0,0,0,0,List.of(),List.of());}
    }
    public record Target(int id,int team,Vector head){}
    public static final class Aim {
        private long shots,engaged,onHead,snaps;private final ArrayDeque<Map<String,Object>> examples=new ArrayDeque<>();private final ArrayDeque<Double> acquisition=new ArrayDeque<>();private final Map<Integer,Double> engagedAt=new LinkedHashMap<>();
        public AimSnapshot snapshot(){return new AimSnapshot(shots,engaged,onHead,snaps,List.copyOf(examples),List.copyOf(acquisition));}
        public void observe(Settings s,int team,int label,Vector origin,Vector direction,List<Target> targets,IntFunction<PlayerMotion.Eye> history,CombatGeometry.Terrain terrain,double now){
            var aim=unit(direction);if(!finite(origin)||aim==null)return;shots++;Target best=null;Vector headDirection=null;double bestAngle=Double.POSITIVE_INFINITY,distance=0;
            for(var target:targets){if(target.team==team||!finite(target.head))continue;var offset=new Vector(target.head.x()-origin.x(),target.head.y()-origin.y(),target.head.z()-origin.z());double length=CombatGeometry.distance(origin,target.head);if(length<.5)continue;var d=new Vector(offset.x()/length,offset.y()/length,offset.z()/length);double angle=angle(aim,d);if(angle<bestAngle){bestAngle=angle;best=target;headDirection=d;distance=length;}}
            if(best==null)return;double cone=Math.toDegrees(Math.atan2(s.get("snap_head_radius"),distance))+s.get("snap_margin_deg");if(bestAngle>Math.max(cone,s.get("engage_cone_deg"))||!CombatGeometry.segmentClear(terrain,origin,best.head))return;
            engaged++;if(bestAngle<=cone){onHead++;for(int back=1;back<=Math.max(1,s.integer("snap_frames"));back++){var previous=history.apply(label-back);var old=previous==null?null:unit(previous.aim());if(old==null)continue;double swing=angle(old,aim);if(swing>=s.get("snap_min_deg")&&angle(old,headDirection)>=.75*s.get("snap_min_deg")){snaps++;examples.addLast(Map.of("deg",round(swing,1),"frames",back,"head_err_deg",round(bestAngle,2),"dist",round(distance,1),"target",best.id));while(examples.size()>5)examples.removeFirst();break;}}}
            boolean fresh=now-engagedAt.getOrDefault(best.id,-1e9)>=s.get("reaction_reengage_seconds");engagedAt.put(best.id,now);if(engagedAt.size()>64){var oldest=engagedAt.entrySet().stream().sorted(Map.Entry.comparingByValue()).limit(engagedAt.size()-32).map(Map.Entry::getKey).toList();oldest.forEach(engagedAt::remove);}if(!fresh)return;
            for(int back=1;back<=s.integer("reaction_history_frames");back++){var previous=history.apply(label-back);var old=previous==null?null:unit(previous.aim());if(old==null)return;if(angle(old,headDirection)>=s.get("reaction_acquire_deg")){acquisition.addLast(back*(1./60)*1000);while(acquisition.size()>200)acquisition.removeFirst();return;}}
        }
    }
    private static boolean finite(Vector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private static Vector unit(Vector v){if(!finite(v))return null;double length=Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z());return length<1e-9?null:new Vector(v.x()/length,v.y()/length,v.z()/length);}
    private static double angle(Vector a,Vector b){return Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,CombatGeometry.dot(a,b)))));}
    public record Evidence(int id,String name,boolean bot,ShotTally.Snapshot shots,Long hitscanKills,Long headshotKills,AimSnapshot aim,Map<String,Long> violations,double firstSeen){public Evidence{Objects.requireNonNull(name);Objects.requireNonNull(shots);Objects.requireNonNull(aim);violations=Map.copyOf(violations);}}
    public record Reason(String check,double score,String detail){public Map<String,Object> data(){return Map.of("check",check,"score",score,"detail",detail);}}
    public record Report(int playerId,String name,double score,boolean flagged,List<Reason> reasons){public Report{reasons=List.copyOf(reasons);}public Map<String,Object> data(){return Map.of("player_id",playerId,"name",name,"score",score,"flagged",flagged,"reasons",reasons.stream().map(Reason::data).toList());}}
    public static double round(double n,int digits){return new BigDecimal(n).setScale(digits,RoundingMode.HALF_EVEN).doubleValue();}
    private static String f(String format,Object... values){return String.format(Locale.ROOT,format,values);}
    private static void reason(List<Reason> out,String check,double weight,String detail,double excess){out.add(new Reason(check,round(weight*Math.max(1,Math.min(3,excess)),2),detail));}
    public static double percentile(List<Double> values,double percent){if(values.isEmpty())throw new IllegalArgumentException("Empty population");var sorted=values.stream().sorted().toList();double index=(sorted.size()-1)*Math.max(0,Math.min(100,percent))/100;int low=(int)index,high=Math.min(low+1,sorted.size()-1);return sorted.get(low)+(sorted.get(high)-sorted.get(low))*(index-low);}
    public static Map<Integer,Double> accuracies(ShotTally.Snapshot stats,Settings s){var result=new TreeMap<Integer,Double>();stats.weapons().forEach((tool,w)->{if(hitscan(tool)&&w.shots()>=s.integer("accuracy_min_shots")&&w.shots()>0)result.put(tool,w.hits()/(double)w.shots());});return result;}
    public static Report analyze(Settings s,Evidence e,Map<Integer,List<Double>> population,double now){
        if(e.bot)return null;var reasons=new ArrayList<Reason>();
        if(e.hitscanKills!=null&&e.hitscanKills>=s.integer("headshot_kill_min_kills")&&e.hitscanKills>0){double ratio=e.headshotKills/(double)e.hitscanKills,limit=s.get("headshot_kill_ratio");if(ratio>limit)reason(reasons,"headshot_kills",1,f("%d/%d hitscan kills are headshots (%.0f%% > %.0f%%)",e.headshotKills,e.hitscanKills,ratio*100,limit*100),ratio/limit);}
        long rayHits=0,rayHeads=0;for(var w:e.shots.weapons().entrySet())if(SINGLE_RAY.contains(w.getKey())){rayHits+=w.getValue().hits();rayHeads+=w.getValue().headshots();}double limit=s.get("headshot_hit_ratio");if(rayHits>=s.integer("headshot_hit_min_hits")&&rayHits>0){double ratio=rayHeads/(double)rayHits;if(ratio>limit)reason(reasons,"headshot_hits",1,f("%d/%d hits are headshots (%.0f%% > %.0f%%)",rayHeads,rayHits,ratio*100,limit*100),ratio/limit);}
        for(var w:accuracies(e.shots,s).entrySet()){var others=population.getOrDefault(w.getKey(),List.of());if(others.size()<Math.max(1,s.integer("accuracy_min_population")))continue;double pct=s.get("accuracy_percentile"),cutoff=percentile(others,pct),median=percentile(others,50),margin=s.get("accuracy_min_margin"),accuracy=w.getValue();if(accuracy>cutoff&&accuracy-median>=margin)reason(reasons,"accuracy:"+w.getKey(),1,f("tool %d accuracy %.0f%% over %d shots > p%s %.0f%% (median %.0f%%, n=%d)",w.getKey(),accuracy*100,e.shots.weapons().get(w.getKey()).shots(),BigDecimal.valueOf(pct).stripTrailingZeros().toPlainString(),cutoff*100,median*100,others.size()),1+(accuracy-cutoff)/Math.max(.05,margin));}
        var aim=e.aim;if(aim.engaged>=s.integer("snap_min_engaged")&&aim.snaps>=s.integer("snap_min_events")&&aim.engaged>0){double ratio=aim.snaps/(double)aim.engaged;limit=s.get("snap_ratio");if(ratio>=limit)reason(reasons,"aim_snap",2,f("%d snap-to-head shots of %d engaged (%.0f%%); e.g. %s",aim.snaps,aim.engaged,ratio*100,aim.examples.subList(Math.max(0,aim.examples.size()-2),aim.examples.size())),ratio/limit);}
        if(!aim.acquisition.isEmpty()&&aim.acquisition.size()>=s.integer("reaction_min_samples")){double median=percentile(aim.acquisition,50);limit=s.get("reaction_median_ms");if(median<limit)reason(reasons,"reaction",1.5,f("median target acquisition %.0f ms over %d engagements (< %s ms)",median,aim.acquisition.size(),BigDecimal.valueOf(limit).stripTrailingZeros().toPlainString()),limit/Math.max(1,median));}
        long total=0,top=0;int seed=0;for(var entry:e.shots.seeds().entrySet()){total+=entry.getValue();if(entry.getValue()>top){top=entry.getValue();seed=entry.getKey();}}limit=s.get("pellet_seed_top_share");if(total>0&&total>=s.integer("pellet_seed_min_shots")&&top>=limit*total)reason(reasons,"pellet_seed",1.5,f("seed %d used %d/%d shotgun shots (%.0f%%; uniform ~%.1f%%)",seed,top,total,100.*top/total,100./256),(top/(double)total)/limit);
        double minutes=Double.isNaN(e.firstSeen)?0:(now-e.firstSeen)/60;if(minutes>0&&minutes>=s.get("sustained_min_minutes"))for(var kind:SUSTAINED){long count=e.violations.getOrDefault(kind,0L)+e.violations.getOrDefault(kind+":observed",0L);double rate=count/minutes;limit=s.get("sustained_per_minute");if(count>=s.integer("sustained_min_count")&&rate>=limit)reason(reasons,"sustained:"+kind,.5,f("%d %s in %.1f min (%.1f/min)",count,kind,minutes,rate),rate/limit);}
        double score=round(reasons.stream().mapToDouble(Reason::score).sum(),2);return new Report(e.id,e.name,score,!reasons.isEmpty()&&score>=s.get("flag_min_score"),reasons);
    }
    private static final Set<Integer> EXPLOSIVES=explosiveStats();
    private static Set<Integer> explosiveStats(){var tools=new HashSet<Integer>();for(int type=0;type<256;type++){var tool=MatchCatalog.killTool(type);if(tool!=null)tools.add(tool);}var stats=new HashSet<Integer>();for(int classId=0;classId<256;classId++)for(var tool:tools){var stat=MatchCatalog.classKill(classId,tool);if(stat!=null)stats.add(stat);}return Set.copyOf(stats);}
    public static long[] profileKills(ProfileStats profile){if(profile==null)return null;long kills=count(profile,MatchCatalog.constant("KILL_SCORE_REASON")),heads=count(profile,MatchCatalog.constant("KILL_SCORE_HEADSHOT_REASON")),melee=count(profile,MatchCatalog.constant("KILL_SCORE_MELEE_REASON"));long explosive=EXPLOSIVES.stream().mapToLong(stat->count(profile,stat)).sum();return new long[]{Math.max(heads,kills-melee-explosive),heads};}
    private static long count(ProfileStats profile,int stat){return profile.values.getOrDefault(stat,new ProfileStats.Delta(0,0)).count();}
}
