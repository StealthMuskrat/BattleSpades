// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Awareness and cooperative orders resolve to the same route, tool and action owner as combat. */
final class BrainTasks {
    private final BrainCombat.Navigation navigation;private final BrainCombat combat;private final BrainIntents intents;
    BrainTasks(BrainCombat.Navigation navigation,BrainCombat combat,BrainIntents intents){this.navigation=navigation;this.combat=combat;this.intents=intents;}
    Intent react(Perception f,Player p,BotBrainState s,BotAwareness.Reaction r,double now){
        if(r.suspect()!=null){s.contactPosition=r.suspect();s.contactUntil=now+4;}if(r.role().isEmpty())return null;
        if(r.goal()!=null){var routed=navigation.navigate(f,p,s,new BotGoal(List.of("awareness",r.role()),r.goal(),r.role(),3,r.sprint(),null),now);if(routed.action().kind()!=ActionKind.NONE||Math.hypot(routed.movement().direction().x(),routed.movement().direction().y())>1e-6||Math.hypot(r.heading().x(),r.heading().y())<=1e-6)return routed;}
        else BrainRoutingState.setGoal(s,null,p.position(),now);
        return intents.emit(f,s,new Movement(r.heading(),false,r.crouch(),false,r.sprint(),Affordance.WALK,true,null,null,null,1),look(r.look()),BrainInventory.weapon(p,null),Action.none(),r.priority(),false,false,r.look(),List.of(),r.role(),true);
    }
    Intent order(Perception f,Player p,BotBrainState s,BotProfile profile,Player visible,TeamTasks.Order order,double now){
        if(order.action().kind()!=ActionKind.NONE)return intents.emit(f,s,Movement.still(),look(order.look()),order.action().toolId(),order.action(),order.urgent()?Priority.SURVIVAL:Priority.TRAVERSAL,false,false,order.goal(),List.of(),order.role(),true);
        if(order.toolId()>=0)return intents.emit(f,s,Movement.still(),look(order.look()),order.toolId(),Action.none(),Priority.ROUTINE,false,false,order.goal(),List.of(),order.role(),true);
        if(visible!=null&&order.hold()){
            var decision=new BotModeDecision(order.goal(),order.role(),false,order.arrivalRadius(),"",BotModeDecision.Posture.DEFEND,.5,160,null);var intent=combat.fight(f,p,visible,s,profile,now,decision);
            if(Set.of(ActionKind.NONE,ActionKind.FIRE,ActionKind.RELOAD).contains(intent.action().kind()))return new Intent(intent.botId(),intent.botGeneration(),intent.frameId(),intent.mapEpoch(),intent.modeEpoch(),intent.topologyVersion(),intent.createdAt(),intent.expiresAt(),new Movement(ZERO,false,intent.movement().crouch(),false,false,Affordance.WALK,true,null,null,null,1),intent.look(),intent.toolId(),intent.action(),intent.priority(),intent.secondaryFire(),intent.zoom(),order.goal(),intent.debugPath(),order.role(),intent.lifeId(),intent.debugNavigation());return intent;
        }
        if(order.hold())return intents.emit(f,s,Movement.still(),look(order.look()),BrainInventory.weapon(p,null),Action.none(),Priority.ROUTINE,false,false,order.goal(),List.of(),order.role(),true);
        return navigation.navigate(f,p,s,new BotGoal(List.of("cooperative",order.taskId(),order.role()),order.goal(),order.role(),order.arrivalRadius(),false,null),now);
    }
    private static Look look(net.battlespades.physics.PlayerPhysics.Vector p){return p==null?null:new Look(p,false,-1,0,0,false);}
}
