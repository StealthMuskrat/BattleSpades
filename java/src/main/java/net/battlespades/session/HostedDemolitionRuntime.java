// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.entities.ProjectileSpec;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;

/** Real frozen terrain cells, authoritative build locks, and ownerless five-shell demolition strikes. */
public final class HostedDemolitionRuntime implements AutoCloseable {
    public enum Phase {WAITING,BUILDING,ACTIVE,AIRSTRIKE}
    public record Base(ObjectiveZone zone,Set<MutableWorld.Cell> objective,Set<MutableWorld.Cell> destroyed){public Base{objective=Set.copyOf(objective);destroyed=Set.copyOf(destroyed);}}
    public record EnemyDamage(int team,int count){}
    private static final List<Integer> TEAMS=List.of(2,3);
    private final StandardMatchDomain match;private final ApplicationHost host;private final DemolitionMode mode;private final SpawnGeometry geometry;private final ObjectiveScores scores;
    private final Map<Integer,ObjectiveZone> zones=new LinkedHashMap<>();private final Set<Integer> authored=new HashSet<>(),warnings=new HashSet<>();
    private final Map<Integer,Set<MutableWorld.Cell>> objectives=new HashMap<>(),destroyed=new HashMap<>(),selfDamaged=new HashMap<>();
    private final Map<PlayerLife,Integer> destroyCredit=new IdentityHashMap<>(),repairCredit=new IdentityHashMap<>();
    private MutableWorld.RemovalLease listener;private Phase phase=Phase.WAITING;private boolean dirty,launched,closed;private double buildEnd,strikeAt,endAt,nextTimer;private List<Integer> fallen=List.of();private Integer pendingWinner;
    public HostedDemolitionRuntime(StandardMatchDomain match,ApplicationHost host,DemolitionMode mode,SpawnGeometry geometry){this.match=match;this.host=host;this.mode=mode;this.geometry=geometry;scores=new ObjectiveScores(()->new ProfileTracker.Context(false,"dem",mode.clock().ended),()->mode.clock().retiring,()->match.binding().players(),match.scoreboard());}
    private boolean open(){return !mode.clock().ended&&!mode.clock().retiring;}
    private boolean current(PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(v->v==p);}
    private void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}
    private TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    public Phase phase(){return phase;}
    public Base base(int id){return new Base(zones.get(id),objectives.getOrDefault(id,Set.of()),destroyed.getOrDefault(id,Set.of()));}
    public double remaining(){return phase==Phase.BUILDING?Math.max(0,buildEnd-match.wall()):mode.clock().timeLimit>0?Math.max(0,mode.clock().timeLimit-(match.wall()-mode.clock().startTime)):0;}
    public void start(){if(closed)throw new IllegalStateException("Demolition closed");unsubscribe();zones.clear();authored.clear();warnings.clear();objectives.clear();destroyed.clear();selfDamaged.clear();destroyCredit.clear();repairCredit.clear();fallen=List.of();launched=false;pendingWinner=null;dirty=true;
        for(var id:TEAMS){var authoredZones=match.metadata().spawnLayout().bases().getOrDefault(id,List.of());ObjectiveZone z;if(!authoredZones.isEmpty()){z=ObjectiveZone.from(id-2,authoredZones.getFirst(),match.world().sourceZShift());authored.add(id);}else{var p=geometry.baseAnchor(id);z=ObjectiveZone.around(id-2,new Vector(p.x(),p.y(),p.z()),10,8,9);}zones.put(id,z);objectives.put(id,new HashSet<>());destroyed.put(id,new HashSet<>());selfDamaged.put(id,new HashSet<>());}
        buildEnd=match.wall()+mode.settings().buildSeconds();phase=mode.settings().buildSeconds()>0?Phase.BUILDING:Phase.ACTIVE;
        listener=match.world().observeTopology(this::mutation,e->match.failure("demolition topology",e));sendZones();if(phase==Phase.BUILDING)locks();else{freeze();progress();}timer();
    }
    private void freeze(){for(var id:TEAMS){var z=zones.get(id);var cells=objectives.get(id);cells.clear();destroyed.get(id).clear();if(authored.contains(id)){for(int x=z.x0();x<=z.x1();x++)for(int y=z.y0();y<=z.y1();y++)for(int h=z.z0();h<=z.z1();h++)if(match.world().solid(x,y,h))cells.add(new MutableWorld.Cell(x,y,h));}
            else{int cx=(int)Math.rint(z.center().x()),cy=(int)Math.rint(z.center().y());for(int x=Math.max(z.x0(),cx-2);x<=Math.min(z.x1(),cx+2);x++)for(int y=Math.max(z.y0(),cy-2);y<=Math.min(z.y1(),cy+2);y++)for(int h=z.z0();h<=z.z1();h++)if(match.world().solid(x,y,h)){cells.add(new MutableWorld.Cell(x,y,h));break;}}
            if(match.metadata().baseMinDestruction().getOrDefault(id,java.math.BigInteger.ZERO).signum()>0&&java.math.BigInteger.valueOf(cells.size()).compareTo(match.metadata().baseMinDestruction().getOrDefault(id,java.math.BigInteger.ZERO))<0)match.failure("demolition objective below authored minimum",new IllegalStateException("Team "+id+" has "+cells.size()+" objective cells"));}dirty=true;}
    private void mutation(MutableWorld.Cell cell,boolean solid){if(phase!=Phase.ACTIVE&&phase!=Phase.AIRSTRIKE)return;for(var id:TEAMS)if(objectives.get(id).contains(cell)){boolean changed=solid?destroyed.get(id).remove(cell):destroyed.get(id).add(cell);dirty|=changed;return;}}
    private MinimapZone zone(int id){var z=zones.get(id);return new MinimapZone(1,team(id).color(),z.x0(),z.x1(),z.y0(),z.y1(),z.z0(),z.z1(),1,1,phase==Phase.BUILDING?1:0);}
    private TeamProgress progress(int id){return new TeamProgress(id,destroyed.get(id).size(),Math.max(1,objectives.get(id).size()),0,0,true,false,true,false);}
    private void sendZones(){for(var id:TEAMS)broadcast(zone(id));}
    private void progress(){for(var id:TEAMS){broadcast(progress(id));double remaining=100*(1-destroyed.get(id).size()/(double)Math.max(1,objectives.get(id).size()));if(remaining<=75){if(warnings.add(id))for(var p:match.lives().players())if(p.team()==id&&host.inGame(p.id()))host.sendPlayer(p.id(),Announcements.localised("REPAIR_BASE",List.of(),false,false));}else warnings.remove(id);}dirty=false;}
    private SimplePackets.LockToZone lockPacket(ObjectiveZone z){return z==null?new SimplePackets.LockToZone(0,0,0,511,511,239):new SimplePackets.LockToZone(z.x0(),z.y0(),z.z0(),z.x1(),z.y1(),z.z1());}
    public void lock(PlayerLife p){if(!current(p)||p.team()!=2&&p.team()!=3)return;var z=phase==Phase.BUILDING?zones.get(p.team()):null;if(p.alive()&&p.spawned()){if(z==null)p.body().unlock();else p.body().lock(new Vector(z.x0(),z.y0(),z.z0()),new Vector(z.x1(),z.y1(),z.z1()));}if(host.inGame(p.id()))host.sendPlayer(p.id(),lockPacket(z));}
    private void locks(){for(var p:match.lives().players())lock(p);}
    private void timer(){if(phase==Phase.BUILDING||mode.clock().timeLimit>0)match.scoreboard().timer(remaining(),true);nextTimer=match.wall()+1;}
    private void sound(int id,Vector at){try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(id,1,new GameplaySounds.Position(at.x(),at.y(),at.z()),.25f),null,true,null);}catch(RuntimeException e){match.failure("demolition sound",e);}}
    public void tick(){if(!open())return;double now=match.wall();if(now>=nextTimer)timer();if(phase==Phase.BUILDING&&now>=buildEnd){phase=Phase.ACTIVE;freeze();locks();sendZones();progress();broadcast(Announcements.localised("DEMOLITION_START",List.of(),false,false));timer();return;}
        if(phase==Phase.ACTIVE){if(dirty)progress();fallen=TEAMS.stream().filter(id->!objectives.get(id).isEmpty()&&destroyed.get(id).size()>=objectives.get(id).size()).toList();if(!fallen.isEmpty()){phase=Phase.AIRSTRIKE;strikeAt=now+5;for(var id:fallen)sound(9,zones.get(id).center());progress();}return;}
        if(phase!=Phase.AIRSTRIKE)return;if(!launched&&now>=strikeAt){for(var id:fallen){var c=zones.get(id).center();sound(GameplaySounds.flyby(match.metadata().environment().skybox()),c);for(var d:new int[][]{{0,0},{-5,0},{5,0},{0,-5},{0,5}})match.entities().projectiles().serverShot(ProjectileSpec.AIRSTRIKE,new Vector(c.x()+d[0],c.y()+d[1],Math.max(1,c.z()-48)),new Vector(0,0,100),null);}
            for(var p:match.lives().players())if(!p.bot()&&(p.team()==2||p.team()==3)&&host.inGame(p.id())){int id=fallen.contains(5-p.team())?5-p.team():fallen.getFirst();var c=zones.get(id).center();match.hud().focus(c,List.of(p));}
            pendingWinner=fallen.size()==1?5-fallen.getFirst():null;if(pendingWinner!=null){team(pendingWinner).score=1;match.scoreboard().teamScore(team(pendingWinner),69);}launched=true;endAt=now+3;return;}
        if(launched&&now>=endAt){if(pendingWinner==null){broadcast(Announcements.localised("GAME_DRAWN",List.of(),false,false));mode.end(null);}else mode.endByScore(pendingWinner);}
    }
    public Integer timeWinner(){double a=remainingFraction(2),b=remainingFraction(3);return a>b?Integer.valueOf(2):b>a?Integer.valueOf(3):null;}
    private double remainingFraction(int id){return 1-destroyed.getOrDefault(id,Set.of()).size()/(double)Math.max(1,objectives.getOrDefault(id,Set.of()).size());}
    private void award(PlayerLife p,long amount,int reason){if(current(p))scores.award(match.binding().row(p),amount,reason,null,Optional.of(new ObjectiveScores.Mode(mode.clock().ended,mode.clock().retiring)));}
    public void kill(Scoreboard.Player k,Scoreboard.Player v,int type){if(!open()||phase!=Phase.ACTIVE||!ObjectiveScores.eligible(k,v,type))return;var own=zones.get(k.team());var enemy=zones.get(v.team());String event=ObjectiveScores.classify(k,v,false,List.of(),List.of(own.center()),List.of(enemy.center()),20,20);if("defend".equals(event))award(k.life,100,71);else if("assault".equals(event))award(k.life,50,72);}
    public Optional<EnemyDamage> enemyObjectiveDamage(PlayerLife p,Collection<MutableWorld.Cell> cells){if(!open()||phase!=Phase.ACTIVE||!current(p)||p.team()!=2&&p.team()!=3)return Optional.empty();int enemy=5-p.team(),count=(int)cells.stream().filter(objectives.get(enemy)::contains).count();return count==0?Optional.empty():Optional.of(new EnemyDamage(enemy,count));}
    private void credit(PlayerLife p,int count,boolean repair){if(count==0||!current(p))return;var row=match.binding().row(p);match.profiles().add(row,repair?73:74,count,0);if(repair)match.demolitionRepair(p,count);var counters=repair?repairCredit:destroyCredit;int total=Math.addExact(count,counters.getOrDefault(p,0));counters.put(p,total%50);award(p,(total/50)*(repair?50L:25L),repair?70:69);}
    public void event(ModeEvents.Event e){var a=e.arguments();if(a.isEmpty()||!(a.getFirst() instanceof Scoreboard.Player row))return;var p=row.life;switch(e.name()){
        case "on_player_spawn","on_player_team_change"->lock(p);
        case "on_player_leave"->{destroyCredit.remove(p);repairCredit.remove(p);}
        case "on_blocks_destroyed","on_blocks_built"->{if(!open()||phase!=Phase.ACTIVE||!current(p)||p.team()!=2&&p.team()!=3)return;boolean repair=e.name().equals("on_blocks_built");int count=0;for(var value:(List<?>)a.get(1)){var c=(BlockDamageModel.Cell)value;var cell=new MutableWorld.Cell(c.x(),c.y(),c.z());if(repair){if(objectives.get(p.team()).contains(cell)&&!selfDamaged.get(p.team()).remove(cell))count++;}else{if(objectives.get(p.team()).contains(cell))selfDamaged.get(p.team()).add(cell);if(objectives.get(5-p.team()).contains(cell))count++;}}credit(p,count,repair);}
        default->{}
    }}
    public void reveal(Consumer<GamePacket> send){for(var id:zones.keySet()){send.accept(zone(id));if(phase!=Phase.BUILDING)send.accept(progress(id));}}
    public void revealLock(PlayerLife p,Consumer<GamePacket> send){if(p.team()==2||p.team()==3)send.accept(lockPacket(phase==Phase.BUILDING?zones.get(p.team()):null));}
    private void unsubscribe(){if(listener!=null){listener.close();listener=null;}}
    public void deactivate(){if(closed)return;unsubscribe();if(phase==Phase.BUILDING){phase=Phase.WAITING;locks();}for(var z:zones.values())broadcast(z.clear());phase=Phase.WAITING;}
    public void close(){if(closed)return;unsubscribe();for(var p:match.lives().players())if(p.alive()&&p.spawned())p.body().unlock();closed=true;zones.clear();objectives.clear();destroyed.clear();selfDamaged.clear();destroyCredit.clear();repairCredit.clear();}
}
