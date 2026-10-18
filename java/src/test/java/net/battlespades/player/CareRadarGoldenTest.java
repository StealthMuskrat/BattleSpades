// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.GamePacket;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class CareRadarGoldenTest {
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static IntStream traces(){return IntStream.range(0,32);}
    @ParameterizedTest @MethodSource("traces") void originalHealingCooldownUsesAndRadarLifecycle(int trace)throws Exception{
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/care-radar.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        try(var h=new LifeHarness(GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",0.)))){
            var ports=new BlastPorts(h);var wire=new ArrayList<String>();h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){throw new AssertionError("Unexpected global care packet "+p.id());}public void owner(int id,GamePacket p){wire.add(id+":"+HexFormat.of().formatHex(p.encode()));}public boolean inGame(int id){return true;}};
            // Capture only operations below; admission broadcasts are tested by the life fixtures.
            var destination=h.destination;h.destination=null;
            for(int id=0;id<2;id++){h.admit(id,id==0?2:3);var p=h.runtime.player(id);p.body().position(new Vector(id==0&&trace%4==0?39:36+id,36,97));p.maxHealth(trace%5==0?50:100);p.health(25);}
            h.destination=destination;boolean[] hardcore={trace%2==1};var care=new MedpackBehavior.Care(){public boolean hardcore(){return hardcore[0];}public double wall(){return h.wall;}public void heal(PlayerLife player,int amount){h.runtime.heal(player.id(),amount);}};
            var saved=new ArrayList<MapEntity>();
            for(int index=0;index<2;index++){var placement=new EntityRegistry.Placement();placement.behavior=new MedpackBehavior(2,index==0?new int[]{-1,0,25,100}[trace%4]:25,index==0?new int[]{0,1,3}[trace%3]:3,1,care);saved.add(ports.registry.place(30,39+index,36,97,placement));}
            var placement=new EntityRegistry.Placement();placement.playerId=0;placement.supportCell=new EntityRegistry.Cell(42,36,97);placement.behavior=new RadarStationBehavior(2,new double[]{.25,1,45}[trace%3],trace%2==1?1:45);placement.fuse=45;var radar=ports.registry.place(36,42,36,97,placement);saved.add(radar);h.runtime.player(0).radarEntityId(trace%3==0?99:radar.id());h.world.set(new MutableWorld.Cell(42,36,97),true,0x406080,null);
            try(var devices=ports.bindDevices()){
                devices.radarAdded(2);
                for(var r:rows){if(r[0].equals("fault")||Integer.parseInt(r[0])!=trace)continue;int step=Integer.parseInt(r[1]),op=Integer.parseInt(r[2]);h.monotonic+=.5;h.wall+=.5;wire.clear();ports.events.clear();var ctx=ports.context();String result="-";
                    switch(op){case 0,1,8,9,13->{int index=op==13?0:op%8;result=saved.get(index).behavior.onTouch(saved.get(index),h.runtime.player(op==13?1:0),ctx)?"1":"0";}case 2,10->h.runtime.damage(0,1,null,7);case 3,11,15->result=Long.toString(ports.registry.tick(ctx,null));case 4->{if(trace%4==0)h.world.set(new MutableWorld.Cell(42,36,97),false,0,null);}case 5->{if(trace%4==1)ports.registry.damageEntity(radar.id(),50,null,ctx);}case 6->hardcore[0]=!hardcore[0];case 7->h.runtime.player(0).radarEntityId(step==7?radar.id():99);case 12->h.runtime.player(0).health(25);case 14->{if(trace%4==2)((RadarStationBehavior)radar.behavior).onDestroyed(radar,null,ctx);}default->throw new AssertionError(op);}
                    String where="trace "+trace+" step "+step;assertEquals(r[3],result,where+" result");var players=h.runtime.players().stream().map(p->p.health()+","+bits(p.lastDamageWall())+","+bits(p.hardcoreNextHeal())).toList();assertEquals(r[4],String.join(";",players),where+" players");var states=saved.stream().map(e->(e.alive?1:0)+","+(e.behavior instanceof MedpackBehavior b?b.uses():0)+","+bits(((DamageableEntityBehavior)e.behavior).health())+","+bits(e.fuse)).toList();assertEquals(r[5],String.join(";",states),where+" entities");assertEquals(r[6],Integer.toString(devices.radarCount(2)),where+" team count");var slot=h.runtime.player(0).radarEntityId();assertEquals(r[7],slot==null?"-":slot.toString(),where+" owner slot");assertEquals(r[8],ports.events.isEmpty()?"-":String.join(";",ports.events),where+" callbacks");assertEquals(r[9],wire.isEmpty()?"-":String.join("|",wire),where+" wire");
                }
                var fault=rows.stream().filter(r->r[0].equals("fault")&&Integer.parseInt(r[1])==trace).findFirst().orElseThrow();h.wall+=10;h.monotonic+=10;hardcore[0]=true;var player=h.runtime.player(0);player.health(25);var extra=new EntityRegistry.Placement();extra.behavior=new MedpackBehavior(2,25,3,1,care);var pack=ports.registry.place(30,39,36,97,extra);wire.clear();h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){throw new AssertionError();}public void owner(int id,GamePacket p){wire.add(id+":"+HexFormat.of().formatHex(p.encode()));throw new IllegalStateException("Controlled reliable IO fault");}public boolean inGame(int id){return true;}};
                assertThrows(IllegalStateException.class,()->pack.behavior.onTouch(pack,player,ports.context()));assertEquals(fault[2],Integer.toString(player.health()));assertEquals(fault[3],Integer.toString(((MedpackBehavior)pack.behavior).uses()));assertEquals(fault[4],bits(player.hardcoreNextHeal()));assertEquals(fault[5],pack.alive?"1":"0");assertEquals(fault[6],Integer.toString(ports.registry.all().size()));assertEquals(fault[7],String.join("|",wire));

            }
        }
    }
}
