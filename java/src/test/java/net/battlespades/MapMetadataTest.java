// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.battlespades.player.SpawnGeometry.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.*;
import net.battlespades.support.FixtureScene;
import net.battlespades.world.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MapMetadataTest {
    @TempDir static Path temp;
    static final Map<Integer,Path> MAPS=new HashMap<>();
    private static List<String[]> rows(String name)throws IOException{
        try(var in=new GZIPInputStream(Objects.requireNonNull(MapMetadataTest.class.getResourceAsStream("/golden/metadata-"+name+".tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
    }
    @BeforeAll static void inputs()throws IOException{
        for(var r:rows("inputs")){int id=Integer.parseInt(r[0]);Path dir=temp.resolve(r[0]);Files.createDirectory(dir);Object data=MetadataDocument.json(r[2]);assertInstanceOf(Map.class,data);for(var e:((Map<?,?>)data).entrySet())Files.writeString(dir.resolve((String)e.getKey()),(String)e.getValue());MAPS.put(id,dir.resolve(r[1]+".vxl"));}
    }
    private static List<Object> l(Object... values){return Arrays.asList(values);}
    private static Object rgb(Integer color){return color==null?null:l(color>>>16&255,color>>>8&255,color&255);}
    private static Object vector(StateData.Vector v){return v==null?null:l(v.x(),v.y(),v.z());}
    private static Object position(Position p){return p==null?null:l(p.x(),p.y(),p.z());}
    private static Object zone(Zone z){return z==null?null:l(z.kind(),z.team(),z.x(),z.y(),z.z(),l(z.x0(),z.x1(),z.y0(),z.y1(),z.z0(),z.z1()),z.item());}
    private static <T> Map<String,Object> keyed(Map<Integer,T> source,java.util.function.Function<T,Object> f){var out=new TreeMap<String,Object>();source.forEach((k,v)->out.put(k.toString(),f.apply(v)));return out;}
    private static Map<String,Object> snapshot(MapMetadata m){
        var result=new LinkedHashMap<String,Object>();var e=m.environment();
        result.put("source",m.source()==null?null:m.source().getFileName().toString());result.put("official",m.officialMap());result.put("format",m.vxlFormat());result.put("unofficial",m.unofficial());
        result.put("environment",l(e.gravity(),rgb(e.fogColor()),rgb(e.lightColor()),vector(e.lightDirection()),rgb(e.backColor()),vector(e.backDirection()),rgb(e.ambientColor()),e.ambientIntensity(),e.groundColors().stream().map(c->l(c.red(),c.green(),c.blue(),c.alpha())).toList(),e.skybox()));
        result.put("regions",keyed(m.spawnLayout().regions(),r->l(r.x0(),r.y0(),r.x1(),r.y1())));result.put("spawns",keyed(m.spawnLayout().spawns(),zones->zones.stream().map(MapMetadataTest::zone).toList()));result.put("bases",keyed(m.spawnLayout().bases(),zones->zones.stream().map(MapMetadataTest::zone).toList()));
        result.put("points",keyed(m.spawnPoints(),points->points.stream().map(MapMetadataTest::position).toList()));result.put("neutral",m.neutralBases().stream().map(MapMetadataTest::zone).toList());result.put("occupation",zone(m.occupationBase()));result.put("bombs",m.occupationBombs().stream().map(MapMetadataTest::position).toList());result.put("diamonds",m.diamondBases().stream().map(MapMetadataTest::zone).toList());result.put("capacities",m.diamondCapacities());result.put("minimum",keyed(m.baseMinDestruction(),n->n));
        result.put("entities",m.entities().stream().map(v->l(v.type(),v.kind(),v.position().x(),v.position().y(),v.position().z(),v.item(),rgb(v.color()))).toList());result.put("ambient",m.ambientSounds().stream().map(v->l(v.name(),v.points().stream().map(MapMetadataTest::vector).toList(),v.volume(),v.attenuation())).toList());result.put("static",keyed(m.staticLightColors(),c->rgb(c)));
        result.put("achievements",m.achievementRegions().stream().map(v->l(v.apiName(),v.kind(),v.bounds(),v.team(),v.kills(),v.weapon())).toList());result.put("layout",m.layoutSources());result.put("name",m.displayName());result.put("cap",m.capLimit());result.put("time",m.timeLimit());result.put("retail",l(m.retailModes(),m.retailPlaylistModes(),m.retailMaxPlayers()));return result;
    }
    private static void same(Object expected,Object actual,String path){
        if(expected==null){assertNull(actual,path);return;}
        if(expected instanceof Map<?,?> map){assertInstanceOf(Map.class,actual,path);var other=(Map<?,?>)actual;assertEquals(map.keySet(),other.keySet(),path);map.forEach((k,v)->same(v,other.get(k),path+"."+k));}
        else if(expected instanceof List<?> list){assertInstanceOf(List.class,actual,path);var other=(List<?>)actual;assertEquals(list.size(),other.size(),path);for(int i=0;i<list.size();i++)same(list.get(i),other.get(i),path+"["+i+"]");}
        else if(expected instanceof Number n){assertInstanceOf(Number.class,actual,path);Number v=(Number)actual;if(n instanceof Double)assertEquals(Double.doubleToRawLongBits(n.doubleValue()),Double.doubleToRawLongBits(v.doubleValue()),path+" expected "+n+" actual "+v);else assertEquals(new BigInteger(n.toString()),new java.math.BigDecimal(v.toString()).toBigIntegerExact(),path);}
        else assertEquals(expected,actual,path);
    }
    /** Independent source digest: expected values never pass through the Java JSON reader. */
    private static byte[] canonical(Object value){
        var out=new ByteArrayOutputStream();
        if(value==null)out.writeBytes("N;".getBytes(StandardCharsets.US_ASCII));
        else if(value instanceof Boolean b)out.writeBytes((b?"T;":"B;").getBytes(StandardCharsets.US_ASCII));
        else if(value instanceof Number n){
            String text;
            if(n instanceof Double||n instanceof Float){double d=n.doubleValue();if(Double.isFinite(d)&&d==Math.rint(d)&&Double.doubleToRawLongBits(d)!=Long.MIN_VALUE)text="I"+new java.math.BigDecimal(d).toBigIntegerExact()+";";else text="F"+String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(d))+";";}
            else text="I"+n+";";
            out.writeBytes(text.getBytes(StandardCharsets.US_ASCII));
        }else if(value instanceof String s){byte[] data=s.getBytes(StandardCharsets.UTF_8);out.writeBytes(("S"+data.length+":").getBytes(StandardCharsets.US_ASCII));out.writeBytes(data);}
        else if(value instanceof List<?> list){out.writeBytes(("L"+list.size()+":").getBytes(StandardCharsets.US_ASCII));for(Object item:list)out.writeBytes(canonical(item));}
        else if(value instanceof Map<?,?> map){
            out.writeBytes(("D"+map.size()+":").getBytes(StandardCharsets.US_ASCII));var keys=map.keySet().stream().map(String.class::cast).sorted((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8))).toList();for(String key:keys){out.writeBytes(canonical(key));out.writeBytes(canonical(map.get(key)));}
        }else throw new AssertionError(value.getClass());return out.toByteArray();
    }
    @TestFactory Stream<DynamicTest> allOriginalMetadataFields()throws IOException{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var row:rows("results"))groups.computeIfAbsent(row[0],k->new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(group->DynamicTest.dynamicTest(group.getKey()+"/"+group.getValue().getFirst()[2],()->{
            Path path=MAPS.get(Integer.parseInt(group.getKey()));for(var r:group.getValue()){
                if(r[3].equals("error")){assertThrows(IllegalArgumentException.class,()->MapMetadata.load(path,r[1]),Arrays.toString(r));continue;}
                var metadata=MapMetadata.load(path,r[1]);var actual=snapshot(metadata);same(MetadataDocument.json(r[4]),actual,path.getFileName()+"/"+r[1]);
                assertEquals(r[5],HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical(actual))),"Independent source digest "+path.getFileName()+"/"+r[1]);
                assertThrows(UnsupportedOperationException.class,()->metadata.spawnLayout().spawns().put(2,List.of()));assertThrows(UnsupportedOperationException.class,()->metadata.layoutSources().put("x","y"));
            }
        }));
    }
    @Test void originalMapVotePoolsAndCatalogueFiltering()throws IOException{
        for(var r:rows("pools")){Path dir=MAPS.get(Integer.parseInt(r[0])).getParent();same(MetadataDocument.json(r[2]),MapMetadata.retailModePool(dir,r[1]),"pool "+r[0]+"/"+r[1]);}
    }
    private static SceneConfig config(String mode,String map){String code=MapMetadata.canonicalMode(mode);return new SceneConfig("Metadata oracle",27015,0,ModeCatalog.get(code),map,false,false,true,10,0x0c0d0b,"User_Grassland.txt",new SceneConfig.Team("Blue",0x0040ff),new SceneConfig.Team("Green",0xff2000),GameRules.serverDefaults(),code);}
    @TestFactory Stream<DynamicTest> sidecarsReachActualScenePacketBuilders()throws IOException{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var r:rows("packets"))groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r);
        return groups.entrySet().stream().map(group->DynamicTest.dynamicTest("packets/"+group.getKey(),()->{
            for(var r:group.getValue()){
                Path path=MAPS.get(Integer.parseInt(r[0]));var m=MapMetadata.load(path,r[1],0xdeadbeef);var c=config(r[1],m.environment().filename());
                assertArrayEquals(HexFormat.of().parseHex(r[2]),ScenePackets.initial(c,m.environment()).encode(),"InitialInfo "+Arrays.toString(r));
                var state=new ScenePackets.State(new ScenePackets.Team("Blue",0x0040ff,7),new ScenePackets.Team("Green",0xff2000,-3),null,null);
                assertArrayEquals(HexFormat.of().parseHex(r[3]),ScenePackets.state(c,m.environment(),state,7).encode(),"StateData "+Arrays.toString(r));
            }
        }));
    }
    @Test void mapAssemblyValidatesFormatCrcAndDoesNotReplaceLiveWorld()throws IOException{
        Path path=temp.resolve("assembly.vxl");Files.write(path,new byte[]{0,(byte)239,(byte)239,0,1,2,3,4});Files.writeString(path.resolveSibling("assembly.json"),"{\"gravity\":0.4,\"vxl_format\":\"retail\"}");
        var map=MapAssembly.load(path,"tdm");assertEquals(26.0/64,map.metadata().environment().gravity());assertEquals((int)map.terrain().sourceCrc(),map.metadata().environment().sourceCrc());
        try(var world=map.world(17,64)){assertEquals(17,world.epoch());assertSame(world,map.spawns(world).world());assertTrue(world.solid(new MutableWorld.Cell(256,256,239)));Files.writeString(path.resolveSibling("assembly.json"),"{\"vxl_format\":\"bad\"}");assertThrows(IllegalArgumentException.class,()->MapAssembly.load(path,"tdm"));assertTrue(world.solid(new MutableWorld.Cell(256,256,239)));}
        Files.writeString(path.resolveSibling("assembly.json"),"{}");Files.write(path,new byte[]{0});assertThrows(ProtocolException.class,()->MapAssembly.load(path,"tdm"));
        try(var channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.WRITE)){channel.position(64*1024*1024L);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}
        assertThrows(IOException.class,()->MapAssembly.load(path,"tdm"));
    }
    @Test void malformedAndExcessiveDocumentsStayInert()throws IOException{
        for(String value:List.of("[1,]","{\"x\":01}","{\"x\":True}","\"\\x41\"","\"line\nfeed\"","[\u2003]","{\"x\":\u001c1}","[".repeat(66)+"]".repeat(66)))assertThrows(IllegalArgumentException.class,()->MetadataDocument.json(value),value);
        assertNull(MetadataDocument.sidecar("gravity = __import__('os').system('bad')",Set.of("gravity")));
        assertThrows(IllegalArgumentException.class,()->MetadataDocument.json(" ".repeat(MetadataDocument.MAX_BYTES+1)));
        Object value=MetadataDocument.json("{\"a\":null,\"b\":[true,false,1,-0.0,1e3],\"c\":\"\\ud83d\\udc31\"}");assertInstanceOf(Map.class,value);assertEquals("🐱",((Map<?,?>)value).get("c"));
    }
    @Test void sceneRejectsMetadataForAnotherModeOrWorld()throws IOException{
        Path path=temp.resolve("binding.vxl");Files.writeString(temp.resolve("binding.json"),"{}");
        try(var domain=new FixtureScene(1,64)){
            var tdm=config("tdm","binding");var ctf=config("ctf","binding");var m=MapMetadata.load(path,"tdm",domain.world().sourceCrc());
            assertThrows(IllegalArgumentException.class,()->new ConfiguredScene(ctf,m,()->ScenePackets.State.initial(ctf),domain));
            var wrong=MapMetadata.load(path,"tdm",domain.world().sourceCrc()+1);assertThrows(IllegalArgumentException.class,()->new ConfiguredScene(tdm,wrong,()->ScenePackets.State.initial(tdm),domain));
            assertEquals(domain.world(),new ConfiguredScene(tdm,m,()->ScenePackets.State.initial(tdm),domain).world());
        }
    }
}
