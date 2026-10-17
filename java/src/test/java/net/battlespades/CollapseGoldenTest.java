// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.world.BlockDamageModel.Cell;
import net.battlespades.world.TerrainCollapse;

final class CollapseGoldenTest {
    private static final List<String> ROWS=load();
    private static List<String> load(){try(var in=CollapseGoldenTest.class.getResourceAsStream("/golden/collapse.tsv.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),java.nio.charset.StandardCharsets.UTF_8))){return reader.lines().toList();}catch(IOException failure){throw new UncheckedIOException(failure);}}
    static IntStream groups(){return IntStream.range(0,16);}
    private static List<Cell> cells(String value){if(value.equals("-"))return List.of();return Arrays.stream(value.split(";")).map(s->{var c=s.split(",");return new Cell(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]));}).toList();}
    @ParameterizedTest @MethodSource("groups") void originalNativeAndFallbackChunks(int group){
        for(int index=group*32;index<Math.min(ROWS.size(),(group+1)*32);index++){var r=ROWS.get(index).split("\t");var solids=new HashSet<>(cells(r[2]));var expected=r[4].equals("-")?List.<List<Cell>>of():Arrays.stream(r[4].split("\\|")).map(CollapseGoldenTest::cells).toList();assertEquals(expected,TerrainCollapse.find((x,y,z)->solids.contains(new Cell(x,y,z)),cells(r[3]),Long.parseLong(r[1])),"source case "+r[0]);}
    }
}
