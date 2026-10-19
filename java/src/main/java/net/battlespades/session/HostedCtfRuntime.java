// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.entities.*;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.protocol.DropPickup.Position;
import net.battlespades.transport.enet.Delivery;
import org.tomlj.TomlTable;

/** Source CTF ground markers, carried identities, capture economy and recovery over the actual match world. */
public final class HostedCtfRuntime implements AutoCloseable {
    private final Thread owner=Thread.currentThread();private boolean closed;
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("CTF objectives outside active owner");}
    private static final List<Integer> TEAMS=List.of(2,3);
    private static final Map<String,Long> KILL_AMOUNTS=Map.of("intercept",50L,"carrier_defend",100L,"defend",50L,"assault",50L,"assault_enemy",50L,"distract",100L);
    private static final Map<String,Integer> KILL_REASONS=Map.of("intercept",58,"carrier_defend",57,"defend",54,"assault",55,"assault_enemy",56,"distract",53);
    private static final class Intel {Vector base,home,position;PlayerLife holder;double dropped;Double carryStarted,carryNext;boolean exposed;Integer entity;MinimapZone zone;}
    public record State(Vector base,Vector home,Vector position,PlayerLife holder,double dropped,boolean exposed,MinimapZone zone){}
    private final StandardMatchDomain match;private final ApplicationHost host;private final CtfMode mode;private final SpawnGeometry geometry;private final PickupWorld world;
    private final LinkedHashMap<Integer,Intel> intel=new LinkedHashMap<>();private final EscortTracker escorts=new EscortTracker(20,1);private final ObjectiveGuard guard;private final ObjectiveGuard.TrapTimer<Integer> traps=new ObjectiveGuard.TrapTimer<>();private final ObjectiveScores scores;
    private final double pitDepth;private double nextGuard;private final boolean endProtection;
    public HostedCtfRuntime(StandardMatchDomain match,ApplicationHost host,CtfMode mode,SpawnGeometry geometry,TomlTable raw){
        this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);this.mode=Objects.requireNonNull(mode);this.geometry=Objects.requireNonNull(geometry);world=new PickupWorld(match.world());for(int team:TEAMS)intel.put(team,new Intel());
        pitDepth=Math.max(0,Math.min(240,CtfMode.Settings.number(raw,"objectives.ctf_base_pit_depth",24)));endProtection=CtfMode.Settings.flag(raw,"objectives.objective_pickup_ends_spawn_protection",true);
        guard=new ObjectiveGuard(match.world(),new ObjectiveGuard.Settings(Math.max(0,Math.min(600,CtfMode.Settings.number(raw,"objectives.objective_entomb_seconds",5))),60,CtfMode.Settings.flag(raw,"objectives.objective_pickup_requires_los",true),endProtection),match::monotonic,p->false);
        scores=new ObjectiveScores(()->new ProfileTracker.Context(false,mode.settings().code(),mode.clock().ended),()->mode.clock().retiring,()->match.binding().players(),match.scoreboard());
    }
    public State state(int team){owned();var f=intel.get(team);return new State(f.base,f.home,f.position,f.holder,f.dropped,f.exposed,f.zone);}
    public boolean carrying(PlayerLife p){owned();return intel.values().stream().anyMatch(f->f.holder==p);}
    public boolean exposed(PlayerLife p){owned();return intel.values().stream().anyMatch(f->f.holder==p&&f.exposed);}
    private TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    private boolean connected(PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(current->current==p);}
    private void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}
    private void marker(PlayerLife p,boolean visible){host.broadcastKnown(MatchPackets.ChangePlayer.marker(p.id(),visible),Delivery.RELIABLE,p.id());}
    private Vector ground(double x,double y){try{var p=world.drySurfaceAnchor(x,y,24);return new Vector(p.x(),p.y(),p.z()-2.25);}catch(RuntimeException error){return new Vector(x,y,59.75);}}
    public void start(){owned();
        for(var f:intel.values())if(f.holder!=null){if(f.exposed&&connected(f.holder))marker(f.holder,false);ObjectiveCarry.clear(f.holder);}
        escorts.reset();traps.clear();nextGuard=0;
        var registry=match.entities().registry();for(var e:registry.all())if(Set.of("base","intel").contains(e.kind)){registry.remove(e.id());if(e.alive&&e.wireVisible)match.entities().destroy(e.id());}
        for(int t:TEAMS){var f=intel.get(t);f.base=geometry.baseAnchor(t).toPhysics();f.holder=null;f.dropped=0;f.entity=null;f.carryStarted=f.carryNext=null;f.exposed=false;}
        for(int t:TEAMS){var f=intel.get(t);var enemy=intel.get(5-t);var zones=match.metadata().spawnLayout().bases().getOrDefault(t,List.of());var zone=zones.isEmpty()?null:zones.getFirst();double distance=mode.settings().classic()?3:zone==null?12:0;
            double dx=enemy.base.x()-f.base.x(),dy=enemy.base.y()-f.base.y(),length=Math.hypot(dx,dy),ux=length<1e-6?t==2?1:-1:dx/length,uy=length<1e-6?0:dy/length;
            if(zone!=null&&distance>0){double limit=distance;double[] p={f.base.x(),f.base.y()},u={ux,uy},lo={zone.x()+zone.x0(),zone.y()+zone.y0()},hi={zone.x()+zone.x1(),zone.y()+zone.y1()};for(int i=0;i<2;i++){if(u[i]>1e-9)limit=Math.min(limit,(hi[i]-.5-p[i])/u[i]);else if(u[i]<-1e-9)limit=Math.min(limit,(lo[i]+1.5-p[i])/u[i]);}distance=Math.max(Math.min(distance,3),limit);}
            f.position=f.home=ground(f.base.x()+ux*distance,f.base.y()+uy*distance);team(t).intel(f.home);f.zone=zone(t,zone,f.base);
            var spec=new EntityRegistry.Placement();spec.kind="base";spec.state=t;spec.wireVisible=false;var at=world.drySurfaceAnchor(f.base.x(),f.base.y(),24);registry.place(1,at.x(),at.y(),at.z(),spec);entity(t,true);broadcast(f.zone);
        }
    }
    private static int bound(double value,int max){return Math.max(0,Math.min(max,(int)Math.rint(value)));}
    private MinimapZone zone(int t,SpawnGeometry.Zone z,Vector base){double shift=match.world().sourceZShift();return new MinimapZone(1,team(t).color(),bound(z==null?base.x()-5:z.x()+z.x0(),511),bound(z==null?base.x()+5:z.x()+z.x1(),511),bound(z==null?base.y()-5:z.y()+z.y0(),511),bound(z==null?base.y()+5:z.y()+z.y1(),511),bound(z==null?base.z()-3:z.z()+z.z0()+shift,239),bound(z==null?base.z()+6:z.z()+z.z1()+shift,239),1,6,0);}
    private void entity(int t,boolean visible){var f=intel.get(t);var registry=match.entities().registry();if(f.entity!=null){var e=registry.get(f.entity);if(e!=null&&e.kind.equals("intel")&&e.state==t){registry.remove(e.id());match.entities().destroy(e.id());}f.entity=null;}if(!visible)return;var at=world.drySurfaceAnchor(f.position.x(),f.position.y(),24);var spec=new EntityRegistry.Placement();spec.kind="intel";spec.state=t;var e=registry.place(16,at.x(),at.y(),at.z(),spec);f.entity=e.id();if(match.entities().wireReady())match.entities().create(e);}
    private void clearCarry(Intel f){if(f.holder!=null)escorts.forget(f.holder);f.carryStarted=f.carryNext=null;f.exposed=false;}
    private void award(PlayerLife p,long amount,int reason){scores.award(match.binding().row(p),amount,reason,null,Optional.of(new ObjectiveScores.Mode(mode.clock().ended,mode.clock().retiring)));}
    private void announce(PlayerLife personal,Integer team,String key,List<String> parameters,boolean localise,PlayerLife exclude){var packet=Announcements.localised(key,parameters,localise,false);if(personal!=null){if(host.inGame(personal.id()))host.sendPlayer(personal.id(),packet);return;}for(var p:match.lives().players())if(p!=exclude&&(team==null||p.team()==team)&&host.inGame(p.id()))host.sendPlayer(p.id(),packet);}
    private void sound(int id,Vector at){try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(id,1,at==null?null:new GameplaySounds.Position(at.x(),at.y(),at.z()),1),null,true,null);}catch(RuntimeException error){match.failure("CTF audio",error);}}
    private void relative(int team){try{match.runtime().gameplay().sounds().relative(team,2,3,1);}catch(RuntimeException error){match.failure("CTF stinger",error);}}
    public void reveal(Consumer<GamePacket> send){owned();for(var f:intel.values()){if(f.zone!=null)send.accept(f.zone);if(connected(f.holder)){send.accept(new SimplePackets.PickPickup(f.holder.id(),16,1));if(f.exposed)send.accept(MatchPackets.ChangePlayer.marker(f.holder.id(),true));}}}
    public boolean atBase(PlayerLife p,int t){owned();var f=intel.get(t);var z=f.zone;var at=p.position();if(at.x()<z.x0()-.5||at.x()>z.x1()+.5||at.y()<z.y0()-.5||at.y()>z.y1()+.5)return false;double dz=at.z()-f.base.z();if(Math.abs(dz)<=6)return true;if(dz<=6||dz>pitDepth)return false;int x=(int)at.x(),y=(int)at.y();return x>=0&&x<512&&y>=0&&y<512&&match.world().surfaceZ(x,y)>=(int)at.z();}
    public void tick(){owned();
        if(mode.clock().ended||mode.clock().retiring)return;double now=match.wall();recover();
        if(mode.settings().autoReturn())for(int t:TEAMS){var f=intel.get(t);if(f.holder==null&&f.dropped>0&&now-f.dropped>=60)returnIntel(t,null);}
        for(var p:match.lives().players()){
            if(mode.clock().ended)return;if(!p.alive()||!TEAMS.contains(p.team()))continue;var own=intel.get(p.team());int enemy=5-p.team();var flag=intel.get(enemy);
            if(mode.settings().touchReturn()&&own.holder==null&&own.dropped>0&&ObjectiveScores.within(p.position(),own.position,3)&&guard.pickupVisible(p,own.position,true,0))returnIntel(p.team(),p);
            if(flag.holder==null&&ObjectiveScores.within(p.position(),flag.position,3)&&guard.pickupVisible(p,flag.position,true,0)&&now-flag.dropped>2.5)pickup(p,enemy);
            if(flag.holder==p&&atBase(p,p.team())&&(!mode.settings().ownIntelRequired()||own.holder==null&&own.dropped<=0))capture(p,enemy);
        }
        if(!mode.clock().ended)carriers(now);
    }
    public boolean pickup(PlayerLife p,int t){owned();var f=intel.get(t);if(mode.clock().ended||f.holder!=null||!connected(p)||!p.alive()||p.team()!=5-t||!ObjectiveCarry.pickup(p,16,true,t,this::broadcast))return false;boolean home=f.dropped==0&&f.position.equals(f.home);clearCarry(f);f.holder=p;f.dropped=0;if(home)award(p,100,52);team(t).pickup(p);entity(t,false);if(endProtection)p.endProtection(match.monotonic());announce(p,null,"CTF_YOU_HAVE_FLAG",List.of(),false,null);announce(null,p.team(),"CTF_TEAM_HAS_FLAG",List.of(p.name()),false,p);announce(null,t,"CTF_ENEMY_HAS_FLAG",List.of(p.name()),false,null);sound(12,p.position());return true;}
    private void capture(PlayerLife p,int t){var f=intel.get(t);ObjectiveCarry.drop(p,position(p.position()),new Position(0,0,0),match::frameLoop,this::broadcast);boolean exposed=f.exposed;clearCarry(f);if(exposed)marker(p,false);f.holder=null;f.position=f.home;f.dropped=0;team(t).returnHome(f.home);entity(t,true);match.binding().row(p).captures++;award(p,10,49);var scoring=team(p.team());scoring.addCapture();match.scoreboard().teamScore(scoring,49);announce(null,p.team(),"CTF_TEAM_SCORE",List.of(p.name()),false,null);announce(null,t,"CTF_ENEMY_SCORE",List.of(p.name()),false,null);relative(p.team());if(scoring.score>=mode.settings().scoreLimit())mode.endByScore(p.team());}
    public boolean drop(PlayerLife p,Position at,Position velocity,boolean forced){owned();for(int t:TEAMS){var f=intel.get(t);if(f.holder!=p)continue;boolean live=connected(p);var dropped=live?ObjectiveCarry.drop(p,at,velocity,match::frameLoop,this::broadcast):null;if(dropped==null&&!forced)return true;double x=dropped==null?at.x():dropped.position().x(),y=dropped==null?at.y():dropped.position().y();if(!Double.isFinite(x)||!Double.isFinite(y)){x=p.position().x();y=p.position().y();}if(dropped==null)ObjectiveCarry.clear(p);boolean exposed=f.exposed;clearCarry(f);if(exposed&&live)marker(p,false);f.holder=null;f.position=ground(x,y);f.dropped=match.wall();team(t).drop(f.position);entity(t,true);return true;}return false;}
    private static Position position(Vector v){return new Position(v.x(),v.y(),v.z());}
    public void retire(PlayerLife p){owned();if(!carrying(p))return;drop(p,position(p.position()),position(p.body().state().velocity()),true);}
    public void returnIntel(int t,PlayerLife returner){owned();var f=intel.get(t);if(f.holder!=null)return;f.position=f.home;f.dropped=0;team(t).returnHome(f.home);entity(t,true);if(returner!=null)award(returner,1,0);sound(20,null);if(returner!=null&&!returner.name().isEmpty())announce(null,null,"CTF_FLAG_RETURNED",List.of(returner.name(),team(t).name),true,null);}
    private void carriers(double now){for(var f:intel.values()){var p=f.holder;if(!connected(p))continue;if(f.carryStarted==null){f.carryStarted=now;f.carryNext=now+5;continue;}if(!f.exposed&&now-f.carryStarted>=30){f.exposed=true;marker(p,true);}if(now<f.carryNext)continue;f.carryNext=now+5;if(!p.alive())continue;award(p,50,50);for(var escort:escorts.escorts(p,match.lives().players()))award(escort,10,51);}}
    public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){owned();
        if(!mode.clock().ended&&ObjectiveScores.eligible(killer,victim,type)){
            var defended=intel.get(killer.team());var attacked=intel.get(victim.team());var friendly=attacked.holder!=null&&attacked.holder.team()==killer.team()?List.of(match.binding().row(attacked.holder)):List.<Scoreboard.Player>of();
            var event=ObjectiveScores.classify(killer,victim,defended.holder==victim.life,friendly,defended.holder==null?List.of(defended.position):List.of(),attacked.holder==null?List.of(attacked.position):List.of(),10,20);scores.killEvent(killer,event,KILL_AMOUNTS,KILL_REASONS,Optional.of(new ObjectiveScores.Mode(false,mode.clock().retiring)));
            var enemy=defended.holder!=null&&defended.holder.team()==victim.team()?List.of(match.binding().row(defended.holder)):List.<Scoreboard.Player>of();if(ObjectiveScores.distraction(victim,killer,type,enemy,20))scores.killEvent(victim,"distract",KILL_AMOUNTS,KILL_REASONS,Optional.of(new ObjectiveScores.Mode(false,mode.clock().retiring)));
        }
        retire(victim.life);
    }
    private void recover(){double now=match.monotonic();if(now<nextGuard)return;nextGuard=now+1;for(int t:TEAMS){var f=intel.get(t);if(f.holder!=null){traps.due(t,false,now,guard.entombSeconds());continue;}if(!traps.due(t,guard.trapped(f.position,true)!=null,now,guard.entombSeconds()))continue;if(f.dropped>0&&mode.settings().autoReturn()){returnIntel(t,null);continue;}try{f.position=guard.resettle(f.position,true);if(f.dropped<=0){f.home=f.position;team(t).returnHome(f.position);}else team(t).drop(f.position);entity(t,true);}catch(RuntimeException error){match.failure("CTF objective recovery",error);}}}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("CTF objectives outside owner");if(closed)return;for(var f:intel.values())if(f.holder!=null)ObjectiveCarry.clear(f.holder);intel.clear();escorts.reset();traps.clear();closed=true;}
}
