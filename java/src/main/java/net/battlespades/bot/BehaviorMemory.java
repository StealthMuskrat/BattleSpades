// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import static net.battlespades.bot.BotMessages.*;
import static net.battlespades.bot.BotState.*;
import static net.battlespades.bot.BotVectors.*;

/** Per-life sensory evidence and outcome aversion; hidden roster entries never refresh contacts. */
public final class BehaviorMemory {
    public record Key(int x,int y,String kind){}public record Contact(Vector position,double expiresAt,double confidence,double uncertainty){}public record Outcome(String kind,Vector position,double at,boolean success){}
    public record State(Map<Key,Contact> contacts,List<Outcome> outcomes,double pressure,double updatedAt){public State{contacts=Collections.unmodifiableMap(new LinkedHashMap<>(contacts));outcomes=List.copyOf(outcomes);}}
    private final Thread owner=Thread.currentThread();private final LinkedHashMap<Key,Contact> contacts=new LinkedHashMap<>();private final ArrayDeque<Outcome> outcomes=new ArrayDeque<>();private double pressure,updatedAt;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Behavior memory outside worker");}
    public void observe(Perception f,Player p,Player visible){owned();double now=f.createdAt();pressure*=Math.exp(-Math.max(0,now-updatedAt)/4);updatedAt=now;contacts.values().removeIf(c->c.expiresAt()<=now);for(var event:f.stimuli().subList(0,Math.min(24,f.stimuli().size()))){if(event.expiresAt()<=now||event.createdAt()>now||event.team()==p.team()||distance(p.position(),event.position())>80||!Set.of(StimulusKind.SHOT,StimulusKind.EXPLOSION,StimulusKind.BLOCK_DESTROYED,StimulusKind.TEAM_SIGHTING).contains(event.kind()))continue;contacts.put(new Key((int)Math.floor(event.position().x()/8),(int)Math.floor(event.position().y()/8),event.kind().name().toLowerCase(Locale.ROOT)),new Contact(event.position(),Math.min(now+6,event.expiresAt()),.45,Math.max(2,event.uncertainty())));}if(visible!=null)contacts.put(new Key(visible.id(),visible.generation(),"seen"),new Contact(visible.position(),now+6,1,.75));if(now-p.lastDamageAt()>=0&&now-p.lastDamageAt()<1&&p.lastDamageAt()>0)pressure=Math.min(1,pressure+.25);while(contacts.size()>24)contacts.remove(contacts.keySet().iterator().next());}
    public Contact contact(Vector at,double now){owned();return contacts.values().stream().filter(c->c.expiresAt()>now).max(Comparator.comparingDouble(c->c.confidence()/(1+distance(at,c.position())/30))).orElse(null);}
    public double penalty(String kind,Vector at,double now){owned();return Math.min(1,outcomes.stream().filter(o->o.kind().equals(kind)&&now-o.at()<(o.success()?20:45)&&distance(o.position(),at)<10).mapToDouble(o->o.success()?.3:.7).sum());}
    public void record(String kind,Vector at,double now,boolean success){owned();if(outcomes.size()>=16)outcomes.removeFirst();outcomes.addLast(new Outcome(kind,at,now,success));}
    public State snapshot(){owned();return new State(contacts,List.copyOf(outcomes),pressure,updatedAt);}
}
