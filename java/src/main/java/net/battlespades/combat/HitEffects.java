// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.combat.CombatGeometry.Cell;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;

/** Original admitted hitscan resolution. Caller owns complete shot authorization, cadence and rewind scope. */
public final class HitEffects {
    public interface Domain {
        boolean hardcore(PlayerLife victim);
        void reject(PlayerLife attacker,String reason);
        void shieldImpact(PlayerLife victim,Vector position,boolean melee);
        /** Original profile hit and shot tally, only after actual health decreases. */
        void playerHit(PlayerLife attacker,PlayerLife victim,boolean headshot);
        void reliable(GamePacket packet);void unreliable(GamePacket packet);
        void damageEntity(HitTrace.Entity entity,double amount,PlayerLife attacker);
        boolean explodeCorpse(HitTrace.Corpse corpse,PlayerLife attacker,boolean showExplosion);
        boolean damageBlock(PlayerLife attacker,Cell cell,double amount);
    }
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final HitTrace trace;private final Domain domain;
    public HitEffects(LifeRuntime lives,HitTrace trace,Domain domain){this.lives=Objects.requireNonNull(lives);this.trace=Objects.requireNonNull(trace);this.domain=Objects.requireNonNull(domain);}
    private static Vector3f wire(Vector v){return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());}
    public boolean melee(PlayerLife attacker,Vector origin,Vector direction,List<Vector> reachEyes,double reachSlack){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Hit effects outside owner");
        var hit=trace.playerRay(attacker,origin==null?attacker.position():origin,direction==null?attacker.aim():direction,DirectDamage.MELEE_RANGE);if(hit==null)return false;
        if(reachEyes!=null&&reachEyes.stream().noneMatch(eye->CombatGeometry.distance(eye,hit.position())<=DirectDamage.MELEE_RANGE+1.5+reachSlack)){domain.reject(attacker,"melee_out_of_reach");return false;}
        var profile=attacker.fire().profile();var target=hit.player();double amount=DirectDamage.amount(profile,false,null,DirectDamage.victim(target,domain.hardcore(target)));
        amount=DirectDamage.mitigate(DirectDamage.shield(target),attacker.position(),amount,hit.position(),true,e->domain.shieldImpact(target,e.position(),e.melee()));
        if(DirectDamage.riotShield(attacker.inventory().tool())){var velocity=PlayerMotion.clipped(target.body().state().velocity());var next=DirectDamage.shieldKnockback(attacker.position(),attacker.aim(),target.position(),velocity);if(next!=velocity)target.body().velocity(next);}
        int before=target.health();lives.damage(target.id(),amount,attacker.id(),profile.killType());
        if(target.health()<before){domain.playerHit(attacker,target,hit.headshot());domain.unreliable(new CombatPackets.ShootResponse(attacker.id(),1,1,wire(hit.position())));}return true;
    }
    public boolean hitscan(PlayerLife attacker,Vector direction,Vector origin){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Hit effects outside owner");
        var profile=attacker.fire().profile();var hit=trace.trace(attacker,origin==null?attacker.position():origin,direction,profile.range());
        return switch(hit){
            case null -> false;
            case HitTrace.PlayerHit h -> {
                var target=h.player();double amount=DirectDamage.amount(profile,h.headshot(),h.part(),DirectDamage.victim(target,domain.hardcore(target)));
                amount=DirectDamage.mitigate(DirectDamage.shield(target),attacker.position(),amount,h.position(),false,e->domain.shieldImpact(target,e.position(),e.melee()));
                int killType=h.headshot()?DirectDamage.HEADSHOT_KILL:profile.killType(),before=target.health();lives.damage(target.id(),amount,attacker.id(),killType);
                if(target.health()<before){domain.playerHit(attacker,target,h.headshot());domain.unreliable(new CombatPackets.ShootResponse(attacker.id(),1,1,wire(h.position())));}yield true;
            }
            case HitTrace.EntityHit h -> {domain.reliable(new CombatPackets.HitEntity(h.entity().id(),wire(h.position()),DirectDamage.ENTITY_PART));domain.damageEntity(h.entity(),DirectDamage.entityAmount(profile),attacker);yield true;}
            case HitTrace.CorpseHit h -> domain.explodeCorpse(h.corpse(),attacker,true);
            case HitTrace.BlockHit h -> domain.damageBlock(attacker,h.cell(),profile.blockDamage());
        };
    }
}
