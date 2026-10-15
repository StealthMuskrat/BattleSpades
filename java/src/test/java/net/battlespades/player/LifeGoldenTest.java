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
import net.battlespades.session.PlayerRoster;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class LifeGoldenTest {
    static List<String[]> rows(String name)throws IOException{try(var in=new GZIPInputStream(Objects.requireNonNull(LifeGoldenTest.class.getResourceAsStream("/golden/life-"+name+".tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String vec(PlayerPhysics.Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static String map(Map<Integer,?> values){return values.isEmpty()?"-":values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+":"+(e.getValue() instanceof LifeInventory.Ammo a?a.clip()+"/"+a.reserve():e.getValue())).collect(Collectors.joining(","));}
    private static String inventory(LifeInventory i){var due=new TreeMap<Integer,String>();i.deployableDue().forEach((k,v)->due.put(k,bits(v)));return String.join(";",Integer.toString(i.weapon()),Integer.toString(i.tool()),i.ammo().clip()+","+i.ammo().reserve(),map(i.weapons()),map(i.oriented()),map(i.deployables()),map(due),Integer.toString(i.turrets()),Integer.toString(i.disguise()),i.blocks()+","+i.maxBlocks(),(i.reloading()?1:0)+","+bits(i.reloadEnd()));}
    @TestFactory Stream<DynamicTest> originalPlayerSpawn()throws IOException{
        return rows("spawn").stream().map(r->DynamicTest.dynamicTest("class="+r[0]+" scale="+r[1]+" equipment="+r[2],()->{
            var tools=new ArrayList<>(List.of(5,6,23));if(!r[2].equals("-"))for(var item:r[2].split(","))tools.add(Integer.parseInt(item));
            var p=new PlayerLife(0,2,0,"Life","tdm",(x,y,z)->false,GameRules.serverDefaults(),new ClassCatalog.Selection(Integer.parseInt(r[0]),tools,List.of(),List.of()),Double.parseDouble(r[1]));assertEquals(r[8],inventory(p.inventory()));
            p.orientation(0,1,0);p.orientation(0,0,-1);p.spawn(new PlayerPhysics.Vector(36.5,36.5,97.25),100,Double.parseDouble(r[1]),0x123456);
            assertEquals(r[3],inventory(p.inventory()));assertEquals(r[4],p.generation()+","+(p.alive()?1:0)+","+(p.spawned()?1:0)+","+p.health()+","+p.jetpack()+","+p.parachute()+","+p.color());assertEquals(r[5],vec(p.position()));assertEquals(r[6],vec(p.aim()));assertEquals(r[7],vec(p.body().orientation()));
        }));
    }
    @TestFactory Stream<DynamicTest> originalInventoryTraces()throws IOException{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("inventory"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            var i=new LifeInventory();int cid=Integer.parseInt(e.getValue().getFirst()[1]);double scale=Double.parseDouble(e.getValue().getFirst()[2]);i.spawn(cid,scale);int index=0;
            for(var r:e.getValue()){
                String[] args=r[4].split(",");switch(r[3]){
                    case "start" -> { }
                    case "tool" -> i.tool(Integer.parseInt(args[0]));
                    case "ammo" -> i.ammo(Integer.parseInt(args[0]),Integer.parseInt(args[1]));
                    case "reload" -> i.reloadState(true,number(args[0]));
                    case "deploy" -> {int id=Integer.parseInt(args[0]);double now=number(args[1]);boolean ready=i.deployableReady(id,now);assertEquals(r[5].equals("1"),ready);if(ready)i.commitDeployable(id,now);}
                    case "oriented" -> {int id=Integer.parseInt(args[0]);i.oriented(id,Integer.parseInt(args[1]));i.launcherUse(id,0,number(args[2]));}
                    case "restock" -> i.restock(Integer.parseInt(args[0]),number(args[1]),args[2].equals("1"));
                    case "spawn" -> i.spawn(cid,scale);
                    default -> fail(r[3]);
                }
                assertEquals(r[6],inventory(i),e.getKey()+"/"+index+++" "+r[3]);
            }
        }));
    }
    @Test void originalKillFeed()throws IOException{
        var feed=new KillFeed();for(var r:rows("killfeed")){int a=Integer.parseInt(r[1]),b=Integer.parseInt(r[2]);switch(r[0]){
            case "multi" -> assertEquals(Integer.parseInt(r[4]),feed.registerMultikill(a,number(r[3])));
            case "dom" -> {var f=feed.registerDomination(a,b);assertEquals(r[4],(f.domination()?1:0)+","+(f.revenge()?1:0));}
            case "forget" -> feed.forget(a);
            case "clear" -> feed.clearPlayer(a);
            case "relation" -> assertEquals(r[4].equals("1"),feed.dominating(a,b));
            default -> fail(r[0]);
        }}
    }
    @Test void originalRosterTransitions()throws IOException{
        var roster=new PlayerRoster();Map<Integer,PlayerRoster.Known> known=Map.of();int row=0;
        for(var r:rows("roster")){
            int id=Integer.parseInt(r[1]);var create=new PlayerPackets.CreatePlayer(id,0,0,2,0,0,new Vector3f(1,2,3),new Vector3f(1,0,0),"Life",List.of(5,6,23),List.of());
            switch(r[0]){
                case "create","respawn" -> roster.create(create,0x123456);
                case "dead" -> roster.death(new LifePackets.KillAction(id,0,3,2,0,false,false));
                case "remove" -> roster.remove(id);
                case "sync" -> {var plan=roster.reconcile(known,0);String packets=plan.packets().stream().filter(p->p.id()!=11).map(p->HexFormat.of().formatHex(p.encode())).collect(Collectors.joining(","));assertEquals(r[2],packets.isEmpty()?"-":packets,"row "+row);known=plan.known();}
                default -> fail(r[0]);
            }row++;
        }
    }
    @TestFactory Stream<DynamicTest> originalLifePackets()throws IOException{return rows("packets").stream().map(r->DynamicTest.dynamicTest(r[0]+"/"+r[1],()->{
        int i=Integer.parseInt(r[1]);GamePacket packet=switch(r[0]){
            case "hp" -> new LifePackets.SetHP(i,i%3,new Vector3f((i-50)*0.125f,36.5f,97.25f));
            case "death" -> new LifePackets.KillAction(i,99-i,i%26,i%8,i,i%2!=0,(i/2)%2!=0);
            case "reload" -> new LifePackets.WeaponReload(i,i%66,i%2!=0);
            default -> throw new AssertionError(r[0]);
        };byte[] expected=HexFormat.of().parseHex(r[2]);assertArrayEquals(expected,packet.encode());assertArrayEquals(HexFormat.of().parseHex(r[3]),PacketCodec.decode(expected).encode());
    }));}
}
