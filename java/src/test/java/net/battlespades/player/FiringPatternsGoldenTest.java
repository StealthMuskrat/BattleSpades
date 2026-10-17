// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.combat.FiringPatterns;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FiringPatternsGoldenTest {
    static String bits(double n){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(n));}
    static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    static String scalar(Object o){return o==null?"-":o.toString();}
    static String state(PlayerLife p,FiringPatterns patterns){
        var inv=p.inventory();var ammo=inv.ammo();var f=p.fire().state();var floor=f.autoReloadFloor();var burst=patterns.bursts().get(0);var spin=patterns.spins().get(0);
        var values=new ArrayList<>(List.of(""+ammo.clip(),""+ammo.reserve(),inv.reloading()?"1":"0",bits(inv.reloadEnd()),bits(f.lastShot()),bits(f.nextShot()),floor==null?"-":floor.tool()+":"+floor.label(),burst==null?"-":burst.count()+","+burst.lastLabel()+","+burst.firstLabel()+","+bits(burst.startedAt()),spin==null?"-":bits(spin.lastPacketAt())+","+bits(spin.interval())+","+scalar(spin.label())));
        var lanes=p.actionClock().lanes().entrySet().stream().map(e->{var l=e.getValue();return Stream.of(e.getKey(),scalar(l.label()),bits(l.interval()),bits(l.due()),bits(l.tokens()),bits(l.tokenInterval()),bits(l.refilledAt()),l.admitted(),l.refused(),l.byLabel()).map(Object::toString).collect(Collectors.joining(","));}).collect(Collectors.joining(";"));values.add(lanes.isEmpty()?"-":lanes);return String.join("~",values);
    }
    @TestFactory Stream<DynamicTest> originalBurstAndSpinWithOwnedGun()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/firing-patterns.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original firing patterns "+e.getKey(),()->{try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var patterns=new FiringPatterns();int index=0;
            for(var r:e.getValue()){
                var a=r[2].split(",");String result="-";
                switch(r[1]){
                    case "tool" -> p.fire().tool(Integer.parseInt(r[2]),true);
                    case "ammo" -> p.inventory().ammo(Integer.parseInt(a[0]),Integer.parseInt(a[1]));
                    case "newest" -> p.inputTimeline().receive(new LiveClientInput(Integer.parseInt(r[2]),0,false,6,1,0,0,0,0,0,0),false,false,p.aim(),1000,0,false);
                    case "time" -> h.monotonic=number(r[2]);
                    case "link" -> p.fire().link(a[0].equals("-")?null:new ActionClock.Link(number(a[0]),number(a[1])));
                    case "bot" -> p.bot(r[2].equals("1"));
                    case "controls" -> p.motion().controls(0,Integer.parseInt(a[0])|(Integer.parseInt(a[1])<<1));
                    case "shot" -> result=(p.inventory().tool()==60?patterns.assault(p,Integer.parseInt(r[2]),h.monotonic):patterns.minigun(p,h.monotonic,r[2].equals("-")?null:Integer.valueOf(r[2])))?"1":"0";
                    case "reload" -> result=p.fire().startReload(h.monotonic)?"1":"0";
                    case "forget" -> patterns.forgetPlayer(0);
                    case "spawn" -> p.spawn(new Vector(36,36,97),h.monotonic,1,p.color());
                    default -> throw new AssertionError(r[1]);
                }
                String context="group "+e.getKey()+" operation "+index+++" "+r[1];assertEquals(r[3],result,context+" admitted");assertEquals(r[4],state(p,patterns),context+" state");
            }
        }}));
    }
}
