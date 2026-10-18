// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
final class Fixtures {
    private Fixtures(){}
    private record Table(Map<String,List<String[]>> kinds,Map<Integer,List<String[]>> traces) {}
    private static final Map<String,Table> TABLES=new HashMap<>();
    private static synchronized Table table(String name)throws IOException {
        var cached=TABLES.get(name);if(cached!=null)return cached;
        var kinds=new LinkedHashMap<String,List<String[]>>();var traces=new LinkedHashMap<Integer,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(Fixtures.class.getResourceAsStream("/golden/"+name+".tsv.gz")))) {
            for(var line:new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().toList()) {
                var row=line.split("\t",-1);
                kinds.computeIfAbsent(row[0],key->new ArrayList<>()).add(row);
                traces.computeIfAbsent(Integer.parseInt(row[1]),key->new ArrayList<>()).add(row);
            }
        }
        kinds.replaceAll((key,rows)->List.copyOf(rows));traces.replaceAll((key,rows)->List.copyOf(rows));
        var loaded=new Table(Map.copyOf(kinds),Map.copyOf(traces));TABLES.put(name,loaded);return loaded;
    }
    static List<String[]> rows(String name,String kind)throws IOException {
        return Objects.requireNonNull(table(name).kinds().get(kind),"Missing fixture kind: "+kind);
    }
    /** Preserve source order across interleaved edits and operations; never deduplicate traces. */
    static List<String[]> trace(String name,int trace,String... kinds)throws IOException {
        var accepted=Set.of(kinds);
        var selected=Objects.requireNonNull(table(name).traces().get(trace),"Missing fixture trace: "+trace)
            .stream().filter(row->accepted.contains(row[0])).toList();
        if(selected.isEmpty())throw new IllegalArgumentException("Missing fixture kinds "+accepted+" in trace "+trace);
        return selected;
    }
    static double number(String v){return Double.longBitsToDouble(Long.parseUnsignedLong(v,16));}
    static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    static Vector vector(String v){var a=v.split(",");return new Vector(number(a[0]),number(a[1]),number(a[2]));}
    static String vec(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static List<Integer> ids(String value){return value.equals("-")?List.of():Arrays.stream(value.split(",")).map(Integer::valueOf).toList();}
}
