// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.support.ArenaHarness;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ArenaRoundsGoldenTest {
    static Map<?,?> fixture()throws Exception{try(var in=Objects.requireNonNull(ArenaRoundsGoldenTest.class.getResourceAsStream("/golden/arena-rounds.json"))){return (Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    static Stream<Map<?,?>> rounds()throws Exception{return ((List<?>)fixture().get("arena")).stream().map(v->(Map<?,?>)v);}static Stream<Map<?,?>> audio()throws Exception{return ((List<?>)fixture().get("audio")).stream().map(v->(Map<?,?>)v);}static int n(Object v){return ((Number)v).intValue();}static double d(Object v){return ((Number)v).doubleValue();}
    static ArenaMode.Settings settings(Map<?,?> input){var lines=new ArrayList<String>();lines.add("[modes.arena]");input.forEach((k,v)->lines.add(k+" = "+v));return ArenaMode.Settings.from(org.tomlj.Toml.parse(String.join("\n",lines)));}
    @ParameterizedTest @MethodSource("rounds") void intactArenaNativeLifeAndRoundLifecycleTraces(Map<?,?> row){
        try(var h=new ArenaHarness(settings((Map<?,?>)row.get("overlay")))){var identities=new IdentityHashMap<PlayerLife,Long>();var old=new HashMap<Integer,Scoreboard.Player>();long[] serial={0};java.util.function.BiFunction<Integer,Integer,PlayerLife> add=(id,team)->{int setupWire=h.wire.size();var p=h.admit(id,team);h.wire.subList(setupWire,h.wire.size()).clear();if(team!=0&&!p.alive())p.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,97),h.life.monotonic,1,team==2?0x123456:0x323c46);identities.put(p,serial[0]++);return p;};for(int i=0;i<4;i++)add.apply(i,new int[]{2,3,2,0}[i]);h.wire.clear();h.effects.clear();h.events.clear();
            for(var item:(List<?>)row.get("steps")){var step=(Map<?,?>)item;var a=(List<?>)step.get("action");String op=(String)a.getFirst();Object result=null;
                switch(op){
                    case "start"->h.mode.start();case "time"->{h.life.wall=d(a.get(1));h.life.monotonic=h.life.wall-900;}case "tick"->h.mode.tick(n(a.get(1))).toCompletableFuture().join();
                    case "death"->{var p=h.life.runtime.player(n(a.get(1)));var killer=a.get(2)==null?null:h.life.runtime.player(n(a.get(2)));h.life.runtime.die(p.id(),killer==null?null:killer.id(),n(a.get(3)));h.mode.death(h.binding.row(p),h.binding.row(killer),n(a.get(3)));if(killer!=null&&killer!=p&&killer.team()!=p.team())h.mode.kill(h.binding.row(killer),h.binding.row(p),n(a.get(3)));h.events.clear();}
                    case "dead"->{h.life.runtime.die(n(a.get(1)),null,0);h.events.clear();}case "leave","replace"->{var p=h.life.runtime.player(n(a.get(1)));var previous=h.binding.row(p);old.put(p.id(),previous);h.teams.stream().filter(t->t.id==p.team()).forEach(t->t.remove(p));h.life.runtime.transitionDetach(p.id());if(op.equals("leave"))h.mode.handle(ModeEvents.Event.of("on_player_leave",previous)).toCompletableFuture().join();else add.apply(p.id(),n(a.get(2)));}case "old_leave"->h.mode.handle(ModeEvents.Event.of("on_player_leave",old.get(n(a.get(1))))).toCompletableFuture().join();case "new"->add.apply(n(a.get(1)),n(a.get(2)));case "respawns"->h.life.runtime.processRespawns();case "fail_respawn"->h.life.failing.add(n(a.get(1)));case "stage"->h.life.runtime.player(n(a.get(1))).stage(ClassCatalog.normalize(n(a.get(2)),List.of(),net.battlespades.rules.GameRules.serverDefaults()));case "retire"->h.mode.beginRetirement((Boolean)a.get(1));case "deactivate"->h.mode.deactivate().toCompletableFuture().join();case "damage"->result=(long)h.mode.damage(d(a.get(1)));default->fail(op);
                }
                var s=h.mode.state();assertEquals(step.get("result"),result,op);assertEquals(n(step.get("round")),s.round(),op);var wins=new LinkedHashMap<String,Long>();s.wins().forEach((id,v)->wins.put(id.toString(),v.longValue()));assertEquals(step.get("wins"),wins,op);assertEquals(step.get("round_started"),s.started(),op);assertEquals(step.get("round_ended"),s.ended(),op);assertEquals(d(step.get("round_start")),s.start(),op);assertEquals(step.get("countdown"),s.countdown(),op);assertEquals(d(step.get("countdown_end")),s.countdownEnd(),op);assertEquals(step.get("next"),s.nextRound(),op);assertEquals(step.get("waiting"),s.waiting(),op);assertEquals(step.get("fighters"),s.fighters().stream().map(identities::get).toList(),op);assertEquals(step.get("started"),h.mode.started(),op);assertEquals(step.get("ended"),h.mode.clock().ended,op);assertEquals(step.get("retiring"),h.mode.clock().retiring,op);assertEquals(h.mode.winner()==null?null:h.mode.winner().longValue(),step.get("winner"),op);
                var players=h.life.runtime.players().stream().map(p->Arrays.asList((Object)(long)p.id(),identities.get(p),(long)p.team(),p.alive(),p.spawned(),h.binding.row(p).score,h.mode.canRespawn(p),h.mode.respawnTime(h.life.delay),h.mode.botRetireSafe(p),(long)p.selection().classId())).toList();assertEquals(step.get("players"),players,op);assertEquals(step.get("teams"),h.teams.stream().map(t->t.score).toList(),op);assertEquals(step.get("wire"),h.wire,op);assertEquals(step.get("effects"),h.effects,op);assertEquals(step.get("respawned"),h.respawned.stream().map(Integer::longValue).toList(),op);
            }
        }
    }
    @ParameterizedTest @MethodSource("audio") void intactServerJoinAudioAndModeTrackUseTheSameSeededStream(Map<?,?> row){
        var wire=new ArrayList<String>();var music=new MusicService(p->fail("Join choice must not broadcast or mutate broadcast family"),new SourceRandom(n(row.get("seed"))));var flags=new HashMap<String,Boolean>();boolean[] bed={true};int[] count={0};Integer[] failAt={null};Object actor=new Object();var audio=new JoinAudio<>(music,()->bed[0],()->music.joinTrack(flags.getOrDefault("started",false),flags.getOrDefault("ended",false),flags.getOrDefault("_end_sequence_running",false),flags.getOrDefault("retiring",false),flags.getOrDefault("_timeout_music_played",false)),p->0,(p,packet)->{count[0]++;wire.add(HexFormat.of().formatHex(packet.encode()));if(Objects.equals(count[0],failAt[0]))throw new IllegalStateException("controlled send");},(label,error)->{});
        for(var item:(List<?>)row.get("steps")){var step=(Map<?,?>)item;var a=(List<?>)step.get("action");switch((String)a.getFirst()){case "reveal"->audio.reveal(actor,List.of(new MapMetadata.Ambient("amb_rural",List.of(),1,1)),new Vector3f(36,36,97));case "forget"->{audio.forget(0);count[0]=0;}case "flag"->flags.put((String)a.get(1),(Boolean)a.get(2));case "bed"->bed[0]=(Boolean)a.get(1);case "fail"->{failAt[0]=a.get(1)==null?null:n(a.get(1));count[0]=0;}default->fail();}assertEquals(step.get("sent"),audio.sent(actor));assertEquals(step.get("wire"),wire);assertNull(music.family());}
    }
    @Test void originalArenaOverlayPrecedenceAndClamps()throws Exception{for(var item:(List<?>)fixture().get("settings")){var r=(Map<?,?>)item;var s=settings((Map<?,?>)r.get("input"));assertEquals(r.get("values"),List.of((long)s.roundsToWin(),s.roundTimeLimit(),s.timeLimit()));}}
}
