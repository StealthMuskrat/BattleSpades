// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.ExplosionGeometry;
import net.battlespades.combat.DirectDamage;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class ExplosionGeometryGoldenTest {
    private static List<String[]> rows()throws IOException{try(var in=new GZIPInputStream(Objects.requireNonNull(ExplosionGeometryGoldenTest.class.getResourceAsStream("/golden/explosion-geometry.tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String bits(double value){return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value));}
    private static String vec(Vector value){return value==null?"-":bits(value.x())+","+bits(value.y())+","+bits(value.z());}
    static IntStream groups(){return IntStream.range(0,64);}
    @ParameterizedTest @MethodSource("groups") void originalBlastGeometryAndRaycastOrder(int group)throws Exception{
        for(var r:rows().subList(group*64,(group+1)*64)){
            var a=r[1].split(",");var origin=new Vector(number(a[0]),number(a[1]),number(a[2]));var position=new Vector(number(a[3]),number(a[4]),number(a[5]));double radius=number(a[6]),minimum=number(a[7]),maximum=number(a[8]),damage=number(a[9]),los=number(a[10]);boolean crouched=a[11].equals("1"),self=a[12].equals("1"),classic=a[14].equals("1");Integer team=a[13].equals("-")?null:Integer.valueOf(a[13]);int mask=Integer.parseInt(a[15]);String where="case "+r[0];
            assertEquals(r[2],bits(ExplosionGeometry.falloff(origin,position,radius,crouched?1.25:.75)),where+" falloff");assertEquals(r[3],bits(ExplosionGeometry.playerDamage(origin,position,radius,damage,crouched,los,self,team,classic)),where+" player damage");assertEquals(r[4],bits(ExplosionGeometry.entityDamage(origin,position,radius,damage)),where+" entity damage");assertEquals(r[5],vec(ExplosionGeometry.impulse(origin,position,radius,minimum,maximum,crouched)),where+" impulse");
            var calls=new ArrayList<String>();var blocker=ExplosionGeometry.segmentBlocker((start,direction,length)->{calls.add(vec(start)+","+vec(direction)+","+bits(length));return (mask&(1<<(calls.size()-1)))!=0;});assertEquals(r[6],bits(ExplosionGeometry.losFraction(blocker,origin,position,crouched)),where+" sight weights");var segment=ExplosionGeometry.raySegment(origin,position);assertEquals(r[7],segment==null?"-":vec(segment.start())+":"+vec(segment.end()),where+" ray segment");assertEquals(r[8],calls.isEmpty()?"-":String.join("|",calls),where+" raycast calls");var scaled=ExplosionGeometry.scaleKnockback(minimum,maximum,los);assertEquals(r[9],bits(scaled[0])+","+bits(scaled[1]),where+" knockback scaling");
        }
    }
    @Test void originalStockHandlerVariantsAndMatchingTolerance()throws Exception{assertEquals(23,ExplosionGeometry.stock().size());for(var r:rows()){if(!r[0].equals("stock"))continue;var found=ExplosionGeometry.stockFor(Integer.parseInt(r[1]),number(r[2]));assertEquals(r[3],found==null?"-":found.name(),Arrays.toString(r));}}
    @Test void originalGenericBlastVictimMultipliers()throws Exception{for(var r:rows()){if(!r[0].equals("victim"))continue;assertEquals(r[5],bits(DirectDamage.victimMultiplier(new DirectDamage.Victim(Integer.parseInt(r[1]),Integer.parseInt(r[2]),r[3].equals("1"),r[4].equals("1")))),Arrays.toString(r));}}

}
