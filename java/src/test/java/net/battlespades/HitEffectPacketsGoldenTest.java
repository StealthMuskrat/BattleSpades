// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.protocol.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HitEffectPacketsGoldenTest {
    static Vector3f vector(String s){var v=Arrays.stream(s.split(",")).mapToDouble(CombatGeometryGoldenTest::number).toArray();return new Vector3f((float)v[0],(float)v[1],(float)v[2]);}
    static String vector(Vector3f v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.x()))+","+HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.y()))+","+HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.z()));}
    @TestFactory Stream<DynamicTest> originalHitFeedbackCodecs()throws IOException {
        final List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/hit-effect-packets.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        return IntStream.range(0,12).mapToObj(group->DynamicTest.dynamicTest("original hit effect packets "+group,()->{
            for(var r:rows.subList(group*1000,(group+1)*1000)){
                GamePacket packet=switch(r[0]){
                    case "entity"->new CombatPackets.HitEntity(Integer.parseInt(r[1]),vector(r[3]),Integer.parseInt(r[2]));
                    case "response"->{var a=r[1].split(",");yield new CombatPackets.ShootResponse(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]),vector(r[3]));}
                    case "feedback"->{var a=r[1].split(",");yield new SimplePackets.ShootFeedbackPacket(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3]),Integer.parseInt(a[4])&255);}
                    default->throw new AssertionError(r[0]);
                };
                assertEquals(r[4],HexFormat.of().formatHex(packet.encode()));var decoded=PacketCodec.decode(HexFormat.of().parseHex(r[4]));
                if(decoded instanceof CombatPackets.HitEntity e)assertEquals(r[5],vector(e.position()));if(decoded instanceof CombatPackets.ShootResponse e)assertEquals(r[5],vector(e.position()));
                // Source signed-magnitude position encoding is deliberately asymmetric on negative values.
                assertEquals(r[6],HexFormat.of().formatHex(decoded.encode()));
            }
        }));
    }
}
