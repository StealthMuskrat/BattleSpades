// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
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

class StandardMatchFactoryTest {
    @TempDir Path temp;
    private static SceneConfig config(){return new SceneConfig("Factory",27015,0,ModeCatalog.get("tdm"),"First",false,false,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());}
    private static ApplicationHost host(HostedSceneState state)throws Exception{return new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));}
    private StandardMatchFactory factory(OwnerLoop loop,StandardPorts ports,org.tomlj.TomlTable raw,List<StandardMatchFactory.Creation> created){return new StandardMatchFactory(config(),raw,ports,new SourceRandom(0),loop,List.of("First","Second"),candidate->{created.add(candidate);return new StandardMatchFactory.Ports(ports,ports,ports,ports);});}
    @Test void immutableCandidateMetadataSurvivesDeletedSourcesSameChecksumAndRollback()throws Exception{
        StandardPorts.writeMap(temp.resolve("First.vxl"));Files.copy(temp.resolve("First.vxl"),temp.resolve("Second.vxl"));Files.writeString(temp.resolve("First.json"),"{\"gravity\":0.5}");Files.writeString(temp.resolve("Second.json"),"{\"gravity\":1.5}");
        var created=new ArrayList<StandardMatchFactory.Creation>();var ports=new StandardPorts();
        try(var worker=Executors.newSingleThreadExecutor();var loop=new OwnerLoop(0,1024,256,error->fail(error))){
            var preflight=new WorldPreflight(temp,worker);var first=preflight.prepare("First","tdm").toCompletableFuture().get(4,TimeUnit.SECONDS);var second=preflight.prepare("Second","arena").toCompletableFuture().get(4,TimeUnit.SECONDS);Files.delete(temp.resolve("First.vxl"));Files.delete(temp.resolve("Second.vxl"));Files.delete(temp.resolve("First.json"));Files.delete(temp.resolve("Second.json"));
            var factory=factory(loop,ports,org.tomlj.Toml.parse("[modes.tdm]\ntime_limit=0\n"),created);
            try(var state=new HostedSceneState("First","tdm",first,1,10000,factory.modes());var host=host(state)){
                state.attach(host);var original=(StandardMatchDomain)state.domain();var old=state.snapshot();assertSame(first.assembly().metadata(),created.getFirst().metadata());assertEquals(.5,original.runtime().infantry().settings().gravity());
                state.install("Second","arena",second);var next=(StandardMatchDomain)state.domain();assertEquals(original.world().sourceCrc(),next.world().sourceCrc());assertSame(second.assembly().metadata(),created.get(1).metadata());assertEquals(1.5,next.runtime().infantry().settings().gravity());assertInstanceOf(ArenaMode.class,next.mode());assertEquals("arena",created.get(1).code());assertSame(next.scene(),host.transitionScene());
                state.restore(old);state.finish();assertThrows(IllegalStateException.class,()->next.runtime().players(1));assertSame(original,state.domain());assertEquals(.5,original.runtime().infantry().settings().gravity());assertEquals(1,ports.closes);
            }assertEquals(2,ports.closes);
        }
    }
    @Test void connectedPlayerRuntimeSimulatesBotsGatesLoadingHumansAndReplacesSnapshotIdentity()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("First.vxl"),"tdm");var ports=new StandardPorts();var created=new ArrayList<StandardMatchFactory.Creation>();
        try(var loop=new OwnerLoop(0,1024,256,error->fail(error))){var factory=factory(loop,ports,org.tomlj.Toml.parse("[modes.tdm]\ntime_limit=0\n"),created);
            try(var state=new HostedSceneState("First","tdm",prepared,1,10000,factory.modes());var host=host(state)){
                state.attach(host);var match=(StandardMatchDomain)state.domain();match.mode().start();
                for(int id=0;id<2;id++)match.scene().admit(id,new SimplePackets.NewPlayerConnection(id==0?2:3,0,0,0,"P"+id),null,0);
                var bot=match.lives().player(0);var loading=match.lives().player(1);bot.bot(true);match.input(0,LiveClientInput.decode(new ClientInput(100,127,false,6,1,0,0,0,1,24,0).encode()),1);match.input(1,LiveClientInput.decode(new ClientInput(200,127,false,6,1,0,0,0,1,24,0).encode()),1);match.runtime().players(1);
                assertEquals(100,bot.inputTimeline().appliedLabel());assertNull(loading.inputTimeline().appliedLabel());assertEquals(1,ports.moves);assertNull(match.binding().row(loading).profileStats);assertNotNull(match.binding().row(bot).roundStats.lastPosition);assertTrue(ports.log.contains("observe:0"));assertFalse(ports.log.contains("observe:1"));
                var before=(WorldUpdate)PacketCodec.decode(match.runtime().replication().build(2,ports.monotonic,null,null));assertEquals(2,before.players().size());match.disconnected(0);match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"Reused"),null,0);var replacement=match.lives().player(0);replacement.health(47);
                var after=(WorldUpdate)PacketCodec.decode(match.runtime().replication().build(4,ports.monotonic,null,null));assertEquals(47,after.players().stream().filter(p->p.playerId()==0).findFirst().orElseThrow().health());assertNotSame(bot,replacement);assertEquals(2,match.teams().stream().mapToInt(TeamState::playerCount).sum());assertTrue(ports.errors.isEmpty());match.mode().deactivate().toCompletableFuture().join();
            }
        }
    }
    @Test void sourceSettingsClampsAndFailedFactoryConstructionReleaseCandidateServices()throws Exception{
        var prepared=StandardPorts.prepared(temp.resolve("First.vxl"),"tdm");var raw=org.tomlj.Toml.parse("[teams]\nbalance_check_interval=200.0\nbalance_grace_seconds=-1.0\nbalance_player_cooldown=999999.0\n[network]\nterrain_repair_queue_limit=1\nterrain_repair_delay_ticks=0\n[debug]\ninput_gap_fill_limit=100\n[anticheat]\nbacklog_max_frames=-10\nstarvation_airborne_ticks=-1\n");
        var settings=HostedPlayerRuntime.Settings.from(raw,config(),prepared.assembly().metadata());assertEquals(60,settings.balance().interval());assertEquals(0,settings.balance().grace());assertEquals(86400,settings.balance().cooldown());assertEquals(64,settings.repair().queueLimit());assertEquals(1,settings.repair().delayTicks());assertEquals(9,settings.infantry().gapLimit());assertEquals(0,settings.infantry().backlogFrames());assertEquals(0,settings.infantry().airborneTicks());
        assertThrows(IllegalArgumentException.class,()->HostedPlayerRuntime.Settings.from(org.tomlj.Toml.parse("[teams]\nbalance_grace_seconds=nan\n"),config(),prepared.assembly().metadata()));
        var ports=new StandardPorts();var created=new ArrayList<StandardMatchFactory.Creation>();try(var loop=new OwnerLoop(0,1024,256,error->fail(error))){var factory=factory(loop,ports,org.tomlj.Toml.parse("[modes.tdm]\nscore_limit=\"bad\"\n"),created);assertThrows(IllegalArgumentException.class,()->new HostedSceneState("First","tdm",prepared,1,10000,factory.modes()));assertEquals(1,ports.closes);assertThrows(IllegalStateException.class,()->created.getFirst().prepared().world().revision());}
    }
}
