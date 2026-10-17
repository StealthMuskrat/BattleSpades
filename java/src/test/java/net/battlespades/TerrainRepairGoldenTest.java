// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;

final class TerrainRepairGoldenTest {
    static IntStream traces(){return IntStream.range(0,32);}
    private static List<Cell> cells(String value){if(value.equals("-"))return List.of();return Arrays.stream(value.split(";")).map(s->{var a=s.split(",");return new Cell(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]));}).toList();}
    @ParameterizedTest @MethodSource("traces") void sourceQueueSendOrderAndMetrics(int trace)throws Exception{
        try(var world=new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,256);var in=getClass().getResourceAsStream("/golden/terrain-repair.tsv.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),java.nio.charset.StandardCharsets.UTF_8))){
            var settings=new TerrainRepair.Settings[]{new TerrainRepair.Settings(true,new int[]{1,64,70,8192}[trace%4],new int[]{-1,1,6}[trace%3],new int[]{-1,1,3}[trace%3],new int[]{0,1,3}[trace%3],new int[]{0,1,4}[trace%3],new int[]{0,1,5}[trace%3])};var recipients=new ArrayList<>(List.of(new TerrainRepair.Recipient(7,true,true),new TerrainRepair.Recipient(2,true,true),new TerrainRepair.Recipient(5,true,true)));var wire=new ArrayList<String>();int[] attempts={0},fault={0};var repair=new TerrainRepair(world,()->settings[0],()->recipients,(recipient,packet)->{attempts[0]++;if(fault[0]!=0&&recipient.playerId()==2&&attempts[0]%fault[0]==0)throw new IllegalStateException("Controlled send fault");wire.add(recipient.playerId()+":"+HexFormat.of().formatHex(packet.encode()));});
            for(String line;(line=reader.readLine())!=null;){var r=line.split("\t");if(Integer.parseInt(r[0])!=trace)continue;long loop=Long.parseLong(r[2]);int op=Integer.parseInt(r[3]),result=0;wire.clear();switch(op){case 0,2,3->repair.record(cells(r[4]),loop);case 1,7,8->repair.recordCollapse(cells(r[4]),loop);case 4->world.set(new MutableWorld.Cell(10,10,100),r[4].equals("1"),0x406080,r[4].equals("1")?9.:null);case 5->{var s=settings[0];settings[0]=new TerrainRepair.Settings(r[4].equals("1"),s.queueLimit(),s.delayTicks(),s.collapseDelayTicks(),s.intervalTicks(),s.batchLimit(),s.collapseBatchLimit());}case 6->{var a=r[4].split(",");int eligible=Integer.parseInt(a[0]);fault[0]=Integer.parseInt(a[1]);recipients.clear();recipients.add(new TerrainRepair.Recipient(7,eligible!=0,true));recipients.add(new TerrainRepair.Recipient(2,eligible==2,true));recipients.add(new TerrainRepair.Recipient(5,true,false));}case 9->{if(r[4].equals("reset"))repair.reset();else result=repair.tick(loop);}case 10->world.terrainHealth().apply(new Cell(10,10,100),2,5);case 11->result=repair.tick(loop);default->fail(r[3]);}assertEquals(Integer.parseInt(r[5]),result,line);var m=repair.metrics();assertEquals(r[6],repair.pending()+","+m.dropped()+","+m.queuePeak()+","+m.cells()+","+m.sends()+","+m.failedSends()+","+attempts[0],line);assertEquals(r[7],wire.isEmpty()?"-":String.join("|",wire),line);}
        }
    }
}
