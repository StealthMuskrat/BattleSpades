// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import net.battlespades.player.SpawnGeometry;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PreparedMapTest {
    @TempDir Path temp;
    static Stream<Map<?,?>> maps()throws Exception{
        try(var in=Objects.requireNonNull(PreparedMapTest.class.getResourceAsStream("/golden/prepared-map.json"))){return ((List<?>)((Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases")).stream().map(r->(Map<?,?>)r);}
    }
    @ParameterizedTest @MethodSource("maps") void actualWorkerPreparedCachesMatchOriginalLoadAndTransferWithoutRecomputing(Map<?,?> row)throws Exception {
        String name=(String)row.get("name");Files.write(temp.resolve(name+".vxl"),HexFormat.of().parseHex((String)row.get("raw")));Files.writeString(temp.resolve(name+".json"),(String)row.get("metadata"));
        Thread caller=Thread.currentThread();var workerThreads=ConcurrentHashMap.<Thread>newKeySet();
        try(var worker=Executors.newSingleThreadExecutor()){
            var preflight=new WorldPreflight(temp,work->{worker.execute(()->{workerThreads.add(Thread.currentThread());work.run();});});
            var prepared=preflight.prepare(name,(String)row.get("mode")).toCompletableFuture().get(5,TimeUnit.SECONDS);assertFalse(workerThreads.contains(caller));assertEquals(1,workerThreads.size());
            try(var first=prepared.bind(1,64);var second=prepared.bind(2,64)){
                assertNotSame(first.world(),second.world());assertNotSame(first.spawns(),second.spawns());
                for(var value:(List<?>)row.get("teams")){
                    var team=(Map<?,?>)value;int id=((Number)team.get("team")).intValue();
                    var expected=((List<?>)team.get("candidates")).stream().map(v->{var c=(List<?>)v;return new SpawnGeometry.Candidate(((Number)c.get(0)).intValue(),((Number)c.get(1)).intValue(),c.size()==3?((Number)c.get(2)).intValue():null);}).toList();
                    assertEquals(expected,first.spawns().candidates(id));var anchor=(List<?>)team.get("anchor");assertEquals(new SpawnGeometry.Position(((Number)anchor.get(0)).doubleValue(),((Number)anchor.get(1)).doubleValue(),((Number)anchor.get(2)).doubleValue()),first.spawns().baseAnchor(id));
                    // Nonempty pools and anchors share immutable worker results, never a mutable owner object.
                    if(!expected.isEmpty()){assertSame(first.spawns().candidates(id),second.spawns().candidates(id));assertThrows(UnsupportedOperationException.class,()->first.spawns().candidates(id).clear());}
                    assertSame(first.spawns().baseAnchor(id),second.spawns().baseAnchor(id));
                }
                assertInstanceOf(IllegalStateException.class,worker.submit(()->{try{first.spawns().candidates(2);return null;}catch(RuntimeException error){return error;}}).get());
                assertEquals(0,first.world().revision());assertEquals(0,second.world().revision());
            }
        }
    }
    @Test void cacheTransferRejectsSameChecksumDifferentTerrainEditedWorldChangedLayoutAndClosedWorld(){
        byte[] raw={0,(byte)239,(byte)239,0,1,2,3,4};var terrain=VxlMap.loadServer(raw,"retail");var layout=SpawnGeometry.Layout.defaults();SpawnGeometry.Prepared prepared;
        try(var workerWorld=new MutableWorld(terrain,0,1)){prepared=new SpawnGeometry(workerWorld,layout).prepare(terrain);}
        try(var unrelated=new MutableWorld(VxlMap.loadServer(raw,"retail"),1,1);var edited=new MutableWorld(terrain,2,1);var restored=new MutableWorld(terrain,3,1);var correct=new MutableWorld(terrain,4,1)){
            assertEquals(unrelated.sourceCrc(),correct.sourceCrc());assertThrows(IllegalArgumentException.class,()->prepared.bind(unrelated,layout));
            edited.set(new MutableWorld.Cell(252,252,100),true,1,null);assertThrows(IllegalArgumentException.class,()->prepared.bind(edited,layout));
            restored.restoreStaticLight(252,252,100,1);assertEquals(0,restored.revision());assertThrows(IllegalArgumentException.class,()->prepared.bind(restored,layout));
            assertThrows(IllegalArgumentException.class,()->prepared.bind(correct,new SpawnGeometry.Layout(Map.of(),Map.of(),Map.of())));assertSame(correct,prepared.bind(correct,layout).world());
        }
        var closed=new MutableWorld(terrain,5,1);closed.close();assertThrows(IllegalStateException.class,()->prepared.bind(closed,layout));
    }
    @Test void preparedCandidatesAreStillValidatedAgainstLiveTerrainEdits()throws Exception {
        var row=maps().filter(r->((Number)r.get("case")).intValue()==1).findFirst().orElseThrow();Files.write(temp.resolve("live.vxl"),HexFormat.of().parseHex((String)row.get("raw")));Files.writeString(temp.resolve("live.json"),(String)row.get("metadata"));var map=PreparedMap.prepare(MapAssembly.load(temp.resolve("live.vxl"),"tdm"));
        try(var bound=map.bind(1,64)){
            var geometry=bound.spawns();var candidate=geometry.candidates(2).getFirst();assertNotNull(geometry.candidatePosition(candidate,geometry.zones(2)));int z=bound.world().surfaceZ(candidate.x(),candidate.y());bound.world().set(new MutableWorld.Cell(candidate.x(),candidate.y(),z-2),true,1,null);assertNull(geometry.candidatePosition(candidate,geometry.zones(2)));
        }
    }
}
