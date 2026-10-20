// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;

/** Bounded local A*: proposals are validated again by the future authoritative bot motor. */
final class BotRoutePlanner {
    static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1}};
    private final BotNavigationWorld world;
    BotRoutePlanner(BotNavigationWorld world) { this.world=world; }
    record Neighbor(Cell neighbor,Affordance affordance,double cost,Breach breach) {}
    private record QueueNode(double priority,long sequence,Cell cell) {}
    private record Clearance(Cell target,int swings) {}
    static Cell key(Surface s) { return new Cell(s.x(),s.y(),s.supportZ()); }
    static Vector position(Cell s) { return new Vector(s.x()+.5,s.y()+.5,s.z()-2.25); }
    static Edge edge(Surface a,Surface b) { return new Edge(key(a),key(b)); }
    private boolean solid(Cell c) { return world.solid(c.x(),c.y(),c.z()); }
    private boolean occupied(int x,int y,int low,int high) {
        for(int z=low;z<high;z++)if(world.solid(x,y,z))return true;
        return false;
    }
    BotNavigationWorld.RoutePlan plan(Vector start,Vector goal,Set<Affordance> abilities,BotDigProfile dig,
                                     boolean water,Set<Edge> blocked,PlanningBudget.Job job,boolean recover,boolean prefer) {
        var origin=world.surface((int)Math.floor(start.x()),(int)Math.floor(start.y()),start.z(),8,water);
        int expected=(int)Math.rint(start.z()+2.25);
        if(origin==null||Math.abs(origin.supportZ()-expected)>2) {
            var adjacent=adjacentStart(start,water);
            if(adjacent!=null&&(origin==null||Math.abs(adjacent.supportZ()-expected)<Math.abs(origin.supportZ()-expected)))origin=adjacent;
        }
        if(origin==null)return new BotNavigationWorld.RoutePlan(List.of(),false,0,false);
        if(dig!=null&&abilities.contains(Affordance.BREACH)&&prefer) {
            var traversal=plan(start,goal,abilities,null,water,blocked,job,false,true);
            if(traversal.deferred()||!traversal.steps().isEmpty())return traversal;
        }
        int tx=Math.max(origin.x()-64,Math.min(origin.x()+64,(int)Math.floor(goal.x())));
        int ty=Math.max(origin.y()-64,Math.min(origin.y()+64,(int)Math.floor(goal.y())));
        boolean local=tx==(int)Math.floor(goal.x())&&ty==(int)Math.floor(goal.y());
        var target=local?world.goalSurface(goal,water):world.surface(tx,ty,goal.z(),12,water);
        var startKey=key(origin);
        int limit=job==null?256:job.beginSearch(256);
        if(limit<=0)return new BotNavigationWorld.RoutePlan(List.of(),false,0,true);
        var queue=new PriorityQueue<QueueNode>(Comparator.comparingDouble(QueueNode::priority).thenComparingLong(QueueNode::sequence));
        queue.add(new QueueNode(0,0,startKey));
        var parent=new HashMap<Cell,Cell>();parent.put(startKey,null);
        var via=new HashMap<Cell,Neighbor>();
        var costs=new HashMap<Cell,Double>();costs.put(startKey,0.);
        long sequence=0;Cell best=startKey;double bestDistance=distance(best,tx,ty,target);boolean reached=false;int expansions=0;
        while(!queue.isEmpty()&&expansions<limit) {
            Cell current=queue.remove().cell();expansions++;if(job!=null)job.expanded();
            double distance=distance(current,tx,ty,target);
            if(distance<bestDistance) {best=current;bestDistance=distance;}
            if(current.x()==tx&&current.y()==ty&&(target==null||current.z()==target.supportZ())) {best=current;reached=true;break;}
            for(var n:neighbors(current,abilities,dig,water)) {
                if(!Double.isFinite(n.cost)||n.cost<0||blocked.contains(new Edge(current,n.neighbor)))continue;
                double cost=costs.get(current)+n.cost;
                if(cost>=costs.getOrDefault(n.neighbor,Double.POSITIVE_INFINITY))continue;
                costs.put(n.neighbor,cost);parent.put(n.neighbor,current);via.put(n.neighbor,n);
                queue.add(new QueueNode(cost+distance(n.neighbor,tx,ty,target),++sequence,n.neighbor));
            }
        }
        if(best.equals(startKey)) {
            double dz=origin.position().z()-start.z();
            if(recover&&((int)Math.floor(start.x())!=origin.x()||(int)Math.floor(start.y())!=origin.y()||Math.abs(dz)>.75)&&dz>=-.75&&dz<=2)
                return new BotNavigationWorld.RoutePlan(List.of(new RouteStep(origin.position(),Affordance.WALK)),false,expansions,false);
            return new BotNavigationWorld.RoutePlan(List.of(),false,expansions,false);
        }
        var nodes=new ArrayList<Cell>();for(Cell c=best;c!=null;c=parent.get(c))nodes.add(c);Collections.reverse(nodes);
        var steps=new ArrayList<RouteStep>();
        for(int i=1;i<nodes.size();i++) {
            var node=nodes.get(i);var n=via.get(node);var previous=nodes.get(i-1);
            steps.add(new RouteStep(n.affordance==Affordance.BREACH?position(node):wallWaypoint(node,previous),n.affordance,n.breach,
                n.affordance==Affordance.JUMP||n.affordance==Affordance.DROP?new Edge(previous,node):null));
        }
        return new BotNavigationWorld.RoutePlan(compact(steps,start),reached&&local,expansions,false);
    }
    private static double distance(Cell node,int x,int y,Surface goal) {
        double dx=node.x()-x,dy=node.y()-y,dz=goal==null?0:node.z()-goal.supportZ();return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    private Surface adjacentStart(Vector p,boolean water) {
        Surface best=null;double score=Double.POSITIVE_INFINITY;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++) {
            if(dx==0&&dy==0)continue;
            var s=world.surface((int)Math.floor(p.x())+dx,(int)Math.floor(p.y())+dy,p.z(),8,water);
            if(s==null)continue;
            double x=s.x()+.5-p.x(),y=s.y()+.5-p.y(),z=s.position().z()-p.z(),candidate=x*x+y*y+z*z;
            if(candidate<score) {score=candidate;best=s;}
        }
        return best;
    }
    private boolean wall(int x,int y,int z) {return world.solid(x,y,z-2)||world.solid(x,y,z-1);}
    private Vector wallWaypoint(Cell c,Cell previous) {
        double bx=c.y()!=previous.y()?(wall(c.x()-1,c.y(),c.z())?1:0)-(wall(c.x()+1,c.y(),c.z())?1:0):0;
        double by=c.x()!=previous.x()?(wall(c.x(),c.y()-1,c.z())?1:0)-(wall(c.x(),c.y()+1,c.z())?1:0):0;
        return new Vector(c.x()+.5+bx*.18,c.y()+.5+by*.18,c.z()-2.25);
    }
    private static void add(List<Neighbor> out,Cell cell,Affordance kind,double cost) {out.add(new Neighbor(cell,kind,cost,null));}
    private static void addBreach(List<Neighbor> out,Breach b,double base) {
        out.add(new Neighbor(b.destination(),Affordance.BREACH,base+1.5+b.estimatedSwings()*Math.max(.05,b.fireInterval())/.25,b));
    }
    List<Neighbor> neighbors(Cell node,Set<Affordance> abilities,BotDigProfile dig,boolean water) {
        var out=new ArrayList<Neighbor>();int x=node.x(),y=node.y(),z=node.z();
        for(var direction:CARDINAL) {
            int dx=direction[0],dy=direction[1],nx=x+dx,ny=y+dy;
            var sample=world.surface(nx,ny,z-2.25,8,2,water);
            if(sample!=null) {
                int delta=sample.supportZ()-z;var next=key(sample);
                if(water&&(z>=239||sample.supportZ()>=239)) {
                    if(Math.abs(delta)<=2)add(out,next,z>=239&&sample.supportZ()<239?Affordance.JUMP:Affordance.SWIM,2.75+Math.abs(delta)*.35);
                    continue;
                }
                boolean overhang=world.solid(nx,ny,sample.supportZ()-3)||delta>0&&world.solid(nx,ny,z-3);
                var ceiling=new ArrayList<Cell>();
                if(delta>=-2&&delta<0)for(int cz=z-3+delta;cz<z-2;cz++)if(world.solid(x,y,cz))ceiling.add(new Cell(x,y,cz));
                if(!ceiling.isEmpty()) {
                    if(dig!=null&&abilities.contains(Affordance.BREACH)) {
                        var clear=clearance(ceiling,z,dig);
                        if(clear.target!=null&&clear.swings>0&&!dig.footprint(clear.target).contains(node)&&!dig.footprint(clear.target).contains(next))
                            addBreach(out,breach(node,next,clear.target,ceiling,dig,clear.swings),1.25);
                    }
                    continue;
                }
                if(Math.abs(delta)<=1&&!overhang) {add(out,next,Affordance.WALK,1+Math.abs(delta)*.25);continue;}
                if(Math.abs(delta)<=1&&overhang&&abilities.contains(Affordance.CROUCH)) {add(out,next,Affordance.CROUCH,1.4+Math.abs(delta)*.25);continue;}
                if(delta==0&&overhang&&dig!=null&&abilities.contains(Affordance.BREACH)) {
                    var blockers=List.of(new Cell(nx,ny,z-3));var clear=clearance(blockers,z,dig);
                    if(clear.target!=null&&clear.swings>0) {addBreach(out,breach(node,next,clear.target,blockers,dig,clear.swings),1);continue;}
                }
                if(delta==-2&&abilities.contains(Affordance.JUMP)) {
                    if(!overhang)add(out,next,Affordance.JUMP,1.8+Math.abs(delta)*.35);
                    if(dig!=null&&abilities.contains(Affordance.BREACH))for(var b:breaches(node,nx,ny,dig))addBreach(out,b,1+Math.abs(b.destination().z()-z)*.25);
                    continue;
                }
                if(delta>=-8&&delta< -2&&abilities.contains(Affordance.JETPACK_CLIMB)&&z<238&&climbClear(x,y,z,sample.supportZ())) {
                    add(out,next,Affordance.JETPACK,3+Math.abs(delta)*.35);continue;
                }
                if(delta>1&&delta<=4&&abilities.contains(Affordance.DROP)&&!occupied(nx,ny,z-3,sample.supportZ())) {
                    add(out,next,Affordance.DROP,1.2+delta*.15);continue;
                }
            }
            if(abilities.contains(Affordance.CROUCH)) {
                var crouch=world.surface(nx,ny,z-2.25,1,1,water);
                if(crouch!=null&&Math.abs(crouch.supportZ()-z)<=1) {add(out,key(crouch),Affordance.CROUCH,1.4);continue;}
            }
            if(dig!=null&&abilities.contains(Affordance.BREACH)) {
                var choices=breaches(node,nx,ny,dig);
                for(var b:choices)addBreach(out,b,1+Math.abs(b.destination().z()-z)*.25);
                if(!choices.isEmpty())continue;
            }
            int maximum=abilities.contains(Affordance.JETPACK)?4:2;
            for(int length=2;length<=maximum;length++) {
                var landing=world.surface(x+dx*length,y+dy*length,z-2.25,8,2,water);
                if(landing==null)continue;
                int delta=landing.supportZ()-z;var target=key(landing);
                if(length==2&&Math.abs(delta)<=2&&abilities.contains(Affordance.JUMP)&&jumpClear(x,y,dx,dy,length,z,landing.supportZ())) {
                    add(out,target,Affordance.JUMP,2.6+Math.abs(delta)*.3);break;
                }
                if(delta==0&&abilities.contains(Affordance.JETPACK)&&flightClear(x,y,dx,dy,length,z,landing.supportZ())) {
                    add(out,target,Affordance.JETPACK,3.5+length*.5);break;
                }
            }
        }
        return List.copyOf(out);
    }
    private boolean climbClear(int x,int y,int source,int landing) {return landing-4>=1&&!occupied(x,y,landing-4,source);}
    private boolean flightClear(int x,int y,int dx,int dy,int length,int source,int landing) {
        int low=Math.min(source,landing)-9,high=Math.max(source,landing)-1;
        if(low<1||high>=238)return false;
        for(int offset=0;offset<=length;offset++)if(occupied(x+dx*offset,y+dy*offset,low,high+1))return false;
        return true;
    }
    private boolean jumpClear(int x,int y,int dx,int dy,int length,int source,int landing) {
        int highest=Math.min(source,landing);
        for(int offset=0;offset<=Math.max(1,length);offset++) {
            int floor=offset==0?source:offset==length?landing:highest;
            if(occupied(x+dx*offset,y+dy*offset,highest-4,floor))return false;
        }
        return true;
    }
    private List<Breach> breaches(Cell source,int x,int y,BotDigProfile profile) {
        var out=new ArrayList<Breach>();
        for(int z:new int[]{source.z(),source.z()-1,source.z()+1}) {
            if(z<2||z>=239||!world.solid(x,y,z))continue;
            var cells=new ArrayList<Cell>();for(int offset:new int[]{2,1})if(world.solid(x,y,z-offset))cells.add(new Cell(x,y,z-offset));
            if(cells.isEmpty())continue;
            var clear=clearance(cells,z,profile);
            if(clear.target!=null&&clear.swings>0)out.add(breach(source,new Cell(x,y,z),clear.target,cells,profile,clear.swings));
        }
        return out;
    }
    static Breach breach(Cell source,Cell destination,Cell target,List<Cell> blockers,BotDigProfile p,int swings) {
        return new Breach(source,destination,target,blockers,p.toolId(),p.secondary(),Math.max(.05,p.fireInterval()),swings);
    }
    private Clearance clearance(List<Cell> blockers,int support,BotDigProfile p) {
        if(blockers.isEmpty()||p.swingsPerBlock()<=0)return new Clearance(null,0);
        if(p.pattern().equals("single"))return new Clearance(blockers.stream().min(Comparator.comparingInt(Cell::z)).orElseThrow(),
            blockers.stream().mapToInt(c->p.swingsForHealth(world.blockHealth(c.x(),c.y(),c.z()))).sum());
        Cell floor=new Cell(blockers.getFirst().x(),blockers.getFirst().y(),support),best=null;int covered=0;
        for(var target:blockers) {
            if(!solid(target))continue;
            var footprint=p.footprint(target);if(footprint.contains(floor))continue;
            int count=(int)blockers.stream().filter(footprint::contains).count();
            if(count>0&&(count>covered||count==covered&&(best==null||target.z()<best.z()))) {best=target;covered=count;}
        }
        if(best==null)return new Clearance(null,0);
        var footprint=p.footprint(best);
        int swings=blockers.stream().filter(footprint::contains).mapToInt(c->p.swingsForHealth(world.blockHealth(c.x(),c.y(),c.z()))).max().orElse(0);
        return new Clearance(best,swings);
    }
    private static int sign(double delta) {return Math.abs(delta)>.25?(delta>0?1:-1):0;}
    static List<RouteStep> compact(List<RouteStep> steps,Vector start) {
        if(steps.size()<3)return List.copyOf(steps);
        var result=new ArrayList<RouteStep>();Vector previous=start;int[] old=null;
        for(int i=0;i<steps.size();i++) {
            var s=steps.get(i);var next=i+1<steps.size()?steps.get(i+1):null;
            int[] dir={sign(s.waypoint().x()-previous.x()),sign(s.waypoint().y()-previous.y())};
            int[] after=next==null?null:new int[]{sign(next.waypoint().x()-s.waypoint().x()),sign(next.waypoint().y()-s.waypoint().y())};
            if(next==null||!Arrays.equals(dir,after)||next.affordance()!=s.affordance()||s.affordance()!=Affordance.WALK||old!=null&&!Arrays.equals(old,dir)||Math.abs(s.waypoint().z()-previous.z())>.25||Math.abs(next.waypoint().z()-s.waypoint().z())>.25)result.add(s);
            previous=s.waypoint();old=dir;
        }
        return List.copyOf(result);
    }
}
