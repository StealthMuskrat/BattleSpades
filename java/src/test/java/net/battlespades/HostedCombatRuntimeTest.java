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
    @FunctionalInterface private interface Setup {void bind(ApplicationHost host,OwnerLoop loop,StandardPorts ports)throws Exception;}
    private static final class Harness implements AutoCloseable {
        final StandardPorts ports=new StandardPorts();final OwnerLoop loop=new OwnerLoop(0,1024,256,error->fail(error));final HostedSceneState state;final ApplicationHost host;final StandardMatchDomain match;
        Harness(Path path,boolean friendly)throws Exception{this(path,friendly,null);}
        Harness(Path path,boolean friendly,Setup setup)throws Exception{
            var prepared=StandardPorts.prepared(path,"tdm");var config=new SceneConfig("Combat",27015,0,ModeCatalog.get("tdm"),"Combat",false,friendly,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());var raw=org.tomlj.Toml.parse("[modes.tdm]\ntime_limit=0\n[network]\nlag_compensation_enabled=false\n");var factory=new StandardMatchFactory(config,raw,ports,new SourceRandom(0),loop,List.of("Combat"),candidate->new StandardMatchFactory.Ports(ports,ports,ports,ports,ports,ports));
            state=new HostedSceneState("Combat","tdm",prepared,1,10000,factory.modes());host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));if(setup!=null)setup.bind(host,loop,ports);state.attach(host);match=(StandardMatchDomain)state.domain();match.mode().start();
        }
        PlayerLife admit(int id,int team,Vector at){match.scene().admit(id,new SimplePackets.NewPlayerConnection(team,5,0,0,"P"+id),null,0);var p=match.lives().player(id);p.body().position(at);p.orientation(1,0,0);return p;}
        void fire(PlayerLife p,int label,int tick,Vector origin){match.frameLoop(tick);p.inputTimeline().receive(new LiveClientInput(label,127,false,6,1,0,0,0,0,24,0),false,false,p.aim(),tick,match.world().topologyVersion(),false);match.packet(p.id(),new ShootPacket(label,254,0,new Vector3f((float)origin.x(),(float)origin.y(),(float)origin.z()),new Vector3f(1,0,0),32767,32767,true,false,0));}
        public void close()throws java.io.IOException{try{var stopped=host.shutdownAchievements().toCompletableFuture();long deadline=System.nanoTime()+4_000_000_000L;while(!stopped.isDone()){assertTrue(System.nanoTime()<deadline);loop.pump(loop.nowNanos()+1_000_000);Thread.onSpinWait();}stopped.join();host.close();}finally{try{state.close();}finally{loop.close();}}}
    }
    @SuppressWarnings("unchecked") @Test void originalAchievementCombatTracesUseAcceptedDeathAndMissOwnersAndPersistEveryTier()throws Exception{
        for(var value:(List<?>)AchievementStoreTest.fixture().get("combat")){var trace=(Map<String,Object>)value;var ref=new java.util.concurrent.atomic.AtomicReference<HostedAchievements>();
            try(var h=new Harness(temp.resolve(trace.get("name")+".vxl"),false,(host,loop,ports)->{var prepared=HostedAchievements.prepare(null).toCompletableFuture().get(4,java.util.concurrent.TimeUnit.SECONDS);ref.set(new HostedAchievements(host,prepared,s->Optional.empty(),ports,loop,(Boolean)trace.get("count_bots")));})){
                h.ports.monotonic=1000;var killer=h.admit(0,2,new Vector(10,10,97));var victim=h.admit(1,3,new Vector(15,10,97));victim.bot((Boolean)trace.get("bot"));int tool=((Number)trace.get("tool")).intValue();killer.fire().tool(tool,true);
                for(var stepValue:(List<?>)trace.get("steps")){var step=(Map<String,Object>)stepValue;if(step.containsKey("round"))ref.get().round((Boolean)step.get("round"));else if(Boolean.TRUE.equals(step.get("miss")))ref.get().resolved(new net.battlespades.combat.ShotTally.Resolved(killer,tool,false));else{h.ports.monotonic+=((Number)step.get("advance")).doubleValue();assertTrue(h.match.lives().die(1,0,((Number)step.get("kill")).intValue()));h.match.lives().respawn(1);}}
                var handed=ref.get().takeUnreported("name:p0").toCompletableFuture();long deadline=System.nanoTime()+4_000_000_000L;while(!handed.isDone()){assertTrue(System.nanoTime()<deadline,trace.get("name").toString());h.loop.pump(h.loop.nowNanos()+1_000_000);Thread.onSpinWait();}assertEquals(trace.get("unlocked"),handed.join().stream().sorted().toList(),trace.get("name").toString());var expected=new TreeMap<String,Long>();((Map<String,Number>)trace.get("progress")).forEach((k,v)->expected.put(k,v.longValue()));assertEquals(expected,ref.get().progress("name:p0"),trace.get("name").toString());assertTrue(ref.get().faults().isEmpty());
            }
        }
    }
    @SuppressWarnings("unchecked") @Test void originalObjectiveTracesRetainCarryHistorySubroundScopesAndHillSurvivors()throws Exception{
        List<?> traces;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/golden/achievement-objectives.json"))){traces=(List<?>)MetadataDocument.json(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        for(var value:traces){var trace=(Map<String,Object>)value;var ref=new java.util.concurrent.atomic.AtomicReference<HostedAchievements>();try(var h=new Harness(temp.resolve(trace.get("name")+".vxl"),false,(host,loop,ports)->ref.set(new HostedAchievements(host,HostedAchievements.prepare(null).toCompletableFuture().get(),session->Optional.empty(),ports,loop,true)))){
            var engine=ref.get();var players=new PlayerLife[]{h.admit(0,2,new Vector(10,10,97)),h.admit(1,3,new Vector(10,10,97)),h.admit(2,3,new Vector(10,10,97))};players[2].bot((Boolean)trace.get("bot"));var zone=new net.battlespades.match.ObjectiveZone(0,5,15,5,15,90,100,new Vector(10,10,97));
            for(var stepValue:(List<?>)trace.get("steps")){var step=(List<Object>)stepValue;String op=(String)step.getFirst();switch(op){
                case "zombie_start","zombie_end"->h.match.zombieRound(new HostedZombieRuntime.RoundObservation(op.equals("zombie_start"),((List<Number>)step.get(1)).stream().map(i->players[i.intValue()]).toList()));
                case "vip_start","vip_end"->{var ids=(List<Number>)step.get(1);h.match.vipRound(new HostedVipRuntime.RoundObservation(op.equals("vip_start"),Map.of(2,players[ids.get(0).intValue()],3,players[ids.get(1).intValue()]),op.equals("vip_start")?Set.of(2,3):new HashSet<>(((List<Number>)step.get(2)).stream().map(Number::intValue).toList())));}
                case "kill"->{var victim=players[n(step,2)];assertTrue(h.match.lives().die(victim.id(),n(step,1),n(step,3)),trace.get("name").toString());h.match.lives().respawn(victim.id());victim.body().position(new Vector(10,10,97));}
                case "match_start"->engine.matchStarted();case "match_end"->engine.matchEnded(h.match,n(step,1));
                case "carry"->h.match.carryObservation(new HostedCarryRuntime.Observation((String)step.get(1),step.get(2)==null?null:players[n(step,2)],step.get(3)==null?null:players[n(step,3)],n(step,4),(Boolean)step.get(5),step.get(6)==null?null:((Number)step.get(6)).doubleValue(),new Vector(10,10,97),n(step,7)));
                case "intel_kill"->engine.intelCarrierKilled(h.match,players[n(step,1)],players[n(step,2)],n(step,3),n(step,4));case "intel_return"->engine.intelReturned(h.match,n(step,1));case "intel_pickup"->engine.intelPickedUp(n(step,1));
                case "hill_score"->h.match.hillEvent(new HostedZoneRuntime.Scored(zone,players[n(step,1)]));case "hill_contest"->h.match.hillEvent(new HostedZoneRuntime.ContestWon(((List<Number>)step.get(1)).stream().map(i->players[i.intValue()]).toList(),(Boolean)step.get(2)));
                case "hill_kill"->h.match.hillEvent(new HostedZoneRuntime.HillKill(players[n(step,1)],players[n(step,2)],(Boolean)step.get(3),(Boolean)step.get(4)));
                case "hill_expired"->{var inside=new HashMap<Integer,PlayerLife>();var teams=new HashMap<Integer,Integer>();for(var id:(List<Number>)step.get(1)){var p=players[id.intValue()];inside.put(p.id(),p);teams.put(p.id(),p.team());}h.match.hillEvent(new HostedZoneRuntime.Expired(zone,inside,teams));}
                case "move"->players[n(step,1)].body().position(new Vector(((Number)step.get(2)).doubleValue(),((Number)step.get(3)).doubleValue(),((Number)step.get(4)).doubleValue()));case "dead"->h.match.lives().die(n(step,1),null,0);case "tick"->engine.tick(h.match,((Number)step.get(1)).doubleValue());default->fail(op);
            }}
            var expected=(List<Map<String,Object>>)trace.get("players");for(int i=0;i<3;i++){if(players[i].bot())continue;String identity="name:p"+i;var handoff=engine.takeUnreported(identity).toCompletableFuture();long deadline=System.nanoTime()+4_000_000_000L;while(!handoff.isDone()){assertTrue(System.nanoTime()<deadline);h.loop.pump(h.loop.nowNanos()+1_000_000);Thread.onSpinWait();}assertEquals(expected.get(i).get("unlocked"),handoff.join().stream().sorted().toList(),trace.get("name")+" P"+i);var counters=new TreeMap<String,Long>();((Map<String,Number>)expected.get(i).get("progress")).forEach((k,v)->counters.put(k,v.longValue()));assertEquals(counters,engine.progress(identity),trace.get("name")+" P"+i);}assertTrue(engine.faults().isEmpty(),engine.faults().toString());
        }}
    }
    private static int n(List<?> row,int index){return ((Number)row.get(index)).intValue();}
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
