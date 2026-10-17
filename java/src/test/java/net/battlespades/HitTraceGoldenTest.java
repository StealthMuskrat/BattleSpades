// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.combat.CombatGeometry.Cell;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HitTraceGoldenTest {
    record Entity(int id,boolean alive,boolean takesDamage,double hitRadius,Vector hitCenter) implements HitTrace.Entity {}
    record Corpse(int id,Vector position) implements HitTrace.Corpse {public Vector orientation(){return new Vector(1,0,0);}}
    static double number(String s){return CombatGeometryGoldenTest.number(s);}
    static Vector vector(String[] a,int at){return new Vector(number(a[at]),number(a[at+1]),number(a[at+2]));}
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static String result(HitTrace.Hit hit){return switch(hit){
        case null->"-";
        case HitTrace.PlayerHit h->"player|"+h.player().id()+"|"+(h.headshot()?1:0)+"|"+h.part()+"|"+vector(h.position());
        case HitTrace.EntityHit h->"entity|"+((Entity)h.entity()).id()+"|0|-|"+vector(h.position());
        case HitTrace.CorpseHit h->"corpse|"+((Corpse)h.corpse()).id()+"|0|-|"+vector(h.position());
        case HitTrace.BlockHit h->"block|"+h.cell().x()+","+h.cell().y()+","+h.cell().z()+"|0|-|"+vector(h.position());
    };}
    @TestFactory Stream<DynamicTest> originalNearestHitSelection()throws IOException {
        final List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/hit-trace.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        return IntStream.range(0,20).mapToObj(group->DynamicTest.dynamicTest("original hit selection "+group,()->{try(var h=new LifeHarness()){
            for(int id=0;id<4;id++)h.admit(id,id<2?2:3);var owner=new LagCompensation.ShotOwner();
            for(int i=group*300;i<(group+1)*300;i++){
                var r=rows.get(i);var entities=new ArrayList<Entity>();var corpses=new ArrayList<Corpse>();
                for(var row:r[4].split(";")){var a=row.split(",");var p=h.runtime.player(Integer.parseInt(a[0]));p.spawn(vector(a,2),100,1,p.color());p.orientation(1,0,0);if(a[1].equals("0"))p.beginDeath(101);}
                for(var row:r[5].split(";")){var a=row.split(",");entities.add(new Entity(Integer.parseInt(a[0]),a[1].equals("1"),a[2].equals("1"),number(a[3]),vector(a,4)));}
                for(var row:r[6].split(";")){var a=row.split(",");corpses.add(new Corpse(Integer.parseInt(a[0]),vector(a,1)));}
                var scene=new HitTrace.Scene(){
                    public Cell raycast(Vector origin,Vector direction,double maximum){return r[2].equals("-")?null:CombatGeometryGoldenTest.cell(r[2]);}
                    public Iterable<PlayerLife> players(){return h.runtime.players();}
                    public boolean enemies(PlayerLife a,PlayerLife b){return a.team()!=b.team()||r[3].equals("1");}
                    public Iterable<Entity> entities(){return entities;}public Iterable<Corpse> corpses(){return corpses;}
                };
                var trace=new HitTrace(scene,owner);assertEquals(r[7],result(trace.trace(h.runtime.player(0),new Vector(20,20,20),new Vector(1,0,0),number(r[1]))),"case "+r[0]);
            }
        }}));
    }
}
