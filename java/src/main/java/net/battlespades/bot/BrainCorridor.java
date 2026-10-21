// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.Player;
import static net.battlespades.bot.BotState.*;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotVectors.*;

/** Retained coarse guidance shares each observer's admitted grant with detailed routing. */
final class BrainCorridor {
    private final BotNavigationWorld world;private final RecoveryDriver skills;
    BrainCorridor(BotNavigationWorld world,RecoveryDriver skills){this.world=world;this.skills=skills;}
    boolean spare(Player p,double now){return world.budget()==null||world.budget().spare(new Observer(p.id(),p.generation()),now);}
    static boolean absurd(List<Vector> corridor,Vector position,Vector goal){double length=0;for(int i=1;i<corridor.size();i++)length+=flat(corridor.get(i),corridor.get(i-1));return length>Math.max(2.2*flat(goal,position)+60,280);}
    static boolean futile(BotBrainState s,Vector at,Vector goal,double now){return s.corridorFutile.stream().anyMatch(e->now<e.until()&&distance(at,e.start())<=24&&distance(goal,e.goal())<=24);}
    Vector segment(BotBrainState s,Player p,BotGoal goal,double now){
        if(p.wade()||s.waterCommitted)return null;
        if(s.corridor.isEmpty()&&s.corridorSearch==null&&(s.deadEnd&&now>=s.deadEndRetryAt||now>=s.corridorRetryAt&&now-s.goalProgressAt>=6||s.walkAnswerWanted)&&!(now<s.corridorRejectedUntil&&s.corridorRejectedGoal!=null&&distance(goal.position(),s.corridorRejectedGoal)<=24)&&!futile(s,p.position(),goal.position(),now)){
            s.corridorSearch=world.corridor(p.position(),goal.position(),s.blockedEdges.keySet());s.corridorOrigin=p.position();s.corridorFailedGoal=s.corridorSearch==null?goal.position():null;
            if(s.corridorSearch==null){s.walklessGoal=goal.position();if(goal.role().equals("tdm_squad_support")){s.supportFailedGoal=goal.position();s.supportFailureAt=now;}}
            s.corridorRetryAt=now+15;s.deadEndRetryAt=now+5;
        }
        var search=s.corridorSearch;
        if(search!=null){
            if(!search.done()&&s.corridorYieldLocal&&(s.routeIndex>=s.route.size()||s.routeTopologyVersion!=world.version()))return null;
            if(!search.done()&&now-s.corridorSliceAt<.5&&!spare(p,now))return null;
            boolean done=search.done();search.advance();if(search.deferred())return null;if(!done)s.corridorSliceAt=now;s.corridorYieldLocal=true;
            if(search.done()){
                if(search.path().isEmpty()&&s.corridorOrigin!=null){s.corridorFutile=new ArrayList<>(s.corridorFutile.stream().filter(e->now<e.until()).toList());while(s.corridorFutile.size()>7)s.corridorFutile.removeFirst();s.corridorFutile.add(new BotBrainState.Futile(s.corridorOrigin,goal.position(),now+120));}
                if(!search.path().isEmpty()){
                    Integer entry=null;var candidates=new ArrayList<Integer>();for(int i=0;i<search.path().size();i++)candidates.add(i);candidates.sort(Comparator.comparingDouble(i->distance(p.position(),search.path().get(i))));
                    for(int offset=0;offset<Math.min(8,candidates.size());offset++){
                        if(offset<s.corridorJoinIndex)continue;int index=candidates.get(offset);var point=search.path().get(index);double gap=distance(p.position(),point);if(gap>12)break;if(gap<=.75){entry=index;break;}
                        var abilities=new HashSet<>(BrainInventory.movementAbilities(p));abilities.remove(Affordance.BREACH);var join=world.plan(p.position(),point,abilities,null,false,s.blockedEdges.keySet());
                        if(join.deferred()){s.corridorJoinIndex=offset;return null;}s.corridorJoinIndex=offset+1;if(join.reachedSegmentGoal()&&(join.steps().isEmpty()||distance(join.steps().getLast().waypoint(),point)<=1.25)){entry=index;break;}
                    }
                    if(entry!=null&&absurd(search.path().subList(entry,search.path().size()),p.position(),goal.position())){entry=null;s.corridorRejectedGoal=goal.position();s.corridorRejectedUntil=now+45;s.walklessGoal=goal.position();}
                    if(entry!=null){s.corridor=search.path();s.corridorIndex=entry;s.walklessGoal=null;s.dryDetourGoal=null;BrainRoutingState.clear(s,now);s.navigationProgressPosition=p.position();s.navigationProgressAt=now;s.navigationWindowPosition=p.position();s.navigationWindowAt=now;skills.restartStuckClock(p,now);}
                }else s.walklessGoal=goal.position();
                s.corridorSearch=null;s.corridorJoinIndex=0;s.corridorYieldLocal=false;
            }
        }
        return ahead(s,p.position());
    }
    static Vector ahead(BotBrainState s,Vector at){
        var corridor=s.corridor;int index=s.corridorIndex,end=Math.min(corridor.size(),index+16);if(index<end){int nearest=index;for(int i=index+1;i<end;i++)if(distance(at,corridor.get(i))<distance(at,corridor.get(nearest)))nearest=i;if(distance(at,corridor.get(nearest))<=1.25)index=nearest+1;}
        s.corridorIndex=index;if(index>=corridor.size()){s.corridor=List.of();return null;}var chosen=corridor.get(index);double traveled=0;for(int i=index;i+1<Math.min(corridor.size(),index+16);i++){traveled+=distance(corridor.get(i),corridor.get(i+1));if(traveled>s.corridorReach)break;chosen=corridor.get(i+1);}return chosen;
    }
}
