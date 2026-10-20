// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.MetadataDocument;
import static net.battlespades.bot.BotState.*;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotFoundationsTest.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BotRoutePlannerTest {
    private static final Map<String,Object> SOURCE=read();
    private static Map<String,Object> read() {
        try(var stream=Objects.requireNonNull(BotRoutePlannerTest.class.getResourceAsStream("/golden/bot-navigation.json"))) {
            return map(MetadataDocument.json(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
        } catch(IOException e) {throw new UncheckedIOException(e);}
    }
    private static Object observation(Object value) {
        if(value instanceof Cell c)return List.of(c.x(),c.y(),c.z());
        if(value instanceof Edge e)return List.of(observation(e.source()),observation(e.target()));
        if(value instanceof Vector v)return List.of(v.x(),v.y(),v.z());
        if(value instanceof Enum<?> e)return e.name().toLowerCase(Locale.ROOT);
        if(value instanceof Record r) {
            var out=new LinkedHashMap<String,Object>();
            try {for(var c:r.getClass().getRecordComponents())out.put(c.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),observation(c.getAccessor().invoke(r)));}
            catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
            return out;
        }
        if(value instanceof Collection<?> c)return c.stream().map(BotRoutePlannerTest::observation).toList();
        if(value instanceof Map<?,?> m) {var out=new LinkedHashMap<String,Object>();m.forEach((k,v)->out.put(k.toString(),observation(v)));return out;}
        return value;
    }
    private static void matches(Object expected,Object actual) {equal(expected,observation(actual));}
    private static Set<Edge> edges(Object value) {
        var result=new HashSet<Edge>();for(var item:list(value)) {var e=list(item);result.add(new Edge(cell(e.get(0)),cell(e.get(1))));}return Set.copyOf(result);
    }
    private static Set<Affordance> abilities(Object value) {
        var result=EnumSet.noneOf(Affordance.class);for(var a:list(value))result.add(Affordance.valueOf(string(a).toUpperCase(Locale.ROOT)));return result;
    }
    private static VoxelChange change(Object item) {
        var c=map(item);return new VoxelChange(integer(c.get("x")),integer(c.get("y")),integer(c.get("z")),bool(c.get("solid")),integer(c.get("color")),real(c.get("health")));
    }
    @Test void ownedToolsRetainRetailFootprintsHealthEstimatesAndBestThroughputSelection() {
        for(var item:list(SOURCE.get("profiles"))) {
            var row=map(item);var expected=map(row.get("profile"));int id=integer(expected.get("tool_id"));
            var p=(bool(expected.get("secondary"))?BotDigProfile.navigation(id):BotDigProfile.primary(id)).orElseThrow();
            matches(expected,p);matches(row.get("footprint"),p.footprint(new Cell(10,11,12)));
            for(var entry:list(row.get("swings"))) {var s=list(entry);assertEquals(integer(s.get(1)),p.swingsForHealth(real(s.get(0))));assertEquals(integer(s.get(2)),p.swingsForHealth(real(s.get(0)),true));}
        }
        for(var item:list(SOURCE.get("selection"))) {var row=map(item);matches(row.get("profile"),BotDigProfile.best(list(row.get("tools")).stream().map(BotFoundationsTest::integer).toList()).orElse(null));}
    }
    @Test void activeSurfaceRoutesAndEveryGeneratedEdgeMatchOriginalAcrossConnectedTerrainScenes()throws Exception {
        var world=new BotNavigationWorld();world.load(new MapSnapshot(7,0,BotNavigationTest.raw(SOURCE.get("raw_zlib")),"tdm","",List.of(),"retail"));
        var planner=new BotRoutePlanner(world);
        for(var item:list(SOURCE.get("commands"))) {
            var c=map(item);
            if(c.get("op").equals("scene")) {
                var cells=list(c.get("cells")).stream().map(v->{var a=list(v);return new VoxelChange(integer(a.get(0)),integer(a.get(1)),integer(a.get(2)),bool(a.get(3)),integer(a.get(4)),real(a.get(5)));}).toList();
                world.apply(new WorldDelta(7,number(c.get("version")),cells));continue;
            }
            try {
                var start=vector(c.get("start"));var goal=vector(c.get("goal"));var a=abilities(c.get("abilities"));var p=c.get("tool")==null?null:BotDigProfile.navigation(integer(c.get("tool"))).orElseThrow();
                var result=world.plan(start,goal,a,p,bool(c.get("water")),edges(c.get("blocked")));
                matches(c.get("result"),result);assertTrue(result.expansions()<=256);
                var origin=world.surface((int)Math.floor(start.x()),(int)Math.floor(start.y()),start.z(),8,bool(c.get("water")));
                matches(c.get("neighbors"),origin==null?List.of():planner.neighbors(BotRoutePlanner.key(origin),a,p,bool(c.get("water"))));
                for(var step:result.steps())if(step.breach()!=null) {
                    var b=step.breach();assertTrue(world.solid(b.targetCell().x(),b.targetCell().y(),b.targetCell().z()));
                    assertFalse(p.footprint(b.targetCell()).contains(b.destination()));
                }
            } catch(AssertionError failure) {throw new AssertionError(c.get("name").toString(),failure);}
        }
    }
    private static List<Object> sortedEdges(Set<Edge> edges) {
        return edges.stream().sorted(Comparator.comparingInt((Edge e)->e.source().x()).thenComparingInt(e->e.source().y()).thenComparingInt(e->e.source().z()).thenComparingInt(e->e.target().x()).thenComparingInt(e->e.target().y()).thenComparingInt(e->e.target().z())).map(BotRoutePlannerTest::observation).toList();
    }
    @Test void liveWaterDeltasPreserveCrossingIntentAndValidateBanksBuildsBridgesAndExactBreachExclusions()throws Exception {
        var initial=map(SOURCE.get("water_world"));var world=new BotNavigationWorld();
        world.load(new MapSnapshot(8,0,BotNavigationTest.raw(initial.get("raw_zlib")),"tdm","",list(initial.get("initial")).stream().map(BotRoutePlannerTest::change).toList(),"retail"));
        for(var item:list(SOURCE.get("water"))) {
            var c=map(item);if(c.get("op").equals("delta")) {world.apply(new WorldDelta(8,number(c.get("version")),list(c.get("cells")).stream().map(BotRoutePlannerTest::change).toList()));continue;}
            try {
                var at=vector(c.get("at"));var goal=c.get("goal")==null?null:vector(c.get("goal"));var blocked=edges(c.get("blocked"));var direction=vector(c.get("direction"));var w=world.water();var expected=map(c.get("result"));
                matches(expected.get("step"),w.step(at,goal,blocked));matches(expected.get("assisted"),w.assistedStep(at,goal,blocked));matches(expected.get("shore_edges"),sortedEdges(w.shoreEdges(at,2)));matches(expected.get("roam"),w.roamStep(at,direction,blocked));matches(expected.get("build"),w.jumpBuildCell(at));
                for(int tool:new int[]{0,2,3,24,45,50})matches(map(expected.get("breaches")).get(Integer.toString(tool)),w.bankBreach(at,BotDigProfile.navigation(tool).orElseThrow(),goal,blocked));
                for(var entry:Map.of("bridge",Optional.ofNullable(w.bridgeLine(at,direction,6,null)),"landing_bridge",Optional.ofNullable(w.bridgeLine(at,direction,6,12)),"shoulder",Optional.ofNullable(w.shoulderLine(at,direction,6))).entrySet()) {
                    var line=entry.getValue().orElse(null);matches(expected.get(entry.getKey()),line==null?null:List.of(line.first(),line.last()));
                }
            } catch(AssertionError failure) {throw new AssertionError(c.get("name").toString(),failure);}
        }
    }
    @Test void sharedSearchCreditDefersWithoutDeclaringImpassableAndWorldRemainsWorkerOwned()throws Exception {
        var budget=new PlanningBudget(1,8);var world=new BotNavigationWorld(budget,()->0);
        world.load(new MapSnapshot(7,0,BotNavigationTest.raw(SOURCE.get("raw_zlib")),"tdm","",List.of(),"retail"));
        var cells=new ArrayList<VoxelChange>();for(int x=10;x<25;x++)cells.add(new VoxelChange(x,10,20,true,0,5));world.apply(new WorldDelta(7,1,cells));
        var start=new Vector(10.5,10.5,17.75);var goal=new Vector(20.5,10.5,17.75);
        assertTrue(world.plan(start,goal,Set.of(Affordance.JUMP),null,false,Set.of()).deferred());
        world.beginPlanning(new Observer(1,1),0);var admitted=world.plan(start,goal,Set.of(Affordance.JUMP),null,false,Set.of());assertTrue(admitted.reachedSegmentGoal());world.endPlanning(false);
        world.beginPlanning(new Observer(2,1),0);var waiting=world.plan(start,goal,Set.of(Affordance.JUMP),null,false,Set.of());assertTrue(waiting.deferred());assertEquals(0,waiting.expansions());assertTrue(waiting.steps().isEmpty());world.endPlanning(false);
        world.beginPlanning(new Observer(2,1),1);assertFalse(world.plan(start,goal,Set.of(Affordance.JUMP),null,false,Set.of()).deferred());world.endPlanning(false);
        var errors=new java.util.concurrent.atomic.AtomicInteger();var thread=new Thread(()->{try {world.plan(start,goal,Set.of(),null,false,Set.of());}catch(IllegalStateException expected) {errors.incrementAndGet();}try {world.water().jumpBuildCell(start);}catch(IllegalStateException expected) {errors.incrementAndGet();}});thread.start();thread.join();assertEquals(2,errors.get());
    }
}
