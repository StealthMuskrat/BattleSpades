// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotRoutePlanner.*;

/** Live-validated swim, shore, excavation and construction proposals; never commits terrain. */
public final class BotWaterNavigation {
    public record Line(Cell first,Cell last) {}
    private final BotNavigationWorld world;
    BotWaterNavigation(BotNavigationWorld world) {this.world=world;}
    private Surface current(Vector at,int span) {return world.surface((int)Math.floor(at.x()),(int)Math.floor(at.y()),at.z(),span,true);}
    private static double distance(Surface s,Vector goal) {return Math.hypot(goal.x()-s.x()-.5,goal.y()-s.y()-.5);}
    private static final Comparator<Surface> COORDINATES=Comparator.comparingInt(Surface::x).thenComparingInt(Surface::y);
    private Surface closest(List<Surface> choices,Vector goal) {
        return choices.stream().min(Comparator.comparingDouble((Surface s)->goal==null?0:distance(s,goal)).thenComparing(COORDINATES)).orElse(null);
    }
    private Surface preferredBank(Surface from,Vector goal) {
        var choices=new ArrayList<Surface>();double before=distance(from,goal);
        for(var d:CARDINAL) {
            int x=from.x()+d[0],y=from.y()+d[1];
            if(Math.hypot(goal.x()-x-.5,goal.y()-y-.5)>=before-1e-6)continue;
            var s=world.surface(x,y,from.position().z(),12,false);
            if(s!=null&&s.supportZ()<239)choices.add(s);
        }
        return closest(choices,goal);
    }
    private Surface dryBank(Surface from,int x,int y) {
        if(x<0||y<0||x>=512||y>=512||!world.solid(x,y,238)&&!world.solid(x,y,237))return null;
        int expected=(int)Math.rint(from.position().z()+2.25);
        var heights=new ArrayList<Integer>();for(int z=3;z<Math.min(239,expected+13);z++)heights.add(z);
        heights.sort(Comparator.comparingInt(z->Math.abs(z-expected)));
        for(int z:heights)if(world.solid(x,y,z)&&!world.solid(x,y,z-1)&&!world.solid(x,y,z-2)&&!world.solid(x,y,z-3))return new Surface(x,y,z);
        return null;
    }
    public RouteStep step(Vector position,Vector preferredGoal,Set<Edge> blocked) {
        var start=current(position,6);if(start==null||start.supportZ()<239)return null;
        if(preferredGoal!=null) {
            var preferred=preferredBank(start,preferredGoal);
            if(preferred!=null) {
                var bank=dryBank(start,preferred.x(),preferred.y());
                if(bank!=null&&!blocked.contains(edge(start,bank))&&Math.abs(bank.supportZ()-start.supportZ())>1)return null;
            }
            var directed=directed(start,preferredGoal,blocked);if(directed!=null)return directed;
        }
        var atlas=world.atlas().orElse(null);
        if(atlas!=null) {
            var route=atlas.waterRoute(start.x(),start.y());
            if(route!=null) {
                Surface cursor=start,furthest=null;Edge entry=null;boolean pathBlocked=false;
                int limit=Math.min(4,Math.max(1,route.distance()));
                for(int i=0;i<limit;i++) {
                    route=atlas.waterRoute(cursor.x(),cursor.y());if(route==null)break;
                    if(route.distance()==1) {
                        var bank=dryBank(cursor,route.goalX(),route.goalY());
                        if(bank==null||blocked.contains(edge(cursor,bank))) {pathBlocked=true;break;}
                        if(cursor==start)return Math.abs(bank.supportZ()-cursor.supportZ())<=1?new RouteStep(bank.position(),Affordance.JUMP):null;
                        break;
                    }
                    if(Math.abs(route.nextX()-cursor.x())+Math.abs(route.nextY()-cursor.y())!=1)break;
                    var sample=world.surface(route.nextX(),route.nextY(),position.z(),5,true);
                    if(sample==null||Math.abs(sample.supportZ()-cursor.supportZ())>2)break;
                    var e=edge(cursor,sample);if(blocked.contains(e)) {pathBlocked=true;break;}
                    if(entry==null)entry=e;
                    if(sample.supportZ()<239)break;
                    furthest=sample;cursor=sample;
                }
                if(furthest!=null&&!pathBlocked)return new RouteStep(furthest.position(),Affordance.WALK,null,entry);
            }
        }
        return boundedSearch(start,blocked);
    }
    private RouteStep directed(Surface start,Vector goal,Set<Edge> blocked) {
        var visited=new HashSet<Integer>();visited.add(start.y()*512+start.x());Surface cursor=start,furthest=null;Edge entry=null;
        for(int i=0;i<4;i++) {
            var choices=new ArrayList<Surface>();double before=distance(cursor,goal);
            for(var d:CARDINAL) {
                int x=cursor.x()+d[0],y=cursor.y()+d[1];if(visited.contains(y*512+x))continue;
                var s=world.surface(x,y,cursor.position().z(),5,true);
                if(s!=null&&!blocked.contains(edge(cursor,s))&&distance(s,goal)<before-1e-6&&Math.abs(s.supportZ()-cursor.supportZ())<=2)choices.add(s);
            }
            var sample=closest(choices,goal);if(sample==null)break;
            if(entry==null)entry=edge(start,sample);
            if(sample.supportZ()<239) {if(cursor==start)return new RouteStep(sample.position(),Affordance.JUMP);break;}
            furthest=sample;cursor=sample;visited.add(cursor.y()*512+cursor.x());
        }
        return furthest==null?null:new RouteStep(furthest.position(),Affordance.SWIM,null,entry);
    }
    private RouteStep boundedSearch(Surface start,Set<Edge> blocked) {
        int first=start.y()*512+start.x();var queue=new ArrayDeque<Integer>();queue.add(first);
        var parent=new HashMap<Integer,Integer>();parent.put(first,null);var samples=new HashMap<Integer,Surface>();samples.put(first,start);
        Integer goal=null;int expansions=0;
        while(!queue.isEmpty()&&expansions<8192) {
            int id=queue.removeFirst();var from=samples.get(id);expansions++;
            for(var d:CARDINAL) {
                int x=from.x()+d[0],y=from.y()+d[1],next=y*512+x;
                // Outside coordinates are solid; don't alias them to a valid column key.
                if(x<0||y<0||x>=512||y>=512||parent.containsKey(next))continue;
                var bank=dryBank(from,x,y);
                if(bank!=null) {
                    if(blocked.contains(edge(from,bank)))continue;
                    parent.put(next,id);samples.put(next,bank);goal=next;queue.clear();break;
                }
                var sample=world.surface(x,y,from.position().z(),4,true);
                if(sample==null||Math.abs(sample.supportZ()-from.supportZ())>2||blocked.contains(edge(from,sample)))continue;
                parent.put(next,id);samples.put(next,sample);
                if(sample.supportZ()<239) {goal=next;queue.clear();break;}
                queue.addLast(next);
            }
        }
        if(goal==null)return null;
        int cursor=goal;while(parent.get(cursor)!=null&&parent.get(cursor)!=first)cursor=parent.get(cursor);
        var sample=samples.get(cursor);
        if(sample.supportZ()<239&&Math.abs(sample.supportZ()-start.supportZ())>1)return null;
        return new RouteStep(sample.position(),sample.supportZ()<239?Affordance.JUMP:Affordance.WALK);
    }
    public RouteStep assistedStep(Vector position,Vector preferredGoal,Set<Edge> blocked) {
        var start=current(position,6);if(start==null||start.supportZ()<239)return null;
        if(preferredGoal!=null) {
            var preferred=preferredBank(start,preferredGoal);
            if(preferred!=null) {
                var bank=dryBank(start,preferred.x(),preferred.y());
                if(bank!=null&&!blocked.contains(edge(start,bank))&&Math.abs(bank.supportZ()-start.supportZ())>1)return new RouteStep(preferred.position(),Affordance.BUILD_STEP);
            }
        }
        var atlas=world.atlas().orElse(null);var route=atlas==null?null:atlas.waterRoute(start.x(),start.y());
        if(route!=null&&route.distance()==1) {
            var bank=dryBank(start,route.goalX(),route.goalY());
            if(bank!=null&&!blocked.contains(edge(start,bank))&&Math.abs(bank.supportZ()-start.supportZ())>1)return new RouteStep(bank.position(),Affordance.BUILD_STEP);
        }
        var choices=new ArrayList<Surface>();
        for(var d:CARDINAL) {
            var bank=dryBank(start,start.x()+d[0],start.y()+d[1]);
            if(bank!=null&&Math.abs(bank.supportZ()-start.supportZ())>1&&!blocked.contains(edge(start,bank)))choices.add(bank);
        }
        var bank=closest(choices,preferredGoal);return bank==null?null:new RouteStep(bank.position(),Affordance.BUILD_STEP);
    }
    public Cell jumpBuildCell(Vector position) {
        int x=(int)Math.floor(position.x()),y=(int)Math.floor(position.y());
        return !world.solid(x,y,239)||world.solid(x,y,238)||Math.floor(position.z()+2)>=238?null:new Cell(x,y,238);
    }
    public Set<Edge> shoreEdges(Vector position,int requestedRadius) {
        int radius=Math.max(1,Math.min(4,requestedRadius)),cx=(int)Math.floor(position.x()),cy=(int)Math.floor(position.y());var out=new HashSet<Edge>();
        for(int x=cx-radius;x<=cx+radius;x++)for(int y=cy-radius;y<=cy+radius;y++) {
            var from=world.surface(x,y,236.75,2,true);if(from==null||from.supportZ()!=239)continue;
            for(var d:CARDINAL) {
                var bank=dryBank(from,x+d[0],y+d[1]);if(bank==null)continue;
                for(int z:new int[]{bank.supportZ(),238,239})out.add(new Edge(key(from),new Cell(bank.x(),bank.y(),z)));
            }
        }
        return Set.copyOf(out);
    }
    private record Roam(int length,double bearing,int rank,RouteStep step) {}
    public RouteStep roamStep(Vector position,Vector heading,Set<Edge> blocked) {
        var start=current(position,3);if(start==null)return null;var choices=new ArrayList<Roam>();
        for(int rank=0;rank<CARDINAL.length;rank++) {
            int dx=CARDINAL[rank][0],dy=CARDINAL[rank][1];var cursor=start;Edge entry=null;
            for(int length=1;length<=4;length++) {
                var sample=world.surface(cursor.x()+dx,cursor.y()+dy,cursor.position().z(),2,3,true);
                if(sample==null||sample.supportZ()!=239)break;
                var e=edge(cursor,sample);if(blocked.contains(e))break;if(entry==null)entry=e;cursor=sample;
                choices.add(new Roam(length,dx*heading.x()+dy*heading.y(),-rank,new RouteStep(sample.position(),Affordance.SWIM,null,entry)));
            }
        }
        return choices.stream().max(Comparator.comparingInt((Roam r)->Math.min(r.length,2)).thenComparingDouble(Roam::bearing).thenComparingInt(Roam::length).thenComparingInt(Roam::rank))
            .map(Roam::step).orElseGet(()->blocked.isEmpty()?null:roamStep(position,heading,Set.of()));
    }
    private static int[] direction(Vector v) {
        if(Math.hypot(v.x(),v.y())<=1e-6)return null;
        return Math.abs(v.x())>=Math.abs(v.y())?new int[]{v.x()>0?1:-1,0}:new int[]{0,v.y()>0?1:-1};
    }
    private boolean body(int x,int y,int support,int clearance) {
        for(int i=1;i<=clearance;i++)if(world.solid(x,y,support-i))return true;return false;
    }
    public Line bridgeLine(Vector position,Vector direction,int maxCells,Integer requireLandingWithin) {
        var d=direction(direction);if(d==null)return null;
        int sx=(int)Math.floor(position.x()),sy=(int)Math.floor(position.y()),z=(int)Math.rint(position.z()+2.25);
        if(!world.solid(sx,sy,z))return null;
        if(requireLandingWithin!=null) {
            boolean landing=false;
            for(int length=1;length<=Math.min(128,Math.max(1,requireLandingWithin));length++) {
                int x=sx+d[0]*length,y=sy+d[1]*length;
                if(x<0||y<0||x>=512||y>=512||body(x,y,z,3))break;
                if(world.solid(x,y,z)) {landing=true;break;}
            }
            if(!landing)return null;
        }
        Cell first=null,last=null;
        for(int length=1;length<=Math.max(1,Math.min(8,maxCells));length++) {
            int x=sx+d[0]*length,y=sy+d[1]*length;
            if(x<0||y<0||x>=512||y>=512||world.solid(x,y,z)||body(x,y,z,3))break;
            last=new Cell(x,y,z);if(first==null)first=last;
        }
        return first==null?null:new Line(first,last);
    }
    public Line shoulderLine(Vector position,Vector direction,int maxCells) {
        var d=direction(direction);if(d==null)return null;
        int sx=(int)Math.floor(position.x()),sy=(int)Math.floor(position.y()),z=(int)Math.rint(position.z()+2.25),sideX=-d[1],sideY=d[0];
        if(!world.solid(sx,sy,z))return null;
        int preferred=(position.x()-sx-.5)*sideX+(position.y()-sy-.5)*sideY>=0?1:-1,limit=Math.max(1,Math.min(8,maxCells));
        for(int sign:new int[]{preferred,-preferred}) {
            Cell first=null,last=null;int count=0;
            for(int length=0;length<=limit;length++) {
                int cx=sx+d[0]*length,cy=sy+d[1]*length,x=cx+sideX*sign,y=cy+sideY*sign;
                if(!world.solid(cx,cy,z)||x<0||y<0||x>=512||y>=512)break;
                if(world.solid(x,y,z)||body(x,y,z,2)) {if(first!=null)break;continue;}
                last=new Cell(x,y,z);if(first==null)first=last;if(++count>=limit)break;
            }
            if(first!=null)return new Line(first,last);
        }
        return null;
    }
    public RouteStep bankBreach(Vector position,BotDigProfile p,Vector preferredGoal,Set<Edge> blocked) {
        var bank=assistedStep(position,preferredGoal,blocked);if(bank==null)return null;
        int sx=(int)Math.floor(position.x()),sy=(int)Math.floor(position.y()),x=(int)Math.floor(bank.waypoint().x()),y=(int)Math.floor(bank.waypoint().y());
        var source=new Cell(sx,sy,239);var destination=new Cell(x,y,238);
        if(!world.solid(x,y,238)||blocked.contains(new Edge(source,destination)))return null;
        var blockers=new ArrayList<Cell>();for(int offset:new int[]{3,2,1})if(world.solid(x,y,238-offset))blockers.add(new Cell(x,y,238-offset));
        Cell target=null;int covered=0;
        for(var candidate:blockers) {
            var footprint=p.footprint(candidate);if(footprint.contains(destination))continue;
            int count=(int)blockers.stream().filter(footprint::contains).count();
            if(count>covered||count==covered&&(target==null||candidate.z()>target.z())) {target=candidate;covered=count;}
        }
        if(target==null||p.swingsPerBlock()<=0||covered<=0)return null;
        int swings=(int)Math.ceil(blockers.size()/(double)covered)*blockers.stream().mapToInt(c->p.swingsForHealth(world.blockHealth(c.x(),c.y(),c.z()))).max().orElse(0);
        return new RouteStep(position(destination),Affordance.BREACH,breach(source,destination,target,blockers,p,swings),null);
    }
}
