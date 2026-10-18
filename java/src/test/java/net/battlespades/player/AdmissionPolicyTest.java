// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.battlespades.protocol.SimplePackets;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ModeCatalog;
import org.junit.jupiter.api.*;
import org.tomlj.Toml;
import static org.junit.jupiter.api.Assertions.*;

class AdmissionPolicyTest {
    private static List<String[]> rows(String name)throws IOException{
        try(var in=new GZIPInputStream(Objects.requireNonNull(AdmissionPolicyTest.class.getResourceAsStream("/golden/"+name+".tsv.gz")))){
            return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();
        }
    }
    private static String decode(String value){return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);}
    private static List<String> strings(String value){return value.isEmpty()?List.of():Arrays.stream(value.split(",")).map(AdmissionPolicyTest::decode).toList();}
    private static List<Integer> ints(String value){return value.isEmpty()?List.of():Arrays.stream(value.split(",")).map(Integer::valueOf).toList();}
    private static boolean yes(String value){return value.equals("1");}
    private static void integer(MessageDigest hash,int value){hash.update(ByteBuffer.allocate(4).putInt(value).array());}
    private static void string(MessageDigest hash,String value){byte[] raw=value.getBytes(StandardCharsets.UTF_8);integer(hash,raw.length);hash.update(raw);}
    @TestFactory Stream<DynamicTest> allUnicodeScalarsMatchReference()throws Exception{
        return rows("name-unicode").stream().map(r->DynamicTest.dynamicTest("Unicode "+r[0]+".."+r[1],()->{
            var hash=MessageDigest.getInstance("SHA-256");
            for(int cp=Integer.parseInt(r[0]);cp<Integer.parseInt(r[1]);cp++){
                if(cp>=0xd800&&cp<=0xdfff)continue;String c=new String(Character.toChars(cp));
                integer(hash,cp);integer(hash,NameUnicode.properties(cp));
                for(String value:List.of(NameUnicode.nfkc(c),NameUnicode.nfkd(c),PlayerNames.skeleton(c),PlayerNames.safeBase(c)))string(hash,value);
            }
            assertEquals(r[2],HexFormat.of().formatHex(hash.digest()));
        }));
    }
    @TestFactory Stream<DynamicTest> originalNamePoliciesAndNormalizationSequences()throws Exception{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("names"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            for(var r:e.getValue()){
                String requested=decode(r[1]),label=decode(r[2]),expected=r[5];
                var players=new ArrayList<PlayerNames.Existing>();
                if(!r[4].isEmpty())for(String p:r[4].split(",")){String[] pair=p.split(":",-1);players.add(new PlayerNames.Existing(decode(pair[0]),yes(pair[1])));}
                String actual=switch(r[0]){
                    case "normalize" -> Base64.getEncoder().encodeToString(NameUnicode.nfkc(requested).getBytes(StandardCharsets.UTF_8))+";"+Base64.getEncoder().encodeToString(NameUnicode.nfkd(requested).getBytes(StandardCharsets.UTF_8));
                    case "skeleton" -> PlayerNames.skeleton(requested);
                    case "allocate" -> PlayerNames.allocate(requested,players,strings(r[3]),label);
                    case "bot" -> PlayerNames.allocateBot(requested,label,players);
                    case "reserved" -> PlayerNames.reserved(requested,strings(r[3]))?"1":"0";
                    case "poses" -> PlayerNames.posesAsBot(requested,label)?"1":"0";
                    default -> throw new IllegalStateException(r[0]);
                };
                assertEquals(r[0].equals("normalize")?expected:decode(expected),actual,Arrays.toString(r));
                if(r[0].equals("allocate")||r[0].equals("bot"))assertTrue(actual.getBytes(StandardCharsets.UTF_8).length<=15);
            }
        }));
    }
    @TestFactory Stream<DynamicTest> originalTeamResolutionForAllWireIds()throws Exception{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("admission-teams"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("team "+e.getKey(),()->{
            for(var r:e.getValue()){
                var players=new ArrayList<AdmissionPolicy.Occupant>();int next=0;
                for(int team:new int[]{2,3})for(int i=0;i<Integer.parseInt(r[team-1]);i++)players.add(new AdmissionPolicy.Occupant(next++,"P",false,team));
                players.add(new AdmissionPolicy.Occupant(next++,"S",false,0));players.add(new AdmissionPolicy.Occupant(next,"N",false,1));
                var settings=new AdmissionPolicy.Settings(yes(r[4]),Integer.parseInt(r[3]),List.of(),"");int hook=Integer.parseInt(r[7]);
                var actual=AdmissionPolicy.resolveTeam(Integer.parseInt(r[0]),yes(r[5]),settings,players,Integer.parseInt(r[8]),yes(r[6]),hook==0?null:t->hook);
                assertEquals(new AdmissionPolicy.Team(Integer.parseInt(r[9]),Integer.parseInt(r[10]),yes(r[11])),actual,Arrays.toString(r));
            }
        }));
    }
    private static GameRules rules(int variant){
        var rules=GameRules.serverDefaults();
        if(variant>0)for(var d:GameRules.definitions().values())if(d.classId()>=0&&(variant==3||d.classId()%2==variant%2))rules=rules.apply(Map.of(d.key(),false));return rules;
    }
    @TestFactory Stream<DynamicTest> originalModeAndRuleClassFallbacks()throws Exception{
        var variants=java.util.stream.IntStream.range(0,4).mapToObj(AdmissionPolicyTest::rules).toList();
        var disabled=variants.stream().map(GameRules::disabledTools).toList();
        var tools=java.util.stream.IntStream.range(0,75).boxed().toList();
        var ugcTools=java.util.stream.IntStream.range(0,30).boxed().toList();
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("admission-classes"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("classes "+e.getKey(),()->{
            var selections=new HashMap<List<Integer>,ClassCatalog.Selection>();
            for(var r:e.getValue()){
                int variant=Integer.parseInt(r[1]);var rules=variants.get(variant);var mode=ModeCatalog.get(r[0]);boolean custom=yes(r[2]),ugc=yes(r[3]);int id=Integer.parseInt(r[4]);
                var selection=selections.computeIfAbsent(List.of(variant,id),key->ClassCatalog.normalize(id,tools,List.of("pillbox"),ugcTools,null,disabled.get(variant)));
                assertEquals(yes(r[6]),AdmissionPolicy.classSelectable(id,rules,mode,custom,ugc),Arrays.toString(r));
                assertEquals(r[7].equals("-1")?null:Integer.valueOf(r[7]),AdmissionPolicy.fallbackClass(rules,mode,custom,ugc),Arrays.toString(r));
                var actual=AdmissionPolicy.enforceClass(selection,Integer.parseInt(r[5]),rules,mode,custom,ugc);
                assertEquals(Integer.parseInt(r[8]),actual.classId(),Arrays.toString(r));assertEquals(ints(r[9]),actual.loadout());assertEquals(strings(r[10]),actual.prefabs());assertEquals(ints(r[11]),actual.ugcTools());
            }
        }));
    }
    @Test void trustedAssignmentIgnoresForcedTeamAndModeOrderingPreservesSpectators(){
        var occupants=List.of(new AdmissionPolicy.Occupant(1,"Kiko",true,2),new AdmissionPolicy.Occupant(2,"Other",false,2));
        var settings=AdmissionPolicy.Settings.defaults();var rules=GameRules.serverDefaults();var mode=ModeCatalog.get("tdm");
        var selection=ClassCatalog.normalize(5,List.of(),rules);var request=new SimplePackets.NewPlayerConnection(2,5,3,1,"K1ko");
        var plain=AdmissionPolicy.resolve(0,request,selection,settings,rules,mode,AdmissionPolicy.Context.ordinary(occupants));
        assertEquals(3,plain.team().wire());assertTrue(plain.team().rebalanced());assertEquals("Player",plain.name());assertEquals(0,plain.selection().classId());
        var assigned=AdmissionPolicy.resolve(0,request,selection,settings,rules,mode,new AdmissionPolicy.Context(occupants,2,new AdmissionPolicy.ModePolicy(){}));
        assertEquals(2,assigned.team().wire());assertFalse(assigned.team().rebalanced());
        var calls=new ArrayList<String>();var hooks=new AdmissionPolicy.ModePolicy(){
            @Override public java.util.function.IntUnaryOperator prepareTeam(){return t->{calls.add("team:"+t);return 3;};}
            @Override public ClassCatalog.Selection prepareSelection(int team,ClassCatalog.Selection s){calls.add("selection:"+team+":"+s.classId());return s;}
        };
        var context=new AdmissionPolicy.Context(occupants,2,hooks);
        assertEquals(3,AdmissionPolicy.resolve(0,request,selection,settings,rules,mode,context).team().wire());assertEquals(List.of("team:2","selection:3:0"),calls);
        calls.clear();var spectator=new SimplePackets.NewPlayerConnection(0,5,3,1,"Watcher");
        var result=AdmissionPolicy.resolve(0,spectator,selection,settings,rules,mode,new AdmissionPolicy.Context(occupants,null,hooks));
        assertEquals(0,result.team().wire());assertSame(selection,result.selection());assertTrue(calls.isEmpty());
    }
    @Test void namesReleaseOnDisconnectAndSettingsUseSourceSections(){
        var live=new ArrayList<PlayerNames.Existing>();live.add(new PlayerNames.Existing("Kiko",false));assertEquals("Kik0~2",PlayerNames.allocate("Kik0",live,List.of(),""));
        live.clear();assertEquals("Kik0",PlayerNames.allocate("Kik0",live,List.of(),""));
        var settings=AdmissionPolicy.Settings.from(Toml.parse("[teams]\nauto_balance=false\nbalance_threshold=0\n[conduct]\nreserved_names=['Kiril']\n[bots]\nname_prefix='BOT_'"));
        assertFalse(settings.autoBalance());assertEquals(0,settings.balanceThreshold());assertEquals(List.of("Kiril"),settings.reservedNames());assertEquals("BOT_",settings.botLabel());
        assertThrows(UnsupportedOperationException.class,()->settings.reservedNames().add("x"));
        for(String prefix:List.of("toolong","BÓT","a\n"))assertThrows(IllegalArgumentException.class,()->new AdmissionPolicy.Settings(true,2,List.of(),prefix));
    }
}
