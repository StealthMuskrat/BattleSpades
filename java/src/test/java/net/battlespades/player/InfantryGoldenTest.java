// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import net.battlespades.world.MutableWorld;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class InfantryGoldenTest {
    private static String scalar(Object value){return value==null?"-":value instanceof Boolean b?b?"1":"0":value.toString();}
    private static String bits(double value){return HexFormat.of().toHexDigits(Double.doubleToRawLongBits(value));}
    private static double number(String value){return Double.longBitsToDouble(Long.parseUnsignedLong(value,16));}
    private static String time(Double value){return value==null?"-":bits(value);}
    private static String vector(Vector v){return v==null?"-":bits(v.x())+","+bits(v.y())+","+bits(v.z());}
    private static Vector vector(String[] a,int offset){return new Vector(number(a[offset]),number(a[offset+1]),number(a[offset+2]));}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException failure){throw new AssertionError(failure);}}
    private static String snapshot(PlayerLife p){
        var b=p.body().state();var m=p.motion().snapshot();String eyes=m.eyes().entrySet().stream().map(e->e.getKey()+":"+vector(e.getValue().position())+":"+vector(e.getValue().aim())).collect(Collectors.joining(";"));
        String delays=m.queueDelays().stream().map(Object::toString).collect(Collectors.joining(","));
        return Stream.of(vector(b.position()),vector(b.velocity()),b.airborne(),b.wade(),b.crouch(),b.jump(),b.jumped(),vector(PlayerMotion.clipped(b.velocity())),vector(p.aim()),p.health(),p.alive(),p.spawned(),m.movement(),m.actions(),m.starved(),m.synthesizedGravity(),m.catchup(),m.backlogDropped(),m.starvedStreak(),m.backlogOver(),m.backlogCatchup(),m.timeoutFlagged(),time(m.lastPacket()),time(m.lastSimulated()),bits(m.starvationBaseline()),bits(m.movementTime()),bits(m.airTime()),bits(m.airborneSince()),time(m.ownBlast()),m.fallResult(),m.landed(),m.triggerJump(),m.retailJumpLabel(),m.queuedImpulses(),m.housekeeping(),digest(eyes),delays.isEmpty()?"-":delays,digest(InputTimelineGoldenTest.snapshot(p.inputTimeline()))).map(InfantryGoldenTest::scalar).collect(Collectors.joining("~"));
    }
    private static final class Replay implements AutoCloseable {
        final LifeHarness h=new LifeHarness();
        final InfantrySimulation simulation;
        final List<String> events=new ArrayList<>();
        final Map<GamePacket,String> lanes=new IdentityHashMap<>();
        int tick=1000,pending;
        boolean burden;
        Replay(int cid){
            for(int x=20;x<=70;x++)for(int y=20;y<=70;y++)h.world.set(new MutableWorld.Cell(x,y,100),true,0x123456,null);
            h.admit(0,2);p().apply(new ClassCatalog.Selection(cid,List.of(5,6,23),List.of(),List.of()));p().god(true);
            h.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){lanes.put(p,"B");}public void owner(int id,GamePacket p){lanes.put(p,"O"+id);}public boolean inGame(int id){return true;}};
            simulation=new InfantrySimulation(h.runtime,InfantrySimulation.Settings.defaults(),new InfantrySimulation.Hooks(){
                public void lagHistory(PlayerLife p,int loop){events.add("lag:"+p.id());}
                public void housekeeping(PlayerLife p){}
                public boolean burdened(PlayerLife p){return burden;}
                public int pendingTerrainMutations(){return pending;}
                public void report(PlayerLife p,String kind,boolean enforced){events.add("report:"+kind+":"+(enforced?1:0));}
                public void disconnect(PlayerLife p,int reason){events.add("disconnect:"+reason);}
                public void afterMove(PlayerLife p,double dt){}
                public void afterTick(PlayerLife p,double dt){events.add("after:"+p.id());}
                public void failed(PlayerLife p,RuntimeException failure){throw new AssertionError(failure);}
            });
        }
        PlayerLife p(){return h.runtime.player(0);}
        String[] operation(String op,String argument){
            var a=argument.split(",");events.clear();h.wire.clear();lanes.clear();
            switch(op){
                case "receive" -> {int actions=Integer.parseInt(a[2]);var aim=vector(a,4);h.runtime.input(0,new LiveClientInput(Integer.parseInt(a[0]),0,(actions&256)!=0,6,aim.x(),aim.y(),aim.z(),Integer.parseInt(a[3]),Integer.parseInt(a[1]),actions&255,0),tick);}
                case "tick" -> {h.monotonic+=number(argument);h.wall=h.monotonic+900;simulation.tick(++tick);}
                case "settings" -> simulation.settings(new InfantrySimulation.Settings(number(a[0]),a[1].equals("1"),a[2].equals("1"),Integer.parseInt(a[3]),a[4].equals("1"),number(a[5]),Integer.parseInt(a[6]),a[7].equals("1"),Integer.parseInt(a[8])));
                case "spawn" -> {p().spawn(vector(a,0),h.monotonic,1,0x123456);burden=false;}
                case "god" -> p().god(argument.equals("1"));
                case "live" -> {if(argument.equals("1"))h.loading.remove(0);else h.loading.add(0);}
                case "retail" -> p().motion().flightCapability(argument.equals("-")?null:argument.equals("1"));
                case "burden" -> burden=argument.equals("1");
                case "impulse" -> simulation.queueVelocityImpulse(p(),Integer.parseInt(a[0]),vector(a,1));
                case "ownblast" -> p().motion().noteOwnBlast(h.monotonic,Integer.parseInt(argument));
                case "peer" -> {int id=Integer.parseInt(a[0]);h.admit(id,Integer.parseInt(a[1]));h.runtime.player(id).body().position(vector(a,2));h.loading.add(id);}
                case "pending" -> pending=Integer.parseInt(argument);
                case "cell" -> h.world.set(new MutableWorld.Cell(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])),a[3].equals("1"),0x123456,null);
                default -> throw new AssertionError(op);
            }
            String wire=h.wire.stream().filter(p->p.id()==5||p.id()==46).map(p->lanes.get(p)+":"+HexFormat.of().formatHex(p.encode())).collect(Collectors.joining(";"));
            return new String[]{snapshot(p()),events.isEmpty()?"-":String.join(";",events),wire.isEmpty()?"-":wire};
        }
        @Override public void close(){h.close();}
    }
    @TestFactory Stream<DynamicTest> originalOwnedInfantrySimulation()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/infantry-simulation.tsv.gz")))){new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));}
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original infantry "+e.getKey(),()->{
            try(var replay=new Replay(Integer.parseInt(e.getValue().getFirst()[1]))){int index=0;
                for(var row:e.getValue()){
                    var values=replay.operation(row[2],row[3]);String context="op "+index+++" "+row[2]+" "+row[3];
                    assertAll(context,()->assertEquals(row[4],values[0],"body/façade/chronology"),()->assertEquals(row[5],values[1],"observer boundaries"),()->assertEquals(row[6],values[2],"HP/death wire"));
                }
            }
        }));
    }
}
