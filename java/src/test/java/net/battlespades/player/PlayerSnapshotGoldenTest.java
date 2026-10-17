// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.protocol.WorldUpdate;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSnapshotGoldenTest {
    @TestFactory Stream<DynamicTest> originalLiveSnapshot()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/player-snapshot.tsv.gz")))){
            new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original live snapshot "+e.getKey(),()->{
            try(var replay=new FlightGoldenTest.Replay(Integer.parseInt(e.getKey()))){int index=0;
                for(var row:e.getValue()){
                    replay.operation(row[2],row[3]);var a=Arrays.stream(row[4].split(",")).mapToInt(Integer::parseInt).toArray();
                    var snapshot=new PlayerSnapshot(replay.p(),p->new PlayerSnapshot.Presentation(a[0],a[1]<0?null:a[1],a[2]!=0,a[3]!=0,a[4]!=0,false,true,false));
                    var result=new WorldUpdate(0,List.of(snapshot.state(0,replay.h.monotonic).row()),List.of(),List.of());
                    assertEquals(row[5],HexFormat.of().formatHex(result.encode()),"operation "+index+++" "+row[2]+" "+row[3]);
                }
            }
        }));
    }
}
