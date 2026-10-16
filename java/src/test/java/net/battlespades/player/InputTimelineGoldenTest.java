// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class InputTimelineGoldenTest {
    private static String scalar(Object value){return value==null?"-":value instanceof Boolean b?b?"1":"0":value.toString();}
    private static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    private static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    private static String aim(Vector v){return v==null?"-":bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static String join(Object... values){return Arrays.stream(values).map(InputTimelineGoldenTest::scalar).collect(Collectors.joining(","));}
    private static String snapshot(InputTimeline timeline){
        var s=timeline.state();var c=s.counters();
        String frames=s.frames().stream().map(f->join(f.label(),f.movement(),aim(f.aim()),f.actions(),f.receivedTick(),f.ownerSequence(),f.inputSequence(),f.unknown(),f.topology())).collect(Collectors.joining(";"));
        String guesses=s.guesses().entrySet().stream().map(e->join(e.getKey(),e.getValue().get(0),e.getValue().get(1))).collect(Collectors.joining(";"));
        return Stream.of(s.newest(),s.applied(),s.fresh(),s.synthesized(),s.ownerSequence(),s.inputSequence(),s.currentInputSequence(),s.currentOwnerSequence(),s.pendingMovement(),s.pendingLabel(),s.pendingTick(),s.pendingOwner(),s.pendingUnknown(),s.sourceLabel(),s.sourceTick(),s.sourceOwner(),s.sourceUnknown(),s.appliedMovement(),aim(s.appliedAim()),s.appliedActions(),s.pressMovement(),s.pressActions(),s.orientationAfterSynth(),frames.isEmpty()?"-":frames,guesses.isEmpty()?"-":guesses,c.dropped(),c.stale(),c.overflow(),c.applied(),c.synthesized(),c.reordered(),c.salvaged(),c.presses(),c.rejectedAhead()).map(InputTimelineGoldenTest::scalar).collect(Collectors.joining("~"));
    }
    private static String step(InputTimeline.Step s){return join(s.label(),s.movement(),aim(s.aim()),s.actions(),s.synthesized(),s.sourceLabel(),s.sourceTick(),s.sourceOwner(),s.sourceUnknown(),s.inputSequence(),s.ownerSequence());}
    private static final class Replay {
        final InputTimeline timeline;
        Vector currentAim=new Vector(1,0,0);
        boolean alive=true,currentZoom;
        Replay(boolean latch){timeline=new InputTimeline(latch);timeline.resetLife();}
        void orient(Vector raw){double n=Math.sqrt(raw.x()*raw.x()+raw.y()*raw.y()+raw.z()*raw.z());if(n>1e-6)currentAim=new Vector(raw.x()/n,raw.y()/n,raw.z()/n);}
        String[] operation(String op,String argument){
            var a=argument.split(",");String result="-",observed="-";
            switch(op){
                case "receive" -> {
                    int label=Integer.parseInt(a[0]),tick=Integer.parseInt(a[1]),movement=Integer.parseInt(a[2]),actions=Integer.parseInt(a[3]);var raw=new Vector(number(a[5]),number(a[6]),number(a[7]));
                    var packet=new LiveClientInput(label,0,(actions&256)!=0,6,raw.x(),raw.y(),raw.z(),Integer.parseInt(a[4]),movement,actions&255,0);
                    var arrival=timeline.receive(packet,alive,alive,currentAim,tick,Long.parseLong(a[8]),a[9].equals("1"));
                    if(arrival.fresh()){orient(raw);currentZoom=(actions&4)!=0;}
                }
                case "consume" -> {
                    var selected=timeline.consumeOne(Integer.parseInt(a[0]),currentAim);
                    if(selected.isPresent()){
                        var s=selected.get();observed=step(s);orient(s.aim());if(s.actions()!=null)currentZoom=(s.actions()&4)!=0;
                        if(!s.synthesized())orient(timeline.state().appliedAim());
                    }
                }
                case "dead" -> alive=false;
                case "spawn" -> {alive=true;currentZoom=false;timeline.resetLife();}
                case "owner" -> result=scalar(timeline.claimOwnerSequence(argument.equals("-")?null:Long.valueOf(argument)));
                case "spread" -> result=scalar(timeline.reorderSpread(Integer.parseInt(argument)));
                case "zoom" -> result=scalar(timeline.zoomForAction(argument.equals("-")?null:Integer.valueOf(argument),currentZoom));
                case "arrival" -> result=scalar(timeline.labelArrivalTick(argument.equals("-")?null:Integer.valueOf(argument)));
                default -> throw new AssertionError(op);
            }
            return new String[]{snapshot(timeline),observed,result};
        }
    }
    @TestFactory Stream<DynamicTest> originalClientDataChronology()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var input=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/input-timeline.tsv.gz")))){
            new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("source input "+e.getKey(),()->{
            var replay=new Replay(e.getValue().getFirst()[1].equals("1"));int index=0;
            for(var r:e.getValue()){
                var observed=replay.operation(r[2],r[3]);String context="operation "+index+++" "+r[2]+" "+r[3];
                assertAll(context,()->assertEquals(r[4],observed[0],"chronology"),()->assertEquals(r[5],observed[1],"movement phase"),()->assertEquals(r[6],observed[2],"action evidence"));
            }
        }));
    }
}
