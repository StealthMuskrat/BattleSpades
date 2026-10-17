// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.DirectDamage;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShieldGoldenTest {
    static double number(String s){return CombatGeometryGoldenTest.number(s);}
    static Vector vector(String s){return CombatGeometryGoldenTest.vector(s);}
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    @TestFactory Stream<DynamicTest> originalFrontalShieldAndHypotKnockback()throws IOException {
        var rows=new ArrayList<String[]>();for(int part=0;;part++){
            var resource=getClass().getResourceAsStream("/golden/shield-%02d.tsv.gz".formatted(part));if(resource==null)break;
            try(var in=new GZIPInputStream(resource)){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(rows::add);}
        }
        return IntStream.range(0,14).mapToObj(group->DynamicTest.dynamicTest("original shield "+group,()->{
            for(var r:rows.subList(group*1000,(group+1)*1000)){
                var shield=new DirectDamage.Shield(Integer.parseInt(r[1]),r[2].equals("1"),vector(r[3]),vector(r[4]));var events=new ArrayList<DirectDamage.ShieldImpact>();
                var amount=DirectDamage.mitigate(shield,vector(r[5]),number(r[8]),r[9].equals("-")?null:vector(r[9]),r[10].equals("1"),events::add);
                assertEquals(r[11],bits(amount),"mitigation "+r[0]);
                assertEquals(r[12],vector(DirectDamage.shieldKnockback(vector(r[5]),vector(r[6]),vector(r[3]),vector(r[7]))),"knockback "+r[0]);
                assertEquals(r[13],events.isEmpty()?"-":events.stream().map(e->vector(e.position())+","+(e.melee()?1:0)).collect(Collectors.joining(";")),"attachment request "+r[0]);
            }
        }));
    }
}
