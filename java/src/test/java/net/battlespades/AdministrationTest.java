// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.admin.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AdministrationTest {
    @TempDir Path temp;
    private static final String SECRET="Long secret phrase 123",TOKEN="aValidRoomClaimToken_123456789";
    @Test void credentialAndVerifiedIdentityContractsExcludeNamesAndKeepOneTimeClaimsAndSharedFailureWindow(){
        double[] now={100};var settings=new AdminAuthority.Settings(SECRET,TOKEN,Set.of("steam:76561198000000000","aosplay:Owner"),3);var auth=new AdminAuthority(settings,()->now[0]);
        assertTrue(AdminAuthority.passwordProblem("changeme")!=null);assertTrue(AdminAuthority.passwordProblem("short")!=null);assertNull(AdminAuthority.passwordProblem("😀😀😀😀😀😀😀😀😀😀😀😀"));assertFalse(settings.toString().contains(SECRET));assertFalse(settings.toString().contains(TOKEN));
        for(String invalid:List.of("Owner","123","steam:bad","aosplay:has spaces","name:Owner"))assertTrue(AdminAuthority.normalizeIdentity(invalid).isEmpty());
        assertEquals("steam:123",AdminAuthority.normalizeIdentity(" STEAM:00123 ").orElseThrow());assertEquals("aosplay:owner",AdminAuthority.normalizeIdentity("AoSPlay:Owner").orElseThrow());
        assertTrue(auth.admin(auth.principal(Set.of("aosplay:OWNER"))));assertFalse(auth.admin(auth.principal(Set.of("Owner"))));assertFalse(auth.admin(auth.principal(Set.of())));
        var first=auth.principal(Set.of());assertEquals("Usage: /admin <password>",auth.login(first,"192.0.2.1","").message());assertEquals(1,auth.login(first,"192.0.2.1","wrong").failures());assertEquals(2,auth.claim(auth.principal(Set.of()),"192.0.2.1","wrong").failures());var banned=auth.login(auth.principal(Set.of()),"192.0.2.1","wrong");assertTrue(banned.kick()&&banned.banAddress());
        var learner=auth.principal(Set.of());assertFalse(auth.claim(learner,"127.0.0.1",TOKEN).kick());assertTrue(auth.admin(learner));assertTrue(auth.roomHost(learner));assertEquals(SECRET,auth.roomPassword(learner).orElseThrow());var replay=auth.principal(Set.of());assertEquals(1,auth.claim(replay,"127.0.0.1",TOKEN).failures());auth.login(replay,"127.0.0.1","wrong");assertFalse(auth.login(replay,"127.0.0.1","wrong").banAddress());assertFalse(auth.admin(replay));
        auth.login(replay,"192.0.2.2","wrong");now[0]+=601;assertEquals(1,auth.login(replay,"192.0.2.2","wrong").failures());auth.login(replay,"192.0.2.2",SECRET);assertTrue(auth.admin(replay));assertFalse(auth.roomHost(replay));
        var dedicated=new AdminAuthority(new AdminAuthority.Settings(SECRET,"",Set.of(),3),()->0);var operator=dedicated.principal(Set.of());dedicated.login(operator,"::1",SECRET);assertTrue(dedicated.roomPassword(operator).isEmpty());assertThrows(IllegalArgumentException.class,()->dedicated.admin(learner));
        for(String loopback:List.of("127.0.0.1","127.255.1.8","::1","[::1]","0:0:0:0:0:0:0:1","localhost"))assertTrue(AdminAuthority.isLoopback(loopback),loopback);
        for(String other:List.of("192.0.2.1","127.999.1.2","::","2001:db8::1","localhost.example"))assertFalse(AdminAuthority.isLoopback(other),other);
        var disabled=new AdminAuthority(new AdminAuthority.Settings("changeme","",Set.of(),1),()->0);var nobody=disabled.principal(Set.of());assertFalse(disabled.login(nobody,"192.0.2.3","changeme").kick());assertFalse(disabled.admin(nobody));
        assertThrows(IllegalArgumentException.class,()->new AdminAuthority.Settings(SECRET,"too_short",Set.of(),3));assertThrows(IllegalArgumentException.class,()->AdminAuthority.Settings.from(org.tomlj.Toml.parse("[anticheat]\nadmin_login_attempts=0")));
    }
    @Test void durableSourceCompatibleBansExpireAndRemoveAcrossReopenWithoutLosingUnicode()throws Exception{
        var path=temp.resolve("bans.json");double[] now={1000};var errors=new ArrayList<Throwable>();
        Files.writeString(path,"{\"192.0.2.1\":{\"name\":\"Old\",\"reason\":\"original Python ban\",\"until\":0}}");
        try(var writer=Executors.newSingleThreadExecutor()){
            try(var store=new BanStore(path,()->now[0],writer,errors::add)){
                assertEquals(1,store.lookup("192.0.2.1").orElseThrow().disconnectReason());assertThrows(java.io.IOException.class,()->new BanStore(path,()->now[0],writer,errors::add));
                store.add("2001:db8::1","雪","réason \"quoted\"\n",30).toCompletableFuture().get(4,TimeUnit.SECONDS);assertEquals(19,store.lookup("2001:db8::1").orElseThrow().disconnectReason());assertEquals(1030,store.lookup("2001:db8::1").orElseThrow().until());
            }
            try(var store=new BanStore(path,()->now[0],writer,errors::add)){assertEquals("雪",store.lookup("2001:db8::1").orElseThrow().name());now[0]=1030;assertTrue(store.lookup("2001:db8::1").isEmpty());store.flush().toCompletableFuture().get(4,TimeUnit.SECONDS);assertTrue(store.remove("192.0.2.1").toCompletableFuture().get(4,TimeUnit.SECONDS));assertFalse(store.remove("absent").toCompletableFuture().join());}
            try(var store=new BanStore(path,()->now[0],writer,errors::add)){assertTrue(store.snapshot().isEmpty());}
        }assertTrue(errors.isEmpty());assertEquals(Map.of(),MetadataDocument.json(Files.readString(path)));
        for(var e:Map.of("30m",1800L,"2H",7200L,"1d",86400L,"2w",1209600L,"90",90L,"perma",0L,"not-a-duration",-1L,"99999999999999999999999999999d",-1L).entrySet())assertEquals(e.getValue().longValue(),BanStore.parseDuration(e.getKey()));
    }
    @Test void immutableQueuedBanSnapshotsAndIoRecoveryKeepExistingDocumentAndReleaseLeases()throws Exception{
        var queue=new ArrayDeque<Runnable>();var errors=new ArrayList<Throwable>();var path=temp.resolve("bans.json");
        try(var store=new BanStore(path,()->1000,queue::add,errors::add)){
            var first=store.add("192.0.2.1","First","one",0).toCompletableFuture();var second=store.add("192.0.2.2","Second","two",0).toCompletableFuture();assertFalse(first.isDone());assertFalse(second.isDone());assertEquals(1,queue.size());queue.remove().run();assertTrue(first.isDone());assertEquals(1,((Map<?,?>)MetadataDocument.json(Files.readString(path))).size());queue.remove().run();assertTrue(second.isDone());
            Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("occupied"),"keep");var broken=store.add("192.0.2.3","Third","three",0).toCompletableFuture();queue.remove().run();assertThrows(CompletionException.class,broken::join);assertEquals(1,errors.size());assertTrue(store.lookup("192.0.2.3").isPresent());
            Files.delete(path.resolve("occupied"));Files.delete(path);var repaired=store.add("192.0.2.4","Fourth","four",0).toCompletableFuture();queue.remove().run();repaired.join();assertEquals(4,((Map<?,?>)MetadataDocument.json(Files.readString(path))).size());
        }
        Files.writeString(path,"{broken");assertThrows(java.io.IOException.class,()->new BanStore(path,()->0,Runnable::run,errors::add));assertEquals("{broken",Files.readString(path));Files.delete(path);try(var reopened=new BanStore(path,()->0,Runnable::run,errors::add)){assertTrue(reopened.snapshot().isEmpty());}
    }
}
