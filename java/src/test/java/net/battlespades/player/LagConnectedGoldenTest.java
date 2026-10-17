// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.LagCompensation.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LagConnectedGoldenTest {
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static String sample(Sample s){return s==null?"-":s.tick()+","+vector(s.position())+","+vector(s.orientation())+","+(s.crouched()?1:0)+","+(s.live()?1:0)+","+s.life()+","+s.epoch();}
    private static String history(History h)throws Exception {
        if(h.last()==null)return "0|-|-";
        String text=h.samples().stream().map(LagConnectedGoldenTest::sample).collect(Collectors.joining(";"));
        return h.epoch()+"|"+sample(h.last())+"|"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    @TestFactory Stream<DynamicTest> originalPreMovementHistoryAndPhysicalCrouch()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/lag-connected.tsv.gz")))){
            new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original connected lag history "+e.getKey(),()->{
            try(var replay=new FlightGoldenTest.Replay(Integer.parseInt(e.getKey()))){int index=0;
                for(var row:e.getValue()){replay.operation(row[1],row[2]);assertEquals(row[3],history(replay.p().lagTarget().history()),"operation "+index+++" "+row[1]+" "+row[2]);}
            }
        }));
    }
}
