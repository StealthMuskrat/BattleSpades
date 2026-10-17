// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.combat.CombatGeometry.*;

/** Source nearest-hit selection using current terrain and scoped read-only player rewind. No damage is committed here. */
public final class HitTrace {
    public interface Entity {
        int id();boolean alive();boolean takesDamage();double hitRadius();Vector hitCenter();
    }
    public interface Corpse {Vector position();Vector orientation();}
    /** Concrete domain registries and enemy policy are required; no empty production registries are invented. */
    public interface Scene {
        Cell raycast(Vector origin,Vector direction,double maximum);
        Iterable<PlayerLife> players();boolean enemies(PlayerLife attacker,PlayerLife target);
        Iterable<? extends Entity> entities();Iterable<? extends Corpse> corpses();
    }
    public sealed interface Hit permits PlayerHit,EntityHit,CorpseHit,BlockHit {Vector position();}
    public record PlayerHit(PlayerLife player,boolean headshot,Vector position,int part) implements Hit {}
    public record EntityHit(Entity entity,Vector position) implements Hit {}
    public record CorpseHit(Corpse corpse,Vector position) implements Hit {}
    public record BlockHit(Cell cell,Vector position) implements Hit {}
    private record Candidate<T>(T target,Entry entry) {}
    private final Thread owner=Thread.currentThread();private final Scene scene;private final LagCompensation.ShotOwner rewind;
    public HitTrace(Scene scene,LagCompensation.ShotOwner rewind){this.scene=Objects.requireNonNull(scene);this.rewind=Objects.requireNonNull(rewind);}
    public Hit trace(PlayerLife attacker,Vector origin,Vector direction,double maximum){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Hit trace outside owner");
        var block=scene.raycast(origin,direction,maximum);var center=block==null?null:new Vector(block.x()+.5,block.y()+.5,block.z()+.5);double limit=center==null?maximum:Math.min(maximum,CombatGeometry.distance(origin,center));
        var playerChoice=firstPlayer(attacker,origin,direction,limit);PlayerLife player=playerChoice==null?null:playerChoice.player;BodyHit playerBody=playerChoice==null?null:playerChoice.body;double nearest=playerBody==null?limit+1:playerBody.entry().distance();
        Candidate<Entity> entity=null;
        for(var target:scene.entities()){
            double radius=target.hitRadius();if(!target.alive()||!target.takesDamage()||radius<=0)continue;
            var hit=CombatGeometry.sphere(origin,direction,limit,target.hitCenter(),radius);if(hit!=null&&(entity==null||hit.distance()<entity.entry.distance()))entity=new Candidate<>(target,hit);
        }
        Candidate<Corpse> corpse=null;
        for(var target:scene.corpses()){
            var hit=CombatGeometry.corpse(origin,direction,limit,target.position(),target.orientation());if(hit!=null&&(corpse==null||hit.distance()<corpse.entry.distance()))corpse=new Candidate<>(target,hit);
        }
        if(player!=null&&(entity==null||nearest<=entity.entry.distance())&&(corpse==null||nearest<=corpse.entry.distance()))return new PlayerHit(player,playerBody.headshot(),playerBody.entry().position(),playerBody.part());
        if(corpse!=null&&(entity==null||corpse.entry.distance()<=entity.entry.distance()))return new CorpseHit(corpse.target,corpse.entry.position());
        if(entity!=null)return new EntityHit(entity.target,entity.entry.position());
        return block==null?null:new BlockHit(block,center);
    }
    private record PlayerChoice(PlayerLife player,BodyHit body) {}
    private PlayerChoice firstPlayer(PlayerLife attacker,Vector origin,Vector direction,double limit){
        PlayerLife player=null;BodyHit playerBody=null;double nearest=limit+1;
        for(var target:scene.players()){
            if(target==attacker||!target.alive()||!target.spawned()||!scene.enemies(attacker,target))continue;
            var hit=CombatGeometry.body(origin,direction,limit,target.selection().classId(),rewind.body(target.lagTarget()));
            if(hit!=null&&hit.entry().distance()<nearest){player=target;playerBody=hit;nearest=hit.entry().distance();}
        }
        return player==null?null:new PlayerChoice(player,playerBody);
    }
    /** Source melee path considers players and intervening terrain, without entity/corpse hit competition. */
    public PlayerHit playerRay(PlayerLife attacker,Vector origin,Vector direction,double maximum){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Hit trace outside owner");
        var block=scene.raycast(origin,direction,maximum);double limit=block==null?maximum:Math.min(maximum,CombatGeometry.distance(origin,new Vector(block.x()+.5,block.y()+.5,block.z()+.5)));
        var choice=firstPlayer(attacker,origin,direction,limit);return choice==null?null:new PlayerHit(choice.player,choice.body.headshot(),choice.body.entry().position(),choice.body.part());
    }
    /** Actual WorldManager.raycast: accurate native-equivalent query, then source 0.1-step fallback. */
    public static Cell terrainRay(CollisionMap map,Vector origin,Vector direction,double maximum){
        Objects.requireNonNull(map);var hit=WorldQueries.ray(map,origin,direction,maximum,false);
        if(hit.isPresent()){var c=hit.get().cube();return new Cell(c.x(),c.y(),c.z());}
        int steps=(int)(maximum/.1);
        for(int i=0;i<steps;i++){
            int x=(int)(origin.x()+direction.x()*.1*i),y=(int)(origin.y()+direction.y()*.1*i),z=(int)(origin.z()+direction.z()*.1*i);
            if(!(x>=0&&x<512&&y>=0&&y<512&&z>=0&&z<240))return null;if(map.solid(x,y,z))return new Cell(x,y,z);
        }
        return null;
    }
}
