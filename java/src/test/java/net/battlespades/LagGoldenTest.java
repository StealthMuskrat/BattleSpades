// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.LagCompensation;
import net.battlespades.combat.LagCompensation.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LagGoldenTest {
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static Vector vector(String[] a,int at){return new Vector(number(a[at]),number(a[at+1]),number(a[at+2]));}
    private static List<String[]> lines(String name)throws IOException {
        try(var in=new GZIPInputStream(Objects.requireNonNull(LagGoldenTest.class.getResourceAsStream("/golden/"+name+".tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
    }
    private static String sample(Sample s){return s==null?"-":s.tick()+","+vector(s.position())+","+vector(s.orientation())+","+(s.crouched()?1:0)+","+(s.live()?1:0)+","+s.life()+","+s.epoch();}
    private static String history(History h)throws Exception {
        if(h==null)return "0|-|-";
        String text=h.samples().stream().map(LagGoldenTest::sample).collect(Collectors.joining(";"));
        return h.epoch()+"|"+sample(h.last())+"|"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String body(Hitbox b){return (b instanceof Rewound?"R":"L")+","+vector(b.position())+","+vector(b.orientation())+","+(b.crouched()?1:0)+","+bits(b.rewoundTicks());}
    private static final class Body implements Target {
        Vector position=new Vector(0,0,0),aim=new Vector(1,0,0);boolean crouched,live=true;long life;History history;
        public Vector position(){return position;}public Vector orientation(){return aim;}public boolean crouched(){return crouched;}
        public boolean live(){return live;}public long life(){return life;}public History history(){return history;}
    }
    @TestFactory Stream<DynamicTest> originalReadOnlyTargetHistory()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:lines("lag-history"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original target history "+e.getKey(),()->{
            var target=new Body();Context context=null;int index=0;
            for(var row:e.getValue()){
                var a=row[2].split(",");String result="-";
                switch(row[1]){
                    case "state" -> {target.position=vector(a,0);target.aim=vector(a,3);target.crouched=a[6].equals("1");target.live=a[7].equals("1")&&a[8].equals("1");target.life=Long.parseLong(a[9]);}
                    case "record" -> {if(target.history==null)target.history=new History();target.history.record(target,Integer.parseInt(row[2]));}
                    case "forget" -> target.history=null;
                    case "body" -> result=body(LagCompensation.rewound(target,Integer.parseInt(a[0]),number(a[1])));
                    case "context" -> context=new Context(Integer.parseInt(a[0]),number(a[1]),0,0,0,null);
                    case "cached" -> result=body(context.body(target));
                    default -> throw new AssertionError(row[1]);
                }
                assertEquals(row[3],history(target.history)+"#"+result,"operation "+index+++" "+row[1]+" "+row[2]);
            }
        }));
    }
    @TestFactory Stream<DynamicTest> originalRewindDecisionsAndDiagnostics()throws IOException {
        var rows=lines("lag-decisions");
        return IntStream.range(0,20).mapToObj(group->DynamicTest.dynamicTest("original lag decisions "+group,()->{
            for(int i=group*350;i<(group+1)*350;i++){
                var row=rows.get(i);var a=row[0].split(",");var arrivals=new HashMap<Integer,Integer>();
                if(!a[12].equals("-"))for(String pair:a[12].split(":")){var kv=pair.split("=");arrivals.put(Integer.valueOf(kv[0]),Integer.valueOf(kv[1]));}
                var settings=new Settings(a[6].equals("1"),number(a[7]),number(a[8]),number(a[9]),number(a[10]),Integer.parseInt(a[11]));
                var events=new ArrayList<Diagnostic>();
                var context=LagCompensation.decide(settings,Integer.parseInt(a[0]),number(a[1]),a[2].equals("1"),number(a[3]),Integer.valueOf(a[4]),Integer.parseInt(a[5]),arrivals::get,events::add);
                String result=context==null?"-":context.currentTick()+","+bits(context.viewTick())+","+bits(context.rewindMillis())+","+bits(context.allowedMillis())+","+bits(context.rttMillis())+","+(context.snapshotTick()==null?"-":context.snapshotTick());
                String diagnostics=events.stream().map(d->d.kind()+","+d.detail().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(p->p.getKey()+"="+bits(p.getValue())).collect(Collectors.joining(","))).collect(Collectors.joining(";"));
                assertEquals(row[1],result+"#"+(diagnostics.isEmpty()?"-":diagnostics),"decision "+i+" "+row[0]);
            }
        }));
    }
}
