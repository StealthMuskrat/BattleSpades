// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.world.MetadataDocument;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotFoundationsTest.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BotCombatTest {
    private static final Map<String,Object> SOURCE=read();
    private static Map<String,Object> read() {
        try(var stream=Objects.requireNonNull(BotCombatTest.class.getResourceAsStream("/golden/bot-combat.json"))) {
            var root=map(MetadataDocument.json(new String(stream.readAllBytes(),StandardCharsets.UTF_8)));
            var definitions=list(root.get("definitions"));var copy=new LinkedHashMap<>(root);copy.remove("definitions");return map(expand(copy,definitions,new HashMap<>()));
        } catch(IOException error) {throw new UncheckedIOException(error);}
    }
    private static Object expand(Object value,List<?> definitions,Map<Integer,Object> cached) {
        if(value instanceof Map<?,?> m) {
            if(m.size()==1&&m.containsKey("$ref")) {int key=integer(m.get("$ref"));return cached.computeIfAbsent(key,k->expand(definitions.get(k),definitions,new HashMap<>()));}
            var result=new LinkedHashMap<String,Object>();m.forEach((k,v)->result.put(k.toString(),expand(v,definitions,cached)));return Collections.unmodifiableMap(result);
        }
        if(value instanceof List<?> list)return list.stream().map(v->expand(v,definitions,cached)).toList();return value;
    }
    private static Player player(Object value) {return BotPlayerTestData.player(value);}
    private static Perception frame(Object value){return BotPerceptionTestData.frame(value);}
    private static Intent intent(Object value){return BotPerceptionTestData.intent(value);}
    private static BotModeDecision decision(Object value){return BotPerceptionTestData.decision(value);}
    private static Object observation(Object value) {
        if(value instanceof Priority p)return p.value();
        if(value instanceof CombatFootwork.Identity i)return List.of(i.playerId(),i.generation(),i.lifeId());
        if(value instanceof Diagnostic d)return Arrays.asList(d.key(),observation(d.value()));
        if(value instanceof Vector v)return List.of(v.x(),v.y(),v.z());
        if(value instanceof Enum<?> e)return e.name().toLowerCase(Locale.ROOT);
        if(value instanceof Record r) {var out=new LinkedHashMap<String,Object>();try {for(var c:r.getClass().getRecordComponents())out.put(c.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),observation(c.getAccessor().invoke(r)));}catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}return out;}
        if(value instanceof Collection<?> c)return c.stream().map(BotCombatTest::observation).toList();return value;
    }
    private static void matches(Object expected,Object actual) {equal(expected,observation(actual));}
    private static BotNavigationWorld world()throws Exception {var w=new BotNavigationWorld();w.load(new MapSnapshot(7,0,BotNavigationTest.raw(SOURCE.get("raw_zlib")),"tdm","",List.of(),"retail"));return w;}
    private static void scene(BotNavigationWorld world,Object value) {var s=map(value);world.apply(new WorldDelta(7,number(s.get("version")),list(s.get("cells")).stream().map(v->{var c=map(v);return new VoxelChange(integer(c.get("x")),integer(c.get("y")),integer(c.get("z")),bool(c.get("solid")),integer(c.get("color")),real(c.get("health")));}).toList()));}
    @Test void weaponDoctrineAndFixedIntegerVariationMatchOriginalCatalog() {
        for(var item:list(SOURCE.get("envelopes"))) {var r=map(item);matches(r.get("envelope"),BotCombatProfiles.envelope(integer(r.get("tool"))));matches(r.get("recoil"),BotCombatProfiles.recoil(integer(r.get("tool"))));}
        for(var item:list(SOURCE.get("mix"))) {var r=map(item);matches(r.get("result"),BotCombatTactics.mix(list(r.get("values")).stream().mapToLong(BotFoundationsTest::number).toArray()));}
    }
    @Test void committedFootworkRetainsSpacingPressureReloadAndLifeTransitions() {
        for(var item:list(SOURCE.get("footwork"))) {var trace=map(item);var p=profile(trace.get("profile"));var footwork=new CombatFootwork();int index=0;
            for(var step:list(trace.get("steps"))) {var s=map(step);try {matches(s.get("direction"),footwork.direction(player(s.get("observer")),player(s.get("target")),p,real(s.get("now")),integer(s.get("blocked"))));matches(s.get("spacing"),footwork.spacing(real(s.get("spacing_distance")),10,40));matches(s.get("state"),footwork.snapshot());}catch(AssertionError error) {throw new AssertionError(p.difficulty()+" footwork "+index,error);}index++;}
        }
    }
    @Test void combatSightCoverRelocationAndHazardDecisionsMatchRealVoxelSourceQueries()throws Exception {
        var w=world();for(var item:list(SOURCE.get("tactical"))) {var trace=map(item);scene(w,trace.get("scene"));var e=new BotCombatTactics.Engagement();int index=0;
            for(var query:list(trace.get("queries"))) {var q=map(query);var p=player(q.get("observer"));var target=player(q.get("target"));var f=frame(q.get("frame"));var expected=map(q.get("result"));double now=f.createdAt();e.retarget(target);
                try {matches(expected.get("aim"),BotCombatTactics.aimOffset(w,p,target,f.profile(),e,now));matches(expected.get("blocked"),BotCombatTactics.fireBlocked(e,p,bool(q.get("wants_fire")),now,.5));var cover=BotCombatTactics.findCover(w,p,target.eye(),e,now);matches(expected.get("cover"),Arrays.asList(cover.position(),cover.duck()));matches(expected.get("relocation"),BotCombatTactics.stationaryRelocation(w,p,target,f.profile(),e,now));matches(expected.get("hazard"),BotCombatTactics.hazardEscape(w,f,p,f.profile(),e,now));var line=BotCombatTactics.firingLine(p.position(),target.eye(),3);matches(expected.get("line"),line);matches(expected.get("shelter"),BotCombatTactics.findShelter(w,p,line,10.5,0,real(q.get("side")),18));matches(expected.get("dead_ground"),BotCombatTactics.findDeadGround(w,p,line,real(q.get("side")),14));matches(expected.get("walkable"),BotCombatTactics.walkableHeading(w,p,new Vector(0,1,0),3));matches(q.get("state"),e.snapshot());}catch(AssertionError error) {throw new AssertionError(map(trace.get("scene")).get("name")+" tactics "+index,error);}index++;
            }
        }
    }
    @Test void completeAwarenessTracesRetainStimulusGatesLifeMemoryAndExactIntentFences()throws Exception {
        var w=world();for(var item:list(SOURCE.get("awareness"))) {var trace=map(item);scene(w,trace.get("scene"));var awareness=new BotAwareness(w);int index=0;
            for(var event:list(trace.get("events"))) {var e=map(event);try {switch(string(e.get("op"))) {
                case "reset"->awareness.reset();case "forget"->awareness.forget(integer(e.get("id")),integer(e.get("generation")));
                case "threw"->awareness.threw(player(e.get("observer")),integer(e.get("tool")),vector(e.get("at")),real(e.get("now")));
                case "react"->{var f=frame(e.get("frame"));var p=player(e.get("observer"));var visible=e.get("visible")==null?null:player(e.get("visible"));matches(e.get("reaction"),awareness.react(f,p,f.profile(),visible,decision(e.get("decision")),f.createdAt()));matches(e.get("overlay"),awareness.overlay(f,intent(e.get("intent"))));}
                default->fail("Unknown awareness command");}assertEquals(integer(e.get("lives")),awareness.lifeCount());assertTrue(awareness.lifeCount()<=128);}catch(AssertionError error) {throw new AssertionError(trace.get("name")+" awareness "+index,error);}index++;
            }
        }
    }
    @Test void mutableCombatMemoryRejectsCallsOutsideItsWorker()throws Exception {
        var awareness=new BotAwareness(new BotNavigationWorld());var footwork=new CombatFootwork();var engagement=new BotCombatTactics.Engagement();var caught=new java.util.concurrent.atomic.AtomicInteger();
        var thread=new Thread(()->{for(Runnable call:List.<Runnable>of(awareness::reset,()->footwork.spacing(20,10,40),engagement::snapshot))try {call.run();}catch(IllegalStateException expected) {caught.incrementAndGet();}});thread.start();thread.join();assertEquals(3,caught.get());
    }
}
