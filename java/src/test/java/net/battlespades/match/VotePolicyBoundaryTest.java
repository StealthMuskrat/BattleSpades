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
        var rotation=new MapRotation(catalog,MapRotation.Settings.defaults());assertEquals(List.of("Other"),rotation.rank(catalog.available(),"First",List.of(false,false,false,false),Boolean::booleanValue));
        for(int i=0;i<20;i++)rotation.notePlayed("Map"+i);rotation.notePlayed(" map19 ");assertEquals(16,rotation.history().size());assertEquals("Map4",rotation.history().getFirst());assertEquals("Map19",rotation.history().getLast());
        try(var worker=Executors.newSingleThreadExecutor()){var error=assertThrows(ExecutionException.class,()->worker.submit(rotation::history).get(4,TimeUnit.SECONDS));assertInstanceOf(IllegalStateException.class,error.getCause());}
        assertThrows(ArithmeticException.class,()->MapRotation.fit(1L,0,1,0));
    }
    @Test void lobbySettingsClampSourceBoundsNormalizeRotationAndPrepareOnlyAnImmutableStartupCatalog()throws Exception{
        var settings=VotingSettings.from(org.tomlj.Toml.parse("[lobby]\nmap_rotation=[' First.vxl ','first.VXL','Other']\nmap_rotation_shuffle=false\nmap_vote_area_per_player=0\nmap_vote_min_area=999999\nmap_vote_recent_exclude=99\nmap_vote_bot_weight=-1\nvotekick_cooldown_seconds=99999\nvotekick_cancelled_cooldown_seconds=-1\nvotekick_min_team_players=99\n[lobby.map_size_overrides]\nFirst=' SMALL '\nOther=0\n"),net.battlespades.rules.GameRules.serverDefaults());
        assertEquals(List.of("First","Other"),settings.rotation());assertFalse(settings.shuffle());assertEquals(1,settings.fit().perPlayer());assertEquals(262144,settings.fit().minimumArea());assertEquals(32,settings.fit().recentExclude());assertEquals(0,settings.fit().botWeight());assertEquals(3600,settings.kicks().cooldown());assertEquals(0,settings.kicks().cancelledCooldown());assertEquals(32,settings.kicks().minimumTeamPlayers());assertEquals(.5,settings.kicks().ratio());assertThrows(UnsupportedOperationException.class,()->settings.rotation().clear());assertThrows(UnsupportedOperationException.class,()->settings.overrides().clear());
        for(String value:List.of("map_rotation='bad'","map_rotation=['../bad']","map_vote_bot_weight=nan","map_vote_size_fit='yes'","map_size_overrides={First=true}","map_size_overrides={First='huge'}"))assertThrows(IllegalArgumentException.class,()->VotingSettings.from(org.tomlj.Toml.parse("[lobby]\n"+value),net.battlespades.rules.GameRules.serverDefaults()),value);
        Files.write(temp.resolve("First.vxl"),new byte[0]);Files.write(temp.resolve("Other.vxl"),new byte[0]);MapCatalog catalog;try(var worker=Executors.newSingleThreadExecutor()){catalog=worker.submit(()->settings.prepareCatalog(temp,new SourceRandom(0))).get(4,TimeUnit.SECONDS);}Files.delete(temp.resolve("First.vxl"));Files.delete(temp.resolve("Other.vxl"));assertEquals(List.of("First","Other"),catalog.available());assertEquals(50000L,catalog.area("First"));assertEquals(1L,catalog.area("Other"));
    }
    @Test void navigationCacheUsesModificationAndSizeAndDoesNotCacheMissingFiles()throws Exception{
        Path path=temp.resolve("cache.botnav");assertNull(NavigationAreas.read(path));byte[] initial=VotePolicyGoldenTest.nav(1000);Files.write(path,initial);assertEquals(1000,NavigationAreas.read(path));FileTime stamp=Files.getLastModifiedTime(path);
        // Same stat identity intentionally retains the advisory result, matching source cache semantics.
        Files.write(path,new byte[initial.length]);Files.setLastModifiedTime(path,stamp);assertEquals(1000,NavigationAreas.read(path));Files.setLastModifiedTime(path,FileTime.fromMillis(stamp.toMillis()+2000));assertNull(NavigationAreas.read(path));
        Files.write(path,initial);assertEquals(1000,NavigationAreas.read(path));Files.delete(path);assertNull(NavigationAreas.read(path));
    }
}
