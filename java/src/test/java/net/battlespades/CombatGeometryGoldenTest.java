// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.combat.CombatGeometry.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatGeometryGoldenTest {
    static long numericDifferences;static double maximumAbsoluteError;
    @AfterAll static void numericAudit(){System.out.println("Geometry numeric components differing from native libm: "+numericDifferences+"; maximum absolute error: "+maximumAbsoluteError);}
    static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    static Vector vector(String s){var a=s.split(",");return new Vector(number(a[0]),number(a[1]),number(a[2]));}
    static Cell cell(String s){var a=s.split(",");return new Cell(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));}
    static Set<Cell> cells(String s){return s.equals("-")?Set.of():Arrays.stream(s.split(";")).map(CombatGeometryGoldenTest::cell).collect(Collectors.toSet());}
    static final class World implements Terrain {
        final int seed;final List<Cell> calls=new ArrayList<>();World(int seed){this.seed=seed;}
        public boolean solid(int x,int y,int z){calls.add(new Cell(x,y,z));return Math.floorMod(x*31+y*17+z*13+seed,19)==0;}
        String calls(){return calls.stream().map(c->c.x()+","+c.y()+","+c.z()).collect(Collectors.joining(";"));}
    }
    record Body(Vector position,Vector orientation,boolean crouched) implements LagCompensation.Hitbox {
        public LagCompensation.Target target(){return null;}public double rewoundTicks(){return 0;}
    }
    static void entry(String expected,Entry actual){
        if(expected.equals("-")){assertNull(actual);return;}assertNotNull(actual);var a=expected.split(",");
        double[] got={actual.distance(),actual.position().x(),actual.position().y(),actual.position().z()};
        // Native libm atan2/sin/cos/pow and standard Java math differ by a few ULPs.
        // Intersections, part IDs and all voxel probes remain exact assertions.
        // A coordinate-scaled roundoff bound accounts for cancellation; classifications remain exact.
        double scale=Math.max(1,Math.max(Math.abs(number(a[1])),Math.max(Math.abs(number(a[2])),Math.abs(number(a[3])))));
        for(int i=0;i<4;i++){double expectedNumber=number(a[i]);if(Double.doubleToRawLongBits(expectedNumber)!=Double.doubleToRawLongBits(got[i]))numericDifferences++;maximumAbsoluteError=Math.max(maximumAbsoluteError,Math.abs(expectedNumber-got[i]));assertEquals(expectedNumber,got[i],16*Math.ulp(Math.max(scale,Math.abs(expectedNumber))),"component "+i);}
    }
    @TestFactory Stream<DynamicTest> unchangedOriginalGeometry()throws IOException {
        var rows=new ArrayList<String[]>();
        for(int part=0;;part++){
            var resource=getClass().getResourceAsStream("/golden/combat-geometry-%02d.tsv.gz".formatted(part));if(resource==null)break;
            try(var in=new GZIPInputStream(resource)){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(rows::add);}
        }
        return IntStream.range(0,(rows.size()+999)/1000).mapToObj(group->DynamicTest.dynamicTest("original geometry group "+group,()->{
            for(var r:rows.subList(group*1000,Math.min(rows.size(),(group+1)*1000))){
            switch(r[0]){
                case "segment" -> {var settings=vector(r[4]);var w=new World((int)settings.z());assertEquals(r[6].equals("1"),CombatGeometry.segmentClear(w,vector(r[2]),vector(r[3]),settings.x(),settings.y(),cells(r[5])));assertEquals(r[7].equals("-")?"":r[7],w.calls(),"exact voxel probe order");}
                case "visible" -> {var w=new World(Integer.parseInt(r[4]));var eyes=r[2].equals("-")?List.<Vector>of():Arrays.stream(r[2].split(";")).map(CombatGeometryGoldenTest::vector).toList();assertEquals(r[6].equals("1"),CombatGeometry.cellVisible(w,eyes,cell(r[3]),cells(r[5])));assertEquals(r[7].equals("-")?"":r[7],w.calls(),"exact visibility probe order");}
                case "sphere" -> entry(r[7],CombatGeometry.sphere(vector(r[2]),vector(r[3]),number(r[4]),vector(r[5]),number(r[6])));
                case "corpse" -> entry(r[7],CombatGeometry.corpse(vector(r[2]),vector(r[3]),number(r[4]),vector(r[5]),vector(r[6])));
                case "body" -> {var hit=CombatGeometry.body(vector(r[2]),vector(r[3]),number(r[4]),Integer.parseInt(r[5]),new Body(vector(r[6]),vector(r[7]),r[8].equals("1")));entry(r[9],hit==null?null:hit.entry());if(hit!=null){var a=r[9].split(",");assertEquals(a[4].equals("True"),hit.headshot());assertEquals(Integer.parseInt(a[5]),hit.part());}}
                default -> fail(r[0]);
            }
            }
        }));
    }
}
