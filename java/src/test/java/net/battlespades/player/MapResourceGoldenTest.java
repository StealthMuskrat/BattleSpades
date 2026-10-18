// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ScenePackets;
import net.battlespades.support.LifeHarness;
import net.battlespades.world.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class MapResourceGoldenTest {
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static int n(String value){return Integer.parseInt(value);}
    static Vector vector(String value){var v=value.split(",");return new Vector(number(v[0]),number(v[1]),number(v[2]));}
    static String vec(Vector value){return bits(value.x())+","+bits(value.y())+","+bits(value.z());}
    static String hex(GamePacket packet){return HexFormat.of().formatHex(packet.encode());}
    static String baseline(PlayerLife player){return player.health()+","+player.inventory().weapon()+","+player.inventory().ammo().reserve();}
    List<String[]> rows()throws Exception{try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/map-resources.tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static IntStream traces(){return IntStream.range(0,48);}
    private static MapMetadata metadata(List<MapMetadata.Entity> entities,Map<Integer,Integer> colors,String sky) {
        var m=MapMetadata.load(Path.of("Fixture.vxl"),"tdm");var e=m.environment();
        var environment=new ScenePackets.Environment(e.filename(),e.sourceCrc(),e.gravity(),e.fogColor(),e.lightColor(),e.lightDirection(),e.backColor(),e.backDirection(),e.ambientColor(),e.ambientIntensity(),e.groundColors(),sky);
        return new MapMetadata(m.source(),m.officialMap(),m.vxlFormat(),m.activeMode(),m.unofficial(),environment,m.spawnLayout(),m.spawnPoints(),m.neutralBases(),m.occupationBase(),m.occupationBombs(),m.diamondBases(),m.diamondCapacities(),m.baseMinDestruction(),entities,m.ambientSounds(),colors,m.achievementRegions(),m.layoutSources(),m.displayName(),m.capLimit(),m.timeLimit(),m.retailModes(),m.retailPlaylistModes(),m.retailMaxPlayers(),m.diagnostics());
    }
    @ParameterizedTest @MethodSource("traces") void originalAssemblyRefillsLifecycleAndNativePackets(int trace)throws Exception {
        var source=new ArrayList<String[]>();boolean selected=false;for(var row:rows()){if(row[0].equals("E"))selected=n(row[1])==trace;if(selected)source.add(row);}var header=source.getFirst();boolean[] wire={header[3].equals("1")};boolean enabled=header[4].equals("1");double delay=number(header[5]);
        try(var h=new LifeHarness(GameRules.serverDefaults(),"tdm",new MutableWorld(VxlMap.loadServer(HexFormat.of().parseHex(header[2]),"retail"),1,10000))) {
            h.admit(0,2);var player=h.runtime.player(0);player.maxHealth(n(header[7]));player.health(25);while(player.inventory().removeBlock()){}player.inventory().ammo(2,0);
            var entities=new ArrayList<MapMetadata.Entity>();var colors=new HashMap<Integer,Integer>();var anchors=new HashMap<Integer,Vector>();var registry=new EntityRegistry();var events=new ArrayList<String>();var awards=new TreeMap<Integer,Integer>();
            h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket packet){throw new AssertionError(packet);}public void owner(int id,GamePacket packet){events.add("owner:"+hex(packet));}public boolean inGame(int id){return true;}};
            for(var r:source)switch(r[0]) {
                case "S"->{Integer rgb=null;if(!r[5].equals("-")){var c=r[5].split(",");rgb=n(c[0])<<16|n(c[1])<<8|n(c[2]);}entities.add(new MapMetadata.Entity(n(r[1]),"fixture",new SpawnGeometry.Position(number(r[2]),number(r[3]),number(r[4])),"fixture",rgb));}
                case "C"->{var c=r[2].split(",");colors.put(n(r[1]),n(c[0])<<16|n(c[1])<<8|n(c[2]));}
                case "A"->anchors.put(n(r[1]),vector(r[2]));
                case "W"->h.world.set(new MutableWorld.Cell(n(r[1]),n(r[2]),n(r[3])),true,0x406080,null);
                case "I"->{var p=new EntityRegistry.Placement();p.kind=r[1];p.wireVisible=r[2].equals("1");var e=registry.place(3,1,2,3,p);e.alive=r[3].equals("1");}
                default->{}
            }
            var effects=new MapResourceService.Effects(){
                public boolean wireReady(){return wire[0];}public boolean pickupsEnabled(){return enabled;}public double respawnDelay(){return delay;}
                public Object crateBaseline(PlayerLife p){String b=baseline(p);events.add("baseline:"+b);return b;}
                public void recordCrate(PlayerLife p,int award){events.add("award:"+award);awards.merge(award,1,Integer::sum);}
                public void crateCollected(PlayerLife p,int award,Object before){events.add("collected:"+award+":"+before);}
                public void create(MapEntity e){events.add("create:"+hex(new CreateEntity(e.toWireEntity())));}
                public void destroy(int id){assertNotNull(registry.get(id),"Destroy precedes removal");events.add("destroy:"+hex(new SimplePackets.DestroyEntity(id)));}
                public void personalSound(PlayerLife p,int id){events.add("owner:"+hex(AudioPackets.PlaySound.personal(id)));}
                public void flyby(AudioPackets.PlaySound p){events.add("flyby:"+hex(p));}
                public void rebuilt(MapResourceService.Result result){assertTrue(result.pickups()<=9);if(trace==47)assertTrue(result.truncatedLights()>0);}
            };
            var service=new MapResourceService(h.runtime,registry,metadata(entities,colors,header[6].equals("-")?null:header[6]),anchors::get,effects);var pickupWorld=new PickupWorld(h.world);int index=0;
            for(var r:source)switch(r[0]) {
                case "B"->{events.clear();index=0;if(r.length>1)wire[0]=r[1].equals("1");service.rebuild();}
                case "T"->{events.clear();index=0;h.wall=number(r[2]);var e=registry.get(n(r[1]));assertTrue(e.behavior.onTouch(e,player,new EntityContext(.05,h.wall,List.of(),registry,effects::create,effects::destroy,null,pickupWorld,null)));}
                case "R"->{events.clear();index=0;h.wall=number(r[1]);registry.tick(new EntityContext(.05,h.wall,List.of(),registry,effects::create,effects::destroy,null,pickupWorld,null),null);}
                case "V"->{var all=registry.all();assertTrue(index<all.size());var e=all.get(index++);String where="trace "+trace+" entity "+r[1];assertEquals(n(r[1]),e.id(),where);assertEquals(n(r[2]),e.type,where);assertEquals(r[3],e.kind,where);assertEquals(r[4],vec(new Vector(e.x,e.y,e.z)),where);assertEquals(r[5].equals("1"),e.alive,where);assertEquals(r[6],bits(e.respawnAt),where);assertEquals(r[7],e.color==null?"-":(int)e.color.x()+","+(int)e.color.y()+","+(int)e.color.z(),where);assertEquals(r[8].equals("1"),e.wireVisible,where);assertEquals(r[9].equals("1"),e.behavior!=null,where);assertEquals(r[10].equals("1"),e.falling,where);assertEquals(r[11],bits(e.fallAccumulator),where);}
                case "O"->assertEquals(r[1],events.isEmpty()?"-":String.join("|",events),"trace "+trace+" ordered effects");
                case "P"->assertEquals(String.join(",",Arrays.copyOfRange(r,1,r.length)),player.health()+","+player.maxHealth()+","+player.inventory().blocks()+","+player.inventory().ammo().clip()+","+player.inventory().ammo().reserve());
                case "Q"->{var stats=new ArrayList<String>();awards.forEach((a,count)->stats.add(a+","+count));assertEquals(r[1],stats.isEmpty()?"-":String.join(";",stats));}
                case "L"->{var lights=new ArrayList<String>();h.world.staticLightCells().forEach((c,rgb)->lights.add(c.x()+","+c.y()+","+c.z()+","+rgb));assertEquals(r[1],lights.isEmpty()?"-":String.join(";",lights));}
                case "N"->assertEquals(Long.parseLong(r[1]),h.world.revision());
                case "Z"->assertEquals(index,registry.all().size());
                default->{}
            }
        }
    }
}
