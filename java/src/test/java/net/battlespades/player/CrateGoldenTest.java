// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class CrateGoldenTest {
    static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    static String vec(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    List<String[]> rows()throws Exception{try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/crate.tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static IntStream traces(){return IntStream.range(0,32);}
    @ParameterizedTest @MethodSource("traces") void originalRefillRespawnAndPhysicalCrateTimelines(int trace)throws Exception{
        try(var h=new LifeHarness()){
            var events=new ArrayList<String>();var wire=new ArrayList<String>();h.admit(0,2);var player=h.runtime.player(0);player.health(25);while(player.inventory().removeBlock()){}player.inventory().ammo(2,0);h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){throw new AssertionError();}public void owner(int id,GamePacket p){wire.add(HexFormat.of().formatHex(p.encode()));}public boolean inGame(int id){return true;}};
            for(int[] c:new int[][]{{39,36,100},{37,34,97},{41,38,105}})h.world.set(new MutableWorld.Cell(c[0],c[1],c[2]),true,0x406080,null);if(trace%4==2)h.world.set(new MutableWorld.Cell(39,36,80),true,0x406080,null);
            for(int x=32;x<48;x++)h.world.set(new MutableWorld.Cell(x,32,100),true,0x406080,null);
            var registry=new EntityRegistry();var world=new PickupWorld(h.world);var b=new PickupCrateBehavior(p->{events.add("refill");if(trace%3==0)h.runtime.heal(p.id(),25);else if(trace%3==1)h.runtime.restock(p.id(),3,false);else h.runtime.restockBlocks(p.id());},new double[]{0,.25,1,15}[trace%4],trace%2==1?14:null,(p,id)->h.destination.owner(p.id(),AudioPackets.PlaySound.personal(id)),trace%3!=0,new double[]{1,90,1,250}[trace%4],e->{events.add("cue");if(trace%5==0)throw new IllegalStateException("Controlled cosmetic cue fault");});var placement=new EntityRegistry.Placement();placement.behavior=b;var e=registry.place(4,39.25,36.75,98.75,placement);
            for(var r:rows()){if(!Character.isDigit(r[0].charAt(0))||Integer.parseInt(r[0])!=trace)continue;int step=Integer.parseInt(r[1]);h.wall=number(r[3]);h.monotonic=h.wall;events.clear();wire.clear();boolean touch=r[5].endsWith(":touch");if(touch)player.body().position(new Vector(e.x,e.y,e.z));var ctx=new EntityContext(number(r[2]),h.wall,touch?List.of(player):List.of(),registry,x->{events.add("create");wire.add(HexFormat.of().formatHex(new CreateEntity(x.toWireEntity()).encode()));},id->{events.add("destroy");wire.add(HexFormat.of().formatHex(new SimplePackets.DestroyEntity(id).encode()));},null,world,x->{events.add("move:"+vec(new Vector(x.x,x.y,x.z)));wire.add(HexFormat.of().formatHex(ChangeEntity.position(x.id(),new Vector3f((float)x.x,(float)x.y,(float)x.z)).encode()));});String result="-";
                switch(r[4]){case "U"->result=b.onTouch(e,player,ctx)?"1":"0";case "R"->{var c=r[5].split(",");int x=Integer.parseInt(c[0]),y=Integer.parseInt(c[1]);for(int z=0;z<239;z++)if(world.solid(x,y,z))h.world.set(new MutableWorld.Cell(x,y,z),false,0,null);}case "B"->{var c=r[5].split(",");h.world.set(new MutableWorld.Cell(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2])),true,0x406080,null);}case "T"->result=Long.toString(registry.tick(ctx,r[5].startsWith("0")?0:null));default->throw new AssertionError(r[4]);}
                String where="trace "+trace+" step "+step;assertEquals(r[6],result,where+" result");assertEquals(r[7],player.health()+","+player.inventory().blocks()+","+player.inventory().ammo().clip()+","+player.inventory().ammo().reserve(),where+" wallets");String state=(e.alive?1:0)+","+bits(e.respawnAt)+","+vec(new Vector(e.x,e.y,e.z))+","+vec(e.velocity)+","+(e.falling?1:0)+","+(e.parachuteDeployed?1:0)+","+(e.parachuteRemoved?1:0)+","+bits(e.fallAccumulator)+","+bits(e.fallClock)+","+(e.terrainSupportZ==null?"-":e.terrainSupportZ)+","+bits(e.terrainOffsetZ)+","+bits(e.terrainCheckAt)+","+vec(e.home);assertEquals(r[8],state,where+" state");assertEquals(r[9],events.isEmpty()?"-":String.join(";",events),where+" callbacks");assertEquals(r[10],wire.isEmpty()?"-":String.join("|",wire),where+" wire");
            }
        }
    }
    static IntStream sounds(){return IntStream.range(0,16);}
    @ParameterizedTest @MethodSource("sounds") void originalNativeSoundOptionalFieldsAndFixedStores(int group)throws Exception{
        for(var r:rows()){if(!r[0].equals("sound")||Integer.parseInt(r[1])/32!=group)continue;var v=Arrays.stream(r[6].split(",")).mapToDouble(CrateGoldenTest::number).toArray();var p=new AudioPackets.PlaySound(Integer.parseInt(r[2]),r[3].equals("1"),r[4].equals("1"),(float)v[0],(float)v[1],Integer.parseInt(r[5]),new Vector3f((float)v[2],(float)v[3],(float)v[4]),(float)v[5]);var expected=HexFormat.of().parseHex(r[7]);assertArrayEquals(expected,p.encode(),"sound "+r[1]);var decoded=(AudioPackets.PlaySound)PacketCodec.decode(expected);assertArrayEquals(HexFormat.of().parseHex(r[8]),decoded.encode(),"native re-encode "+r[1]);assertEquals(r[9],String.join(",",bits(decoded.volume()),bits(decoded.time()),bits(decoded.position().x()),bits(decoded.position().y()),bits(decoded.position().z()),bits(decoded.attenuation())),"decoded fields "+r[1]);}
    }
    @ParameterizedTest @MethodSource("sounds") void originalNativeChangeEntityActions(int group)throws Exception{
        for(var r:rows()){if(!r[0].equals("change")||Integer.parseInt(r[1])/32!=group)continue;var v=Arrays.stream(r[7].split(",")).mapToDouble(CrateGoldenTest::number).toArray();var p=new ChangeEntity(Integer.parseInt(r[2]),Integer.parseInt(r[3]),Integer.parseInt(r[4]),new Vector3f((float)v[0],(float)v[1],(float)v[2]),Integer.parseInt(r[5]),Integer.parseInt(r[6]),(float)v[3]);var expected=HexFormat.of().parseHex(r[8]);assertArrayEquals(expected,p.encode(),"change "+r[1]);assertArrayEquals(HexFormat.of().parseHex(r[9]),PacketCodec.decode(expected).encode(),"native change re-encode "+r[1]);}
    }

}
