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

/** Source VIP roster identities, live promotion, bounded resets and final-minute sudden death. */
public final class HostedVipRuntime implements AutoCloseable {
    public enum Phase {WAITING,RESETTING,SELECTING,ACTIVE,INTERMISSION}
    public record RoundObservation(boolean started,Map<Integer,PlayerLife> vips,Set<Integer> alive){public RoundObservation{vips=Map.copyOf(vips);alive=Set.copyOf(alive);}}
    private record Attacker(PlayerLife player,double at){}
    private record SpawnCache(SpawnGeometry.Zone zone,List<SpawnGeometry.Candidate> candidates){}
    private static final List<Integer> TEAMS=List.of(2,3),ORDINARY=List.of(6,7,8,9);
    private final StandardMatchDomain match;private final ApplicationHost host;private final VipMode mode;private final VipMode.Settings settings;private final OwnerLoop loop;private final SourceRandom random;private final GameRules rules;private final SpawnGeometry geometry;private final ObjectiveScores scores;
    private final Map<Integer,PlayerLife> vips=new LinkedHashMap<>();private final Set<Integer> alive=new HashSet<>(),respawns=new HashSet<>(),awarded=new HashSet<>(),revealed=new TreeSet<>(),lastMan=new HashSet<>();
    private final Map<Integer,Double> activateAt=new HashMap<>(),damageAt=new HashMap<>();private final Map<Integer,Map<Integer,Attacker>> attackers=new HashMap<>();private final Map<Integer,SpawnCache> city=new HashMap<>();
    private final ArrayDeque<PlayerLife> reset=new ArrayDeque<>();private Phase phase=Phase.WAITING;private CompletableFuture<Void> timer;private long generation;private boolean closed;private double selectionAt=Double.POSITIVE_INFINITY,deadline=Double.POSITIVE_INFINITY,nextAudit,nextSurvival,nextEscort,nextTimer;private int rounds;
    public HostedVipRuntime(StandardMatchDomain match,ApplicationHost host,VipMode mode,OwnerLoop loop,SourceRandom random,GameRules rules,SpawnGeometry geometry){this.match=match;this.host=host;this.mode=mode;settings=mode.settings();this.loop=loop;this.random=random;this.rules=rules;this.geometry=geometry;respawns.addAll(TEAMS);scores=new ObjectiveScores(()->new ProfileTracker.Context(false,"vip",mode.clock().ended),()->mode.clock().retiring,()->match.binding().players(),match.scoreboard());}
    private boolean open(){return !mode.clock().ended&&!mode.clock().retiring&&!closed;}
    private boolean current(PlayerLife p){return p!=null&&match.lives().players().stream().anyMatch(v->v==p);}
    private boolean playing(PlayerLife p){return p.team()==2||p.team()==3;}
    private void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}
    private void personal(PlayerLife p,GamePacket packet){if(current(p)&&host.inGame(p.id()))host.sendPlayer(p.id(),packet);}
    private void announce(String id,List<String> args,boolean local){broadcast(Announcements.localised(id,args,local,true));}
    private void tellTeam(int team,String id,List<String> args,PlayerLife exclude){for(var p:match.lives().players())if(p.team()==team&&p!=exclude)personal(p,Announcements.localised(id,args,false,false));}
    private TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    private List<PlayerLife> candidates(int team,PlayerLife exclude){return match.lives().players().stream().filter(p->p!=exclude&&p.team()==team&&(p.bot()||host.inGame(p.id()))).toList();}
    private boolean ready(PlayerLife exclude){return TEAMS.stream().allMatch(t->candidates(t,exclude).size()>=settings.minimum());}
    public Phase phase(){return phase;}public boolean active(){return phase==Phase.ACTIVE;}public int rounds(){return rounds;}
    public Optional<PlayerLife> vip(int team){return Optional.ofNullable(vips.get(team));}
    public boolean marked(PlayerLife p){return vips.values().stream().anyMatch(v->v==p);}
    public boolean liveVip(PlayerLife p){return p!=null&&vips.get(p.team())==p&&alive.contains(p.team());}
    public boolean canRespawn(PlayerLife p){return open()&&phase!=Phase.INTERMISSION&&phase!=Phase.RESETTING&&respawns.contains(p.team());}
    public double respawnDelay(PlayerLife p,double normal){if(active()&&liveVip(p))return 255;if(!canRespawn(p))return open()&&active()&&playing(p)?255:0;return normal;}
    public boolean allowsTeam(PlayerLife p){return !liveVip(p)&&(!open()||!active()||!playing(p)||respawns.contains(p.team()));}
    public ClassCatalog.Selection ordinary(ClassCatalog.Selection selection){int id=ORDINARY.contains(selection.classId())?selection.classId():random.choice(ORDINARY);return ClassCatalog.normalize(id,selection.loadout(),selection.prefabs(),selection.ugcTools(),id,rules.disabledTools());}
    public void prepareSpawn(PlayerLife p){if(playing(p)&&!liveVip(p)){p.clearPendingSelection();p.apply(ordinary(p.selection()));}}
    private void crown(PlayerLife p,boolean visible){if(current(p))host.broadcastKnown(MatchPackets.ChangePlayer.marker(p.id(),visible),Delivery.RELIABLE,p.id());}
    private void clearCrowns(){for(var p:vips.values())crown(p,false);}
    private void mapVisibility(int team,boolean visible){broadcast(new SimplePackets.TeamMapVisibility(team,visible?1:0));}
    private void clearSudden(boolean visibility){deadline=Double.POSITIVE_INFINITY;activateAt.clear();damageAt.clear();attackers.clear();if(visibility){for(var t:revealed)mapVisibility(t,false);revealed.clear();}}
    private void resetClock(){boolean music=mode.clock().timeoutMusicPlayed;mode.clock().reset(match.wall());if(music)match.restoreGameplayMusic();}
    public void start(){rounds=0;for(var p:match.lives().players())if(playing(p)){p.clearPendingSelection();p.apply(ordinary(p.selection()));}begin(false);}
    private void begin(boolean resetPlayers){if(!open())return;clearCrowns();vips.clear();alive.clear();respawns.clear();respawns.addAll(TEAMS);selectionAt=Double.POSITIVE_INFINITY;lastMan.clear();nextAudit=0;nextSurvival=nextEscort=Double.POSITIVE_INFINITY;reset.clear();clearSudden(true);awarded.clear();resetClock();nextTimer=0;
        if(resetPlayers){for(var p:match.lives().players())if(playing(p))reset.addLast(p);if(!reset.isEmpty()){phase=Phase.RESETTING;return;}}phase=Phase.WAITING;arm(match.monotonic(),null);
    }
    private void arm(double now,PlayerLife exclude){if(!open()||phase!=Phase.WAITING&&phase!=Phase.SELECTING)return;if(!ready(exclude)){phase=Phase.WAITING;selectionAt=Double.POSITIVE_INFINITY;return;}if(phase==Phase.WAITING){phase=Phase.SELECTING;selectionAt=now+settings.selection();announce("VIP_AWAITING_CHOICE",List.of(),false);}}
    private void promote(int team,PlayerLife p){p.clearPendingSelection();p.apply(ClassCatalog.normalize(team==2?10:11,List.of(),rules));if(p.alive()&&p.spawned()){var s=p.selection();host.broadcastKnown(new PlayerPackets.SetClassLoadout(p.id(),s.classId(),1,s.loadout(),s.prefabs(),s.ugcTools()),Delivery.RELIABLE,p.id());p.inventory().refreshEquipment();if(!s.loadout().isEmpty())p.inventory().tool(s.loadout().getLast());}
        else match.lives().respawn(p.id());match.roster().appearance(p.create());int hp=(int)Math.max(1,Math.min(255,Math.rint(100*settings.health())));p.health(hp);p.maxHealth(hp);var at=p.position();personal(p,new LifePackets.SetHP(hp,0,new Vector3f((float)at.x(),(float)at.y(),(float)at.z())));crown(p,true);tellTeam(team,"VIP_NAME_IS_VIP",List.of(p.name()),p);
    }
    private void select(){if(!ready(null)){phase=Phase.WAITING;selectionAt=Double.POSITIVE_INFINITY;return;}for(var t:TEAMS)vips.put(t,random.choice(candidates(t,null)));alive.clear();respawns.addAll(TEAMS);selectionAt=Double.POSITIVE_INFINITY;nextSurvival=match.monotonic()+10;nextEscort=match.monotonic()+5;
        // Both promotions run outside ACTIVE. Dead candidates retain the boss class through respawn preparation.
        for(var t:TEAMS){alive.add(t);promote(t,vips.get(t));}resetClock();phase=Phase.ACTIVE;match.vipRound(new RoundObservation(true,vips,alive));announce("VIP_START",List.of(),false);publishTimer();
    }
    public double remaining(){if(phase==Phase.INTERMISSION)return 0;double normal=settings.seconds()>0?Math.max(0,settings.seconds()-(match.wall()-mode.clock().startTime)):Double.POSITIVE_INFINITY;return Math.max(0,Math.min(normal,deadline-match.monotonic()));}
    private void publishTimer(){double remaining=remaining();if(Double.isFinite(remaining))match.scoreboard().timer(remaining,true);nextTimer=match.monotonic()+1;}
    public void timeout(){if(!open()||phase==Phase.INTERMISSION)return;if(!active()){resetClock();return;}finish(alive.size()==1?alive.iterator().next():null);}
    public void tick(){if(!open()||phase==Phase.INTERMISSION)return;double now=match.monotonic();if(now>=nextTimer)publishTimer();if(phase==Phase.RESETTING){int budget=settings.resetBudget();while(budget-->0&&!reset.isEmpty()){var p=reset.removeFirst();if(!current(p)||!playing(p))continue;try{p.clearPendingSelection();p.apply(ordinary(p.selection()));match.lives().respawn(p.id());}catch(RuntimeException e){match.failure("VIP round respawn:"+p.id(),e);}}if(reset.isEmpty()){phase=Phase.WAITING;arm(now,null);}return;}
        boolean audit=now>=nextAudit;if(audit)nextAudit=now+.25;if(audit&&(phase==Phase.WAITING||phase==Phase.SELECTING))arm(now,null);if(phase==Phase.SELECTING&&now>=selectionAt)select();if(!active())return;periodic(now);sudden(now);if(open()&&active()&&audit){for(var t:TEAMS){var p=vips.get(t);if(p!=null&&alive.contains(t)&&(!current(p)||p.team()!=t||!p.bot()&&!host.inGame(p.id())))depart(t,p);}elimination(null);}
    }
    public double incoming(PlayerLife p,double amount,PlayerLife source){if(!liveVip(p))return amount;if(source!=null&&source!=p&&playing(source)&&source.team()!=p.team()&&amount>0)attackers.computeIfAbsent(p.team(),t->new TreeMap<>()).put(source.id(),new Attacker(source,match.monotonic()));return amount>0?Math.max(1,Math.rint(amount*.5)):0;}
    private void award(PlayerLife p,long amount,int reason){if(current(p)&&amount>0)scores.award(match.binding().row(p),amount,reason,null,Optional.of(new ObjectiveScores.Mode(mode.clock().ended,mode.clock().retiring)));}
    public void kill(Scoreboard.Player k,Scoreboard.Player v,int type){if(!open()||!active()||!ObjectiveScores.eligible(k,v,type)||marked(v.life))return;var own=vips.get(k.team());if(own!=null&&own!=k.life&&alive.contains(k.team())&&current(own)&&own.alive()&&ObjectiveScores.within(v.life.position(),own.position(),20)){award(k.life,150,19);award(own,50,15);return;}var enemy=vips.get(v.team());if(enemy!=null&&alive.contains(v.team())&&current(enemy)&&enemy.alive()&&ObjectiveScores.within(v.life.position(),enemy.position(),20))award(k.life,50,17);}
    public void death(PlayerLife p,PlayerLife killer){if(!open())return;if(active()&&killer!=null&&killer!=p&&playing(p)&&playing(killer)&&killer.team()!=p.team()&&liveVip(killer))award(killer,100,16);if(active()&&liveVip(p))killVip(p.team(),p,killer);elimination(null);}
    private void killVip(int t,PlayerLife p,PlayerLife killer){if(!awarded.add(t))return;alive.remove(t);if(settings.sudden())respawns.remove(t);else respawns.add(t);var recent=attackers.remove(t);int scoring=5-t;team(scoring).addScore(1);match.scoreboard().teamScore(team(scoring),0);if(revealed.add(scoring))mapVisibility(scoring,true);if(settings.sudden()){activateAt.put(t,match.monotonic()+6);damageAt.remove(t);}crown(p,false);tellTeam(t,"VIP_KILLED_VIP_YOURTEAM",List.of(),null);tellTeam(scoring,"VIP_KILLED_VIP_OPPOSITION",List.of(),null);try{match.runtime().gameplay().sounds().relative(scoring,8,7,1);}catch(RuntimeException e){match.failure("VIP sound",e);}
        if(killer!=null&&killer!=p&&killer.team()!=t){award(killer,match.binding().row(p).score/10,14);if(recent!=null)for(var a:recent.values())if(a.player!=killer&&current(a.player)&&playing(a.player)&&a.player.team()!=t&&match.monotonic()-a.at<=10)award(a.player,50,18);}
        long a=team(2).score,b=team(3).score;if(settings.target()>0&&a==b&&a>=settings.target())finish(null);else if(!settings.sudden())finish(scoring);
    }
    private void periodic(double now){boolean survival=now>=nextSurvival,escort=now>=nextEscort;if(!survival&&!escort)return;if(survival)nextSurvival=now+10;if(escort)nextEscort=now+5;for(var t:TEAMS){var p=vips.get(t);if(p==null||!alive.contains(t)||!current(p)||!p.alive()||!p.spawned())continue;if(survival)award(p,50,12);if(escort)for(var other:match.lives().players())if(other!=p&&other.team()==t&&other.alive()&&other.spawned()&&ObjectiveScores.within(p.position(),other.position(),15))award(other,10,13);}}
    private void sudden(double now){if(Double.isFinite(deadline)&&now>=deadline)return;for(var t:TEAMS){if(!open()||!active())return;var activation=activateAt.get(t);if(activation!=null&&now>=activation){activateAt.remove(t);if(remaining()>60)deadline=now+60;publishTimer();if(!mode.clock().timeoutMusicPlayed){mode.clock().timeoutMusicPlayed=true;match.timeoutMusic();}damageAt.put(t,now);announce("VIP_SUDDEN_DEATH_ACTIVATED",List.of(team(t).name),true);}var damage=damageAt.get(t);if(damage==null||now<damage)continue;damageAt.put(t,now+1);for(var p:match.lives().players())if(p.team()==t&&p.alive()&&p.spawned()&&!p.god()){int hp=Math.max(0,p.health()-1);p.health(hp);p.modeDamageStamp(match.wall());var at=p.position();personal(p,new LifePackets.SetHP(hp,4,new Vector3f((float)at.x(),(float)at.y(),(float)at.z())));if(hp==0)match.lives().die(p.id(),null,26);}}}
    private void depart(int t,PlayerLife departed){if(settings.leave().equals("reassign")){var candidates=candidates(t,departed);var living=candidates.stream().filter(p->p.alive()&&p.spawned()).toList();if(!candidates.isEmpty()){var next=random.choice(living.isEmpty()?candidates:living);crown(departed,false);vips.put(t,next);alive.add(t);attackers.remove(t);try{promote(t,next);return;}catch(RuntimeException e){match.failure("VIP handover",e);vips.put(t,departed);}}}killVip(t,departed,null);}
    private void elimination(PlayerLife exclude){if(!open()||!active())return;var eliminated=new ArrayList<Integer>();for(var t:TEAMS){if(respawns.contains(t))continue;long n=match.lives().players().stream().filter(p->p!=exclude&&p.team()==t&&p.alive()&&p.spawned()).count();if(n==0)eliminated.add(t);else if(n==1&&lastMan.add(t))announce("VIP_LAST_MAN_STANDING",List.of(team(t).name),true);}if(eliminated.size()==1){announce("VIP_TEAM_WIPED_OUT",List.of(team(eliminated.getFirst()).name),true);finish(5-eliminated.getFirst());}else if(eliminated.size()==2)finish(alive.size()==1?alive.iterator().next():null);}
    private void finish(Integer winner){if(!open()||phase==Phase.INTERMISSION)return;match.vipRound(new RoundObservation(false,vips,alive));phase=Phase.INTERMISSION;match.scoreboard().timer(0,true);clearSudden(false);clearCrowns();lastMan.clear();rounds++;long a=team(2).score,b=team(3).score;if(settings.target()>0&&Math.max(a,b)>=settings.target()){if(a==b){announce("GAME_DRAWN",List.of(),false);mode.end(null);}else mode.endByScore(a>b?2:3);return;}announce(winner==null?"GAME_DRAWN":"TEAM_DEFEAT",winner==null?List.of():List.of(team(winner).name),winner!=null);long token=generation;timer=loop.sleep(settings.intermission()).toCompletableFuture();timer.whenComplete((unused,error)->{if(token!=generation)return;timer=null;if(error!=null||!open())return;try{begin(true);}catch(RuntimeException e){match.failure("VIP restart",e);mode.end(null);}});}
    public boolean botRetireSafe(PlayerLife p){return !open()||!active()||respawns.contains(p.team())||!p.alive()||!p.spawned()||match.lives().players().stream().anyMatch(v->v!=p&&v.team()==p.team()&&v.alive()&&v.spawned());}
    public void event(ModeEvents.Event e){if(e.arguments().isEmpty()||!(e.arguments().getFirst() instanceof Scoreboard.Player row))return;var p=row.life;switch(e.name()){
        case "on_player_join"->{if(phase==Phase.RESETTING&&playing(p)&&reset.stream().noneMatch(v->v==p))reset.addLast(p);arm(match.monotonic(),null);}
        case "on_player_leave"->{reset.removeIf(v->v==p);if(open()&&active()&&liveVip(p))depart(p.team(),p);elimination(p);arm(match.monotonic(),p);}
        case "on_player_team_change"->{elimination(null);arm(match.monotonic(),null);lockedOut(p,packet->personal(p,packet));}
        default->{}
    }}
    public void lockedOut(PlayerLife p,Consumer<GamePacket> send){if(open()&&active()&&playing(p)&&!alive.contains(p.team())&&!respawns.contains(p.team())&&!p.alive())send.accept(Announcements.localised("VIP_ALREADY_DEAD",List.of(),false,false));}
    public void reveal(Consumer<GamePacket> send){for(var t:TEAMS){var p=vips.get(t);if(p!=null&&alive.contains(t)&&current(p))send.accept(MatchPackets.ChangePlayer.marker(p.id(),true));}for(var t:revealed)send.accept(new SimplePackets.TeamMapVisibility(t,1));}
    public Optional<Vector> spawn(PlayerLife p){if(!java.nio.file.Path.of(match.metadata().environment().filename()).getFileName().toString().toLowerCase(Locale.ROOT).replace(".vxl","").equals("cityofchicago")||!playing(p))return Optional.empty();for(int pass=0;pass<2;pass++){var cache=city.computeIfAbsent(p.team(),this::city);for(var candidate:random.sample(cache.candidates,Math.min(pass==0?32:64,cache.candidates.size()))){var at=geometry.candidatePosition(candidate,List.of(cache.zone));if(at!=null)return Optional.of(at.toPhysics());}city.remove(p.team());}return Optional.empty();}
    private SpawnCache city(int team){double[] a=team==2?new double[]{326.469,458.450,223.745,352.547,485.533,233.741}:new double[]{30.452,66.451,224.742,53.549,106.548,227.750};double cx=(a[0]+a[3])/2,cy=(a[1]+a[4])/2,cz=(a[2]+a[5])/2+2.75;var z=new SpawnGeometry.Zone("spawn",team,cx,cy,cz,a[0]-cx,a[3]-cx,a[1]-cy,a[4]-cy,a[2]+2.75-cz-5,a[5]+2.75-cz+5,"vip_cityofchicago_surveyed_spawn");return new SpawnCache(z,geometry.zoneCandidates(z));}
    public void cancelReset(){generation++;var old=timer;timer=null;if(old!=null)old.cancel(false);reset.clear();}
    public void deactivate(){if(closed)return;cancelReset();clearSudden(true);clearCrowns();phase=Phase.WAITING;}
    public void close(){if(closed)return;cancelReset();closed=true;vips.clear();alive.clear();respawns.clear();attackers.clear();city.clear();}
}
