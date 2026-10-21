// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.combat.CombatCatalog;

/** Weapon doctrine is bounded by the actual authoritative catalog reach. */
public final class BotCombatProfiles {
    public record Envelope(double idealMin,double idealMax,double hardMax,boolean prefersStationary,
                           List<Integer> burstShots,List<Double> burstPause) {
        public Envelope {burstShots=List.copyOf(burstShots);burstPause=List.copyOf(burstPause);}
    }
    public static final Set<Integer> FIREARMS=Set.copyOf(SourceCatalog.integers(SourceCatalog.load("bot-live").getArray("firearms")));
    private static final Map<String,Envelope> ENVELOPES=new HashMap<>();
    private static final Map<String,Double> RECOIL=new HashMap<>();
    static {
        var rows=SourceCatalog.load("bot-combat").getArray("envelope");
        for(int i=0;i<rows.size();i++) {
            var r=rows.getTable(i);var shots=r.getArray("burst_shots");var pauses=r.getArray("burst_pause");
            ENVELOPES.put(r.getString("category"),new Envelope(r.getDouble("ideal_min"),r.getDouble("ideal_max"),r.getDouble("hard_max"),r.getBoolean("prefers_stationary"),
                List.of(Math.toIntExact(shots.getLong(0)),Math.toIntExact(shots.getLong(1))),List.of(pauses.getDouble(0),pauses.getDouble(1))));
            RECOIL.put(r.getString("category"),r.getDouble("recoil"));
        }
    }
    private BotCombatProfiles() {}
    public static Envelope envelope(int tool) {
        var p=FIREARMS.contains(tool)?CombatCatalog.WEAPONS.get(tool):null;var e=ENVELOPES.getOrDefault(p==null?"":p.category(),ENVELOPES.get("default"));
        if(p!=null&&p.range()>0&&p.range()<e.hardMax()) {
            double maximum=Math.min(e.idealMax(),p.range()*.75);
            return new Envelope(Math.min(e.idealMin(),maximum*.5),maximum,p.range()*.95,e.prefersStationary(),e.burstShots(),e.burstPause());
        }
        return e;
    }
    public static double recoil(int tool) {var p=FIREARMS.contains(tool)?CombatCatalog.WEAPONS.get(tool):null;return p==null?0:RECOIL.getOrDefault(p.category(),0.);}
}
