// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import java.util.function.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;
import net.battlespades.bot.BotState.Observer;
import static net.battlespades.bot.BotMessages.Player;
import static net.battlespades.bot.BotVectors.*;
import static net.battlespades.bot.RecoveryPlanning.*;
import net.battlespades.bot.RecoverySkill.Command;

/** Worker-owned skill triggers, resumable searches, progress watchdogs and failure backoff. */
public final class RecoveryDriver {
    public static final Set<String> DIRECTIVES=Set.of("climb","pillar_up","dig_staircase_up","bridge");
    public record Request(String kind,Vector target,Vector direction,int levels,int length) {
        public static Request fromDirective(String directive,Vector position,Player p) {
            var here=p.position();var direction=new Vector(position.x()-here.x(),position.y()-here.y(),0);int rise=Math.max(0,(int)Math.rint(here.z()-position.z()));
            return switch(directive) {case "pillar_up"->new Request(directive,position,null,Math.max(1,rise),0);case "dig_staircase_up"->new Request(directive,position,direction,Math.max(1,rise),0);case "bridge"->new Request(directive,position,direction,0,Math.max(1,(int)Math.ceil(Math.max(Math.abs(direction.x()),Math.abs(direction.y())))));default->new Request("climb",position,null,0,0);};
        }
    }
    private record Spec(String kind,Vector target,double radius,Request request) {}
    private record Pending(Search search,String purpose,Spec spec,double startedAt,Predicate<Plan> accept,Spec fallback) {}
    private static final class Slot {
        final int lifeId;RecoverySkill skill;Spec goal;Request request;Pending pending;int failures,completed,progressIndex=-1;double retryAt,anchorAt,progressAt;Double waterSince,drySince,lowSince;Vector anchor,progressPosition;String lastReason="";final LinkedHashMap<Cell,Double> avoid=new LinkedHashMap<>();
        Slot(int lifeId) {this.lifeId=lifeId;}
    }
    public record Event(double at,int playerId,String purpose,String reason,int index,int steps,String kind,Cell source,Cell destination,Vector position,boolean grounded,boolean wade) {}
    public record State(int lifeId,boolean active,String pending,int failures,double retryAt,Double waterSince,Double drySince,Vector anchor,double anchorAt,Double lowSince,int completed,String lastReason,int avoidCount,int progressIndex,Vector progressPosition,double progressAt) {}
    private final Thread owner=Thread.currentThread();private final BotNavigationWorld world;private final World collision;private final DoubleSupplier clock;private final LinkedHashMap<Observer,Slot> slots=new LinkedHashMap<>();private final Map<String,Integer> metrics=new LinkedHashMap<>();private final ArrayList<Event> events=new ArrayList<>();
    public RecoveryDriver(BotNavigationWorld world) {this(world,()->System.nanoTime()/1e9);}
    public RecoveryDriver(BotNavigationWorld world,DoubleSupplier clock) {this.world=world;collision=reader(world);this.clock=clock;}
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Recovery driver outside worker");}
    private static Observer key(Player p) {return new Observer(p.id(),p.generation());}
    private Slot slot(Player p) {
        owned();var key=key(p);var slot=slots.get(key);if(slot==null||slot.lifeId!=p.lifeId()) {slot=new Slot(p.lifeId());slots.put(key,slot);if(slots.size()>256)slots.remove(slots.keySet().iterator().next());}return slot;
    }
    public void reset() {owned();slots.clear();}public void forget(int id,int generation) {owned();slots.remove(new Observer(id,generation));}
    private void count(String name) {metrics.merge(name,1,Integer::sum);}
    public Map<String,Integer> metrics() {owned();return Collections.unmodifiableMap(new LinkedHashMap<>(metrics));}public List<Event> events() {owned();return List.copyOf(events);}public int slotCount() {owned();return slots.size();}
    public State snapshot(Player p) {owned();var s=slots.get(key(p));return s==null?null:new State(s.lifeId,s.skill!=null,s.pending==null?"":s.pending.purpose(),s.failures,s.retryAt,s.waterSince,s.drySince,s.anchor,s.anchorAt,s.lowSince,s.completed,s.lastReason,s.avoid.size(),s.progressIndex,s.progressPosition,s.progressAt);}
    public boolean active(Player p) {owned();var s=slots.get(key(p));return s!=null&&s.lifeId==p.lifeId()&&(s.skill!=null||s.pending!=null&&s.goal!=null);}
    public void cancel(Player p) {var s=slot(p);s.skill=null;s.goal=null;s.request=null;s.pending=null;}
    private static String tuple(Cell c) {return "("+c.x()+", "+c.y()+", "+c.z()+")";}
    public String describe(Player p) {
        owned();var s=slots.get(key(p));if(s==null)return "";if(s.skill==null)return "idle(last="+s.lastReason+")"+(s.pending==null?"":" searching="+s.pending.purpose());
        var skill=s.skill;var step=skill.current();String detail=step==null?"":" "+step.kind().name().toLowerCase(Locale.ROOT)+" "+tuple(step.source())+"->"+tuple(step.destination())+" dig="+step.digCells().size()+" build="+step.buildCells().size();return skill.purpose()+":"+skill.index()+"/"+skill.plan().steps().size()+detail+" last="+s.lastReason;
    }
    private Search searchFor(Spec spec,Cell start,Abilities abilities,Set<Cell> avoid) {
        Goal target;if(spec.kind().equals("main"))target=Goal.mainGround(world,start,28,12);else if(spec.kind().equals("toward"))target=Goal.toward(spec.target(),spec.radius());else return null;
        return target==null?null:new Search(collision,start,target,abilities,spec.kind().equals("main")?28:24,2500,true,null,avoid,clock);
    }
    private Command begin(Slot s,Player p,BotProfile profile,double now,String purpose,Spec spec,Predicate<Plan> accept,Spec fallback,Set<Cell> avoid) {
        var search=searchFor(spec,node(p.position()),Abilities.from(p),avoid);
        if(search==null) {
            if(fallback!=null)return begin(s,p,profile,now,purpose,fallback,accept,null,avoid);
            count(purpose+"_no_plan");s.goal=null;s.retryAt=now+3;return null;
        }
        s.goal=spec;s.pending=new Pending(search,purpose,spec,now,accept,fallback);return advance(s,p,profile,now);
    }
    private Command advance(Slot s,Player p,BotProfile profile,double now) {
        var pending=s.pending;if(pending==null)return null;String status=pending.search().advance(.012);
        if(status.equals("pending")) {if(now-pending.startedAt()>5) {s.pending=null;s.goal=null;s.retryAt=now+3;count(pending.purpose()+"_search_timeout");}return null;}
        s.pending=null;var plan=pending.search().plan();
        if(status.equals("found")&&plan!=null&&(pending.accept()==null||pending.accept().test(plan))) {start(s,p,profile,plan,pending.purpose(),now,pending.spec());return s.skill.update(p,collision,now,Abilities.from(p));}
        if(pending.fallback()!=null)return begin(s,p,profile,now,pending.purpose(),pending.fallback(),pending.accept(),null,Set.copyOf(s.avoid.keySet()));
        count(pending.purpose()+"_no_plan");s.goal=null;s.retryAt=now+3;return null;
    }
    private void event(Slot s,Player p,RecoverySkill skill,String why,double now,boolean failed) {
        var step=failed?skill.current():null;var at=p.position();
        events.add(new Event(rounded(now,2),p.id(),skill.purpose(),why,skill.index(),skill.plan().steps().size(),step==null?"":step.kind().name().toLowerCase(Locale.ROOT),step==null?null:step.source(),step==null?null:step.destination(),new Vector(rounded(at.x(),2),rounded(at.y(),2),rounded(at.z(),2)),p.grounded(),p.wade()));
        if(events.size()>64)events.subList(0,events.size()-64).clear();
    }
    public Command step(Player p,BotProfile profile,double now) {
        var s=slot(p);var skill=s.skill;if(skill==null)return s.pending!=null&&s.goal!=null?advance(s,p,profile,now):null;
        if(!p.alive()) {s.skill=null;return null;}var abilities=Abilities.from(p);
        if(s.progressIndex!=skill.index()||s.progressPosition==null||flat(p.position(),s.progressPosition)>=.75||Math.abs(p.position().z()-s.progressPosition.z())>=1.5) {s.progressIndex=skill.index();s.progressPosition=p.position();s.progressAt=now;}
        if(now-s.progressAt>(p.wade()?4:7)) {
            count(skill.purpose()+"_stalled");event(s,p,skill,"stalled",now,false);s.skill=null;s.goal=null;s.request=null;s.failures=0;s.progressPosition=null;s.retryAt=now+8;return null;
        }
        var command=skill.update(p,collision,now,abilities);if(command.status().equals("running"))return command;
        if(command.status().equals("done")) {s.skill=null;s.goal=null;s.request=null;s.failures=0;s.completed++;s.anchor=p.position();s.anchorAt=now;s.lowSince=null;count(skill.purpose()+"_done");return command;}
        s.failures++;s.lastReason=command.reason();event(s,p,skill,command.reason(),now,true);count(skill.purpose()+"_fail_"+command.reason());s.skill=null;
        if(skill.blockedCell()!=null)s.avoid.put(skill.blockedCell(),now+20);s.avoid.entrySet().removeIf(e->e.getValue()<=now);var goal=s.goal;
        if(p.wade()&&command.reason().equals("step_timeout"))s.failures=4;
        if(s.failures>3||goal==null) {s.goal=null;s.request=null;s.retryAt=now+8;s.failures=0;return null;}
        if(goal.kind().equals("request")||goal.kind().equals("bridge")) {
            var plan=goal.kind().equals("request")?planRequest(p,goal.request(),abilities):gapBridge(collision,p.position(),goal.target(),abilities,16,2);
            if(plan==null) {s.goal=null;s.retryAt=now+8;return null;}start(s,p,profile,plan,skill.purpose(),now,goal);return s.skill.update(p,collision,now,abilities);
        }
        return begin(s,p,profile,now,skill.purpose(),goal,null,null,Set.copyOf(s.avoid.keySet()));
    }
    private void start(Slot s,Player p,BotProfile profile,Plan plan,String purpose,double now,Spec goal) {
        double skill=profile==null?.5:profile.skill(),reaction=profile==null?.22:profile.reactionTime();
        s.skill=new RecoverySkill(plan,purpose,now,p.id()*7919+p.lifeId(),skill,Math.max(.05,Math.min(.6,reaction)),skill>=.45,now);s.goal=goal;s.progressIndex=-1;s.progressPosition=null;s.progressAt=now;count(purpose+"_start");
    }
    public Command considerWater(Player p,BotProfile profile,double now,Vector goal,double swimSeconds,boolean recovering) {
        var s=slot(p);if(s.skill!=null)return step(p,profile,now);if(s.waterSince==null)s.waterSince=now;s.drySince=null;if(now-s.waterSince>swimSeconds+8)swimSeconds=Double.POSITIVE_INFINITY;
        if(s.pending!=null)return advance(s,p,profile,now);if(now<s.retryAt||now-s.waterSince<.6||!recovering&&now-s.waterSince<6)return null;
        var a=Abilities.from(p);var start=node(p.position());if(start.z()<239)start=new Cell(start.x(),start.y(),239);double quick=quickClimb(world,start,a,28);
        if(!Double.isFinite(quick)||quick>swimSeconds*1.3+3) {s.retryAt=now+2;return null;}
        final double swim=swimSeconds;var here=p.position();Predicate<Plan> accept=plan->{
            if(plan.seconds()*1.2+2>swim)return false;
            if(goal!=null&&plan.destination()!=null&&Double.isFinite(swim)) {double near=flat(goal,here);var d=plan.destination();double far=Math.hypot(goal.x()-d.x()-.5,goal.y()-d.y()-.5);if(far>near+12)return false;}return true;
        };
        return begin(s,p,profile,now,"climb_out",new Spec("main",null,0,null),accept,null,Set.copyOf(s.avoid.keySet()));
    }
    public void leftWater(Player p,Double now) {var s=slot(p);if(s.waterSince==null)return;if(now==null) {s.waterSince=null;return;}if(s.drySince==null)s.drySince=now;else if(now-s.drySince>=3) {s.waterSince=null;s.drySince=null;}}
    public void restartStuckClock(Player p,double now) {var s=slot(p);if(s.skill==null&&s.pending==null) {s.anchor=p.position();s.anchorAt=now;}}
    public boolean climbAwaitsWalkAnswer(Player p,double now,Vector goal) {
        owned();var s=slots.get(key(p));if(s==null||s.lifeId!=p.lifeId()||goal==null||s.skill!=null||s.pending!=null||s.anchor==null||!p.grounded()||p.wade()||now<s.retryAt)return false;
        int support=(int)Math.rint(goal.z()+2.25);return now-s.anchorAt>=6&&support<=node(p.position()).z()-3&&flat(p.position(),goal)<=40;
    }
    public Command considerStuck(Player p,BotProfile profile,double now,Vector goal,boolean climbToGoal) {
        var s=slot(p);if(s.skill!=null)return step(p,profile,now);if(s.pending!=null)return advance(s,p,profile,now);var pos=p.position();
        if(s.anchor==null||flat(pos,s.anchor)>=2.5||Math.abs(pos.z()-s.anchor.z())>=1.5) {s.anchor=pos;s.anchorAt=now;}
        if(!p.grounded()||p.wade()||goal==null||now<s.retryAt)return null;var start=node(pos);var atlas=world.atlas().orElse(null);boolean low=false,stranded=false;
        if(atlas!=null&&atlas.mainRegionId()!=0) {
            int i=start.y()*atlas.width()+start.x(),region=atlas.region(i),gi=(int)Math.floor(goal.y())*atlas.width()+(int)Math.floor(goal.x()),goalRegion=gi>=0&&gi<atlas.area()?atlas.region(gi):-1,main=atlas.mainRegionId(),primary=atlas.primary(i);
            boolean sea=start.z()>=235;stranded=region!=main&&region!=goalRegion&&sea||sea&&primary<start.z()-6;low=stranded||primary<start.z()-2&&region==main&&atlas.layerCount(i)<=1;
        }
        if(stranded) {if(s.lowSince==null)s.lowSince=now;}else s.lowSince=null;
        boolean above=(int)Math.rint(goal.z()+2.25)<=start.z()-3&&flat(pos,goal)<=40;
        if(above&&!climbToGoal)return null;boolean stalled=now-s.anchorAt>=6,pocket=s.lowSince!=null&&now-s.lowSince>=5;
        if(!(stalled&&(above||low)||pocket))return null;
        count("stuck_considered");s.anchorAt=now;Spec main=low?new Spec("main",null,0,null):null,first=above?new Spec("toward",goal,2,null):main;
        if(first==null) {s.retryAt=now+4;return null;}
        return begin(s,p,profile,now,"pit_climb",first,plan->plan.rise()>=1,first!=main?main:null,Set.copyOf(s.avoid.keySet()));
    }
    public Command considerGap(Player p,BotProfile profile,double now,Vector goal,int maximum) {
        var s=slot(p);if(s.skill!=null)return step(p,profile,now);if(s.pending!=null)return advance(s,p,profile,now);if(now<s.retryAt||!p.grounded()||p.wade())return null;
        var plan=gapBridge(collision,p.position(),goal,Abilities.from(p),maximum,2);if(plan==null) {s.retryAt=now+1.5;return null;}
        start(s,p,profile,plan,"bridge",now,new Spec("bridge",goal,0,null));return step(p,profile,now);
    }
    public Command request(Player p,BotProfile profile,Request request,double now) {
        var s=slot(p);boolean same=sameRequest(s.request,request);
        if(same&&s.skill!=null)return step(p,profile,now);if(same&&s.pending!=null)return advance(s,p,profile,now);if(now<s.retryAt&&same||requestSatisfied(p,request))return null;
        s.request=request;
        if(request.kind().equals("climb")&&request.target()!=null)return begin(s,p,profile,now,"climb",new Spec("toward",request.target(),1.5,null),null,null,Set.copyOf(s.avoid.keySet()));
        var plan=planRequest(p,request,Abilities.from(p));if(plan==null) {s.retryAt=now+2;return null;}
        String purpose=switch(request.kind()) {case "pillar_up"->"pillar_up";case "dig_staircase_up"->"staircase";case "bridge"->"bridge";default->"climb";};
        start(s,p,profile,plan,purpose,now,new Spec("request",null,0,request));return step(p,profile,now);
    }
    private Plan planRequest(Player p,Request r,Abilities a) {
        var start=node(p.position());var here=p.position();
        if(r.kind().equals("pillar_up")) {int levels=r.levels();if(r.target()!=null)levels=Math.max(r.levels()==0?levels:0,start.z()-(int)Math.rint(r.target().z()+2.25));return primitive(collision,start,ZERO,Math.max(1,levels),a,Kind.PILLAR);}
        if(r.kind().equals("dig_staircase_up")||r.kind().equals("bridge")) {
            var direction=r.direction();if(direction==null&&r.target()!=null)direction=new Vector(r.target().x()-here.x(),r.target().y()-here.y(),0);if(direction==null)direction=p.orientation();
            return primitive(collision,start,direction,Math.max(1,r.kind().equals("bridge")?r.length():r.levels()),a,r.kind().equals("bridge")?Kind.BRIDGE:Kind.STAIR);
        }
        if(r.target()==null)return null;var search=new Search(collision,start,Goal.toward(r.target(),1.5),a,24,2500,true,null,Set.of(),clock);return search.advance(.02).equals("found")?search.plan():null;
    }
    public static boolean sameRequest(Request a,Request b) {return a!=null&&b!=null&&a.kind().equals(b.kind())&&(a.target()==null||b.target()==null?a.target()==null&&b.target()==null:distance(a.target(),b.target())<=2.5);}
    public static boolean requestSatisfied(Player p,Request r) {if(r.target()==null||r.kind().equals("bridge"))return false;var start=node(p.position());if(r.kind().equals("climb"))return Goal.toward(r.target(),1.5).accepts().test(start);return p.grounded()&&start.z()<=(int)Math.rint(r.target().z()+2.25);}
    public static double swimSecondsToShore(BotNavigationWorld world,Player p,Vector goal) {
        var atlas=world.atlas().orElse(null);if(atlas==null)return Double.POSITIVE_INFINITY;var r=atlas.waterRoute((int)Math.floor(p.position().x()),(int)Math.floor(p.position().y()));
        if(r==null||!r.climbable()||atlas.region(r.goalY()*atlas.width()+r.goalX())!=atlas.mainRegionId())return Double.POSITIVE_INFINITY;return r.distance()*.34+1.5;
    }
}
