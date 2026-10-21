// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.*;
import java.util.function.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotMessages.Player;
import static net.battlespades.bot.BotVectors.*;

/** Source recovery geometry and bounded, resumable terrain-action planning. */
public final class RecoveryPlanning {
    private RecoveryPlanning() {}
    public enum Kind {WALK,SWIM,STAIR,PILLAR,BRIDGE}
    public record Step(Kind kind,Cell source,Cell destination,List<Cell> digCells,List<Cell> buildCells) {
        public Step {digCells=List.copyOf(digCells);buildCells=List.copyOf(buildCells);}
        public Step(Kind kind,Cell source,Cell destination) {this(kind,source,destination,List.of(),List.of());}
    }
    public record Plan(List<Step> steps,double cost,int blocks,int swings,int expansions,double seconds) {
        public Plan {steps=List.copyOf(steps);}
        public Cell destination() {return steps.isEmpty()?null:steps.getLast().destination();}
        public int rise() {return steps.isEmpty()?0:steps.getFirst().source().z()-destination().z();}
    }
    public record Abilities(BotDigProfile dig,int blocks,boolean canBuild) {
        public boolean instantDig() {return dig!=null&&dig.swingsPerBlock()<=1;}
        public double blockPenalty() {return instantDig()?1.2:.15;}
        public static Abilities from(Player p) {
            var dig=p.canShoot()?BotDigProfile.best(p.loadout()).orElse(null):null;
            if(dig!=null&&dig.blockDamage()<=0)dig=null;
            boolean build=p.loadout().contains(BLOCK_TOOL);return new Abilities(dig,build?Math.max(0,p.blocks()):0,build);
        }
    }
    public static final int BLOCK_TOOL=Math.toIntExact(net.battlespades.catalog.SourceCatalog.load("bot-recovery").getLong("block_tool"));
    public interface World {
        boolean solid(int x,int y,int z);
        default boolean solid(Cell c) {return solid(c.x(),c.y(),c.z());}
        default double health(Cell c) {return 5;}
    }
    public static World reader(BotNavigationWorld world) {return new World() {
        public boolean solid(int x,int y,int z) {return world.solid(x,y,z);}
        public double health(Cell c) {return world.blockHealth(c.x(),c.y(),c.z());}
    };}
    public static Cell node(Vector p) {return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),Math.min(239,(int)Math.floor(p.z()+2.25+.05)));}
    public static Vector position(Cell c) {return new Vector(c.x()+.5,c.y()+.5,c.z()-2.25);}
    public static Vector centre(Cell c) {return new Vector(c.x()+.5,c.y()+.5,c.z()+.5);}
    public static Vector coordinates(Cell c) {return new Vector(c.x(),c.y(),c.z());}
    public static List<Cell> body(Cell n) {return List.of(new Cell(n.x(),n.y(),n.z()-1),new Cell(n.x(),n.y(),n.z()-2),new Cell(n.x(),n.y(),n.z()-3));}
    static boolean inside(int x,int y) {return x>=0&&x<512&&y>=0&&y<512;}
    public static boolean editable(Cell c) {return inside(c.x(),c.y())&&c.z()>=1&&c.z()<=238;}
    public static boolean standable(World w,Cell n) {return inside(n.x(),n.y())&&n.z()>=4&&n.z()<=239&&w.solid(n)&&body(n).stream().noneMatch(w::solid);}
    private static final int[][] FACES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    static final int[][] CARDINAL={{1,0},{-1,0},{0,1},{0,-1}};
    public static boolean faceSupported(World w,Cell c,Collection<Cell> pending,Collection<Cell> ignore) {
        for(var d:FACES) {var n=new Cell(c.x()+d[0],c.y()+d[1],c.z()+d[2]);if(!ignore.contains(n)&&(pending.contains(n)||w.solid(n)))return true;}return false;
    }
    public static List<Cell> digAims(World w,Collection<Cell> required,Collection<Cell> protectedCells,BotDigProfile p) {
        var remaining=new HashSet<Cell>();for(var c:required)if(w.solid(c))remaining.add(c);
        if(remaining.stream().anyMatch(c->!editable(c)))return null;var aims=new ArrayList<Cell>();
        while(!remaining.isEmpty()) {
            Cell best=null;int bestCovered=-1,bestCollateral=Integer.MAX_VALUE;
            var candidates=new HashSet<>(remaining);
            if(!p.pattern().equals("single"))for(var c:remaining)for(int dz:new int[]{-1,1})candidates.add(new Cell(c.x(),c.y(),c.z()+dz));
            for(var aim:candidates) {
                if(!w.solid(aim)||!editable(aim))continue;var footprint=p.footprint(aim);if(!Collections.disjoint(footprint,protectedCells))continue;
                int covered=(int)footprint.stream().filter(remaining::contains).count();if(covered<=0)continue;
                int collateral=(int)footprint.stream().filter(c->!remaining.contains(c)&&w.solid(c)).count();
                if(covered>bestCovered||covered==bestCovered&&(collateral<bestCollateral||collateral==bestCollateral&&(best==null||aim.z()<best.z()))) {best=aim;bestCovered=covered;bestCollateral=collateral;}
            }
            if(best==null)return null;aims.add(best);remaining.removeAll(p.footprint(best));if(aims.size()>16)return null;
        }
        return List.copyOf(aims);
    }
    public static Integer estimateSwings(World w,Collection<Cell> required,Collection<Cell> protectedCells,BotDigProfile p) {
        var solid=required.stream().filter(w::solid).toList();if(solid.isEmpty())return 0;
        if(p==null||p.blockDamage()<=0||solid.stream().anyMatch(c->!editable(c)))return null;
        int perCell=solid.stream().mapToInt(c->p.swingsForHealth(w.health(c))).max().orElse(0);
        if(solid.size()==1||!p.pattern().equals("single")) {
            var sorted=new ArrayList<>(solid);sorted.sort(Comparator.comparingInt(Cell::z));var footprint=p.footprint(sorted.get(sorted.size()/2));
            if(footprint.containsAll(solid)&&Collections.disjoint(footprint,protectedCells))return perCell;
        }
        if(p.pattern().equals("single"))return solid.size()*perCell;
        var aims=digAims(w,solid,protectedCells,p);return aims==null?null:aims.size()*perCell;
    }
    public static Cell firstSolid(World w,Vector origin,Vector target,double limit) {
        double dx=target.x()-origin.x(),dy=target.y()-origin.y(),dz=target.z()-origin.z(),distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(distance<=1e-6)return null;int steps=Math.max(1,(int)Math.ceil(Math.min(distance+.6,limit)*10));Cell last=null;
        for(int i=1;i<=steps;i++) {double t=i*.1/distance;var cell=new Cell((int)Math.floor(origin.x()+dx*t),(int)Math.floor(origin.y()+dy*t),(int)Math.floor(origin.z()+dz*t));if(cell.equals(last))continue;last=cell;if(w.solid(cell))return cell;}return null;
    }
    public record Estimate(double horizontal,int levels) {}
    public record Goal(Predicate<Cell> accepts,Function<Cell,Estimate> estimate,String label) {
        public static Goal toward(Vector target,double radius) {
            int support=(int)Math.rint(target.z()+2.25);
            return new Goal(n->Math.hypot(n.x()+.5-target.x(),n.y()+.5-target.y())<=radius&&n.z()<=support+1,
                n->new Estimate(Math.max(0,Math.hypot(n.x()+.5-target.x(),n.y()+.5-target.y())-radius),Math.max(0,n.z()-support-1)),"toward");
        }
        public static Goal mainGround(BotNavigationWorld world,Cell start,int radius,int candidates) {
            var atlas=world.atlas().orElse(null);if(atlas==null||atlas.mainRegionId()==0)return null;
            record Candidate(double cost,int x,int y,int support) {}
            var found=new ArrayList<Candidate>();var columns=new HashMap<Integer,Integer>();
            for(int y=Math.max(0,start.y()-radius);y<Math.min(512,start.y()+radius+1);y++)for(int x=Math.max(0,start.x()-radius);x<Math.min(512,start.x()+radius+1);x++) {
                int i=y*atlas.width()+x;if(atlas.region(i)!=atlas.mainRegionId())continue;int support=atlas.primary(i);if(support>=239)continue;
                found.add(new Candidate((Math.abs(x-start.x())+Math.abs(y-start.y()))*.27+Math.max(0,start.z()-support)*.45,x,y,support));columns.put(y*512+x,support);
            }
            if(found.isEmpty())return null;
            found.sort(Comparator.comparingDouble(Candidate::cost).thenComparingInt(Candidate::x).thenComparingInt(Candidate::y).thenComparingInt(Candidate::support));
            var nearest=List.copyOf(found.subList(0,Math.min(found.size(),Math.max(1,candidates))));var cache=new HashMap<Cell,Estimate>();
            return new Goal(n->{var s=columns.get(n.y()*512+n.x());return s!=null&&Math.abs(n.z()-s)<=1;},n->cache.computeIfAbsent(n,key->{
                Estimate best=null;for(var c:nearest) {var value=new Estimate(Math.abs(n.x()-c.x())+Math.abs(n.y()-c.y()),Math.max(0,n.z()-c.support()));if(best==null||value.horizontal()<best.horizontal()||value.horizontal()==best.horizontal()&&value.levels()<best.levels())best=value;}return best;
            }),"main_ground");
        }
    }
    public record Move(Step step,double cost,int spent,int dug) {}
    public static Move move(World w,Abilities a,Cell n,Kind kind,int dx,int dy,int wallet,Set<Cell> avoid) {
        int x=n.x(),y=n.y(),s=n.z();var p=a.dig();Cell dest,floor;List<Cell> digs;double stepCost;
        if(kind==Kind.PILLAR) {
            dest=floor=new Cell(x,y,s-1);
            if(!a.canBuild()||wallet<1||!editable(floor)||s-4<1||avoid.contains(floor)||s>=239)return null;
            digs=s-5>=1?List.of(new Cell(x,y,s-4),new Cell(x,y,s-5)):List.of(new Cell(x,y,s-4));
            var swings=estimateSwings(w,digs,List.of(n,floor),p);if(swings==null)return null;var solid=digs.stream().filter(w::solid).toList();
            double cost=.85+.25+a.blockPenalty()+swings*((p==null?0:p.fireInterval())+.12);
            return new Move(new Step(kind,n,dest,solid,List.of(floor)),cost,1,solid.size());
        }
        int nx=x+dx,ny=y+dy;if(!inside(nx,ny)||!inside(x,y))return null;
        if(kind==Kind.STAIR) {dest=new Cell(nx,ny,s-1);floor=dest;digs=List.of(new Cell(nx,ny,s-4),new Cell(nx,ny,s-3),new Cell(nx,ny,s-2),new Cell(x,y,s-4));stepCost=.45;}
        else {dest=floor=new Cell(nx,ny,s);digs=body(dest).reversed();stepCost=s>=239?.36:.27;if(s>=239)kind=Kind.SWIM;}
        if(dest.z()-3<1||avoid.contains(dest))return null;
        List<Cell> builds=List.of();int spent=0;
        if(!w.solid(floor)) {
            if(kind==Kind.WALK&&s+1<239) {var lower=new Cell(nx,ny,s+1);if(standable(w,lower))return new Move(new Step(Kind.WALK,n,lower),.32,0,0);}
            var ignore=new ArrayList<>(body(n));ignore.addAll(digs);
            if(!a.canBuild()||wallet<1||!editable(floor)||avoid.contains(floor)||!faceSupported(w,floor,List.of(),ignore))return null;
            if(kind==Kind.WALK&&floor.z()>=238)return null;builds=List.of(floor);spent=1;if(kind==Kind.WALK)kind=Kind.BRIDGE;
        }
        var swings=estimateSwings(w,digs,List.of(n,floor),p);if(swings==null)return null;
        var solid=digs.stream().filter(w::solid).toList();double cost=stepCost+spent*(.25+a.blockPenalty());
        if(swings>0) {cost+=swings*((p==null?0:p.fireInterval())+.12);if(p!=null&&p.pattern().equals("cube")&&kind==Kind.STAIR)cost+=.25+a.blockPenalty()*.5;}
        if(kind==Kind.SWIM&&!solid.isEmpty())return null;
        return new Move(new Step(kind,n,dest,solid,builds),cost,spent,solid.size());
    }
    public static double typicalLevelCost(Abilities a) {
        double best=a.canBuild()&&a.blocks()>0?.45+.25+a.blockPenalty():Double.POSITIVE_INFINITY;var p=a.dig();
        if(p!=null&&p.blockDamage()>0) {int cells=p.pattern().equals("single")?4:p.pattern().equals("cube")?1:2;best=Math.min(best,.45+cells*(p.fireInterval()+.12)*Math.max(1,p.swingsPerBlock())+(p.pattern().equals("cube")?.5:0));}
        return Double.isFinite(best)?best:4;
    }
    public static final class Search {
        private record Entry(double priority,double cost,long counter,Cell node) {}
        private record Parent(Cell node,Step step,int spent,int dug) {}
        private final Thread owner=Thread.currentThread();private final World world;private final Cell start;private final Goal goal;private final Abilities abilities;
        private final int radius,maximum;private final boolean allowSwim,cube;private final Set<Kind> kinds;private final Set<Cell> avoid;private final DoubleSupplier clock;private final double levelCost;
        private final PriorityQueue<Entry> queue=new PriorityQueue<>(Comparator.comparingDouble(Entry::priority).thenComparingDouble(Entry::cost).thenComparingLong(Entry::counter));
        private final Map<Cell,Double> best=new HashMap<>();private final Map<Cell,Integer> wallets=new HashMap<>();private final Map<Cell,Parent> parents=new HashMap<>();private long counter;private int expansions;private String status="pending";private Plan plan;
        public Search(World w,Cell start,Goal goal,Abilities abilities,int radius,int maximum,boolean allowSwim,Set<Kind> allowed,Set<Cell> avoid,DoubleSupplier clock) {
            var cache=new HashMap<Cell,Boolean>();world=new World() {public boolean solid(int x,int y,int z) {return cache.computeIfAbsent(new Cell(x,y,z),w::solid);}public double health(Cell c) {return w.health(c);}};
            this.start=start;this.goal=goal;this.abilities=abilities;this.radius=radius;this.maximum=maximum;this.allowSwim=allowSwim;kinds=allowed==null||allowed.isEmpty()?Set.copyOf(EnumSet.allOf(Kind.class)):Set.copyOf(allowed);this.avoid=Set.copyOf(avoid);this.clock=clock;
            cube=abilities.dig()!=null&&abilities.dig().pattern().equals("cube");levelCost=typicalLevelCost(abilities);best.put(start,0.);wallets.put(start,abilities.blocks());queue.add(new Entry(heuristic(start),0,0,start));
        }
        private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Recovery search outside worker");}
        private double heuristic(Cell n) {var e=goal.estimate().apply(n);return (e.horizontal()*.27+e.levels()*levelCost)*2.2;}
        public String status() {owned();return status;}public int expansions() {owned();return expansions;}public Plan plan() {owned();return plan;}
        public String advance(double seconds) {
            owned();if(!status.equals("pending"))return status;double deadline=clock.getAsDouble()+Math.max(.0005,seconds);int iterations=0;
            while(!queue.isEmpty()&&expansions<maximum) {
                iterations++;if((iterations&31)==0&&clock.getAsDouble()>deadline)return "pending";
                var entry=queue.remove();var n=entry.node();double cost=entry.cost();if(cost>best.getOrDefault(n,Double.POSITIVE_INFINITY)+1e-9)continue;expansions++;
                if(goal.accepts().test(n)&&!n.equals(start)) {plan=reconstruct(n,cost);status="found";return status;}
                int wallet=wallets.getOrDefault(n,abilities.blocks());var parent=parents.get(n);
                for(int index=0;index<9;index++) {
                    Kind kind=index==0?Kind.PILLAR:index%2==1?Kind.STAIR:Kind.WALK;int dx=index==0?0:CARDINAL[(index-1)/2][0],dy=index==0?0:CARDINAL[(index-1)/2][1];
                    if((kind==Kind.PILLAR||kind==Kind.STAIR)&&!kinds.contains(kind))continue;int nx=n.x()+dx,ny=n.y()+dy;
                    if(kind==Kind.STAIR&&parent!=null&&parent.step().kind()==Kind.STAIR) {
                        if(!parent.step().buildCells().isEmpty()&&nx==parent.node().x()&&ny==parent.node().y())continue;
                        if(cube&&Math.max(Math.abs(nx-parent.node().x()),Math.abs(ny-parent.node().y()))<2)continue;
                    }
                    if(Math.abs(nx-start.x())>radius||Math.abs(ny-start.y())>radius)continue;
                    var move=move(world,abilities,n,kind,dx,dy,wallet,avoid);if(move==null||!kinds.contains(move.step().kind())||move.step().kind()==Kind.SWIM&&!allowSwim)continue;
                    var dest=move.step().destination();double total=cost+move.cost();if(total+1e-9>=best.getOrDefault(dest,Double.POSITIVE_INFINITY))continue;
                    best.put(dest,total);wallets.put(dest,wallet-move.spent()+move.dug());parents.put(dest,new Parent(n,move.step(),move.spent(),move.dug()));queue.add(new Entry(total+heuristic(dest),total,++counter,dest));
                }
            }
            return status="failed";
        }
        private Plan reconstruct(Cell last,double cost) {
            var steps=new ArrayList<Step>();int blocks=0,swings=0;for(Cell n=last;!n.equals(start);) {var p=parents.get(n);steps.add(p.step());blocks+=p.spent();n=p.node();}Collections.reverse(steps);
            for(var s:steps)if(!s.digCells().isEmpty()&&abilities.dig()!=null)swings+=abilities.dig().pattern().equals("single")?s.digCells().size():1;
            return new Plan(steps,rounded(cost,3),blocks,swings,expansions,rounded(Math.max(0,cost-blocks*abilities.blockPenalty()),3));
        }
    }
    public static double rounded(double value,int digits) {return new java.math.BigDecimal(value).setScale(digits,java.math.RoundingMode.HALF_EVEN).doubleValue();}
    private static int[] axis(Vector d) {if(Math.abs(d.x())<1e-6&&Math.abs(d.y())<1e-6)return new int[]{1,0};return Math.abs(d.x())>=Math.abs(d.y())?new int[]{d.x()>0?1:-1,0}:new int[]{0,d.y()>0?1:-1};}
    public static Plan primitive(World w,Cell start,Vector direction,int count,Abilities a,Kind kind) {
        int[] d=axis(direction);var steps=new ArrayList<Step>();double cost=0;int wallet=a.blocks();Cell n=start;
        for(int i=0;i<Math.max(0,count);i++) {
            if(kind==Kind.BRIDGE) {
                var dest=new Cell(n.x()+d[0],n.y()+d[1],n.z());if(!inside(dest.x(),dest.y())||body(dest).stream().anyMatch(w::solid))break;
                if(w.solid(dest)) {steps.add(new Step(Kind.WALK,n,dest));cost+=.27;}
                else {if(!a.canBuild()||wallet<1||!editable(dest))break;steps.add(new Step(Kind.BRIDGE,n,dest,List.of(),List.of(dest)));wallet--;cost+=.27+.25;}
                n=dest;
            } else {
                var m=move(w,a,n,kind,kind==Kind.PILLAR?0:d[0],kind==Kind.PILLAR?0:d[1],wallet,Set.of());if(m==null)break;steps.add(m.step());cost+=m.cost();wallet+=m.dug()-m.spent();n=m.step().destination();
            }
        }
        if(steps.isEmpty())return null;int blocks=kind==Kind.PILLAR?steps.size():steps.stream().mapToInt(s->s.buildCells().size()).sum();return new Plan(steps,rounded(cost,3),blocks,0,0,0);
    }
    public static Plan gapBridge(World w,Vector position,Vector goal,Abilities a,int maximum,int minGap) {
        if(!a.canBuild()||a.blocks()<minGap)return null;var start=node(position);if(start.z()>=239||!w.solid(start))return null;int[] d=axis(new Vector(goal.x()-position.x(),goal.y()-position.y(),0));int x=start.x(),y=start.y(),s=start.z(),lead=0;
        while(lead<2&&standable(w,new Cell(x+d[0],y+d[1],s))) {x+=d[0];y+=d[1];lead++;}
        var gap=new ArrayList<Cell>();int limit=Math.min(maximum,a.blocks());
        for(int i=0;i<=limit;i++) {
            x+=d[0];y+=d[1];if(!inside(x,y))return null;
            for(int landing:new int[]{s,s-1,s+1})if(standable(w,new Cell(x,y,landing))) {
                if(gap.size()<minGap)return null;var steps=new ArrayList<Step>();Cell n=start;
                for(int j=0;j<lead;j++) {var dest=new Cell(n.x()+d[0],n.y()+d[1],s);steps.add(new Step(Kind.WALK,n,dest));n=dest;}
                for(var c:gap) {steps.add(new Step(Kind.BRIDGE,n,c,List.of(),List.of(c)));n=c;}
                steps.add(new Step(landing<s?Kind.STAIR:Kind.WALK,n,new Cell(x,y,landing)));return new Plan(steps,rounded(steps.size()*(.27+.25),3),gap.size(),0,0,0);
            }
            for(int offset=0;offset<=3;offset++)if(w.solid(x,y,s-offset))return null;
            if(w.solid(x,y,s+1)||w.solid(x,y,s+2))return null;gap.add(new Cell(x,y,s));
        }
        return null;
    }
    public static boolean inWater(World w,Player p) {var n=node(p.position());return p.wade()||p.position().z()>=239-2.25-.6&&!w.solid(n.x(),n.y(),238);}
    public static double quickClimb(BotNavigationWorld world,Cell start,Abilities a,int radius) {
        var atlas=world.atlas().orElse(null);if(atlas==null||atlas.mainRegionId()==0||a.dig()==null&&!(a.canBuild()&&a.blocks()>0))return Double.POSITIVE_INFINITY;
        double level=.85+.25;var p=a.dig();if(p!=null) {double dig=.45+2*(p.fireInterval()+.12)*Math.max(1,p.swingsPerBlock());level=a.canBuild()&&a.blocks()>4?Math.min(level+a.blockPenalty(),dig):dig;}
        double best=Double.POSITIVE_INFINITY;
        for(int y=Math.max(0,start.y()-radius);y<Math.min(512,start.y()+radius+1);y++)for(int x=Math.max(0,start.x()-radius);x<Math.min(512,start.x()+radius+1);x++) {
            int i=y*atlas.width()+x;if(atlas.region(i)!=atlas.mainRegionId())continue;
            best=Math.min(best,(Math.abs(x-start.x())+Math.abs(y-start.y()))*.36+Math.max(0,start.z()-atlas.primary(i))*level);
        }
        return best;
    }
}
