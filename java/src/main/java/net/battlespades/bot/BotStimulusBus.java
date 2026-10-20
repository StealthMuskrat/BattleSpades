// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SourceRandom;
import static net.battlespades.bot.BotState.*;
/** Owner-only, finite-window sensory feed. Hearing exposes approximate positions and never hidden exact coordinates. */
public final class BotStimulusBus {
    public static final double HEARING_DISTANCE=SourceCatalog.load("bot-foundations").getDouble("hearing_distance");
    private record Sound(StimulusKind kind,Vector at,double created,double expires,double radius,int source,int team){}
    private record Key(StimulusKind kind,int source){}private record Perceived(double distance,Stimulus stimulus){}
    private final Thread owner=Thread.currentThread();private final int capacity;private final ArrayDeque<Sound> events=new ArrayDeque<>();private final Map<Key,Double> published=new HashMap<>();private final Map<Integer,Double> nextStep=new HashMap<>();
    public BotStimulusBus(){this(512);}public BotStimulusBus(int capacity){this.capacity=Math.max(32,capacity);}private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Hearing outside owner");}
    private static boolean finite(Vector at){return at!=null&&Double.isFinite(at.x())&&Double.isFinite(at.y())&&Double.isFinite(at.z());}
    public boolean publish(StimulusKind kind,Vector at,int source,int team,double radius,double lifetime,double now){owned();var key=new Key(kind,source);if(now-published.getOrDefault(key,Double.NEGATIVE_INFINITY)<.05)return false;if(!finite(at))return false;radius=Math.max(0,Math.min(256,radius));if(switch(kind){case SHOT,EXPLOSION,BLOCK_DESTROYED,FOOTSTEP,DEPLOYABLE,DEATH->true;default->false;})radius=Math.min(radius,HEARING_DISTANCE);lifetime=Math.max(.05,Math.min(10,lifetime));published.put(key,now);if(events.size()==capacity)events.removeFirst();events.addLast(new Sound(kind,at,now,now+lifetime,radius,source,team));return true;}
    public int footsteps(Collection<FootstepPlayer> players,double now){owned();int count=0;var live=new HashSet<Integer>();for(var p:players){live.add(p.id());if(!p.alive()||!p.spawned()||!p.grounded()||p.crouch()||p.sneak())continue;double speed=32*Math.hypot(p.velocity().x(),p.velocity().y());if(speed<1.5||now<nextStep.getOrDefault(p.id(),0.))continue;nextStep.put(p.id(),now+(p.sprint()?.386:.512));if(publish(StimulusKind.FOOTSTEP,p.position(),p.id(),p.team(),HEARING_DISTANCE,.45,now))count++;}if(nextStep.size()>live.size()+32)nextStep.keySet().retainAll(live);return count;}
    public List<Stimulus> perceive(Vector at,double now,SourceRandom random,int limit,int observer,Integer team){owned();while(!events.isEmpty()&&events.getFirst().expires()<=now)events.removeFirst();var result=new ArrayList<Perceived>();for(var e:events){if(e.expires()<=now||team!=null&&(e.source()==observer||e.kind()==StimulusKind.FOOTSTEP&&e.team()==team))continue;double dx=e.at().x()-at.x(),dy=e.at().y()-at.y(),dz=e.at().z()-at.z(),distance=Math.sqrt(dx*dx+dy*dy+dz*dz);if(distance>e.radius())continue;double uncertainty=Math.max(.75,distance*.045),angle=random.uniform(-Math.PI,Math.PI),error=uncertainty*Math.sqrt(random.random());var approximate=new Vector(e.at().x()+Math.cos(angle)*error,e.at().y()+Math.sin(angle)*error,e.at().z()+random.uniform(-.25,.25)*uncertainty);result.add(new Perceived(distance,new Stimulus(e.kind(),approximate,e.created(),e.expires(),e.source(),e.team(),uncertainty)));}result.sort(Comparator.comparingDouble(Perceived::distance));return result.stream().limit(Math.max(0,limit)).map(Perceived::stimulus).toList();}
}
