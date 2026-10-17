// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.rules.GameRules;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FlightGoldenTest {
    private static String scalar(Object value){return value==null?"-":value instanceof Boolean b?b?"1":"0":value.toString();}
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static Vector vector(String[] a){return new Vector(number(a[0]),number(a[1]),number(a[2]));}
    static String flight(PlayerLife p){
        var s=p.flight().snapshot();var event=s.event();
        String schedule=s.schedule().stream().map(v->v.label()+","+(v.active()?1:0)).collect(Collectors.joining(";"));
        return Stream.of(bits(s.fuel()),s.active(),s.physicsActive(),s.defer(),s.tail(),s.requiresRelease(),bits(s.heldSeconds()),bits(s.idleSeconds()),s.canopy(),s.canopyPhysics(),s.canopyOwner(),s.unsentFrames(),s.hoverHeld(),s.jumpHeld(),s.deployPending(),s.usedThisFall(),s.openFrames(),s.fallTouched(),s.fallbackClock(),schedule.isEmpty()?"-":schedule,event==null?"-":(event.active()?"1":"0")+","+event.reason()+","+scalar(event.label()),HexFormat.of().formatHex(p.flight().profile().encode()),bits(p.lastDamageWall())).map(FlightGoldenTest::scalar).collect(Collectors.joining("~"));
    }
    private static final class Replay extends InfantryGoldenTest.Replay {
        Replay(int cid){super(cid);p().flight().negotiate(0);p().motion().flightCapability(false);}
        @Override String[] operation(String op,String argument){
            var a=argument.split(",");events.clear();h.wire.clear();lanes.clear();boolean special=true;
            switch(op){
                case "equip" -> {
                    int pack=Integer.parseInt(a[1]);var loadout=new ArrayList<>(List.of(5,6));if(pack!=0)loadout.add(pack);if(a[2].equals("1"))loadout.add(72);
                    p().apply(new ClassCatalog.Selection(Integer.parseInt(a[0]),loadout,List.of(),List.of()));p().spawn(new Vector(36,36,20),h.monotonic,1,0x123456);
                }
                case "negotiate" -> {int capability=Integer.parseInt(argument);p().flight().negotiate(capability);p().motion().flightCapability(capability>0);}
                case "rtt" -> p().flight().roundTripMillis(argument.equals("-")?null:number(argument));
                case "flightsettings" -> p().flight().settings(new PlayerFlight.Settings(Integer.parseInt(a[0]),Integer.parseInt(a[1]),a[2].equals("1"),number(a[3]),number(a[4]),number(a[5]),Integer.parseInt(a[6])));
                case "owner" -> p().flight().ownerRowQueued(p());
                case "velocity" -> p().body().velocity(vector(a));
                case "hit" -> h.runtime.damage(0,number(argument),null,6);
                case "restock" -> h.runtime.restockJetpack(0);
                default -> special=false;
            }
            var result=special?result():super.operation(op,argument);
            return new String[]{result[0],result[1],result[2],flight(p())};
        }
    }
    @TestFactory Stream<DynamicTest> originalConnectedFlightSimulation()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        String manifest;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/golden/flight-simulation/index.txt"))){manifest=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        for(String name:manifest.lines().toList()){
            try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/flight-simulation/"+name)))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original flight "+e.getKey(),()->{
            try(var replay=new Replay(Integer.parseInt(e.getValue().getFirst()[1]))){int index=0;
                for(var row:e.getValue()){
                    var values=replay.operation(row[2],row[3]);String context="op "+index+++" "+row[2]+" "+row[3];
                    assertAll(context,()->assertEquals(row[4],values[0],"body/façade/chronology"),()->assertEquals(row[5],values[1],"observer boundaries"),()->assertEquals(row[6],values[2],"HP/death/restock wire"),()->assertEquals(row[7],values[3],"flight resources/canopy/handoff"));
                }
            }
        }));
    }
    @TestFactory Stream<DynamicTest> originalCanopyImpactArithmeticIncludingDisabledWaterHealth()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/flight-impact.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original canopy impact class "+e.getKey(),()->{
            try(var h=new LifeHarness(GameRules.serverDefaults().apply(Map.of("RULE_ENABLE_FALL_ON_WATER_DAMAGE",false)))){
                h.admit(0,2);var p=h.runtime.player(0);p.apply(new ClassCatalog.Selection(Integer.parseInt(e.getKey()),List.of(5,6,72),List.of(),List.of()));
                for(var row:e.getValue()){
                    p.flight().negotiate(Integer.parseInt(row[1]));p.body().position(new Vector(36,36,number(row[5])));
                    assertEquals(Integer.parseInt(row[6]),p.flight().speedDamage(p,1.0/60,number(row[3]),row[4].equals("1"),number(row[2])),String.join(" ",row));
                }
            }
        }));
    }
}
