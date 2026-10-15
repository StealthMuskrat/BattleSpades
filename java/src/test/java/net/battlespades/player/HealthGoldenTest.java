// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HealthGoldenTest {
    private static List<String[]> rows(String name)throws IOException{try(var in=new GZIPInputStream(Objects.requireNonNull(HealthGoldenTest.class.getResourceAsStream("/golden/health-"+name+".tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    private static int integer(String s){return Integer.parseInt(s);}
    private static double number(String s){return LifeGoldenTest.number(s);}
    private static String bits(double v){return LifeGoldenTest.bits(v);}
    private static String vector(PlayerPhysics.Vector v){return v==null?"-":bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static String history(LifeHarness h,List<DamageHistory.Contribution> entries){return entries.isEmpty()?"-":entries.stream().map(c->c.player().id()+","+c.team()+","+c.generation()+","+bits(c.damage())+","+bits(c.touched())+","+(h.runtime.players().contains(c.player())?1:0)).collect(Collectors.joining("|"));}
    private static String state(LifeHarness h){return h.runtime.players().stream().map(p->{var s=h.runtime.score(p.id());return String.join("~",Integer.toString(p.id()),Integer.toString(p.health()),Integer.toString(p.maxHealth()),p.alive()?"1":"0",p.spawned()?"1":"0",p.god()?"1":"0",Long.toString(p.generation()),bits(p.lastDamageWall()),bits(p.lastCombatHit()),Integer.toString(p.lastDamageSource()),Integer.toString(p.lastDamageType()),vector(p.lastDamagePosition()),bits(p.protectionRemaining(h.monotonic)),Integer.toString(s.kills()),Integer.toString(s.deaths()),Integer.toString(s.streak()),Integer.toString(s.lastKilledBy()),p.lastDeath()==null?"-":HexFormat.of().formatHex(p.lastDeath().encode()),history(h,p.damageHistory().snapshot()));}).collect(Collectors.joining(";"));}
    private static void place(PlayerLife p){if(p.team()!=0)p.body().position(position(p.id()));}
    private static PlayerPhysics.Vector position(int id){return new PlayerPhysics.Vector(36+id*5.125,id==1?-1.5:36,97.25);}
    private static void actors(LifeHarness h){h.admit(0,2);h.admit(1,3);h.admit(2,2);h.runtime.players().forEach(HealthGoldenTest::place);h.wire.clear();h.events.clear();}
    private static void modifier(LifeHarness h,String name){h.damageModifier=name.equals("none")?Optional.empty():Optional.of((v,a,s,k)->switch(name){case "identity"->a;case "veto"->0;case "half"->a*0.5;case "double"->a*2;case "minus"->a-0.5;default->throw new AssertionError(name);});}
    private record Observation(String result,String state,String wire,String evidence){}
    private static Observation operation(LifeHarness h,Deque<LifeRuntime.BlastScope> scopes,String op,String argument){
        h.wire.clear();h.damageEvidence.clear();h.healingEvidence.clear();h.deathEvidence.clear();var lanes=new IdentityHashMap<GamePacket,String>();var evidence=new ArrayList<String>();
        h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){lanes.put(p,"B");}public void owner(int id,GamePacket p){lanes.put(p,"O"+id);}public boolean inGame(int id){return true;}};
        String result="-";var a=argument.split(",");
        try{switch(op){
            case "damage" -> result=h.runtime.damage(integer(a[0]),number(a[1]),a[2].equals("-")?null:integer(a[2]),integer(a[3]),a[4].equals("-")?null:integer(a[4]))?"1":"0";
            case "heal" -> h.runtime.heal(integer(a[0]),integer(a[1]));
            case "advance" -> {h.monotonic+=number(a[0]);h.wall+=number(a[0]);}
            case "god" -> h.runtime.player(integer(a[0])).god(a[1].equals("1"));
            case "cap" -> h.runtime.player(integer(a[0])).protectionCap(a[1].equals("-")?null:number(a[1]));
            case "health" -> {h.runtime.player(integer(a[0])).health(integer(a[1]));h.runtime.player(integer(a[0])).maxHealth(integer(a[2]));}
            case "live" -> {if(a[1].equals("0"))h.loading.add(integer(a[0]));else h.loading.remove(integer(a[0]));}
            case "modifier" -> modifier(h,a[0]);
            case "scoring" -> h.scoringActive=a[0].equals("1");
            case "spawn" -> {var p=h.runtime.player(integer(a[0]));p.applyPending();p.spawn(position(p.id()),h.monotonic,1,p.team()==2?0x123456:0x323c46);}
            case "class" -> h.runtime.player(integer(a[0])).apply(new ClassCatalog.Selection(integer(a[1]),List.of(5,6,23),List.of(),List.of()));
            case "die" -> h.runtime.die(integer(a[0]),a[1].equals("-")?null:integer(a[1]),integer(a[2]));
            case "recent" -> {var p=h.runtime.recentEnemyAttacker(integer(a[0]));result=p==null?"-1":Integer.toString(p.id());}
            case "transition" -> {var p=h.runtime.endLifeForTransition(integer(a[0]),integer(a[1]));result=p==null?"-1":Integer.toString(p.id());}
            case "remove" -> h.runtime.disconnected(integer(a[0]));
            case "add" -> {h.admit(integer(a[0]),integer(a[1]));place(h.runtime.player(integer(a[0])));}
            case "blast" -> scopes.addLast(h.runtime.blast(a[0].equals("-")?null:integer(a[0])));
            case "endblast" -> scopes.removeLast().close();
            default -> throw new AssertionError(op);
        }}catch(IllegalArgumentException error){result="error";}
        for(var d:h.damageEvidence)evidence.add("damage:"+d.victim().id()+","+(d.source()==null?-1:d.source().id())+","+d.removed()+","+d.killType()+","+(d.healthBefore()-d.removed())+","+(d.killed()?1:0));
        for(var e:h.healingEvidence)evidence.add("heal:"+e.victim().id()+":"+e.restored());
        for(var d:h.deathEvidence)evidence.add("death:"+d.victim().id()+":"+(d.killer()==null?-1:d.killer().id())+":"+d.presentationType()+":"+history(h,d.contributions()));
        var wire=h.wire.stream().filter(p->p.id()==5||p.id()==46).map(p->lanes.get(p)+":"+HexFormat.of().formatHex(p.encode())).collect(Collectors.joining("|"));
        return new Observation(result,state(h),wire.isEmpty()?"-":wire,evidence.isEmpty()?"-":String.join(";",evidence));
    }
    private static void compare(String[] row,int offset,Observation o,String context){assertAll(context,()->assertEquals(row[offset],o.result(),"result"),()->assertEquals(row[offset+1],o.state(),"state"),()->assertEquals(row[offset+2],o.wire(),"wire"),()->assertEquals(row[offset+3],o.evidence(),"observer evidence"));}
    @TestFactory Stream<DynamicTest> originalDamagePolicy()throws IOException{return rows("damage").stream().map(r->DynamicTest.dynamicTest(r[0],()->{
        String[] a=r[1].split(",");var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_WEAPON_DAMAGE",number(a[2]),"RULE_ZOMBIE_CLASS_DAMAGE",number(a[3]),"RULE_ONE_HIT_KILL",a[4].equals("1"),"RULE_SPAWN_PROTECTION_TIME",number(a[5])));
        try(var h=new LifeHarness(rules,a[0])){actors(h);h.runtime.player(1).apply(new ClassCatalog.Selection(integer(a[1]),List.of(5,6,23),List.of(),List.of()));var p=h.runtime.player(0);p.protectionCap(a[6].equals("-")?null:number(a[6]));p.god(a[7].equals("1"));p.health(integer(a[8]));p.maxHealth(integer(a[9]));h.monotonic+=number(a[10]);h.wall+=number(a[10]);modifier(h,a[11]);if(a[16].equals("0"))h.loading.add(0);
            compare(r,2,operation(h,new ArrayDeque<>(),"damage",String.join(",","0",a[13],a[12],a[14],a[15])),r[0]+" "+r[1]);
        }
    }));}
    @TestFactory Stream<DynamicTest> originalHealingPolicy()throws IOException{return rows("healing").stream().map(r->DynamicTest.dynamicTest(r[0],()->{String[] a=r[1].split(",");try(var h=new LifeHarness()){actors(h);h.runtime.player(0).health(integer(a[0]));h.runtime.player(0).maxHealth(integer(a[1]));if(a[3].equals("0"))h.loading.add(0);compare(r,2,operation(h,new ArrayDeque<>(),"heal","0,"+a[2]),r[0]+" "+r[1]);}}));}
    @TestFactory Stream<DynamicTest> originalStatefulHealthAndCredit()throws IOException{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("traces"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",integer(e.getKey())%2==1?3.0:0.0));try(var h=new LifeHarness(rules)){actors(h);var scopes=new ArrayDeque<LifeRuntime.BlastScope>();int i=0;for(var r:e.getValue())compare(r,3,operation(h,scopes,r[1],r[2]),e.getKey()+"/"+i+++" "+r[1]+" "+r[2]);assertTrue(scopes.isEmpty());}}));
    }
    @TestFactory Stream<DynamicTest> originalNestedBlastAttribution()throws IOException{return rows("attribution").stream().map(r->DynamicTest.dynamicTest(String.join("/",r),()->{try(var h=new LifeHarness()){
        actors(h);var outer=h.runtime.blast(r[0].equals("-")?null:integer(r[0]));LifeRuntime.BlastScope nested=r[1].equals("none")?null:h.runtime.blast(r[1].equals("-")?null:integer(r[1]));
        try{h.runtime.damage(0,1,r[2].equals("-")?null:integer(r[2]),0);assertEquals(integer(r[3]),h.runtime.player(0).lastDamageSource());}finally{if(nested!=null)nested.close();outer.close();}
    }}));}
}
