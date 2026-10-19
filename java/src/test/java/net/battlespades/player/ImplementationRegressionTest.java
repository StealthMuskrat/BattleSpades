// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImplementationRegressionTest {
    private static PlayerLife player(int id,Vector at){var p=new PlayerLife(id,2,0,"P"+id,"tdm",(x,y,z)->false,GameRules.serverDefaults(),new ClassCatalog.Selection(12,List.of(5,12,16,64),List.of(),List.of()),1);p.spawn(at,100,1,0x123456);return p;}
    private static void input(PlayerLife p,int loop,int movement){p.inputTimeline().receive(new LiveClientInput(loop,p.id(),false,64,1,0,0,0,movement,0,0),true,true,p.aim(),loop,0,false);}
    @Test void literalReceiveLayoutsKeepVoxelCoordinatesSeparateFromFixedProjectileAndEraseFields(){
        byte[] placement=new Wire.Writer().u8(88).i32(123).u8(254).i16(511).i16(256).i16(238).i16(0x85a0).toByteArray();var v=LivePlacement.decode(placement);assertEquals(511,v.x());assertEquals(256,v.y());assertEquals(-22.5,v.yaw());byte[] encodedPlacement=placement.clone();encodedPlacement[encodedPlacement.length-2]=(byte)0x9f;assertArrayEquals(encodedPlacement,v.encode());assertThrows(ProtocolException.class,()->LivePlacement.decode(Arrays.copyOf(placement,placement.length-1)));
        byte[] launch=new Wire.Writer().u8(10).i32(9).u8(254).u8(12).i16(160).i16(64).i16(128).i16(192).i16(0x8320).i16(0).i16(4800).toByteArray();var p=(OrientedItemPacket)PacketCodec.decode(launch);assertEquals(new Vector3f(1,2,3),p.position());assertEquals(new Vector3f(-12.5f,0,75),p.velocity());assertEquals(2.5,p.fuse());byte[] encodedLaunch=launch.clone();encodedLaunch[15]=0x1f;assertArrayEquals(encodedLaunch,p.encode());
        byte[] erase=new Wire.Writer().u8(31).i32(4).string("bunker").u8(3).u8(1).u8(2).u8(3).i32(0).i32(68).i16(64).i16(128).i16(192).toByteArray();assertEquals(new Vector3f(1,2,3),((ErasePrefabPacket)PacketCodec.decode(erase)).position());assertArrayEquals(erase,PacketCodec.decode(erase).encode());
    }
    @Test void projectileTimersBounceAndSweptContactsMatchSourceOrdering(){
        var e=new ProjectileEngine();var grenade=e.spawn(11,new Vector(10,10,9.9),new Vector(3,0,12),2.5,0,100);assertTrue(e.update(1.0/60,(x,y,z)->z>=10,100,List.of()).isEmpty());assertEquals(9.9,grenade.position.z());assertEquals(1.08,grenade.velocity.x(),1e-12);assertEquals(-4.5,grenade.velocity.z(),1e-12);
        var cooked=e.spawn(11,new Vector(1,1,1),new Vector(30,0,0),0,0,100);var event=e.update(1.0/60,(x,y,z)->false,100,List.of()).getFirst();assertSame(cooked,event.projectile());assertEquals(ProjectileEngine.Kind.EXPLOSION,event.kind());assertEquals(new Vector(1,1,1),cooked.position);
        e.clear();var target=player(1,new Vector(12,10,9));var rocket=e.spawn(13,new Vector(10,10,10),new Vector(500,0,0),0,0,100);event=e.update(1.0/60,(x,y,z)->x>=14,100,List.of(target)).getFirst();assertSame(rocket,event.projectile());assertEquals(1,rocket.contactPlayerId);assertEquals(11.05,rocket.position.x(),1e-9);assertNull(rocket.contactBlock);assertTrue(e.projectiles().isEmpty());
        var mine=e.spawn(58,new Vector(10,10,10),new Vector(500,0,0),0,0,100);event=e.update(1.0/60,(x,y,z)->x>=11,100,List.of(target)).getFirst();assertEquals(ProjectileEngine.Kind.DEPLOY,event.kind());assertEquals(new EntityRegistry.Cell(11,10,10),mine.contactBlock);assertNull(mine.contactPlayerId);
    }
    @Test void stickyCarrierDeathFreezesItAndTimerWinsBeforeCarrierMovement(){
        var engine=new ProjectileEngine();var target=player(1,new Vector(12,10,9));var sticky=engine.spawn(57,new Vector(10,10,10),new Vector(100,0,0),0,0,100);var events=engine.update(1.0/60,(x,y,z)->false,100,List.of(target));assertEquals(ProjectileEngine.Kind.ATTACH,events.getFirst().kind());assertEquals(1,sticky.attachedPlayerId);assertEquals(105,sticky.explodeAt);engine.update(1.0/60,(x,y,z)->false,101,List.of(target));assertEquals(new Vector(12,10,10),sticky.position);
        target.beginDeath(1000);events=engine.update(1.0/60,(x,y,z)->false,102,List.of(target));assertEquals(ProjectileEngine.Kind.DETACH,events.getFirst().kind());assertNull(sticky.attachedPlayerId);target.spawn(new Vector(400,400,50),103,1,0);engine.update(1.0/60,(x,y,z)->false,104,List.of(target));assertEquals(new Vector(12,10,10),sticky.position);assertEquals(ProjectileEngine.Kind.EXPLOSION,engine.update(1.0/60,(x,y,z)->false,105,List.of(target)).getFirst().kind());assertTrue(engine.projectiles().isEmpty());
    }
    @Test void mutationQueueHonorsPhysicsBudgetTimeoutAndOriginalLifeReservations(){
        var owner=player(0,new Vector(1,1,1));var queue=new WorldMutations(new WorldMutations.Settings(2,1,2,3));var applied=new AtomicInteger();var cancelled=new AtomicInteger();
        assertTrue(queue.enqueue(new WorldMutations.Mutation(owner,owner.generation(),10,0,1,applied::incrementAndGet,cancelled::incrementAndGet)));assertTrue(queue.enqueue(new WorldMutations.Mutation(owner,owner.generation(),11,0,1,applied::incrementAndGet,cancelled::incrementAndGet)));assertFalse(queue.enqueue(new WorldMutations.Mutation(owner,owner.generation(),12,0,1,applied::incrementAndGet,cancelled::incrementAndGet)));assertEquals(0,queue.commit(1,List.of(owner)));
        input(owner,10,0);owner.inputTimeline().consumeOne(8,owner.aim());assertEquals(1,queue.commit(2,List.of(owner)));assertEquals(1,applied.get());assertEquals(0,queue.commit(3,List.of(owner)));assertEquals(2,cancelled.get());assertEquals(1,queue.metrics().committed());assertEquals(1,queue.metrics().expired());
        assertTrue(queue.enqueue(new WorldMutations.Mutation(owner,owner.generation(),10,4,1,applied::incrementAndGet,cancelled::incrementAndGet)));owner.beginDeath(1000);owner.spawn(new Vector(1,1,1),104,1,0);assertEquals(0,queue.commit(4,List.of(owner)));assertEquals(3,cancelled.get());
    }
    @Test void disguiseGuardsOnlyEarlierInputAndCountersSurviveRespawnWithoutLogSpam(){
        var p=player(0,new Vector(1,1,1));input(p,10,1);input(p,13,0);assertTrue(p.disguise().activate(p,13,100));assertEquals(1,p.inventory().disguise());p.inputTimeline().consumeOne(8,p.aim());p.motion().controls(1,0);p.body().position(new Vector(2,1,1));p.disguise().moved(p,100.1);assertTrue(p.disguise().active());p.disguise().moved(p,101.5);assertFalse(p.disguise().active());assertTrue(p.disguise().activate(p,null,102));p.motion().controls(32,0);p.disguise().moved(p,102.1);assertTrue(p.disguise().active());p.body().position(new Vector(2.51,1,1));p.disguise().moved(p,102.2);assertFalse(p.disguise().active());assertFalse(p.disguise().activate(p,null,103));
        assertNotNull(p.anticheat().report("launch_rejected",true,Map.of(),100));assertNull(p.anticheat().report("launch_rejected",true,Map.of(),101));assertNotNull(p.anticheat().report("launch_rejected",false,Map.of(),101));p.beginDeath(1000);p.spawn(new Vector(1,1,1),104,1,0);assertEquals(Map.of("launch_rejected",2L,"launch_rejected:observed",1L),p.anticheat().counts());assertNotNull(p.anticheat().report("launch_rejected",true,Map.of(),110));assertEquals(2,p.inventory().disguise());
    }
    @Test void prefabPivotRoundingRotationOrderAndBlendingMatchNativeHelpers(){
        var model=new Kv6(1,1,1,.5f,-.5f,.5f,List.of(new Kv6.Voxel(0,0,0,10,20,30,255,0,0)),List.of(1L),List.of(1),new byte[0],false);var block=PrefabModels.expand(model,new BlockDamageModel.Cell(10,20,30),1,1,1,0x646e78).getFirst();assertEquals(new BlockDamageModel.Cell(9,19,29),block.cell());assertEquals(0x37414b,block.color());
    }
}
