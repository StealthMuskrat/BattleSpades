// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.util.*;
import java.util.function.*;
import net.battlespades.bot.*;
import net.battlespades.player.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;

/** Copies live authorities on the gameplay thread; no live identity crosses to the worker. */
public final class HostedBotSnapshots {
    private record Seen(PlayerLife player,int generation){}
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final ToIntFunction<PlayerLife> botGeneration;private final Function<PlayerLife,BotMotor> motor;
    private final Map<Integer,Seen> seen=new HashMap<>();private final BotPerception.Entities entities=new BotPerception.Entities();
    public HostedBotSnapshots(StandardMatchDomain match,ToIntFunction<PlayerLife> botGeneration,Function<PlayerLife,BotMotor> motor){this.match=Objects.requireNonNull(match);this.botGeneration=Objects.requireNonNull(botGeneration);this.motor=Objects.requireNonNull(motor);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot snapshots outside gameplay owner");}
    public int generation(PlayerLife p){owned();if(p.bot())return botGeneration.applyAsInt(p);var previous=seen.get(p.id());if(previous==null||previous.player()!=p){previous=new Seen(p,previous==null?1:previous.generation()+1);seen.put(p.id(),previous);}return previous.generation();}
    public int readonlyGeneration(PlayerLife p){owned();if(p.bot())return botGeneration.applyAsInt(p);var previous=seen.get(p.id());return previous!=null&&previous.player()==p?previous.generation():-1;}
    public Player player(PlayerLife p){owned();return player(p,generation(p),true);}
    public Player liveTarget(PlayerLife p){owned();return player(p,readonlyGeneration(p),false);}
    private Player player(PlayerLife p,int generation,boolean waterLease){
        var inv=p.inventory();var physics=p.physicsState().orElse(null);var velocity=physics==null?new Vector(0,0,0):physics.velocity();var runtime=p.bot()?motor.apply(p):null;var feedback=runtime==null?new BotIntentState.Feedback("",true,null,-1,0,0,"",true,0):runtime.state().feedback();double now=match.monotonic();
        var ammo=new ArrayList<Ammo>();for(int tool:p.selection().loadout().subList(0,Math.min(16,p.selection().loadout().size())))if(BotCombatProfiles.FIREARMS.contains(tool)){var wallet=tool==inv.weapon()?inv.ammo():inv.weapons().getOrDefault(tool,new LifeInventory.Ammo(0,0));ammo.add(new Ammo(tool,wallet.clip(),wallet.reserve()));}
        var deploy=p.selection().loadout().stream().distinct().filter(tool->Set.of(15,16,20,21,51,56,59,64).contains(tool)).sorted().map(tool->new Stock(tool,inv.rawDeployableStock(tool))).toList();var oriented=inv.oriented().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Stock(e.getKey(),e.getValue())).toList();
        return new Player(p.id(),generation,p.team(),p.selection().classId(),p.alive(),p.spawned(),p.position(),p.position(),p.aim(),velocity,p.health(),inv.tool(),inv.blocks(),inv.ammo().clip(),inv.ammo().reserve(),p.bot(),inv.weapon(),p.selection().loadout(),p.selection().prefabs(),oriented,p.pickupId()==null?-1:p.pickupId(),p.jetpack(),p.flight().snapshot().fuel(),physics!=null&&!physics.airborne(),physics!=null&&physics.wade()||waterLease&&runtime!=null&&now<runtime.wadeObservedUntil(),inv.reloading(),feedback.kind(),feedback.accepted(),feedback.position(),feedback.frame(),feedback.at(),p.lastCombatHit(),p.lastDamageSource(),p.lastDamagePosition(),Math.toIntExact(p.generation()),deploy,feedback.request(),feedback.reason(),feedback.taskAccepted(),feedback.taskAt(),!p.pickupBurdensome()||match.shootWithIntel(),ammo,p.protectionRemaining(now)>0,p.lastDamageType(),true);
    }
    public long entityOverflow(){owned();return entities.overflow();}
    public List<Entity> entities(){
        owned();var result=new ArrayList<Entity>();double now=match.monotonic();for(var e:match.entities().registry().all()){
            if(!e.alive||e.type==13||Set.of("map_flare","static_flare","flare_block","projectile").contains(e.kind))continue;
            int tool=switch(e.type){case 10->21;case 9->20;case 38->59;case 30->51;case 36->56;case 8->16;case 7->15;default->-1;};int team=switch(e.behavior){case RocketTurretBehavior t->t.team;case MachineGunBehavior gun->gun.team;case MedpackBehavior pack->pack.team();case RadarStationBehavior radar->radar.team();case ProximityMineBehavior mine->mine.team();case null,default->-1;};double radius=e.behavior instanceof DeployableRuntime.Explosive explosive?explosive.blast().radius():0,due=e.behavior instanceof TimedExplosiveBehavior charge?charge.detonateAt():0;if(e.kind.equals("blockfire")){radius=3;due=now+e.fuse;}int uses=e.behavior instanceof MedpackBehavior pack?pack.uses():-1;result.add(new Entity(e.id(),e.type,team,e.playerId,new Vector(e.x,e.y,e.z),true,e.kind,tool,e.velocity,radius,due,(e.type==10||e.type==9||e.type==38)&&radius>0,uses,e.behavior==null?null:e.behavior.hitCenter(e),e.behavior==null?0:e.behavior.hitRadius));
        }
        var roster=match.lives().players();var projectiles=match.entities().projectiles().engine().projectiles();for(int i=0;i<projectiles.size();i++){var p=projectiles.get(i);var owner=roster.stream().filter(q->q.id()==p.throwerId).findFirst().orElse(null);result.add(new Entity(p.entityId==null?-1-i:p.entityId,p.spec.entityType()==0?-1:p.spec.entityType(),owner==null?-1:owner.team(),p.throwerId,p.position,true,"projectile",p.tool,p.velocity,p.spec.radius(),p.explodeAt!=null&&p.explodeAt!=0?p.explodeAt:p.lifespanAt,p.spec.damage()>0&&p.spec.radius()>0,-1,null,0));}
        return result.size()<=192?List.copyOf(result):entities.select(result,roster.stream().map(this::player).toList(),now);
    }
}
