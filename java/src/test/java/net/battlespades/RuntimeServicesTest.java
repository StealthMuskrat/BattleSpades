// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.battlespades.service.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeServicesTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    private static Map<String,Object> fixture()throws Exception{try(var input=Objects.requireNonNull(RuntimeServicesTest.class.getResourceAsStream("/golden/runtime-services.json"))){return map(MetadataDocument.json(new String(input.readAllBytes(),StandardCharsets.UTF_8)));}}
    private static Discovery.State state(List<Discovery.Player> players,List<Discovery.Connection> connections){return new Discovery.State("Café 🎮","Live Map","cctf",32887,90087911866072064L,4,true,false,"1.0.0.0",'l',Discovery.tags("cctf",168,8,"eu","",List.of("relay=123","x".repeat(128))),true,false,5.9,10,"Bleu","Grün",4,5,players,connections);}
    @Test void originalA2SWireDurationsIdentityReplacementAndRetailTagsMatch()throws Exception{
        Object human=new Object(),bot=new Object();var players=new ArrayList<>(List.of(new Discovery.Player(human,7,"Zoë",1L<<40,false),new Discovery.Player(bot,2,"",-(1L<<40),true)));var connections=List.of(new Discovery.Connection(human,false,true),new Discovery.Connection(null,false,true));double[] clock={100};var protocol=new Discovery(()->state(players,connections),()->clock[0],()->0x12345678);var fixture=fixture();
        for(var value:(List<?>)fixture.get("requests")){var row=map(value);byte[] request=HexFormat.of().parseHex((String)row.get("request"));byte[] response=protocol.reply(request);if(row.get("response")==null)assertTrue(response==null||response.length==0);else assertEquals(row.get("response"),HexFormat.of().formatHex(response),row.get("request").toString());}
        clock[0]=103.25;byte[] request=HexFormat.of().parseHex("ffffffff5578563412");assertEquals(fixture.get("duration"),HexFormat.of().formatHex(protocol.reply(request)));players.set(0,new Discovery.Player(new Object(),7,"New",3,false));clock[0]=104;assertEquals(fixture.get("replacement"),HexFormat.of().formatHex(protocol.reply(request)));
        for(var rowValue:(List<?>)fixture.get("tags")){var row=map(rowValue);assertEquals(row.get("value"),Discovery.tags((String)row.get("mode"),168,8,"eu","",List.of("relay=123","x".repeat(128))));}
        assertThrows(IllegalArgumentException.class,()->Discovery.tags("tdm",168,8,"é".repeat(70),"",List.of()));
        var capped=state(players,List.of(new Discovery.Connection(null,false,true),new Discovery.Connection(null,false,true),new Discovery.Connection(null,false,true)));assertEquals(new Discovery.Population(4,4,0,4),Discovery.population(capped));
        var next=new AtomicInteger(42);var rotating=new Discovery(()->state(players,connections),()->clock[0],next::getAndIncrement);for(int i=0;i<17999;i++)rotating.update();assertEquals("ffffffff412a000000",HexFormat.of().formatHex(rotating.reply(HexFormat.of().parseHex("ffffffff57"))));rotating.update();assertEquals("ffffffff412b000000",HexFormat.of().formatHex(rotating.reply(HexFormat.of().parseHex("ffffffff57"))));
        var negative=new Discovery(()->state(players,connections),()->clock[0],()->-1);assertEquals('I',negative.reply(HexFormat.of().parseHex("ffffffff54"))[4]);
    }
    @Test void boundedMetricsUseOriginalEvenRoundingAndRetainCountersAcrossWindows()throws Exception{
        var metrics=new RuntimeMetrics();var snapshots=(List<?>)fixture().get("metrics");for(int window=0;window<5;window++){metrics.resetTimingSamples();double[] values=switch(window){case 0->new double[0];case 1->new double[]{2,8};case 2->new double[]{4,1,7};case 3->new double[]{9,3,8,2};default->java.util.stream.IntStream.range(0,36005).asDoubleStream().toArray();};for(double value:values)metrics.recordTick(value);metrics.recordSubsystem("z",3);metrics.recordSubsystem("a",1);metrics.recordSubsystem("a",2);metrics.recordWorldPacket(9,3);metrics.recordWorldDelivery(2,99);assertNumbers(map(snapshots.get(window)),metrics.snapshot());}metrics.resetTimingSamples();assertNumbers(map(snapshots.get(5)),metrics.snapshot());assertEquals(0,new TelemetryService(null).snapshot().get("logging_dropped_records").intValue());
    }
    private static void assertNumbers(Map<String,Object> expected,Map<String,Number> actual){assertEquals(expected.keySet(),actual.keySet());expected.forEach((key,value)->assertEquals(((Number)value).doubleValue(),actual.get(key).doubleValue(),1e-9,key));}
    @Test void fullQueueDrainsAsynchronouslyFormatsOffOwnerIsolatesFailuresAndRotatesUtf8()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var records=new CopyOnWriteArrayList<String>();var formatted=new AtomicReference<Thread>();var closed=new AtomicBoolean();Thread owner=Thread.currentThread();var sink=new LoggingRuntime.Sink(){public void write(String record){entered.countDown();try{assertTrue(release.await(4,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}records.add(record);}public void close(){closed.set(true);}};
        var logging=new LoggingRuntime(1,LoggingRuntime.Level.INFO,List.of(new LoggingRuntime.Sink(){public void write(String s)throws java.io.IOException{throw new java.io.IOException("controlled sink failure");}public void close(){}},sink));var telemetry=new TelemetryService(logging);
        try{assertTrue(logging.log(LoggingRuntime.Level.INFO,"owner",()->{formatted.set(Thread.currentThread());return "first";},null));assertTrue(entered.await(4,TimeUnit.SECONDS));for(int i=0;i<256;i++)assertTrue(logging.log(LoggingRuntime.Level.WARNING,"queue",()->"queued",null));assertFalse(logging.log(LoggingRuntime.Level.INFO,"overflow",()->"dropped",null));assertEquals(1,telemetry.snapshot().get("logging_dropped_records").longValue());var stopped=telemetry.stop().toCompletableFuture();assertFalse(stopped.isDone());assertFalse(logging.log(LoggingRuntime.Level.INFO,"late",()->"late",null));release.countDown();stopped.get(4,TimeUnit.SECONDS);assertEquals(257,records.size());assertEquals(257,logging.failedRecords());assertTrue(closed.get());assertNotSame(owner,formatted.get());}finally{release.countDown();logging.stop().toCompletableFuture().get(4,TimeUnit.SECONDS);}
        Path file=temp.resolve("logs/server.log");var settings=new LoggingRuntime.Settings(file,false,1,1,1,LoggingRuntime.Level.DEBUG);var disk=LoggingRuntime.open(settings);String text="x".repeat(550000)+"é 🎮";disk.log(LoggingRuntime.Level.INFO,"utf8",()->text,null);disk.log(LoggingRuntime.Level.ERROR,"utf8",()->text,new IllegalStateException("captured"));disk.stop().toCompletableFuture().get(4,TimeUnit.SECONDS);assertTrue(Files.readString(file).contains("captured"));assertTrue(Files.readString(Path.of(file+".1")).contains(text));assertEquals(0,disk.failedRecords());assertEquals(256,settings.capacity());assertEquals(1048576,settings.maxBytes());
    }
}
