// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFireGoldenTest {
    private static String scalar(Object value){return value==null?"-":value.toString();}
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String state(PlayerLife p){
        var s=p.fire().state();var inv=p.inventory();var ammo=inv.ammo();
        var values=new ArrayList<>(List.of(""+inv.tool(),""+inv.weapon(),s.raw()?"1":"0",scalar(s.beforeMachineGun()),""+ammo.clip(),""+ammo.reserve(),inv.reloading()?"1":"0",bits(inv.reloadEnd()),bits(s.lastShot()),bits(s.nextShot()),s.autoReloadFloor()==null?"-":s.autoReloadFloor().tool()+":"+s.autoReloadFloor().label(),s.donePending()?"1":"0",""+p.fire().profile().id()));
        values.add(inv.weapons().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+":"+e.getValue().clip()+":"+e.getValue().reserve()).collect(Collectors.joining(";")));
        String lanes=p.actionClock().lanes().entrySet().stream().map(e->{var l=e.getValue();return Stream.of(e.getKey(),scalar(l.label()),bits(l.interval()),bits(l.due()),bits(l.tokens()),bits(l.tokenInterval()),bits(l.refilledAt()),l.admitted(),l.refused(),l.byLabel()).map(Object::toString).collect(Collectors.joining(","));}).collect(Collectors.joining(";"));values.add(lanes.isEmpty()?"-":lanes);
        return String.join("~",values);
    }
    @TestFactory Stream<DynamicTest> originalPlayerReloadAndFireTiming()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/player-fire.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],k->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original player fire "+e.getKey(),()->{
            try(var h=new LifeHarness()){
                h.admit(0,2);var p=h.runtime.player(0);int index=0;
                for(var row:e.getValue()){
                    var a=row[2].split(",");String result="-";h.wire.clear();
                    switch(row[1]){
                        case "tool" -> p.fire().tool(Integer.parseInt(a[0]),a[1].equals("-")?null:a[1].equals("1"));
                        case "ammo" -> p.inventory().ammo(Integer.parseInt(a[0]),Integer.parseInt(a[1]));
                        case "newest" -> p.inputTimeline().receive(new LiveClientInput(Integer.parseInt(row[2]),0,false,6,1,0,0,0,0,0,0),false,false,p.aim(),1000,0,false);
                        case "time" -> h.monotonic=number(row[2]);
                        case "link" -> p.fire().link(a[0].equals("-")?null:new ActionClock.Link(number(a[0]),number(a[1])));
                        case "bot" -> p.bot(row[2].equals("1"));
                        case "can","shot" -> {Double interval=a[0].equals("-")?null:number(a[0]);Integer label=a[1].equals("-")?null:Integer.valueOf(a[1]);result=(row[1].equals("can")?p.fire().canFire(h.monotonic,interval,label):p.fire().consumeShot(h.monotonic,interval,label))?"1":"0";}
                        case "reload" -> result=p.fire().startReload(h.monotonic)?"1":"0";
                        case "advance" -> result=p.fire().advanceReload(h.monotonic)?"1":"0";
                        case "announce" -> p.fire().advanceAndAnnounce(h.monotonic,h.wire::add);
                        case "finish" -> result=p.fire().finishReload()?"1":"0";
                        case "spawn" -> p.spawn(new Vector(36,36,97),h.monotonic,1,p.color());
                        case "crate" -> p.inventory().restock(3,h.monotonic,p.bot());
                        case "launcher" -> p.inventory().launcherUse(Integer.parseInt(a[0]),Integer.parseInt(a[1]),number(a[2]));
                        case "stock" -> p.inventory().oriented(Integer.parseInt(a[0]),Integer.parseInt(a[1]));
                        default -> throw new AssertionError(row[1]);
                    }
                    String context="operation "+index+++" "+row[1]+" "+row[2];assertEquals(row[3],result,context+" result");assertEquals(row[4],state(p),context+" state");
                    String wire=h.wire.stream().map(packet->HexFormat.of().formatHex(packet.encode())).collect(Collectors.joining(";"));assertEquals(row[5],wire.isEmpty()?"-":wire,context+" broadcasts");
                }
            }
        }));
    }
}
