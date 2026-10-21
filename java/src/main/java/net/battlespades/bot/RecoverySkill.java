// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotVectors.*;
import static net.battlespades.bot.RecoveryPlanning.*;

/** Live terrain revalidation and ordinary input proposals for one recovery plan. */
public final class RecoverySkill {
    public record Command(String status,String role,Vector direction,boolean jump,boolean crouch,boolean sneak,boolean sprint,
                          Affordance affordance,Vector look,int toolId,Action action,boolean secondary,String reason) {}
    public record Attempt(Cell cell,int count) {}
    public record State(int index,double stepStartedAt,String phase,double nextSwingAt,double nextBuildAt,double pauseUntil,
                        List<Attempt> attempts,int buildAttempts,double jumpedAt,int swings,int builds,String reason,boolean backwards,
                        double feedbackAt,double alignSince,Cell blockedCell) {public State {attempts=List.copyOf(attempts);}}
    private final Thread owner=Thread.currentThread();private final Plan plan;private final String purpose;private final double startedAt,skill,reaction;private final int identity;private final boolean backwards;
    private int index,buildAttempts,swings,builds;private double stepStartedAt,nextSwingAt,nextBuildAt,pauseUntil,jumpedAt,feedbackAt,alignSince;private String phase="",reason="";private Cell blockedCell;private final Map<Cell,Integer> attempts=new HashMap<>();
    public RecoverySkill(Plan plan,String purpose,double startedAt,int identity,double skill,double reaction,boolean backwards,double stepStartedAt) {
        this.plan=Objects.requireNonNull(plan);this.purpose=Objects.requireNonNull(purpose);this.startedAt=startedAt;this.identity=identity;this.skill=skill;this.reaction=reaction;this.backwards=backwards;this.stepStartedAt=stepStartedAt;
    }
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Recovery skill outside worker");}
    public Plan plan() {owned();return plan;}public String purpose() {owned();return purpose;}public int index() {owned();return index;}public Cell blockedCell() {owned();return blockedCell;}
    public Step current() {owned();return index<plan.steps().size()?plan.steps().get(index):null;}
    public State snapshot() {owned();return new State(index,stepStartedAt,phase,nextSwingAt,nextBuildAt,pauseUntil,attempts.entrySet().stream().sorted(Map.Entry.comparingByKey(CELLS)).map(e->new Attempt(e.getKey(),e.getValue())).toList(),buildAttempts,jumpedAt,swings,builds,reason,backwards,feedbackAt,alignSince,blockedCell);}
    static final Comparator<Cell> CELLS=Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z);
    public static double mix(long... values) {long value=0x9e3779b9L;for(long item:values) {value=((value^(item&0xffff_ffffL))*0x01000193)&0xffff_ffffL;value^=value>>>15;}return (value&65535)/65535.;}
    private double jitter(int salt) {return mix(identity,index,salt,swings,builds);}
    public static Command status(String status,String role,String reason) {return new Command(status,role,ZERO,false,false,false,false,Affordance.WALK,null,-1,Action.none(),false,reason);}
    static Command motion(String role,Vector direction,boolean jump,boolean crouch,boolean sneak,boolean sprint,Affordance affordance,Vector look,int tool,Action action,boolean secondary) {return new Command("running",role,direction,jump,crouch,sneak,sprint,affordance,look,tool,action,secondary,"");}
    static Action action(ActionKind kind,int tool,Vector position,Vector end) {return new Action(kind,tool,position,end,"",-1,0,0,0,0);}
    private Command fail(String why) {reason=why;return status("failed",purpose+":failed",why);}
    public Command update(Player p,World w,double now,Abilities a) {
        owned();var pos=p.position();var body=node(pos);boolean wading=p.wade(),settled=p.grounded()||wading;catchUp(p,w);
        for(int i=0;i<3;i++) {
            var step=current();if(step==null)return status("done",purpose+":done","");
            if(!arrived(step,p,w))break;
            index++;stepStartedAt=now;phase="";attempts.clear();buildAttempts=0;alignSince=0;var following=current();
            if(following!=null&&(!following.digCells().isEmpty()||!following.buildCells().isEmpty()||following.kind()!=step.kind()))pauseUntil=now+reaction*(.15+.35*jitter(1));
        }
        var step=current();if(step==null)return status("done",purpose+":done","");if(stepStartedAt<=0)stepStartedAt=now;
        var source=step.source();var dest=step.destination();double horizontal=flat(pos,position(source));
        for(int i=index;i<Math.min(plan.steps().size(),index+6);i++)horizontal=Math.min(horizontal,flat(pos,position(plan.steps().get(i).destination())));
        boolean atColumn=body.x()==dest.x()&&body.y()==dest.y(),standing=p.grounded()&&!wading;
        if(horizontal>1.9&&!atColumn||standing&&Math.abs(body.z()-source.z())>1&&Math.abs(body.z()-dest.z())>1)return fail("off_plan");
        if(wading&&source.z()<237&&dest.z()<237&&pos.z()>source.z()-2.25+1.5)return fail("fell");
        if(now-stepStartedAt>timeout(step,a)) {blockedCell=dest;return fail("step_timeout");}
        boolean nearWater=pos.z()>=238-4.25-.05;String role=(wading||nearWater?"water_":"")+purpose+":"+step.kind().name().toLowerCase(Locale.ROOT);
        var remaining=new ArrayList<Cell>();for(var c:step.digCells())if(w.solid(c))remaining.add(c);
        if(step.kind()!=Kind.PILLAR)for(var c:corridor(step))if(w.solid(c)&&!remaining.contains(c))remaining.add(c);
        if(!remaining.isEmpty())return dig(p,w,now,a,step,remaining,role);
        if(step.kind()!=Kind.PILLAR&&!w.solid(dest))return layFloor(p,w,now,a,step,dest,role);
        if(now<pauseUntil)return motion(role+"_beat",ZERO,false,false,false,false,affordance(step,wading),gaze(step,p),-1,Action.none(),false);
        return step.kind()==Kind.PILLAR?pillar(p,w,now,a,step,role,settled):walk(p,now,step,role,wading);
    }
    private void catchUp(Player p,World w) {
        var body=node(p.position());if(!p.grounded()&&!p.wade())return;
        for(int i=index;i<Math.min(plan.steps().size(),index+8);i++) {
            var s=plan.steps().get(i);if(s.kind()!=Kind.WALK&&s.kind()!=Kind.SWIM||!s.digCells().isEmpty()||!s.buildCells().isEmpty())return;var d=s.destination();
            if(body.x()==d.x()&&body.y()==d.y()&&Math.abs(body.z()-d.z())<=1&&w.solid(d)&&i>index) {index=i;stepStartedAt=0;return;}
        }
    }
    private void feedback(Player p) {if((p.lastActionKind().equals("build")||p.lastActionKind().equals("build_line"))&&p.lastActionAt()>feedbackAt) {feedbackAt=p.lastActionAt();if(!p.lastActionAccepted())buildAttempts++;}}
    private static boolean arrived(Step s,Player p,World w) {
        var d=s.destination();var n=node(p.position());if(n.x()!=d.x()||n.y()!=d.y()||!w.solid(d))return false;double error=p.position().z()+2.25-d.z();
        return d.z()>=239?p.wade()||Math.abs(error)<=.8:(p.grounded()||p.wade())&&error>=-.45&&error<=1;
    }
    private static List<Cell> corridor(Step s) {var out=new ArrayList<>(body(s.destination()));if(s.kind()==Kind.STAIR)out.add(new Cell(s.source().x(),s.source().y(),s.source().z()-4));return out.stream().filter(RecoveryPlanning::editable).toList();}
    private static double timeout(Step s,Abilities a) {var p=a.dig();double swing=p==null?0:p.fireInterval()*Math.max(1,p.swingsPerBlock());return 3.5+(s.digCells().size()+(s.kind()==Kind.STAIR?1:0))*swing*2.5+s.buildCells().size()*1.5;}
    private Set<Cell> protectedCells(Step s,BotDigProfile p) {
        var out=new HashSet<Cell>();out.add(s.source());out.add(s.destination());
        if(p!=null&&p.pattern().equals("cube"))return out;
        if(index+1<plan.steps().size()) {var next=plan.steps().get(index+1);if(next.kind()!=Kind.PILLAR)out.add(next.destination());}return out;
    }
    private Command dig(Player p,World w,double now,Abilities a,Step step,List<Cell> remaining,String role) {
        var profile=a.dig();if(profile==null)return fail("no_dig_tool");
        if(flat(p.position(),position(step.source()))>1.25)return steer(p,position(step.source()),role+"_align",p.wade(),.25,true);
        var protectedCells=protectedCells(step,profile);protectedCells.add(node(p.position()));var aim=visibleAim(w,p.eye(),remaining,protectedCells,profile);if(aim==null)return fail("dig_unreachable");
        int count=attempts.getOrDefault(aim,0),limit=profile.swingsForHealth(Math.max(9,w.health(aim)))+3;if(count>limit)return fail("dig_rejected");
        var action=Action.none();if(now>=nextSwingAt) {action=action(ActionKind.MELEE,profile.toolId(),centre(aim),null);attempts.put(aim,count+1);swings++;nextSwingAt=now+Math.max(.05,profile.fireInterval())+(.03+.12*(1.2-skill))*jitter(7);}
        return motion(role+"_dig",ZERO,false,false,false,false,Affordance.BREACH,centre(aim),profile.toolId(),action,profile.secondary());
    }
    public static Cell visibleAim(World w,Vector eye,List<Cell> remaining,Set<Cell> protectedCells,BotDigProfile p) {
        var required=new HashSet<>(remaining);var pool=new HashSet<>(remaining);if(!p.pattern().equals("single"))for(var c:remaining)for(int dz:new int[]{-1,1})pool.add(new Cell(c.x(),c.y(),c.z()+dz));
        Cell best=null;int coverage=-1,collateral=Integer.MAX_VALUE;double nearest=Double.POSITIVE_INFINITY;
        for(var aim:pool) {
            if(!w.solid(aim)||!editable(aim))continue;var footprint=p.footprint(aim);if(!Collections.disjoint(footprint,protectedCells))continue;
            int covered=(int)footprint.stream().filter(required::contains).count();if(covered<=0)continue;
            double gap=distance(eye,centre(aim));if(gap>3.6||!aim.equals(firstSolid(w,eye,centre(aim),6)))continue;
            int extra=(int)footprint.stream().filter(c->!required.contains(c)&&w.solid(c)).count();
            if(covered>coverage||covered==coverage&&(extra<collateral||extra==collateral&&(gap<nearest||gap==nearest&&(best==null||CELLS.compare(aim,best)>0)))) {best=aim;coverage=covered;collateral=extra;nearest=gap;}
        }
        if(best!=null)return best;var sorted=new ArrayList<>(remaining);sorted.sort(Comparator.comparingDouble(c->distance(eye,centre(c))));
        for(var c:sorted) {var blocker=firstSolid(w,eye,centre(c),6);if(blocker==null||protectedCells.contains(blocker)||!editable(blocker)||distance(eye,centre(blocker))>3.6||!Collections.disjoint(p.footprint(blocker),protectedCells))continue;return blocker;}return null;
    }
    private Command layFloor(Player p,World w,double now,Abilities a,Step step,Cell floor,String role) {
        if(!a.canBuild()||a.blocks()<1)return fail("no_blocks");
        if(!faceSupported(w,floor,List.of(),List.of())) {var support=supportFor(w,step,floor);if(support==null||a.blocks()<2)return fail("floor_unsupported");floor=support;}
        feedback(p);if(buildAttempts>4)return fail("build_rejected");if(step.kind()==Kind.BRIDGE)return bridge(p,w,now,a,step,floor,role);
        if(overlaps(p.position(),floor,.03)&&p.wade()) {
            var source=position(step.source());var target=new Vector(source.x()+(step.source().x()-floor.x())*.25,source.y()+(step.source().y()-floor.y())*.25,source.z());
            var c=steer(p,target,role+"_align",true,.1,false);return motion(role+"_align",c.direction(),false,false,false,false,Affordance.SWIM,centre(floor),BLOCK_TOOL,Action.none(),false);
        }
        if(overlaps(p.position(),floor,.03)) {
            boolean clear=p.position().z()<floor.z()-2-(p.wade()?.02:.1),settled=p.grounded()||p.wade(),jump=false;var action=Action.none();
            if(clear&&now>=nextBuildAt) {action=action(ActionKind.BUILD,BLOCK_TOOL,coordinates(floor),null);nextBuildAt=now+.18;builds++;}
            else if(settled&&!clear&&now-jumpedAt>.32) {jump=true;jumpedAt=now;}
            return motion(role+"_hop_floor",ZERO,jump||p.wade(),false,false,false,Affordance.BUILD_STEP,centre(floor),BLOCK_TOOL,action,false);
        }
        var action=Action.none();if(now>=nextBuildAt) {action=action(ActionKind.BUILD,BLOCK_TOOL,coordinates(floor),null);nextBuildAt=now+.3+.2*jitter(11);builds++;}
        return motion(role+"_floor",ZERO,false,false,false,false,Affordance.BUILD_STEP,centre(floor),BLOCK_TOOL,action,false);
    }
    private static Cell supportFor(World w,Step s,Cell floor) {
        var clear=new HashSet<>(body(s.source()));clear.addAll(body(s.destination()));int x=floor.x(),y=floor.y(),z=floor.z();
        for(var n:List.of(new Cell(x,y,z+1),new Cell(x+1,y,z),new Cell(x-1,y,z),new Cell(x,y+1,z),new Cell(x,y-1,z)))if(!clear.contains(n)&&editable(n)&&!w.solid(n)&&faceSupported(w,n,List.of(),List.of()))return n;return null;
    }
    private Command bridge(Player p,World w,double now,Abilities a,Step s,Cell floor,String role) {
        var line=new ArrayList<Cell>();line.add(floor);int maximum=Math.max(1,Math.min(2+(int)Math.rint(2*skill),a.blocks()));
        for(int i=index+1;i<plan.steps().size();i++) {var later=plan.steps().get(i);if(later.kind()!=Kind.BRIDGE||line.size()>=maximum)break;var c=later.buildCells().getFirst();if(w.solid(c))break;line.add(c);}
        int dx=s.destination().x()-s.source().x(),dy=s.destination().y()-s.source().y();var source=s.source();var look=bridgeLook(p,line.getFirst(),dx,dy);var body=node(p.position());double vx=p.velocity().x(),vy=p.velocity().y(),speed=Math.hypot(vx,vy);
        if(body.x()!=source.x()||body.y()!=source.y()) {var c=steer(p,position(source),role+"_recover",false,.1,true);return motion(role+"_recover",c.direction(),false,false,true,false,Affordance.WALK,look,BLOCK_TOOL,Action.none(),false);}
        if(speed>.06)return motion(role+"_brake",new Vector(-vx/speed,-vy/speed,0),false,false,false,false,Affordance.WALK,look,BLOCK_TOOL,Action.none(),false);
        var action=Action.none();if(now>=nextBuildAt&&settledOn(p,source)) {action=action(ActionKind.BUILD_LINE,BLOCK_TOOL,coordinates(line.getFirst()),coordinates(line.getLast()));nextBuildAt=now+.25+.15*jitter(13);builds++;}
        var target=new Vector(source.x()+.5+dx*.25,source.y()+.5+dy*.25,p.position().z());var command=steer(p,target,role+"_lay",false,.12,true);boolean crouch=action.kind()!=ActionKind.NONE&&!overlaps(p.position(),line.getFirst(),.15);
        return motion(role+"_lay",command.direction(),false,crouch,true,false,Affordance.WALK,look,BLOCK_TOOL,action,false);
    }
    private Vector bridgeLook(Player p,Cell cell,int dx,int dy) {return backwards?new Vector(p.eye().x()-dx*.55,p.eye().y()-dy*.55,p.eye().z()+2.4):centre(cell);}
    private Command pillar(Player p,World w,double now,Abilities a,Step step,String role,boolean settled) {
        var source=step.source();int x=source.x(),y=source.y(),s=source.z();var floor=new Cell(x,y,s-1);
        var look=new Vector(x+.5+.08*(jitter(17)-.5),y+.5+.08*(jitter(19)-.5),s-.6);var centering=toward(p.position(),position(source),.18);
        if(w.solid(floor))return motion(role+"_land",centering,false,false,false,false,Affordance.BUILD_STEP,look,BLOCK_TOOL,Action.none(),false);
        if(!a.canBuild()||a.blocks()<1)return fail("no_blocks");feedback(p);if(buildAttempts>6)return fail("build_rejected");
        double z=p.position().z();boolean rising=p.velocity().z()<.02,clear=z<s-1-2-(p.wade()?.02:.1);var action=Action.none();
        if(clear&&now>=nextBuildAt&&(rising||z<s-3.6)) {
            var following=index+1<plan.steps().size()?plan.steps().get(index+1):null;var second=new Cell(x,y,s-2);
            if(following!=null&&following.kind()==Kind.PILLAR&&following.digCells().isEmpty()&&a.blocks()>=2&&skill>=.55&&rising&&z<s-2-2-.15&&!w.solid(second)&&!w.solid(x,y,s-5))action=action(ActionKind.BUILD_LINE,BLOCK_TOOL,coordinates(floor),coordinates(second));
            else action=action(ActionKind.BUILD,BLOCK_TOOL,coordinates(floor),null);
            nextBuildAt=now+.18;builds++;
        }
        boolean jump=false;if(settled&&!clear&&now-jumpedAt>.32) {jump=true;jumpedAt=now;}
        return motion(role+(action.kind()!=ActionKind.NONE?"_place":"_jump"),centering,jump||p.wade(),false,false,false,Affordance.BUILD_STEP,look,BLOCK_TOOL,action,false);
    }
    private Command walk(Player p,double now,Step step,String role,boolean wading) {
        var last=step;if(step.kind()==Kind.SWIM||step.kind()==Kind.WALK)for(int i=index+1;i<Math.min(plan.steps().size(),index+6);i++) {
            var later=plan.steps().get(i);if(later.kind()!=step.kind()||!later.digCells().isEmpty()||!later.buildCells().isEmpty()||later.destination().z()!=step.destination().z())break;last=later;
        }
        var target=position(last.destination());var following=index+1<plan.steps().size()?plan.steps().get(index+1):null;
        boolean workNext=following!=null&&(!following.digCells().isEmpty()||!following.buildCells().isEmpty()||following.kind()==Kind.PILLAR),arriving=flat(target,p.position())<.9;
        boolean careful=arriving&&(workNext&&step.kind()!=Kind.STAIR||step.kind()==Kind.STAIR&&!step.buildCells().isEmpty());var command=steer(p,target,role,wading,.2,last==step&&careful);
        boolean jump=wading&&step.destination().z()<step.source().z()||step.kind()==Kind.STAIR&&now-stepStartedAt>1.4&&p.grounded();boolean sprint=step.kind()==Kind.SWIM||step.kind()==Kind.WALK&&last!=step;
        return motion(role,command.direction(),jump,false,command.sneak(),sprint&&!command.sneak(),affordance(step,wading),gaze(step,p),-1,Action.none(),false);
    }
    private static Affordance affordance(Step step,boolean wading) {if(step.kind()==Kind.SWIM||step.destination().z()>=239)return Affordance.SWIM;if(wading&&step.kind()!=Kind.PILLAR)return Affordance.JUMP;return step.kind()==Kind.PILLAR?Affordance.BUILD_STEP:Affordance.WALK;}
    private Vector gaze(Step step,Player p) {
        if(step.kind()==Kind.PILLAR)return new Vector(step.source().x()+.5,step.source().y()+.5,step.source().z()-.6);
        if(step.kind()==Kind.BRIDGE) {int dx=step.destination().x()-step.source().x(),dy=step.destination().y()-step.source().y();return backwards?bridgeLook(p,step.destination(),dx,dy):new Vector(step.destination().x()+.5+dx,step.destination().y()+.5+dy,step.destination().z()+.2);}
        var target=plan.steps().get(Math.min(plan.steps().size()-1,index+2)).destination();return new Vector(target.x()+.5,target.y()+.5,target.z()-1.6);
    }
    private static Command steer(Player p,Vector target,String role,boolean wading,double tolerance,boolean sneak) {return motion(role,toward(p.position(),target,tolerance),false,false,sneak&&!wading,false,wading?Affordance.SWIM:Affordance.WALK,new Vector(target.x(),target.y(),p.eye().z()),-1,Action.none(),false);}
    static boolean overlaps(Vector p,Cell c,double margin) {double half=.45+margin;return Math.floor(p.x()-half)<=c.x()&&c.x()<=Math.floor(p.x()+half)&&Math.floor(p.y()-half)<=c.y()&&c.y()<=Math.floor(p.y()+half);}
    static boolean settledOn(Player p,Cell n) {var body=node(p.position());double error=p.position().z()+2.25-n.z();return body.x()==n.x()&&body.y()==n.y()&&p.grounded()&&error>=-.5&&error<=1;}
    static Vector toward(Vector p,Vector target,double deadband) {double dx=target.x()-p.x(),dy=target.y()-p.y(),length=Math.hypot(dx,dy);return length<=deadband?ZERO:new Vector(dx/length,dy/length,0);}
}
