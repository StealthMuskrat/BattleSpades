// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.battlespades.config.ServerConfig;
import net.battlespades.app.Main;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.ClassCatalog;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.*;
import net.battlespades.support.FixtureScene;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import static org.junit.jupiter.api.Assertions.*;

class ScenePolicyTest {
    @TempDir Path temp;
    private static List<String[]> rows(String name) throws IOException {
        try(var in=Objects.requireNonNull(ScenePolicyTest.class.getResourceAsStream("/golden/"+name+".tsv"))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t")).toList();}
    }
    private static Object value(String text){if(text.equals("null"))return null;var t=Toml.parse("value="+text);assertFalse(t.hasErrors(),text);return t.get("value");}
    private static List<Integer> ints(String text){var t=Toml.parse("value="+text);return t.getArray("value").toList().stream().map(n->((Number)n).intValue()).toList();}
    private static List<String> strings(String text){var t=Toml.parse("value="+text);return t.getArray("value").toList().stream().map(String.class::cast).toList();}
    private static String typed(Object v){return v instanceof Boolean b?"bool:"+(b?1:0):v instanceof Long?"int:"+v:"float:"+Double.toHexString(((Number)v).doubleValue());}
    @TestFactory Stream<DynamicTest> everyRuleCoercionMatchesSource() throws Exception {
        var groups=new LinkedHashMap<String,List<String[]>>();for(var r:GoldenRows.distinct(rows("rules")))groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            for(var r:e.getValue()){
                if(r[2].equals("error"))assertThrows(IllegalArgumentException.class,()->GameRules.serverDefaults().apply(Map.of(r[0],value(r[1]))),Arrays.toString(r));
                else{
                    var rules=GameRules.serverDefaults().apply(Map.of(r[0],value(r[1])));Object actual=rules.get(r[0]);
                    if(r[2].startsWith("float:")){assertInstanceOf(Double.class,actual);assertEquals(Double.valueOf(r[2].substring(6)),actual,Arrays.toString(r));}
                    else assertEquals(r[2],typed(actual),Arrays.toString(r));
                    assertTrue(rules.explicit(r[0]));
                }
            }
        }));
    }
    @TestFactory Stream<DynamicTest> classSelectionMatchesSource() throws Exception {
        var groups=new LinkedHashMap<String,List<String[]>>();for(var r:rows("selections"))groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("class "+e.getKey(),()->{
            for(var r:e.getValue()){
                int fallback=Integer.parseInt(r[5]);var actual=ClassCatalog.normalize(Integer.parseInt(r[0]),ints(r[1]),strings(r[2]),ints(r[3]),fallback==-1?null:fallback,ints(r[4]));
                assertEquals(Integer.parseInt(r[6]),actual.classId());assertEquals(ints(r[7]),actual.loadout(),Arrays.toString(r));assertEquals(strings(r[8]),actual.prefabs());assertEquals(ints(r[9]),actual.ugcTools());
            }
        }));
    }
    private static GameRules rules(int variant){
        var rules=GameRules.serverDefaults();if(variant>0)rules=rules.apply(Map.of("RULE_CHARACTER_SPEED",1.5,"RULE_CLASS_SPEED",2.0,"RULE_ENABLE_CLASS_COMMANDO",false,"RULE_ENABLE_WEAPON_MINIGUN",false,"RULE_ENABLE_GRAVESTONES",false,"RULE_ENABLE_FALL_ON_WATER_DAMAGE",false,"RULE_ENABLE_FLARE_BLOCKS",false));return rules;
    }
    static SceneConfig config(String mode,int variant){return new SceneConfig("Configured oracle",27015,90087911866072064L,ModeCatalog.get(mode),"ArcticBase",variant>0,variant>0,true,10,0x0c0d0b,SceneConfig.DEFAULT_SKYBOX,new SceneConfig.Team("Blue",0x0040ff),new SceneConfig.Team("Green",0xff2000),rules(variant));}
    static ScenePackets.Environment environment(int variant,int crc){
        return variant!=2?ScenePackets.Environment.defaults("ArcticBase",crc):new ScenePackets.Environment("ArcticBase",crc,26.0/64,0x454c27,0xecf4cb,new StateData.Vector(-0.7,0.3,0),0x0f140a,new StateData.Vector(0,0.7,0.3),0x0f1e0a,0.3,List.of(new InitialInfo.Rgba(18,52,86,255)),"ArcticBase.txt");
    }
    @TestFactory Stream<DynamicTest> originalCommonSceneBuilderBytes() throws Exception {
        return rows("scene-builders").stream().map(r->DynamicTest.dynamicTest(r[0]+"/"+r[1],()->{
            int variant=Integer.parseInt(r[1]);var config=config(r[0],variant);var env=environment(variant,0xdeadbeef);
            var identity=new ScenePackets.ServerIdentity(variant==2?123456789:0,variant==2?0xd5986593L:0,false,27015);
            assertArrayEquals(HexFormat.of().parseHex(r[2]),ScenePackets.initial(config,env,identity).encode(),"InitialInfo");
            var state=new ScenePackets.State(new ScenePackets.Team("Blue",0x0040ff,7),new ScenePackets.Team("Green",0xff2000,-3),variant==2?200:null,variant==2?0x010203:null);
            assertArrayEquals(HexFormat.of().parseHex(r[3]),ScenePackets.state(config,env,state,7).encode(),"StateData");
        }));
    }
    @Test void wireRoundedRulesDriveActualNativeEquivalentPhysics() throws Exception {
        for(var row:rows("rule-movement")){
            int id=Integer.parseInt(row[0]);String mode=row[1];var rules=GameRules.serverDefaults().apply(Map.of("RULE_CHARACTER_SPEED",Double.valueOf(row[2]),"RULE_CLASS_SPEED",2.0,"RULE_ENABLE_FALL_ON_WATER_DAMAGE",row[3].equals("1")));
            assertEquals(Double.valueOf(row[5]),ClassCatalog.speedScale(id,mode,rules));
            var p=new PlayerPhysics((x,y,z)->z==239);p.profile(ClassCatalog.movement(id,mode,rules));p.position(new Vector(100.5,100.5,100));p.input(new PlayerPhysics.Input(true,false,false,false,false,row[4].equals("sneak"),row[4].equals("sprint")));
            for(int i=0;i<12;i++)p.update(1.0/60,1,List.of());var s=p.state();double[] actual={s.position().x(),s.position().y(),s.position().z(),s.velocity().x(),s.velocity().y(),s.velocity().z()};
            for(int i=0;i<6;i++)assertEquals(Double.doubleToLongBits(Double.valueOf(row[6+i])),Double.doubleToLongBits(actual[i]),Arrays.toString(row)+" field "+i);
            assertEquals(row[3].equals("1")?MovementProfile.forClass(id).waterDamage():0,ClassCatalog.movement(id,mode,rules).waterDamage());
        }
    }
    @Test void originalSkyboxAssetValidation() throws Exception {
        for(var row:rows("skyboxes"))assertEquals(value(row[1]),SceneConfig.validSkybox((String)value(row[0])),Arrays.toString(row));
        var c=config("tdm",0);var e=environment(0,1);assertEquals("User_Grassland.txt",ScenePackets.skybox(c,e));
        assertEquals(26.0/64,ScenePackets.canonicalGravity(0.4));assertEquals(1,ScenePackets.canonicalGravity(Double.NaN));assertEquals(1,ScenePackets.canonicalGravity(9));
    }
    private ServerConfig core(String text) throws IOException {Path file=temp.resolve("scene.toml");Files.writeString(file,text);return ServerConfig.load(file);}
    @Test void configCompatibilityAliasesAndExplicitRulePrecedence() throws Exception {
        var c=SceneConfig.from(core("[server]\nname='Configured'\n[game]\nrespawn_time=7\nfall_damage=false\n[game_rules]\ncharacter_speed='150%'\nRULE_RESPAWN_TIMES=10\n[teams]\nteam1_color=[0,64,255]\n[world]\ndefault_skybox='ArcticBase.txt'"));
        assertEquals(1.5,c.rules().number("RULE_CHARACTER_SPEED"));assertEquals(10,c.rules().number("RULE_RESPAWN_TIMES"));assertFalse(c.rules().enabled("RULE_ENABLE_FALL_ON_WATER_DAMAGE"));assertEquals(0x0040ff,c.team1().color());assertEquals("ArcticBase.txt",c.skybox());
        assertEquals(7,SceneConfig.from(core("[game]\nrespawn_time=7")).rules().number("RULE_RESPAWN_TIMES"));
        assertEquals("zom",ModeCatalog.get(" zombie ").code());assertEquals("hc_ctf",ModeCatalog.get("hardcore_ctf").code());assertEquals("nor",ModeCatalog.get("no-such-mode").code());
        assertEquals("arena",SceneConfig.from(core("[game]\ndefault_mode='arena'")).modeCode());assertEquals("nor",ModeCatalog.get("arena").code());
        assertEquals("zom",ModeCatalog.resolve(" zombie "));assertThrows(IllegalArgumentException.class,()->ModeCatalog.resolve("typo"));
    }
    @Test void configDiagnosticsRejectUnknownRulesAndUnavailableHardcorePolicy() throws Exception {
        for(String text:List.of("[game_rules]\nRULE_UNKNOWN=true","[game_rules]\nRULE_CHARACTER_SPEED=true","[world]\nfog_color_rgb=[1,2,256]","[game]\ndefault_mode='hc_tdm'","[game]\ndefault_mode='typo'")){
            var file=core(text).root().resolve("scene.toml");assertEquals(2,Main.run(new String[]{"--check-config",file.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(OutputStream.nullOutputStream())));
        }
    }
    @Test void immutableRulesKeepDisabledMenusSelectionAndToolAuthorityConsistent(){
        var base=GameRules.serverDefaults();assertEquals(102,GameRules.definitions().size());assertEquals(18,ClassCatalog.definitions().size());assertEquals(43,ModeCatalog.definitions().size());
        assertTrue(base.toolEnabled(-1));assertTrue(base.classEnabled(-1));assertTrue(base.toolEnabled(999));
        var rules=base.apply(Map.of("enable_weapon_minigun",false,"enable_class_commando",false));
        assertTrue(base.toolEnabled(8));assertFalse(rules.toolEnabled(8));assertEquals(List.of(0),rules.disabledClasses());
        var selection=ClassCatalog.normalize(0,List.of(8,60,51,68),rules);assertTrue(selection.loadout().contains(60));assertFalse(selection.loadout().contains(8));assertFalse(selection.loadout().contains(51));
        assertTrue(ClassCatalog.inventoryToolAuthorized(true,true,60,selection,rules));assertFalse(ClassCatalog.inventoryToolAuthorized(true,true,51,selection,rules));assertFalse(ClassCatalog.inventoryToolAuthorized(false,true,60,selection,rules));
        assertFalse(ClassCatalog.normalize(12,List.of(64),rules).loadout().contains(68));
        assertThrows(UnsupportedOperationException.class,()->selection.loadout().add(51));
    }
    @Test void configuredSessionBoundaryUsesActualMetadataAndAtomicStockSelection(){
        try(var domain=new FixtureScene(1,64)){
            var config=config("tdm",1);var env=environment(0,domain.world().sourceCrc());
            var scores=new java.util.concurrent.atomic.AtomicReference<>(ScenePackets.State.initial(config));
            var scene=new ConfiguredScene(config,env,scores::get,domain);
            assertEquals("TDM_TITLE",scene.initialInfo(2).mode().name());assertEquals(domain.world().sourceCrc(),scene.initialInfo(2).map().checksum());
            assertFalse(scene.stateData(7,2).team1().classes().contains(0));assertEquals(7,scene.stateData(7,2).playerId());assertEquals(1.05f,scene.movement(12).acceleration());
            var admission=scene.admit(7,new SimplePackets.NewPlayerConnection(2,0,0,1,"name"),new PlayerPackets.SetClassLoadout(255,12,1,List.of(64,51),List.of("invalid"),List.of(255)),2);
            assertEquals(7,admission.player().playerId());assertEquals(12,admission.player().classId());assertTrue(admission.player().loadout().contains(64));assertFalse(admission.player().loadout().contains(51));assertFalse(admission.player().loadout().contains(68));assertTrue(admission.player().prefabs().isEmpty());
            scores.set(new ScenePackets.State(new ScenePackets.Team("Blue",1,33),new ScenePackets.Team("Green",2,4),200,0));
            assertEquals(33,scene.stateData(7,2).team1().score());assertEquals(0,scene.stateData(7,2).fogColor());
            scene.input(7,new LiveClientInput(12,7,false,7,1,0,0,0,0,0,0),15);assertEquals(List.of(15),domain.receivedLoops);
            scene.disconnected(7);assertTrue(domain.players.isEmpty());
            assertThrows(IllegalArgumentException.class,()->new ConfiguredScene(config,environment(0,123),scores::get,domain));
        }
    }
    @Test void modeAmendmentsRunBeforeFinalRuleInvariants(){
        try(var domain=new FixtureScene(1,64)){
            var config=config("tdm",1);var gameplay=new ConfiguredScene.Gameplay(){
                @Override public net.battlespades.world.MutableWorld world(){return domain.world();}
                @Override public net.battlespades.session.PlayerRoster roster(){return domain.roster();}
                @Override public net.battlespades.player.AdmissionPolicy.Context admissionContext(int id){return domain.admissionContext(id);}
                @Override public net.battlespades.session.JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){return domain.admit(id,request,selection,capability);}
                @Override public InitialInfo configureInitialInfo(InitialInfo p,int capability){return FixtureScene.copy(p,Map.of("customRules",List.of(new InitialInfo.Rule("mode-rule",String.valueOf(capability)),new InitialInfo.Rule("RULE_ENABLE_GRAVESTONES","1"))));}
                @Override public StateData configureStateData(StateData p,int capability){return FixtureScene.copy(p,Map.of("team1",FixtureScene.copy(p.team1(),Map.of("classes",List.of(0,12),"locked",true))));}
            };
            var scene=new ConfiguredScene(config,environment(0,domain.world().sourceCrc()),()->ScenePackets.State.initial(config),gameplay);
            assertEquals(List.of(new InitialInfo.Rule("mode-rule","2"),new InitialInfo.Rule("RULE_ENABLE_GRAVESTONES","0")),scene.initialInfo(2).customRules());
            assertEquals(List.of(12),scene.stateData(1,2).team1().classes());assertTrue(scene.stateData(1,2).team1().locked());
        }
    }
    @Test void invalidCachedClassUsesItsOwnFallbackBeforeNewPlayerRequest(){
        try(var domain=new FixtureScene(1,64)){
            var config=config("nor",0);var scene=new ConfiguredScene(config,environment(0,domain.world().sourceCrc()),()->ScenePackets.State.initial(config),domain);
            var admission=scene.admit(1,new SimplePackets.NewPlayerConnection(0,0,3,1,"Kiko"),new PlayerPackets.SetClassLoadout(255,255,1,List.of(8,51),List.of(),List.of()),2);
            assertEquals(0,admission.player().team());assertEquals(5,admission.player().classId());assertFalse(admission.player().loadout().contains(8));assertFalse(admission.player().loadout().contains(51));
        }
    }
}
