// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.physics.WorldQueries.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldQueriesTest {
    private static List<String> lines(String name) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(resource(name),StandardCharsets.UTF_8))){return reader.lines().toList();}
    }
    private static java.io.InputStream resource(String name) throws java.io.IOException {
        var stream=Objects.requireNonNull(WorldQueriesTest.class.getResourceAsStream("/golden/"+name));
        return name.endsWith(".gz")?new GZIPInputStream(stream):stream;
    }
    private static double[] values(String text){return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();}
    private static Vector vector(double[] a,int i){return new Vector(a[i],a[i+1],a[i+2]);}
    private static Cube cube(double[] a,int i){return new Cube((int)a[i],(int)a[i+1],(int)a[i+2]);}
    private static void exact(double[] expected,double[] actual,String context){
        assertEquals(expected.length,actual.length,context);
        for(int i=0;i<expected.length;i++)assertEquals(Double.doubleToLongBits(expected[i]),Double.doubleToLongBits(actual[i]),context+" field "+i);
    }
    @Test void compiledSourceQueries() throws Exception {
        var cells=new HashSet<Cube>();for(String line:lines("query-cells.tsv"))cells.add(cube(values(line),0));
        CollisionMap map=(x,y,z)->z==239||cells.contains(new Cube(x,y,z));
        int row=0;
        for(String line:lines("queries.tsv.gz")){
            var fields=line.split("\t");double[] a=values(fields[1]);String context="row "+row+++" "+line;
            switch(fields[0]){
                case "ray"->{
                    var hit=WorldQueries.ray(map,vector(a,0),vector(a,3),a[6],a[7]!=0);
                    if(fields[2].equals("none"))assertTrue(hit.isEmpty(),context);
                    else {var h=hit.orElseThrow();exact(values(fields[2]),new double[]{h.position().x(),h.position().y(),h.position().z(),h.cube().x(),h.cube().y(),h.cube().z(),h.face()},context);}
                }
                case "line"->{var expected=Arrays.stream(fields[2].split(";")).map(v->cube(values(v),0)).toList();assertEquals(expected,WorldQueries.cubeLine(cube(a,0),cube(a,3)),context);}
                case "face"->{var center=WorldQueries.faceCenter(cube(a,0),(int)a[3]);var next=WorldQueries.adjacent(cube(a,0),(int)a[3]);exact(values(fields[2]),new double[]{center.x(),center.y(),center.z(),next.x(),next.y(),next.z()},context);}
                case "center"->assertEquals(fields[2].equals("1"),WorldQueries.centered(vector(a,0),vector(a,3),vector(a,6),a[9]),context);
                default->fail(context);
            }
        }
        assertEquals(941,row);
    }
    @Test void queryBoundsAndSourceTieDecisions(){
        var zero=new Vector(0,0,0);CollisionMap empty=(x,y,z)->false;
        assertTrue(WorldQueries.ray(empty,zero,zero).isEmpty());
        assertTrue(WorldQueries.ray(empty,zero,new Vector(1,0,0),-1,false).isEmpty());
        assertThrows(IllegalArgumentException.class,()->WorldQueries.ray(empty,zero,zero,Double.POSITIVE_INFINITY,false));
        assertThrows(IllegalArgumentException.class,()->WorldQueries.cubeLine(new Cube(Integer.MIN_VALUE,0,0),new Cube(Integer.MAX_VALUE,0,0)));
        assertEquals(List.of(new Cube(0,0,0),new Cube(0,0,1),new Cube(0,1,1),new Cube(1,1,1)),WorldQueries.cubeLine(new Cube(0,0,0),new Cube(1,1,1)));
    }
}
