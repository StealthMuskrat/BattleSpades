// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectDamageGoldenTest {
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    @TestFactory Stream<DynamicTest> originalPerPartVictimAndEntityDamage()throws IOException {
        var groups=new LinkedHashMap<String,List<String>>();
        for(int part=0;;part++){
            var resource=getClass().getResourceAsStream("/golden/direct-damage-%02d.tsv.gz".formatted(part));if(resource==null)break;
            try(var in=new GZIPInputStream(resource)){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().forEach(line->groups.computeIfAbsent(line.substring(0,line.indexOf("\t")),k->new ArrayList<>()).add(line));}
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original direct damage tool "+e.getKey(),()->{
            var profile=CombatCatalog.get(Integer.parseInt(e.getKey()));int index=0;
            for(var line:e.getValue()){var row=line.split("\t",-1);
                var victim=row[1].equals("-")?null:new DirectDamage.Victim(Integer.parseInt(row[1]),Integer.parseInt(row[3]),row[4].equals("1"),row[2].equals("1"));
                double amount=DirectDamage.amount(profile,row[5].equals("1"),row[6].equals("-")?null:Integer.valueOf(row[6]),victim);
                String context="operation "+index+++" "+String.join(",",row);assertEquals(row[7],bits(amount),context);assertEquals(row[8],bits(DirectDamage.entityAmount(profile)),context+" entity");
            }
        }));
    }
}
