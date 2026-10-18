// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.CreateEntity;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class EntityRegistryGoldenTest {
    private static final EntityRegistry.Cell A=new EntityRegistry.Cell(1,2,3),B=new EntityRegistry.Cell(2,3,4),STALE=new EntityRegistry.Cell(9,9,9);
    private static String bits(double value){return String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits(value));}
    private static List<String[]> rows()throws IOException{try(var input=new GZIPInputStream(Objects.requireNonNull(EntityRegistryGoldenTest.class.getResourceAsStream("/golden/entity-registry.tsv.gz")))){return new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}}
    static IntStream traces(){return IntStream.range(0,48);}
    private static final class Driver {
        final EntityRegistry registry=new EntityRegistry();final Map<Integer,MapEntity> saved=new HashMap<>();final List<String> events=new ArrayList<>(),wire=new ArrayList<>();
        final PlayerLife player;EntityContext context;boolean fault;
        final EntityVisibility visibility=new EntityVisibility(packet->{if(fault)throw new IllegalStateException("Controlled send fault");wire.add(HexFormat.of().formatHex(packet.encode()));});
        Driver(PlayerLife player){this.player=player;player.body().position(new Vector(0,0,0));}
        void mutate(MapEntity e,int action){var other=registry.get(e.id()+1);switch(action){
            case 1->{if(other!=null)registry.remove(other.id());}
            case 2->{if(other!=null){other.alive=false;other.respawnAt=context.now();}}
            case 3->{if(other!=null)other.behavior=new EntityBehavior();}
            case 4->{var p=new EntityRegistry.Placement();p.behavior=new Observed(0);registry.place(4,e.x,e.y,e.z,p);}
            case 5->registry.clear();case 6->{if(other!=null)other.x+=16;}
            case 7->{e.alive=false;e.respawnAt=context.now();}
            case 8->{var at=player.position();player.body().position(new Vector(at.x()+8,at.y(),at.z()));}
            default->{ }
        }}
        class Observed extends EntityBehavior {
            final int action;boolean fired;
            Observed(int action){this.action=action;touchRadius=2.5;}
            @Override public boolean onTouch(MapEntity e,PlayerLife p,EntityContext c){events.add("U:"+e.id());if(!fired){fired=true;mutate(e,action);}return true;}
            @Override public void onSupportLost(MapEntity e,EntityContext c){events.add("S:"+e.id()+":"+(e.supportCell==null?1:0));events.add("N:"+registry.supportRemoved(A,c));super.onSupportLost(e,c);}
        }
        class Ticker extends Observed {
            Ticker(int action){super(action);}
            @Override public void onTick(MapEntity e,double dt,EntityContext c){events.add("T:"+e.id());if(!fired){fired=true;mutate(e,action);}}
        }
        final class Inherited extends Ticker {Inherited(int action){super(action);}}
        final class Respawner extends Observed implements EntityBehavior.Respawning {
            Respawner(int action){super(action);}
            @Override public void onRespawn(MapEntity e,EntityContext c){events.add("R:"+e.id()+":"+(e.alive?1:0)+":"+bits(e.respawnAt));e.z+=.25;}
        }
        EntityBehavior behavior(int code,int action){return switch(code){case 0->null;case 1->new EntityBehavior();case 2->new Observed(action);case 3->new Ticker(action);case 4->new Inherited(action);case 5->new Respawner(action);case 6->new DamageableEntityBehavior(5);default->throw new AssertionError(code);};}
        String state(){var parts=new ArrayList<String>();for(var e:registry.all()){var b=e.behavior;int code=b==null?0:b instanceof DamageableEntityBehavior?6:b instanceof Inherited?4:b instanceof Ticker?3:b instanceof Respawner?5:b instanceof Observed?2:1;parts.add(e.id()+","+(e.alive?1:0)+","+bits(e.respawnAt)+","+bits(e.x)+","+bits(e.y)+","+bits(e.z)+","+code+","+(code==6?bits(((DamageableEntityBehavior)b).health()):"-")+","+(e.supportCell==null?"-":e.supportCell.x()+":"+e.supportCell.y()+":"+e.supportCell.z())+","+(e.wireVisible?1:0)+","+e.kind);}return joined(parts,"|");}
        String extras(){String flags="";for(var c:List.of(A,B,STALE))flags+=registry.hasSupportAt(c)?"1":"0";var known=saved.keySet().stream().filter(visibility::knows).sorted().map(Object::toString).toList();return flags+";"+joined(known,",")+";"+joined(registry.dueRespawns(context.now()).stream().map(e->Integer.toString(e.id())).toList(),",");}
        String run(String[] r){int step=Integer.parseInt(r[1]),op=Integer.parseInt(r[2]);String[] a=r[3].split(",");context=new EntityContext(1./60,100.+step*.25,List.of(player),registry,e->events.add("C:"+e.id()),id->events.add("D:"+id));events.clear();wire.clear();String result="-";
            switch(op){
                case 0,1,2,3,4,5,6,7,8,9->{var p=new EntityRegistry.Placement();p.behavior=behavior(Integer.parseInt(a[0]),Integer.parseInt(a[1]));p.supportCell=new EntityRegistry.Cell(Integer.parseInt(a[5]),Integer.parseInt(a[6]),Integer.parseInt(a[7]));p.wireVisible=a[8].equals("1");p.kind=a[9];var e=registry.place(4,Double.parseDouble(a[2]),Double.parseDouble(a[3]),Double.parseDouble(a[4]),p);saved.put(e.id(),e);result=Integer.toString(e.id());}
                case 10,13->{try{result=Long.toString(registry.tick(context,a[0].equals("none")?null:Integer.valueOf(a[0])));}catch(ArithmeticException zero){result="zero";}}
                case 11->{var e=saved.get(Integer.valueOf(a[0]));e.alive=false;e.respawnAt=context.now();}
                case 12->result=Integer.toString(registry.supportRemoved(new EntityRegistry.Cell(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])),context));
                case 14->saved.get(Integer.valueOf(a[0])).supportCell=STALE;
                case 15->result=registry.remove(Integer.parseInt(a[0]))==null?"0":"1";
                case 16->registry.damageEntity(Integer.parseInt(a[0]),Double.parseDouble(a[1]),null,context);
                case 17->{fault=a[1].equals("1");try{result=visibility.reveal(saved.get(Integer.valueOf(a[0])))?"1":"0";}catch(IllegalStateException send){result="sendfail";}}
                case 18->{player.body().position(new Vector(Double.parseDouble(a[0]),Double.parseDouble(a[1]),0));result=Long.toString(registry.tick(context,2));}
                case 19->{if(step/20==7){registry.clear();result="clear";}else result=joined(registry.staticEntities().stream().map(e->Integer.toString(e.id())).toList(),",");}
                default->throw new AssertionError(op);
            }return result;
        }
    }
    private static String joined(List<String> values,String delimiter){return values.isEmpty()?"-":String.join(delimiter,values);}
    @ParameterizedTest @MethodSource("traces") void unchangedRegistryCallbacksAndSnapshots(int trace)throws Exception{
        try(var h=new LifeHarness()){h.admit(0,2);var d=new Driver(h.runtime.player(0));for(var r:rows()){if(!r[0].equals(Integer.toString(trace)))continue;String where="trace "+trace+" step "+r[1];assertEquals(r[4],d.run(r),where+" result");assertEquals(r[5],joined(d.events,"|"),where+" callbacks");assertEquals(r[6],d.state(),where+" state");assertEquals(r[7],d.extras(),where+" indexes");assertEquals(r[8],joined(d.wire,"|"),where+" bytes");}}
    }
    @Test void nativeFloatFieldsAndWireTail()throws Exception{for(var r:rows()){if(!r[0].equals("wire"))continue;var a=r[2].split(",");var registry=new EntityRegistry();int id=Integer.parseInt(a[0]);for(int i=0;i<id;i++)registry.allocateId();var p=new EntityRegistry.Placement();p.yaw=Double.parseDouble(a[5]);p.state=Integer.parseInt(a[6]);if(!a[7].equals("-")){var c=a[7].split(":");p.color=new Vector(Double.parseDouble(c[0]),Double.parseDouble(c[1]),Double.parseDouble(c[2]));}p.playerId=Integer.parseInt(a[8]);p.velocity=new Vector(Double.parseDouble(a[9]),Double.parseDouble(a[10]),Double.parseDouble(a[11]));p.radius=Double.parseDouble(a[12]);p.face=Integer.parseInt(a[13]);p.fuse=Double.parseDouble(a[14]);var e=registry.place(Integer.parseInt(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Double.parseDouble(a[4]),p);e.ugcMode=Integer.parseInt(a[15]);e.intProperties=List.of(Integer.valueOf(a[16]),Integer.valueOf(a[17]));assertEquals(r[3],HexFormat.of().formatHex(new CreateEntity(e.toWireEntity()).encode()),"wire case "+r[1]);}}
    @Test void allocatorWrapExhaustionAndDeadReservations(){var registry=new EntityRegistry();for(int i=0;i<65536;i++){var e=registry.place(4,0,0,0);assertEquals(i,e.id());e.alive=false;}assertThrows(IllegalStateException.class,registry::allocateId);assertFalse(registry.remove(32768).alive);assertEquals(32768,registry.allocateId());assertNull(registry.get(null));registry.clear();assertEquals(0,registry.allocateId());}
    @Test void failingDestroyLeavesDeadEntityRegisteredAndFailedCreateRemainsUnknown(){var registry=new EntityRegistry();var e=registry.place(4,0,0,0);var context=new EntityContext(0,0,List.of(),registry,null,id->{assertFalse(e.alive);assertSame(e,registry.get(id));throw new IllegalStateException("Controlled destroy fault");});assertThrows(IllegalStateException.class,()->EntityBehavior.remove(e,context));assertSame(e,registry.get(e.id()));EntityBehavior.remove(e,context);e.alive=true;boolean[] fail={true};var visibility=new EntityVisibility(p->{if(fail[0])throw new IllegalStateException("Controlled send fault");});assertThrows(IllegalStateException.class,()->visibility.reveal(e));assertFalse(visibility.knows(e.id()));fail[0]=false;assertTrue(visibility.reveal(e));assertFalse(visibility.reveal(e));visibility.forget(e.id());assertTrue(visibility.reveal(e));visibility.clear();assertFalse(visibility.knows(e.id()));}
    @Test void sourceDamageClampAndFailedDestroyKnowledge(){
        var registry=new EntityRegistry();var p=new EntityRegistry.Placement();var damage=new DamageableEntityBehavior(Double.NaN);p.behavior=damage;var e=registry.place(4,0,0,0,p);var context=new EntityContext(0,0,List.of(),registry,null,null);registry.damageEntity(e.id(),Double.NaN,null,context);assertTrue(e.alive);registry.damageEntity(e.id(),1,null,context);assertFalse(e.alive);assertNull(registry.get(e.id()));
        e=registry.place(4,0,0,0);boolean[] fail={false};var visibility=new EntityVisibility(packet->{if(fail[0])throw new IllegalStateException("Controlled send fault");});assertFalse(visibility.destroy(e.id()));assertTrue(visibility.reveal(e));fail[0]=true;int id=e.id();assertThrows(IllegalStateException.class,()->visibility.destroy(id));assertTrue(visibility.knows(id));fail[0]=false;assertTrue(visibility.destroy(id));assertFalse(visibility.knows(id));assertFalse(visibility.destroy(id));
    }
    @Test void emptyNegativeBudgetPreservesSourceError(){var registry=new EntityRegistry();assertThrows(ArithmeticException.class,()->registry.tick(new EntityContext(0,0,List.of(),registry,null,null),-1));}
}
