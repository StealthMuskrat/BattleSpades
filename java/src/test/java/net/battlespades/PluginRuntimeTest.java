// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.plugin.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.session.*;
import net.battlespades.support.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PluginRuntimeTest {
    @TempDir Path temp;
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object o){return (Map<String,Object>)o;}
    private static Map<String,Object> fixture()throws Exception{try(var in=Objects.requireNonNull(PluginRuntimeTest.class.getResourceAsStream("/golden/plugins.json"))){return map(MetadataDocument.json(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));}}
    private static RuntimePlugin.Context context(java.util.concurrent.Executor owner,List<String> messages){return new RuntimePlugin.Context(){public ApplicationHost host(){throw new UnsupportedOperationException();}public StandardMatchDomain match(){throw new UnsupportedOperationException();}public java.util.concurrent.Executor owner(){return owner;}public void broadcast(String text){messages.add(text);}public void log(String text){}};}
    @Test void intactSourceLifecycleEnabledGatesAndBudgetBoundariesMatch()throws Exception{
        var calls=new ArrayList<String>();var faults=new ArrayList<String>();var warnings=new ArrayList<String>();long[] skipped={0};double[] measured={0};var manager=new PluginManager(context(Runnable::run,new ArrayList<>()),()->measured[0],()->100,(label,error)->faults.add(label),warnings::add,count->skipped[0]+=count);
        for(var rowValue:(List<?>)fixture().get("lifecycle")){var row=map(rowValue);var step=(List<?>)row.get("step");String operation=(String)step.getFirst();Object result=null;String name=step.size()>1?(String)step.get(1):"";
            switch(operation){case "load"->{boolean badLoad=(Boolean)step.get(2),badUnload=(Boolean)step.get(3);double cost=((Number)step.get(4)).doubleValue();result=manager.load(()->{calls.add("construct:"+name);return new RuntimePlugin(){public String name(){return name;}public CompletionStage<Void> onLoad(Context c){calls.add("load:"+name);return badLoad?CompletableFuture.failedFuture(new IllegalArgumentException("controlled")):CompletableFuture.completedFuture(null);}public CompletionStage<Void> onUnload(){calls.add("unload:"+name);if(badUnload)throw new IllegalArgumentException("controlled");return CompletableFuture.completedFuture(null);}public CompletionStage<Void> onEnable(){calls.add("enable:"+name);return CompletableFuture.completedFuture(null);}public CompletionStage<Void> onDisable(){calls.add("disable:"+name);return CompletableFuture.completedFuture(null);}public CompletionStage<Void> onEvent(ModeEvents.Event event){calls.add((event.name().equals("on_tick")?"tick:":"spawn:")+name+":"+event.arguments().getFirst());if(cost<0)throw new IllegalArgumentException("controlled event");measured[0]+=cost;return CompletableFuture.completedFuture(null);}};}).toCompletableFuture().join();}
                case "unload"->result=manager.unload(name).toCompletableFuture().join();case "disable","enable"->result=manager.enable(name,operation.equals("enable")).toCompletableFuture().join();case "event"->manager.event(ModeEvents.Event.of(name,((Number)step.get(2)).intValue()),step.get(3)==null?null:((Number)step.get(3)).doubleValue()).toCompletableFuture().join();default->fail(operation);}
            assertEquals(row.get("result"),result,step.toString());assertEquals(row.get("calls"),calls,step.toString());assertEquals(row.get("plugins"),manager.all().stream().map(p->List.of(p.name(),p.enabled())).toList());assertEquals(((Number)row.get("skipped")).longValue(),skipped[0]);assertEquals(((Number)row.get("warnings")).intValue(),warnings.size());
        }assertEquals(3,faults.size());manager.stop().toCompletableFuture().join();assertTrue(manager.stopped());assertThrows(IllegalStateException.class,()->manager.event(ModeEvents.Event.of("late"),null));
    }
    @Test void actualJarDiscoveryFiltersBrokenNeighborsAndSourceExamplePolicyMatch()throws Exception{
        Path directory=temp.resolve("plugins");Path example=PluginFixtures.jar(directory,"example.jar",Map.of("example/ExamplePlugin.java",PluginFixtures.example()),"example.ExamplePlugin\n");Files.copy(example,directory.resolve("_private.jar"));Files.copy(example,directory.resolve("sample.jar.disabled"));Files.writeString(directory.resolve("broken.jar"),"not a jar");var faults=new java.util.concurrent.CopyOnWriteArrayList<String>();var settings=new PluginCatalog.Settings(true,directory,Set.of(),Set.of(),2);
        try(var catalog=PluginCatalog.prepare(settings,(name,error)->faults.add(name));var life=new LifeHarness()){
            assertEquals(1,catalog.candidates().size());var messages=new ArrayList<String>();var manager=new PluginManager(context(Runnable::run,messages),()->0,()->100,(name,error)->fail(error),_->{},_ ->{});var candidate=catalog.candidates().getFirst();assertTrue(manager.load(candidate.factory(),p->settings.permits(candidate.stem(),p.name())).toCompletableFuture().join());
            var rows=new ArrayList<Scoreboard.Player>();for(int i=0;i<2;i++){life.runtime.admit(i,new net.battlespades.protocol.SimplePackets.NewPlayerConnection(i+2,0,0,0,"P"+i),new net.battlespades.player.ClassCatalog.Selection(0,List.of(5,6,23),List.of(),List.of()),0);rows.add(new Scoreboard.Player(life.runtime.player(i)));}
            for(var value:(List<?>)fixture().get("example")){var row=map(value);Object killer=row.get("killer")==null?null:rows.get(((Number)row.get("killer")).intValue());manager.event(ModeEvents.Event.of("on_player_kill",killer,rows.get(((Number)row.get("victim")).intValue()),0),null).toCompletableFuture().join();assertEquals(row.get("messages"),messages);}manager.stop().toCompletableFuture().join();
        }assertFalse(faults.isEmpty());assertTrue(settings.permits("example","Example"));assertFalse(new PluginCatalog.Settings(true,directory,Set.of("EXAMPLE"),Set.of("example"),2).permits("example","Example"));assertTrue(new PluginCatalog.Settings(true,directory,Set.of("strasse"),Set.of(),2).permits("other","Straße"));assertEquals(.1,new PluginCatalog.Settings(false,directory,Set.of(),Set.of(),0).tickBudgetMillis());
        try(var empty=PluginCatalog.prepare(new PluginCatalog.Settings(false,directory,Set.of(),Set.of(),2),(name,error)->fail(error))){assertTrue(empty.candidates().isEmpty());}Files.delete(example);
    }
    @Test void workerCompletionsResumeOwnerAndShutdownRetainsSuspendedEvents()throws Exception{
        Thread owner=Thread.currentThread();var events=new ArrayList<String>();try(var loop=new OwnerLoop(0,64,64,error->fail(error));var worker=Executors.newSingleThreadExecutor()){
            var manager=new PluginManager(context(loop,new ArrayList<>()),()->0,()->100,(name,error)->fail(error),_->{},_ ->{});var load=new CompletableFuture<Void>();var gate=new CompletableFuture<Void>();manager.load(()->new RuntimePlugin(){public String name(){return "held";}public CompletionStage<Void> onLoad(Context c){return load;}public CompletionStage<Void> onEvent(ModeEvents.Event e){assertSame(owner,Thread.currentThread());events.add(e.name());return gate;}public CompletionStage<Void> onUnload(){assertSame(owner,Thread.currentThread());events.add("unload");return CompletableFuture.completedFuture(null);}});worker.submit(()->load.complete(null)).get(4,TimeUnit.SECONDS);assertTrue(manager.all().isEmpty());loop.pump(1);assertEquals(1,manager.all().size());var active=manager.event(ModeEvents.Event.of("held"),null).toCompletableFuture();var stopped=manager.stop().toCompletableFuture();assertFalse(stopped.isDone());worker.submit(()->gate.complete(null)).get(4,TimeUnit.SECONDS);assertFalse(active.isDone());for(int i=0;i<8&&!stopped.isDone();i++)loop.pump(2+i);stopped.get(4,TimeUnit.SECONDS);assertEquals(List.of("held","unload"),events);
        }
    }
}
