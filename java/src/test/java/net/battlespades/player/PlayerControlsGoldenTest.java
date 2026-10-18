// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.support.LifeHarness;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PlayerControlsGoldenTest {
    static Stream<Map<?,?>> cases()throws Exception{try(var input=Objects.requireNonNull(PlayerControlsGoldenTest.class.getResourceAsStream("/golden/player-controls.json"))){return ((List<?>)((Map<?,?>)MetadataDocument.json(new String(input.readAllBytes(),StandardCharsets.UTF_8))).get("cases")).stream().map(value->(Map<?,?>)value);}}
    private static int n(Object value){return ((Number)value).intValue();}private static double d(Object value){return ((Number)value).doubleValue();}
    private static String hex(GamePacket packet){return HexFormat.of().formatHex(packet.encode());}
    private static List<Object> selection(ClassCatalog.Selection s){return Arrays.asList((long)s.classId(),s.loadout().stream().map(Integer::longValue).toList(),s.prefabs(),s.ugcTools().stream().map(Integer::longValue).toList());}
    @ParameterizedTest @MethodSource("cases") void intactSourceTeamClassAndPaletteTraces(Map<?,?> row)throws Exception{
        var overrides=new HashMap<String,Object>();((Map<?,?>)row.get("rules")).forEach((k,v)->overrides.put((String)k,v));var rules=GameRules.serverDefaults().apply(overrides);
        try(var h=new LifeHarness(rules);var loop=new OwnerLoop(100_000_000_000L,1024,1024,error->fail(error))){
            var wire=new ArrayList<List<Object>>();var direct=new ArrayList<String>();var log=new ArrayList<List<Object>>();var teams=new LinkedHashMap<Integer,TeamState>();teams.put(2,new TeamState(2,"Team",18,52,86));teams.put(3,new TeamState(3,"Team",50,60,70));boolean[] allow={true},locked={false},selectable={true};
            h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket packet){wire.add(Arrays.asList(hex(packet),null));}public void owner(int id,GamePacket packet){direct.add(hex(packet));}public boolean inGame(int id){return true;}};
            var changes=new TeamChanges(h.runtime,rules,new AdmissionPolicy.Settings((Boolean)row.get("auto"),2,List.of(),"[BOT]"),5,()->h.monotonic,()->h.wall,new TeamChanges.Effects(){
                public boolean allows(PlayerLife p,int team){return allow[0];}public boolean locked(int team){return locked[0];}public boolean assignsTeams(){return (Boolean)row.get("assign");}public void retireDeployables(PlayerLife p){log.add(List.of("retire",(long)p.id(),(long)p.team()));}public void removeTeam(PlayerLife p){var team=teams.get(p.team());if(team!=null)team.remove(p);}public void addTeam(PlayerLife p){var team=teams.get(p.team());if(team!=null)team.add(p);}public void changed(PlayerLife p,int oldTeam,int newTeam){log.add(List.of("changed",(long)p.id(),(long)oldTeam,(long)newTeam));}public void send(PlayerLife p,GamePacket packet){direct.add(hex(packet));}public void failure(String label,RuntimeException error){fail(label,error);}
            });
            var selections=new SelectionChanges(h.runtime,new SelectionChanges.Policy(){
                public ClassCatalog.Selection normalize(int id,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback){var s=ClassCatalog.normalize(id,tools,prefabs,ugc,fallback,rules.disabledTools());return (Boolean)row.get("ugc")&&s.classId()==13?new ClassCatalog.Selection(13,s.loadout().stream().filter(t->t!=23).toList(),prefabs.stream().filter("Construct"::equals).toList(),s.ugcTools()):s;}public boolean selectable(int id){return rules.classEnabled(id);}public boolean allows(PlayerLife p,ClassCatalog.Selection s){return selectable[0];}public boolean liveUgc(PlayerLife p,ClassCatalog.Selection s){return (Boolean)row.get("ugc")&&p.selection().classId()==13&&s.classId()==13;}public void send(PlayerLife p,GamePacket packet){direct.add(hex(packet));}public void broadcast(GamePacket packet){wire.add(Arrays.asList(hex(packet),null));}public void failure(String label,RuntimeException error){fail(label,error);}
            },()->h.monotonic);
            var palette=new PaletteRelay(h.runtime,()->rules.enabled("RULE_ENABLE_COLOUR_PICKER"),()->h.monotonic,loop,(p,packet)->wire.add(List.of(hex(packet),(long)p.id())));var controls=new PlayerControls(changes,selections,palette);h.runtime.controls(controls);
            for(int i=0;i<5;i++){h.runtime.admit(i,new SimplePackets.NewPlayerConnection(i==1?3:2,(Boolean)row.get("ugc")?13:0,0,0,"P"+i), (Boolean)row.get("ugc")?new ClassCatalog.Selection(13,ClassCatalog.normalize(13,List.of(),rules).loadout().stream().filter(t->t!=23).toList(),List.of(),List.of()):ClassCatalog.normalize(0,List.of(),rules),0);teams.get(i==1?3:2).add(h.runtime.player(i));}var p=h.runtime.player(0);int[] departedDeaths={-1};wire.clear();direct.clear();
            for(var item:(List<?>)row.get("actions")){var step=(Map<?,?>)item;var action=(List<?>)step.get("action");String op=(String)action.getFirst();Object result=null;
                switch(op){
                    case "team"->result=changes.change(p,n(action.get(1)),(Boolean)action.get(2),(Boolean)action.get(3));case "wireteam"->controls.packet(p,new SimplePackets.ChangeTeam(254,n(action.get(1))));case "join"->controls.packet(p,new SimplePackets.NewPlayerConnection(n(action.get(1)),0,0,0,"Ignored"));
                    case "class"->controls.packet(p,new SimplePackets.ChangeClass(254,n(action.get(1))));case "loadout"->{var tools=((List<?>)action.get(2)).stream().map(PlayerControlsGoldenTest::n).toList();var prefabs=((List<?>)action.get(3)).stream().map(String.class::cast).toList();controls.packet(p,new PlayerPackets.SetClassLoadout(254,n(action.get(1)),n(action.get(4)),tools,prefabs,List.of()));}
                    case "palette"->controls.packet(p,new SimplePackets.SetColor(254,n(action.get(1))));case "advance"->{h.monotonic+=d(action.get(1));h.wall+=d(action.get(1));loop.pump(Math.round(h.monotonic*1e9));loop.pump(Math.round(h.monotonic*1e9));}
                    case "spawn"->{p.applyPending();p.spawn(new Vector(36,36,97),h.monotonic,1,teams.get(p.team()).color());p.clearDeathTime();}case "die"->h.runtime.die(p.id(),null,0);case "hit"->p.combatStamp(h.runtime.player(1),0,h.monotonic);case "allow"->{allow[0]=(Boolean)action.get(1);locked[0]=(Boolean)action.get(2);}case "selection_allow"->selectable[0]=(Boolean)action.get(1);case "depart"->{departedDeaths[0]=h.runtime.score(0).deaths();h.runtime.transitionDetach(p.id());}default->fail(op);
                }
                assertEquals(step.get("result"),result,op);assertEquals(n(step.get("team")),p.team(),op);assertEquals(step.get("alive"),p.alive(),op);assertEquals(step.get("spawned"),p.spawned(),op);assertEquals(d(step.get("death_time")),p.deathTime(),1e-10,op);assertEquals(n(step.get("color")),p.color(),op);assertEquals(step.get("selection"),selection(p.selection()),op);assertEquals(step.get("pending"),p.pendingSelection().map(PlayerControlsGoldenTest::selection).orElse(null),op);assertEquals(step.get("kills"),List.of((long)h.runtime.score(1).kills(),(long)(departedDeaths[0]>=0?departedDeaths[0]:h.runtime.score(0).deaths())),op);assertEquals(step.get("log"),log,op);assertEquals(step.get("wire"),wire,op);assertEquals(step.get("direct"),direct,op);var roster=new LinkedHashMap<String,List<Long>>();teams.forEach((id,t)->roster.put(id.toString(),t.players().stream().map(v->(long)v.id()).toList()));assertEquals(step.get("teams"),roster,op);
            }
        }
    }
}
