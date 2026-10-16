// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.LiveClientInput;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ActionClockTest {
    private static String scalar(Object value){return value==null?"-":value instanceof Boolean b?b?"1":"0":value.toString();}
    private static String bits(double v){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(v));}
    private static double number(String s){return Double.longBitsToDouble(Long.parseUnsignedLong(s,16));}
    private static String state(ActionClock clock){var value=clock.lanes().entrySet().stream().map(e->{var l=e.getValue();return Stream.of(e.getKey(),scalar(l.label()),bits(l.interval()),bits(l.due()),bits(l.tokens()),bits(l.tokenInterval()),bits(l.refilledAt()),l.admitted(),l.refused(),l.byLabel()).map(ActionClockTest::scalar).collect(Collectors.joining(","));}).collect(Collectors.joining(";"));return value.isEmpty()?"-":value;}
    private static void newest(InputTimeline timeline,int label){timeline.receive(new LiveClientInput(label,0,false,6,1,0,0,0,0,0,0),false,false,new Vector(1,0,0),0,0,false);}
    @TestFactory Stream<DynamicTest> originalActionCadence()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/action-clock.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("source cadence "+e.getKey(),()->{
            var timeline=new InputTimeline();newest(timeline,600);var clock=new ActionClock(timeline);int index=0;
            for(var r:e.getValue()){
                var a=r[2].split(",");String result="-";
                try{switch(r[1]){
                    case "admit","peek" -> {
                        Integer label=a[1].equals("-")?null:Integer.valueOf(a[1]),min=a[4].equals("-")?null:Integer.valueOf(a[4]);var link=a[6].equals("-")?null:new ActionClock.Link(number(a[6]),number(a[7]));
                        result=scalar(r[1].equals("admit")?clock.admit(a[0],label,number(a[2]),number(a[3]),min,number(a[5]),link):clock.peek(a[0],label,number(a[2]),number(a[3]),min,number(a[5]),link));
                    }
                    case "reset" -> clock.reset(r[2].equals("-")?null:r[2]);
                    case "newest" -> newest(timeline,Integer.parseInt(r[2]));
                    case "stall" -> result=bits(ActionClock.linkStallSeconds(a[0].equals("-")?null:new ActionClock.Link(number(a[0]),number(a[1]))));
                    default -> throw new AssertionError(r[1]);
                }}catch(IllegalArgumentException failure){result="error";}
                assertEquals(r[3],result,"result "+index+" "+r[1]+" "+r[2]);assertEquals(r[4],state(clock),"lane "+index+++" "+r[1]+" "+r[2]);
            }
        }));
    }
    @Test void peekCannotSpendTokensAndNewLifeClearsCadenceWithoutForgettingClientClock(){
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);newest(p.inputTimeline(),600);var clock=p.actionClock();
            for(int i=0;i<100;i++)assertTrue(clock.peek("fire",100,.1,20,null,ActionClock.ARRIVAL_GRACE,null));assertTrue(clock.lanes().isEmpty());
            assertTrue(clock.admit("fire",100,.1,20,null,ActionClock.ARRIVAL_GRACE,null));assertFalse(clock.admit("fire",100,.1,20,null,ActionClock.ARRIVAL_GRACE,null));
            var snapshot=clock.lanes();assertThrows(UnsupportedOperationException.class,()->snapshot.clear());
            h.runtime.die(0,null,12);h.runtime.respawn(0);assertTrue(clock.lanes().isEmpty());assertEquals(600,p.inputTimeline().newestLabel());assertFalse(snapshot.isEmpty());
        }
    }
    @Test void forgedLabelsDoNotIncreaseSustainedServerTimeBudget(){
        var input=new InputTimeline();var clock=new ActionClock(input);int accepted=0;
        for(int step=0;step<=6000;step++){newest(input,step*100);if(clock.admit("fire",step*100,.4,100+step/1000.0,null,ActionClock.ARRIVAL_GRACE,null))accepted++;}
        assertTrue(accepted<=17,"Six seconds at 2.5 Hz plus bounded initial allowance: "+accepted);
    }
}
