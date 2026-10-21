// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Bounded advisory worker leases, shared mutation budgets and tactical outcomes. */
public final class TeamTasks {
    public record Identity(int id,int generation,int life){}public record Observer(int id,int generation){}public record Observed(boolean alive,int life){}
    public enum Stage {APPROACH,EXECUTE,CONFIRM,USE}
    public record Order(long taskId,String role,Vector goal,Vector look,Action action,double arrivalRadius,boolean hold,boolean urgent,int toolId){public Order{Objects.requireNonNull(action);}public static Order move(long id,String role,Vector goal,double radius){return new Order(id,role,goal,null,Action.none(),radius,false,false,-1);}}
    public static final class Project {
        public final long id;public final int team;public final String kind;public final Identity owner;public Vector position;public final Vector lane;public final double createdAt;public double expiresAt;public Stage stage=Stage.APPROACH;public final LinkedHashMap<Identity,Double> participants=new LinkedHashMap<>();public final List<Cell> cells;public double lastProgressAt;public final Set<Identity> usedBy=new LinkedHashSet<>();
        public Project(long id,int team,String kind,Identity owner,Vector position,Vector lane,double createdAt,double expiresAt,List<Cell> cells,double lastProgressAt){this.id=id;this.team=team;this.kind=kind;this.owner=owner;this.position=position;this.lane=lane;this.createdAt=createdAt;this.expiresAt=expiresAt;this.cells=List.copyOf(cells);this.lastProgressAt=lastProgressAt;}
    }
    public record Mutation(double at,int cells){}public record Event(String kind,long taskId,String reason,double at){}
    private final Thread owner=Thread.currentThread();private final LinkedHashMap<Long,Project> projects=new LinkedHashMap<>();private final LinkedHashMap<Integer,Double> mischiefReady=new LinkedHashMap<>();private final LinkedHashMap<Integer,ArrayDeque<Mutation>> mutations=new LinkedHashMap<>();private final LinkedHashMap<String,Integer> metrics=new LinkedHashMap<>();private final ArrayDeque<Event> events=new ArrayDeque<>();
    void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Team tasks outside worker");}
    public static Identity identity(Player p){return new Identity(p.id(),p.generation(),p.lifeId());}
    public Map<Long,Project> projects(){owned();return Collections.unmodifiableMap(projects);}public void remove(long id){owned();projects.remove(id);}public Map<String,Integer> metrics(){owned();return Collections.unmodifiableMap(new LinkedHashMap<>(metrics));}public List<Event> events(){owned();return List.copyOf(events);}public List<Mutation> mutations(int team){owned();return List.copyOf(mutations.getOrDefault(team,new ArrayDeque<>()));}
    public double mischiefReady(int team){owned();return mischiefReady.getOrDefault(team,0.);}public void mischiefReady(int team,double until){owned();mischiefReady.put(team,until);}
    public void expire(double now,Map<Observer,Observed> observed){owned();var it=projects.values().iterator();while(it.hasNext()){var p=it.next();var state=observed.get(new Observer(p.owner.id(),p.owner.generation()));if(now>=p.expiresAt||state!=null&&(!state.alive()||state.life()!=p.owner.life())){it.remove();continue;}p.participants.entrySet().removeIf(e->{var v=observed.get(new Observer(e.getKey().id(),e.getKey().generation()));return e.getValue()<=now||v!=null&&(!v.alive()||v.life()!=e.getKey().life());});}}
    public boolean reserve(Project p){owned();var same=projects.values().stream().filter(q->q.team==p.team).toList();if(p.kind.equals("rampart")){var ramparts=same.stream().filter(q->q.kind.equals("rampart")).toList();var cells=new HashSet<>(p.cells);if(ramparts.size()>=8||projects.size()>=24||same.stream().anyMatch(q->q.owner.equals(p.owner))||ramparts.stream().anyMatch(q->q.cells.stream().anyMatch(cells::contains)))return false;}else{same=same.stream().filter(q->!q.kind.equals("rampart")).toList();if(same.size()>=3||projects.size()>=12||same.stream().anyMatch(q->q.owner.equals(p.owner))||same.stream().anyMatch(q->distance(q.position,p.position)<7))return false;}projects.put(p.id,p);metrics.merge("projects_started",1,Integer::sum);return true;}
    public boolean allowMutation(int team,double now,int cells){owned();var history=mutations.computeIfAbsent(team,k->new ArrayDeque<>());while(!history.isEmpty()&&now-history.getFirst().at()>=10)history.removeFirst();return cells>0&&cells<=128&&history.stream().mapToInt(Mutation::cells).sum()+cells<=256;}
    public void recordMutation(int team,double now,int cells){owned();var history=mutations.computeIfAbsent(team,k->new ArrayDeque<>());if(history.size()==32)cells=Math.min(257,history.removeLast().cells()+cells);history.addLast(new Mutation(now,cells));}
    public void event(String kind,long id,String reason,double now){owned();metrics.merge(kind,1,Integer::sum);if(events.size()==256)events.removeFirst();events.addLast(new Event(kind,id,reason,now));}
}
