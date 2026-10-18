// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.replication.SnapshotWire;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplicationWireGoldenTest {
    private static byte[] bytes(String value){return HexFormat.of().parseHex(value);}
    private static String hex(byte[] value){return HexFormat.of().formatHex(value);}
    @Test void originalImmutableRecipientTransforms()throws IOException {
        List<String> rows;
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/replication-wire.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().distinct().toList();}
        assertEquals(1474,rows.size(),"unique operation/input/expected-output vectors");
        int index=0;
        for(String line:rows){var row=line.split("\t",-1);var a=row[1].split(",",-1);String result;
            switch(row[0]){
                case "tail" -> result=hex(SnapshotWire.tailWithout(bytes(a[0]),Arrays.stream(a[1].split(":")).map(Integer::valueOf).collect(Collectors.toSet())));
                case "parts" -> {var parsed=SnapshotWire.parse(bytes(a[0]));result=SnapshotWire.observerParts(parsed.prefix(),List.copyOf(parsed.rows().values()),parsed.tail(),Integer.parseInt(a[1])).stream().map(ReplicationWireGoldenTest::hex).collect(Collectors.joining(";"));}
                case "local" -> {byte[] data=bytes(a[0]),before=data.clone();result=hex(SnapshotWire.localOverride(data,SnapshotWire.toolOffsets(data),Integer.parseInt(a[1])));assertArrayEquals(before,data);}
                case "owner" -> {byte[] data=bytes(a[1]),before=data.clone();result=hex(SnapshotWire.ownerPayload(bytes(a[0]),data));assertArrayEquals(before,data);}
                case "parse" -> {var parsed=SnapshotWire.parse(bytes(a[0]));result=parsed==null?"-":hex(parsed.prefix())+"|"+parsed.rows().keySet().stream().map(Object::toString).collect(Collectors.joining(":"))+"|"+hex(parsed.tail());}
                default -> throw new AssertionError(row[0]);
            }
            assertEquals(row[2],result,"operation "+index+++" "+row[0]);
        }
    }
}
