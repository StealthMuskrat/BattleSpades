// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class ObjectiveDropGoldenTest {
    static String state(PlayerLife p){return String.join(",",ObjectiveCarryGoldenTest.optional(p.pickupId()),p.pickupBurdensome()?"1":"0",ObjectiveCarryGoldenTest.optional(p.pickupState()),p.body().burdened()?"1":"0",p.alive()?"1":"0",p.spawned()?"1":"0");}
    static String vec(Vector p){return ObjectiveCarryGoldenTest.bits(p.x())+","+ObjectiveCarryGoldenTest.bits(p.y())+","+ObjectiveCarryGoldenTest.bits(p.z());}
    static String entity(MapEntity e){return String.join(";",""+e.id(),""+e.type,""+e.state,""+e.playerId,e.kind,vec(new Vector(e.x,e.y,e.z)),vec(e.velocity),ObjectiveCarryGoldenTest.bits(e.radius),e.alive?"1":"0",e.wireVisible?"1":"0",""+e.face,e.behavior==null?"1":"0",vec(e.home));}
    static IntStream traces(){return IntStream.range(0,16);}
    @ParameterizedTest @MethodSource("traces") void originalRegisteredAsyncHandlerAndRegistryFallback(int trace)throws Exception {
        List<String[]> rows;try(var in=new GZIPInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/golden/objective-drop.tsv.gz")))){rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var registry=new EntityRegistry();var wire=new ArrayList<String>();var events=new ArrayList<String>();boolean[] fault={false};int[] loop={0};
            java.util.function.Consumer<GamePacket> send=packet->{wire.add(HexFormat.of().formatHex(packet.encode()));if(fault[0])throw new IllegalStateException("send-fault");};
            for(var r:rows){
                if(Integer.parseInt(r[0])!=trace)continue;h.runtime.respawn(0);registry.clear();wire.clear();events.clear();fault[0]=false;
                int scenario=Integer.parseInt(r[2]),guard=Integer.parseInt(r[4]),type=Integer.parseInt(r[5]);boolean deferred=r[3].equals("1");
                ObjectiveCarry.pickup(p,type,trace%2==1,Integer.parseInt(r[6]),send);wire.clear();loop[0]=Integer.parseInt(r[7]);
                if(guard==1||guard==2)p.beginDeath(h.wall);else if(guard==3)p.carry(null,p.pickupBurdensome(),p.pickupState());
                var packet=new DropPickup(-12345,254,Integer.parseInt(r[8]),ObjectiveCarryGoldenTest.vector(r[9]),ObjectiveCarryGoldenTest.vector(r[10]));var gate=new CompletableFuture<Void>();
                ObjectiveDropRequests.Mode mode=(player,pos,vel)->{
                    assertSame(p,player);assertEquals(packet.position(),pos);assertEquals(packet.velocity(),vel);events.add("begin");
                    if(scenario==10)throw new IllegalStateException("hook-fault");
                    return (deferred?gate:CompletableFuture.<Void>completedFuture(null)).thenApply(ignored->{
                        events.add("resume");if(scenario==11)throw new IllegalStateException("hook-fault");
                        if(scenario==5||scenario==6)ObjectiveCarry.drop(p,new DropPickup.Position(36,36,97),new DropPickup.Position(0,0,0),loop[0],send);
                        return scenario==3||scenario==4||scenario==5?Boolean.TRUE:scenario==2?null:Boolean.FALSE;
                    });
                };
                var service=new ObjectiveDropRequests(()->scenario==0?Optional.empty():Optional.of(mode),registry,()->loop[0],send,command->{throw new AssertionError("Already on owner");});fault[0]=scenario==12;
                var result=service.handle(p,packet).toCompletableFuture();boolean pending=!result.isDone();String where="trace "+trace+" case "+r[1];assertEquals(r[11].equals("1"),pending,where+" suspension");
                assertEquals(r[12],state(p)+";"+registry.all().size()+";"+String.join("|",wire)+";"+String.join(",",events),where+" before await");
                if(pending){
                    if(scenario==7){ObjectiveCarry.drop(p,new DropPickup.Position(36,36,97),new DropPickup.Position(0,0,0),loop[0],send);ObjectiveCarry.pickup(p,14+(trace+1)%3,true,7,send);}
                    else if(scenario==8)p.body().position(new Vector(100,100,100));
                    else if(scenario==9)p.beginDeath(h.wall);
                    else if(scenario==13){p.carry(p.pickupId(),p.pickupBurdensome(),9);loop[0]+=70000;}
                    else if(scenario==14)h.runtime.respawn(0);
                    gate.complete(null);
                }
                String outcome="ok";try{result.join();}catch(CompletionException error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();outcome=cause.getMessage();}
                assertEquals(r[13],outcome,where+" result");assertEquals(r[14],state(p),where+" state");assertEquals(r[15],wire.isEmpty()?"-":String.join("|",wire),where+" wire");assertEquals(r[16],events.isEmpty()?"-":String.join(",",events),where+" hooks");assertEquals(r[17],registry.all().isEmpty()?"-":String.join("|",registry.all().stream().map(ObjectiveDropGoldenTest::entity).toList()),where+" registry");
            }
        }
    }
    @Test void workerCompletionReturnsToOwnerAndWrongExecutorFailsBeforeMutation()throws Exception {
        try(var h=new LifeHarness()){
            h.admit(0,2);var p=h.runtime.player(0);var registry=new EntityRegistry();var queue=new ConcurrentLinkedQueue<Runnable>();var gate=new CompletableFuture<Boolean>();
            ObjectiveCarry.pickup(p,16,true,3,h.wire::add);h.wire.clear();var service=new ObjectiveDropRequests(()->Optional.of((a,b,c)->gate),registry,()->77,h.wire::add,queue::add);
            var packet=new DropPickup(-1,254,16,new DropPickup.Position(36,36,97),new DropPickup.Position(99,0,0));var result=service.handle(p,packet).toCompletableFuture();
            Thread.ofPlatform().start(()->gate.complete(false)).join();assertFalse(result.isDone());assertEquals(16,p.pickupId());assertTrue(registry.all().isEmpty());assertTrue(h.wire.isEmpty());assertEquals(1,queue.size());queue.remove().run();result.join();assertNull(p.pickupId());assertEquals(1,registry.all().size());assertEquals(77,((DropPickup)h.wire.getFirst()).loop());
            ObjectiveCarry.pickup(p,15,false,2,h.wire::add);h.wire.clear();var badGate=new CompletableFuture<Boolean>();var bad=new ObjectiveDropRequests(()->Optional.of((a,b,c)->badGate),registry,()->78,h.wire::add,Runnable::run);var rejected=bad.handle(p,new DropPickup(-1,254,15,packet.position(),packet.velocity())).toCompletableFuture();Thread.ofPlatform().start(()->badGate.complete(false)).join();assertThrows(CompletionException.class,rejected::join);assertEquals(15,p.pickupId());assertEquals(1,registry.all().size());assertTrue(h.wire.isEmpty());
        }
    }
}
