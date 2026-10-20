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
    private static PlayerLife survivor(Harness h,int id){return survivor(h,id,2);}
    private static PlayerLife survivor(Harness h,int id,int team){h.match.scene().admit(id,new SimplePackets.NewPlayerConnection(team,5,0,0,"S"+id),null,3);var p=h.match.lives().player(id);p.bot(true);p.body().position(new Vector(250.5,250.5,97.25));return p;}
    private static void modeTick(Harness h,int tick){h.match.events().tick(tick).toCompletableFuture().join();}
    @Test void zombieOutbreakAndCombatConversionKeepPositionSkipGravesAndUseTenSecondOrdinaryRespawns()throws Exception{
        try(var h=new Harness(temp.resolve("Infect.vxl"),"zom","infection_delay=0\nfirst_infected=1\n")){
            h.match.mode().start();survivor(h,0);survivor(h,1);survivor(h,2);modeTick(h,1);var z=h.match.zombie().orElseThrow();assertEquals(HostedZombieRuntime.Phase.ACTIVE,z.phase());assertEquals(1,z.patientZeros().size());var infected=z.patientZeros().iterator().next();assertEquals(4,infected.selection().classId());assertEquals(new Vector(250.5,250.5,97.25),infected.position());assertTrue(h.match.entities().registry().all().stream().noneMatch(e->e.kind.equals("grave")));var human=h.match.lives().players().stream().filter(p->p.team()==3).findFirst().orElseThrow();h.match.lives().die(human.id(),infected.id(),0);assertEquals(0,human.lastDeath().respawnTime());modeTick(h,2);assertTrue(human.alive());assertEquals(2,human.team());assertEquals(4,human.selection().classId());assertEquals(100,h.match.binding().row(infected).score);assertTrue(h.match.entities().registry().all().stream().noneMatch(e->e.kind.equals("grave")));assertTrue(z.lastSurvivor().isPresent());
            h.match.lives().die(infected.id(),null,0);assertEquals(10,infected.lastDeath().respawnTime());assertTrue(h.match.entities().registry().all().stream().anyMatch(e->e.kind.equals("grave")));h.ports.wall+=9;h.match.lives().processRespawns();assertFalse(infected.alive());h.ports.wall++;h.match.lives().processRespawns();assertTrue(infected.alive());assertEquals(0,h.match.zombie().orElseThrow().respawnDelay(human,5)-10);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void zombieRoundScoresLastSurvivorAndRestoresSavedSelectionsThroughBoundedReset()throws Exception{
        try(var h=new Harness(temp.resolve("Round.vxl"),"zom","infection_delay=0\nfirst_infected=1\nscore_limit=2\nround_intermission=1\nround_respawns_per_tick=1\n")){
            h.match.mode().start();survivor(h,0);survivor(h,1);modeTick(h,1);var z=h.match.zombie().orElseThrow();var last=z.lastSurvivor().orElseThrow();h.ports.wall+=10;modeTick(h,2);assertEquals(350,h.match.binding().row(last).score);z.timeout();assertEquals(550,h.match.binding().row(last).score);assertEquals(1,h.match.teams().get(1).score);assertFalse(h.match.mode().clock().ended);h.loop.pump(1_000_000_000L);assertEquals(HostedZombieRuntime.Phase.RESETTING,z.phase());var before=h.match.lives().players().stream().mapToLong(PlayerLife::generation).sum();modeTick(h,3);assertEquals(before+1,h.match.lives().players().stream().mapToLong(PlayerLife::generation).sum());modeTick(h,4);assertTrue(h.match.lives().players().stream().allMatch(p->p.team()==3&&p.selection().classId()==0));modeTick(h,5);assertTrue(z.active());z.timeout();assertTrue(h.match.mode().clock().ended);assertEquals(3,h.match.mode().winner());assertEquals(2,z.rounds());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void zombieDeparturesReplaceLastInfectedAndAbortUnderpopulationWithoutAwardingRound()throws Exception{
        try(var h=new Harness(temp.resolve("Population.vxl"),"zom","infection_delay=0\nfirst_infected=1\n")){
            h.match.mode().start();survivor(h,0);survivor(h,1);survivor(h,2);modeTick(h,1);var z=h.match.zombie().orElseThrow();var departed=z.patientZeros().iterator().next();h.match.disconnected(departed.id());assertEquals(1,h.match.lives().players().stream().filter(p->p.team()==2).count());var replacement=survivor(h,departed.id());assertEquals(2,replacement.team());assertFalse(z.patientZeros().contains(replacement));var human=h.match.lives().players().stream().filter(p->p.team()==3).findFirst().orElseThrow();assertFalse(z.botRetireSafe(human));for(var p:new ArrayList<>(h.match.lives().players()))if(p!=human)h.match.disconnected(p.id());assertEquals(HostedZombieRuntime.Phase.WAITING,z.phase());assertEquals(0,z.rounds());assertEquals(List.of(0L,0L),h.match.teams().stream().map(t->t.score).toList());assertEquals(3,human.team());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"hc_tdm","hc_ctf","hc_cctf","hc_zom","hc_vip","hc_mh","hc_tc","hc_dia","hc_dem","hc_oc"})
    void allHardcoreFactoriesApplyNativeRoleMenusAndRulesWithoutLosingTheirBaseObjectives(String code)throws Exception{
        try(var h=new Harness(temp.resolve(code+".vxl"),code,"")){
            h.match.mode().start();var info=h.match.scene().initialInfo(3);var state=h.match.scene().stateData(0,3);assertEquals(3,h.match.scene().requiredCapability());assertEquals(0,info.features().classic());assertEquals("",info.features().textureSkin());assertEquals(35,info.loadouts().size());assertEquals(Hardcore.CLASSES,state.team2().classes());assertEquals(1,state.gravity());assertTrue(info.customRules().contains(new InitialInfo.Rule("BATTLESPADES_HARDCORE","1")));assertEquals(0,info.enableCorpseExplosion());assertTrue(info.loadouts().stream().noneMatch(l->l.tools().contains(23)));assertEquals(51./64,info.movementSpeedMultipliers().get(5));var p=survivor(h,0);assertEquals(5,p.selection().classId());assertTrue(p.selection().loadout().containsAll(List.of(60,2,72)),p.selection().toString());assertFalse(p.selection().loadout().contains(23));h.match.mode().deactivate().toCompletableFuture().join();assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void hardcoreCombatBodyArmorCorpseTeabagAndRuleRestorationUseExistingOwners()throws Exception{
        try(var h=new Harness(temp.resolve("Hardcore.vxl"),"hc_tdm","")){
            h.match.mode().start();var a=actor(h,0,2,5,60,2);var victim=actor(h,1,3,0,61,2);var profile=ClassCatalog.movement(0,"hc_tdm",Hardcore.rules(GameRules.serverDefaults()));assertEquals(.77f,profile.jump());assertEquals(.7f*(float)(44./64),profile.acceleration());assertFalse(h.match.scene().initialInfo(3).disabledClasses().contains(0));h.match.lives().die(victim.id(),a.id(),0);assertTrue(h.match.entities().registry().all().stream().noneMatch(e->e.kind.equals("grave")));var row=h.match.binding().row(a);long before=row.score;for(int i=0;i<3;i++){h.ports.monotonic+=.2;h.match.inputs().receive(a,new LiveClientInput(i*2,0,false,5,1,0,0,0,32,0,0),i*2);h.match.inputs().receive(a,new LiveClientInput(i*2+1,0,false,5,1,0,0,0,0,0,0),i*2+1);}assertEquals(before+2,row.score);
            var prepared=PreparedMap.prepare(MapAssembly.load(temp.resolve("Hardcore.vxl"),"tdm"));h.state.install("Normal","tdm",prepared);var normal=(StandardMatchDomain)h.state.domain();assertFalse(normal.hardcore());assertEquals(0,normal.scene().requiredCapability());assertEquals(1,normal.scene().initialInfo(0).enableCorpseExplosion());assertFalse(normal.scene().initialInfo(0).disabledTools().contains(23));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void hardcoreVipKeepsItsRoleHealthAndBodyWhileHardcoreZombieRestoresItsSurvivorRole()throws Exception{
        try(var h=new Harness(temp.resolve("HCboss.vxl"),"hc_vip","selection_delay=0\n")){
            h.match.mode().start();var a=survivor(h,0);survivor(h,1,3);a.health(63);var body=a.body();h.match.vip().orElseThrow().tick();assertSame(body,a.body());assertEquals(5,a.selection().classId());assertEquals(63,a.health());assertTrue(h.match.vip().orElseThrow().liveVip(a));assertTrue(h.ports.errors.isEmpty());
        }
        try(var h=new Harness(temp.resolve("HCzombie.vxl"),"hc_zom","infection_delay=0\nfirst_infected=1\n")){
            h.match.mode().start();survivor(h,0);survivor(h,1);modeTick(h,1);var z=h.match.zombie().orElseThrow();var p=z.patientZeros().iterator().next();assertEquals(4,p.selection().classId());z.timeout();h.loop.pump(5_000_000_000L);modeTick(h,2);assertTrue(h.match.lives().players().stream().allMatch(v->v.team()==3&&v.selection().classId()==5));assertTrue(h.ports.errors.isEmpty());
        }
    }
    private Path demolitionMap(String name)throws Exception{var path=temp.resolve(name+".vxl");Files.writeString(temp.resolve(name+".json"),"{\"team_one_base_point\":[40,40,100],\"team_one_base_w_h_d\":[10,10,2],\"team_two_base_point\":[80,40,100],\"team_two_base_w_h_d\":[10,10,2]}");return path;}
    @Test void demolitionBuildFreezesNewCellsLocksPhysicsThenTracksCollapseRepairAndReleasesLocks()throws Exception{
        try(var h=new Harness(demolitionMap("Build"),"dem","build_state_length=2\n")){
            var p=actor(h,0,2,0,5);h.match.mode().start();var d=h.match.demolition().orElseThrow();assertEquals(HostedDemolitionRuntime.Phase.BUILDING,d.phase());assertFalse(h.match.scene().stateData(0,0).team1().showScore());p.body().position(new Vector(200,200,97));p.body().update(1./60,1,List.of());assertTrue(p.position().x()<=d.base(2).zone().x1());
            var built=new MutableWorld.Cell(40,40,99);h.match.world().set(built,true,0xffffff,null);h.ports.wall+=2;d.tick();assertTrue(d.base(2).objective().contains(built));assertEquals(243,d.base(2).objective().size());p.body().position(new Vector(200,200,97));p.body().update(1./60,1,List.of());assertTrue(p.position().x()>d.base(2).zone().x1());
            h.match.world().destroyBlocks(List.of(built));assertTrue(d.base(2).destroyed().contains(built));h.match.world().set(built,true,0xffffff,null);assertFalse(d.base(2).destroyed().contains(built));h.match.world().set(built,true,0xabcdef,null);assertFalse(d.base(2).destroyed().contains(built));
            d.deactivate();h.match.world().destroyBlocks(List.of(built));assertFalse(d.base(2).destroyed().contains(built));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void demolitionScoresCommittedEnemyDamagePreventsSelfRepairFarmingAndForgetsOldIdentities()throws Exception{
        try(var h=new Harness(demolitionMap("Credit"),"dem","build_state_length=0\n")){
            h.match.mode().start();var d=h.match.demolition().orElseThrow();var p=actor(h,0,2,0,5);var q=actor(h,1,3,0,5);var enemy=d.base(3).objective().stream().sorted(Comparator.comparingInt(MutableWorld.Cell::x).thenComparingInt(MutableWorld.Cell::y)).limit(50).toList();h.match.world().destroyBlocks(enemy);var cells=enemy.stream().map(c->new BlockDamageModel.Cell(c.x(),c.y(),c.z())).toList();assertEquals(50,d.enemyObjectiveDamage(p,enemy).orElseThrow().count());h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_destroyed",h.match.binding().row(p),cells,true));assertEquals(25,h.match.binding().row(p).score);
            for(var c:enemy)h.match.world().set(c,true,0xffffff,null);h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_built",h.match.binding().row(q),cells));assertEquals(50,h.match.binding().row(q).score);assertEquals(50,h.ports.repairs.get(q));
            h.match.world().destroyBlocks(enemy);h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_destroyed",h.match.binding().row(q),cells,true));for(var c:enemy)h.match.world().set(c,true,0xffffff,null);h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_built",h.match.binding().row(q),cells));assertEquals(50,h.match.binding().row(q).score);assertEquals(50,h.ports.repairs.get(q));
            var old=h.match.binding().row(p);h.match.disconnected(0);var replacement=actor(h,0,2,0,5);h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_destroyed",old,cells,true));assertEquals(0,h.match.binding().row(replacement).score);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void demolitionBothBasesFallTogetherAndRealStrikesPrecedeDrawByThreeSeconds()throws Exception{
        try(var h=new Harness(demolitionMap("Draw"),"dem","build_state_length=0\n")){
            h.match.mode().start();var d=h.match.demolition().orElseThrow();var all=new ArrayList<MutableWorld.Cell>(d.base(2).objective());all.addAll(d.base(3).objective());h.match.world().destroyBlocks(all);d.tick();assertEquals(HostedDemolitionRuntime.Phase.AIRSTRIKE,d.phase());assertTrue(h.match.entities().projectiles().engine().projectiles().isEmpty());h.ports.wall+=5;d.tick();assertEquals(10,h.match.entities().projectiles().engine().projectiles().size());assertTrue(h.match.entities().projectiles().engine().projectiles().stream().allMatch(p->p.throwerId==-1&&p.spec==ProjectileSpec.AIRSTRIKE));assertFalse(h.match.mode().clock().ended);h.ports.wall+=3;d.tick();assertTrue(h.match.mode().clock().ended);assertNull(h.match.mode().winner());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void demolitionTimeoutComparesBaseFractionsAndSingleBaseStrikeAwardsOnePoint()throws Exception{
        try(var h=new Harness(demolitionMap("Win"),"dem","build_state_length=0\n")){
            h.match.mode().start();var d=h.match.demolition().orElseThrow();h.match.world().destroyBlocks(d.base(3).objective().stream().limit(1).toList());assertEquals(2,d.timeWinner());h.match.mode().endByTime();assertEquals(2,h.match.mode().winner());h.match.mode().start();h.match.world().destroyBlocks(new ArrayList<>(d.base(3).objective()));d.tick();h.ports.wall+=5;d.tick();assertEquals(1,h.match.teams().getFirst().score);assertFalse(h.match.mode().clock().ended);h.ports.wall+=3;d.tick();assertEquals(2,h.match.mode().winner());assertTrue(h.ports.errors.isEmpty());
        }
    }
    private static PlayerLife vipActor(Harness h,int id,int team){var p=actor(h,id,team,0,5);p.bot(true);return p;}
    @Test void vipPromotionRetainsBodyPositionGenerationWalletAndUsesRealDamageAndKillPresentation()throws Exception{
        try(var h=new Harness(temp.resolve("Boss.vxl"),"vip","selection_delay=0\n")){
            h.match.mode().start();var a=vipActor(h,0,2);var b=vipActor(h,1,3);var v=h.match.vip().orElseThrow();var body=a.body();var at=a.position();long generation=a.generation();var rosterBefore=h.match.roster().reconcile(Map.of(),-1).known();while(a.inventory().blocks()>10)a.inventory().removeBlock();v.tick();assertEquals(HostedVipRuntime.Phase.ACTIVE,v.phase());assertSame(body,a.body());assertEquals(at,a.position());assertEquals(generation,a.generation());assertEquals(10,a.inventory().blocks());assertEquals(10,a.selection().classId());assertEquals(11,b.selection().classId());var rosterAfter=h.match.roster().reconcile(rosterBefore,-1);assertEquals(rosterBefore,rosterAfter.known());assertTrue(rosterAfter.packets().isEmpty());assertEquals(1,h.ports.vipRounds.size());assertEquals(2,v.incoming(a,5,b));assertEquals(1,v.incoming(a,1,b));
            h.match.packet(0,new PlayerPackets.SetClassLoadout(0,6,1,List.of(5),List.of(),List.of()));assertEquals(10,a.selection().classId());assertFalse(a.pendingSelection().isPresent());assertFalse(v.allowsTeam(a));h.match.lives().die(a.id(),b.id(),0);assertEquals(26,a.lastDeath().killType());assertEquals(255,a.lastDeath().respawnTime());h.match.events().tick(1).toCompletableFuture().join();assertFalse(v.canRespawn(a));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void vipFinalMinuteAllowsCounterattackThenTargetTieEndsMatchWithoutOvertime()throws Exception{
        try(var h=new Harness(temp.resolve("Answer.vxl"),"vip","selection_delay=0\nscore_limit=1\n")){
            h.match.mode().start();vipActor(h,0,2);vipActor(h,1,2);vipActor(h,2,3);vipActor(h,3,3);var v=h.match.vip().orElseThrow();v.tick();var blue=v.vip(2).orElseThrow();var green=v.vip(3).orElseThrow();var survivor=h.match.lives().players().stream().filter(p->p.team()==2&&p!=blue).findFirst().orElseThrow();h.match.lives().die(blue.id(),green.id(),0);h.match.events().tick(1).toCompletableFuture().join();assertEquals(1,h.match.teams().get(1).score);assertFalse(h.match.mode().clock().ended);assertFalse(v.canRespawn(blue));h.ports.monotonic+=5.9;v.tick();assertEquals(100,survivor.health());h.ports.monotonic+=.1;v.tick();assertEquals(99,survivor.health());assertEquals(60,v.remaining(),.00001);assertFalse(v.allowsTeam(survivor));assertFalse(v.botRetireSafe(survivor));h.match.lives().die(green.id(),survivor.id(),0);h.match.events().tick(2).toCompletableFuture().join();assertEquals(List.of(1L,1L),h.match.teams().stream().map(t->t.score).toList());assertTrue(h.match.mode().clock().ended);assertNull(h.match.mode().winner());assertEquals(1,v.rounds());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void vipDepartureHandsCrownToCurrentTeammateAndResetDrainsBoundedLiveBodies()throws Exception{
        try(var h=new Harness(temp.resolve("Handover.vxl"),"vip","selection_delay=0\nround_intermission=1\nround_respawns_per_tick=1\n")){
            h.match.mode().start();vipActor(h,0,2);vipActor(h,1,2);vipActor(h,2,3);vipActor(h,3,3);var v=h.match.vip().orElseThrow();v.tick();var departed=v.vip(2).orElseThrow();h.match.disconnected(departed.id());var successor=v.vip(2).orElseThrow();assertNotSame(departed,successor);assertTrue(v.liveVip(successor));assertEquals(0,h.match.teams().get(1).score);var replacement=vipActor(h,departed.id(),2);assertFalse(v.marked(replacement));var generations=h.match.lives().players().stream().map(PlayerLife::generation).toList();v.timeout();assertEquals(HostedVipRuntime.Phase.INTERMISSION,v.phase());assertEquals(0,v.remaining());h.loop.pump(1_000_000_000L);assertEquals(HostedVipRuntime.Phase.RESETTING,v.phase());v.tick();assertEquals(1,h.match.lives().players().stream().mapToLong(PlayerLife::generation).sum()-generations.stream().mapToLong(Long::longValue).sum());for(int i=0;i<3;i++)v.tick();assertEquals(HostedVipRuntime.Phase.SELECTING,v.phase());v.tick();assertEquals(HostedVipRuntime.Phase.ACTIVE,v.phase());assertTrue(h.ports.errors.isEmpty());
        }
    }
    private Path carryMap(String name)throws Exception{var p=temp.resolve(name+".vxl");Files.writeString(temp.resolve(name+".json"),"{\"diamond_base_points\":[[40,40,97],[80,40,97],[120,40,97]],\"diamond_base_w_h_d\":[[10,10,10],[10,10,10],[10,10,10]],\"occupation_base_point\":[120,40,97],\"occupation_base_w_h_d\":[10,10,10],\"occupation_bomb_points\":[[40,40,100],[60,40,100]]}");return p;}
    @Test void diamondMiningCarryEscortCashAndRotationUseLiveScoresAndPreserveSerials()throws Exception{
        try(var h=new Harness(carryMap("Diamonds"),"dia","score_limit=2\ndiscovery_guarantee_blocks=1\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var d=(HostedDiamondRuntime)h.match.carry().orElseThrow();var p=actor(h,0,2,0,5);var escort=actor(h,1,2,0,5);escort.body().position(new Vector(200,200,97));var cells=List.of(new BlockDamageModel.Cell(60,60,97));h.match.mode().handle(net.battlespades.match.ModeEvents.Event.of("on_blocks_destroyed",h.match.binding().row(p),cells,true)).toCompletableFuture().join();assertEquals(1,d.ground().size());int serial=d.ground().getFirst().serial();assertEquals(10,h.match.binding().row(p).score);
            p.body().position(d.ground().getFirst().position());d.tick();assertTrue(d.carrying(p));assertEquals(serial,p.pickupState());escort.body().position(p.position());h.ports.wall+=5;d.tick();assertEquals(60,h.match.binding().row(p).score);assertEquals(10,h.match.binding().row(escort).score);p.body().position(new Vector(40,40,97));d.tick();assertNull(p.pickupId());assertEquals(160,h.match.binding().row(p).score);assertEquals(1,h.match.teams().getFirst().score);assertEquals(1L,h.match.binding().row(p).profileStats.values.get(48).count());assertTrue(d.bases().get(1).active());assertFalse(d.bases().getFirst().active());
            h.ports.wall+=15;d.mined(p,List.of(new MutableWorld.Cell(90,60,97)),true);p.body().position(d.ground().getFirst().position());d.tick();p.body().position(new Vector(80,40,97));d.tick();assertTrue(h.match.mode().clock().ended);assertEquals(2,h.match.mode().winner());assertEquals(2,h.match.teams().getFirst().score);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void diamondDropRejectsForgedOriginThenLooseCashStopsWinningPassAndRestartKeepsResources()throws Exception{
        try(var h=new Harness(carryMap("Loose"),"dia","score_limit=1\ndiscovery_guarantee_blocks=1\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var d=(HostedDiamondRuntime)h.match.carry().orElseThrow();var p=actor(h,0,2,0,5);d.mined(p,List.of(new MutableWorld.Cell(60,60,97)),true);p.body().position(d.ground().getFirst().position());d.tick();int serial=p.pickupState();h.match.packetAsync(0,new DropPickup(99,254,15,new DropPickup.Position(400,400,97),new DropPickup.Position(0,0,0))).toCompletableFuture().join();assertEquals(serial,p.pickupState());assertTrue(d.ground().isEmpty());p.body().position(new Vector(40,40,97));h.match.packetAsync(0,new DropPickup(99,254,15,new DropPickup.Position(40,40,97),new DropPickup.Position(0,0,0))).toCompletableFuture().join();assertNull(p.pickupId());assertTrue(h.match.mode().clock().ended);assertEquals(10,h.match.binding().row(p).score);assertEquals(1,h.match.teams().getFirst().score);
            h.match.mode().start();assertTrue(d.ground().isEmpty());assertTrue(h.match.entities().registry().all().stream().anyMatch(e->e.kind.startsWith("map_")));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void diamondsExpireWithoutPayingAndDepartedFinderCannotReceiveReusedSlotCredit()throws Exception{
        try(var h=new Harness(carryMap("Expire"),"dia","diamond_lifetime=1\ndiscovery_guarantee_blocks=1\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var d=(HostedDiamondRuntime)h.match.carry().orElseThrow();var p=actor(h,0,2,0,5);d.mined(p,List.of(new MutableWorld.Cell(60,60,97)),true);int id=d.ground().getFirst().id();h.ports.wall++;d.tick();assertTrue(d.ground().isEmpty());assertNull(h.match.entities().registry().get(id));assertEquals(10,h.match.binding().row(p).score);h.ports.wall+=15;d.mined(p,List.of(new MutableWorld.Cell(60,60,97)),true);p.body().position(d.ground().getFirst().position());d.tick();h.match.disconnected(0);var replacement=actor(h,0,2,0,5);replacement.body().position(new Vector(40,40,97));d.tick();assertEquals(0,h.match.binding().row(replacement).score);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void occupationPlantsRealNativeBombBlastAndEndsOnlyAfterExplosion()throws Exception{
        try(var h=new Harness(carryMap("Plant"),"oc","score_limit=3\nbomb_fuse_time=1\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var oc=(HostedOccupationRuntime)h.match.carry().orElseThrow();var p=actor(h,0,2,0,5);p.body().position(new Vector(40,40,97.25));oc.tick();assertTrue(oc.carrying(p));assertEquals(14,p.pickupId());assertFalse(oc.states().getFirst().armed());p.body().position(new Vector(120,40,97.25));oc.retire(p,null);assertTrue(oc.states().getFirst().armed());assertEquals(100,oc.states().getFirst().position().z());assertFalse(h.match.mode().clock().ended);long revision=h.match.world().revision();h.ports.wall++;oc.tick();assertEquals(3,h.match.teams().getFirst().score);assertTrue(h.match.mode().clock().ended);assertEquals(2,h.match.mode().winner());assertTrue(h.match.world().revision()>revision);assertTrue(h.ports.blastLog.contains("stimulus:-1"));assertEquals(1,h.ports.carryEvents.stream().filter(e->e.event().equals("bomb_detonated")&&e.flag()).count());h.match.mode().start();assertTrue(h.match.entities().registry().all().stream().anyMatch(e->e.kind.startsWith("map_")));assertEquals(1,oc.states().size());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void occupationDefenderInterceptionRetainsFuseAndDeparturePreventsSlotReuseDisposalCredit()throws Exception{
        try(var h=new Harness(carryMap("Dispose"),"oc","score_limit=false\nbomb_fuse_time=4\n[objectives]\nobjective_pickup_requires_los=false\n")){
            h.match.mode().start();var oc=(HostedOccupationRuntime)h.match.carry().orElseThrow();var attacker=actor(h,0,2,0,5);var defender=actor(h,1,3,0,5);attacker.body().position(new Vector(40,40,97.25));defender.body().position(new Vector(300,300,97));oc.tick();attacker.body().position(new Vector(80,40,97.25));oc.retire(attacker,null);double deadline=oc.states().getFirst().explodeAt();attacker.body().position(new Vector(300,300,97));h.ports.wall+=2.5;defender.body().position(new Vector(80,40,97.25));oc.tick();assertTrue(oc.carrying(defender));assertTrue(oc.states().getFirst().intercepted());assertEquals(deadline,oc.states().getFirst().explodeAt());h.match.disconnected(1);var replacement=actor(h,1,3,0,5);replacement.body().position(new Vector(300,300,97));h.ports.wall=deadline;oc.tick();assertEquals(0,h.match.binding().row(replacement).score);assertEquals(0,h.match.teams().getFirst().score);assertTrue(oc.states().isEmpty());assertFalse(h.match.mode().clock().ended);h.ports.wall+=10;oc.tick();assertEquals(1,oc.states().size());assertTrue(h.ports.errors.isEmpty());
        }
    }
    private Path zoneMap(String name)throws Exception{
        var path=temp.resolve(name+".vxl");Files.writeString(temp.resolve(name+".json"),"{\"tc_base_points\":[[40,40,97],[80,40,97],[120,40,97]],\"tc_base_w_h_d\":[[12,12,12],[12,12,12],[12,12,12]],\"mh_base_points\":[[40,40,97],[80,40,97],[120,40,97]],\"mh_base_w_h_d\":[[12,12,12],[12,12,12],[12,12,12]]}");return path;
    }
    @Test void territoryContentionFreezesProgressAndEnemyCaptureNeutralizesBeforeWinning()throws Exception{
        try(var h=new Harness(zoneMap("Territory"),"tc","max_active_bases=3\ncapture_rate=100\n")){
            h.match.mode().start();var zones=h.match.zones().orElseThrow();assertEquals(List.of(2,1,3),zones.states().stream().map(HostedZoneRuntime.State::owner).toList());assertEquals(3,h.match.mode().sceneState().activeScoreLimit());
            var blue=actor(h,0,2,0,5,8);var green=actor(h,1,3,0,5);blue.body().position(new Vector(120,40,97));green.body().position(new Vector(120,40,97));zones.tick();assertTrue(zones.states().get(2).contested());assertEquals(1,zones.states().get(2).progress());
            h.ports.wall+=5;zones.tick();assertEquals(25,h.match.binding().row(blue).score);assertEquals(25,h.match.binding().row(green).score);
            green.body().position(new Vector(300,300,97));h.ports.wall+=.5;zones.tick();assertEquals(1,zones.states().get(2).owner());assertEquals(.5,zones.states().get(2).progress());assertEquals(1,h.match.teams().getFirst().score);
            h.ports.wall+=.5;zones.tick();assertEquals(2,zones.states().get(2).owner());assertEquals(125,h.match.binding().row(blue).score);assertEquals(2,h.match.teams().getFirst().score);
            blue.body().position(new Vector(80,40,97));h.ports.wall+=.5;zones.tick();assertTrue(h.match.mode().clock().ended);assertEquals(2,h.match.mode().winner());assertEquals(275,h.match.binding().row(blue).score);assertEquals(3,h.match.teams().getFirst().score);assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void captureResupplyIsImmediateThenIntervalGatedAndAfkOrReusedBodiesCannotHold()throws Exception{
        try(var h=new Harness(zoneMap("Refill"),"tc","[objectives]\nobjective_afk_seconds=1\n")){
            h.match.mode().start();var zones=h.match.zones().orElseThrow();var p=actor(h,0,2,0,5,8);p.body().position(new Vector(40,40,97));p.health(20);int blocks=p.inventory().maxBlocks();while(p.inventory().removeBlock()){}zones.tick();assertEquals(p.maxHealth(),p.health());assertEquals(blocks,p.inventory().blocks());
            p.health(20);h.ports.wall+=.5;zones.tick();assertEquals(20,p.health());h.ports.monotonic+=1;h.ports.wall+=10;zones.tick();assertFalse(zones.states().getFirst().occupants().contains(p.id()));assertEquals(20,p.health());
            h.match.disconnected(0);var replacement=actor(h,0,2,0,5);replacement.body().position(new Vector(40,40,97));replacement.health(20);h.ports.wall+=.5;zones.tick();assertEquals(replacement.maxHealth(),replacement.health());assertEquals(Set.of(0),zones.states().getFirst().occupants());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void multiHillNeutralizesEmptyZonesContestsInRedAndSerializesTwoRealAirstrikesWithoutTicks()throws Exception{
        try(var h=new Harness(zoneMap("Hills"),"mh","max_active_bases=2\nbase_active_time=2\n")){
            h.match.mode().start();var zones=h.match.zones().orElseThrow();var blue=actor(h,0,2,0,5);var green=actor(h,1,3,0,5);blue.body().position(new Vector(40,40,97));green.body().position(new Vector(80,40,97));zones.tick();assertEquals(400,h.match.binding().row(blue).score);assertEquals(400,h.match.binding().row(green).score);
            h.ports.wall++;zones.tick();assertEquals(List.of(1L,1L),h.match.teams().stream().map(t->t.score).toList());green.body().position(new Vector(40,40,97));zones.tick();var replay=new ArrayList<GamePacket>();zones.reveal(replay::add);assertEquals(0xff0000,((MinimapZone)replay.get(0)).color());assertEquals(0xffffff,((MinimapZone)replay.get(1)).color());assertEquals(1,zones.states().get(1).owner());
            h.ports.wall++;zones.tick();assertTrue(zones.states().isEmpty());assertEquals(9,zones.pendingShells());var engine=h.match.entities().projectiles().engine();assertEquals(1,engine.projectiles().size());var shell=engine.projectiles().getFirst();assertSame(ProjectileSpec.AIRSTRIKE,shell.spec);assertEquals(-1,shell.throwerId);var visual=h.match.entities().registry().get(shell.entityId);assertEquals(17,visual.type);assertEquals(255,visual.playerId);assertEquals(1,visual.state);assertEquals(new Vector(0,0,100),shell.velocity);
            h.loop.pump(649_000_000);assertEquals(1,engine.projectiles().size());h.loop.pump(650_000_000);assertEquals(2,engine.projectiles().size());for(int i=2;i<=9;i++)h.loop.pump(i*650_000_000L);assertEquals(10,engine.projectiles().size());assertEquals(0,zones.pendingShells());assertEquals(2,h.ports.hillEvents.stream().filter(HostedZoneRuntime.Expired.class::isInstance).count());assertEquals(0,h.match.mode().sceneState().activeScoreLimit());h.match.mode().endByScore(2);assertFalse(h.match.mode().clock().ended);
            blue.endProtection(h.ports.monotonic);green.endProtection(h.ports.monotonic);long revision=h.match.world().revision();for(int i=0;i<60&&!engine.projectiles().isEmpty();i++)h.match.entities().projectiles().tick();assertTrue(engine.projectiles().isEmpty());assertFalse(blue.alive());assertFalse(green.alive());assertEquals(16,blue.lastDeath().killType());assertTrue(h.match.world().revision()>revision);assertTrue(h.ports.blastLog.contains("stimulus:-1"));assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void hillDeadlineSettlesOnlyEligibleTeamTicksAndRetirementCancelsQueuedShells()throws Exception{
        try(var h=new Harness(zoneMap("Deadline"),"mh","base_active_time=20\n")){
            h.match.mode().start();var zones=h.match.zones().orElseThrow();var p=actor(h,0,2,0,5);p.body().position(new Vector(40,40,97));zones.tick();h.match.mode().clock().timeLimit=3;h.match.events().offer(net.battlespades.match.ModeEvents.Event.of("pending"));h.ports.wall+=4;h.match.mode().tick(1).toCompletableFuture().join();assertEquals(3,h.match.teams().getFirst().score);assertEquals(400,h.match.binding().row(p).score);assertTrue(h.match.mode().clock().ended);assertEquals(2,h.match.mode().winner());assertTrue(h.match.entities().projectiles().engine().projectiles().isEmpty());
            h.match.mode().clock().timeLimit=0;h.match.mode().start();zones.tick();h.ports.wall+=20;zones.tick();assertEquals(4,zones.pendingShells());h.match.mode().beginRetirement(false);assertEquals(0,zones.pendingShells());h.loop.pump(10_000_000_000L);assertEquals(1,h.match.entities().projectiles().engine().projectiles().size());assertTrue(h.ports.errors.isEmpty());
        }
    }
    @Test void escapeWatchHoldsSkyFlagsClearsAfterTwoPassesAndRejectsSealedObjectivePresence()throws Exception{
        try(var h=new Harness(zoneMap("Escape"),"tc","[objectives]\nescape_watch_embedded_seconds=0\nescape_watch_entomb_seconds=0\n")){
            h.match.mode().start();var p=actor(h,0,2,0,5);var watch=h.match.escapeWatch();p.body().position(new Vector(40,40,-1));watch.tick();assertFalse(watch.flagged(p));h.ports.monotonic+=5;watch.tick();assertTrue(watch.flagged(p));var replay=new ArrayList<GamePacket>();watch.reveal(replay::add);assertEquals(List.of(MatchPackets.ChangePlayer.marker(0,true)),replay);
            p.body().position(new Vector(40,40,97));h.ports.monotonic++;watch.tick();assertTrue(watch.flagged(p));h.ports.monotonic++;watch.tick();assertFalse(watch.flagged(p));
            for(int x=39;x<=41;x++)for(int y=39;y<=41;y++)for(int z=96;z<=100;z++)if(x==39||x==41||y==39||y==41||z==96||z==100)h.match.world().set(new MutableWorld.Cell(x,y,z),true,0x123456,null);
            h.ports.monotonic++;watch.tick();assertEquals("entombed",watch.classify(p));assertTrue(watch.flagged(p));h.match.zones().orElseThrow().tick();assertTrue(h.match.zones().orElseThrow().states().getFirst().occupants().isEmpty());h.match.disconnected(0);assertFalse(watch.flagged(p));assertTrue(h.ports.errors.isEmpty());
        }
    }
    private static final class Harness implements AutoCloseable {
        final StandardPorts ports=new StandardPorts();final OwnerLoop loop=new OwnerLoop(0,1024,256,error->fail(error));
        final HostedSceneState state;final ApplicationHost host;final StandardMatchDomain match;
        Harness(Path path,String settings)throws Exception{this(path,"tdm",settings);}
        Harness(Path path,String code,String settings)throws Exception{
            StandardPorts.writeMap(path);if(Set.of("ctf","cctf","tc","mh","dia","oc","dem","vip","zom").contains(Hardcore.base(code))){byte[] column={2,100,100,0,1,2,3,4,0,(byte)239,(byte)239,(byte)239,1,2,3,4};byte[] data=new byte[512*512*column.length];for(int i=0;i<512*512;i++)System.arraycopy(column,0,data,i*column.length,column.length);Files.write(path,data);}var prepared=PreparedMap.prepare(MapAssembly.load(path,code));var config=new SceneConfig("Entities",27015,0,ModeCatalog.get(code),"Entities",false,false,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults());
            var raw=org.tomlj.Toml.parse("[modes."+Hardcore.base(code)+"]\ntime_limit=0\n"+settings);var factory=new StandardMatchFactory(config,raw,ports,new SourceRandom(0),loop,List.of("Entities"),candidate->new StandardMatchFactory.Ports(ports,ports,ports,ports,ports,ports));
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
    @Test void infiniteWalletCoversLinesFlaresPrefabsAndBlockLauncherWithoutBypassingTheirAuthorities()throws Exception{
        try(var h=new Harness(temp.resolve("Wallet.vxl"),"")){
            h.match.mode().start();var p=actor(h,0,2,0,5,22,29,23);p.bot(true);p.apply(new ClassCatalog.Selection(0,List.of(5,22,29,23),List.of("review_block"),List.of()));while(p.inventory().removeBlock()){}h.match.teamRules().set(2,true,false);p.fire().tool(5,true);
            assertTrue(h.match.construction().line(p,new BlockDamageModel.Cell(15,12,99),new BlockDamageModel.Cell(16,12,99),1));h.match.construction().tick();assertTrue(h.match.world().solid(16,12,99));assertEquals(0,p.inventory().blocks());
            p.fire().tool(22,true);assertTrue(h.match.construction().flare(p,new LivePlacement(104,2,127,15,15,99,4,0,-1,-1)));assertEquals(0,p.inventory().blocks());
            p.fire().tool(29,true);assertTrue(h.match.entities().projectiles().use(p,29,new Vector3f(12.5f,12.5f,97.25f),new Vector3f(20,0,0),1,3));assertEquals(0,p.inventory().blocks());
            var models=temp.resolve("wallet-models");Files.createDirectories(models);Files.write(models.resolve("review_block.kv6"),net.battlespades.support.CreatorFixtures.model(1,1,1));p.fire().tool(23,true);
            try(var prefabs=new HostedPrefabRuntime(h.match,h.host,new HostedPrefabRuntime.Settings(32,2048),List.of(models))){assertTrue(prefabs.place(p,new PrefabPacket(30,4,"review_block",127,0,0,0,0,0,15,14,99,0xabcdef,false)));long deadline=System.nanoTime()+4_000_000_000L;while(!h.match.world().solid(15,14,99)){assertTrue(System.nanoTime()<deadline,"prefab worker adoption timed out");prefabs.tick();Thread.onSpinWait();}assertEquals(0,p.inventory().blocks());}
            h.match.teamRules().set(2,false,false);p.fire().tool(5,true);assertFalse(h.match.construction().single(p,new BlockDamageModel.Cell(14,14,99),5));assertEquals(0,h.match.construction().mutations().metrics().pending());assertTrue(h.ports.errors.isEmpty());
        }
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
