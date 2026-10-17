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
import net.battlespades.support.CombatPorts;
import net.battlespades.support.TerrainPorts;
import net.battlespades.simulation.FixedStepRuntime;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.transport.enet.*;
import net.battlespades.world.*;
import net.battlespades.scene.*;
import net.battlespades.rules.GameRules;
import net.battlespades.player.*;
import net.battlespades.replication.*;
import net.battlespades.combat.*;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
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
        boolean withholdAcks;
        final List<EnetDatagram.Command> delayedAcks=new ArrayList<>();
        void releaseAcks()throws Exception {withholdAcks=false;if(!delayedAcks.isEmpty()){udp.send(server,new EnetDatagram(peerId,outSession,-1,List.copyOf(delayedAcks)).encode());delayedAcks.clear();}}
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
                    if((c.flags()&128)!=0){var ack=EnetDatagram.acknowledge(c.channel(),c.reliableSequence(),c.reliableSequence(),datagram.sentTime());if(withholdAcks)delayedAcks.add(ack);else udp.send(server,new EnetDatagram(peerId,outSession,-1,List.of(ack)).encode());}
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
        final ApplicationHost host;final List<Client> clients=new ArrayList<>();long now;int tickOffset,explicitTick=-1;
        Loop(ApplicationHost host) {this.host=host;}
        void step() throws Exception {host.service(++now,explicitTick>=0?explicitTick:tickOffset+(int)(now*60/1000));for(var client:clients) client.drain();}
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
    @Test void realUdpOwnedBulletAndSpadeCommitTerrainHealthCollapseAndCredit(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Terrain commit UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var ports=new CombatPorts(h);var combat=ports.bind();var terrainPorts=new TerrainPorts();terrainPorts.activeTool=combat::activeShotTool;var random=new SourceRandom(107);ports.terrain=new TerrainDamage(h.world,terrainPorts,()->random.below(256));
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),3,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address());var late=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};ports.reliableSink=p->host.broadcast(p,Delivery.RELIABLE);ports.unreliableSink=p->host.broadcast(p,Delivery.UNRELIABLE);ports.feedbackSink=(p,owner)->host.broadcastExcept(p,Delivery.UNRELIABLE,owner);terrainPorts.reliableSink=p->host.broadcast(p,Delivery.RELIABLE);
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"shooter"),false);loop.until(()->a.has(69));loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"target"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,127,false,6,1,0,0,0,0,24,0),true);b.send(new ClientInput(200,127,false,5,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var shooter=h.runtime.player(0);var target=h.runtime.player(1);shooter.body().position(new Vector(36,36.5,97.5));target.body().position(new Vector(42,36.5,97));shooter.orientation(1,0,0);shooter.fire().tool(6,true);shooter.inventory().ammo(8,100);var wall=new MutableWorld.Cell(39,36,97);var floating=new MutableWorld.Cell(39,36,98);var edge=new MutableWorld.Cell(40,36,98);for(var c:List.of(wall,floating,edge))h.world.set(c,true,0x406080,null);
                    for(int shot=0;shot<4;shot++){
                        int label=100+180*shot;if(shot>0){h.monotonic+=3;h.wall+=3;ports.loop=loop.explicitTick+=180;a.send(new ClientInput(label,127,false,6,1,0,0,0,0,24,0),true);loop.until(()->Objects.equals(shooter.inputTimeline().newestLabel(),label));}a.packets.clear();a.commands.clear();b.packets.clear();b.commands.clear();a.send(new ShootPacket(label,254,0,new Vector3f(36,36.5f,97.5f),new Vector3f(1,0,0),32767,32767,true,true,0),false);
                        if(shot<3){loop.until(()->a.has(37)&&b.has(37));assertEquals(100,target.health());var damage=(DamagePacket)a.packets.stream().filter(p->p.id()==37).findFirst().orElseThrow();assertEquals(6,damage.type());assertEquals(0,damage.playerId());assertEquals(new Vector3f(39,36,97),damage.position());assertEquals(shot==2?31.75f:2f,damage.damage());for(var client:List.of(a,b))for(int i=0;i<client.packets.size();i++)if(client.packets.get(i).id()==37)assertEquals(6,client.commands.get(i));if(shot<2){assertTrue(h.world.solid(wall));assertEquals((shot+1)*2,h.world.terrainHealth().accumulated(wall));}else{assertFalse(h.world.solid(wall));assertFalse(h.world.solid(floating));assertFalse(h.world.solid(edge));assertTrue(terrainPorts.events.stream().anyMatch(e->e.startsWith("collapsed:")));}}
                        else{loop.until(()->a.has(9)&&b.has(5));assertEquals(30,target.health());}assertEquals(7-shot,shooter.inventory().ammo().clip());assertNull(combat.activeShotTool(shooter));
                    }
                    assertEquals(200,shooter.inventory().blocks(),"gun destruction never credits the digging wallet");h.world.set(wall,true,0x406080,9.);h.world.set(floating,true,0x406080,9.);shooter.fire().tool(2,true);var lease=h.world.capture(false,false).lease();
                    for(int swing=0;swing<2;swing++){
                        h.monotonic+=3;h.wall+=3;ports.loop=loop.explicitTick+=180;int label=820+180*swing;a.send(new ClientInput(label,127,false,2,1,0,0,0,0,24,0),true);loop.until(()->Objects.equals(shooter.inputTimeline().newestLabel(),label));a.packets.clear();b.packets.clear();a.send(new ShootPacket(label,254,0,new Vector3f(36,36.5f,97.5f),new Vector3f(1,0,0),32767,32767,true,false,0),false);loop.until(()->a.has(37)&&b.has(37));var damage=(DamagePacket)a.packets.stream().filter(p->p.id()==37).findFirst().orElseThrow();assertEquals(2,damage.type());assertEquals(5f,damage.damage());assertEquals(30,target.health());assertFalse(a.has(8));assertFalse(b.has(8));
                        if(swing==0){assertEquals(5,h.world.terrainHealth().accumulated(wall));assertTrue(h.world.solid(wall));var catchup=new ArrayList<GamePacket>();assertEquals(MutableWorld.Replay.COMPLETE,lease.pump(0,ports.loop,99,p->{catchup.add(p);return true;}));assertTrue(catchup.stream().filter(BlockManagerState.class::isInstance).map(BlockManagerState.class::cast).flatMap(p->p.damaged().stream()).anyMatch(r->r.x()==39&&r.y()==36&&r.z()==97&&r.health()==4.));
                            loop.ticketAndLoad(late,h.world);late.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"late"),false);loop.until(()->late.has(69));loop.until(()->{try{late.send(new ClientInput(900,127,false,5,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(2);});loop.until(()->late.packets.stream().filter(BlockManagerState.class::isInstance).map(BlockManagerState.class::cast).flatMap(p->p.damaged().stream()).anyMatch(r->r.x()==39&&r.y()==36&&r.z()==97&&r.health()==4.));int user=-1,damaged=-1;for(int i=0;i<late.packets.size();i++)if(late.packets.get(i) instanceof BlockManagerState state){assertEquals(6,late.commands.get(i));if(state.users().stream().anyMatch(r->r.x()==39&&r.y()==36&&r.z()==97&&r.health()==9.))user=i;if(state.damaged().stream().anyMatch(r->r.x()==39&&r.y()==36&&r.z()==97&&r.health()==4.))damaged=i;}assertTrue(user>=0&&damaged>user,"fresh join receives initial health before remaining health");}else{assertFalse(h.world.solid(wall));assertFalse(h.world.solid(floating));assertEquals(202,shooter.inventory().blocks());}
                    }
                    lease.close();assertTrue(terrainPorts.events.stream().anyMatch(e->e.startsWith("achieve:")&&e.endsWith(":2")));
                }
            }
        });
    }
    @Test void delayedRealUdpShotDamagesRewoundTargetWithoutMovingItsLiveBody(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Rewound damage UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                for(int x=30;x<48;x++)for(int y=30;y<62;y++)h.world.set(new MutableWorld.Cell(x,y,100),true,0x777777,9.0);
                var ports=new CombatPorts(h);ports.rewindEnabled=true;var combat=ports.bind();combat.settings(new CombatRuntime.Settings(ShotValidation.Settings.defaults(),LagCompensation.Settings.defaults()));
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};ports.reliableSink=p->host.broadcast(p,Delivery.RELIABLE);ports.unreliableSink=p->host.broadcast(p,Delivery.UNRELIABLE);ports.feedbackSink=(p,owner)->host.broadcastExcept(p,Delivery.UNRELIABLE,owner);
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"shooter"),false);loop.until(()->a.has(69));loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"target"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,127,false,6,1,0,0,0,0,24,0),true);b.send(new ClientInput(200,127,false,5,1,0,0,0,8,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var shooter=h.runtime.player(0);var target=h.runtime.player(1);shooter.body().position(new Vector(36,36,97));target.body().position(new Vector(39,36,97));shooter.fire().tool(6,true);shooter.inventory().ammo(8,100);var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new InfantryPorts());
                    for(int frame=0;frame<50;frame++){int label=201+frame;b.send(new ClientInput(label,127,false,5,1,0,0,0,8,24,0),true);loop.until(()->Objects.equals(target.inputTimeline().newestLabel(),label));h.monotonic+=FixedStepRuntime.DELTA;h.wall+=FixedStepRuntime.DELTA;ports.loop=++loop.explicitTick;simulation.tick(loop.explicitTick);}
                    var before=target.body().state();var origin=shooter.position();var probe=new ShootPacket(100,254,0,new Vector3f((float)origin.x(),(float)origin.y(),(float)origin.z()),new Vector3f(1,0,0),32767,32767,true,true,0);var context=LagCompensation.prepare(LagCompensation.Settings.defaults(),ports.loop,FixedStepRuntime.DELTA,shooter,probe,event->{});assertNotNull(context);var earlier=context.body(target.lagTarget());assertTrue(earlier.position().y()<before.position().y()-.5);
                    var aim=CombatGeometry.normalize(new Vector(earlier.position().x()-origin.x(),earlier.position().y()-origin.y(),earlier.position().z()+.7-origin.z()));shooter.orientation(aim.x(),aim.y(),aim.z());var map=CollisionMap.of(h.world);
                    var liveTrace=new HitTrace(new HitTrace.Scene(){public CombatGeometry.Cell raycast(Vector p,Vector d,double max){return HitTrace.terrainRay(map,p,d,max);}public Iterable<PlayerLife> players(){return h.runtime.players();}public boolean enemies(PlayerLife p,PlayerLife q){return p.team()!=q.team();}public Iterable<HitTrace.Entity> entities(){return List.of();}public Iterable<HitTrace.Corpse> corpses(){return List.of();}},new LagCompensation.ShotOwner());assertFalse(liveTrace.trace(shooter,origin,aim,128) instanceof HitTrace.PlayerHit,"live body is outside this historical ray");
                    a.packets.clear();b.packets.clear();a.send(new ShootPacket(100,254,0,probe.origin(),new Vector3f((float)aim.x(),(float)aim.y(),(float)aim.z()),32767,32767,true,true,0),false);loop.until(()->a.has(9)&&b.has(5));assertEquals(7,shooter.inventory().ammo().clip());assertTrue(target.health()<100);assertEquals(before.position(),target.body().state().position());assertEquals(before.velocity(),target.body().state().velocity());assertTrue(shooter.lastLagRewindMillis().orElseThrow()>=400);assertTrue(ports.rewindEvents.stream().anyMatch(e->e.kind().equals("lag_comp_late_shot")));
                    var response=(CombatPackets.ShootResponse)a.packets.stream().filter(p->p.id()==9).findFirst().orElseThrow();assertEquals(0,response.damageBy());assertTrue(response.position().y()<before.position().y()-.3);assertEquals(1,shooter.combatStatistics().snapshot().hits());
                }
            }
        });
    }
    @Test void realUdpOwnedShotCommitConsumesOnceAndSendsHealthDeathAndRemoteFeedback(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Combat commit UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var ports=new CombatPorts(h);ports.bind();h.packetHandler=(p,packet)->{if(packet instanceof ShootPacket)throw new AssertionError("Owned combat must handle packet 6 before the test input port");};
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){ports.capture("B",true,null,p);host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){ports.capture("O"+id,true,null,p);assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    ports.reliableSink=p->host.broadcast(p,Delivery.RELIABLE);ports.unreliableSink=p->host.broadcast(p,Delivery.UNRELIABLE);ports.feedbackSink=(p,owner)->host.broadcastExcept(p,Delivery.UNRELIABLE,owner);
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"shooter"),false);loop.until(()->a.has(69));loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"target"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,127,false,6,1,0,0,0,0,24,0),true);b.send(new ClientInput(200,127,false,5,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var shooter=h.runtime.player(0);var target=h.runtime.player(1);shooter.body().position(new Vector(36,36,97));target.body().position(new Vector(39,36,96.5));shooter.orientation(1,0,0);target.orientation(1,0,0);shooter.fire().tool(6,true);shooter.inventory().ammo(8,100);a.packets.clear();a.commands.clear();b.packets.clear();b.commands.clear();ports.events.clear();ports.wire.clear();
                    a.send(new ShootPacket(100,254,0,new Vector3f(999,999,999),new Vector3f(1,0,0),32767,32767,true,true,0),false);loop.until(()->ports.events.stream().anyMatch(e->e.startsWith("drop:shot_origin_far")));assertEquals(8,shooter.inventory().ammo().clip());assertEquals(100,target.health());assertTrue(ports.disguised);
                    ports.events.clear();var valid=new ShootPacket(100,254,0,new Vector3f(36,36,97),new Vector3f(1,0,0),32767,32767,true,true,0);a.send(valid,false);loop.until(()->a.has(9)&&b.has(9)&&b.has(5)&&b.has(8));assertEquals(7,shooter.inventory().ammo().clip());assertEquals(30,target.health());assertFalse(ports.disguised);assertFalse(a.has(8));
                    var feedback=(SimplePackets.ShootFeedbackPacket)b.packets.stream().filter(p->p.id()==8).findFirst().orElseThrow();assertEquals(0,feedback.shooter_id());assertEquals(6,feedback.tool_id());assertEquals(1000,feedback.loop_count());
                    for(var client:List.of(a,b))for(int i=0;i<client.packets.size();i++){int id=client.packets.get(i).id();if(id==8||id==9)assertEquals(7,client.commands.get(i),"cosmetic shot/hit events stay sequenced-unreliable");if(id==5)assertEquals(6,client.commands.get(i),"HP is reliable");}
                    ports.events.clear();a.send(valid,false);loop.until(()->ports.events.contains("drop:fire_rate"));assertEquals(7,shooter.inventory().ammo().clip());assertEquals(30,target.health());assertEquals(1,shooter.combatStatistics().snapshot().shots());
                    h.monotonic+=3;h.wall+=3;ports.loop=1180;loop.explicitTick=1180;a.send(new ClientInput(280,127,false,6,1,0,0,0,0,24,0),true);loop.until(()->Objects.equals(shooter.inputTimeline().newestLabel(),280));a.send(new ShootPacket(280,254,0,new Vector3f(36,36,97),new Vector3f(1,0,0),32767,32767,true,true,0),false);loop.until(()->a.has(46)&&b.has(46));assertEquals(6,shooter.inventory().ammo().clip());assertEquals(0,target.health());assertFalse(target.alive());assertEquals(1,h.runtime.score(0).kills());assertEquals(2,shooter.combatStatistics().snapshot().shots());assertEquals(2,shooter.combatStatistics().snapshot().hits());
                    var death=(LifePackets.KillAction)a.packets.stream().filter(p->p.id()==46).findFirst().orElseThrow();assertEquals(0,death.killerId());assertEquals(1,death.playerId());
                }
            }
        });
    }
    @Test void realUdpShotValidationRejectsForgedOriginsAndTracesOwnedBodiesAndTerrain(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Shot geometry UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address())){
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"shooter"),false);loop.until(()->a.has(69));
                    loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"target"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,127,false,5,1,0,0,0,0,24,0),true);b.send(new ClientInput(200,127,false,5,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var shooter=h.runtime.player(0);var target=h.runtime.player(1);shooter.body().position(new Vector(36,36,97));target.body().position(new Vector(42,36,97));shooter.orientation(1,0,0);target.orientation(1,0,0);
                    var map=CollisionMap.of(h.world);var owner=new LagCompensation.ShotOwner();
                    // Explicit test domains; production entity/corpse registries remain required.
                    var trace=new HitTrace(new HitTrace.Scene(){
                        public CombatGeometry.Cell raycast(Vector origin,Vector direction,double maximum){return HitTrace.terrainRay(map,origin,direction,maximum);}
                        public Iterable<PlayerLife> players(){return h.runtime.players();}public boolean enemies(PlayerLife a,PlayerLife b){return a.team()!=b.team();}
                        public Iterable<HitTrace.Entity> entities(){return List.of();}public Iterable<HitTrace.Corpse> corpses(){return List.of();}
                    },owner);
                    var results=new ArrayList<ShotValidation.Result>();var hits=new ArrayList<HitTrace.Hit>();var before=target.body().state();var ammo=shooter.inventory().ammo();
                    h.packetHandler=(bound,packet)->{
                        if(!(packet instanceof ShootPacket shot))return;assertSame(shooter,bound);assertEquals(254,shot.shooterId());
                        var result=ShotValidation.validate(ShotValidation.Settings.defaults(),ShotValidation.view(bound),ShotValidation.Claims.from(shot),map::solid,bound.shotStatistics(),event->{});results.add(result);
                        if(result.allowed())hits.add(trace.trace(bound,result.accepted().origin(),result.accepted().direction(),128));
                    };
                    a.send(new ShootPacket(100,254,0,new Vector3f(999,999,999),new Vector3f(1,0,0),32767,32767,true,true,255),false);loop.until(()->results.size()==1);assertFalse(results.getFirst().allowed());assertTrue(results.getFirst().reason().startsWith("shot_origin_far"));assertTrue(hits.isEmpty());
                    var valid=new ShootPacket(100,254,0,new Vector3f(36,36,97),new Vector3f(1,0,0),32767,32767,true,true,255);a.send(valid,false);loop.until(()->results.size()==2);assertTrue(results.getLast().allowed());assertSame(target,((HitTrace.PlayerHit)hits.getLast()).player());
                    h.world.set(new MutableWorld.Cell(39,36,97),true,0x777777,9.0);a.send(valid,false);loop.until(()->results.size()==3);assertTrue(results.getLast().allowed());assertEquals(new CombatGeometry.Cell(39,36,97),((HitTrace.BlockHit)hits.getLast()).cell());
                    a.send(new ShootPacket(100,254,0,new Vector3f(40,36,97),new Vector3f(1,0,0),32767,32767,true,true,255),false);loop.until(()->results.size()==4);assertEquals("shot_origin_occluded",results.getLast().reason());assertEquals(2,hits.size());
                    assertEquals(before,target.body().state());assertEquals(ammo,shooter.inventory().ammo());assertEquals(100,target.health());assertEquals(100,shooter.health());
                }
            }
        });
    }
    @Test void delayedRealUdpShotUsesBoundLifeAndArrivalDatesWithoutMutatingTheTarget(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Lag UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address())){
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"shooter"),false);loop.until(()->a.has(69));
                    loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"target"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,127,false,5,1,0,0,0,0,24,0),true);b.send(new ClientInput(200,127,false,5,1,0,0,0,1,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var shooter=h.runtime.player(0);var target=h.runtime.player(1);var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new InfantryPorts());
                    for(int frame=0;frame<50;frame++){
                        int label=201+frame;b.send(new ClientInput(label,127,false,5,1,0,0,0,1,24,0),true);loop.until(()->Objects.equals(target.inputTimeline().newestLabel(),label));
                        h.monotonic+=FixedStepRuntime.DELTA;h.wall+=FixedStepRuntime.DELTA;simulation.tick(++loop.explicitTick);
                    }
                    var before=target.body().state();var ammo=shooter.inventory().ammo();var result=new java.util.concurrent.atomic.AtomicReference<LagCompensation.Context>();
                    var owner=new LagCompensation.ShotOwner();
                    h.packetHandler=(bound,packet)->{
                        if(!(packet instanceof ShootPacket shot))return;
                        assertEquals(0,bound.id());assertEquals(254,shot.shooterId());
                        var context=LagCompensation.prepare(LagCompensation.Settings.defaults(),loop.explicitTick,FixedStepRuntime.DELTA,bound,shot,event->{});assertNotNull(context);result.set(context);
                        try(var scope=owner.install(context)){assertSame(context,scope.context());var body=owner.body(target.lagTarget());assertTrue(body instanceof LagCompensation.Rewound);assertTrue(body.position().x()<before.position().x());assertSame(target.lagTarget(),body.target());}
                        assertSame(target.lagTarget(),owner.body(target.lagTarget()));assertEquals(before,target.body().state());
                    };
                    a.send(new ShootPacket(100,254,0,new Vector3f(999,999,999),new Vector3f(1,0,0),32767,32767,true,true,255),false);loop.until(()->result.get()!=null);
                    assertTrue(result.get().rewindMillis()>=400);assertTrue(shooter.flight().roundTripMillis().isPresent());
                    assertEquals(result.get().rewindMillis(),shooter.lastLagRewindMillis().orElseThrow());assertEquals(before,target.body().state());
                    assertEquals(ammo,shooter.inventory().ammo());assertEquals(100,shooter.health());assertEquals(100,target.health());
                }
            }
        });
    }
    @Test void realUdpReloadIgnoresClaimedIdentityAndCompletionAndUsesOwnedHousekeeping(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Reload UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),1,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var client=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}public void owner(int id,GamePacket packet){assertTrue(host.sendPlayer(id,packet));}public boolean inGame(int id){return host.inGame(id);}};
                    var loop=new Loop(host);loop.explicitTick=1000;loop.ticketAndLoad(client,h.world);client.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"reload"),false);loop.until(()->client.has(69));
                    loop.until(()->{try{client.send(new ClientInput(100,0,false,5,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0);});
                    var p=h.runtime.player(0);int tool=p.selection().loadout().stream().filter(LifeCatalog.WEAPONS::containsKey).findFirst().orElseThrow();p.fire().tool(tool,true);p.inventory().ammo(0,3);
                    client.send(new LifePackets.WeaponReload(254,255,false),false);loop.until(()->p.inventory().reloading());loop.until(()->client.packets.stream().anyMatch(packet->packet instanceof LifePackets.WeaponReload reload&&!reload.done()));
                    var started=(LifePackets.WeaponReload)client.packets.stream().filter(packet->packet instanceof LifePackets.WeaponReload reload&&!reload.done()).findFirst().orElseThrow();assertEquals(0,started.playerId());assertEquals(tool,started.toolId());
                    assertNotNull(p.fire().link());assertTrue(p.fire().link().varianceMillis()>=0);double end=p.inventory().reloadEnd();
                    client.send(new LifePackets.WeaponReload(254,255,true),false);client.send(new SimplePackets.ClockSync(91234,0),false);loop.until(()->client.packets.stream().anyMatch(packet->packet instanceof SimplePackets.ClockSync clock&&clock.client_time()==91234));
                    assertTrue(p.inventory().reloading());assertEquals(end,p.inventory().reloadEnd());assertEquals(0,p.inventory().ammo().clip());
                    var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new InfantryPorts());h.monotonic=end-1e-6;simulation.tick(++loop.explicitTick);assertTrue(p.inventory().reloading());
                    h.monotonic=end;simulation.tick(++loop.explicitTick);loop.until(()->client.packets.stream().anyMatch(packet->packet instanceof LifePackets.WeaponReload reload&&reload.done()));
                    assertFalse(p.inventory().reloading());assertEquals(3,p.inventory().ammo().clip());assertEquals(0,p.inventory().ammo().reserve());
                    for(int i=0;i<client.packets.size();i++)if(client.packets.get(i) instanceof LifePackets.WeaponReload)assertEquals(6,client.commands.get(i),"reload events remain reliable");
                    assertEquals(100,p.health());
                }
            }
        });
    }
    @Test void ownedReplicationCrossesRealUdpAndHoldsRespawnRowsUntilReliableRelease(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var config=new SceneConfig("Replication UDP",27015,0,ModeCatalog.get("tdm"),"fixture",false,false,true,200,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            try(var h=new LifeHarness()){
                var scene=new ConfiguredScene(config,ScenePackets.Environment.defaults("fixture",h.world.sourceCrc()),()->ScenePackets.State.initial(config),h.runtime);
                try(var host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),2,FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO)),scene,new BoundedSnapshotCompression(1,2));var a=new Client(host.address());var b=new Client(host.address())){
                    h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}public void owner(int id,GamePacket p){assertTrue(host.sendPlayer(id,p));}public boolean inGame(int id){return host.inGame(id);}};
                    var replication=new ReplicationService(new ReplicationService.World(){
                        public List<PlayerSnapshot> players(){return h.runtime.players().stream().map(p->new PlayerSnapshot(p,life->new PlayerSnapshot.Presentation(5,null,false,false,false,false,true,false))).toList();}
                        public List<? extends WorldUpdateLink> connections(){return host.snapshotLinks();}
                        public List<EntityRecord> entities(){return List.of();}public List<WorldUpdate.TurretRow> turrets(){return List.of();}
                        public void delivered(int packets,long bytes){}
                    },new ReplicationService.Settings(true,2,20,20,20,true,0,true,true));h.runtime.replication(replication);
                    var loop=new Loop(host);loop.tickOffset=1000;loop.ticketAndLoad(a,h.world);a.send(new SimplePackets.NewPlayerConnection(2,0,0,0,"owner"),false);loop.until(()->a.has(69));
                    loop.ticketAndLoad(b,h.world);b.send(new SimplePackets.NewPlayerConnection(3,0,0,0,"observer"),false);loop.until(()->b.has(69));
                    loop.until(()->{try{a.send(new ClientInput(100,0,false,6,1,0,0,0,1,24,0),true);b.send(new ClientInput(100,1,false,6,1,0,0,0,0,24,0),true);}catch(Exception e){throw new IllegalStateException(e);}return host.inGame(0)&&host.inGame(1);});
                    var ports=new InfantryPorts();var simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),ports);int[] tick={1020};
                    var fixed=new FixedStepRuntime(16,16,()->simulation.tick(++tick[0]),()->replication.broadcast(tick[0],h.monotonic),failure->{throw new AssertionError(failure);});
                    fixed.advance(FixedStepRuntime.DELTA*2);loop.until(()->a.has(2)&&b.has(2));
                    var p=h.runtime.player(0);var history=p.ownerAnchors().snapshot();assertFalse(history.isEmpty());assertEquals(p.position(),history.getLast().position());assertEquals(100,history.getLast().stamp());
                    int owner=-1,observer=-1;
                    for(int i=0;i<a.packets.size();i++)if(a.packets.get(i) instanceof WorldUpdate update&&update.players().stream().anyMatch(r->r.playerId()==0)){owner=i;assertEquals(255,update.players().stream().filter(r->r.playerId()==0).findFirst().orElseThrow().tool());}
                    for(int i=0;i<b.packets.size();i++)if(b.packets.get(i) instanceof WorldUpdate update&&update.players().stream().anyMatch(r->r.playerId()==0)){observer=i;assertEquals(5,update.players().stream().filter(r->r.playerId()==0).findFirst().orElseThrow().tool());}
                    assertTrue(owner>=0&&observer>=0);assertEquals(7,a.commands.get(owner));assertEquals(9,b.commands.get(observer));
                    // Delay actual ENet releases across a new-life boundary. Observer packets still flow, but omit this life.
                    b.withholdAcks=true;h.runtime.die(0,null,12);h.runtime.respawn(0);long oldCreates=b.packets.stream().filter(packet->packet.id()==28).count();loop.until(()->b.packets.stream().filter(packet->packet.id()==28).count()>oldCreates);
                    var link=host.snapshotLinks().stream().filter(l->Objects.equals(l.localPlayerId(),1)).findFirst().orElseThrow();assertTrue(link.heldSnapshotRows(1024).contains(new SnapshotHolds.Row(false,0)));
                    b.packets.clear();b.commands.clear();replication.broadcast(1024,h.monotonic);loop.until(()->b.has(2));
                    assertTrue(b.packets.stream().filter(packet->packet instanceof WorldUpdate).map(packet->(WorldUpdate)packet).allMatch(update->update.players().stream().noneMatch(r->r.playerId()==0)));
                    b.releaseAcks();loop.until(()->link.heldSnapshotRows(1026).isEmpty());b.packets.clear();replication.broadcast(1026,h.monotonic);loop.until(()->b.has(2));
                    assertTrue(b.packets.stream().filter(packet->packet instanceof WorldUpdate).map(packet->(WorldUpdate)packet).anyMatch(update->update.players().stream().anyMatch(r->r.playerId()==0)));
                    assertTrue(p.ownerAnchors().snapshot().isEmpty(),"respawn clears owner output history and has no applied label yet");
                }
            }
        });
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
