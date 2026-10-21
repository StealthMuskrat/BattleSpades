// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Stable team traversal styles, shoreline detours, live water ownership and route continuation. */
final class BrainTraversal {
    enum Style {DRY,SWIM,BRIDGE}
    record Personality(Style style,int sign,double bias){}
    private final BotNavigationWorld world;private final BrainCorridor corridor;
    BrainTraversal(BotNavigationWorld world,BrainCorridor corridor){this.world=world;this.corridor=corridor;}
    static Personality personality(Perception f,Player p){var ids=f.players().stream().filter(q->q.bot()&&q.team()==p.team()).map(Player::id).sorted().toList();int rank=ids.indexOf(p.id());if(rank<0)rank=Math.abs(p.id());double centered=rank-(ids.size()-1)*.5;int sign=centered<0?-1:1;if(Math.abs(centered)<.5)sign=rank%2!=0?-1:1;var profile=f.profile()==null?BrainInventory.fallback(p.id()):f.profile();double bias=Math.clamp((profile.creativity()-.5)*8+(profile.caution()-profile.aggression())*4,-6,6);return new Personality(Style.values()[rank%3],sign,bias);}
    static Personality crossingPersonality(Perception f,Player p){var personality=personality(f,p);return personality.style()==Style.BRIDGE&&(!p.loadout().contains(5)||p.blocks()<=0)?new Personality(Style.SWIM,personality.sign(),personality.bias()):personality;}
    boolean dryLanding(Player p){if(p.grounded())return true;var surface=world.surface((int)Math.floor(p.position().x()),(int)Math.floor(p.position().y()),p.position().z(),3,false);return surface!=null&&surface.supportZ()<239&&Math.abs(surface.position().z()-p.position().z())<=1.5;}
    boolean overWater(Vector p){var surface=world.surface((int)Math.floor(p.x()),(int)Math.floor(p.y()),p.z(),6,true);return surface!=null&&surface.supportZ()>=239;}
    boolean waterContact(Player p){if(p.wade())return true;var surface=world.surface((int)Math.floor(p.position().x()),(int)Math.floor(p.position().y()),p.position().z(),3,true);return surface!=null&&surface.supportZ()>=239&&Math.abs(surface.position().z()-p.position().z())<=1.5;}
    private static Integer region(NavigationAtlas atlas,Vector p){int x=(int)Math.floor(p.x()),y=(int)Math.floor(p.y());if(x<0||x>=atlas.width()||y<0||y>=atlas.height())return null;int index=y*atlas.width()+x;if((atlas.flags(index)&NavigationAtlas.WATER)!=0)return -1;int region=atlas.region(index);return region==0?null:region;}
    int detoursBeforeSwim(Player p,BotBrainState s,BotGoal goal,double now){var atlas=world.atlas().orElse(null);if(atlas==null)return 3;var here=region(atlas,p.position());var there=region(atlas,goal.position());if(Objects.equals(there,-1)||here!=null&&here!=0&&there!=null&&there!=0&&!here.equals(there))return 0;if(now<s.corridorRejectedUntil&&s.corridorRejectedGoal!=null&&distance(goal.position(),s.corridorRejectedGoal)<=24)return 0;return 3;}
    static Vector detour(Player p,Vector goal,Personality personality,int attempt){double dx=goal.x()-p.position().x(),dy=goal.y()-p.position().y(),gap=Math.hypot(dx,dy);if(gap<=1e-6)return goal;double ux=dx/gap,uy=dy/gap;int sign=personality.sign();if(attempt%2==0)sign*=-1;double lateral=36+Math.min(2,Math.max(0,attempt-1))*12;return new Vector(Math.clamp(p.position().x()+ux*8-uy*lateral*sign,1,510),Math.clamp(p.position().y()+uy*8+ux*lateral*sign,1,510),p.position().z());}
    static Vector lane(Perception f,Player p,Vector goal){return lane(f,p,goal,p.position());}
    static Vector lane(Perception f,Player p,Vector goal,Vector origin){double dx=goal.x()-origin.x(),dy=goal.y()-origin.y(),gap=Math.hypot(dx,dy);if(gap<96)return goal;var ids=f.players().stream().filter(q->q.bot()&&q.team()==p.team()).map(Player::id).sorted().toList();int rank=ids.indexOf(p.id());if(rank<0)return goal;var style=personality(f,p);double centered=rank-(ids.size()-1)*.5,raw=centered*8,lateral=Math.clamp(raw,-20,20),overflow=Math.max(0,Math.abs(raw)-20),forward=Math.min(Math.max(36,56-overflow*.5+style.bias()),gap),ux=dx/gap,uy=dy/gap;return new Vector(Math.clamp(origin.x()+ux*forward-uy*lateral,1,510),Math.clamp(origin.y()+uy*forward+ux*lateral,1,510),origin.z());}
    void extend(Perception f,Player p,BotBrainState s,BotGoal goal,double now){
        var route=s.route;if(route.isEmpty()||s.routeIndex>=route.size()||now<s.nextExtensionAt||s.escapeGoal!=null||!s.corridor.isEmpty()||s.corridorSearch!=null||s.dryDetourGoal!=null||s.crowdDetourGoal!=null||route.getLast().affordance()!=Affordance.WALK||s.routeTopologyVersion!=f.topologyVersion())return;
        var last=route.getLast().waypoint();if(flat(goal.position(),last)<=goal.arrivalRadius()+2||PathFollowing.remainingDistance(route,s.routeIndex,p.position())>12||!corridor.spare(p,now))return;
        s.nextExtensionAt=now+.35;var target=lane(f,p,goal.position(),last);var abilities=new HashSet<>(BrainInventory.movementAbilities(p));abilities.remove(Affordance.JETPACK);var plan=world.plan(last,target,abilities,BrainInventory.dig(p),false,s.blockedEdges.keySet());if(plan.deferred())return;if(!BrainRouteProgress.advance(plan,last)){s.nextExtensionAt=now+3;return;}var continued=new ArrayList<>(route);continued.addAll(plan.steps());s.route=List.copyOf(continued);
    }
}
