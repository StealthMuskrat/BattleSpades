// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import net.battlespades.protocol.MapSyncTransfer;
import net.battlespades.session.*;
import net.battlespades.support.FixtureScene;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.BoundedSnapshotCompression;
import net.battlespades.world.MutableWorld;
import net.battlespades.scene.*;
import net.battlespades.config.ServerConfig;
import java.nio.file.Path;
import java.nio.file.Files;
import net.battlespades.world.MapMetadata;

/** Test-only protocol fixture, never a gameplay launcher. stdin controls run on the application owner. */
public final class LoaderProbe {
    private LoaderProbe() {}
    private static ConfiguredScene configured(FixtureScene domain,SceneConfig config,Path metadataRoot){
        return new ConfiguredScene(config,MapMetadata.load(metadataRoot.resolve("fixture.vxl"),config.modeCode(),domain.world().sourceCrc()),()->ScenePackets.State.initial(config),domain);
    }
    public static void main(String[] args) throws Exception {
        var commands=new ArrayBlockingQueue<String>(32);
        Thread.ofPlatform().daemon(true).name("probe-stdin").start(()->{
            try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {String line;while((line=reader.readLine())!=null) commands.put(line);}
            catch(InterruptedException e) {Thread.currentThread().interrupt();}
            catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
        });
        String secret=args.length>0?args[0]:"";
        var settings=FixtureScene.copy(JoinSettings.defaults(),Map.of("mapMode",MapSyncTransfer.Mode.AUTO,"replayBudget",1,"password",new JoinPasswordGate.Policy(secret,3,60000,60000)));
        FixtureScene scene=new FixtureScene(1,64);long epoch=1;
        var config=SceneConfig.from(ServerConfig.load(Path.of("config.toml")));
        Path metadataRoot=Files.createTempDirectory("battle-metadata-probe-");
        Files.writeString(metadataRoot.resolve("fixture.json"),"{\"gravity\":0.4}");
        Files.writeString(metadataRoot.resolve("fixture.ugc"),"{\"skybox_name\":\"User_Lunar.txt\"}");
        Files.writeString(metadataRoot.resolve("fixture.txt"),"fog_color=(1,2,3)\n");
        try(var compression=new BoundedSnapshotCompression(1,4);
                var host=new ApplicationHost(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),EnetSettings.defaults(),2,settings,configured(scene,config,metadataRoot),compression)) {
            System.out.println("READY "+((InetSocketAddress)host.address()).getPort());System.out.flush();
            long start=System.nanoTime();
            while(System.nanoTime()-start<120_000_000_000L) {
                for(int n=0;n<32;n++) {
                    String command=commands.poll();if(command==null) break;String[] p=command.split(" ");
                    switch(p[0]) {
                        case "EDIT" -> {
                            scene.world().set(new MutableWorld.Cell(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])),p[4].equals("1"),Integer.parseUnsignedInt(p[5],16),p[6].equals("none")?null:Double.parseDouble(p[6]));
                            System.out.println("APPLIED "+scene.world().revision());
                        }
                        case "STATS" -> System.out.println("STATS "+host.occupiedSlots()+" "+host.sessions().stream().map(m->m.state().name()).collect(java.util.stream.Collectors.joining(",")));
                        case "RELOAD" -> {Files.writeString(metadataRoot.resolve("fixture.json"),"{\"gravity\":0.75}");var replacement=new FixtureScene(++epoch,64);host.replaceScene(configured(replacement,config,metadataRoot));scene.close();scene=replacement;System.out.println("RELOADED "+epoch);}
                        case "STOP" -> {System.out.println("RESULT "+host.occupiedSlots()+" "+scene.world().activeLeases()+" "+scene.world().journalSize());System.out.flush();return;}
                        default -> throw new IllegalArgumentException("Unknown probe command");
                    }
                    System.out.flush();
                }
                long now=(System.nanoTime()-start)/1_000_000;host.service(now,(int)(now*60/1000));Thread.sleep(1);
            }
            throw new AssertionError("Loader probe timed out");
        } finally {scene.close();try(var paths=Files.walk(metadataRoot)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
