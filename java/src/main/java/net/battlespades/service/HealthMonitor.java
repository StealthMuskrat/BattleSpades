// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.service;
import java.util.*;
import java.util.function.Consumer;
/** Source 600-frame health window, separate from the 36,000-sample telemetry lanes. */
public final class HealthMonitor {
    public record Window(long ticks,double averageMillis,double maxMillis,long slow,Map<String,Timing> subsystems){}
    public record Timing(long count,double totalMillis,double maxMillis){Timing add(double value){return new Timing(count+1,totalMillis+value,Math.max(maxMillis,value));}}
    private final Consumer<Window> report;private final SortedMap<String,Timing> lanes=new TreeMap<>();private long ticks,slow;private double total,max;
    public HealthMonitor(Consumer<Window> report){this.report=Objects.requireNonNull(report);}
    public void subsystem(String name,double ms){lanes.compute(name,(_,previous)->(previous==null?new Timing(0,0,0):previous).add(ms));}
    public void tick(double ms){ticks++;total+=ms;max=Math.max(max,ms);if(ms>10)slow++;if(ticks<600)return;var window=new Window(ticks,total/ticks,max,slow,Collections.unmodifiableMap(new TreeMap<>(lanes)));ticks=slow=0;total=max=0;lanes.clear();report.accept(window);}
    public void reset(){ticks=slow=0;total=max=0;lanes.clear();}
}
