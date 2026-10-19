// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.nio.file.*;
import java.util.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.support.CreatorFixtures;
import net.battlespades.ugc.*;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldCreatorTest {
    @TempDir Path temp;
    private Path config()throws Exception{CreatorFixtures.assets(temp.resolve("retail"));var file=temp.resolve("config.toml");Files.writeString(file,"[game]\ndefault_mode='ugc'\n[map_creator]\nproject='New World'\noutput_dir='worlds'\nretail_root='retail'\nterrain='lunar'\ntarget_mode='ctf'\n");return file;}
    @Test void portableLauncherPreflightsWithoutCreatingAndRespectsExplicitPublishDestination()throws Exception{
        Path config=config();String[] args={"--ugc-host","--config",config.toString(),"--check","--publish-root","client/hosted_ugc","--prefab-set","2"};var options=CreatorLauncher.options(args);options.preflight();assertEquals(temp.resolve("client/hosted_ugc/maps"),options.directory());assertEquals("New_World",options.name());assertFalse(Files.exists(options.directory()));var opened=options.prepare();assertEquals("ctf",opened.project().targetMode());assertEquals("lunar",opened.project().terrain().key());assertEquals(2,opened.project().prefabSet());assertArrayEquals(Files.readAllBytes(options.assets().files(opened.project().terrain()).vxl()),opened.source());
        Files.writeString(opened.files().sidecar(),opened.project().json().replace("\"modified_since_publish\":true","\"modified_since_publish\":false"));var edited=CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString(),"--project",opened.files().sidecar().toString(),"--target-mode","dem"}).prepare();assertEquals("dem",edited.project().targetMode());assertTrue(edited.project().modified());assertThrows(IllegalArgumentException.class,()->CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString(),"--title","Bad\nTitle"}));
        assertThrows(IllegalArgumentException.class,()->CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString(),"--publish-root","client/maps"}));assertThrows(IllegalArgumentException.class,()->CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString(),"--output-dir","one","--publish-root","two/hosted_ugc"}));assertThrows(IllegalArgumentException.class,()->CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString(),"--unknown"}));
        Files.writeString(config,"[map_creator]\noutput_dir='one'\npublish_root='hosted_ugc'\nretail_root='retail'\n");assertThrows(IllegalArgumentException.class,()->CreatorLauncher.options(new String[]{"--ugc-host","--config",config.toString()}));
    }
    @Test void projectLeaseProtectsOverwriteAndStartupFailureReleasesIt()throws Exception{
        var assets=CreatorFixtures.assets(temp.resolve("retail"));var opened=CreatorFixtures.project(temp.resolve("worlds"),assets);var workspace=new UgcWorkspace(opened.files());byte[] bytes=Files.readAllBytes(opened.files().vxl());
        try(var lease=workspace.lock()){assertTrue(lease.fileLock().isValid());assertThrows(java.io.IOException.class,workspace::lock);assertThrows(java.io.IOException.class,()->UgcWorkspace.create(opened.project(),temp.resolve("worlds"),"Creator",assets,true));assertArrayEquals(bytes,Files.readAllBytes(opened.files().vxl()));}
        try(var lease=workspace.lock()){assertTrue(lease.fileLock().isValid());}
        var core=net.battlespades.config.ServerConfig.load(config());byte[] sidecar=Files.readAllBytes(opened.files().sidecar());try(var socket=new java.net.DatagramSocket(0,java.net.InetAddress.getLoopbackAddress())){var bind=new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(),socket.getLocalPort());assertThrows(java.io.IOException.class,()->CreatorHost.open(bind,net.battlespades.scene.SceneConfig.from(core,"tdm"),core.raw(),opened,assets,error->fail(error)));}assertArrayEquals(bytes,Files.readAllBytes(opened.files().vxl()));assertArrayEquals(sidecar,Files.readAllBytes(opened.files().sidecar()));assertFalse(Files.exists(workspace.sceneMetadata()));try(var lease=workspace.lock()){assertTrue(lease.fileLock().isValid());}
        Files.delete(opened.files().metadata());assertThrows(java.io.IOException.class,()->UgcWorkspace.open(temp.resolve("worlds"),"Creator",null));
    }
    @Test void canonicalSnapshotPersistsLightsInStandardMetadataAndKeepsStrictNativeProjectItems()throws Exception{
        var assets=CreatorFixtures.assets(temp.resolve("retail"));var opened=CreatorFixtures.project(temp.resolve("worlds"),assets);var workspace=new UgcWorkspace(opened.files());var registry=new EntityRegistry();var placement=new EntityRegistry.Placement();placement.kind="flare_block";placement.color=new Vector(18,52,86);registry.place(13,255,255,99,placement);
        Files.writeString(workspace.sceneMetadata(),"{\"gravity\":0.7,\"skybox_name\":\"obsolete.txt\"}");
        try(var world=new MutableWorld(VxlMap.loadServer(opened.source(),"retail"),1,128)){
            opened.project().place(250,250,99,7);var snapshot=workspace.capture(world,opened.project(),registry.all());registry.clear();workspace.save(snapshot);var project=UgcWorkspace.open(temp.resolve("worlds"),"Creator",null);assertEquals(1,project.project().placements().size());assertFalse(VxlMap.loadServer(project.source(),"retail").solid(255,255,99));var metadata=MapMetadata.load(opened.files().vxl(),"ctf");var flare=metadata.entities().stream().filter(e->e.type()==13).findFirst().orElseThrow();assertEquals(0x123456,flare.color());assertEquals(.703125,metadata.environment().gravity());
            opened.project().title("Latest");opened.project().place(251,250,99,4);workspace.checkpoint(opened.project().json());var again=MapMetadata.load(opened.files().vxl(),"ctf");assertEquals(1,again.entities().stream().filter(e->e.type()==13).count());assertEquals(2,UgcWorkspace.open(temp.resolve("worlds"),"Creator",null).project().placements().size());
            workspace.save(workspace.capture(world,opened.project(),List.of()));assertTrue(MapMetadata.load(opened.files().vxl(),"ctf").entities().stream().noneMatch(e->e.type()==13));
        }
    }
}
