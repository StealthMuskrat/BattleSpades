// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.combat.ShotValidation.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerMotion;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShotValidationGoldenTest {
    static double number(String s){return CombatGeometryGoldenTest.number(s);}
    static Vector vector(String s){return CombatGeometryGoldenTest.vector(s);}
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static String counters(Map<String,Long> map){String result=map.entrySet().stream().filter(e->e.getValue()!=0).sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining(","));return result.isEmpty()?"-":result;}
    static String stats(Statistics s){String result=s.histograms().entrySet().stream().filter(e->!e.getValue().isEmpty()).sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+":"+counters(e.getValue())).collect(Collectors.joining(";"));return result.isEmpty()?"-":result;}
    static String value(Object o){return switch(o){case String s->"s"+s;case Boolean b->"b"+(b?1:0);case Integer i->"i"+i;case Double d->"n"+bits(d);default->throw new AssertionError(o);};}
    static String event(Event e){return e.kind()+","+(e.enforced()?1:0)+","+e.detail().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(p->p.getKey()+"="+value(p.getValue())).collect(Collectors.joining(","));}
    @TestFactory Stream<DynamicTest> originalValidationAndAccounting()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        for(int part=0;part<4;part++)try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/shot-validation-%02d.tsv.gz".formatted(part))))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original shot validation "+e.getKey(),()->{
            var stats=new Statistics();for(var r:e.getValue()){
                var history=new HashMap<Integer,PlayerMotion.Eye>();if(!r[7].equals("-"))for(String h:r[7].split(";")){var a=h.split(",",2);var v=a[1].split(",");history.put(Integer.valueOf(a[0]),new PlayerMotion.Eye(new Vector(number(v[0]),number(v[1]),number(v[2])),new Vector(number(v[3]),number(v[4]),number(v[5]))));}
                var view=new View(vector(r[2]),vector(r[3]),vector(r[4]),100,r[5].equals("1"),Integer.parseInt(r[6]),history::get);var origin=r[9].split(",");var aim=r[10].split(",");var claims=new Claims(r[8].equals("-")?null:Integer.valueOf(r[8]),number(origin[0]),number(origin[1]),number(origin[2]),number(aim[0]),number(aim[1]),number(aim[2]));var config=vector(r[11]);var settings=new Settings(config.x(),config.y(),r[12].charAt(0)=='1',r[12].charAt(1)=='1');var world=new CombatGeometryGoldenTest.World((int)config.z());var events=new ArrayList<Event>();
                var result=ShotValidation.validate(settings,view,claims,world,stats,events::add);String output="-";
                if(result.allowed()){var a=result.accepted();output=a.eyes().stream().map(ShotValidationGoldenTest::vector).collect(Collectors.joining(";"))+"|"+bits(a.slack())+"|"+vector(a.direction());}
                String context="group "+r[0]+" step "+r[1];assertEquals(r[13],output,context+" accepted geometry");assertEquals(r[14],result.reason()==null?"-":result.reason(),context+" reason");assertEquals(r[15],stats(stats),context+" aggregates");assertEquals(r[16],counters(stats.violations()),context+" violations");assertEquals(r[17],events.isEmpty()?"-":events.stream().map(ShotValidationGoldenTest::event).collect(Collectors.joining(";")),context+" reports");assertEquals(r[18].equals("-")?"":r[18],world.calls(),context+" voxel probes");
            }
        }));
    }
}
