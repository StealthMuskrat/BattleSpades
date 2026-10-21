// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;

/** One intent owner for active, completed and explicitly requested recovery primitives. */
final class BrainSkills {
    private final RecoveryDriver driver;private final BrainIntents intents;
    BrainSkills(RecoveryDriver driver,BrainIntents intents){this.driver=driver;this.intents=intents;}
    Intent wrap(Perception f,Player p,RecoverySkill.Command c,Vector goal){return intents.emit(f,null,new Movement(c.direction(),c.jump(),c.crouch(),c.sneak(),c.sprint(),c.affordance(),true,null,null,null,1),c.look()==null?null:new Look(c.look(),false,-1,0,0,false),c.toolId()>=0?c.toolId():BrainInventory.weapon(p,null),c.action(),c.role().startsWith("water_")?Priority.SURVIVAL:Priority.TRAVERSAL,c.secondary(),false,goal,List.of(p.position()),c.role(),false);}
    Intent result(Perception f,Player p,BotBrainState s,RecoverySkill.Command c,double now){if(c==null)return null;if(!c.status().equals("running")){BrainRoutingState.clear(s,now);s.escapeGoal=null;return null;}var intent=wrap(f,p,c,s.goal==null?null:s.goal.position());return new Intent(intent.botId(),intent.botGeneration(),intent.frameId(),intent.mapEpoch(),intent.modeEpoch(),intent.topologyVersion(),intent.createdAt(),intent.expiresAt(),intent.movement(),intent.look(),intent.toolId(),intent.action(),intent.priority(),intent.secondaryFire(),intent.zoom(),intent.debugGoal(),intent.debugPath(),intent.debugRole(),intent.lifeId(),BrainIntents.diagnostics(s,f.createdAt()));}
    Intent step(Perception f,Player p,BotBrainState s,double now){return result(f,p,s,driver.step(p,f.profile(),now),now);}
    Intent request(Perception f,RecoveryDriver.Request request){var p=f.players().stream().filter(v->v.id()==f.observerId()&&v.generation()==f.observerGeneration()).findFirst().orElse(null);if(p==null||!p.alive())return null;var command=driver.request(p,f.profile(),request,f.createdAt());return command==null||!command.status().equals("running")?null:wrap(f,p,command,request.target());}
}
