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
import net.battlespades.world.MutableWorld.Cell;
import org.tomlj.TomlTable;

/** Common concrete transport, carry validation, identity and economy for carried-objective modes. */
public abstract class HostedCarryRuntime implements AutoCloseable {
    /** Required achievement observations; persistence/unlock policy is supplied by the service owner. */
    public record Observation(String event,PlayerLife player,PlayerLife other,int serial,boolean flag,Double seconds,Vector position,int team){}
    protected final StandardMatchDomain match;protected final ApplicationHost host;protected final CarryMode mode;protected final SpawnGeometry geometry;protected final ObjectiveGuard guard;protected final ObjectiveScores scores;protected boolean closed;private boolean disposing;
    protected HostedCarryRuntime(StandardMatchDomain m,ApplicationHost h,CarryMode mode,SpawnGeometry geometry,TomlTable raw){match=m;host=h;this.mode=mode;this.geometry=geometry;guard=new ObjectiveGuard(m.world(),new ObjectiveGuard.Settings(Math.max(0,Math.min(600,CtfMode.Settings.number(raw,"objectives.objective_entomb_seconds",5))),60,CtfMode.Settings.flag(raw,"objectives.objective_pickup_requires_los",true),CtfMode.Settings.flag(raw,"objectives.objective_pickup_ends_spawn_protection",true)),m::monotonic,p->m.escapeWatch().flagged(p));scores=new ObjectiveScores(()->new ProfileTracker.Context(false,mode.settings().code(),mode.clock().ended),()->mode.clock().retiring,()->m.binding().players(),m.scoreboard());}
    protected void owned(){if(closed)throw new IllegalStateException("Carried objectives closed");match.mode();}
    protected boolean open(){return !mode.clock().ended&&!mode.clock().retiring;}
    protected boolean current(PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(v->v==p);}
    protected boolean active(PlayerLife p){return current(p)&&p.alive()&&p.spawned()&&(p.team()==2||p.team()==3);}
    protected TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    protected void broadcast(GamePacket p){if(disposing)return;host.broadcast(p,Delivery.RELIABLE);}
    protected void personal(PlayerLife p,GamePacket packet){if(current(p)&&host.inGame(p.id()))host.sendPlayer(p.id(),packet);}
    protected void tell(PlayerLife p,String id,List<String> args){personal(p,Announcements.localised(id,args,false,false));}
    protected void teamMessage(int team,String id,List<String> args,PlayerLife except,boolean override){for(var p:match.lives().players())if(p.team()==team&&p!=except)personal(p,Announcements.localised(id,args,false,override));}
    protected void announce(String id,List<String> args,boolean localized){broadcast(Announcements.localised(id,args,localized,false));}
    protected void sound(int id,Vector at){try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(id,1,at==null?null:new GameplaySounds.Position(at.x(),at.y(),at.z()),1),null,true,null);}catch(RuntimeException e){match.failure("objective sound",e);}}
    protected void relative(int team,int good){try{match.runtime().gameplay().sounds().relative(team,good,3,1);}catch(RuntimeException e){match.failure("objective relative sound",e);}}
    protected void award(PlayerLife p,long points,int reason){if(current(p))scores.award(match.binding().row(p),points,reason,null,Optional.of(new ObjectiveScores.Mode(mode.clock().ended,mode.clock().retiring)));}
    protected void observation(String event,PlayerLife p,PlayerLife other,int serial,boolean flag,Double seconds,Vector at){match.carryObservation(new Observation(event,p,other,serial,flag,seconds,at,p==null?1:p.team()));}
    protected static Position position(Vector v){return new Position(v.x(),v.y(),v.z());}
    protected Vector surface(double x,double y){return new PickupWorld(match.world()).drySurfaceAnchor(x,y,24);}
    protected Vector anchor(int team){return geometry.baseAnchor(team).toPhysics();}
    protected Vector ground(double x,double y){return geometry.dryGround(x,y,48).toPhysics();}
    protected int entity(int type,Vector at,String kind,double fuse){var v=new EntityRegistry.Placement();v.state=1;v.kind=kind;v.radius=.5;v.fuse=fuse;var e=match.entities().registry().place(type,at.x(),at.y(),at.z(),v);match.entities().create(e);return e.id();}
    protected void remove(int id,String kind){var e=match.entities().registry().get(id);if(e!=null&&e.kind.startsWith(kind)){if(!disposing)match.entities().destroy(id);match.entities().registry().remove(id);}}
    protected ObjectiveCarry.Dropped nativeDrop(PlayerLife p,Position at,Position speed,boolean forced){var result=ObjectiveCarry.drop(p,at,speed,match::frameLoop,this::broadcast);if(result==null&&forced)ObjectiveCarry.clear(p);return result;}
    protected void teamScore(int id,long amount,int reason){var t=team(id);t.addScore(amount);match.scoreboard().teamScore(t,reason);}
    protected void checkScore(int id){if(mode.settings().target()>0&&team(id).score>=mode.settings().target())mode.endByScore(id);}
    public abstract void start();public abstract void tick();public abstract void clear();public abstract boolean carrying(PlayerLife p);public abstract boolean drop(PlayerLife p,Position at,Position speed,boolean forced,Integer oldTeam);public abstract void death(Scoreboard.Player v,Scoreboard.Player k,int type);public abstract void reveal(Consumer<GamePacket> send);
    public void retire(PlayerLife p,Integer oldTeam){owned();if(!carrying(p))return;drop(p,position(p.position()),position(p.body().state().velocity()),true,oldTeam);}
    public void revealCarriers(Consumer<GamePacket> send){owned();for(var packet:ObjectiveCarry.reveal(match.lives().players().stream().filter(this::carrying).toList()))send.accept(packet);}
    public void startCue(PlayerLife p,Consumer<GamePacket> send){}
    public void spawned(PlayerLife p){}public void mined(PlayerLife p,List<Cell> cells,boolean mined){}
    public void close(){if(closed)return;disposing=true;try{clear();}finally{closed=true;}}
}
