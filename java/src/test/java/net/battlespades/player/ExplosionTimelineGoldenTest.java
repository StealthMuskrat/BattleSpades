// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class ExplosionTimelineGoldenTest {
    static IntStream groups(){return IntStream.range(0,12);}
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    @ParameterizedTest @MethodSource("groups") void originalDenseReceiveSequenceBlastPhysics(int group)throws Exception{
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/explosion-timeline.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).filter(r->Integer.parseInt(r[0])==group).toList();}
        try(var replay=new InfantryGoldenTest.Replay(Integer.parseInt(rows.getFirst()[1]))){int step=0;for(var r:rows){String[] result;String returned="-";var a=r[3].split(",");switch(r[2]){case "explosion"->{replay.events.clear();replay.h.wire.clear();replay.lanes.clear();var sequence=replay.p().motion().queueExplosion(replay.p(),Integer.parseInt(a[0]),new Vector(number(a[1]),number(a[2]),number(a[3])),number(a[4]),number(a[5]),number(a[6]));returned=sequence==null?"-":sequence.toString();result=replay.result();}case "bot"->{replay.events.clear();replay.h.wire.clear();replay.lanes.clear();replay.p().bot(r[3].equals("1"));result=replay.result();}default->result=replay.operation(r[2],r[3]);}String where="group "+group+" step "+step+++" "+r[2];assertEquals(r[4],returned,where+" target");assertEquals(r[5],result[0],where+" body/façade/chronology");assertEquals(r[6],result[1],where+" hooks");assertEquals(r[7],result[2],where+" wire");var pending=replay.p().motion().pendingExplosions().stream().map(e->e.targetInputSequence()+","+vector(e.origin())+","+bits(e.radius())+","+bits(e.minimum())+","+bits(e.maximum())).toList();assertEquals(r[8],pending.isEmpty()?"-":String.join(";",pending),where+" pending");}}
    }
}
