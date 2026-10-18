// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
final class Fixtures {
    private Fixtures(){}
    static List<String[]> rows(String name)throws Exception{try(var in=new GZIPInputStream(Objects.requireNonNull(Fixtures.class.getResourceAsStream("/golden/"+name+".tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static double number(String v){return Double.longBitsToDouble(Long.parseUnsignedLong(v,16));}
    static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    static Vector vector(String v){var a=v.split(",");return new Vector(number(a[0]),number(a[1]),number(a[2]));}
    static String vec(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static List<Integer> ids(String value){return value.equals("-")?List.of():Arrays.stream(value.split(",")).map(Integer::valueOf).toList();}
}
