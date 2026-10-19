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
        Harness(Path path,String settings)throws Exception{this(path,"tdm",settings);}
        Harness(Path path,String code,String settings)throws Exception{
            StandardPorts.writeMap(path);if(Set.of("ctf","cctf").contains(code)){byte[] column={2,100,100,0,1,2,3,4,0,(byte)239,(byte)239,(byte)239,1,2,3,4};byte[] data=new byte[512*512*column.length];for(int i=0;i<512*512;i++)System.arraycopy(column,0,data,i*column.length,column.length);Files.write(path,data);}var prepared=PreparedMap.prepare(MapAssembly.load(path,code));var config=new SceneConfig("Entities",27015,0,ModeCatalog.get(code),"Entities",false,false,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            var raw=org.tomlj.Toml.parse("[modes."+code+"]\ntime_limit=0\n"+settings);var factory=new StandardMatchFactory(config,raw,ports,new SourceRandom(0),loop,List.of("Entities"),candidate->new StandardMatchFactory.Ports(ports,ports,ports,ports,ports,ports));
            state=new HostedSceneState("Entities",code,prepared,1,10000,factory.modes());host=new ApplicationHost(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),EnetSettings.defaults(),4,JoinSettings.defaults(),state.scene(),new BoundedSnapshotCompression(1,2));state.attach(host);match=(StandardMatchDomain)state.domain();
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

    private static PlayerLife actor(Harness h,int id,int team,int cid,Integer... tools){
        h.match.lives().admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"P"+id),new ClassCatalog.Selection(cid,List.of(tools),List.of(),List.of()),0);var p=h.match.lives().player(id);p.body().position(new Vector(12.5,12.5,97.25));for(int x=12;x<=16;x++)for(int y=12;y<=15;y++)h.match.world().set(new MutableWorld.Cell(x,y,100),true,0x123456,null);return p;
    }
    @Test void turretConsumesLastStockOnceAndMgRelocationRetainsEquipmentWhileForeignMountIsReleased()throws Exception{
        try(var h=new Harness(temp.resolve("Devices.vxl"),"")){
            h.match.mode().start();var engineer=actor(h,0,2,12,5,16,64);var a=h.match.entities().actions();engineer.fire().tool(16,true);
            assertTrue(a.turret(engineer,new Vector(12,13,99),0));assertEquals(1,engineer.inventory().turrets());h.ports.monotonic+=2;
            assertTrue(a.turret(engineer,new Vector(13,13,99),0));assertEquals(0,engineer.inventory().turrets());h.ports.monotonic+=2;assertFalse(a.turret(engineer,new Vector(14,13,99),0));assertEquals(2,h.match.entities().turrets().size());
            var soldier=actor(h,1,2,0,5,15);soldier.fire().tool(15,true);assertTrue(a.machineGun(soldier,new Vector(12,14,99),15));var gun=h.match.entities().registry().all().stream().filter(e->e.behavior instanceof MachineGunBehavior).findFirst().orElseThrow();var behavior=(MachineGunBehavior)gun.behavior;behavior.health=43;behavior.ammo=27;
            engineer.body().position(new Vector(12,14,98));assertTrue(a.use(engineer));assertEquals(gun.id(),engineer.mountedEntityId());engineer.fire().tool(15,true);h.ports.monotonic+=2;assertFalse(a.machineGun(soldier,new Vector(13,14,99),30));assertEquals(43,behavior.health);
            a.retire(engineer);assertNull(engineer.mountedEntityId());assertSame(gun,h.match.entities().registry().get(gun.id()));assertNotEquals(15,engineer.inventory().tool());assertTrue(h.match.entities().turrets().isEmpty());
            assertTrue(a.machineGun(soldier,new Vector(13,14,99),30));var moved=h.match.entities().registry().all().stream().filter(e->e.behavior instanceof MachineGunBehavior).findFirst().orElseThrow();assertNotEquals(gun.id(),moved.id());assertEquals(43,((MachineGunBehavior)moved.behavior).health);assertEquals(27,((MachineGunBehavior)moved.behavior).ammo);h.match.disconnected(1);assertNull(h.match.entities().registry().get(moved.id()));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void canonicalLaunchRejectsBeforeDebitAndC4ShotRetirementPreservesAnotherOwnersRegistry()throws Exception{
        try(var h=new Harness(temp.resolve("Actions.vxl"),"")){
            h.match.mode().start();var p=actor(h,0,2,0,5,12);p.fire().tool(12,true);int before=p.inventory().oriented().get(12);
            h.match.packet(0,new OrientedItemPacket(1,254,12,0,new Vector3f(200,200,30),new Vector3f(75,0,0)));assertEquals(before,p.inventory().oriented().get(12));assertTrue(h.match.entities().projectiles().engine().projectiles().isEmpty());assertEquals(1L,p.anticheat().counts().get("launch_rejected"));
            h.match.packet(0,new OrientedItemPacket(1,254,12,0,new Vector3f(12.5f,12.5f,97.25f),new Vector3f(500,0,0)));assertEquals(before-1,p.inventory().oriented().get(12));var shot=h.match.entities().projectiles().engine().projectiles().getFirst();assertEquals(0,shot.throwerId);assertEquals(132.5,shot.velocity.x());assertEquals(0,h.match.entities().registry().get(shot.entityId).playerId);
            h.match.packet(0,new OrientedItemPacket(1,254,12,0,new Vector3f(12.5f,12.5f,97.25f),new Vector3f(75,0,0)));assertEquals(1,h.match.entities().projectiles().engine().projectiles().size());
            var miner=actor(h,1,3,3,5,59);miner.fire().tool(59,true);var a=h.match.entities().actions();assertTrue(a.place(miner,59,new Vector(12,13,100),4));int charge=miner.chargeIds().getFirst();int scopes=h.ports.blastLog.size();h.match.entities().registry().damageEntity(charge,1,p,h.match.entities().context());assertNull(h.match.entities().registry().get(charge));assertTrue(miner.chargeIds().isEmpty());assertEquals(scopes,h.ports.blastLog.size());assertNotNull(h.match.entities().registry().get(shot.entityId));h.match.disconnected(0);assertTrue(h.match.entities().projectiles().engine().projectiles().isEmpty());assertNull(h.match.entities().registry().get(shot.entityId));
        }
    }
    @Test void gooUsesFullSourceSplashFootprintAndFractionalContactStopsImmediatelyOnExit()throws Exception{
        try(var h=new Harness(temp.resolve("Effects.vxl"),"")){
            h.match.mode().start();var owner=actor(h,0,2,0,5);var victim=actor(h,1,3,0,5);owner.body().position(new Vector(200,200,90));victim.body().position(new Vector(100.5,100.5,97.75));victim.endProtection(h.ports.monotonic);
            h.match.world().set(new MutableWorld.Cell(100,100,100),true,0xffffff,null);h.match.entities().effects().impact(new Vector(97,99,99),owner,true);var goo=h.match.entities().registry().all().stream().filter(e->e.kind.equals("blockgoo")).toList();assertEquals(1,goo.size(),"source radius+0.5 includes offset (3,1,1)");
            var effects=h.match.entities().effects();effects.gooTick();assertTrue(effects.touchingGoo(victim));assertEquals(98,victim.health());h.ports.wall+=.3;effects.gooTick();assertEquals(95,victim.health());victim.body().position(new Vector(120,120,90));h.ports.wall+=.3;effects.gooTick();assertFalse(effects.touchingGoo(victim));assertEquals(95,victim.health());h.match.disconnected(0);assertTrue(h.match.entities().registry().all().stream().noneMatch(e->e.kind.equals("blockgoo")));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void jetpackCorpseCompletesOnceAndCleanupPrecedesFreshLife()throws Exception{
        try(var h=new Harness(temp.resolve("Corpses.vxl"),"")){
            h.match.mode().start();var p=actor(h,0,2,2,5,66);assertEquals(66,p.jetpack());p.body().position(new Vector(12.5,12.5,80));h.match.lives().die(0,null,0);assertTrue(h.match.corpses().replicate(p));var before=p.position();for(int i=0;i<61;i++)h.match.corpses().tick();assertFalse(h.match.corpses().replicate(p));assertNotEquals(before,p.position());assertEquals(1,h.match.corpses().catchup().size());assertEquals(1,h.match.entities().registry().all().stream().filter(e->e.kind.equals("grave")).count());int scopes=h.ports.blastLog.size();h.match.corpses().tick();assertEquals(scopes,h.ports.blastLog.size());h.match.lives().respawn(0);assertTrue(h.match.corpses().catchup().isEmpty());assertFalse(h.match.corpses().replicate(p));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void acceptedTeammateDamageCapsEachIncidentDecaysAndRetainsConnectionCountersAcrossLives()throws Exception{
        try(var h=new Harness(temp.resolve("Conduct.vxl"),"")){
            h.match.mode().start();var source=actor(h,0,2,0,5);var ally=actor(h,1,2,0,5);var enemy=actor(h,2,3,0,5);ally.endProtection(h.ports.monotonic);enemy.endProtection(h.ports.monotonic);
            assertFalse(h.match.lives().damage(1,50,0,0));assertEquals(50,ally.health());assertTrue(h.match.lives().damage(1,50,0,0));h.match.lives().respawn(1);ally.endProtection(h.ports.monotonic);assertTrue(h.match.lives().damage(1,100,0,0));assertTrue(h.match.lives().damage(2,100,0,0));
            var conduct=h.match.conduct();var harm=conduct.snapshot(source);assertEquals(6,harm.griefPoints());assertEquals(2,harm.teamKills());assertEquals(200,harm.teamDamage());assertFalse(harm.kickPending());h.ports.monotonic+=60;assertEquals(5,conduct.snapshot(source).griefPoints());h.match.lives().die(0,null,10);h.match.lives().respawn(0);assertEquals(200,conduct.snapshot(source).teamDamage());h.match.disconnected(0);assertEquals(0,conduct.snapshot(source).teamDamage());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void commandsKeepPermissionsAndBudgetsSeparateAndResumeWorkerCompletionOnOwner()throws Exception{
        try(var h=new Harness(temp.resolve("Commands.vxl"),"")){
            h.match.mode().start();var p=actor(h,0,2,0,5);var calls=new ArrayList<CommandRegistry.Context>();var social=h.match.social();social.commands().register(new CommandRegistry.Command("record",List.of("r"),false,false,"","",ctx->{calls.add(ctx);return java.util.concurrent.CompletableFuture.completedFuture(null);}));
            social.muted(p,true);for(int i=0;i<6;i++)h.match.packetAsync(0,new SimplePackets.ChatMessage(254,1,"/R  hello\u2003world")).toCompletableFuture().join();assertEquals(5,calls.size());assertEquals(List.of("hello","world"),calls.getFirst().args());assertSame(p,calls.getFirst().player());assertEquals(1L,p.anticheat().counts().get("command_rate_limited"));h.ports.monotonic+=1;h.match.packetAsync(0,new SimplePackets.ChatMessage(254,0,"/record again")).toCompletableFuture().join();assertEquals(6,calls.size());
            var notices=new ArrayList<String>();var registry=new CommandRegistry(h.match,(player,text)->notices.add(text),h.loop,false);var gate=new java.util.concurrent.CompletableFuture<Void>();registry.register(new CommandRegistry.Command("guarded",List.of(),true,false,"","",ctx->{fail("Non-admin handler ran");return gate;}));registry.execute(p,"guarded secret").toCompletableFuture().join();assertEquals(List.of("You don't have permission to use this command."),notices);
            registry.register(new CommandRegistry.Command("await",List.of("a"),false,false,"","",ctx->gate));var result=registry.execute(p,"A").toCompletableFuture();assertFalse(result.isDone());var worker=new Thread(()->gate.complete(null));worker.start();worker.join();assertFalse(result.isDone());h.loop.pump(0);assertTrue(result.isDone());result.join();assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void ctfCaptureStopsThePassAtTheWinningScoreAndPreservesMapResources()throws Exception{
        try(var h=new Harness(temp.resolve("Ctf.vxl"),"ctf","score_limit=1\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var c=h.match.ctf().orElseThrow();var p=actor(h,0,2,0,5);var other=actor(h,1,3,0,5);long resources=h.match.entities().registry().all().stream().filter(e->e.kind.startsWith("map_")).count();assertTrue(resources>0);assertEquals(2,h.match.entities().registry().all().stream().filter(e->e.kind.equals("base")&&!e.wireVisible).count());assertTrue(h.match.entities().registry().staticEntities().stream().noneMatch(e->e.type==1));
            assertTrue(c.pickup(p,3));assertTrue(c.pickup(other,2));assertEquals(16,p.pickupId());assertTrue(p.pickupBurdensome());assertEquals(100,h.match.binding().row(p).score);p.body().position(c.state(2).base());other.body().position(c.state(3).base());c.tick();assertTrue(h.match.mode().clock().ended);assertEquals(2,h.match.mode().winner());assertEquals(1,h.match.teams().getFirst().score);assertEquals(0,h.match.teams().getLast().score);assertEquals(110,h.match.binding().row(p).score);assertEquals(1,h.match.binding().row(p).captures);assertNull(p.pickupId());assertSame(other,c.state(2).holder());assertEquals(resources,h.match.entities().registry().all().stream().filter(e->e.kind.startsWith("map_")).count());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void ctfCarryExposureAndEscortHaveOnePayoutPerDeadlineAndReplayDedicatedCarrierState()throws Exception{
        try(var h=new Harness(temp.resolve("Carry.vxl"),"ctf","[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var c=h.match.ctf().orElseThrow();var p=actor(h,0,2,0,5);var escort=actor(h,1,2,0,5);p.body().position(new Vector(200,200,90));escort.body().position(new Vector(201,200,90));assertTrue(c.pickup(p,3));c.tick();h.ports.wall+=31;c.tick();assertTrue(c.state(3).exposed());assertEquals(150,h.match.binding().row(p).score);assertEquals(10,h.match.binding().row(escort).score);c.tick();assertEquals(150,h.match.binding().row(p).score);
            var reveal=new ArrayList<GamePacket>();c.reveal(reveal::add);assertEquals(2,reveal.stream().filter(MinimapZone.class::isInstance).count());assertTrue(reveal.stream().anyMatch(packet->packet instanceof SimplePackets.PickPickup pickup&&pickup.player_id()==0&&pickup.pickup_id()==16));assertTrue(reveal.contains(MatchPackets.ChangePlayer.marker(0,true)));
            h.match.mode().start();assertNull(p.pickupId());assertFalse(c.state(3).exposed());assertNull(c.state(3).holder());assertEquals(2,h.match.entities().registry().all().stream().filter(e->e.kind.equals("intel")).count());assertEquals(0,h.match.binding().row(p).score);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void ctfRejectsForgedDropThenReturnsAfterCooldownAndReleasesCarrierBeforeSlotReuse()throws Exception{
        try(var h=new Harness(temp.resolve("Drop.vxl"),"ctf","[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var c=h.match.ctf().orElseThrow();var p=actor(h,0,2,0,5);p.body().position(new Vector(200,200,90));assertTrue(c.pickup(p,3));h.match.packetAsync(0,new DropPickup(1,254,16,new DropPickup.Position(400,400,90),new DropPickup.Position(0,0,0))).toCompletableFuture().join();assertSame(p,c.state(3).holder());assertEquals(16,p.pickupId());
            h.match.packetAsync(0,new DropPickup(1,254,16,new DropPickup.Position(200,200,90),new DropPickup.Position(100,0,0))).toCompletableFuture().join();assertNull(c.state(3).holder());assertNull(p.pickupId());double dropped=c.state(3).dropped();p.body().position(c.state(3).position());c.tick();assertNull(c.state(3).holder());p.body().position(new Vector(500,500,90));h.ports.wall=dropped+60;c.tick();assertEquals(0,c.state(3).dropped());assertEquals(c.state(3).home(),c.state(3).position());
            assertTrue(c.pickup(p,3));h.match.disconnected(0);assertNull(c.state(3).holder());var replacement=actor(h,0,2,0,5);c.retire(p);assertNull(replacement.pickupId());assertNull(c.state(3).holder());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void ctfDeathCreditsInterceptBeforeDroppingAndOwnIntelRequirementBlocksCapture()throws Exception{
        try(var h=new Harness(temp.resolve("Intercept.vxl"),"ctf","intel_in_own_base_to_score=true\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var c=h.match.ctf().orElseThrow();var carrier=actor(h,0,2,0,5);var enemy=actor(h,1,3,0,5);assertTrue(c.pickup(carrier,3));assertTrue(c.pickup(enemy,2));carrier.body().position(c.state(2).base());enemy.body().position(new Vector(500,500,90));c.tick();assertEquals(0,h.match.binding().row(carrier).captures);assertEquals(100,h.match.binding().row(carrier).score);
            h.match.lives().die(0,1,0);h.match.events().tick(1).toCompletableFuture().join();assertNull(c.state(3).holder());assertNull(carrier.pickupId());assertEquals(1,h.match.binding().row(enemy).profileStats.values.get(58).count());assertEquals(50,h.match.binding().row(enemy).profileStats.values.get(58).score());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void classicCtfUsesDeuceMenusWeaponDefaultsOperatorOverridesAndPersistentDroppedIntel()throws Exception{
        try(var h=new Harness(temp.resolve("Classic.vxl"),"cctf","[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var info=h.match.scene().initialInfo(0);var state=h.match.scene().stateData(0,0);assertEquals(8,info.mode().key());assertEquals(8,state.modeType());assertEquals(1,info.features().classic());assertEquals(0,info.features().minimap());assertEquals(1,info.allowShootingHoldingIntel());assertFalse(info.disabledTools().contains(37));assertFalse(info.disabledTools().contains(38));assertTrue(info.disabledTools().contains(22));assertEquals(List.of(5),state.team1().classes());assertFalse(state.team1().lockedClass());assertEquals(5,state.scoreLimit());
            h.match.scene().admit(0,new SimplePackets.NewPlayerConnection(2,0,0,0,"Deuce"),null,0);var p=h.match.lives().player(0);assertEquals(5,p.selection().classId());var c=h.match.ctf().orElseThrow();p.body().position(new Vector(200,200,90));assertTrue(c.pickup(p,3));assertTrue(h.match.shootWithIntel());c.drop(p,new DropPickup.Position(200,200,90),new DropPickup.Position(0,0,0),false);p.body().position(new Vector(500,500,90));h.ports.wall+=61;c.tick();assertTrue(c.state(3).dropped()>0);
            h.match.packet(0,new PlayerPackets.SetClassLoadout(254,5,0,List.of(38),List.of(),List.of()));h.match.events().tick(1).toCompletableFuture().join();assertFalse(p.alive());assertEquals(1,h.match.corpses().hittable().size());h.match.lives().respawn(0);assertTrue(p.selection().loadout().contains(38));h.match.input(0,new LiveClientInput(10,254,false,38,1,0,0,0,0,24,0),10);assertEquals(38,p.inventory().weapon());assertTrue(h.match.corpses().hittable().isEmpty());assertTrue(h.ports.errors.isEmpty());
        }
        var rules=GameRules.serverDefaults().apply(Map.of("RULE_ENABLE_WEAPON_CLASSIC_SMG",false,"RULE_CTF_ENABLE_SHOOT_WITH_INTEL",false));var settings=net.battlespades.match.CtfMode.Settings.from(org.tomlj.Toml.parse(""),rules,"cctf");assertFalse(settings.shootWithIntel());assertFalse(settings.autoReturn());
    }
    @Test void ctfRecoversBuriedHomeAndAcceptsOnlyOpenBasePitsWithinConfiguredDepth()throws Exception{
        try(var h=new Harness(temp.resolve("Recovery.vxl"),"ctf","[objectives]\nobjective_entomb_seconds=0\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var c=h.match.ctf().orElseThrow();var p=actor(h,0,2,0,5);p.body().position(new Vector(500,500,90));var old=c.state(3).home();h.match.world().set(new MutableWorld.Cell((int)old.x(),(int)old.y(),(int)(old.z()+2.25)-1),true,0x123456,null);c.tick();assertEquals(old.z()-1,c.state(3).home().z());assertEquals(c.state(3).home(),c.state(3).position());
            var base=c.state(2).base();var column=new MutableWorld.Cell((int)base.x(),(int)base.y(),(int)(base.z()+2.25));for(int z=column.z();z<column.z()+12;z++)h.match.world().set(new MutableWorld.Cell(column.x(),column.y(),z),false,0,null);p.body().position(new Vector(base.x(),base.y(),base.z()+10));assertTrue(c.atBase(p,2));h.match.world().set(column,true,0xffffff,null);assertFalse(c.atBase(p,2));p.body().position(new Vector(base.x(),base.y(),base.z()+25));assertFalse(c.atBase(p,2));assertTrue(h.ports.errors.isEmpty());
        }
    }
}
