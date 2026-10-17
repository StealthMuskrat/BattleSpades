// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeHitEffectsGoldenTest {
    static Vector vector(String s){var a=s.split(",");return new Vector(number(a[0]),number(a[1]),number(a[2]));}
    static String vector(Vector v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.x()))+","+HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.y()))+","+HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v.z()));}
    static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    @TestFactory Stream<DynamicTest> originalMeleeWithRealHealthAndFeedback()throws IOException {
        final List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/melee-hit-effects.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        return IntStream.range(0,17).mapToObj(group->DynamicTest.dynamicTest("original melee hit effects "+group,()->{
            for(var r:rows.subList(group*64,(group+1)*64)){
                var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",number(r[3]),"RULE_WEAPON_DAMAGE",number(r[5]),"RULE_ONE_HIT_KILL",r[6].equals("1")));
                try(var h=new LifeHarness(rules)){
                    h.admit(0,2);h.admit(1,3);var attacker=h.runtime.player(0);var target=h.runtime.player(1);target.apply(new ClassCatalog.Selection(Integer.parseInt(r[2]),List.of(5,6,23),List.of(),List.of()));attacker.body().position(new Vector(36,36,97));target.body().position(new Vector(Double.parseDouble(r[24]),36,number(r[13])));attacker.orientation(1,0,0);target.orientation(r[12].equals("1")?-1:1,0,0);attacker.fire().tool(Integer.parseInt(r[1]),true);target.fire().tool(r[10].equals("1")?52:6,true);target.motion().controls(0,r[11].equals("1")?16:0);target.god(r[9].equals("1"));target.health(Integer.parseInt(r[8]));target.body().velocity(Integer.parseInt(r[0])%11==0?new Vector(1e-6,-1e-6,1e-6):new Vector(.125,-.25,.05));h.monotonic+=number(r[4]);h.wall+=number(r[4]);
                    h.damageModifier=r[7].equals("none")?Optional.empty():Optional.of((v,a,s,k)->switch(r[7]){case "veto"->0;case "half"->a*.5;case "double"->a*2;default->throw new AssertionError(r[7]);});
                    var wire=new ArrayList<String>();var events=new ArrayList<String>();h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){if(p.id()==5||p.id()==46)wire.add("B:1:"+HexFormat.of().formatHex(p.encode()));}public void owner(int id,GamePacket p){if(p.id()==5||p.id()==46)wire.add("O"+id+":1:"+HexFormat.of().formatHex(p.encode()));}public boolean inGame(int id){return true;}};
                    var map=CollisionMap.of(h.world);var trace=new HitTrace(new HitTrace.Scene(){public CombatGeometry.Cell raycast(Vector origin,Vector direction,double maximum){return HitTrace.terrainRay(map,origin,direction,maximum);}public Iterable<PlayerLife> players(){return h.runtime.players();}public boolean enemies(PlayerLife a,PlayerLife b){return a.team()!=b.team();}public Iterable<HitTrace.Entity> entities(){return List.of();}public Iterable<HitTrace.Corpse> corpses(){return List.of();}},new LagCompensation.ShotOwner());
                    var effects=new HitEffects(h.runtime,trace,new HitEffects.Domain(){
                        public boolean hardcore(PlayerLife v){return false;}public void reject(PlayerLife a,String reason){events.add("reject:"+reason);}public void shieldImpact(PlayerLife v,Vector p,boolean melee){events.add("shield");}public void playerHit(PlayerLife a,PlayerLife v,boolean head){events.add("hit");}
                        public void reliable(GamePacket p){wire.add("B:1:"+HexFormat.of().formatHex(p.encode()));}public void unreliable(GamePacket p){wire.add("B:0:"+HexFormat.of().formatHex(p.encode()));}
                        public void damageEntity(HitTrace.Entity e,double amount,PlayerLife a){fail("No entities in this explicit test domain");}public boolean explodeCorpse(HitTrace.Corpse c,PlayerLife a,boolean show){throw new AssertionError("No corpses in this explicit test domain");}public boolean damageBlock(PlayerLife a,CombatGeometry.Cell c,double amount){throw new AssertionError("No terrain on these fixture rays");}
                    });
                    assertEquals(r[14].equals("1"),effects.melee(attacker,vector(r[25]),new Vector(1,0,0),r[26].equals("-")?null:r[26].equals("empty")?List.of():Arrays.stream(r[26].split(";")).map(MeleeHitEffectsGoldenTest::vector).toList(),number(r[27])),"result "+r[0]);assertEquals(Integer.parseInt(r[15]),target.health(),"HP "+r[0]);assertEquals(r[16].equals("1"),target.alive());assertEquals(r[17].equals("1"),target.spawned());assertEquals(Integer.parseInt(r[18]),h.runtime.score(0).kills());assertEquals(Integer.parseInt(r[19]),h.runtime.score(1).deaths());assertEquals(r[20],events.isEmpty()?"-":String.join(";",events),"effects "+r[0]);assertEquals(r[21],wire.isEmpty()?"-":String.join("|",wire),"wire "+r[0]);assertEquals(r[22],vector(PlayerMotion.clipped(target.body().state().velocity())),"facade velocity "+r[0]);assertEquals(r[23],vector(target.body().state().velocity()),"raw velocity "+r[0]);
                }
            }
        }));
    }
}
