// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatCommitGoldenTest {
    static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static Vector3f vector(String s){var a=s.split(",");return new Vector3f((float)number(a[0]),(float)number(a[1]),(float)number(a[2]));}
    static String aggregates(PlayerLife p){var s=p.combatStatistics().snapshot();return s.shots()+","+s.hits()+","+s.headshots()+","+s.pelletHits()+"|"+(s.weapons().isEmpty()?"-":s.weapons().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+","+e.getValue().shots()+","+e.getValue().hits()+","+e.getValue().headshots()).collect(Collectors.joining(";")))+"|"+(s.seeds().isEmpty()?"-":s.seeds().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+","+e.getValue()).collect(Collectors.joining(";")));}
    @TestFactory Stream<DynamicTest> originalPacketSixCommitOrderAndRealDamage()throws IOException {
        final List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/combat-commit.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        return IntStream.range(0,16).mapToObj(group->DynamicTest.dynamicTest("original combat commit "+group,()->{
            for(var r:rows.subList(group*50,(group+1)*50)){
                var values=new HashMap<String,Object>();values.put("RULE_SPAWN_PROTECTION_TIME",number(r[3]));if(!r[15].equals("-"))values.put(r[15],false);var rules=GameRules.serverDefaults().compatibility(values);
                try(var h=new LifeHarness(rules)){
                    var ports=new CombatPorts(h);ports.allows=r[8].equals("1");ports.burden=r[9].equals("1");ports.intel=r[10].equals("1");ports.mounted=r[11].equals("1");var combat=ports.bind();h.admit(0,2);h.admit(1,3);var attacker=h.runtime.player(0);var target=h.runtime.player(1);target.apply(new ClassCatalog.Selection(Integer.parseInt(r[2]),List.of(5,6,23),List.of(),List.of()));attacker.body().position(new Vector(36,36,97));target.body().position(new Vector(39,36,97));attacker.orientation(1,0,0);target.orientation(1,0,0);attacker.fire().tool(Integer.parseInt(r[1]),true);attacker.bot(r[12].equals("1"));attacker.inventory().ammo(Integer.parseInt(r[14]),100);attacker.inputTimeline().receive(new LiveClientInput(101,0,false,6,1,0,0,0,0,0,0),false,false,attacker.aim(),1000,0,false);target.health(Integer.parseInt(r[5]));target.god(r[6].equals("1"));
                    if(r[13].equals("1"))attacker.fire().consumeShot(h.monotonic,null,100);if(!r[7].equals("1"))attacker.beginDeath(h.wall);h.monotonic+=number(r[4]);h.wall+=number(r[4]);ports.events.clear();ports.wire.clear();
                    var packet=new ShootPacket(101,254,0,vector(r[16]),vector(r[17]),32767,32767,true,true,Integer.parseInt(r[18]));boolean result=combat.shot(attacker,packet);String context="case "+r[0];
                    assertEquals(r[19].equals("1"),result,context+" result");assertEquals(Integer.parseInt(r[20]),attacker.inventory().ammo().clip(),context+" ammo");assertEquals(r[21].equals("1"),ports.disguised,context+" disguise");assertEquals(r[22],bits(attacker.protectionRemaining(h.monotonic)),context+" protection");assertEquals(Integer.parseInt(r[23]),target.health(),context+" HP");assertEquals(r[24].equals("1"),target.alive());assertEquals(Integer.parseInt(r[25]),h.runtime.score(0).kills());assertEquals(Integer.parseInt(r[26]),h.runtime.score(1).deaths());assertEquals(r[27],aggregates(attacker),context+" tally");assertEquals(r[28],ports.events.isEmpty()?"-":String.join(";",ports.events),context+" order");assertEquals(r[29],ports.wire.isEmpty()?"-":String.join("|",ports.wire),context+" wire");
                }
            }
        }));
    }
}
