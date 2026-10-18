// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.player.SourceRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VotePolicyBoundaryTest {
    @TempDir Path temp;
    @Test void workerCatalogSurvivesSourceRemovalWhileRotationStaysOnOwner()throws Exception{
        Files.write(temp.resolve("First.vxl"),new byte[0]);Files.write(temp.resolve("Other.vxl"),new byte[0]);Files.write(temp.resolve("Other.botnav"),VotePolicyGoldenTest.nav(1000));
        MapCatalog catalog;try(var worker=Executors.newSingleThreadExecutor()){catalog=worker.submit(()->MapCatalog.load(temp,List.of(),false,Map.of(),new SourceRandom(0))).get(4,TimeUnit.SECONDS);}
        try(var paths=Files.list(temp)){for(var path:paths.toList())Files.delete(path);}
        assertEquals(1000L,catalog.area("OTHER"));assertEquals(List.of("First","Other"),catalog.forMode("tdm",List.of()));assertThrows(UnsupportedOperationException.class,()->catalog.available().clear());
        var rotation=new MapRotation(catalog,MapRotation.Settings.defaults());assertEquals(List.of("Other"),rotation.rank(catalog.available(),"First",4,0));
        for(int i=0;i<20;i++)rotation.notePlayed("Map"+i);rotation.notePlayed(" map19 ");assertEquals(16,rotation.history().size());assertEquals("Map4",rotation.history().getFirst());assertEquals("Map19",rotation.history().getLast());
        try(var worker=Executors.newSingleThreadExecutor()){var error=assertThrows(ExecutionException.class,()->worker.submit(rotation::history).get(4,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,error.getCause());}
        assertThrows(ArithmeticException.class,()->MapRotation.fit(1L,0,1,0));
    }
    @Test void navigationCacheUsesModificationAndSizeAndDoesNotCacheMissingFiles()throws Exception{
        Path path=temp.resolve("cache.botnav");assertNull(NavigationAreas.read(path));byte[] initial=VotePolicyGoldenTest.nav(1000);Files.write(path,initial);assertEquals(1000,NavigationAreas.read(path));FileTime stamp=Files.getLastModifiedTime(path);
        // Same stat identity intentionally retains the advisory result, matching source cache semantics.
        Files.write(path,new byte[initial.length]);Files.setLastModifiedTime(path,stamp);assertEquals(1000,NavigationAreas.read(path));Files.setLastModifiedTime(path,FileTime.fromMillis(stamp.toMillis()+2000));assertNull(NavigationAreas.read(path));
        Files.write(path,initial);assertEquals(1000,NavigationAreas.read(path));Files.delete(path);assertNull(NavigationAreas.read(path));
    }
}
