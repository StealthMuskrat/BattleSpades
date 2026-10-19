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

class HostedCombatRuntimeTest {
    @TempDir Path temp;
    private static final class Harness implements AutoCloseable {
        final StandardPorts ports=new StandardPorts();final OwnerLoop loop=new OwnerLoop(0,1024,256,error->fail(error));final HostedSceneState state;final ApplicationHost host;final StandardMatchDomain match;
        Harness(Path path,boolean friendly)throws Exception{
            var prepared=StandardPorts.prepared(path,"tdm");var config=new SceneConfig("Combat",27015,0,ModeCatalog.get("tdm"),"Combat",false,friendly,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());var raw=org.tomlj.Toml.parse("[modes.tdm]\ntime_limit=0\n[network]\nlag_compensation_enabled=false\n");var factory=new StandardMatchFactory(config,raw,ports,new SourceRandom(0),loop,List.of("Combat"),candidate->new StandardMatchFactory.Ports(ports,ports,ports,ports,ports,ports));
            state=new HostedSceneState("Combat","tdm",prepared,1,10000,factory.modes());host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));state.attach(host);match=(StandardMatchDomain)state.domain();match.mode().start();
        }
        PlayerLife admit(int id,int team,Vector at){match.scene().admit(id,new SimplePackets.NewPlayerConnection(team,5,0,0,"P"+id),null,0);var p=match.lives().player(id);p.body().position(at);p.orientation(1,0,0);return p;}
        void fire(PlayerLife p,int label,int tick,Vector origin){match.frameLoop(tick);p.inputTimeline().receive(new LiveClientInput(label,127,false,6,1,0,0,0,0,24,0),false,false,p.aim(),tick,match.world().topologyVersion(),false);match.packet(p.id(),new ShootPacket(label,254,0,new Vector3f((float)origin.x(),(float)origin.y(),(float)origin.z()),new Vector3f(1,0,0),32767,32767,true,false,0));}
        public void close()throws java.io.IOException{try{host.close();}finally{try{state.close();}finally{loop.close();}}}
    }
    @Test void ownedShotTargetsFriendlyFireWithoutChangingProfileOpponentRelationAndRejectsBeforeAmmo()throws Exception{
        for(boolean friendly:new boolean[]{false,true})try(var h=new Harness(temp.resolve("Combat"+friendly+".vxl"),friendly)){
            var p=h.admit(0,2,new Vector(10,10,97));var ally=h.admit(1,2,new Vector(13,10,96.5));var enemy=h.admit(2,3,new Vector(16,10,96.5));p.fire().tool(6,true);p.inventory().ammo(8,100);h.fire(p,100,1000,p.position());
            assertEquals(7,p.inventory().ammo().clip());assertEquals(friendly?30:100,ally.health());assertEquals(friendly?100:30,enemy.health());assertEquals(1,p.combatStatistics().snapshot().shots());assertEquals(1,p.combatStatistics().snapshot().hits());var profile=h.match.binding().row(p).profileStats;assertEquals(1,profile.values.get(1006).count());assertEquals(friendly?0:1,profile.values.containsKey(2006)?profile.values.get(2006).count():0);assertTrue(h.ports.log.contains("shot:0"));assertFalse(h.ports.packets.stream().anyMatch(ShootPacket.class::isInstance));
            long clears=h.ports.log.stream().filter(s->s.equals("clear-disguise:0")).count();h.ports.monotonic+=3;h.fire(p,280,1180,new Vector(300,300,50));assertEquals(7,p.inventory().ammo().clip());assertEquals(1,profile.values.get(1006).count());assertEquals(clears,h.ports.log.stream().filter(s->s.equals("clear-disguise:0")).count());assertTrue(h.ports.validation.stream().anyMatch(e->e.kind().equals("shot_origin_far")));
            var old=h.match.combatRuntime();old.close();assertThrows(IllegalStateException.class,()->old.shots());
        }
    }
    @Test void hostedTerrainCollapsesCreditProfilesAndQueueRepairAndActualEntityShotsUseOwnedRegistry()throws Exception{
        try(var h=new Harness(temp.resolve("Combat.vxl"),false)){
            var p=h.admit(0,2,new Vector(10,13.5,99.5));p.fire().tool(6,true);p.inventory().ammo(8,100);var root=new MutableWorld.Cell(12,13,99);var cap=new MutableWorld.Cell(12,13,98);for(int z=100;z<240;z++)h.match.world().set(new MutableWorld.Cell(12,13,z),true,0x123456,null);h.match.world().set(root,true,0x123456,null);h.match.world().set(cap,true,0x123456,null);
            for(int i=0;i<3;i++){h.ports.monotonic+=3;h.fire(p,100+i*180,1000+i*180,p.position());}
            assertFalse(h.match.world().solid(root));assertFalse(h.match.world().solid(cap));var row=h.match.binding().row(p);assertEquals(2,row.profileStats.values.get(158).count());assertEquals(2L,row.roundStats.awards.get(10));assertEquals(1L,row.roundStats.awards.get(12));assertEquals(List.of(6),h.ports.blockTools);assertEquals(1,h.match.runtime().repair().pending());var event=h.match.events().snapshot().stream().filter(e->e.name().equals("on_blocks_destroyed")).findFirst().orElseThrow();assertSame(row,event.arguments().getFirst());assertEquals(List.of(new BlockDamageModel.Cell(12,13,99)),event.arguments().get(1));assertEquals(false,event.arguments().get(2));
            var settings=new EntityRegistry.Placement();settings.kind="deployable";settings.playerId=p.id();settings.behavior=new RemoteChargeBehavior(new DeviceBlast(p.id(),30,0,1,15,16,true,0,0,null,true),1);var charge=h.match.entities().registry().place(38,13,13,99,settings);p.chargeIds(List.of(charge.id()));h.ports.monotonic+=3;h.fire(p,640,1540,p.position());assertNull(h.match.entities().registry().get(charge.id()));assertTrue(p.chargeIds().isEmpty());assertTrue(h.ports.blastLog.isEmpty());assertEquals(1,h.ports.blockTools.size());assertTrue(h.ports.errors.isEmpty());
        }
    }
}
