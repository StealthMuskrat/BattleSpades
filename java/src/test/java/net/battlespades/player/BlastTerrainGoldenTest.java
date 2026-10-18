// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.protocol.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.rules.GameRules;
import net.battlespades.support.*;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class BlastTerrainGoldenTest {
    static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    static IntStream groups(){return IntStream.range(0,24);}
    @ParameterizedTest @MethodSource("groups") void originalNativeKnownCauserExactFallbackAndCubeCommit(int group)throws Exception{
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/blast-terrain.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        for(int caseId=group*8;caseId<(group+1)*8;caseId++){
            var first=rows.get(caseId*3);try(var h=new LifeHarness(GameRules.serverDefaults().compatibility(Map.of("RULE_BLOCK_HEALTH",number(first[4]),"RULE_SPAWN_PROTECTION_TIME",0.)))){
                h.admit(0,2);h.world.terrainHealth().configure(number(first[4]),net.battlespades.world.TerrainHealth.UserMode.NORMAL);var source=first[8].equals("1")?h.runtime.player(0):null;var ports=new TerrainPorts();ports.allows=first[6].equals("1");var random=new SourceRandom(8000+caseId);var terrain=new TerrainDamage(h.world,ports,()->random.below(256));var wire=new ArrayList<String>();ports.reliableSink=p->wire.add("B:"+HexFormat.of().formatHex(p.encode()));
                var blast=new BlastTerrain(h.world,terrain,new BlastTerrain.Delivery(){public List<BlastTerrain.Recipient> recipients(){return List.of(new BlastTerrain.Recipient(7,true,Set.of(91)),new BlastTerrain.Recipient(2,true,Set.of()),new BlastTerrain.Recipient(5,false,Set.of(91)));}public void reliable(BlastTerrain.Recipient recipient,GamePacket p){wire.add("R"+recipient.id()+":"+HexFormat.of().formatHex(p.encode()));}public void reliable(GamePacket p){wire.add("B:"+HexFormat.of().formatHex(p.encode()));}});
                var cells=new ArrayList<MutableWorld.Cell>();for(int x=39;x<42;x++)for(int y=35;y<38;y++)for(int z=96;z<99;z++)cells.add(new MutableWorld.Cell(x,y,z));if(first[5].equals("1"))for(int z=99;z<240;z++)cells.add(new MutableWorld.Cell(41,37,z));for(var c:cells)h.world.set(c,true,0x406080,first[3].equals("-")?null:number(first[3]));
                for(var r:rows.subList(caseId*3,caseId*3+3)){
                    ports.events.clear();wire.clear();int step=Integer.parseInt(r[1]);boolean result=blast.apply(new Vector(40.25,36.5,97.125),new double[]{.7,2.,10.}[step],source,1,r[2].equals("-")?null:Integer.valueOf(r[2]),r[7].equals("-")?null:Integer.valueOf(r[7]));String where="case "+caseId+" step "+step;assertEquals(r[9].equals("1"),result,where+" result");var state=cells.stream().map(c->c.x()+","+c.y()+","+c.z()+","+(h.world.solid(c)?1:0)+","+bits(h.world.terrainHealth().accumulated(c))+","+bits(h.world.initialHealth(c))+","+(h.world.solid(c)?h.world.color(c)&0xffffff:0)).toList();assertEquals(r[10],String.join(";",state),where+" canonical state");assertEquals(r[11],wire.isEmpty()?"-":String.join("|",wire),where+" wire");assertEquals(r[12],ports.events.isEmpty()?"-":String.join("~",ports.events),where+" effects");
                }
            }
        }
    }
}
