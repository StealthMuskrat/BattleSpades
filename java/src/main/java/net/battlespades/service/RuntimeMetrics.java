// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.service;
import java.util.*;
/** Owner-only bounded timing samples and cumulative source counters. */
public final class RuntimeMetrics {
    public static final int CAPACITY=36000;
    public enum Counter {world_serializations,world_sends,world_bytes,dropped_ingame_packets,skipped_plugin_callbacks,skipped_entity_ticks,map_mutation_overflows,dropped_mode_events,committed_world_mutations,rejected_world_mutations,expired_world_mutations,world_mutation_queue_peak,terrain_repair_cells,terrain_repair_sends,terrain_repair_queue_peak,dropped_terrain_repairs,failed_terrain_repair_sends,bot_perception_entity_overflow}
    private final Thread owner=Thread.currentThread();private final Samples ticks=new Samples();private final SortedMap<String,Samples> subsystems=new TreeMap<>();private final long[] counters=new long[Counter.values().length];
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Metrics outside owner");}
    public void recordTick(double ms){owned();ticks.add(ms);}public void recordSubsystem(String name,double ms){owned();subsystems.computeIfAbsent(Objects.requireNonNull(name),_->new Samples()).add(ms);}
    public void resetTimingSamples(){owned();ticks.clear();subsystems.clear();}
    public void add(Counter counter,long delta){owned();counters[counter.ordinal()]+=delta;}public void peak(Counter counter,long value){owned();counters[counter.ordinal()]=Math.max(counters[counter.ordinal()],value);}
    public void recordWorldPacket(int size,int recipients){recordWorldDelivery(recipients,(long)size*recipients);}public void recordWorldDelivery(long sends,long bytes){add(Counter.world_serializations,1);add(Counter.world_sends,sends);add(Counter.world_bytes,bytes);}
    public Map<String,Number> snapshot(){owned();var out=new LinkedHashMap<String,Number>();double[] v=ticks.sorted();out.put("tick_samples",v.length);out.put("tick_avg_ms",average(v));out.put("tick_p50_ms",percentile(v,.5));out.put("tick_p95_ms",percentile(v,.95));out.put("tick_p99_ms",percentile(v,.99));out.put("tick_max_ms",v.length==0?0.:v[v.length-1]);for(var c:Counter.values())out.put(c.name(),counters[c.ordinal()]);subsystems.forEach((name,s)->{double[] values=s.sorted();out.put("subsystem_"+name+"_avg_ms",average(values));out.put("subsystem_"+name+"_p99_ms",percentile(values,.99));});return Collections.unmodifiableMap(out);}
    private static double average(double[] v){double sum=0;for(double x:v)sum+=x;return v.length==0?0:sum/v.length;}
    private static double percentile(double[] v,double p){return v.length==0?0:v[(int)Math.rint((v.length-1)*p)];}
    private static final class Samples{private final double[] values=new double[CAPACITY];private int next,count;void add(double value){values[next]=value;next=(next+1)%CAPACITY;count=Math.min(count+1,CAPACITY);}void clear(){next=0;count=0;}double[] sorted(){double[] result=Arrays.copyOf(values,count);Arrays.sort(result);return result;}}
}
