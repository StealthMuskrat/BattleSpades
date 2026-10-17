// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.BlockDamageModel;

final class BlockDamageGoldenTest {
    static IntStream parts(){return IntStream.range(0,21);}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static void exact(String expected,double actual){assertEquals(Long.parseUnsignedLong(expected,16),Double.doubleToRawLongBits(actual));}
    @ParameterizedTest @MethodSource("parts") void sourceTerrainFunctions(int part)throws Exception{
        try(var input=getClass().getResourceAsStream("/golden/block-damage-%02d.tsv.gz".formatted(part))){assertNotNull(input);
            try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(input),java.nio.charset.StandardCharsets.UTF_8))){
                for(String line;(line=reader.readLine())!=null;){var r=line.split("\t");
                    if(r[0].equals("M")){double d=number(r[1]);int component=Integer.parseInt(r[2]),rgb=Integer.parseInt(r[3]);exact(r[4],BlockDamageModel.ceil4(d));exact(r[5],BlockDamageModel.wireDamage(d));assertEquals(Long.parseLong(r[6]),BlockDamageModel.retailRound(d));assertEquals(Integer.parseInt(r[7]),BlockDamageModel.dim(component,d));assertEquals(Integer.parseInt(r[8]),BlockDamageModel.dimRgb(rgb,d));continue;}
                    int type=Integer.parseInt(r[1]),seed=Integer.parseInt(r[2]);var p=r[3].split(",");var position=new Vector(number(p[0]),number(p[1]),number(p[2]));double amount=BlockDamageModel.wireDamage(number(r[4]));exact(r[5],amount);var kind=BlockDamageModel.kind(type);
                    if(r[6].equals("-"))assertNull(kind);else{var expected=r[6].split(",");assertEquals(expected[0].toUpperCase(Locale.ROOT),kind.kind().name());exact(expected[1],kind.parameter());}assertEquals(r[7].equals("1"),BlockDamageModel.nativeType(type));
                    var hits=BlockDamageModel.footprint(type,position,amount,seed);var expected=r[8].equals("-")?new String[0]:r[8].split(";");assertEquals(expected.length,hits.size());
                    for(int i=0;i<expected.length;i++){var cell=expected[i].split(",");var hit=hits.get(i);assertEquals(new BlockDamageModel.Cell(Integer.parseInt(cell[0]),Integer.parseInt(cell[1]),Integer.parseInt(cell[2])),hit.cell());exact(cell[3],hit.damage());assertEquals(cell[4].equals("1"),BlockDamageModel.damageable(hit));}
                }
            }
        }
    }
}
