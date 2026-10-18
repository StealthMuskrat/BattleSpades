// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
/** Connection-owned retail counters, independent of round score/reset and external reward writes. */
public final class ProfileStats {
    public record Delta(long count,long score){}
    public final Map<Integer,Delta> values=new LinkedHashMap<>();
    public final Map<Integer,Double> remainder=new LinkedHashMap<>();
    public long shotSerial,hitSerial=-1,lastScore;
    public double connectedSeconds,activeSeconds,idleSeconds,afkSeconds,opponentSampleSeconds;
    public boolean activitySeen;
    public int humanOpponents,botOpponents;
    public Vector lastPosition,lastOrientation;
    public void add(int stat,long count,long score){
        int n=MatchCatalog.constant("STATS_MAX_WEAPONS");
        if(!(stat>=0&&stat<=250||stat>=1000&&stat<1000+n||stat>=2000&&stat<2000+n||stat>=3000&&stat<3000+n))throw new IllegalArgumentException("Unknown retail profile statistic");
        if(count<0||score<0)throw new IllegalArgumentException("Negative profile delta");
        var old=values.getOrDefault(stat,new Delta(0,0));values.put(stat,new Delta(Math.addExact(old.count,count),Math.addExact(old.score,score)));
    }
    static long integer(double value){if(Double.isNaN(value))throw new IllegalArgumentException("NaN counter");if(!Double.isFinite(value)||value< -0x1.0p63||value>=0x1.0p63)throw new ArithmeticException("Counter outside signed 64-bit range");return (long)value;}
    public void fractional(int stat,double amount){double value=remainder.getOrDefault(stat,0.0)+amount;long whole=integer(value+1e-9);double rest=value-whole;remainder.put(stat,rest>0?rest:0.0);if(whole!=0)add(stat,whole,0);}
    public Map<Integer,Delta> snapshot(){return Collections.unmodifiableMap(new LinkedHashMap<>(values));}
}
