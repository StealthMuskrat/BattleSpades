// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;

/** Per-connection violation accounting survives respawns; diagnostic throttling never suppresses counters. */
public final class AnticheatStatistics {
    public record Report(String key,long count,boolean enforced,Map<String,?> detail){public Report{detail=Map.copyOf(detail);}}
    private final Thread owner=Thread.currentThread();private final Map<String,Long> counts=new LinkedHashMap<>();private final Map<String,Double> logged=new HashMap<>();
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Anticheat accounting outside owner");}
    public Map<String,Long> counts(){owned();return Collections.unmodifiableMap(new LinkedHashMap<>(counts));}
    public Report report(String kind,boolean enforced,Map<String,?> detail,double now){
        owned();String key=kind+(enforced?"":":observed");long count=counts.merge(key,1L,Long::sum);if(now-logged.getOrDefault(key,-1e9)<10)return null;logged.put(key,now);return new Report(key,count,enforced,detail);
    }
}
