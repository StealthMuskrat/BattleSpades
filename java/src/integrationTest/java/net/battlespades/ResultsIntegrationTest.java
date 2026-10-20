// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.battlespades.results.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ResultsIntegrationTest {
    @TempDir Path temp;
    private static void pump(OwnerLoop owner,BooleanSupplier done){long deadline=System.nanoTime()+5_000_000_000L;while(!done.getAsBoolean()){assertTrue(System.nanoTime()<deadline,"HTTP result continuation timed out");owner.pump(System.nanoTime());Thread.onSpinWait();}}
    @Test void actualMasterRequestsRetryTheSameDurableEnvelopeAndRejectInvalidOrUnboundedAcknowledgements()throws Exception{
        var requests=new CopyOnWriteArrayList<String>();var headers=new CopyOnWriteArrayList<String>();var unique=ConcurrentHashMap.<String>newKeySet();var reply=new AtomicInteger(500);var server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.createContext("/api/master/stats",exchange->{String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.add(body);headers.add(exchange.getRequestHeaders().getFirst("Authorization"));unique.add(body);int code=reply.get();byte[] response=(code==201?"not json":code==202?"x".repeat(65537):code==203?"{\"accepted\":\"true\"}":"{\"accepted\":true}").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(code>=201&&code<=203?200:code,response.length);try(var output=exchange.getResponseBody()){output.write(response);}catch(java.io.IOException cancelled){/* The bounded HTTP subscriber deliberately cancels oversized bodies. */}finally{exchange.close();}});server.start();
        var address=server.getAddress();var uri=new URI("http",null,address.getAddress().getHostAddress(),address.getPort(),null,null,null);String secret="integration-only-write-credential";
        try(var client=new HttpMasterResults(uri,secret,Duration.ofSeconds(2));var worker=Executors.newSingleThreadExecutor();var owner=new OwnerLoop(0,1024,256,error->fail(error))){
            assertFalse(client.toString().contains(secret));var event=ResultOutbox.Event.from(Map.of("event_id","round_test","server_id","one:27015","players",List.of(Map.of("name","雪"))));var outbox=new ResultOutbox(temp.resolve("spool"),temp.resolve("mirror"));var publisher=new ResultPublisher(outbox,"one:27015",worker,owner,client,error->fail(error));publisher.offer(event);var uncertain=publisher.flush().toCompletableFuture();pump(owner,uncertain::isDone);assertThrows(CompletionException.class,uncertain::join);assertEquals(List.of(event),outbox.pending("one:27015",64));var stopping=publisher.shutdown().toCompletableFuture();pump(owner,stopping::isDone);stopping.join();
            reply.set(200);var restarted=new ResultPublisher(new ResultOutbox(temp.resolve("spool"),temp.resolve("mirror")),"one:27015",worker,owner,client,error->fail(error));var accepted=restarted.flush().toCompletableFuture();pump(owner,accepted::isDone);accepted.join();assertTrue(outbox.pending("one:27015",64).isEmpty());assertFalse(Files.exists(temp.resolve("mirror/round_test.json")));assertEquals(1,unique.size());assertTrue(requests.size()>=2);assertTrue(requests.stream().allMatch(event.json()::equals));assertTrue(headers.stream().allMatch(("Bearer "+secret)::equals));assertFalse(event.json().contains(secret));
            reply.set(201);assertThrows(ExecutionException.class,()->client.post(event).toCompletableFuture().get(4,TimeUnit.SECONDS));reply.set(202);assertThrows(ExecutionException.class,()->client.post(event).toCompletableFuture().get(4,TimeUnit.SECONDS));reply.set(203);assertFalse(client.post(event).toCompletableFuture().get(4,TimeUnit.SECONDS));var closed=restarted.shutdown().toCompletableFuture();pump(owner,closed::isDone);closed.join();
            assertThrows(IllegalArgumentException.class,()->new HttpMasterResults(URI.create("http://example.com"),secret,Duration.ofSeconds(2)));assertThrows(IllegalArgumentException.class,()->new HttpMasterResults(URI.create("https://example.com?redirect=bad"),secret,Duration.ofSeconds(2)));
        }finally{server.stop(0);}
    }
    @Test void packagedJavaImportsLegacyEnvelopesIdempotentlyWithoutLoadingPythonOrSqlite()throws Exception{
        Path input=temp.resolve("legacy.ndjson"),directory=temp.resolve("imported");var event=ResultOutbox.Event.from(Map.of("event_id","round_import","server_id","one:27015","players",List.of(Map.of("name","Canonical雪"))));Files.writeString(input,event.json()+"\n");
        Path java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java");String jar=Objects.requireNonNull(System.getProperty("battlespades.applicationJar"));
        for(int i=0;i<2;i++){var process=new ProcessBuilder(java.toString(),"-jar",jar,"--results-import",input.toString(),directory.toString()).redirectErrorStream(true).start();try{assertTrue(process.waitFor(10,TimeUnit.SECONDS),"Packaged importer timed out");assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}}
        var imported=new ResultOutbox(directory,null);assertEquals(List.of(event),imported.pending("one:27015",64));Files.writeString(input,JsonData.encode(Map.of("event_id","../escape","server_id","one:27015")));assertThrows(java.io.IOException.class,()->imported.importJsonLines(input));assertEquals(List.of(event),imported.pending("one:27015",64));assertFalse(Files.exists(temp.resolve("escape.json")));
    }
}
