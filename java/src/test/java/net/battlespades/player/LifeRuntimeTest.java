// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.stream.Collectors;
import net.battlespades.physics.PlayerPhysics;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LifeRuntimeTest {
    @Test void originalDeathAndRoundLifecycleTrace()throws Exception{
        try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,3);h.admit(2,0);int row=0;
            for(var r:LifeGoldenTest.rows("runtime")){
                h.wire.clear();String[] a=r[1].split(",");switch(r[0]){
                    case "die" -> h.runtime.die(Integer.parseInt(a[0]),a[1].equals("-1")?null:Integer.valueOf(a[1]),Integer.parseInt(a[2]));
                    case "stage" -> h.runtime.player(Integer.parseInt(a[0])).stage(new ClassCatalog.Selection(Integer.parseInt(a[1]),List.of(5,6,23,69),List.of(),List.of()));
                    case "advance" -> h.advance(Double.parseDouble(a[0]));
                    case "allow" -> {if(a[1].equals("0"))h.denied.add(Integer.parseInt(a[0]));else h.denied.remove(Integer.parseInt(a[0]));}
                    case "fail" -> {if(a[1].equals("1"))h.failing.add(Integer.parseInt(a[0]));else h.failing.remove(Integer.parseInt(a[0]));}
                    case "delay" -> h.delay=Double.parseDouble(a[0]);
                    default -> fail(r[0]);
                }
                String actual=h.runtime.players().stream().map(p->{var s=h.runtime.score(p.id());return p.id()+":"+p.generation()+","+(p.alive()?1:0)+","+(p.spawned()?1:0)+","+LifeGoldenTest.bits(p.deathTime())+","+p.selection().classId()+","+p.health()+","+s.kills()+","+s.deaths()+","+s.streak()+","+s.lastKilledBy()+","+p.inventory().turrets();}).collect(Collectors.joining(";"));
                String context=row+++" "+r[0]+" "+r[1];assertEquals(r[2],actual,context);assertEquals(r[3],h.wire.isEmpty()?"-":h.wire.stream().map(p->Integer.toString(p.id())).collect(Collectors.joining(",")),context);
                assertEquals(r[4],h.runtime.players().stream().map(p->p.id()+":"+(p.lastDeath()==null?"-":HexFormat.of().formatHex(p.lastDeath().encode()))).collect(Collectors.joining(";")),context);
            }
            assertTrue(h.events.contains("failed:0"));
        }
    }
    @Test void pendingSelectionIsAtomicAndBodyIsFreshAcrossDeath(){try(var h=new LifeHarness()){
        h.admit(0,2);var p=h.runtime.player(0);var old=p.body();old.velocity(new PlayerPhysics.Vector(1,2,3));p.inventory().reloadState(true,200);p.stage(new ClassCatalog.Selection(3,List.of(5,6,69,72),List.of(),List.of()));assertEquals(0,p.selection().classId());
        h.events.clear();assertTrue(h.runtime.die(0,null,10));assertTrue(h.events.contains("delay:false"));assertFalse(h.runtime.die(0,null,10));assertEquals(100,p.health());assertEquals(new PlayerPhysics.Vector(1,2,3),old.state().velocity());assertFalse(p.inventory().reloading());h.advance(1.5);
        assertNotSame(old,p.body());assertEquals(new PlayerPhysics.Vector(0,0,0),p.body().state().velocity());assertEquals(3,p.selection().classId());assertEquals(69,p.jetpack());assertEquals(72,p.parachute());assertTrue(p.pendingSelection().isEmpty());assertTrue(h.events.contains("prepare:0:3"));assertEquals(4,p.inventory().turrets());
    }}
    @Test void firstLifeDoesNotDoubleGrantAndDeadJoinRepairsOwnerOnce(){try(var h=new LifeHarness()){
        var initial=h.admit(0,2);assertEquals(List.of(5,69),initial.afterCreate().stream().map(GamePacket::id).toList());assertEquals(2,h.runtime.player(0).inventory().turrets());
        h.denied.add(1);var dead=h.admit(1,3);assertEquals(1,((LifePackets.KillAction)dead.afterCreate().getFirst()).respawnTime());assertFalse(h.runtime.player(1).alive());assertEquals(0,h.runtime.player(1).generation());h.denied.remove(1);h.advance(1.5);
        assertEquals(List.of(28,5,69),h.runtime.reveal(1).stream().map(GamePacket::id).toList());assertTrue(h.runtime.reveal(1).isEmpty());
        h.admit(2,0);assertFalse(h.runtime.player(2).alive());assertThrows(IllegalStateException.class,()->h.runtime.player(2).body());
    }}
    @Test void protectionUsesMonotonicClockAndNaNCapDoesNotEraseDuration(){
        var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_SPAWN_PROTECTION_TIME",3.0));try(var h=new LifeHarness(rules)){h.admit(0,2);var p=h.runtime.player(0);assertEquals(3,p.protectionRemaining(100));p.protectionCap(Double.NaN);assertEquals(2,p.protectionRemaining(101));p.protectionCap(1.5);assertEquals(0.5,p.protectionRemaining(101));assertTrue(p.endProtection(101));assertFalse(p.endProtection(101));}
    }
    @Test void disconnectForgetsBeforeIdReuse(){try(var h=new LifeHarness()){h.admit(0,2);var old=h.runtime.player(0);h.events.clear();h.runtime.disconnected(0);assertEquals(List.of("input-forget:0","forget:0"),h.events);h.admit(0,2);assertNotSame(old,h.runtime.player(0));assertEquals(1,h.runtime.player(0).generation());assertEquals(0,h.runtime.score(0).kills());}}
}
