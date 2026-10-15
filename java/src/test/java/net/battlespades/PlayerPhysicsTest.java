// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.*;
import net.battlespades.world.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPhysicsTest {
    private static int key(int x,int y,int z){return (x*512+y)*240+z;}
    @TestFactory Stream<DynamicTest> originalEngineTraces() throws Exception {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/movement.tsv.gz"))),StandardCharsets.UTF_8))){
            for(String line:reader.lines().toList()){var fields=line.split("\t");groups.computeIfAbsent(fields[0],k->new ArrayList<>()).add(fields);}
        }
        return groups.entrySet().stream().map(entry->DynamicTest.dynamicTest(entry.getKey(),()->replay(entry.getValue())));
    }
    private static void replay(List<String[]> rows){
        var cells=new HashSet<Integer>();
        PlayerPhysics p=null;double gravity=1;
        for(int row=0;row<rows.size();row++){
            String[] r=rows.get(row);List<?> args=parseArray(r[2]);int result=0;
            switch(r[1]){
                case "new"->{
                    cells.clear();p=new PlayerPhysics((x,y,z)->x>=0&&x<512&&y>=0&&y<512&&z>=0&&z<240&&(z==239||cells.contains(key(x,y,z))));
                    p.profile(MovementProfile.forClass(integer(args,0)));gravity=number(args,1);
                }
                case "position"->p.position(vector(args));
                case "velocity"->p.velocity(vector(args));
                case "orientation"->p.orientation(vector(args));
                case "input"->p.input(new Input(flag(args,0),flag(args,1),flag(args,2),flag(args,3),flag(args,4),flag(args,5),flag(args,6)));
                case "flight"->p.flight(new Flight(integer(args,0),flag(args,1),flag(args,2),flag(args,3),flag(args,4),flag(args,5),(float)number(args,6),(float)number(args,7),flag(args,8)));
                case "crouch"->p.crouch(flag(args,0),peers(args));
                case "dead"->p.alive(!flag(args,0));
                case "exploded"->p.exploded(flag(args,0));
                case "burdened"->p.burdened(flag(args,0));
                case "slowdown"->p.climbSlowdown((float)number(args,0));
                case "lock"->p.lock(vector(args),new Vector(number(args,3),number(args,4),number(args,5)));
                case "unlock"->p.unlock();
                case "cell"->{int k=key(integer(args,0),integer(args,1),integer(args,2));if(flag(args,3))cells.add(k);else cells.remove(k);}
                case "box"->{for(int x=integer(args,0);x<=integer(args,3);x++)for(int y=integer(args,1);y<=integer(args,4);y++)for(int z=integer(args,2);z<=integer(args,5);z++)cells.add(key(x,y,z));}
                case "tick"->result=p.update(number(args,0),gravity,peers(args));
                default->fail("Unknown command "+r[1]);
            }
            String context=r[0]+" row "+row+" "+r[1]+" "+r[2];
            assertEquals(Integer.parseInt(r[3]),result,context+" landing result");
            var s=Objects.requireNonNull(p).state();
            double[] values={s.position().x(),s.position().y(),s.position().z(),s.velocity().x(),s.velocity().y(),s.velocity().z()};
            for(int i=0;i<6;i++)assertEquals(Double.doubleToLongBits(Double.valueOf(r[4+i])),Double.doubleToLongBits(values[i]),context+" field "+i+" expected "+Double.valueOf(r[4+i])+" actual "+values[i]);
            boolean[] flags={s.airborne(),s.wade(),s.crouch(),s.jump(),s.jumped()};
            for(int i=0;i<5;i++)assertEquals(r[10+i].equals("1"),flags[i],context+" flag "+i);
        }
    }
    private static Vector vector(List<?> a){return new Vector(number(a,0),number(a,1),number(a,2));}
    private static double number(List<?> a,int i){return ((Number)a.get(i)).doubleValue();}
    private static int integer(List<?> a,int i){return ((Number)a.get(i)).intValue();}
    private static boolean flag(List<?> a,int i){return a.get(i) instanceof Boolean b?b:number(a,i)!=0;}
    private static List<Peer> peers(List<?> a){
        var peers=new ArrayList<Peer>();for(int i=1;i<a.size();i++){var v=(List<?>)a.get(i);peers.add(new Peer(number(v,0),number(v,1),number(v,2),v.size()>3?number(v,3):2.699999928474426));}return peers;
    }
    // The fixture grammar is arrays, finite numbers and booleans only.
    private static List<?> parseArray(String text){return new ArrayParser(text).array();}
    private static final class ArrayParser {
        final String text;int offset;
        ArrayParser(String text){this.text=text;}
        List<?> array(){
            if(text.charAt(offset++)!='[')throw new IllegalArgumentException(text);
            var values=new ArrayList<>();
            while(text.charAt(offset)!=']'){
                if(text.charAt(offset)=='[')values.add(array());
                else {int start=offset;while(text.charAt(offset)!=','&&text.charAt(offset)!=']')offset++;String token=text.substring(start,offset);values.add(switch(token){case "true"->Boolean.TRUE;case "false"->Boolean.FALSE;default->Double.valueOf(token);});}
                if(text.charAt(offset)==',')offset++;
            }
            offset++;return values;
        }
    }
    @Test void mutableTerrainChangesCollisionWithoutReplacingMover(){
        byte[] column={0,(byte)239,(byte)239,0,0,0,0,0};
        try(var world=new MutableWorld(VxlMap.load(column,"retail"),0,10)){
            var p=new PlayerPhysics(CollisionMap.of(world));p.position(new Vector(10.5,10.5,59.75));
            for(int x=10;x<=11;x++)for(int y=10;y<=11;y++)world.set(new MutableWorld.Cell(x,y,62),true,0xffffff,null);
            p.update(1.0/60,1,List.of());assertFalse(p.state().airborne());
            for(int x=10;x<=11;x++)for(int y=10;y<=11;y++)world.set(new MutableWorld.Cell(x,y,62),false,0,null);
            p.update(1.0/60,1,List.of());assertTrue(p.state().airborne());
        }
    }
    @Test void validatesProfilesAndRejectsCrossThreadStateAccess() throws InterruptedException {
        assertThrows(IllegalArgumentException.class,()->MovementProfile.forClass(18));
        assertThrows(IllegalArgumentException.class,()->new Flight(5,false,false,false,false,false,0.1f,0.05f,false));
        var p=new PlayerPhysics((x,y,z)->false);
        assertThrows(IllegalArgumentException.class,()->p.update(Double.NaN,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->new Vector(Double.POSITIVE_INFINITY,0,0));
        var failure=new AtomicReference<Throwable>();var thread=Thread.ofPlatform().start(()->{try{p.state();}catch(Throwable e){failure.set(e);}});thread.join();
        assertInstanceOf(IllegalStateException.class,failure.get());
    }
}
