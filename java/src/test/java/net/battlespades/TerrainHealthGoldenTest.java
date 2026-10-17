// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.protocol.*;
import net.battlespades.world.*;
import net.battlespades.world.MutableWorld.Cell;

final class TerrainHealthGoldenTest {
    static IntStream traces(){return IntStream.range(0,36);}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static void exact(String expected,double actual){assertEquals(Long.parseUnsignedLong(expected,16),Double.doubleToRawLongBits(actual));}
    private static Cell cell(String value){var c=value.split(",");return new Cell(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]));}
    private static String packets(BlockManagerState rows){var result=new ArrayList<GamePacket>();if(!rows.users().isEmpty())result.add(new BlockManagerState(rows.users(),List.of()));if(!rows.damaged().isEmpty())result.add(new BlockManagerState(List.of(),rows.damaged()));return wire(result);}
    private static String wire(List<? extends GamePacket> packets){return packets.isEmpty()?"-":String.join(";",packets.stream().map(p->HexFormat.of().formatHex(p.encode())).toList());}
    @ParameterizedTest @MethodSource("traces") void nativeWorldLedgerAndCanonicalRepair(int trace)throws Exception{
        VxlMap base;try(var in=getClass().getResourceAsStream("/golden/world/cave.vxl")){base=VxlMap.load(in.readAllBytes(),"auto");}
        try(var world=new MutableWorld(base,1,128);var in=getClass().getResourceAsStream("/golden/terrain-health.tsv.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),java.nio.charset.StandardCharsets.UTF_8))){
            var health=world.terrainHealth();double scale=trace<32?new double[]{.25,1,1.25,10}[trace%4]:10;var mode=trace>=32?TerrainHealth.UserMode.NORMAL:new TerrainHealth.UserMode[]{TerrainHealth.UserMode.NORMAL,TerrainHealth.UserMode.CLASSIC,TerrainHealth.UserMode.UGC}[trace%3];health.configure(scale,mode);
            for(String line;(line=reader.readLine())!=null;){var r=line.split("\t");if(Integer.parseInt(r[0])!=trace)continue;var c=cell(r[4]);
                switch(r[3]){case "scale"->health.configure(number(r[5]),mode);case "remove"->world.set(c,false,0,null);case "set"->{var args=r[5].split(",");world.set(c,true,Integer.parseInt(args[0]),args[1].equals("-")?null:number(args[1]));}case "damage"->{var result=health.apply(new BlockDamageModel.Cell(c.x(),c.y(),c.z()),number(r[5]),5);var expected=r[6].split(",");exact(expected[0],result.total());assertEquals(expected[1].equals("1"),result.destroyed());}default->fail(r[3]);}
                for(var row:r[7].split(";")){var s=row.split(",");var p=new Cell(Integer.parseInt(s[0]),Integer.parseInt(s[1]),Integer.parseInt(s[2]));assertEquals(s[3].equals("1"),world.solid(p),line);assertEquals((int)Long.parseLong(s[4]),world.color(p),line);assertEquals(s[5].equals("1"),world.explicit(p),line);exact(s[6],world.initialHealth(p));exact(s[7],health.accumulated(p));}
                assertEquals(r[8],packets(health.rows(null,false)),line);assertEquals(r[9],packets(health.rows(null,true)),line);assertEquals(r[10],packets(health.rows(List.of(c),false)),line);
                var shades=health.shades(null).stream().map(s->s.cell().x()+","+s.cell().y()+","+s.cell().z()+","+s.rgb()).toList();assertEquals(r[11],shades.isEmpty()?"-":String.join(";",shades),line);
                var hits=health.replays(null).stream().map(h->h.cell().x()+","+h.cell().y()+","+h.cell().z()+","+String.join(",",h.amounts().stream().map(v->"%016x".formatted(Double.doubleToRawLongBits(v))).toList())).toList();assertEquals(r[12],hits.isEmpty()?"-":String.join(";",hits),line);assertEquals(r[13],wire(world.canonical(c,7,1000)),line);assertEquals(r[14],wire(health.reveal(7,1000,new int[]{0,1,2,128,5000}[trace%5])),line);
            }
        }
    }
}
