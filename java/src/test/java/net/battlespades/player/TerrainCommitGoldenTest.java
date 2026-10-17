// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import net.battlespades.combat.TerrainDamage;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;

final class TerrainCommitGoldenTest {
    private static final List<String[]> ROWS=load();
    private static List<String[]> load(){try(var in=TerrainCommitGoldenTest.class.getResourceAsStream("/golden/terrain-commit.tsv.gz");var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(in),java.nio.charset.StandardCharsets.UTF_8))){return reader.lines().map(s->s.split("\t")).toList();}catch(IOException failure){throw new UncheckedIOException(failure);}}
    static IntStream groups(){return IntStream.range(0,16);}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static Vector vector(String value){var c=value.split(",");return new Vector(number(c[0]),number(c[1]),number(c[2]));}
    private static void exact(String expected,double actual){assertEquals(Long.parseUnsignedLong(expected,16),Double.doubleToRawLongBits(actual));}
    @ParameterizedTest @MethodSource("groups") void originalAdmittedTerrainPaths(int group){
        for(int caseId=group*16;caseId<(group+1)*16;caseId++){var first=ROWS.get(caseId*3);double scale=number(first[8]);var rules=GameRules.serverDefaults().compatibility(Map.of("RULE_BLOCK_HEALTH",scale,"RULE_SPAWN_PROTECTION_TIME",0));
            try(var h=new LifeHarness(rules)){
                h.admit(0,2);var player=h.runtime.player(0);player.fire().tool(Integer.parseInt(first[2]),true);player.orientation(1,0,0);var userMode=switch(first[11]){case "cctf"->TerrainHealth.UserMode.CLASSIC;case "ugc"->TerrainHealth.UserMode.UGC;default->TerrainHealth.UserMode.NORMAL;};h.world.terrainHealth().configure(scale,userMode);Double initial=first[7].equals("-")?null:number(first[7]);
                for(int x=39;x<42;x++)for(int y=35;y<38;y++)for(int z=96;z<99;z++)h.world.set(new MutableWorld.Cell(x,y,z),true,0x406080,initial);
                if(first[6].equals("1"))for(int z=99;z<240;z++)h.world.set(new MutableWorld.Cell(41,37,z),true,0x406080,initial);
                var ports=new TerrainPorts();ports.allows=first[4].equals("1");ports.build=first[5].equals("1");ports.active=first[10].equals("1");var random=new SourceRandom(8000+caseId);var terrain=new TerrainDamage(h.world,ports,()->random.below(256));
                var origin=new Vector(36,36,97);var direction=first[12].equals("1")?new Vector(0,1,0):new Vector(1,0,0);var packet=new ShootPacket(0,254,0,new Vector3f(36,36,97),new Vector3f(1,0,0),32767,32767,true,first[9].equals("1"),0);List<Vector> eyes=first[13].equals("-")?null:first[13].equals("empty")?List.of():Arrays.stream(first[13].split(";")).map(TerrainCommitGoldenTest::vector).toList();
                for(int step=0;step<3;step++){
                    ports.events.clear();ports.wire.clear();var row=ROWS.get(caseId*3+step);boolean result=first[3].equals("gun")?terrain.gun(player,new Cell(39,36,97),new double[]{.7,2,10}[step]):terrain.dig(player,origin,direction,packet,eyes,number(first[14]));assertEquals(row[15].equals("1"),result,"result "+caseId+"/"+step);assertEquals(Integer.parseInt(row[16]),player.inventory().blocks(),"blocks "+caseId);
                    for(var value:row[17].split(";")){var c=value.split(",");var cell=new MutableWorld.Cell(Integer.parseInt(c[0]),Integer.parseInt(c[1]),Integer.parseInt(c[2]));assertEquals(c[3].equals("1"),h.world.solid(cell),"solid "+caseId+" "+value);exact(c[4],h.world.terrainHealth().accumulated(cell));exact(c[5],h.world.initialHealth(cell));}
                    assertEquals(row[18],ports.wire.isEmpty()?"-":String.join("|",ports.wire),"wire "+caseId+"/"+step);assertEquals(row[19],ports.events.isEmpty()?"-":String.join("~",ports.events),"events "+caseId+"/"+step);
                }
            }
        }
    }
}
