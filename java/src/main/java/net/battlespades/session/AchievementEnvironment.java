// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.math.BigInteger;
import java.util.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.world.BlockDamageModel.Cell;
import net.battlespades.world.MapMetadata;

/** Owner-only blast, terrain, authored-volume and resupply rules. No disk operations occur here. */
final class AchievementEnvironment {
    static final class Blast {
        final StandardMatchDomain match;final PlayerLife thrower;final int type;final Vector origin;
        boolean buried,closed;MapEntity turret;PlayerLife target;int kills,blocks;
        final Set<PlayerLife> victims=Collections.newSetFromMap(new IdentityHashMap<>());
        Blast(StandardMatchDomain match,PlayerLife thrower,int type,Vector origin){this.match=match;this.thrower=thrower;this.type=type;this.origin=origin;}
    }
    private record Projectile(int type,MapEntity turret,Integer target){}
    private static final class Region {
        final MapMetadata.Achievement definition;Set<Cell> remaining=new HashSet<>();boolean done;
        final Set<PlayerLife> contributors=Collections.newSetFromMap(new IdentityHashMap<>());
        Region(MapMetadata.Achievement definition){this.definition=definition;}
    }
    private final HostedAchievements engine;
    private final ArrayDeque<Blast> blasts=new ArrayDeque<>();private Projectile pending;
    private final Map<MapEntity,Integer> turretKills=new IdentityHashMap<>();private final List<Region> regions=new ArrayList<>();
    private final Map<Integer,PlayerLife> lastDamager=new HashMap<>();private double resyncIn=1;
    AchievementEnvironment(HostedAchievements engine){this.engine=engine;}
    private static int c(String name){return net.battlespades.match.AchievementCatalog.constant(name);}
    private static boolean connected(StandardMatchDomain match,PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(v->v==p);}
    void reset(){blasts.clear();pending=null;regions.clear();turretKills.clear();lastDamager.clear();resyncIn=1;}
    void start(StandardMatchDomain match){
        if(!match.metadata().officialMap())return;var razed=new HashSet<String>();
        for(var definition:match.metadata().achievementRegions()){
            if(!net.battlespades.match.AchievementCatalog.definitions().containsKey(definition.apiName()))continue;
            var region=new Region(definition);
            if(kind(region,"ACH_BLOCK_DESTROY_REGION")){
                var b=definition.bounds();for(int x=floor(b.get(0));x<=floor(b.get(1));x++)for(int y=floor(b.get(2));y<=floor(b.get(3));y++)for(int z=floor(b.get(4));z<=floor(b.get(5));z++)if(match.world().solid(x,y,z))region.remaining.add(new Cell(x,y,z));
                if(region.remaining.isEmpty())razed.add(definition.apiName());
            }else if(!kind(region,"ACH_KILL_REGION"))continue;
            regions.add(region);
        }
        regions.removeIf(r->kind(r,"ACH_BLOCK_DESTROY_REGION")&&razed.contains(r.definition.apiName()));
    }
    private static int floor(double n){return (int)Math.floor(n);}
    private static boolean kind(Region r,String constant){return r.definition.kind().equals(BigInteger.valueOf(c(constant)));}
    void projectile(StandardMatchDomain match,ProjectileEngine.Projectile p,PlayerLife thrower){
        pending=new Projectile(p.spec.killType(),p.turretSource!=null?p.turretSource:p.turretId==null?null:match.entities().registry().get(p.turretId),p.turretTargetId);
        if(p.contactPlayerId==null||thrower==null||thrower.bot()||!engine.live(match)||!Set.of("rocket","rocket2").contains(p.spec.name()))return;
        var target=match.lives().players().stream().filter(v->v.id()==p.contactPlayerId).findFirst().orElse(null);
        if(target==null||target==thrower||target.team()==thrower.team()||!Set.of(2,3).contains(target.team())||!Set.of(2,3).contains(thrower.team())||target.bot()&&!engine.countBotKills())return;
        if(thrower.alive()&&thrower.body().state().airborne())engine.add(thrower,"airborne_rocket_count",1);
    }
    Blast begin(StandardMatchDomain match,PlayerLife thrower,int type,Vector origin){
        var scope=new Blast(match,thrower,type,origin);var projectile=pending;pending=null;
        if(projectile!=null&&projectile.type==type){scope.turret=projectile.turret;scope.target=projectile.target==null?null:match.lives().players().stream().filter(p->p.id()==projectile.target).findFirst().orElse(null);}
        if(type==c("LANDMINE_KILL")&&origin!=null)scope.buried=match.world().solid(floor(origin.x()),floor(origin.y()),floor(origin.z()));
        blasts.addLast(scope);return scope;
    }
    void end(Blast scope){
        if(scope.closed)return;scope.closed=true;blasts.remove(scope);var target=scope.target;
        if(scope.blocks>0&&target!=null&&!target.bot()&&engine.live(scope.match)&&target.alive()&&(scope.thrower==null||target.team()!=scope.thrower.team())&&!scope.victims.contains(target)&&engine.count(target,false,"turret_evaded_blocks",scope.blocks)>=100)engine.unlock(target,"turret_evasion");
    }
    boolean damaged(LifeRuntime.DamageEvidence event){
        var scope=blasts.peekLast();if(scope==null)return false;scope.victims.add(event.victim());
        if(scope.origin==null||event.killType()!=c("DYNAMITE_KILL"))return false;
        double feet=event.victim().position().z()+net.battlespades.match.AchievementCatalog.number(event.victim().body().hitboxCrouched()?"PLAYER_CROUCHING_POS_ABOVE_GROUND":"PLAYER_STANDING_POS_ABOVE_GROUND");
        return scope.origin.z()-feet>=.5;
    }
    void killed(StandardMatchDomain match,PlayerLife killer,PlayerLife victim,int type,boolean below,PlayerLife last,int lastType){
        if(last==killer&&below&&lastType==c("DYNAMITE_KILL")&&(type==c("DYNAMITE_KILL")||type==c("FALL_KILL")))engine.add(killer,"dynamite_below_count",1);
        var scope=blasts.peekLast();if(scope!=null&&scope.thrower==killer&&scope.type==type){
            if(++scope.kills==3)engine.unlock(killer,"misc_triple_explosion");
            if(type==c("LANDMINE_KILL")&&scope.buried)engine.add(killer,"landmine_hidden_count",1);
            if(scope.turret!=null&&type==c("ROCKET_TURRET_KILL")&&turretKills.merge(scope.turret,1,Integer::sum)==10)engine.unlock(killer,"turret_accuracy");
        }
        var position=killer.position();for(var region:regions){var d=region.definition;if(!kind(region,"ACH_KILL_REGION")||d.team()!=null&&d.team()!=killer.team()||!contains(d.bounds(),position))continue;
            if(d.apiName().equals("map_maya_kill")&&type!=c("MELEE_KILL")||d.apiName().equals("map_zombieisland_zombie_kill")&&!engine.zombie(victim))continue;
            if(engine.count(killer,true,"region:"+d.apiName(),1)==Math.max(1,d.kills().doubleValue()))engine.unlock(killer,d.apiName());
        }
    }
    private static boolean contains(List<Double> b,Vector p){return p.x()>=b.get(0)&&p.x()<=b.get(1)&&p.y()>=b.get(2)&&p.y()<=b.get(3)&&p.z()>=b.get(4)&&p.z()<=b.get(5);}
    void blocks(StandardMatchDomain match,PlayerLife p,List<Cell> removed,List<Cell> collapsed,List<List<Cell>> chunks,Integer tool){
        var cells=new ArrayList<>(removed);cells.addAll(collapsed);if(cells.isEmpty())return;var scope=blasts.peekLast();Integer cause=scope==null?null:scope.type;
        for(var region:regions){if(region.done||region.remaining.isEmpty())continue;boolean hit=region.remaining.removeAll(cells);if(!hit)continue;var d=region.definition;
            boolean credit=p!=null&&!p.bot()&&engine.live(match)&&(d.team()==null||d.team()==p.team());
            if(d.apiName().equals("map_zombieisland_destroy"))credit&=p!=null&&engine.zombie(p);
            if(d.apiName().equals("map_moon_destroy"))credit&=cause!=null&&(cause==c("ROCKET_KILL")||cause==c("ROCKET2_KILL"));
            if(d.apiName().equals("map_concrete_destroy"))credit&=match.modeCode().equals("dem");
            if(credit)region.contributors.add(p);if(region.remaining.isEmpty())finish(match,d.apiName());
        }
        if(scope!=null)scope.blocks=Math.addExact(scope.blocks,cells.size());if(p==null||!engine.live(match))return;
        var damage=match.demolition().flatMap(d->d.enemyObjectiveDamage(p,cells.stream().map(v->new net.battlespades.world.MutableWorld.Cell(v.x(),v.y(),v.z())).toList()));
        damage.ifPresent(d->lastDamager.put(d.team(),p));if(p.bot())return;
        if(engine.zombie(p))engine.add(p,"block_as_zombie_count",cells.size());
        if(cause!=null&&(cause==c("GRENADE_KILL")||cause==c("CLASSIC_GRENADE_KILL"))){long structures=chunks.stream().filter(chunk->chunk.size()>=100).count();if(structures>0)engine.add(p,"grenade_demolish_count",structures);}
        else if(scope==null&&tool!=null&&tool==c("MINIGUN_TOOL")&&chunks.stream().anyMatch(chunk->chunk.size()>=50))engine.unlock(p,"minigun_demolish");
        damage.ifPresent(d->{engine.add(p,"demolition_damage_many_rounds_count",d.count());if(engine.count(p,false,"demolition_damage",d.count())>=100)engine.unlock(p,"demolition_damage_one_round");if(cause!=null&&cause==c("DRILL_KILL"))engine.add(p,"drillgun_demolition_count",d.count());});
    }
    private void finish(StandardMatchDomain match,String api){
        var entries=regions.stream().filter(r->kind(r,"ACH_BLOCK_DESTROY_REGION")&&r.definition.apiName().equals(api)).toList();
        if(entries.isEmpty()||entries.stream().anyMatch(r->!r.remaining.isEmpty()||r.done))return;
        var shared=Collections.newSetFromMap(new IdentityHashMap<PlayerLife,Boolean>());shared.addAll(entries.getFirst().contributors);
        for(var entry:entries){entry.done=true;shared.retainAll(entry.contributors);}for(var p:shared)if(connected(match,p))engine.unlock(p,api);
    }
    void tick(StandardMatchDomain match,double dt){resyncIn-=dt;if(resyncIn>0)return;resyncIn=1;for(var region:regions){if(region.done||region.remaining.isEmpty()||region.remaining.size()>512)continue;region.remaining.removeIf(v->!match.world().solid(v.x(),v.y(),v.z()));if(region.remaining.isEmpty())finish(match,region.definition.apiName());}}
    void crate(StandardMatchDomain match,PlayerLife p,int award,HostedAchievements.CrateBaseline before){
        if(before==null||p.bot()||!engine.live(match))return;
        if(award==c("MOST_HEALTH_CRATES_COLLECTED")){int gained=p.health()-before.health();if(gained>0&&engine.count(p,false,"health_from_drops",gained)>=150)engine.unlock(p,"health_drop_greedy");}
        else if(award==c("MOST_AMMO_CRATES_COLLECTED")){var profile=LifeCatalog.WEAPONS.get(before.weapon());if(profile==null||p.inventory().weapon()!=before.weapon())return;int gained=p.inventory().ammo().reserve()-before.reserve();if(gained>0&&profile.reserve()>0&&engine.count(p,false,"ammo_from_drops:"+before.weapon(),gained)>=profile.reserve())engine.unlock(p,"ammo_drop_greedy");}
    }
    void ended(StandardMatchDomain match,Integer winner){if(winner==null||!engine.counting()||!Set.of(2,3).contains(winner))return;var p=lastDamager.get(5-winner);if(connected(match,p)&&p.team()==winner)engine.unlock(p,"demolition_final_damage");}
}
