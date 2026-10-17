// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.session.SnapshotHolds;
import net.battlespades.transport.enet.DeliveryReceipt;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotHoldsGoldenTest {
    private static final class Receipt implements DeliveryReceipt {
        final boolean fail;
        Runnable callback;
        boolean released;
        Receipt(boolean fail){this.fail=fail;}
        public boolean released(){return released;}
        public boolean onRelease(Runnable value){if(fail)throw new IllegalStateException("Controlled unsupported binding");callback=value;return true;}
        void release(){released=true;if(callback!=null)callback.run();}
    }
    @TestFactory Stream<DynamicTest> originalConnectionReleaseHolds()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/snapshot-holds.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original snapshot holds "+e.getKey(),()->{
            var holds=new SnapshotHolds();var receipts=new HashMap<Integer,Receipt>();int tick=0,index=0;
            for(var row:e.getValue()){
                String result="-";var a=row[2].split(",");
                switch(row[1]){
                    case "queue" -> {var receipt=new Receipt(a[2].equals("1"));receipts.put(Integer.parseInt(a[0]),receipt);holds.queued(HexFormat.of().parseHex(a[1]),receipt,tick);}
                    case "release" -> receipts.get(Integer.parseInt(row[2])).release();
                    case "tick" -> tick=Integer.parseInt(row[2]);
                    case "held" -> {result=holds.heldRows(tick).stream().map(key->(key.entity()?"E:":"P:")+key.id()).sorted().collect(Collectors.joining(";"));if(result.isEmpty())result="-";}
                    case "pending" -> result=holds.reliableUnackedThrough(Long.parseLong(row[2]),tick)?"1":"0";
                    default -> throw new AssertionError(row[1]);
                }
                assertEquals(row[3],holds.reliableSendIndex()+"|"+result,"operation "+index+++" "+row[1]+" "+row[2]);
            }
        }));
    }
}
