// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import net.battlespades.achievements.AchievementStore;
import net.battlespades.match.AchievementCatalog;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AchievementStoreTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    static Map<String,Object> fixture()throws Exception{try(var in=Objects.requireNonNull(AchievementStoreTest.class.getResourceAsStream("/golden/achievement-tiers.json"))){return map(MetadataDocument.json(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));}}
    @Test void allOriginalEngineTierBoundariesMatchPortableTransactions()throws Exception{
        assertEquals(77,AchievementCatalog.definitions().size());String stat=null;AchievementStore store=null;for(var value:(List<?>)fixture().get("tiers")){var row=map(value);String key=(String)row.get("stat");if(!key.equals(stat)){stat=key;store=new AchievementStore(null);}var result=store.transact(List.of(new AchievementStore.Delta("name:fixture",key,((Number)row.get("amount")).longValue(),"Fixture")),List.of(),Set.of(),1000);var account=result.accounts().get("name:fixture");assertEquals(((Number)row.get("value")).longValue(),account==null?0:account.counters().getOrDefault(key,0L),key);assertEquals(row.get("new"),result.created().stream().map(AchievementStore.Created::apiName).toList(),key);assertEquals(row.get("unlocked"),account==null?List.of():account.unlocks().keySet().stream().sorted().toList(),key);}
    }
    @Test void legacySqliteExportReopensRetainsReportedStateAndMergesAcrossStoreInstances()throws Exception{
        Path file=temp.resolve("achievements.json");Files.writeString(file,JsonData.encode(fixture().get("legacy")));var a=new AchievementStore(file);var b=new AchievementStore(file);assertEquals(25,a.snapshot().get("aosplay:42").counters().get("sniper_kill_count"));assertEquals(List.of("spade_kill"),a.transact(List.of(),List.of(),Set.of("aosplay:42"),1100).taken().get("aosplay:42"));assertTrue(b.transact(List.of(),List.of(),Set.of("aosplay:42"),1101).taken().get("aosplay:42").isEmpty());
        var delta=new AchievementStore.Delta("aosplay:42","spade_kill_count",1,"Renamed");a.transact(List.of(delta),List.of(),Set.of(),1102);b.transact(List.of(delta),List.of(),Set.of(),1103);assertEquals(5,new AchievementStore(file).snapshot().get("aosplay:42").counters().get("spade_kill_count"));assertEquals("Naïve 🎮",a.snapshot().get("aosplay:42").unlocks().get("sniper_kill").playerName());
        byte[] before=Files.readAllBytes(file);try(var channel=FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lease=channel.lock()){assertTrue(lease.isValid());assertThrows(java.io.IOException.class,()->b.transact(List.of(delta),List.of(),Set.of(),1104));}assertArrayEquals(before,Files.readAllBytes(file));assertEquals(6,b.transact(List.of(delta),List.of(),Set.of(),1105).accounts().get("aosplay:42").counters().get("spade_kill_count"));
        var request=new AchievementStore.Request("aosplay:42","misc_five_in_a_row","Renamed");assertEquals(1,a.transact(List.of(),List.of(request),Set.of(),1106).created().size());assertTrue(b.transact(List.of(),List.of(request),Set.of(),1107).created().isEmpty());
        try(var first=java.util.concurrent.Executors.newSingleThreadExecutor();var second=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var ready=new java.util.concurrent.CountDownLatch(2);var writers=new ArrayList<java.util.concurrent.Future<?>>();for(var worker:List.of(first,second))writers.add(worker.submit(()->{var store=new AchievementStore(file);ready.countDown();assertTrue(ready.await(4,java.util.concurrent.TimeUnit.SECONDS));for(int i=0;i<2;i++){long deadline=System.nanoTime()+4_000_000_000L;while(true)try{store.transact(List.of(delta),List.of(),Set.of(),1110);break;}catch(java.io.IOException busy){assertTrue(System.nanoTime()<deadline,busy.toString());Thread.onSpinWait();}}return null;}));for(var writer:writers)writer.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }assertEquals(10,new AchievementStore(file).snapshot().get("aosplay:42").counters().get("spade_kill_count"));
        var bad=temp.resolve("old.sqlite");Files.writeString(bad,"SQLite format 3\0");assertThrows(java.io.IOException.class,()->new AchievementStore(bad));assertEquals("SQLite format 3\0",Files.readString(bad));
    }
}
