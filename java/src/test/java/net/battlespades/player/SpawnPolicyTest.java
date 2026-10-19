// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.battlespades.player.SpawnGeometry.Position;
import net.battlespades.world.*;
import net.battlespades.player.SpawnGeometry.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SpawnPolicyTest {
    @TestFactory Stream<DynamicTest> actualBaseModeToNativeWorldManagerAndFinalRescue()throws Exception{
        List<String[]> source;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/golden/standard-spawns.tsv"))){source=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(line->line.split("\t")).toList();}
        return Stream.of("flat","sea","empty").map(name->DynamicTest.dynamicTest(name,()->{
            try(var f=fixture(name)){
                var rules=net.battlespades.rules.GameRules.serverDefaults();var random=new SourceRandom(168);PlayerLife[] actor={null};double[] now={100};
                var spawns=new StandardSpawns(f.geometry,new Object(),random,()->List.of(actor[0]),()->now[0]);int step=0;
                for(var row:source)if(row[0].equals(name)){
                    actor[0]=new PlayerLife(0,Integer.parseInt(row[1]),0,"Spawn","tdm",net.battlespades.physics.CollisionMap.of(f.world),rules,ClassCatalog.normalize(0,List.of(),rules));now[0]=100+step++;
                    var at=spawns.spawn(actor[0]);exact(row[2],new Position(at.x(),at.y(),at.z()),name+"/"+step);actor[0].camera(at,0x123456);spawns.death(actor[0]);assertEquals(Double.doubleToLongBits(Double.parseDouble(row[3])),Double.doubleToLongBits(random.random()),name+" shared random stream "+step);
                }assertEquals(6,step);
            }
        }));
    }
    private static List<String[]> rows(String name)throws IOException{
        try(var in=new GZIPInputStream(Objects.requireNonNull(SpawnPolicyTest.class.getResourceAsStream("/golden/spawn-"+name+".tsv.gz")))){
            return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();
        }
    }
    private static double[] nums(String value){return Arrays.stream(value.split(",")).mapToDouble(SpawnPolicyTest::number).toArray();}
    private static double number(String value){return switch(value){case "nan"->Double.NaN;case "inf"->Double.POSITIVE_INFINITY;case "-inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value);};}
    private static Position vector(double[] a,int i){return new Position(a[i],a[i+1],a[i+2]);}
    private static Position vector(String text){return text.equals("none")?null:vector(nums(text),0);}
    private static void exact(String expected,Position actual,String context){
        if(expected.equals("none")){assertNull(actual,context);return;}assertNotNull(actual,context);double[] e=nums(expected),a={actual.x(),actual.y(),actual.z()};
        for(int i=0;i<3;i++)assertEquals(Double.doubleToLongBits(e[i]),Double.doubleToLongBits(a[i]),context+" coordinate "+i);
    }
    private static List<Integer> integers(String text){return text.equals("-")?List.of():Arrays.stream(text.split(",")).map(Integer::valueOf).toList();}
    private static String columns(List<Candidate> values){return values.isEmpty()?"-":String.join(";",values.stream().map(c->c.x()+","+c.y()+(c.floor()==null?"":","+c.floor())).toList());}
    private static Map<String,List<String[]>> groups(String name)throws IOException{
        var groups=new LinkedHashMap<String,List<String[]>>();for(var r:rows(name))groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r);return groups;
    }
    private record Fixture(MutableWorld world,SpawnGeometry geometry,List<Zone> zones)implements AutoCloseable{
        @Override public void close(){world.close();}
    }
    private static void box(MutableWorld world,double[] a){
        for(int x=(int)a[0];x<=a[1];x++)for(int y=(int)a[2];y<=a[3];y++)for(int z=(int)a[4];z<=a[5];z++)world.set(new MutableWorld.Cell(x,y,z),a[6]!=0,0x123456,null);
    }
    private static Fixture fixture(String name)throws IOException{
        var world=new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,64);var zones=new ArrayList<Zone>();var regions=new HashMap<Integer,Region>();
        for(var r:rows("layouts"))if(r[0].equals(name)){
            String[] f=r[2].split(",");switch(r[1]){
                case "box"->box(world,nums(r[2]));
                case "zone"->zones.add(new Zone(f[0],Integer.parseInt(f[1]),Double.parseDouble(f[2]),Double.parseDouble(f[3]),Double.parseDouble(f[4]),Double.parseDouble(f[5]),Double.parseDouble(f[6]),Double.parseDouble(f[7]),Double.parseDouble(f[8]),Double.parseDouble(f[9]),Double.parseDouble(f[10]),f[11]));
                case "region"->{double[] a=nums(r[2]);regions.put((int)a[0],new Region((int)a[1],(int)a[2],(int)a[3],(int)a[4]));}
                default->fail(r[1]);
            }
        }
        var spawns=new HashMap<Integer,List<Zone>>();var bases=new HashMap<Integer,List<Zone>>();
        for(var z:zones)(z.kind().equals("base")?bases:spawns).computeIfAbsent(z.team(),k->new ArrayList<>()).add(z);
        return new Fixture(world,new SpawnGeometry(world,new Layout(spawns,bases,regions)),List.copyOf(zones));
    }
    @TestFactory Stream<DynamicTest> originalWorldManagerGeometry()throws Exception{
        return groups("geometry").entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            try(var f=fixture(e.getKey())){
                var g=f.geometry;var resolver=new SpawnResolver(g,new Object(),new SourceRandom(1000));int count=0;
                for(var r:e.getValue()){
                    double[] a=nums(r[2]);String context=e.getKey()+"/"+count+++" "+Arrays.toString(r);
                    switch(r[1]){
                        case "safe"->assertEquals(r[3].equals("1"),g.safePosition(vector(a,0)),context);
                        case "safe2","safe3"->assertEquals(r[3].equals("1"),g.safePosition(vector(a,0),r[1].equals("safe2")?2:3),context);
                        case "clear"->assertEquals(r[3].equals("1"),g.bodyClear(vector(a,0)),context);
                        case "support"->assertEquals(r[3].equals("1"),g.supported(vector(a,0)),context);
                        case "surface"->assertEquals(Integer.parseInt(r[3]),g.surface((int)a[0],(int)a[1]),context);
                        case "column"->assertEquals(r[3].equals("1"),g.safeColumn((int)a[0],(int)a[1],a[2]<0?null:f.zones.get((int)a[2]),a[3]!=0),context);
                        case "candidate"->{var zones=f.zones.stream().filter(z->z.kind().equals("spawn")).toList();exact(r[3],g.candidatePosition(new Candidate((int)a[0],(int)a[1]),zones),context);}
                        case "zone"->assertEquals(r[3],columns(g.zoneCandidates(f.zones.get((int)a[0]))),context);
                        case "floors"->assertEquals(integers(r[3]),g.floors((int)a[0],(int)a[1],f.zones.get((int)a[2])),context);
                        case "floor"->assertEquals(r[3].equals("1"),g.safeFloor((int)a[0],(int)a[1],(int)a[2],f.zones.get((int)a[3])),context);
                        case "pocket"->assertEquals(r[3].equals("1"),g.sealedPocket((int)a[0],(int)a[1],(int)a[2],(int)a[3]),context);
                        case "fallback"->assertEquals(r[3],columns(g.fallbackCandidates((int)a[0])),context);
                        case "authored"->assertEquals(r[3],columns(g.authoredCandidates((int)a[0])),context);
                        case "candidates"->assertEquals(r[3],columns(g.candidates((int)a[0])),context);
                        case "anchor"->exact(r[3],g.baseAnchor((int)a[0]),context);
                        case "nearest"->exact(r[3],g.nearestSafe(a[0],a[1],(int)a[2]),context);
                        case "dry"->exact(r[3],g.dryGround(a[0],a[1],(int)a[2]),context);
                        case "segment"->assertEquals(r[3].equals("1"),resolver.segmentClear(vector(a,0),vector(a,3)),context);
                        default->fail(context);
                    }
                }
            }
        }));
    }
    @TestFactory Stream<DynamicTest> sourceSeededSelectionsAndRescueEvents()throws Exception{
        return groups("events").entrySet().stream().map(e->DynamicTest.dynamicTest(e.getKey(),()->{
            try(var f=fixture(e.getKey())){
                var resolver=new SpawnResolver(f.geometry,new Object(),new SourceRandom(1000));var random=new SourceRandom(168);var scoreRandom=new SourceRandom(376);var players=new LinkedHashMap<Integer,SpawnResolver.Player>();double now=100;int count=0;
                for(var r:e.getValue()){
                    String context=e.getKey()+"/"+count+++" "+Arrays.toString(r);
                    switch(r[1]){
                        case "players"->{players.clear();for(String text:r[2].split(";")){double[] a=nums(text);players.put((int)a[0],new SpawnResolver.Player((int)a[0],(int)a[1],a[2]!=0,a[3]!=0,vector(a,4),vector(a,7)));}}
                        case "mode"->resolver.mode(new Object());
                        case "boxedit"->box(f.world,nums(r[2]));
                        case "death"->{double[] a=nums(r[2]);now=a[0];resolver.recordDeath(new SpawnResolver.Player(-1,(int)a[1],false,false,vector(a,2),null),now);}
                        case "choose"->{double[] a=nums(r[2]);now=a[0];exact(r[3],resolver.choose(players.get((int)a[1]),List.copyOf(players.values()),now,random),context);}
                        case "score"->{
                            double[] a=nums(r[2]);var live=players.values().stream().filter(p->p.id()!=0&&p.alive()&&p.spawned()&&(p.team()==2||p.team()==3)).toList();
                            var enemies=live.stream().filter(p->p.team()!=(int)a[1]).map(SpawnResolver.Player::view).toList();var allies=live.stream().filter(p->p.team()==(int)a[1]).map(SpawnResolver.Player::view).toList();
                            assertEquals(Double.doubleToLongBits(number(r[3])),Double.doubleToLongBits(resolver.score(vector(a,2),(int)a[1],enemies,allies,a[0],scoreRandom)),context);
                        }
                        case "rescue"->{int comma=r[2].indexOf(',');int team=Integer.parseInt(r[2].substring(0,comma));exact(r[3],resolver.rescue(team,vector(r[2].substring(comma+1)),now),context);}
                        case "emergency"->{double[] a=nums(r[2]);now=a[0];exact(r[3],resolver.emergency((int)a[1],null,now),context);}
                        case "nearEmergency"->{double[] a=nums(r[2]);now=a[0];exact(r[3],resolver.emergency((int)a[1],vector(a,2),now),context);}
                        case "resolve"->{int comma=r[2].indexOf(',');int team=Integer.parseInt(r[2].substring(0,comma));String p=r[2].substring(comma+1);resolver.mode(new Object());final double timestamp=now;exact(r[3],resolver.resolve(team,()->{if(p.equals("raise"))throw new IllegalStateException("mode failure");return vector(p);},timestamp),context);}
                        default->fail(context);
                    }
                }
            }
        }));
    }
    @TestFactory Stream<DynamicTest> sourceRandomStreamsAndSelectionAlgorithms()throws Exception{
        return groups("random").entrySet().stream().map(e->DynamicTest.dynamicTest("seed "+e.getKey(),()->{
            var random=new SourceRandom(new BigInteger(e.getKey()));int row=0;
            for(var r:e.getValue()){
                String context=e.getKey()+"/"+row+++" "+Arrays.toString(r);
                switch(r[1]){
                    case "random"->assertEquals(Double.doubleToLongBits(number(r[3])),Double.doubleToLongBits(random.random()),context);
                    case "bits"->assertEquals(Long.parseLong(r[3]),random.bits(Integer.parseInt(r[2])),context);
                    case "below"->assertEquals(Integer.parseInt(r[3]),random.below(Integer.parseInt(r[2])),context);
                    case "sample"->{var a=integers(r[2]);assertEquals(integers(r[3]),random.sample(java.util.stream.IntStream.range(0,a.get(0)).boxed().toList(),a.get(1)),context);}
                    case "shuffle"->{var values=new ArrayList<>(java.util.stream.IntStream.range(0,Integer.parseInt(r[2])).boxed().toList());random.shuffle(values);assertEquals(integers(r[3]),values,context);}
                    case "weighted"->assertEquals(Integer.parseInt(r[3]),random.weighted(nums(r[2])),context);
                    default->fail(context);
                }
            }
        }));
    }
    @Test void sourceHypotRoundingIncludingSubnormals()throws Exception{
        int i=0;for(var r:rows("hypot")){var a=nums(r[0]);assertEquals(Double.doubleToLongBits(number(r[1])),Double.doubleToLongBits(SpawnMath.hypot(a[0],a[1])),"hypot row "+i+++" "+Arrays.toString(r));}
    }
    @Test void sourceCompensatedSquaredDistanceSum()throws Exception{
        for(var r:rows("sums"))assertEquals(Double.doubleToLongBits(number(r[1])),Double.doubleToLongBits(SpawnMath.sumSquares(nums(r[0]))),Arrays.toString(r));
    }
    @TestFactory Stream<DynamicTest> resolvedSpawnSettlesInOriginalPhysicsForEveryClass()throws Exception{
        return groups("settle").entrySet().stream().map(e->DynamicTest.dynamicTest("settle "+e.getKey(),()->{
            try(var f=fixture(e.getKey())){
                net.battlespades.physics.PlayerPhysics mover=null;int previous=-1;
                for(var r:e.getValue()){
                    int cid=Integer.parseInt(r[1]);if(cid!=previous){mover=new net.battlespades.physics.PlayerPhysics(net.battlespades.physics.CollisionMap.of(f.world));mover.profile(net.battlespades.physics.MovementProfile.forClass(cid));mover.position(vector(r[2]).toPhysics());previous=cid;}
                    String context=e.getKey()+"/class "+cid+"/tick "+r[3];assertNotNull(mover);
                    assertEquals(Integer.parseInt(r[4]),mover.update(1.0/60,1,List.of()),context);
                    var s=mover.state();double[] expected=nums(r[5]),actual={s.position().x(),s.position().y(),s.position().z(),s.velocity().x(),s.velocity().y(),s.velocity().z()};
                    for(int i=0;i<6;i++)assertEquals(Double.doubleToLongBits(expected[i]),Double.doubleToLongBits(actual[i]),context+" field "+i);
                    assertEquals(integers(r[6]),List.of(s.airborne()?1:0,s.wade()?1:0,s.crouch()?1:0,s.jump()?1:0,s.jumped()?1:0),context);
                }
            }
        }));
    }
    @Test void boundedMemoryAndModeResetDoNotLeakAcrossPlayerLives()throws Exception{
        try(var f=fixture("flat")){
            Object mode=new Object();var resolver=new SpawnResolver(f.geometry,mode,new SourceRandom(168));var p=new SpawnResolver.Player(1,2,true,true,new Position(36.5,36.5,97.25),null);
            for(int i=0;i<150;i++){resolver.recordDeath(p,i);resolver.choose(p,List.of(p),i);}
            assertEquals(128,resolver.deaths().size());assertEquals(64,resolver.spawns().size());assertEquals(22,resolver.deaths().getFirst().stamp());assertEquals(86,resolver.spawns().getFirst().stamp());
            resolver.mode(mode);assertEquals(128,resolver.deaths().size());resolver.mode(new Object());assertTrue(resolver.deaths().isEmpty());assertTrue(resolver.spawns().isEmpty());
            assertNull(resolver.choose(new SpawnResolver.Player(2,0,false,false,p.position(),null),List.of(),200));
        }
    }
    @Test void liveSurfaceCacheTracksTopologyAndOwnerAndWorldClosure()throws Exception{
        try(var f=fixture("flat")){
            var g=f.geometry;assertEquals(100,g.surface(36,36));var p=g.columnPosition(36,36);assertTrue(g.safePosition(p));
            f.world.set(new MutableWorld.Cell(36,36,90),true,0x123456,null);assertEquals(90,g.surface(36,36));assertFalse(g.safeColumn(36,36));
            f.world.set(new MutableWorld.Cell(36,36,90),false,0,null);assertEquals(100,g.surface(36,36));assertTrue(g.safePosition(p));
            f.world.set(new MutableWorld.Cell(36,36,100),true,0x654321,null);assertEquals(100,g.surface(36,36));
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();var thread=new Thread(()->{try{g.surface(36,36);}catch(Throwable e){failure.set(e);}});thread.start();thread.join();assertInstanceOf(IllegalStateException.class,failure.get());
        }
    }
}
