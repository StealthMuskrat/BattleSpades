// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class OwnerGoldenTest {
    private static String scalar(Object value){return value==null?"-":value.toString();}
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static Integer integer(String value){return value.equals("-")?null:Integer.valueOf(value);}
    private static Long sequence(String value){return value.equals("-")?null:Long.valueOf(value);}
    private static Vector vector(String[] a,int offset){return new Vector(number(a[offset]),number(a[offset+1]),number(a[offset+2]));}
    private static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static String anchor(OwnerAnchors.Anchor a){return a.stamp()+"|"+vector(a.position())+"|"+vector(a.velocity())+"|"+scalar(a.serverTick())+"|"+a.ownerSequence();}
    private static String state(PlayerLife p){var h=p.ownerAnchors();String rows=h.snapshot().stream().map(OwnerGoldenTest::anchor).collect(Collectors.joining(";"));return vector(h.spawnAnchor())+"#"+vector(h.lastAdvertised())+"#"+(rows.isEmpty()?"-":rows);}
    private static final class Replay extends FlightGoldenTest.Replay {
        Replay(int cid){super(cid);}
        @Override String[] operation(String op,String argument){
            var a=argument.split(",");String query="-";String[] result;
            if(op.equals("row")||op.equals("query")){
                events.clear();h.wire.clear();lanes.clear();
                if(op.equals("row"))p().recordOwnerAnchor(Integer.parseInt(a[0]),vector(a,1),vector(a,4),integer(a[7]),sequence(a[8]));
                else{var history=p().ownerAnchors();var loop=integer(a[0]);var tick=integer(a[1]);var seq=sequence(a[2]);query=history.entryBeforeInput(loop,tick,seq).map(OwnerGoldenTest::anchor).orElse("-")+"#"+vector(history.beforeInput(loop,tick,seq));}
                var base=result();result=new String[]{base[0],base[1],base[2],FlightGoldenTest.flight(p())};
            }else result=super.operation(op,argument);
            return new String[]{result[0],result[1],result[2],result[3],state(p()),query};
        }
    }
    @TestFactory Stream<DynamicTest> originalQueuedOwnerHistory()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/owner-history.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original owner history "+e.getKey(),()->{
            try(var replay=new Replay(Integer.parseInt(e.getValue().getFirst()[1]))){int index=0;
                for(var row:e.getValue()){
                    var values=replay.operation(row[2],row[3]);String context="op "+index+++" "+row[2]+" "+row[3];
                    for(int i=0;i<values.length;i++)assertEquals(row[4+i],values[i],context+" field "+i);
                }
            }
        }));
    }
}
