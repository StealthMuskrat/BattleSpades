// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ShotTallyGoldenTest {
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static String state(PlayerLife p){String counts=p.shotStatistics().violations().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).collect(Collectors.joining(";"));return CombatCommitGoldenTest.aggregates(p)+"|"+(counts.isEmpty()?"-":counts);}
    @TestFactory Stream<DynamicTest> originalShotAggregatesAndSeedSkew()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/shot-tally.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original shot tally "+e.getKey(),()->{try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,2);for(var p:h.runtime.players())p.fire().tool(6,true);var tally=new ShotTally();int index=0;
            for(var r:e.getValue()){
                var a=r[2].split(",");var p=h.runtime.player(Integer.parseInt(a[0]));var reports=new ArrayList<ShotValidation.Event>();var resolved=new ArrayList<ShotTally.Resolved>();
                switch(r[1]){
                    case "begin"->tally.begin(p);case "hit"->tally.notePlayerHit(p,a[1].equals("1"));case "end"->tally.end(p,resolved::add);case "seed"->tally.observeSeed(p,Integer.parseInt(a[1]),reports::add);case "tool"->p.fire().tool(Integer.parseInt(a[1]),true);case "bot"->p.bot(a[1].equals("1"));case "spawn"->p.spawn(new Vector(36,36,97),h.monotonic,1,p.color());default->throw new AssertionError(r[1]);
                }
                String context="operation "+index+++" "+r[1]+" "+r[2];assertEquals(r[3],state(h.runtime.player(0))+"#"+state(h.runtime.player(1)),context+" state");
                String events=reports.stream().map(report->report.kind()+","+report.detail().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(v->v.getKey()+"="+(v.getValue() instanceof Double d?"n"+bits(d):"i"+v.getValue())).collect(Collectors.joining(","))).collect(Collectors.joining(";"));assertEquals(r[4],events.isEmpty()?"-":events,context+" diagnostic");String callbacks=resolved.stream().map(c->c.player().id()+","+c.tool()+","+(c.hit()?1:0)).collect(Collectors.joining(";"));assertEquals(r[5],callbacks.isEmpty()?"-":callbacks,context+" callback");
            }
        }}));
    }
}
