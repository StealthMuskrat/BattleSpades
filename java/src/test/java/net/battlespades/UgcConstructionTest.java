// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.ugc.*;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UgcConstructionTest {
    @TempDir Path temp;
    private final class Harness implements AutoCloseable,UgcConstruction.Ports {
        final ExecutorService worker=Executors.newSingleThreadExecutor();final LifeHarness life;final UgcCapacity capacity;final UgcConstruction construction;final PlayerLife p;final List<GamePacket> wire=new ArrayList<>();final List<Throwable> errors=new ArrayList<>();int moved,sounds,built,tick;
        Harness()throws Exception{var assets=CreatorFixtures.assets(temp.resolve("assets"));var path=assets.files(UgcProject.terrain("grassland")).vxl();byte[] bytes=Files.readAllBytes(path);life=new LifeHarness(GameRules.serverDefaults(),"ugc",new MutableWorld(VxlMap.loadServer(bytes,"retail"),1,4096));capacity=UgcCapacity.fromVxl(bytes).track(life.world);var normalized=ClassCatalog.normalize(13,List.of(),List.of(),List.of(7),13,List.of());var selection=new ClassCatalog.Selection(13,normalized.loadout(),List.of(CreatorFixtures.MODEL),normalized.ugcTools());life.runtime.admit(0,new SimplePackets.NewPlayerConnection(2,13,0,0,"Builder"),selection,0);p=life.runtime.player(0);p.body().position(new Vector(251,251,97));p.fire().tool(42,true);construction=new UgcConstruction(life.world,life.runtime,capacity,List.of(assets.prefabs()),worker,UgcConstruction.Settings.defaults(),()->life.monotonic,this);}
        public void broadcast(GamePacket packet){wire.add(packet);}public void send(PlayerLife player,GamePacket packet){wire.add(packet);}public void moved(PlayerLife player){moved++;}public void built(PlayerLife p,Vector anchor){built++;}public void sound(int id,Vector at){sounds++;}public void failure(Throwable e){errors.add(e);}
        void finish()throws Exception{long deadline=System.nanoTime()+2_000_000_000L;while(construction.pending()>0){assertTrue(System.nanoTime()<deadline);construction.tick(++tick);Thread.yield();}assertTrue(errors.isEmpty(),errors.toString());}
        public void close(){construction.close();worker.shutdownNow();capacity.close();life.close();}
    }
    @Test void nativePrefabsCommitWithinBudgetUseRawModelColorsAndRelocateThenErase()throws Exception{try(var h=new Harness()){
        assertTrue(h.construction.prefab(h.p,CreatorFixtures.MODEL,250,250,30,0,0,0,0xffffff,false));long deadline=System.nanoTime()+2_000_000_000L;while(h.wire.stream().noneMatch(PrefabPacket.class::isInstance)){assertTrue(System.nanoTime()<deadline);h.construction.tick(++h.tick);Thread.yield();}
        var echo=(PrefabPacket)h.wire.stream().filter(PrefabPacket.class::isInstance).findFirst().orElseThrow();assertEquals(630,echo.to());assertEquals(0,echo.from());assertEquals(h.p.id(),echo.player());assertFalse(echo.userBlocks());assertEquals(512,h.life.world.revision());assertFalse(h.wire.stream().anyMatch(p->p.id()==29));h.finish();assertEquals(630,h.life.world.revision());assertEquals(0x123456,h.life.world.color(new MutableWorld.Cell(250,250,99))&0xffffff);assertEquals(1,h.moved);assertEquals(1,h.built);assertEquals(26.75,h.p.position().z());assertTrue(h.wire.stream().anyMatch(p->p.id()==29));
        assertTrue(h.construction.prefab(h.p,CreatorFixtures.MODEL,250,250,30,0,0,0,0,true));h.finish();assertFalse(h.life.world.solid(250,250,99));assertTrue(h.wire.stream().anyMatch(ErasePrefabPacket.class::isInstance));assertEquals(0,h.construction.pending());
    }}
    @Test void rejectedGroundPlaneAndDepartedActorNeverPublishOrMutatePreparedWork()throws Exception{try(var h=new Harness()){
        assertTrue(h.construction.prefab(h.p,CreatorFixtures.MODEL,250,250,0,0,0,0,0,false));h.finish();assertEquals(0,h.life.world.revision());assertTrue(h.wire.isEmpty());assertFalse(h.construction.prefab(h.p,"unknown",250,250,30,0,0,0,0,false));
        assertTrue(h.construction.prefab(h.p,CreatorFixtures.MODEL,250,250,30,0,0,0,0,false));h.life.runtime.disconnected(0);h.finish();assertEquals(0,h.life.world.revision());assertTrue(h.wire.isEmpty());
    }}
    @Test void blocksWaitForAppliedInputPaintingIsOwnerInclusiveAndStockIsInfinite()throws Exception{try(var h=new Harness()){
        h.p.body().position(new Vector(251,251,97));h.p.fire().tool(5,true);int stock=h.p.inventory().blocks();var c=new Cell(253,251,99);assertTrue(h.construction.single(h.p,c,100));assertFalse(h.life.world.solid(c.x(),c.y(),c.z()));h.construction.tick(1);assertFalse(h.life.world.solid(c.x(),c.y(),c.z()));
        h.life.runtime.input(0,new LiveClientInput(100,0,false,5,1,0,0,0,0,0,0),100);h.p.inputTimeline().consumeOne(0,h.p.aim());h.construction.tick(2);assertTrue(h.life.world.solid(c.x(),c.y(),c.z()));assertEquals(stock,h.p.inventory().blocks());assertTrue(h.wire.stream().anyMatch(p->p.id()==33));
        h.p.fire().tool(43,true);assertTrue(h.construction.paint(h.p,c,0xaabbcc,100));assertEquals(0xaabbcc,h.life.world.color(new MutableWorld.Cell(c.x(),c.y(),c.z()))&0xffffff);assertTrue(h.wire.stream().anyMatch(p->p instanceof SimplePackets.PaintBlockPacket paint&&paint.color()==0xaabbcc));assertEquals(1,h.sounds);
        h.life.monotonic+=.2;h.p.fire().tool(5,true);assertTrue(h.construction.liberate(h.p,c,101));h.construction.forget(h.p);h.construction.tick(3);assertTrue(h.life.world.solid(c.x(),c.y(),c.z()));assertEquals(0,h.construction.pending());
        h.life.monotonic+=.2;var start=new Cell(253,252,99);var end=new Cell(253,254,99);assertTrue(h.construction.line(h.p,start,end,102));assertFalse(h.life.world.solid(253,254,99));h.life.runtime.input(0,new LiveClientInput(102,0,false,5,1,0,0,0,0,0,0),102);h.p.inputTimeline().consumeOne(0,h.p.aim());h.construction.tick(4);assertTrue(h.life.world.solid(253,254,99));assertEquals(stock,h.p.inventory().blocks());
        h.p.fire().tool(43,true);h.p.body().position(new Vector(251,251,97.5));h.p.orientation(1,0,0);for(int x=253;x<=254;x++)h.life.world.set(new MutableWorld.Cell(x,251,97),true,1,null);int cues=h.sounds;h.construction.brush(h.p,new LiveClientInput(103,0,false,43,1,0,0,0,0,2,0));assertEquals(h.p.color(),h.life.world.color(new MutableWorld.Cell(254,251,97))&0xffffff);assertEquals(cues,h.sounds);
    }}
    @Test void creatorPresentationAndNativeFogRemainIsolatedFromTheCompetitiveRegistry(){var base=new net.battlespades.scene.SceneConfig("Base",0,0,net.battlespades.scene.ModeCatalog.get("tdm"),"Base",true,true,true,10,0,"User_Grassland.txt",new net.battlespades.scene.SceneConfig.Team("Blue",1),new net.battlespades.scene.SceneConfig.Team("Green",2),GameRules.serverDefaults());var config=UgcPresentation.config(base,new UgcProject("A".repeat(80),"","","grassland","ctf"));assertEquals(12,config.mode().id());assertEquals(31,config.name().length());assertFalse(config.fallDamage());assertFalse(config.friendlyFire());assertFalse(net.battlespades.scene.ModeCatalog.registrations().containsKey("ugc"));assertEquals("4a00563412",HexFormat.of().formatHex(new UgcPackets.Fog(0x123456).encode()));assertEquals(new UgcPackets.Fog(0x123456),PacketCodec.decode(HexFormat.of().parseHex("4a00563412")));}
}
