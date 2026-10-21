// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import java.util.function.DoubleSupplier;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotVectors.*;

/** Owner-side shared snapshot staging and bounded per-observer worker publication. */
public final class BotPerception {
    public interface Actor {int id();Player snapshot();}
    public static final class Observer {
        public final Actor actor;public final int generation;public BotProfile profile;private double next;
        public Observer(Actor actor,int generation,BotProfile profile,double next){this.actor=Objects.requireNonNull(actor);this.generation=generation;this.profile=Objects.requireNonNull(profile);this.next=next;}
        public double next(){return next;}
    }
    public interface Services {
        List<Observer> observers();List<Actor> players();List<Entity> entities();List<Objective> objectives();Map<String,Object> community();
        void footsteps(List<Actor> players,double now);List<BotState.Stimulus> stimuli(Observer observer,double now);
        long mapEpoch();long modeEpoch();long topology();String mode();String phase();String behavior();boolean friendlyMischief();
        void submit(Perception frame);
    }
    private final Thread owner=Thread.currentThread();private final Services service;private final DoubleSupplier workClock;private final double interval,publishBudget;
    private List<Actor> building=List.of();private final List<Player> built=new ArrayList<>();private int buildIndex;private double cacheUntil;private long frame;
    private List<Player> players=List.of();private List<Entity> entities=List.of();private List<Objective> objectives=List.of();private Map<String,Object> fun=Map.of();
    public BotPerception(Services service,double hz,double budgetMillis,DoubleSupplier workClock){this.service=Objects.requireNonNull(service);this.workClock=Objects.requireNonNull(workClock);interval=1/Math.max(1,hz);publishBudget=Math.max(.00005,Math.max(.0001,budgetMillis/1000)*.55);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot perception outside owner");}
    public void frameId(long value){owned();if(value<frame)throw new IllegalArgumentException("Frame id regressed");frame=value;}
    public void reset(){owned();building=List.of();built.clear();buildIndex=0;cacheUntil=0;players=List.of();entities=List.of();objectives=List.of();fun=Map.of();}
    /** True means a shared snapshot batch owns this tick, so director motors defer that phase. */
    public boolean publishDue(double now){
        owned();var due=service.observers().stream().filter(o->now>=o.next).sorted(Comparator.comparingDouble((Observer o)->o.next).thenComparingInt(o->o.actor.id())).toList();if(due.isEmpty())return false;double started=workClock.getAsDouble();
        if(now>=cacheUntil||!building.isEmpty()){
            if(building.isEmpty()){building=List.copyOf(service.players());built.clear();buildIndex=0;}int stop=Math.min(building.size(),buildIndex+12);for(int i=buildIndex;i<stop;i++)built.add(building.get(i).snapshot());buildIndex=stop;if(stop<building.size())return true;
            players=List.copyOf(built);entities=List.copyOf(service.entities());objectives=List.copyOf(service.objectives());fun=BotMessages.freeze(service.community());service.footsteps(building,now);building=List.of();built.clear();buildIndex=0;cacheUntil=now+Math.min(interval,.1);return true;
        }
        int published=0;for(var o:due){if(published>0&&workClock.getAsDouble()-started>=publishBudget)break;o.next=now+interval;service.submit(new Perception(++frame,service.mapEpoch(),service.modeEpoch(),service.topology(),o.actor.id(),o.generation,now,service.mode(),cohort(players,o.actor.id(),objectives),o.profile,entities,objectives,service.stimuli(o,now),service.phase(),service.behavior(),service.friendlyMischief(),players.size()<=32,fun));published++;}return false;
    }
    public static List<Player> cohort(List<Player> players,int observer,List<Objective> objectives){if(players.size()<=32)return List.copyOf(players);var origin=players.stream().filter(p->p.id()==observer).map(Player::position).findFirst().orElse(ZERO);var carriers=new HashSet<Integer>();for(var o:objectives)if(o.carrierId()>=0)carriers.add(o.carrierId());return players.stream().sorted(Comparator.<Player>comparingInt(p->p.id()==observer?0:carriers.contains(p.id())?1:p.bot()?2:p.alive()&&p.spawned()?3:4).thenComparingDouble(p->squared(p.position(),origin)).thenComparingInt(Player::id)).limit(32).toList();}
    private static double squared(Vector a,Vector b){double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return x*x+y*y+z*z;}
    /** Shared entity ranking retains cached ordinary order while refreshing urgent hazards every sample. */
    public static final class Entities {
        private final Thread owner=Thread.currentThread();private List<Integer> order=List.of();private Set<Integer> ids=Set.of();private double until;private long reranks,overflow;
    public void reset(){owned();order=List.of();ids=Set.of();until=0;}private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Entity perception outside owner");}
        public long reranks(){owned();return reranks;}public long overflow(){owned();return overflow;}
        public List<Entity> select(List<Entity> entities,List<Player> players,double now){owned();if(entities.size()<=192)return List.copyOf(entities);var carried=new HashSet<Integer>();for(var p:players)if(p.carriedEntityId()>=0)carried.add(p.carriedEntityId());var positions=players.stream().filter(p->p.alive()&&p.spawned()).map(Player::position).toList();var urgent=new ArrayList<Entity>();var ordinary=new LinkedHashMap<Integer,Entity>();for(var e:entities)if(e.hazardous()||carried.contains(e.id())||e.kind().equals("blockfire"))urgent.add(e);else ordinary.put(e.id(),e);java.util.function.ToDoubleFunction<Entity> distance=e->positions.stream().mapToDouble(p->squared(e.position(),p)).min().orElse(0);urgent.sort(Comparator.<Entity>comparingInt(e->e.hazardous()?0:1).thenComparingDouble(distance).thenComparingInt(Entity::id));if(now>=until||!ids.containsAll(ordinary.keySet())){order=ordinary.values().stream().sorted(Comparator.<Entity>comparingInt(e->switch(e.kind().toLowerCase(Locale.ROOT)){case "objective","intel","base"->2;case "projectile"->3;case "pickup","crate","resource"->4;default->5;}).thenComparingDouble(distance).thenComparingInt(Entity::id)).map(Entity::id).toList();ids=Set.copyOf(order);until=now+.5;reranks++;}var selected=new ArrayList<>(urgent.subList(0,Math.min(192,urgent.size())));for(int id:order){if(selected.size()>=192)break;var entity=ordinary.get(id);if(entity!=null)selected.add(entity);}overflow+=entities.size()-selected.size();return List.copyOf(selected);}
    }
}
