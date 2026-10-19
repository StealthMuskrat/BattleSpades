// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.scene.*;
import net.battlespades.session.*;
import net.battlespades.support.StandardPorts;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HostedEntityRuntimeTest {
    @TempDir Path temp;
    private static final class Harness implements AutoCloseable {
        final StandardPorts ports=new StandardPorts();final OwnerLoop loop=new OwnerLoop(0,1024,256,error->fail(error));
        final HostedSceneState state;final ApplicationHost host;final StandardMatchDomain match;
        Harness(Path path,String settings)throws Exception{
            var prepared=StandardPorts.prepared(path,"tdm");var config=new SceneConfig("Entities",27015,0,ModeCatalog.get("tdm"),"Entities",false,false,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            var raw=org.tomlj.Toml.parse("[modes.tdm]\ntime_limit=0\n"+settings);var factory=new StandardMatchFactory(config,raw,ports,new SourceRandom(0),loop,List.of("Entities"),candidate->new StandardMatchFactory.Ports(ports,ports,ports,ports,ports));
            state=new HostedSceneState("Entities","tdm",prepared,1,10000,factory.modes());host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));state.attach(host);match=(StandardMatchDomain)state.domain();
        }
        PlayerLife admit(int id,int team){match.scene().admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"P"+id),null,0);return match.lives().player(id);}
        public void close()throws java.io.IOException{try{host.close();}finally{try{state.close();}finally{loop.close();}}}
    }
    @Test void sourceContextUsesWallClockAndAliveBodiesWhileBudgetRotatesAndReportsSkips()throws Exception{
        var settings=HostedEntityRuntime.Settings.from(org.tomlj.Toml.parse("[network]\nentity_tick_batch_limit=1\n[world]\nentities_wire_ready=false\n"));assertEquals(64,settings.tickLimit());assertFalse(settings.wireReady());assertThrows(IllegalArgumentException.class,()->HostedEntityRuntime.Settings.from(org.tomlj.Toml.parse("[network]\nentity_tick_batch_limit=1.5\n")));
        try(var h=new Harness(temp.resolve("Entities.vxl"),"[network]\nentity_tick_batch_limit=1\n[world]\nentities_wire_ready=false\n")){
            h.match.mode().start();var runtime=h.match.entities();var human=h.admit(0,2);var bot=h.admit(1,3);bot.bot(true);h.admit(2,0);h.admit(3,3);h.match.lives().die(3,null,0);
            var context=runtime.context();assertEquals(List.of(human,bot),context.players());assertEquals(h.ports.wall,context.now());assertNotEquals(h.ports.monotonic,context.now());assertEquals(1.0/60,context.dt());assertTrue(runtime.reveal(0).isEmpty());
            runtime.registry().clear();int[] ticks=new int[67];for(int i=0;i<ticks.length;i++){int index=i;var p=new EntityRegistry.Placement();p.behavior=new EntityBehavior(){public void onTick(MapEntity e,double dt,EntityContext c){ticks[index]++;}};runtime.registry().place(3,400,400,100,p);}
            runtime.tick();runtime.tick();assertEquals(6,h.ports.skippedEntities);assertEquals(128,Arrays.stream(ticks).sum());assertTrue(Arrays.stream(ticks).allMatch(n->n>0));
            runtime.close();assertThrows(IllegalStateException.class,runtime::context);assertDoesNotThrow(()->h.match.world().revision());try(var lease=h.match.world().observeRemovals(cell->{})){assertNotNull(lease);}
        }
    }
    @Test void mapResourcesRebuildPreservesForeignEntitiesAndUsesCanonicalRefillAwardsAndRespawnClock()throws Exception{
        try(var h=new Harness(temp.resolve("Entities.vxl"),"")){
            h.match.mode().start();var runtime=h.match.entities();assertEquals(1,h.ports.resourceRebuilds);assertTrue(h.ports.log.indexOf("map-resources")<h.ports.log.indexOf("resources"));var registry=runtime.registry();var foreign=new EntityRegistry.Placement();foreign.kind="objective";var retained=registry.place(1,400,400,100,foreign);var old=registry.all().stream().filter(e->e.kind.startsWith("map_")).map(MapEntity::id).toList();runtime.rebuild();assertSame(retained,registry.get(retained.id()));assertTrue(old.stream().allMatch(id->registry.get(id)==null));
            var player=h.admit(0,2);var health=registry.all().stream().filter(e->e.type==4).findFirst().orElseThrow();player.health(25);player.body().position(new Vector(health.x,health.y,health.z));runtime.tick();assertEquals(player.maxHealth(),player.health());assertFalse(health.alive);assertEquals(h.ports.wall+GameRules.serverDefaults().number("RULE_CRATES_SPAWN_TIME"),health.respawnAt);assertEquals(1L,h.match.binding().row(player).roundStats.awards.get(2));assertTrue(h.ports.log.contains("crate:0:2"));
            player.body().position(new Vector(500,500,10));h.ports.wall=health.respawnAt;runtime.tick();assertTrue(health.alive);assertEquals(0,health.respawnAt);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void canonicalRemovalDetonatesSupportedChargeThroughRealBlastAndCloseReleasesObserver()throws Exception{
        try(var h=new Harness(temp.resolve("Entities.vxl"),"")){
            h.match.mode().start();var runtime=h.match.entities();var player=h.admit(0,2);player.body().position(new Vector(12.5,13.5,98));var cell=new MutableWorld.Cell(12,13,100);h.match.world().set(cell,true,0x123456,null);
            var p=new EntityRegistry.Placement();p.kind="deployable";p.playerId=0;p.supportCell=new EntityRegistry.Cell(12,13,100);p.behavior=new RemoteChargeBehavior(new DeviceBlast(0,30,0,1,15,16,true,0,0,null,true),10);var charge=runtime.registry().place(11,12,13,99,p);player.chargeIds(List.of(charge.id()));int before=player.health();h.match.world().set(cell,false,0,null);
            assertFalse(charge.alive);assertNull(runtime.registry().get(charge.id()));assertTrue(player.chargeIds().isEmpty());assertTrue(player.health()<before);assertEquals(List.of("begin","stimulus:0","end"),h.ports.blastLog);assertTrue(h.ports.errors.isEmpty());
            runtime.close();try(var lease=h.match.world().observeRemovals(removed->{})){assertNotNull(lease);h.match.world().set(cell,true,0x123456,null);h.match.world().set(cell,false,0,null);}assertThrows(IllegalStateException.class,runtime::tick);
        }
    }
}
