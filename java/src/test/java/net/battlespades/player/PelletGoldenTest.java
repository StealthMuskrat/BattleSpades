// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.PelletSpread;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PelletGoldenTest {
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    @TestFactory Stream<DynamicTest> originalSeededPelletsAndOwnFrameZoom()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/pellets.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original pellets "+e.getKey(),()->{
            try(var h=new LifeHarness()){
                h.admit(0,2);var p=h.runtime.player(0);var spread=new PelletSpread();int index=0;
                for(var row:e.getValue()){
                    var a=row[2].split(",");String result="-";
                    switch(row[1]){
                        case "tool" -> p.fire().tool(Integer.parseInt(row[2]),true);
                        case "zoom" -> p.inputTimeline().receive(new LiveClientInput(Integer.parseInt(a[0]),0,false,6,1,0,0,0,0,a[1].equals("1")?4:0,0),false,false,p.aim(),1000,0,false);
                        case "current" -> p.motion().controls(0,row[2].equals("1")?4:0);
                        case "bot" -> p.bot(row[2].equals("1"));
                        case "forget" -> spread.forgetPlayer(0);
                        case "pellets" -> {var pellets=spread.expand(p,new Vector(number(a[3]),number(a[4]),number(a[5])),p.fire().profile(),a[1].equals("-")?null:Integer.valueOf(a[1]),Integer.parseInt(a[2]),number(a[0]));result=pellets.stream().map(PelletGoldenTest::vector).collect(Collectors.joining(";"));if(result.isEmpty())result="-";}
                        default -> throw new AssertionError(row[1]);
                    }
                    var s=spread.snapshot().get(0);String state=s==null?"-":s.tool()+","+bits(s.spread())+","+bits(s.lastAt())+","+(s.label()==null?"-":s.label());String context="operation "+index+++" "+row[1]+" "+row[2];
                    assertEquals(row[3],result,context+" directions");assertEquals(row[4],state,context+" bloom");
                }
            }
        }));
    }
}
