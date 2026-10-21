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
    private static Vector nullableVector(Object value) {return value==null?null:vector(value);}
    private static Priority priority(Object value) {return Arrays.stream(Priority.values()).filter(p->p.value()==integer(value)).findFirst().orElseThrow();}
    private static Affordance affordance(Object value) {return Affordance.valueOf(string(value).toUpperCase(Locale.ROOT));}
    private static List<Stock> stocks(Object value) {return list(value).stream().map(v->{var a=list(v);return new Stock(integer(a.get(0)),integer(a.get(1)));}).toList();}
    private static Player player(Object value) {return BotPlayerTestData.player(value);}
    private static Entity entity(Object value) {var e=map(value);return new Entity(integer(e.get("entity_id")),integer(e.get("entity_type")),integer(e.get("team")),integer(e.get("owner_id")),vector(e.get("position")),bool(e.get("alive")),string(e.get("kind")),integer(e.get("tool_id")),vector(e.get("velocity")),real(e.get("blast_radius")),real(e.get("detonate_at")),bool(e.get("hazardous")),integer(e.get("uses_remaining")),nullableVector(e.get("hit_position")),real(e.get("hit_radius")));}
    private static Objective objective(Object value) {var o=map(value);return new Objective(string(o.get("kind")),integer(o.get("team")),vector(o.get("position")),integer(o.get("carrier_id")),integer(o.get("state")),list(o.get("bounds")).stream().map(BotFoundationsTest::integer).toList(),list(o.get("cells")).stream().map(BotFoundationsTest::cell).toList(),list(o.get("repair_cells")).stream().map(BotFoundationsTest::cell).toList(),real(o.get("progress")),integer(o.get("attacker")),real(o.get("expires_in")));}
    private static Stimulus stimulus(Object value) {var s=map(value);return new Stimulus(StimulusKind.valueOf(string(s.get("kind")).toUpperCase(Locale.ROOT)),vector(s.get("position")),real(s.get("created_at")),real(s.get("expires_at")),integer(s.get("source_id")),integer(s.get("team")),real(s.get("uncertainty")));}
    private static Perception frame(Object value) {var f=map(value);return new Perception(number(f.get("frame_id")),number(f.get("map_epoch")),number(f.get("mode_epoch")),number(f.get("topology_version")),integer(f.get("observer_id")),integer(f.get("observer_generation")),real(f.get("created_at")),string(f.get("mode_id")),list(f.get("players")).stream().map(BotCombatTest::player).toList(),profile(f.get("profile")),list(f.get("entities")).stream().map(BotCombatTest::entity).toList(),list(f.get("objectives")).stream().map(BotCombatTest::objective).toList(),list(f.get("stimuli")).stream().map(BotCombatTest::stimulus).toList(),string(f.get("mode_phase")),string(f.get("behavior_version")),bool(f.get("friendly_mischief")),bool(f.get("local_safety_complete")),Map.of());}
    private static Movement movement(Object value) {var m=map(value);return new Movement(vector(m.get("direction")),bool(m.get("jump")),bool(m.get("crouch")),bool(m.get("sneak")),bool(m.get("sprint")),affordance(m.get("affordance")),bool(m.get("jetpack_thrust")),nullableVector(m.get("travel_source")),nullableVector(m.get("travel_waypoint")),nullableVector(m.get("gaze_waypoint")),integer(m.get("walk_drop")));}
    private static Look look(Object value) {if(value==null)return null;var l=map(value);return new Look(vector(l.get("target")),bool(l.get("visible")),integer(l.get("target_player_id")),integer(l.get("target_generation")),real(l.get("aim_offset_z")),bool(l.get("glance")));}
    private static Action action(Object value) {var a=map(value);return new Action(ActionKind.valueOf(string(a.get("kind")).toUpperCase(Locale.ROOT)),integer(a.get("tool_id")),nullableVector(a.get("position")),nullableVector(a.get("end_position")),string(a.get("argument")),integer(a.get("face")),real(a.get("yaw")),integer(a.get("burst")),real(a.get("burst_pause")),number(a.get("request_id")));}
    private static Intent intent(Object value) {var i=map(value);return new Intent(integer(i.get("bot_id")),integer(i.get("bot_generation")),number(i.get("frame_id")),number(i.get("map_epoch")),number(i.get("mode_epoch")),number(i.get("topology_version")),real(i.get("created_at")),real(i.get("expires_at")),movement(i.get("movement")),look(i.get("look")),integer(i.get("tool_id")),action(i.get("action")),priority(i.get("priority")),bool(i.get("secondary_fire")),bool(i.get("zoom")),nullableVector(i.get("debug_goal")),list(i.get("debug_path")).stream().map(BotFoundationsTest::vector).toList(),string(i.get("debug_role")),integer(i.get("life_id")),list(i.get("debug_navigation")).stream().map(v->{var a=list(v);return new Diagnostic(string(a.get(0)),a.get(1));}).toList());}
    private static BotModeDecision decision(Object value) {if(value==null)return null;var d=map(value);return new BotModeDecision(vector(d.get("position")),string(d.get("role")),bool(d.get("sprint")),real(d.get("arrival_radius")),string(d.get("directive")),BotModeDecision.Posture.valueOf(string(d.get("posture")).toUpperCase(Locale.ROOT)),real(d.get("objective_priority")),real(d.get("engagement_radius")),nullableVector(d.get("watch_position")));}
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
