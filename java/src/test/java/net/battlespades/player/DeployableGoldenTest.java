// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class DeployableGoldenTest {
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static String vector(Vector v){return bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    static IntStream traces(){return IntStream.range(0,32);}
    @ParameterizedTest @MethodSource("traces") void originalConcreteDevicesChainsAndSupportCallbacks(int trace)throws Exception{
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/deployable.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        try(var h=new LifeHarness(GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",0.)))){
            var ports=new BlastPorts(h);ports.build=false;var wire=new ArrayList<String>();h.destination=new LifeRuntime.Messages(){private void capture(String lane,GamePacket p){if(p.id()==5||p.id()==46){wire.add(lane+":"+HexFormat.of().formatHex(p.encode()));ports.events.add("wire:"+lane+":"+p.id());}}public void broadcast(GamePacket p){capture("B",p);}public void owner(int id,GamePacket p){capture("O"+id,p);}public boolean inGame(int id){return true;}};
            for(int id=0;id<3;id++){h.admit(id,id==1?3:2);var p=h.runtime.player(id);p.body().position(new Vector(36+id*3,36,id==1?95:97));p.orientation(1,0,0);p.health(trace%3==0?100:1000);}
            var saved=new ArrayList<MapEntity>();
            for(var r:rows){if(!r[0].equals("init")||Integer.parseInt(r[1])!=trace)continue;var a=r[3].split(",");int kind=Integer.parseInt(a[1]);var spec=new DeviceBlast(0,number(a[4]),0,1,new int[]{13,15,14,36}[kind],number(a[5]),a[9].equals("1"),number(a[6]),number(a[7]),a[8].equals("-")?null:Integer.valueOf(a[8]),a[10].equals("1"));double health=number(a[3]);var p=new EntityRegistry.Placement();p.behavior=switch(kind){case 0->new GraveBehavior(spec,.5,trace%2==1?new Vector(39,36,96):null);case 1->new TimedExplosiveBehavior(spec,.5,health);case 2->new ProximityMineBehavior(spec,health,2,2.5,.25,3,-.5);case 3->new RemoteChargeBehavior(spec,health);default->throw new AssertionError(kind);};int index=Integer.parseInt(r[2]);p.face=Integer.parseInt(a[2]);p.playerId=0;p.supportCell=new EntityRegistry.Cell(39+index,36,97);h.world.set(new MutableWorld.Cell(39+index,36,97),true,0x406080,null);saved.add(ports.registry.place(kind==3?38:9,39+index,36,97,p));}
            h.runtime.player(0).chargeIds(List.of(0,1,2,0));
            try(var devices=ports.bindDevices()){
                assertSame(devices,ports.devices);
                for(var r:rows){if(r[0].equals("init")||Integer.parseInt(r[0])!=trace)continue;h.monotonic+=.25;h.wall+=.25;ports.events.clear();wire.clear();int step=Integer.parseInt(r[1]),op=Integer.parseInt(r[2]),index=Integer.parseInt(r[3]);var e=saved.get(index);var ctx=ports.context();String result="-";
                    switch(op){case 0,3->result=Long.toString(ports.registry.tick(ctx,op==3?1:null));case 1->ports.registry.damageEntity(e.id(),1,trace%4==0?null:h.runtime.player(trace%3),ctx);case 2->{if(trace%2==0)h.world.destroyBlocks(List.of(new MutableWorld.Cell(39+index,36,97),new MutableWorld.Cell(39+(index+1)%3,36,97)));else h.world.set(new MutableWorld.Cell(39+index,36,97),false,0,null);}case 4->{var player=h.runtime.player(1);player.body().position(new Vector(39.5,36,95));player.motion().controls(player.motion().snapshot().movement()^32,0);}case 5->result=Integer.toString(ports.registry.supportRemoved(new EntityRegistry.Cell(39+index,36,97),ctx));case 6->{if(e.behavior instanceof RemoteChargeBehavior b)b.detonate(e,ctx);}case 7->{if(step==15)ports.registry.clear();}default->throw new AssertionError(op);}
                    String where="trace "+trace+" step "+step;assertEquals(r[4],result,where+" result");var state=saved.stream().map(x->x.id()+","+(x.alive?1:0)+","+bits(x.behavior instanceof DamageableEntityBehavior b?b.health():0)+","+(x.supportCell==null?"-":x.supportCell.x()+":"+x.supportCell.y()+":"+x.supportCell.z())).toList();assertEquals(r[5],String.join(";",state),where+" entity state");var players=h.runtime.players().stream().map(p->p.id()+","+p.health()+","+(p.alive()?1:0)+","+vector(PlayerMotion.clipped(p.body().state().velocity()))).toList();assertEquals(r[6],String.join(";",players),where+" players");var present=ports.registry.all().stream().map(x->Integer.toString(x.id())).toList();assertEquals(r[7],present.isEmpty()?"-":String.join(",",present),where+" registry");var charges=h.runtime.player(0).chargeIds().stream().map(Object::toString).toList();assertEquals(r[8],charges.isEmpty()?"-":String.join(",",charges),where+" owner list");assertEquals(r[9],ports.events.isEmpty()?"-":String.join(";",ports.events),where+" callbacks");assertEquals(r[10],wire.isEmpty()?"-":String.join("|",wire),where+" wire");
                }
            }
        }
    }
}
