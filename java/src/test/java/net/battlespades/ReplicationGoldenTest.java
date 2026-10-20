// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.protocol.*;
import net.battlespades.replication.*;
import net.battlespades.session.SnapshotHolds;
import net.battlespades.transport.enet.Delivery;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ReplicationGoldenTest {
    private static Integer number(String value){return value.equals("-")?null:Integer.valueOf(value);}
    private static boolean bit(String value){return value.equals("1");}
    private static final class Player implements ReplicationService.Player {
        final Fixture fixture;final int id;String[] args;int ack;
        Player(Fixture fixture,int id){this.fixture=fixture;this.id=id;}
        public ReplicationService.State state(int loop,double now){
            var a=args;boolean alive=bit(a[1]),spawned=bit(a[2]);int tool=Integer.parseInt(a[19]);
            var row=new WorldUpdate.PlayerRow(id,new Vector3f((float)(100.125+id),200.25f,50.5f),new Vector3f(1,0,0),new Vector3f(.125f,-.25f,0),
                0,ack,100,17,Integer.parseInt(a[20]),Integer.parseInt(a[21]),tool,255,12.5,1.5,0);
            return new ReplicationService.State(id,"Pilot Ω:"+id,bit(a[3]),alive&&spawned,number(a[4]),bit(a[5]),a[6].equals("-")?null:bit(a[6]),
                bit(a[7]),number(a[8]),number(a[9]),Double.parseDouble(a[10]),bit(a[11]),bit(a[12]),Integer.parseInt(a[13]),Integer.parseInt(a[14]),
                bit(a[15]),bit(a[16]),bit(a[17]),Integer.parseInt(a[18]),row,alive&&spawned&&(tool==7||tool==15));
        }
        public void ownerRowQueued(int stamp,int loop,boolean transition){fixture.anchors.add(id+","+stamp+","+loop);}
        public void acknowledge(int stamp){ack=stamp;}
    }
    private static final class Link implements WorldUpdateLink {
        final Fixture fixture;final int id;final SnapshotObserver observer=new SnapshotObserver();
        final List<String> packets=new ArrayList<>();Set<SnapshotHolds.Row> held=Set.of();
        int limit=1329;long index;boolean pending,accepted=true,inGame=true;
        Link(Fixture fixture,int id){this.fixture=fixture;this.id=id;}
        public Integer localPlayerId(){return id;}
        public boolean inGame(){return inGame;}
        public SnapshotObserver observerState(){return observer;}
        public boolean snapshot(byte[] body,Delivery delivery,int loop){
            if(!accepted)return false;
            String mode=switch(delivery){case RELIABLE->"R";case UNSEQUENCED->"N";default->"U";};
            packets.add(id+","+mode+","+HexFormat.of().formatHex(body));if(delivery==Delivery.RELIABLE)index++;return true;
        }
        public boolean snapshotUnsequenced(){return true;}
        public Set<SnapshotHolds.Row> heldSnapshotRows(int loop){return held;}
        public int snapshotPayloadLimit(){return limit;}
        public long reliableSendIndex(){return index;}
        public boolean reliableUnackedThrough(long index,int loop){return pending&&index>0;}
        public void flushSnapshots(){fixture.flushes++;}
    }
    @Test void rejectedQueueAdmissionDoesNotFabricateAnOwnerAnchorOrConsumeUrgency(){
        var fixture=new Fixture(0);
        fixture.operation("state","0,1,1,0,100,0,0,0,-,100,40,1,1,0,0,0,0,0,0,7,255,0");
        var link=fixture.links.get(0);link.accepted=false;fixture.service.broadcast(1,0);
        assertTrue(fixture.anchors.isEmpty());assertEquals(0,fixture.flushes);assertEquals(0,fixture.packets);
        link.accepted=true;fixture.service.broadcast(1,0);assertEquals(List.of("0,99,1"),fixture.anchors);assertEquals(1,fixture.flushes);
        assertEquals(1,fixture.packets);fixture.service.broadcast(1,0);assertEquals(1,fixture.packets);
    }
    @Test void liveOwnerTuningPreservesCadenceAndHistoryAndBoundsWireLabels(){
        var f=new Fixture(0);f.operation("state","0,1,1,0,100,0,0,0,-,100,40,0,0,0,0,0,0,0,0,7,255,0");f.service.broadcast(2,0);assertEquals(List.of("0,99,2"),f.anchors);var observer=f.links.get(0).observer.loop();int sent=f.packets;
        f.service.tune(new ReplicationService.OwnerTuning(false,8,1));assertEquals(observer,f.links.get(0).observer.loop());f.service.broadcast(2,0);assertEquals(sent,f.packets,"Tuning must not reset the cadence bucket");f.service.broadcast(4,0);assertEquals(1,f.anchors.size());assertEquals(108,f.players.get(0).ack);
        f.service.tune(new ReplicationService.OwnerTuning(true,8,1));f.service.broadcast(6,0);assertEquals("0,108,6",f.anchors.getLast());assertEquals("0,99,2",f.anchors.getFirst());
        f.service.tune(new ReplicationService.OwnerTuning(true,Integer.MAX_VALUE,0));f.service.broadcast(8,0);assertEquals(Integer.MAX_VALUE,f.players.get(0).ack);assertEquals(1,f.service.tuning().interval());f.service.tune(new ReplicationService.OwnerTuning(true,Integer.MIN_VALUE,1));f.service.broadcast(10,0);assertEquals(0,f.players.get(0).ack);
    }
    private static final class Fixture implements ReplicationService.World {
        final LinkedHashMap<Integer,Player> players=new LinkedHashMap<>();final LinkedHashMap<Integer,Link> links=new LinkedHashMap<>();
        final List<String> anchors=new ArrayList<>();int flushes,packets;long bytes;
        final ReplicationService service;
        Fixture(int group){service=new ReplicationService(this,new ReplicationService.Settings(true,2,6,10,20,group!=15,group%3-1,group%2==0,true));}
        public List<Player> players(){return List.copyOf(players.values());}
        public List<Link> connections(){return List.copyOf(links.values());}
        public List<EntityRecord> entities(){return List.of();}
        public List<WorldUpdate.TurretRow> turrets(){return List.of();}
        public void delivered(int packets,long bytes){this.packets+=packets;this.bytes+=bytes;}
        String operation(String op,String value){
            var a=value.split(",",-1);
            switch(op){
                case "state" -> {int id=Integer.parseInt(a[0]);players.computeIfAbsent(id,key->{links.put(id,new Link(this,id));return new Player(this,id);}).args=a;}
                case "link" -> {var link=links.get(Integer.parseInt(a[0]));link.limit=Integer.parseInt(a[1]);link.pending=bit(a[2]);link.index=Long.parseLong(a[3]);
                    var held=new HashSet<SnapshotHolds.Row>();if(!a[4].equals("-"))for(String id:a[4].split(":"))held.add(new SnapshotHolds.Row(false,Integer.parseInt(id)));link.held=held;}
                case "forget" -> service.forgetPlayer(Integer.parseInt(value));
                case "ingame" -> links.get(Integer.parseInt(a[0])).inGame=bit(a[1]);
                case "broadcast" -> {
                    anchors.clear();flushes=packets=0;bytes=0;links.values().forEach(l->l.packets.clear());int loop=Integer.parseInt(value);service.broadcast(loop,loop/60.0);
                    String sent=links.values().stream().flatMap(l->l.packets.stream()).collect(Collectors.joining(";"));
                    String observers=links.values().stream().map(l->l.id+","+(l.observer.loop()==null?"-":l.observer.loop())+","+(l.observer.sequenced()?1:0)+","+(l.observer.orderedBarrier()==null?"-":l.observer.orderedBarrier())).collect(Collectors.joining(";"));
                    return (sent.isEmpty()?"-":sent)+"|"+(anchors.isEmpty()?"-":String.join(";",anchors))+"|"+flushes+"|"+packets+","+bytes+"|"+observers;
                }
                default -> throw new AssertionError(op);
            }
            return "-";
        }
    }
    @TestFactory Stream<DynamicTest> originalReplicationService()throws IOException {
        var groups=new LinkedHashMap<String,List<String[]>>();
        try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/replication.tsv.gz")))){
            new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).forEach(r->groups.computeIfAbsent(r[0],key->new ArrayList<>()).add(r));
        }
        return groups.entrySet().stream().map(e->DynamicTest.dynamicTest("original replication "+e.getKey(),()->{
            var fixture=new Fixture(Integer.parseInt(e.getKey()));int index=0;
            for(var row:e.getValue())assertEquals(row[3],fixture.operation(row[1],row[2]),"operation "+index+++" "+row[1]+" "+row[2]);
        }));
    }
}
