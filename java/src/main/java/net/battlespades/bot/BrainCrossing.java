// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.player.LifeCatalog;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Strategic swim ownership preserves the far-bank goal while bounded local detours finish. */
final class BrainCrossing {
    private final BotNavigationWorld world;private final BotModePolicies.Memory memory;private final RecoveryDriver skills;private final BrainNavigation navigation;private final BrainCombat combat;
    BrainCrossing(BotNavigationWorld world,BotModePolicies.Memory memory,RecoveryDriver skills,BrainNavigation navigation,BrainCombat combat){this.world=world;this.memory=memory;this.skills=skills;this.navigation=navigation;this.combat=combat;}
    boolean crosses(Perception f,Player p,BotBrainState s,double now){if(LifeCatalog.ZOMBIES.contains(p.classId()))return true;if(s.goal==null)return false;if(BrainTraversal.crossingPersonality(f,p).style()==BrainTraversal.Style.SWIM)return true;return s.dryRouteFailures>=navigation.traversal.detoursBeforeSwim(p,s,s.goal,now);}
    Intent cross(Perception f,Player p,BotBrainState s,BotProfile profile,double now){
        boolean infected=LifeCatalog.ZOMBIES.contains(p.classId());BotModeDecision decision=null;Player target=null;double reach=Double.POSITIVE_INFINITY;
        if(infected){decision=memory.decide(f,p);if(decision==null)return null;target=BrainTargeting.visible(world,f,p,s,decision);if(target!=null)reach=flat(target.position(),p.position());if(reach<=2.35){s.waterEscapePosition=p.position();s.waterEscapeAt=now;return combat.fight(f,p,target,s,profile,now,decision);}}
        if(s.waterRecovery||skills.active(p))return null;
        var goal=!infected?s.goal:reach<=8?new BotGoal(List.of("enemy",target.id(),target.generation()),target.position(),"combat_pursuit",1.5,true,null):BrainGoals.select(world,memory,f,p,s,now,decision);
        if(goal==null)return null;double remaining=flat(goal.position(),p.position());if(!infected&&remaining<=Math.max(4,goal.arrivalRadius()))return null;
        for(int segment=0;segment<2;segment++){
            boolean afloat=true;
            if(!(s.waterDetour&&s.routeIndex<s.route.size())){
                afloat=navigation.traversal.overWater(p.position());var excluded=BrainWater.exclusions(s,now);var step=world.water().step(p.position(),goal.position(),excluded);
                if(step==null&&infected){var bank=world.water().assistedStep(p.position(),goal.position(),excluded);if(bank!=null&&239-Math.rint(bank.waypoint().z()+2.25)<=3)step=new RouteStep(bank.waypoint(),Affordance.JUMP,bank.breach(),bank.entryEdge());}
                if(step!=null&&flat(goal.position(),step.waypoint())<remaining-.25){s.waterDetour=false;if(!s.route.isEmpty())BrainRoutingState.clear(s,now);BrainRoutingState.setGoal(s,goal,p.position(),now);return navigation.water.exit(f,p,s,step,now,false);}
            }
            var intent=navigation.navigate(f,p,s,goal,now,true);if(intent==null)return null;
            if(intent.action().kind()!=ActionKind.NONE||Math.hypot(intent.movement().direction().x(),intent.movement().direction().y())>1e-6||intent.debugRole().endsWith(":planning_wait")){s.waterDetour=afloat;return intent;}
            s.waterDetour=false;if(!intent.debugRole().endsWith(":segment_complete"))return null;
        }
        return null;
    }
}
