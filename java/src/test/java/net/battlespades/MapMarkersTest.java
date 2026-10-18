// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;
import net.battlespades.world.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MapMarkersTest {
    private record Case(String name,List<String[]> rows){}
    @TestFactory Stream<DynamicTest> originalServerCleanupAndStaticLights() throws Exception {
        var cases=new ArrayList<Case>();List<String[]> current=null;
        try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/map-markers.tsv.gz"))),StandardCharsets.UTF_8))) {
            for(String line;(line=in.readLine())!=null;){var p=line.split("\t");if(p[0].equals("E")){current=new ArrayList<>();cases.add(new Case(p[1],current));}current.add(p);}
        }
        assertEquals(76,cases.size());return cases.stream().map(c->DynamicTest.dynamicTest(c.name,()->verify(c)));
    }
    private static int n(String value){return (int)Long.parseLong(value);}
    private void verify(Case test) throws Exception {
        var header=test.rows.getFirst();byte[] raw;
        if(header[3].startsWith("@"))raw=Files.readAllBytes(Path.of("../maps",header[3].substring(1)+".vxl"));
        else if(header[3].equals("!classic")){raw=new byte[512*512*8];for(int i=0;i<raw.length;i+=8){raw[i+1]=63;raw[i+2]=63;raw[i+5]=(byte)255;raw[i+7]=(byte)128;}}
        else raw=HexFormat.of().parseHex(header[3]);
        var map=VxlMap.loadServer(raw,header[2]);assertEquals(n(header[4]),map.zShift());assertArrayEquals(raw,map.originalBytes());var crc=new CRC32();crc.update(raw);assertEquals(crc.getValue(),map.sourceCrc());
        var markers=new ArrayList<VxlMap.Marker>();for(var p:test.rows)if(p[0].equals("K"))markers.add(new VxlMap.Marker(n(p[1]),n(p[2]),n(p[3]),n(p[4])));assertEquals(markers,map.markers());
        try(var world=new MutableWorld(map,1,64)) {
            var events=new ArrayList<String>();int[] faults={0};
            world.observeStaticLights((cell,rgb)->{assertEquals(rgb,world.staticLightCells().get(cell));events.add("F:"+cell.x()+","+cell.y()+","+cell.z()+","+rgb);throw new IllegalStateException("controlled observer fault");},failure->faults[0]++);
            world.observeStaticLights((cell,rgb)->events.add("G:"+cell.x()+","+cell.y()+","+cell.z()+","+rgb),failure->fail(failure));
            for(var p:test.rows)switch(p[0]) {
                case "P" -> {var cell=new MutableWorld.Cell(n(p[1]),n(p[2]),n(p[3]));assertEquals(p[4].equals("1"),world.solid(cell),Arrays.toString(p));assertEquals(n(p[5]),world.color(cell),Arrays.toString(p));assertEquals(p[6].equals("1"),world.explicit(cell),Arrays.toString(p));}
                case "C" -> assertArrayEquals(HexFormat.of().parseHex(p[3]),world.serializeColumn(n(p[1]),n(p[2])),Arrays.toString(p));
                case "D" -> {var capture=world.capture(false,true);try(var lease=capture.lease()){assertEquals(n(p[1]),capture.snapshot().dirtyColumnCount());var bytes=new ByteArrayOutputStream();for(var chunk:capture.snapshot().buildChunks())bytes.write(chunk);byte[] plain;if(bytes.size()==0)plain=new byte[0];else try(var zip=new InflaterInputStream(new ByteArrayInputStream(bytes.toByteArray()))){plain=zip.readAllBytes();}assertEquals(p[2],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain)));assertEquals(Long.parseLong(p[3]),world.revision());assertEquals(0,world.journalSize());var packets=new ArrayList<net.battlespades.protocol.GamePacket>();assertEquals(MutableWorld.Replay.COMPLETE,lease.pumpAir(0,0,100,packet->{packets.add(packet);return true;}));assertTrue(packets.isEmpty());}}
                case "S" -> {var values=new ArrayList<String>();world.staticLightCells().forEach((c,rgb)->values.add(c.x()+","+c.y()+","+c.z()+","+rgb));assertEquals(p[1],values.isEmpty()?"-":String.join(";",values));}
                case "L" -> {events.clear();int before=faults[0];boolean ok=world.restoreStaticLight(Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),n(p[4]));assertEquals(p[5].equals("1"),ok);assertEquals(p[6],events.isEmpty()?"-":String.join(";",events));assertEquals(ok?1:0,faults[0]-before);}
                case "F" -> {var capture=world.capture(true,false);try(var lease=capture.lease()){assertEquals(world.revision(),lease.watermark());var bytes=new ByteArrayOutputStream();for(var chunk:capture.snapshot().buildChunks())bytes.write(chunk);try(var zip=new InflaterInputStream(new ByteArrayInputStream(bytes.toByteArray()))){assertEquals(p[1],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip.readAllBytes())));}}}
                case "H" -> {var cell=new MutableWorld.Cell(0,0,10);world.set(cell,true,0x406080,9.);world.terrainHealth().apply(new BlockDamageModel.Cell(0,0,10),.25,5.);long before=world.revision();world.restoreStaticLight(0,0,10,0x010203);assertEquals(Double.parseDouble(p[1]),world.initialHealth(cell));assertEquals(Double.parseDouble(p[2]),world.terrainHealth().accumulated(cell));assertTrue(world.terrainHealth().shades(List.of(cell)).isEmpty());assertTrue(world.terrainHealth().replays(List.of(cell)).isEmpty());assertTrue(world.terrainHealth().rows(null,false).users().isEmpty());assertEquals(Long.parseLong(p[5]),world.revision()-before);}
                default -> {}
            }
        }
    }
}
