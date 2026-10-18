// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.protocol.*;

/** Retail scene-boundary packets; packet53 is safe only after preflight and with a bundled level screenshot. */
public final class TransitionPackets {
    private static final org.tomlj.TomlParseResult DATA=SourceCatalog.load("transitions");
    private static final Set<String> SCREENSHOTS=Set.copyOf(SourceCatalog.strings(DATA.getArray("screenshots")));
    private TransitionPackets(){}
    public static GamePacket audioFlush(){return new AudioPackets.PlaySound(Math.toIntExact(DATA.getLong("flushSound")),false,false,0,0,0,new Vector3f(0,0,0),0);}
    public static boolean supportsStatistics(String displayName){return SCREENSHOTS.contains(displayName);}
    public static double bound(double value,double maximum){return Double.isNaN(value)?0:Math.min(maximum,Math.max(0,value));}
    public static List<GamePacket> statistics(double seconds,Integer headline){
        var packets=new ArrayList<GamePacket>();packets.add(audioFlush());packets.add(new SimplePackets.ForceShowScores(0));packets.add(new SimplePackets.ShowGameStats());
        if(headline!=null)packets.add(new ScorePackets.ShowTextMessage(Math.max(0,Math.min(8,headline)),(float)bound(seconds,120)));return List.copyOf(packets);
    }
}
