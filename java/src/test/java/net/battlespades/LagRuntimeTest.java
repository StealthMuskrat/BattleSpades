// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.LagCompensation;
import net.battlespades.combat.LagCompensation.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LagRuntimeTest {
    @Test void originalShotPacketBytesAndReservedFlags()throws IOException {
        List<String> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/shoot.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().toList();}
        for(String line:rows){var a=line.split("\t");int i=Integer.parseInt(a[0]);var position=switch(i%3){case 0->new Vector3f(.125f,-.25f,100.5f);case 1->new Vector3f(-0f,1e-30f,-1e30f);default->new Vector3f(1,2,3);};
            var packet=new ShootPacket(switch(i%3){case 0->0;case 1->Integer.MAX_VALUE;default->Integer.MIN_VALUE;},i%256,123456,position,new Vector3f(1,-.5f,.125f),new int[]{-32768,0,32767}[i%3],-1,(i&1)!=0,(i&2)!=0,255-i%256);
            assertEquals(a[1],HexFormat.of().formatHex(packet.encode()),"source encode "+i);
            assertEquals(a[3],HexFormat.of().formatHex(PacketCodec.decode(HexFormat.of().parseHex(a[2])).encode()),"source decode "+i);
        }
    }
    @Test void readOnlyBodiesAndNestedScopesRestoreEvenOnFailure(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var target=p.lagTarget();target.history().record(target,10);p.body().position(new Vector(37,36,97));target.history().record(target,11);
            var physics=p.body().state();var life=p.health();var input=p.inputTimeline().state();var inventory=p.inventory().ammo();
            var first=new Context(12,10,0,0,0,null);var second=new Context(12,10.5,0,0,0,null);var owner=new ShotOwner();
            assertSame(target,owner.body(target));
            try(var outer=owner.install(first)){
                assertEquals(36,owner.body(target).position().x());var cached=owner.body(target);assertSame(cached,owner.body(target));assertSame(target,cached.target());
                try(var inner=owner.install(second)){assertSame(second,inner.context());assertEquals(36.5,owner.body(target).position().x());assertThrows(IllegalStateException.class,outer::close);}
                assertSame(cached,owner.body(target));
                assertThrows(IllegalArgumentException.class,()->{try(var empty=owner.install(null)){assertNull(empty.context());assertSame(target,owner.body(target));throw new IllegalArgumentException("controlled handler failure");}});
                assertSame(cached,owner.body(target));
            }
            assertSame(target,owner.body(target));assertEquals(physics,p.body().state());assertEquals(life,p.health());assertEquals(input,p.inputTimeline().state());assertEquals(inventory,p.inventory().ammo());
        }
    }
    @Test void diagnosticFailureCannotRejectARewindAndDisabledRewindRetainsLastObservation(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);p.flight().roundTripMillis(100.0);
            var packet=new ShootPacket(10,254,1,new Vector3f(0,0,0),new Vector3f(1,0,0),32767,32767,true,true,255);
            var context=LagCompensation.prepare(Settings.defaults(),100,1.0/60,p,packet,event->{throw new IllegalStateException("log sink unavailable");});
            assertNotNull(context);assertEquals(100,context.rewindMillis());assertEquals(100,p.lastLagRewindMillis().orElseThrow());
            assertNull(LagCompensation.prepare(new Settings(false,250,50,0,400,2),100,1.0/60,p,packet,event->fail()));
            assertEquals(100,p.lastLagRewindMillis().orElseThrow());assertTrue(p.alive());assertEquals(100,p.health());
        }
    }
}
