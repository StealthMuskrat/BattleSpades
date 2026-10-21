// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import java.util.function.DoubleSupplier;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.combat.CombatCatalog;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotVectors.*;

/** Worker-owned source decision loop. Live actions remain the director's responsibility. */
public final class SimpleBotBrain implements BotWorker.Brain {
    private final Thread owner=Thread.currentThread();
    private final BotNavigationWorld world;
    private final double interval;
    private long mapEpoch=-1;
    private final LinkedHashMap<Observer,BotBrainState> states=new LinkedHashMap<>();
    final CooperativeBehavior cooperative;
    final BotModePolicies.Memory memory;
    final RecoveryDriver skills;
    final BotAwareness awareness;
    final BrainIntents intents;
    final BrainSkills skillIntents;
    final BrainNavigation navigation;
    final BrainCombat combat;
    final BrainCrossing crossing;
    final BrainBlockWork blocks;
    final BrainCharges charges;
    final BrainDeploy deploy;
    final BrainTasks tasks;
    final BrainUnofficial unofficial;
    private final BrainOriented oriented=new BrainOriented();

    public SimpleBotBrain(BotNavigationWorld world,double decisionHz,DoubleSupplier clock){
        this.world=Objects.requireNonNull(world);interval=1/Math.max(1,decisionHz);
        cooperative=new CooperativeBehavior(world);memory=new BotModePolicies.Memory(new BotModePolicies());skills=new RecoveryDriver(world,clock);awareness=new BotAwareness(world);intents=new BrainIntents(clock);skillIntents=new BrainSkills(skills,intents);navigation=new BrainNavigation(world,intents,skills,skillIntents);combat=new BrainCombat(world,awareness,intents,oriented,navigation);crossing=new BrainCrossing(world,memory,skills,navigation,combat);blocks=new BrainBlockWork(world,intents);charges=new BrainCharges(world,intents,navigation,blocks);deploy=new BrainDeploy(world,intents,navigation,combat,memory);tasks=new BrainTasks(navigation,combat,intents);unofficial=new BrainUnofficial(world,intents,skills,skillIntents,navigation,combat);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Brain outside worker");}
    @Override public void resetForMap(long epoch){owned();if(epoch==mapEpoch)return;mapEpoch=epoch;states.clear();oriented.reset();cooperative.reset();memory.reset();skills.reset();awareness.reset();}
    @Override public void resetBot(int id,int generation){owned();states.remove(new Observer(id,generation));cooperative.forget(id,generation);memory.forget(id,generation);skills.forget(id,generation);awareness.forget(id,generation);}
    public Intent requestLocomotionSkill(Perception frame,RecoveryDriver.Request request){owned();var result=skillIntents.request(frame,request);var s=states.get(new Observer(frame.observerId(),frame.observerGeneration()));return result==null||s==null?result:diagnostics(result,s,frame.createdAt());}
    private static Intent diagnostics(Intent i,BotBrainState s,double now){return new Intent(i.botId(),i.botGeneration(),i.frameId(),i.mapEpoch(),i.modeEpoch(),i.topologyVersion(),i.createdAt(),i.expiresAt(),i.movement(),i.look(),i.toolId(),i.action(),i.priority(),i.secondaryFire(),i.zoom(),i.debugGoal(),i.debugPath(),i.debugRole(),i.lifeId(),BrainIntents.diagnostics(s,now));}
    static Intent action(Intent i,Action action){return new Intent(i.botId(),i.botGeneration(),i.frameId(),i.mapEpoch(),i.modeEpoch(),i.topologyVersion(),i.createdAt(),i.expiresAt(),i.movement(),i.look(),i.toolId(),action,i.priority(),i.secondaryFire(),i.zoom(),i.debugGoal(),i.debugPath(),i.debugRole(),i.lifeId(),i.debugNavigation());}
    @Override public Intent decide(Perception f){owned();var result=decideCore(f);if(!f.fun().isEmpty())return result;if(result!=null)result=awareness.overlay(f,result);if(result==null||result.action().kind()!=ActionKind.NONE||result.priority().value()>=Priority.SURVIVAL.value()||result.look()!=null&&result.look().visible())return result;
        var p=observer(f);if(p==null||p.reloading()||!BrainInventory.FIREARMS.contains(result.toolId()))return result;var wallet=BrainInventory.wallet(p,result.toolId());int size=CombatCatalog.get(result.toolId()).clip();if(wallet.reserve()>0&&wallet.clip()<size&&wallet.clip()<=Math.max(0,(int)(size*.4))&&p.weaponTool()==result.toolId()&&f.createdAt()-p.lastDamageAt()>1.5)return action(result,BrainCombat.action(ActionKind.RELOAD,result.toolId()));return result;
    }
    private static Player observer(Perception f){return f.players().stream().filter(p->p.id()==f.observerId()&&p.generation()==f.observerGeneration()).findFirst().orElse(null);}
    private Intent decideCore(Perception f){
        var p=observer(f);if(p==null||!p.alive()||!p.spawned())return null;if(f.mapEpoch()!=mapEpoch)resetForMap(f.mapEpoch());var key=new Observer(p.id(),p.generation());var s=states.get(key);if(s==null||s.mapEpoch!=f.mapEpoch()||s.modeEpoch!=f.modeEpoch()||s.lifeId!=p.lifeId()||s.carriedEntityId!=p.carriedEntityId()||s.canShoot!=p.canShoot()){s=new BotBrainState(f.mapEpoch(),f.modeEpoch(),p.lifeId(),p.carriedEntityId(),p.canShoot());states.put(key,s);}
        double now=f.createdAt();if(now+1e-9<s.nextDecisionAt)return null;s.nextDecisionAt=now+interval;prune(f);BrainRoutingState.prune(s,now);if(!f.fun().isEmpty())return unofficial.decide(f,p,s,now);var profile=f.profile()==null?BrainInventory.fallback(p.id()):f.profile();
        if(s.flightStep!=null){var flight=BrainMobility.flight(p,s,now);if(flight!=null)return intents.emit(f,s,flight.movement(),flight.look(),flight.tool(),Action.none(),Priority.TRAVERSAL,false,false,flight.goal(),List.of(),flight.role(),true);}
        if(navigation.traversal.waterContact(p)){s.waterCommitted=true;s.waterDrySince=null;}else if(s.waterCommitted&&navigation.traversal.dryLanding(p)){s.waterCommitted=false;s.waterDrySince=now;s.dryDetourGoal=null;s.dryDetourUntil=0;s.dryRouteFailures=0;}
        if(s.waterCommitted)return water(f,p,s,profile,now);
        if(s.waterDrySince==null||now-s.waterDrySince>=2){s.waterStepKey=null;s.waterLandingStep=null;s.waterBreachTarget=null;s.waterBestDistance=Double.POSITIVE_INFINITY;s.waterProgressAt=now;s.waterRecovery=false;s.waterGoalReached=false;s.waterEscapePosition=null;s.waterEscapeAt=now;s.waterSearchHeading=null;s.waterSearchUntil=0;s.waterSearchOrigin=null;s.waterDrySince=null;s.waterDetour=false;}skills.leftWater(p,now);
        if(p.grounded()){var escape=BotCombatTactics.hazardEscape(world,f,p,profile,s.engagement,now);if(escape!=null){BrainRoutingState.clear(s,now);return emit(f,p,s,new Movement(escape,false,false,false,true,Affordance.WALK,true,null,null,null,1),null,Priority.SURVIVAL,null,"evade_explosive",false);}}
        var decision=memory.decide(f,p);
        if(decision!=null&&decision.role().equals("diamond_guard_dropoff")){double gap=distance(p.position(),decision.position());if(now>=s.guardRetryAt){if(s.guardTarget==null||distance(s.guardTarget,decision.position())>8){s.guardTarget=decision.position();s.guardBestDistance=gap;s.guardProgressAt=now;}else if(gap+1<s.guardBestDistance){s.guardBestDistance=gap;s.guardProgressAt=now;}}
            if(now>=s.guardRetryAt&&s.guardTarget!=null&&now-s.guardProgressAt>=10&&gap>decision.arrivalRadius()){s.guardRetryAt=now+35+p.id()%7;s.guardTarget=null;BrainRoutingState.setGoal(s,null,p.position(),now);}if(now<s.guardRetryAt)decision=new BotModeDecision(p.position(),"diamond_mine_blocks",decision.sprint(),.5,"mine",BotModeDecision.Posture.MINE,decision.objectivePriority(),decision.engagementRadius(),decision.watchPosition());
        }else s.guardTarget=null;
        var visible=BrainTargeting.visible(world,f,p,s,decision);var reaction=awareness.react(f,p,profile,visible,decision,now);if(reaction!=null){var r=tasks.react(f,p,s,reaction,now);if(r!=null)return r;}boolean coop=f.behaviorVersion().equals("cooperative");if(coop){var r=charges.own(f,p,s,now,visible!=null);if(r!=null)return r;}
        if(visible==null&&p.reloading()&&p.grounded()&&now<s.engagement.coverUntil&&(s.engagement.coverDuck||s.engagement.cover!=null)&&now-p.lastDamageAt()>.6){var contact=s.contactPosition;return intents.emit(f,s,new Movement(ZERO,false,true,false,false,Affordance.WALK,true,null,null,null,1),contact==null?null:new Look(new Vector(contact.x(),contact.y(),p.eye().z()),false,-1,0,0,false),BrainInventory.FIREARMS.contains(p.weaponTool())?p.weaponTool():BrainInventory.weapon(p,null),Action.none(),Priority.COMBAT,false,false,contact,List.of(),"cover_reload",true);}
        if(visible==null&&skills.active(p)){var r=skillIntents.step(f,p,s,now);if(r!=null)return r;}if(coop){var order=cooperative.decide(f,p,visible,decision);if(order!=null)return tasks.order(f,p,s,profile,visible,order,now);}if(visible!=null)return combat.fight(f,p,visible,s,profile,now,decision);
        if(coop){var r=charges.plant(f,p,s,now,decision);if(r!=null)return r;}if(decision!=null&&decision.directive().equals("mine")){var r=blocks.mine(f,p,s,now);if(r!=null)return r;decision=position(decision,blocks.mineSite(f,p,s,now),1.25);}if(decision!=null&&Set.of("demolish","repair","siege").contains(decision.directive())){var r=blocks.work(f,p,s,now,decision);if(r!=null)return r;}
        if(!coop&&(decision==null||decision.objectivePriority()<.9||Set.of(BotModeDecision.Posture.DEFEND,BotModeDecision.Posture.ESCORT).contains(decision.posture()))){var r=deploy.medic(f,p,s,now);if(r!=null)return r;}
        Intent gadget=coop?deploy.classGadget(f,p,s,now,decision):decision==null||decision.directive().equals("fortify")||decision.objectivePriority()<.85?deploy.strategic(f,p,s,now,decision):null;if(gadget!=null)return gadget;
        if(decision!=null&&RecoveryDriver.DIRECTIVES.contains(decision.directive())&&flat(decision.position(),p.position())<=14){var r=skillIntents.result(f,p,s,skills.request(p,f.profile(),RecoveryDriver.Request.fromDirective(decision.directive(),decision.position(),p),now),now);if(r!=null)return r;}
        var goal=BrainGoals.select(world,memory,f,p,s,now,decision);if(goal==null){BrainRoutingState.setGoal(s,null,p.position(),now);return emit(f,p,s,Movement.still(),null,Priority.ROUTINE,null,"idle_no_goal",true);}return navigation.navigate(f,p,s,goal,now);
    }
    static BotModeDecision position(BotModeDecision d,Vector p,double radius){return new BotModeDecision(p,d.role(),d.sprint(),radius,d.directive(),d.posture(),d.objectivePriority(),d.engagementRadius(),d.watchPosition());}
    private Intent water(Perception f,Player p,BotBrainState s,BotProfile profile,double now){
        if(s.waterBreachTarget!=null&&!world.solid(s.waterBreachTarget.x(),s.waterBreachTarget.y(),s.waterBreachTarget.z())){s.waterBreachTarget=null;s.waterEscapeAt=now;}if(s.goal!=null&&s.goal.role().equals("tdm_squad_support"))s.waterRecovery=true;s.navigationProgressPosition=null;s.navigationProgressAt=now;boolean force=false;
        if(s.waterEscapePosition==null||flat(p.position(),s.waterEscapePosition)>=4){s.waterEscapePosition=p.position();s.waterEscapeAt=now;}else if(now-s.waterEscapeAt>=4.5){boolean spent=s.waterRecovery;s.waterEscapePosition=p.position();s.waterEscapeAt=now;s.waterRecovery=true;BrainRoutingState.clear(s,now);force=true;if(spent&&crossing.crosses(f,p,s,now)){s.waterRecovery=false;force=false;}}
        if(skills.active(p)){var r=skillIntents.step(f,p,s,now);if(r!=null)return r;}var landing=s.waterLandingStep;if(landing!=null&&!force&&!p.grounded()&&now-s.waterProgressAt<1.2&&flat(p.position(),landing.waypoint())<=1.75&&p.position().z()<landing.waypoint().z()-.25)return navigation.water.exit(f,p,s,landing,now,false);
        if(crossing.crosses(f,p,s,now)){var r=crossing.cross(f,p,s,profile,now);if(r!=null)return r;}boolean blocked=BrainWater.exclusions(s,now).stream().anyMatch(e->e.source().z()>=239);if(s.goal!=null&&flat(s.goal.position(),p.position())<=Math.max(4,s.goal.arrivalRadius())){s.waterGoalReached=true;s.waterRecovery=true;}else if(blocked)s.waterRecovery=true;
        var target=s.goal==null?null:s.goal.position();var climb=skillIntents.result(f,p,s,skills.considerWater(p,f.profile(),now,target,RecoveryDriver.swimSecondsToShore(world,p,target),s.waterRecovery),now);if(climb!=null)return climb;
        if(s.goal!=null&&!s.waterRecovery){var r=navigation.navigate(f,p,s,s.goal,now,true);if(r.action().kind()!=ActionKind.NONE||Math.hypot(r.movement().direction().x(),r.movement().direction().y())>1e-6)return r;s.waterRecovery=true;}
        var step=world.water().step(p.position(),s.goal!=null&&!s.waterGoalReached&&!s.waterRecovery?s.goal.position():null,BrainWater.exclusions(s,now));var shore=navigation.water.exit(f,p,s,step,now,force);if(Set.of("water_no_route","water_search_shore").contains(shore.debugRole())&&s.waterRecovery&&crossing.crosses(f,p,s,now)){s.waterRecovery=false;var r=crossing.cross(f,p,s,profile,now);if(r!=null)return r;}return shore;
    }
    private Intent emit(Perception f,Player p,BotBrainState s,Movement m,Look look,Priority priority,Vector goal,String role,boolean crowd){return intents.emit(f,s,m,look,BrainInventory.weapon(p,null),Action.none(),priority,false,false,goal,List.of(),role,crowd);}
    private void prune(Perception f){var observed=new HashMap<Observer,Player>();for(var p:f.players())observed.put(new Observer(p.id(),p.generation()),p);for(var key:new ArrayList<>(states.keySet())){var s=states.get(key);var p=observed.get(key);if(p!=null&&(!p.alive()||s.lifeId!=p.lifeId())||f.createdAt()-s.nextDecisionAt>15){states.remove(key);memory.forget(key.playerId(),key.generation());}}while(states.size()>128){var oldest=states.entrySet().stream().min(Comparator.comparingDouble(e->e.getValue().nextDecisionAt)).orElseThrow().getKey();states.remove(oldest);}}
    @Override public Map<String,Object> metrics(){owned();var teams=cooperative.teams();var kinds=new LinkedHashMap<String,Integer>();for(var p:teams.projects().values())kinds.merge(p.kind,1,Integer::sum);var events=teams.events().stream().map(e->Map.<String,Object>of("kind",e.kind(),"task_id",e.taskId(),"reason",e.reason(),"at",e.at())).toList();return BotMessages.freeze(Map.of("counters",teams.metrics(),"events",events,"active_projects",teams.projects().size(),"project_kinds",kinds,"active_lives",cooperative.lifeCount()));}
}
