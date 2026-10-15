// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.battlespades.protocol.*;
import net.battlespades.session.*;
import net.battlespades.support.FixtureScene;
import net.battlespades.transport.UdpEndpoint;
import net.battlespades.transport.enet.*;
import net.battlespades.world.*;
import net.battlespades.scene.*;
import net.battlespades.rules.GameRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationIntegrationTest {
    private static final class Client implements AutoCloseable {
        final UdpEndpoint udp;
        final SocketAddress server;
        final List<GamePacket> packets=new ArrayList<>();
        final List<Integer> commands=new ArrayList<>();
        final Set<Integer> received=new HashSet<>();
        int peerId=-1,outSession,inSession,reliable,group,reason=-1;
        byte[] key=new byte[0];
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
                    byte[] wire=c.payload();byte[] body=Lzf.decode(Arrays.copyOfRange(wire,1,wire.length),65536);packets.add(PacketCodec.decode(body));commands.add(c.id());
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
        void ticketAndLoad(Client client,FixtureScene scene) throws Exception {
            clients.add(client);client.connect();until(()->client.peerId>=0);client.send(new LoadingPackets.SteamSessionTicket(new byte[]{3,7,19}),false);client.key=new byte[]{3,7,19};
            until(()->client.has(114));client.send(new SimplePackets.MapDataValidation(scene.world().sourceCrc()),false);until(()->client.has(45));
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
}
