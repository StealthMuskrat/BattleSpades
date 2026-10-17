// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.battlespades.protocol.*;
import net.battlespades.session.*;
import net.battlespades.support.FixtureScene;
import net.battlespades.support.LifeHarness;
import net.battlespades.support.InfantryPorts;
import net.battlespades.simulation.FixedStepRuntime;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.transport.enet.*;
import net.battlespades.world.*;
import net.battlespades.scene.*;
import net.battlespades.rules.GameRules;
import net.battlespades.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationIntegrationTest {
    @TempDir Path temp;
    private static final class Client implements AutoCloseable {
        final UdpEndpoint udp;
        final SocketAddress server;
        final List<GamePacket> packets=new ArrayList<>();
        final List<Integer> commands=new ArrayList<>();
        final Set<Integer> received=new HashSet<>();
        int peerId=-1,outSession,inSession,reliable,group,reason=-1;
        byte[] key=new byte[0];
        int flightCapability;
        byte[] negotiatedFlight;
        Client(SocketAddress server) throws Exception {this.server=server;udp=new UdpEndpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),4096);}
        void connect() throws Exception {
            byte[] p=ByteBuffer.allocate(44).putShort((short)0).put((byte)255).put((byte)255).putInt(1400).putInt(65536).putInt(1)
                    .putInt(0).putInt(0).putInt(5000).putInt(2).putInt(2).putInt(12345).putInt(168).array();
            udp.send(server,new EnetDatagram(4095,0,0,List.of(new EnetDatagram.Command(0x82,255,1,p,new byte[0]))).encode());
        }
        void send(GamePacket packet,boolean unsequenced) throws Exception {
            byte[] body=packet.encode();for(int i=0;key.length>0 && i<body.length;i++) body[i]^=key[i%key.length];
            wire(new Wire.Writer().u8(0x30).bytes(body).toByteArray(),unsequenced);
        }
        void wire(byte[] data,boolean unsequenced) throws Exception {
            EnetDatagram.Command c=unsequenced?new EnetDatagram.Command(0x49,0,0,ByteBuffer.allocate(4).putShort((short)++group).putShort((short)data.length).array(),data):EnetDatagram.reliable(0,++reliable,data);
            udp.send(server,new EnetDatagram(peerId,outSession,reliable,List.of(c)).encodeCompressed());
        }
        void drain() throws Exception {
            while(true) {
                var incoming=udp.receive();if(incoming.isEmpty()) return;var datagram=EnetDatagram.decode(incoming.orElseThrow().payload());
                for(var c:datagram.commands()) {
                    if(c.id()==3) {var p=ByteBuffer.wrap(c.parameters());peerId=Short.toUnsignedInt(p.getShort());inSession=p.get()&255;outSession=p.get()&255;}
                    if((c.flags()&128)!=0) udp.send(server,new EnetDatagram(peerId,outSession,-1,List.of(EnetDatagram.acknowledge(c.channel(),c.reliableSequence(),c.reliableSequence(),datagram.sentTime()))).encode());
                    if(c.id()==4) {reason=ByteBuffer.wrap(c.parameters()).getInt();continue;}
                    if(c.id()!=6 && c.id()!=7 && c.id()!=9) continue;
                    if(c.id()==6 && !received.add(c.reliableSequence())) continue;
                    byte[] wire=c.payload();byte[] body=Lzf.decode(Arrays.copyOfRange(wire,1,wire.length),65536);
                    if(body[0]==114&&flightCapability>0){int length=flightCapability>=2?24:20;negotiatedFlight=Arrays.copyOfRange(body,body.length-length,body.length);body=Arrays.copyOf(body,body.length-length);}
                    packets.add(PacketCodec.decode(body));commands.add(c.id());
                }
            }
        }
        boolean has(int id) {return packets.stream().anyMatch(p->p.id()==id);}
        @Override public void close() throws java.io.IOException {udp.close();}
    }
    private static final class Loop {
        final ApplicationHost host;final List<Client> clients=new ArrayList<>();long now;
        Loop(ApplicationHost host) {this.host=host;}
        void step() throws Exception {host.service(++now,(int)(now*60/1000));for(var client:clients) client.drain();}
        void until(BooleanSupplier condition) throws Exception {long deadline=System.nanoTime()+4_000_000_000L;while(!condition.getAsBoolean()) {assertTrue(System.nanoTime()<deadline,"UDP application loop timed out");step();Thread.onSpinWait();}}
        void ticketAndLoad(Client client,FixtureScene scene) throws Exception {ticketAndLoad(client,scene.world());}
        void ticketAndLoad(Client client,MutableWorld world) throws Exception {
            ticketAndLoad(client,world,0);
        }
        void ticketAndLoad(Client client,MutableWorld world,int capability) throws Exception {
            client.flightCapability=capability;
            clients.add(client);client.connect();until(()->client.peerId>=0);client.send(new LoadingPackets.SteamSessionTicket(new byte[]{3,7,19},capability==0?new byte[0]:new byte[]{'B','S','C','F',(byte)capability}),false);client.key=new byte[]{3,7,19};
            until(()->client.has(114));client.send(new SimplePackets.MapDataValidation(world.sourceCrc()),false);until(()->client.has(45));
        }
    }
    @Test void realUdpTicketMapStateSelectionAndCanonicalFirstInputCatchup() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var scene=new FixtureScene(1,64);var settings=FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO));
            try(scene;var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,settings,scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())) {
                var loop=new Loop(host);loop.ticketAndLoad(client,scene);assertTrue(client.has(55));assertTrue(client.has(59));assertFalse(client.has(57));assertEquals(1,host.occupiedSlots());
                scene.world().set(new MutableWorld.Cell(1,2,3),true,0x654321,9.0);
                client.send(new SimplePackets.NewPlayerConnection(1,5,0,0,"Source"),false);loop.until(()->client.has(28));
                var input=new ClientInput(100,0,false,7,1,0,0,0,0,0,0);
                loop.until(()->{try {client.send(input,true);}catch(Exception e) {throw new IllegalStateException(e);}return !scene.inputs.isEmpty();});
                assertTrue(client.has(33));assertTrue(client.has(7));assertTrue(client.has(38));assertEquals(0,scene.world().activeLeases());assertFalse(client.has(14));
                assertEquals(1,host.broadcast(new SimplePackets.ChatMessage(0,0,"live"),Delivery.RELIABLE));loop.until(()->client.packets.stream().anyMatch(p->p instanceof SimplePackets.ChatMessage chat && chat.value().equals("live")));
                client.send(new SimplePackets.ClockSync(12345,0),true);loop.until(()->client.has(0));int index=java.util.stream.IntStream.range(0,client.packets.size()).filter(i->client.packets.get(i).id()==0).findFirst().orElseThrow();assertEquals(9,client.commands.get(index));
            }
            assertTrue(scene.players.isEmpty());assertEquals(List.of(0),scene.departed);
        });
    }
    @Test void malformedPeerDoesNotInterruptAnotherRealUdpLoader() {
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            try(var scene=new FixtureScene(1,64);var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));
                    var good=new Client(host.address());var bad=new Client(host.address())) {
                var loop=new Loop(host);loop.ticketAndLoad(good,scene);loop.clients.add(bad);bad.connect();loop.until(()->bad.peerId>=0);bad.wire(new byte[]{0x31,31,0},false);loop.until(()->bad.reason>=0);
                assertEquals(13,bad.reason);assertEquals(1,host.occupiedSlots());good.send(new SimplePackets.NewPlayerConnection(1,5,0,0,"good"),false);loop.until(()->good.has(28));assertEquals(-1,good.reason);
            }
        });
    }
    @Test void configuredMetadataAndNormalizedSelectionCrossRealUdpSession(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var rules=GameRules.serverDefaults().apply(Map.of("RULE_CHARACTER_SPEED",1.5,"RULE_ENABLE_WEAPON_MINIGUN",false));
            var config=new SceneConfig("Configured UDP",27015,90087911866072064L,ModeCatalog.get("tdm"),"CityOfChicago",false,false,true,200,0x123456,"ArcticBase.txt",new SceneConfig.Team("Blue",0x0040ff),new SceneConfig.Team("Green",0xff2000),rules);
            try(var domain=new FixtureScene(1,64)){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("CityOfChicago",domain.world().sourceCrc()),()->ScenePackets.State.initial(config),domain);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    var loop=new Loop(host);loop.ticketAndLoad(client,domain);
                    var info=(InitialInfo)client.packets.stream().filter(p->p.id()==114).findFirst().orElseThrow();assertEquals("Configured UDP",info.serverName());assertEquals("City of Chicago",info.map().name());assertEquals(List.of(37,38,8).stream().sorted().toList(),info.disabledTools());assertEquals(1.5,info.movementSpeedMultipliers().get(0));
                    var state=(StateData)client.packets.stream().filter(p->p.id()==45).findFirst().orElseThrow();assertEquals(200,state.scoreLimit());assertEquals(0x123456,state.fogColor());assertEquals("Blue",state.team1().name());
                    client.send(new PlayerPackets.SetClassLoadout(255,0,1,List.of(8,51),List.of(),List.of()),false);client.send(new SimplePackets.NewPlayerConnection(2,0,0,1,"Configured"),false);loop.until(()->client.has(28));
                    var create=(PlayerPackets.CreatePlayer)client.packets.stream().filter(p->p.id()==28).findFirst().orElseThrow();assertEquals(0,create.playerId());assertTrue(create.loadout().contains(60));assertFalse(create.loadout().contains(8));assertFalse(create.loadout().contains(51));
                    loop.until(()->{try{client.send(new ClientInput(1,0,false,60,1,0,0,0,0,0,0),true);}catch(Exception e){throw new IllegalStateException(e);}return !domain.inputs.isEmpty();});
                    assertEquals(1,host.broadcast(new SimplePackets.ChatMessage(0,0,"configured live"),Delivery.RELIABLE));assertEquals(1.05f,scene.movement(0).acceleration());
                }
                assertTrue(domain.players.isEmpty());
            }
        });
    }
    @Test void configuredAdmissionNamesTeamsAndClassFallbackCrossRealUdp(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Admission UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",1),new SceneConfig.Team("Green",2),GameRules.serverDefaults());
            try(var domain=new FixtureScene(1,64)){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",domain.world().sourceCrc()),()->ScenePackets.State.initial(config),domain);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,4));
                    var a=new Client(host.address());var b=new Client(host.address());var c=new Client(host.address());var spectator=new Client(host.address())){
                    var loop=new Loop(host);var clients=List.of(a,b,c,spectator);var names=List.of("Admin","Ρlayer","Player","Watcher");
                    for(int i=0;i<clients.size();i++){
                        var client=clients.get(i);loop.ticketAndLoad(client,domain);
                        int reserved=((StateData)client.packets.stream().filter(p->p.id()==45).findFirst().orElseThrow()).playerId();
                        client.send(new PlayerPackets.SetClassLoadout(255,4,1,List.of(7,51),List.of(),List.of()),false);
                        client.send(new SimplePackets.NewPlayerConnection(i==3?0:i==1?1:2,4,3,1,names.get(i)),false);
                        loop.until(()->client.packets.stream().anyMatch(p->p instanceof PlayerPackets.CreatePlayer player&&player.playerId()==reserved));
                        var create=(PlayerPackets.CreatePlayer)client.packets.stream().filter(p->p instanceof PlayerPackets.CreatePlayer player&&player.playerId()==reserved).findFirst().orElseThrow();
                        assertEquals(i,create.playerId());assertEquals(i==3?0:i==2?3:2,create.team());assertEquals(i==3?4:0,create.classId());
                        assertEquals(List.of("Player","Ρlayer~2","Player~3","Watcher").get(i),create.name());
                        assertFalse(create.loadout().contains(51));
                    }
                }
                assertTrue(domain.players.isEmpty());
            }
        });
    }
    @Test void sourceSpawnPlacementAndLiveTerrainRevalidationCrossRealUdp(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Spawn UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",1),new SceneConfig.Team("Green",2),GameRules.serverDefaults());
            try(var domain=new FixtureScene(1,10000)){
                for(int x=95;x<=109;x++)for(int y=95;y<=109;y++)for(int z=100;z<=238;z++)domain.world().set(new MutableWorld.Cell(x,y,z),true,0x123456,null);
                Files.writeString(temp.resolve("fixture.json"),"{\"gravity\":0.4,\"ground_colors\":[[1,2,3,4]]}");
                Files.writeString(temp.resolve("fixture.ugc"),"{\"skybox_name\":\"User_Lunar.txt\",\"ugc_entities\":[{\"position\":[102,102,94],\"item\":\"ugc_spawnblue_small\",\"mode\":\"tdm\"}]}");
                Files.writeString(temp.resolve("fixture.txt"),"fog_color=(1,2,3)\nteam_one_spawn_area=[((102,102,94),(12,12,24))]\n");
                var metadata=MapMetadata.load(temp.resolve("fixture.vxl"),config.modeCode(),domain.world().sourceCrc());
                var geometry=new SpawnGeometry(domain.world(),metadata.spawnLayout());
                geometry.prewarm();var resolver=new SpawnResolver(geometry,new Object(),new SourceRandom(168));
                domain.placement=(id,request)->{
                    var player=new SpawnResolver.Player(id,request.team(),false,false,new SpawnGeometry.Position(102.5,102.5,97.25),null);
                    var occupants=domain.players.values().stream().map(a->{var p=a.player();var v=p.position();return new SpawnResolver.Player(p.playerId(),p.team(),true,true,new SpawnGeometry.Position(v.x(),v.y(),v.z()),null);}).toList();
                    var p=resolver.resolve(request.team(),()->resolver.choose(player,occupants,id+1),id+1);return new Vector3f((float)p.x(),(float)p.y(),(float)p.z());
                };
                var scene=new ConfiguredScene(config,metadata,()->ScenePackets.State.initial(config),domain);
                assertEquals(26.0/64,scene.gravity());assertEquals("User_Lunar.txt",scene.skybox());
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var first=new Client(host.address());var second=new Client(host.address())){
                    var loop=new Loop(host);loop.ticketAndLoad(first,domain);first.send(new SimplePackets.NewPlayerConnection(2,0,0,1,"one"),false);loop.until(()->first.has(28));
                    var state=(StateData)first.packets.stream().filter(p->p instanceof StateData).findFirst().orElseThrow();assertEquals((float)(26.0/64),state.gravity());assertEquals(0x3e3b39,state.fogColor());
                    var initial=(InitialInfo)first.packets.stream().filter(p->p instanceof InitialInfo).findFirst().orElseThrow();assertEquals(List.of(new InitialInfo.Rgba(1,2,3,4)),initial.groundColors());
                    var a=(PlayerPackets.CreatePlayer)first.packets.stream().filter(p->p.id()==28).findFirst().orElseThrow();var p=a.position();assertTrue(geometry.safePosition(new SpawnGeometry.Position(p.x(),p.y(),p.z())));
                    int holeX=(int)p.x(),holeY=(int)p.y();for(int z=100;z<=238;z++)domain.world().set(new MutableWorld.Cell(holeX,holeY,z),false,0,null);
                    assertFalse(geometry.safePosition(new SpawnGeometry.Position(p.x(),p.y(),p.z())));
                    loop.ticketAndLoad(second,domain);int reserved=((StateData)second.packets.stream().filter(v->v.id()==45).findFirst().orElseThrow()).playerId();second.send(new SimplePackets.NewPlayerConnection(2,0,0,1,"two"),false);
                    loop.until(()->second.packets.stream().anyMatch(v->v instanceof PlayerPackets.CreatePlayer c&&c.playerId()==reserved));
                    var b=(PlayerPackets.CreatePlayer)second.packets.stream().filter(v->v instanceof PlayerPackets.CreatePlayer c&&c.playerId()==reserved).findFirst().orElseThrow();var q=b.position();
                    assertTrue(geometry.safePosition(new SpawnGeometry.Position(q.x(),q.y(),q.z())));assertTrue((int)q.x()!=holeX||(int)q.y()!=holeY);
                }
                assertTrue(domain.players.isEmpty());
            }
        });
    }
    @Test void productionLifeDeathRespawnAndGatedOwnerRepairCrossRealUdp(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Life UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var first=new Client(host.address());var second=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    var loop=new Loop(host);loop.ticketAndLoad(first,h.world);first.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"one"),false);loop.until(()->first.has(28));
                    var original=h.runtime.player(0).body();assertEquals(2,h.runtime.player(0).inventory().turrets());
                    loop.until(()->{try{first.send(new ClientInput(100,0,false,6,1,0,0,0,0,0,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.sessions().stream().anyMatch(s->s.state()==JoinSession.State.LIVE);});
                    loop.ticketAndLoad(second,h.world);h.denied.add(1);second.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"two"),false);loop.until(()->second.packets.stream().anyMatch(p->p instanceof LifePackets.KillAction k&&k.playerId()==1));
                    assertTrue(h.runtime.die(0,1,0));h.denied.add(0);h.denied.remove(1);h.advance(1.5);assertFalse(h.runtime.player(0).alive());assertTrue(h.runtime.player(1).alive());assertEquals(1,h.runtime.player(1).generation());
                    loop.until(()->{try{second.send(new ClientInput(101,1,false,6,1,0,0,0,0,0,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.sessions().stream().allMatch(s->s.state()==JoinSession.State.LIVE);});
                    loop.until(()->second.packets.stream().filter(p->p instanceof PlayerPackets.CreatePlayer c&&c.playerId()==1).count()==2&&second.packets.stream().anyMatch(p->p instanceof LifePackets.KillAction k&&k.playerId()==0));
                    var replay=(LifePackets.KillAction)second.packets.stream().filter(p->p instanceof LifePackets.KillAction k&&k.playerId()==0).findFirst().orElseThrow();assertEquals(0,replay.killCount());assertFalse(replay.domination());assertFalse(replay.revenge());
                    assertTrue(second.packets.stream().anyMatch(p->p instanceof LifePackets.SetHP hp&&hp.hp()==100));h.denied.remove(0);h.advance(0);
                    loop.until(()->first.packets.stream().filter(p->p instanceof PlayerPackets.CreatePlayer c&&c.playerId()==0).count()==2&&second.packets.stream().filter(p->p instanceof PlayerPackets.CreatePlayer c&&c.playerId()==0).count()==2);
                    assertEquals(2,h.runtime.player(0).generation());assertNotSame(original,h.runtime.player(0).body());assertEquals(4,h.runtime.player(0).inventory().turrets());assertEquals(0,h.world.activeLeases());assertTrue(h.inputCount>=2);
                }
                assertTrue(h.runtime.players().isEmpty());
            }
        });
    }
    @Test void udpInputDrivesOwnedInfantryStepsAndRealFallHealth(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Infantry UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                for(int x=25;x<=50;x++)for(int y=30;y<=40;y++)h.world.set(new MutableWorld.Cell(x,y,100),true,0x123456,null);
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),1,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    var loop=new Loop(host);loop.ticketAndLoad(client,h.world);client.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"walker"),false);loop.until(()->client.has(69));var p=h.runtime.player(0);
                    var ports=new InfantryPorts();var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),ports);int[] tick={1000};var fixed=new FixedStepRuntime(16,16,()->simulation.tick(++tick[0]),()->{},failure->{throw new AssertionError(failure);});
                    var before=p.body().state();fixed.advance(FixedStepRuntime.DELTA);assertEquals(before,p.body().state());assertEquals(0,ports.moves);
                    loop.until(()->{try{client.send(new ClientInput(100,127,false,6,1,0,0,0,1,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0);});
                    assertEquals(before,p.body().state());fixed.advance(FixedStepRuntime.DELTA);assertEquals(100,p.inputTimeline().appliedLabel());assertEquals(before.position().x(),p.position().x());
                    client.send(new ClientInput(102,127,false,6,1,0,0,0,1,24,0),true);loop.until(()->p.inputTimeline().size()==1);fixed.advance(FixedStepRuntime.DELTA);assertEquals(101,p.inputTimeline().appliedLabel());assertTrue(p.inputTimeline().state().synthesized());fixed.advance(FixedStepRuntime.DELTA);assertEquals(102,p.inputTimeline().appliedLabel());assertTrue(p.position().x()>before.position().x());
                    int moved=ports.moves;var frozen=p.body().state();fixed.advance(FixedStepRuntime.DELTA*2);assertEquals(frozen,p.body().state());assertEquals(moved,ports.moves);
                    p.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,20),h.monotonic,1,p.color());
                    for(int label=200;label<560&&p.alive();label++){client.send(new ClientInput(label,0,false,6,1,0,0,0,0,24,0),true);loop.until(()->p.inputTimeline().size()>0);h.monotonic+=FixedStepRuntime.DELTA;h.wall+=FixedStepRuntime.DELTA;fixed.advance(FixedStepRuntime.DELTA);}
                    loop.until(()->client.has(46));assertFalse(p.alive());int hp=-1,death=-1;for(int i=0;i<client.packets.size();i++){var packet=client.packets.get(i);if(packet instanceof LifePackets.SetHP value&&value.hp()==0)hp=i;if(packet instanceof LifePackets.KillAction)death=i;}assertTrue(hp>=0&&hp<death);assertTrue(ports.moves>50);
                }
            }
        });
    }
    @Test void negotiatedFlightConsumesRealUdpInputAndCanopyBrakesARealLanding(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Flight UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                for(int x=25;x<=70;x++)for(int y=30;y<=40;y++)h.world.set(new MutableWorld.Cell(x,y,100),true,0x123456,null);
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),1,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    var loop=new Loop(host);loop.ticketAndLoad(client,h.world,2);assertArrayEquals(FlightProfile.BALANCED_V2.encode(),client.negotiatedFlight);client.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"pilot"),false);loop.until(()->client.has(69));var p=h.runtime.player(0);assertEquals(FlightProfile.BALANCED_V2,p.flight().profile());
                    p.apply(new ClassCatalog.Selection(0,List.of(5,6,68),List.of(),List.of()));p.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,20),h.monotonic,1,p.color());
                    var ports=new InfantryPorts();var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),ports);int[] tick={1000};var fixed=new FixedStepRuntime(16,16,()->simulation.tick(++tick[0]),()->{},failure->{throw new AssertionError(failure);});
                    loop.until(()->{try{client.send(new ClientInput(100,0,false,6,1,0,0,0,17,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0);});
                    var initial=p.body().state();assertEquals(100,p.flight().snapshot().fuel());assertEquals(0,ports.moves);assertTrue(p.flight().roundTripMillis().isPresent());
                    for(int label=101;label<285;label++){client.send(new ClientInput(label,0,false,6,1,0,0,0,17,24,0),true);int accepted=label;loop.until(()->Objects.equals(p.inputTimeline().newestLabel(),accepted));h.monotonic+=FixedStepRuntime.DELTA;h.wall+=FixedStepRuntime.DELTA;fixed.advance(FixedStepRuntime.DELTA);}
                    assertTrue(p.flight().snapshot().physicsActive(),"accepted steps ignite thrust");assertTrue(p.flight().snapshot().fuel()<90,"thrust consumes fuel");assertTrue(p.position().x()>initial.position().x(),"flight moves forward");assertTrue(p.body().state().velocity().z()<0,"sustained Engineer thrust overcomes the initial fall");
                    for(int label=285;label<288;label++){client.send(new ClientInput(label,0,false,6,1,0,0,0,0,24,0),true);int accepted=label;loop.until(()->Objects.equals(p.inputTimeline().newestLabel(),accepted));fixed.advance(FixedStepRuntime.DELTA);}assertFalse(p.flight().snapshot().physicsActive());
                    p.apply(new ClassCatalog.Selection(0,List.of(5,6,72),List.of(),List.of()));p.spawn(new net.battlespades.physics.PlayerPhysics.Vector(36,36,60),h.monotonic,1,p.color());
                    boolean sawCanopy=false;
                    for(int label=400;label<1050;label++){
                        client.send(new ClientInput(label,0,false,6,1,0,0,0,0,label>=403?152:24,0),true);int accepted=label;loop.until(()->Objects.equals(p.inputTimeline().newestLabel(),accepted));h.monotonic+=FixedStepRuntime.DELTA;h.wall+=FixedStepRuntime.DELTA;fixed.advance(FixedStepRuntime.DELTA);sawCanopy|=p.flight().snapshot().canopyPhysics();
                        if(sawCanopy&&!p.body().state().airborne())break;
                    }
                    assertTrue(sawCanopy);assertFalse(p.body().state().airborne());assertFalse(p.flight().snapshot().canopy());assertFalse(p.flight().snapshot().canopyPhysics());assertEquals(100,p.health());assertTrue(p.alive());assertFalse(client.has(46));
                }
            }
        });
    }
    @Test void productionInputChronologySurvivesUnsequencedUdpAndRespawn(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Input UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),1,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    var loop=new Loop(host);loop.ticketAndLoad(client,h.world);client.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"input"),false);loop.until(()->client.has(69));
                    var p=h.runtime.player(0);var initial=p.body().state();
                    loop.until(()->{try{client.send(new ClientInput(100,127,false,6,1,0,0,17,1,4,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0);});
                    client.send(new ClientInput(102,127,true,6,0,1,0,19,33,0,0),true);loop.until(()->Objects.equals(p.inputTimeline().state().newest(),102));
                    int fresh=h.inputCount;client.send(new ClientInput(101,127,false,6,1,0,0,18,17,128,0),true);loop.until(()->p.inputTimeline().state().frames().size()==3);
                    assertEquals(fresh,h.inputCount);assertEquals(1,p.aim().y());assertEquals(initial,p.body().state());assertEquals(1,p.inputTimeline().reorderSpread(p.inputTimeline().state().frames().get(1).receivedTick()));
                    var first=p.inputTimeline().consumeOne(8,p.aim()).orElseThrow();assertEquals(100,first.label());assertEquals(0,first.movement());assertNull(first.sourceLabel());
                    var second=p.inputTimeline().consumeOne(8,p.aim()).orElseThrow();assertEquals(101,second.label());assertEquals(1,second.movement());assertEquals(100,second.sourceLabel());assertEquals(128,second.actions());
                    var third=p.inputTimeline().consumeOne(8,p.aim()).orElseThrow();assertEquals(102,third.label());assertEquals(49,third.movement());assertEquals(18,third.sourceUnknown());assertEquals(19,p.inputTimeline().state().pendingUnknown());
                    assertTrue(p.inputTimeline().zoomForAction(102,false));assertEquals(100,p.health());assertTrue(p.inputTimeline().consumeOne(8,p.aim()).isEmpty());
                    h.runtime.die(0,null,12);h.runtime.respawn(0);assertTrue(p.inputTimeline().state().frames().isEmpty());assertNull(p.inputTimeline().state().applied());assertEquals(102,p.inputTimeline().state().newest());
                }
            }
        });
    }
    @Test void acceptedDamageHealingAndOwnerHealthGateCrossRealUdp(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Health UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),1,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    var loop=new Loop(host);loop.ticketAndLoad(client,h.world);client.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"one"),false);loop.until(()->client.has(69));assertFalse(host.inGame(0));long initial=client.packets.stream().filter(p->p.id()==5).count();assertEquals(1,initial);
                    h.runtime.damage(0,25,null,7);h.runtime.heal(0,10);for(int i=0;i<20;i++)loop.step();assertEquals(85,h.runtime.player(0).health());assertEquals(initial,client.packets.stream().filter(p->p.id()==5).count());
                    loop.until(()->{try{client.send(new ClientInput(100,0,false,6,1,0,0,0,0,0,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0);});
                    h.runtime.damage(0,5,null,7);loop.until(()->client.packets.stream().anyMatch(p->p instanceof LifePackets.SetHP hp&&hp.hp()==80&&hp.damageType()==0));h.runtime.heal(0,100);loop.until(()->client.packets.stream().filter(p->p instanceof LifePackets.SetHP hp&&hp.hp()==100&&hp.damageType()==2).count()==2);
                    h.runtime.player(0).god(true);assertFalse(h.runtime.damage(0,100,null,7));assertTrue(h.runtime.player(0).alive());h.runtime.player(0).god(false);assertTrue(h.runtime.damage(0,100,null,7));loop.until(()->client.has(46));
                    int death=-1,zero=-1;for(int i=0;i<client.packets.size();i++){var p=client.packets.get(i);if(p instanceof LifePackets.KillAction)death=i;if(p instanceof LifePackets.SetHP hp&&hp.hp()==0)zero=i;}assertTrue(zero>=0&&zero<death);assertEquals(1,h.runtime.score(0).deaths());h.advance(1.5);loop.until(()->client.packets.stream().filter(p->p.id()==28).count()==2);assertEquals(100,h.runtime.player(0).health());assertEquals(2,h.runtime.player(0).generation());
                }
            }
        });
    }
}
