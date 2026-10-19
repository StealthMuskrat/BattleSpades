// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;
/** Actual survivor/infected bodies, grave-free conversion, population recovery and fixed-count rounds. */
public final class HostedZombieRuntime implements AutoCloseable {
    public enum Phase {WAITING,COUNTDOWN,ACTIVE,INTERMISSION,RESETTING}
    public record RoundObservation(boolean started,List<PlayerLife> players){public RoundObservation{players=List.copyOf(players);}}
    private static final List<Integer> SURVIVORS=List.of(0,1,12,3,2,16,17);
    private final StandardMatchDomain match;private final ApplicationHost host;private final ZombieMode mode;private final ZombieMode.Settings settings;private final OwnerLoop loop;private final SourceRandom random;private final GameRules rules;
    private final Map<PlayerLife,ClassCatalog.Selection> selections=new IdentityHashMap<>();private final Map<PlayerLife,Vector> infectionSpawns=new IdentityHashMap<>();private final Set<PlayerLife> zeros=Collections.newSetFromMap(new IdentityHashMap<>()),pendingProtection=Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<PlayerLife> reset=new ArrayDeque<>();private Phase phase=Phase.WAITING;private PlayerLife last;private boolean pickSound,musicChanged,closed;private int rounds;private double infectionAt=Double.POSITIVE_INFINITY,nextSurvival=Double.POSITIVE_INFINITY,nextLast=Double.POSITIVE_INFINITY,nextVisibility,nextTimer;private CompletableFuture<Void> timer;private long generation;
    public HostedZombieRuntime(StandardMatchDomain match,ApplicationHost host,ZombieMode mode,OwnerLoop loop,SourceRandom random,GameRules rules){this.match=match;this.host=host;this.mode=mode;settings=mode.settings();this.loop=loop;this.random=random;this.rules=rules;}
    private boolean open(){return !closed&&!mode.clock().ended&&!mode.clock().retiring;}
    private boolean current(PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(v->v==p);}
    private List<PlayerLife> players(PlayerLife exclude){return match.lives().players().stream().filter(p->p!=exclude&&p.team()!=0).toList();}
    private List<PlayerLife> survivors(PlayerLife exclude){return players(exclude).stream().filter(p->p.team()==3).toList();}
    private List<PlayerLife> zombies(PlayerLife exclude){return players(exclude).stream().filter(p->p.team()==2).toList();}
    private void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}
    private void personal(PlayerLife p,GamePacket packet){if(current(p)&&!p.bot()&&host.inGame(p.id()))host.sendPlayer(p.id(),packet);}
    private void announce(String id){broadcast(Announcements.localised(id,List.of(),false,false));}
    private void tell(PlayerLife p,String id){personal(p,Announcements.localised(id,List.of(),false,true));}
    private void tellTeam(int team,String id){for(var p:players(null))if(p.team()==team)tell(p,id);}
    private void sound(int id){try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(id,1,null,1),null,true,null);}catch(RuntimeException e){match.failure("zombie sound",e);}}
    public Phase phase(){return phase;}public boolean active(){return phase==Phase.ACTIVE;}public boolean finalRound(){return rounds+1>=settings.rounds();}public int rounds(){return rounds;}
    public Optional<PlayerLife> lastSurvivor(){return Optional.ofNullable(last);}public Set<PlayerLife> patientZeros(){return Set.copyOf(zeros);}
    public int joinTeam(){return active()?2:3;}
    public List<Integer> survivorClasses(){return match.hardcore()?Hardcore.CLASSES:SURVIVORS;}
    public ClassCatalog.Selection selection(int team,ClassCatalog.Selection s){if(team==2)return zombieSelection();int id=survivorClasses().contains(s.classId())?s.classId():match.hardcore()?5:0;return match.hardcore()?Hardcore.normalize(id,s.loadout()):ClassCatalog.normalize(id,s.loadout(),s.prefabs(),s.ugcTools(),id,rules.disabledTools());}
    private ClassCatalog.Selection zombieSelection(){var d=ClassCatalog.get(4);return ClassCatalog.normalize(4,List.of(),d.prefabs(),List.of(),4,rules.disabledTools());}
    public boolean allowsSelection(PlayerLife p,ClassCatalog.Selection s){return p.team()==2?s.classId()==4:survivorClasses().contains(s.classId())&&(!active()||s.classId()==p.selection().classId());}
    public boolean allowsTeam(PlayerLife p,int team){return p.team()==0&&team==joinTeam();}
    public double respawnDelay(PlayerLife p,double normal){return active()?p.team()==2?settings.respawn():0:normal;}
    public Optional<Vector> spawn(PlayerLife p){var at=infectionSpawns.remove(p);return at==null?Optional.empty():Optional.of(match.spawns().resolver().resolve(p.team(),()->new SpawnGeometry.Position(at.x(),at.y(),at.z()),match.monotonic()).toPhysics());}
    public boolean converting(PlayerLife p){return active()&&p.team()==3;}
    public boolean objective(PlayerLife p){return active()&&p.team()==3;}
    public boolean marked(PlayerLife p){return last==p;}
    private void marker(PlayerLife p,boolean visible){if(current(p))host.broadcastKnown(MatchPackets.ChangePlayer.marker(p.id(),visible),Delivery.RELIABLE,p.id());}
    public void clearMarker(){if(last!=null)marker(last,false);last=null;}
    public void visibility(Consumer<GamePacket> send){send.accept(new SimplePackets.TeamMapVisibility(2,1));send.accept(new SimplePackets.TeamMapVisibility(3,0));}
    private void locks(){broadcast(new SimplePackets.LockTeam(2,active()?0:1));broadcast(new SimplePackets.LockTeam(3,active()?1:0));broadcast(new SimplePackets.TeamLockClass(3,active()?1:0));}
    private void move(PlayerLife p,int team){for(var t:match.teams())t.remove(p);match.lives().modeTeam(p,team);match.teams().stream().filter(t->t.id==team).findFirst().orElseThrow().add(p);}
    private void survivor(PlayerLife p){move(p,3);var s=selections.remove(p);p.apply(s==null?selection(3,p.selection()):s);if(p.pendingSelection().isPresent()&&!survivorClasses().contains(p.pendingSelection().orElseThrow().classId()))p.clearPendingSelection();}
    private void zombie(PlayerLife p){move(p,2);p.apply(zombieSelection());p.clearPendingSelection();}
    public void infect(PlayerLife p,boolean zero){if(!open()||!current(p)||p.team()==2)return;if(survivorClasses().contains(p.selection().classId()))selections.put(p,selection(3,p.selection()));infectionSpawns.put(p,p.position());if(p.alive())match.lives().die(p.id(),null,9);zombie(p);if(zero){zeros.add(p);pendingProtection.add(p);}marker(p,false);visibility(this::broadcast);if(!p.alive())match.lives().respawn(p.id());tell(p,"YOU_HAVE_BEEN_INFECTED");}
    public void spawned(PlayerLife p){if(p.team()==2)marker(p,false);if(pendingProtection.remove(p)&&p.team()==2)p.protectionCap(.5);}
    private void state(){phase=Phase.WAITING;infectionAt=Double.POSITIVE_INFINITY;zeros.clear();pendingProtection.clear();nextSurvival=nextLast=Double.POSITIVE_INFINITY;infectionSpawns.clear();mode.clock().reset(match.wall());nextTimer=0;}
    public void start(){rounds=0;selections.clear();state();locks();visibility(this::broadcast);for(var p:players(null))survivor(p);arm(match.wall());}
    private void arm(double now){if(!open()||phase!=Phase.WAITING||players(null).size()<settings.minimum())return;phase=Phase.COUNTDOWN;infectionAt=now+Math.max(0,settings.infection());pickSound=false;pick(now);publishTimer();for(var p:players(null))tell(p,p.team()==2?"ZOMBIE_START_ZOMBIE":"ZOMBIE_START_SURVIVOR");announce("ZOMBIE_VIRUS_RELEASED");}
    private void pick(double now){if(phase==Phase.COUNTDOWN&&!pickSound&&infectionAt-now<=4){pickSound=true;sound(29);}}
    private void outbreak(double now){var candidates=survivors(null).stream().filter(p->p.alive()&&p.spawned()).toList();if(candidates.size()<2){if(players(null).size()<settings.minimum()){phase=Phase.WAITING;infectionAt=Double.POSITIVE_INFINITY;}else infectionAt=now+1;return;}int count=Math.min(settings.first(),candidates.size()-1);var settled=candidates.stream().filter(p->p.bot()||host.inGame(p.id())).toList();var selected=random.sample(settled.size()>=count?settled:candidates,count);phase=Phase.ACTIVE;infectionAt=Double.POSITIVE_INFINITY;mode.clock().reset(now);nextSurvival=now+10;nextLast=now+5;locks();visibility(this::broadcast);for(var p:selected)infect(p,true);match.zombieRound(new RoundObservation(true,selected));tellTeam(3,"ZOMBIE_INFECTION_DETECTED");sound(28);publishTimer();refresh(null);}
    public double remaining(){if(phase==Phase.COUNTDOWN)return Math.max(0,infectionAt-match.wall());if(!active())return 0;return settings.seconds()>0?Math.max(0,settings.seconds()-(match.wall()-mode.clock().startTime)):Double.POSITIVE_INFINITY;}
    private void publishTimer(){match.scoreboard().timer(Double.isFinite(remaining())?remaining():0,true);nextTimer=match.wall()+1;}
    public void beforeClock(){if(!open())return;double now=match.wall();if(now>=nextVisibility){visibility(this::broadcast);nextVisibility=now+3;}if(phase==Phase.INTERMISSION)return;if(phase==Phase.RESETTING){drain(now);return;}if(phase==Phase.WAITING)arm(now);if(phase==Phase.COUNTDOWN&&players(null).size()<settings.minimum()){phase=Phase.WAITING;infectionAt=Double.POSITIVE_INFINITY;}pick(now);if(phase==Phase.COUNTDOWN&&now>=infectionAt)outbreak(now);if(active())abort(null);}
    public void tick(){if(!open())return;if(match.wall()>=nextTimer)publishTimer();if(active()){periodic(match.wall());population(null);}}
    private void award(PlayerLife p,long amount,int reason){if(!current(p)||amount<=0)return;var row=match.binding().row(p);row.score=Math.addExact(row.score,amount);match.scoreboard().playerScore(row,reason);}
    private void periodic(double now){var all=survivors(null).stream().filter(p->p.alive()&&p.spawned()).toList();if(all.isEmpty())return;var eligible=all.stream().filter(p->!match.escapeWatch().flagged(p)).toList();if(now>=nextSurvival){long intervals=1+(long)Math.floor((now-nextSurvival)/10);for(var p:eligible)award(p,intervals*50,59);nextSurvival+=intervals*10;}if(now>=nextLast){long intervals=1+(long)Math.floor((now-nextLast)/5);if(eligible.contains(last))award(last,intervals*150,60);nextLast+=intervals*5;}}
    public void death(PlayerLife p,PlayerLife killer,int type){if(!open()||LifeCatalog.TRANSITIONS.contains(type)||!active()||p.team()!=3||!current(p))return;if(killer!=null&&killer!=p&&killer.team()==2)award(killer,100,61);infect(p,false);population(null);}
    public void kill(PlayerLife killer,PlayerLife victim,int type){if(last==killer)award(killer,50,62);}
    private void refresh(PlayerLife exclude){var survivors=survivors(exclude);var next=survivors.size()==1?survivors.getFirst():null;if(next==last)return;clearMarker();if(next==null)return;last=next;marker(next,true);nextLast=match.wall()+5;announce("LAST_MAN_STANDING");tell(next,"ZOMBIE_LAST_MAN");musicChanged|=match.media().music().lastMan();}
    private void population(PlayerLife exclude){if(!open()||!active())return;if(survivors(exclude).isEmpty()&&!players(exclude).isEmpty()){finish(2,"ZOMBIE_WIN");return;}refresh(exclude);}
    private void restoreMusic(){if(mode.clock().timeoutMusicPlayed||musicChanged){match.restoreGameplayMusic();mode.clock().timeoutMusicPlayed=false;musicChanged=false;}}
    private boolean abort(PlayerLife exclude){if(!active()||players(exclude).size()>=settings.minimum())return false;var remaining=players(exclude);clearMarker();restoreMusic();state();locks();for(var p:remaining)if(p.team()!=3){if(p.alive())match.lives().die(p.id(),null,9);survivor(p);}return true;}
    public void timeout(){if(!open())return;finish(survivors(null).isEmpty()?2:3,survivors(null).isEmpty()?"ZOMBIE_WIN":"SURVIVOR_WIN");}
    private void finish(int winner,String cue){if(!open()||phase==Phase.INTERMISSION||phase==Phase.RESETTING)return;var bonus=winner==3?survivors(null):List.<PlayerLife>of();match.zombieRound(new RoundObservation(false,bonus));phase=Phase.INTERMISSION;infectionAt=Double.POSITIVE_INFINITY;locks();clearMarker();rounds++;var team=match.teams().stream().filter(t->t.id==winner).findFirst().orElseThrow();team.addScore(1);match.scoreboard().teamScore(team,0);announce(cue);if(winner==3&&rounds<settings.rounds())musicChanged|=match.media().music().ending();for(var p:bonus)award(p,200,59);publishTimer();if(rounds>=settings.rounds()){mode.end(RoundClock.timeWinner(match.teams()));return;}long token=generation;timer=loop.sleep(settings.intermission()).toCompletableFuture();timer.whenComplete((unused,error)->{if(token!=generation)return;timer=null;if(error!=null||!open())return;try{begin();}catch(RuntimeException e){match.failure("zombie restart",e);mode.end(null);}});}
    private void begin(){clearMarker();restoreMusic();state();visibility(this::broadcast);reset.clear();reset.addAll(players(null));if(!reset.isEmpty())phase=Phase.RESETTING;locks();if(reset.isEmpty())arm(match.wall());}
    private void drain(double now){int budget=settings.resetBudget();while(budget-->0&&!reset.isEmpty()){var p=reset.removeFirst();if(!current(p)||p.team()==0)continue;try{if(p.alive())match.lives().die(p.id(),null,p.team()==2?9:10);survivor(p);match.lives().respawn(p.id());}catch(RuntimeException e){match.failure("zombie round respawn:"+p.id(),e);}}if(reset.isEmpty()){phase=Phase.WAITING;arm(now);}}
    public void event(ModeEvents.Event e){if(e.arguments().isEmpty()||!(e.arguments().getFirst() instanceof Scoreboard.Player row))return;var p=row.life;switch(e.name()){
        case "on_player_join"->{if(!current(p)||p.team()==0)return;if(active()&&p.team()!=3)zombie(p);else{survivor(p);arm(match.wall());}if(host.inGame(p.id()))visibility(packet->personal(p,packet));}
        case "on_player_spawn"->spawned(p);
        case "on_player_team_change"->{if(!current(p)||p.team()!=(int)e.arguments().get(2))return;if(p.team()==2)zombie(p);else if(p.team()==3){survivor(p);arm(match.wall());}}
        case "on_player_leave"->{zeros.remove(p);pendingProtection.remove(p);infectionSpawns.remove(p);selections.remove(p);reset.removeIf(v->v==p);if(last==p)last=null;if(!open())return;if(phase==Phase.COUNTDOWN&&players(p).size()<settings.minimum()){phase=Phase.WAITING;infectionAt=Double.POSITIVE_INFINITY;}if(!active()||abort(p))return;if(zombies(p).isEmpty()&&!survivors(p).isEmpty()){infect(random.choice(survivors(p)),true);tellTeam(3,"ZOMBIE_INFECTION_DETECTED");}population(p);}
        default->{}
    }}
    public void reveal(PlayerLife p,Consumer<GamePacket> send){visibility(send);for(var z:zombies(null))send.accept(MatchPackets.ChangePlayer.marker(z.id(),false));if(p.team()!=0)send.accept(Announcements.localised(p.team()==2?"ZOMBIE_START_ZOMBIE":"ZOMBIE_START_SURVIVOR",List.of(),false,false));if(current(last))send.accept(MatchPackets.ChangePlayer.marker(last.id(),true));send.accept(new SimplePackets.DisplayCountdown((float)(Double.isFinite(remaining())?Scoreboard.hudSeconds(remaining()):0)));}
    public boolean botRetireSafe(PlayerLife p){return !open()||!active()||p.team()==0||(p.team()==2?zombies(p):survivors(p)).size()>0;}
    public void cancelReset(){generation++;var old=timer;timer=null;if(old!=null)old.cancel(false);reset.clear();}
    public void deactivate(){if(closed)return;cancelReset();clearMarker();infectionSpawns.clear();selections.clear();}
    public void close(){if(closed)return;cancelReset();closed=true;last=null;infectionSpawns.clear();selections.clear();zeros.clear();pendingProtection.clear();}
}
