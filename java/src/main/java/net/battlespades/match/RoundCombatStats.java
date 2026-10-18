// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Original per-round awards/fractional measures; recorders remain separate from scoreboard selection. */
public final class RoundCombatStats {
    public final Map<Integer,Long> awards=new LinkedHashMap<>();
    public final Map<Object,Double> measures=new LinkedHashMap<>();
    public Vector lastPosition;
    public double lastAirstrikeSurvivedAt=-1e9;
    public void increment(int stat,long count){awards.merge(stat,count,Math::addExact);}
    public void raiseTo(int stat,long value){if(value>awards.getOrDefault(stat,0L))awards.put(stat,value);}
    public void accumulate(int stat,double amount){double total=measures.getOrDefault(stat,0.0)+amount;measures.put(stat,total);if(Double.isNaN(total))throw new IllegalArgumentException("NaN award accumulator");if(Double.isInfinite(total))throw new ArithmeticException("Infinite award accumulator");if(total< -0x1.0p63||total>=0x1.0p63)throw new ArithmeticException("Award outside signed 64-bit range");awards.put(stat,(long)total);}
}
