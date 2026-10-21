// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** A stagnant cohort keeps one leader on its edge while followers find different geometry. */
final class BrainCrowd {
    private final BotNavigationWorld world;private final BrainIntents intents;
    BrainCrowd(BotNavigationWorld world,BrainIntents intents){this.world=world;this.intents=intents;}
    Intent detour(Perception f,Player p,BotBrainState s,BotGoal goal,double gap,double now){
        if(s.crowdDetourGoal!=null){if(now<s.crowdDetourUntil&&flat(s.crowdDetourGoal,p.position())>1.25)return null;s.crowdDetourGoal=null;s.crowdDetourUntil=now;}
        var nearby=f.players().stream().filter(q->q.bot()&&q.alive()&&q.spawned()&&q.team()==p.team()&&Math.abs(q.position().z()-p.position().z())<=2.25&&flat(q.position(),p.position())<=7).toList();
        if(gap<40||nearby.size()<4){s.crowdAnchor=null;s.crowdProgressAt=now;return null;}
        if(s.crowdAnchor==null||flat(p.position(),s.crowdAnchor)>=1.5){s.crowdAnchor=p.position();s.crowdProgressAt=now;return null;}
        if(now-s.crowdProgressAt<3)return null;s.crowdAnchor=p.position();s.crowdProgressAt=now;if(p.id()==nearby.stream().mapToInt(Player::id).min().orElseThrow())return null;
        var ids=f.players().stream().filter(q->q.bot()&&q.alive()&&q.spawned()&&q.team()==p.team()).map(Player::id).sorted().toList();int rank=ids.indexOf(p.id());double centered=rank-(ids.size()-1)*.5,sign=centered<0?-1:1,lateral=8+Math.abs(centered)*2;var axis=unit(goal.position().x()-p.position().x(),goal.position().y()-p.position().y());if(Math.hypot(axis.x(),axis.y())<=1e-6)axis=new Vector(1,0,0);
        s.crowdDetourGoal=new Vector(Math.clamp(p.position().x()-axis.y()*sign*lateral-axis.x()*12,1,510),Math.clamp(p.position().y()+axis.x()*sign*lateral-axis.y()*12,1,510),p.position().z());s.crowdDetourUntil=now+20;BrainRoutingState.invalidate(world,s,p.position(),now,20.);BrainRoutingState.clear(s,now);
        return intents.emit(f,s,Movement.still(),null,BrainInventory.weapon(p,null),Action.none(),Priority.TRAVERSAL,false,false,goal.position(),List.of(p.position(),s.crowdDetourGoal),goal.role()+":crowd_detour",false);
    }
}
