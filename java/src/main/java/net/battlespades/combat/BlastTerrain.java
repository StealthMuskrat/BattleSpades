// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;

/** Original native footprint delivery, known-causer fallback and exact cube path over the owned health/journal store. */
public final class BlastTerrain {
    public record Recipient(int id,boolean inGame,Set<Integer> knownEntities){public Recipient{knownEntities=Set.copyOf(knownEntities);}}
    public interface Delivery {
        List<Recipient> recipients();
        void reliable(Recipient recipient,GamePacket packet);
        void reliable(GamePacket packet);
    }
    private final Thread owner=Thread.currentThread();private final MutableWorld world;private final TerrainDamage terrain;private final Delivery delivery;
    public BlastTerrain(MutableWorld world,TerrainDamage terrain,Delivery delivery){this.world=Objects.requireNonNull(world);this.terrain=Objects.requireNonNull(terrain);this.delivery=Objects.requireNonNull(delivery);}
    public static DamagePacket packet(PlayerLife player,Vector at,double amount,int type,int seed,Integer causer,int chunk){return new DamagePacket(player==null?255:player.id(),type,(float)Math.min(amount,31.75),0,chunk,seed&255,causer==null?(player==null?0:player.id()):causer,new Vector3f((float)at.x(),(float)at.y(),(float)at.z()));}
    private static Vector point(Cell cell){return new Vector(cell.x(),cell.y(),cell.z());}
    public boolean apply(Vector origin,double amount,PlayerLife thrower,int craterRadius,Integer type,Integer causer){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Blast terrain outside owner");
        if(type!=null&&type!=6&&BlockDamageModel.nativeType(type)){
            var result=terrain.nativeDamage(origin,type,amount,null);if(result.destroyed().isEmpty()&&result.damaged().isEmpty()&&!ExplosionGeometry.STOCK_DAMAGE_TYPES.contains(type))return false;
            var packet=packet(thrower,origin,result.wireAmount(),type,result.seed(),causer,1);
            if(causer==null)delivery.reliable(packet);
            else{
                List<GamePacket> exact=null;
                for(var recipient:delivery.recipients()){
                    if(!recipient.inGame)continue;
                    if(recipient.knownEntities.contains(causer)){delivery.reliable(recipient,packet);continue;}
                    if(exact==null){exact=new ArrayList<>();for(var c:result.destroyed())exact.add(packet(thrower,point(c),31.75,6,0,null,1));for(var hit:result.damaged())exact.add(packet(thrower,point(hit.cell()),hit.damage(),6,0,null,0));}
                    for(var item:exact)delivery.reliable(recipient,item);
                }
            }
            // MutableWorld already journals committed topology and remaining health without retaining expired causers.
            terrain.completeNative(thrower,result.destroyed(),causer!=null);return true;
        }
        int x=(int)Math.floor(origin.x()+.5),y=(int)Math.floor(origin.y()+.5),z=(int)Math.floor(origin.z()+.5),r=Math.max(1,craterRadius);
        for(int ax=x-r;ax<=x+r;ax++)for(int ay=y-r;ay<=y+r;ay++)for(int az=z-r;az<=z+r;az++)if(ax>=0&&ax<512&&ay>=0&&ay<512&&az>=0&&az<240&&world.solid(new MutableWorld.Cell(ax,ay,az)))terrain.gun(thrower,new Cell(ax,ay,az),amount);
        return false;
    }
}
