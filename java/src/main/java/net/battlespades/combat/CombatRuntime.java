// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;

/** Source owned packet-6 commit sequence. Concrete mode, objective, scoring, presentation and terrain/entity domains are required. */
public final class CombatRuntime {
    public interface Timing {int loop();double monotonic();double tickSeconds();}
    public interface Domain extends HitEffects.Domain {
        boolean allowsShoot(PlayerLife player);boolean burdensomePickup(PlayerLife player);boolean shootWithIntel();boolean mountedGun(PlayerLife player);
        boolean enemies(PlayerLife a,PlayerLife b);Iterable<? extends HitTrace.Entity> entities();Iterable<? extends HitTrace.Corpse> corpses();
        void clearDisguise(PlayerLife player);void weaponFired(PlayerLife player,int tool);void profileShot(PlayerLife player);void recordShot(PlayerLife player);
        void observeShot(PlayerLife player,ShootPacket packet,double now);void resolved(ShotTally.Resolved result);
        void validationReport(PlayerLife player,ShotValidation.Event report);void rewindReport(PlayerLife player,LagCompensation.Diagnostic report);
        void feedback(SimplePackets.ShootFeedbackPacket packet,int excludedOwner);
        void shotStimulus(Vector position,int sourceId,int team,double radius,double lifetime);
        boolean dig(PlayerLife player,Vector origin,Vector direction,ShootPacket packet,List<Vector> eyes,double slack);
        /** Log-only diagnostic ports; failures cannot alter combat. */
        void dropped(PlayerLife player,ShootPacket packet,String reason);void cadenceDropped(PlayerLife player,ShootPacket packet,double now);
        void nearMiss(PlayerLife player,ShootPacket packet,Vector origin,Vector direction,CombatCatalog.Profile profile);
    }
    public record Settings(ShotValidation.Settings validation,LagCompensation.Settings rewind){public Settings{Objects.requireNonNull(validation);Objects.requireNonNull(rewind);}}
    private final Thread owner=Thread.currentThread();private final Timing timing;private final Domain domain;private final CollisionMap map;
    private final FiringPatterns patterns=new FiringPatterns();private final PelletSpread spread=new PelletSpread();private final ShotTally tally=new ShotTally();private final LagCompensation.ShotOwner rewind=new LagCompensation.ShotOwner();private final HitEffects hits;
    private Settings settings;
    public CombatRuntime(LifeRuntime lives,Timing timing,Settings settings,Domain domain){
        Objects.requireNonNull(lives);this.timing=Objects.requireNonNull(timing);this.settings=Objects.requireNonNull(settings);this.domain=Objects.requireNonNull(domain);map=CollisionMap.of(lives.world());
        var trace=new HitTrace(new HitTrace.Scene(){
            public CombatGeometry.Cell raycast(Vector origin,Vector direction,double maximum){return HitTrace.terrainRay(map,origin,direction,maximum);}
            public Iterable<PlayerLife> players(){return lives.players();}public boolean enemies(PlayerLife a,PlayerLife b){return domain.enemies(a,b);}
            public Iterable<? extends HitTrace.Entity> entities(){return domain.entities();}public Iterable<? extends HitTrace.Corpse> corpses(){return domain.corpses();}
        },rewind);
        hits=new HitEffects(lives,trace,new HitEffects.Domain(){
            public boolean hardcore(PlayerLife p){return domain.hardcore(p);}public void reject(PlayerLife p,String reason){p.shotStatistics().reject(p,reason,Map.of(),e->domain.validationReport(p,e));domain.reject(p,reason);}
            public void shieldImpact(PlayerLife p,Vector at,boolean melee){domain.shieldImpact(p,at,melee);}
            public void playerHit(PlayerLife a,PlayerLife v,boolean head){domain.playerHit(a,v,head);tally.notePlayerHit(a,head);}
            public void reliable(GamePacket p){domain.reliable(p);}public void unreliable(GamePacket p){domain.unreliable(p);}
            public void damageEntity(HitTrace.Entity e,double amount,PlayerLife a){domain.damageEntity(e,amount,a);}public boolean explodeCorpse(HitTrace.Corpse c,PlayerLife a,boolean show){return domain.explodeCorpse(c,a,show);}public boolean damageBlock(PlayerLife a,CombatGeometry.Cell c,double amount){return domain.damageBlock(a,c,amount);}
        });
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Combat runtime outside owner");}
    public Integer activeShotTool(PlayerLife player){owned();return tally.active(player)?player.inventory().tool():null;}
    public void settings(Settings value){owned();settings=Objects.requireNonNull(value);}
    public void forgetPlayer(int id){owned();patterns.forgetPlayer(id);spread.forgetPlayer(id);}
    private void dropped(PlayerLife player,ShootPacket packet,String reason){try{domain.dropped(player,packet,reason);}catch(RuntimeException failure){/* Source diagnostic failures cannot interrupt combat. */}}
    private boolean cadence(PlayerLife player,ShootPacket packet,double now){
        if(player.inventory().tool()==15&&!domain.mountedGun(player)){player.shotStatistics().reject(player,"mg_fire_unmounted",Map.of(),e->domain.validationReport(player,e));dropped(player,packet,"mg_fire_unmounted");return false;}
        boolean accepted=switch(player.inventory().tool()){
            case 60->patterns.assault(player,packet.loopCount(),now);
            case 8->patterns.minigun(player,now,packet.loopCount());
            case 15->player.fire().consumeShot(now,DirectDamage.MOUNTED_GUN_INTERVAL,packet.loopCount());
            default->player.fire().consumeShot(now,null,packet.loopCount());
        };
        if(!accepted)try{domain.cadenceDropped(player,packet,now);}catch(RuntimeException failure){/* Source shot-drop logging is isolated. */}return accepted;
    }
    public boolean shot(PlayerLife player,ShootPacket packet){
        owned();if(!domain.allowsShoot(player)&&!player.fire().spade())return false;
        if(!player.alive()||!player.spawned()){dropped(player,packet,"not_spawned");return false;}
        if(!player.toolEnabled(player.inventory().tool())){dropped(player,packet,"tool_disabled_by_rules");return false;}
        if(domain.burdensomePickup(player)&&!domain.shootWithIntel()){dropped(player,packet,"carrying_objective");return false;}
        if(!player.fire().weapon()&&!player.fire().spade()){dropped(player,packet,"held_tool_not_a_weapon");return false;}
        var validated=ShotValidation.validate(settings.validation,ShotValidation.view(player),ShotValidation.Claims.from(packet),map::solid,player.shotStatistics(),e->domain.validationReport(player,e));
        if(!validated.allowed()){dropped(player,packet,validated.reason());return false;}
        var reach=validated.accepted();var profile=player.fire().profile();double now=timing.monotonic();if(!cadence(player,packet,now))return false;
        domain.clearDisguise(player);domain.weaponFired(player,player.inventory().tool());domain.profileShot(player);domain.recordShot(player);tally.begin(player);try{domain.observeShot(player,packet,now);}catch(RuntimeException failure){/* Source statistical aim observer never interrupts combat. */}
        if(!player.fire().spade())player.endProtection(now);
        if(!player.fire().spade())domain.feedback(new SimplePackets.ShootFeedbackPacket(timing.loop(),player.id(),player.inventory().tool(),packet.snapshotLoop(),packet.seed()&255),player.id());
        domain.shotStimulus(reach.origin(),player.id(),player.team(),72,1.25);
        var context=LagCompensation.prepare(settings.rewind,timing.loop(),timing.tickSeconds(),player,packet,e->domain.rewindReport(player,e));
        try(var scope=rewind.install(context)){
            // Install also shadows an outer context when rewind is disabled, as source compensated() does.
            if(scope.context()!=context)throw new IllegalStateException("Shot context was not installed");
            try{
                if(player.fire().spade()){boolean dug=domain.dig(player,reach.origin(),reach.direction(),packet,reach.eyes(),reach.slack());boolean hit=hits.melee(player,reach.origin(),reach.direction(),reach.eyes(),reach.slack());return dug||hit;}
                tally.observeSeed(player,packet.seed(),e->domain.validationReport(player,e));boolean any=false;
                for(var pellet:spread.expand(player,reach.direction(),profile,packet.loopCount(),packet.seed(),now))if(hits.hitscan(player,pellet,reach.origin()))any=true;
                if(!tally.hadHit(player))try{domain.nearMiss(player,packet,reach.origin(),reach.direction(),profile);}catch(RuntimeException failure){/* Source near-miss logging is isolated. */}return any;
            }finally{tally.end(player,result->{try{domain.resolved(result);}catch(RuntimeException failure){/* Source achievement hooks isolate service faults. */}});}
        }
    }
}
