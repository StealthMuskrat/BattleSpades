// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import java.security.MessageDigest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.entities.SourceEntityIds;

final class EntityIdSetGoldenTest {
    static IntStream traces(){return IntStream.range(0,33);}
    @ParameterizedTest @MethodSource("traces") void actualPythonSupportSetOrder(int trace)throws Exception{
        var values=new SourceEntityIds();try(var in=getClass().getResourceAsStream("/golden/entity-id-set.tsv.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),java.nio.charset.StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){var r=line.split("\t");if(Integer.parseInt(r[0])!=trace)continue;int key=Integer.parseInt(r[3]);boolean result=switch(r[2]){case "add"->values.add(key);case "discard"->values.discard(key);case "contains"->values.contains(key);case "clear"->{values.clear();yield false;}case "fill"->{for(int id=0;id<65536;id++)values.add(id);yield false;}case "discard_even"->{for(int id=0;id<65536;id+=2)values.discard(id);yield false;}case "reinsert_low"->{for(int id=0;id<256;id++)values.add(id);yield false;}default->throw new AssertionError(r[2]);};assertEquals(r[4].equals("1"),result,line);assertEquals(Integer.parseInt(r[5]),values.size(),line);var digest=MessageDigest.getInstance("SHA-256");for(int value:values.values())for(int shift=0;shift<32;shift+=8)digest.update((byte)(value>>>shift));assertEquals(r[6],HexFormat.of().formatHex(digest.digest()),line);}
        }
    }
}
