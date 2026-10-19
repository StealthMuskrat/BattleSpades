// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.battlespades.entities.ProjectileSpec;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;
import org.tomlj.TomlTable;

/** Actual TC capture, Multi-Hill rotation, personal/team economy and shared live resupply. */
public final class HostedZoneRuntime implements AutoCloseable {
    private static final List<Integer> TEAMS=List.of(2,3);
    private static final class Hill {
        final ObjectiveZone zone;int owner=1,attacker=1,lastOwner=1;double progress=.5;boolean contested,firstPaid;
        final Map<Integer,PlayerLife> occupants=new TreeMap<>();final Map<Integer,Integer> occupantTeams=new HashMap<>();final Map<Integer,Double> shouts=new HashMap<>();
        Hill(ObjectiveZone z){zone=z;}
    }
    /** Required achievement-service observations retain real bodies and zone identities. */
    public sealed interface HillEvent permits Scored,ContestWon,HillKill,Expired {}
    public record Scored(ObjectiveZone zone,PlayerLife player) implements HillEvent {}
    public record ContestWon(List<PlayerLife> players,boolean retained) implements HillEvent {public ContestWon{players=List.copyOf(players);}}
    public record HillKill(PlayerLife killer,PlayerLife victim,boolean killerInside,boolean victimInside) implements HillEvent {}
    public record Expired(ObjectiveZone zone,Map<Integer,PlayerLife> occupants,Map<Integer,Integer> teams) implements HillEvent {public Expired{occupants=Map.copyOf(occupants);teams=Map.copyOf(teams);}}
    public record State(ObjectiveZone zone,int owner,int attacker,double progress,boolean contested,Set<Integer> occupants){}
    private record Refill(PlayerLife player,double at){}
    private record Shell(Vector center,int dx,int dy,boolean first){}
    private final StandardMatchDomain match;private final ApplicationHost host;private final ZoneMode mode;private final ZoneMode.Settings settings;private final SpawnGeometry geometry;private final OwnerLoop loop;private final ObjectiveGuard guard;private final ObjectiveScores scores;
    private final List<Hill> hills=new ArrayList<>(),active=new ArrayList<>();private final Map<Integer,Refill> refills=new HashMap<>();private final ArrayDeque<Shell> shells=new ArrayDeque<>();
    private CompletableFuture<Void> timer;private long generation;private boolean closed,intermission;private int cursor;private double nextCapture,nextPersonal,lastTeamScore,nextRotation,nextActivation;
    public HostedZoneRuntime(StandardMatchDomain match,ApplicationHost host,ZoneMode mode,SpawnGeometry geometry,TomlTable raw,OwnerLoop loop){
        this.match=match;this.host=host;this.mode=mode;settings=mode.settings();this.geometry=geometry;this.loop=loop;
        guard=new ObjectiveGuard(match.world(),new ObjectiveGuard.Settings(5,Math.max(0,Math.min(3600,CtfMode.Settings.number(raw,"objectives.objective_afk_seconds",60))),true,true),match::monotonic,p->match.escapeWatch().flagged(p));
        scores=new ObjectiveScores(()->new ProfileTracker.Context(false,settings.code(),mode.clock().ended),()->mode.clock().retiring,()->match.binding().players(),match.scoreboard());
    }
    private void owned(){if(closed)throw new IllegalStateException("Zone objectives closed");match.mode();}
    private boolean open(){return !mode.clock().ended&&!mode.clock().retiring;}
    private TeamState team(int id){return match.teams().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    private void broadcast(GamePacket p){host.broadcast(p,Delivery.RELIABLE);}
    private boolean current(PlayerLife p){return match.lives().players().stream().anyMatch(v->v==p);}
    private void personal(PlayerLife p,GamePacket packet){if(current(p)&&host.inGame(p.id()))host.sendPlayer(p.id(),packet);}
    private static MatchPackets.LocalisedMessage message(String id,List<String> args,boolean override){return Announcements.localised(id,args,false,override);}
    private void tell(PlayerLife p,String id,List<String> args){personal(p,message(id,args,false));}
    private void tellTeam(int team,String id,List<String> args){for(var p:match.lives().players())if(p.team()==team)tell(p,id,args);}
    private void relative(int team){try{match.runtime().gameplay().sounds().relative(team,2,3,1);}catch(RuntimeException e){match.failure("objective sound",e);}}
    private void startCue(Consumer<GamePacket> send){send.accept(message(settings.territory()?"TC_START":"MULTI_HILL_START",List.of(),false));}
    private int color(Hill h){return !settings.territory()&&h.contested?0xff0000:h.owner==2||h.owner==3?team(h.owner).color():0xffffff;}
    private GamePacket zone(Hill h){return h.zone.packet(color(h),settings.territory()?7+h.zone.index():2);}
    private TerritoryBaseState state(Hill h,int action){double anchor=h.owner==2?0:h.owner==3?1:.5,delta=h.progress-anchor,amount=Math.max(0,Math.min(100,Math.abs(delta)/.5*100));return new TerritoryBaseState(h.zone.index(),action,h.owner,amount==0?h.attacker:delta>0?3:2,amount);}
    private void award(PlayerLife p,long amount,int reason){award(p,amount,reason,null);}
    private void award(PlayerLife p,long amount,int reason,Hill hill){if(current(p)&&scores.award(match.binding().row(p),amount,reason,null,Optional.of(new ObjectiveScores.Mode(mode.clock().ended,mode.clock().retiring)))&&!settings.territory()&&hill!=null)match.hillEvent(new Scored(hill.zone,p));}
    private void storeOccupants(Hill h,Map<Integer,PlayerLife> occupants){h.occupants.clear();h.occupants.putAll(occupants);h.occupantTeams.clear();for(var p:occupants.values())h.occupantTeams.put(p.id(),p.team());}
    public List<State> states(){owned();return active.stream().map(h->new State(h.zone,h.owner,h.attacker,h.progress,h.contested,Set.copyOf(h.occupants.keySet()))).toList();}
    public boolean objective(PlayerLife p){owned();return active.stream().anyMatch(h->h.zone.contains(p.position()));}
    private Vector anchor(int team){var p=geometry.baseAnchor(team);return new Vector(p.x(),p.y(),p.z());}
    private Vector ground(double x,double y){var p=geometry.dryGround(x,y,48);return new Vector(p.x(),p.y(),p.z());}
    private List<ObjectiveZone> build(){
        var zones=new ArrayList<ObjectiveZone>();var authored=match.metadata().neutralBases();for(int i=0;i<Math.min(10,authored.size());i++)zones.add(ObjectiveZone.from(i,authored.get(i),match.world().sourceZShift()));
        var a=anchor(2);var b=anchor(3);double dx=b.x()-a.x(),dy=b.y()-a.y();
        if(zones.size()>=2){if(settings.territory()&&Math.abs(dx)+Math.abs(dy)>=1e-6)zones.sort(Comparator.comparingDouble((ObjectiveZone z)->(z.center().x()-a.x())*dx+(z.center().y()-a.y())*dy).thenComparingInt(ObjectiveZone::index));}
        else {zones.clear();if(settings.territory()){for(double f:new double[]{.15,.325,.5,.675,.85})zones.add(ObjectiveZone.around(zones.size(),ground(a.x()+dx*f,a.y()+dy*f),15,8,10));}
            else {double distance=Math.max(1,Math.hypot(dx,dy)),lateral=Math.min(80,distance*.2),px=-dy/distance,py=dx/distance;double mx=a.x()+dx*.5,my=a.y()+dy*.5;double[][] candidates={{a.x()+dx*.35,a.y()+dy*.35},{mx,my},{a.x()+dx*.65,a.y()+dy*.65},{mx+px*lateral,my+py*lateral},{mx-px*lateral,my-py*lateral}};var seen=new HashSet<String>();
                for(var xy:candidates){var p=ground(xy[0],xy[1]);if(seen.add((int)p.x()+":"+(int)p.y()))zones.add(ObjectiveZone.around(zones.size(),p,16,7,10));}if(zones.size()<2){zones.clear();zones.add(ObjectiveZone.around(0,a,16,8,8));zones.add(ObjectiveZone.around(1,b,16,8,8));}}
        }
        if(settings.territory()&&zones.size()>settings.maxActive()){var selected=new ArrayList<ObjectiveZone>();for(int i=0;i<settings.maxActive();i++)selected.add(zones.get((int)Math.rint(i*(zones.size()-1.0)/(settings.maxActive()-1))));zones=selected;}
        var result=new ArrayList<ObjectiveZone>();for(int i=0;i<zones.size();i++)result.add(zones.get(i).indexed(i));return result;
    }
    public void start(){owned();cancelShells();if(!settings.territory())clearActive();hills.clear();active.clear();refills.clear();cursor=0;intermission=false;double now=match.wall();nextCapture=now;nextPersonal=now+5;
        for(var zone:build())hills.add(new Hill(zone));
        if(settings.territory()){active.addAll(hills);int n=hills.size();for(int i=0;i<n;i++){var h=hills.get(i);h.owner=i<n/2?2:i>(n-1)/2?3:1;h.progress=h.owner==2?0:h.owner==3?1:.5;h.lastOwner=h.owner;broadcast(zone(h));broadcast(state(h,0));broadcast(state(h,1));}refreshTerritoryScores();}
        else activate(now);
        for(var p:match.lives().players())if(host.inGame(p.id()))startCue(packet->personal(p,packet));
    }
    private void activate(double now){if(hills.isEmpty())return;active.clear();int count=Math.min(hills.size(),settings.maxActive());for(int i=0;i<count;i++){var h=hills.get((cursor+i)%hills.size());h.owner=1;h.contested=false;h.firstPaid=false;h.occupants.clear();h.occupantTeams.clear();active.add(h);broadcast(zone(h));}cursor=(cursor+count)%hills.size();intermission=false;lastTeamScore=now;nextPersonal=now+5;nextRotation=now+settings.activeSeconds();}
    private void clearActive(){for(var h:active)broadcast(h.zone.clear());active.clear();}
    private Map<Integer,PlayerLife> occupants(Hill h){var result=new TreeMap<Integer,PlayerLife>();for(var p:match.lives().players())if(p.alive()&&p.spawned()&&(p.team()==2||p.team()==3)&&h.zone.contains(p.position())&&guard.presenceEligible(p,match.monotonic()))result.put(p.id(),p);return result;}
    private void refill(Hill h,Map<Integer,PlayerLife> occupants,double now){if(!settings.resupply()||h.contested||h.owner==1)return;for(var p:occupants.values())if(p.team()==h.owner){var old=refills.get(p.id());if(old!=null&&old.player==p&&now-old.at<settings.refillSeconds())continue;refills.put(p.id(),new Refill(p,now));if(p.health()<p.maxHealth())match.lives().heal(p.id(),p.maxHealth());match.lives().restock(p.id(),3,p.bot());match.lives().restockBlocks(p.id());}}
    private void presence(Hill h,Map<Integer,PlayerLife> occupants,double now){
        for(var team:TEAMS){var present=occupants.values().stream().filter(p->p.team()==team).toList();boolean previous=h.occupantTeams.containsValue(team);
            for(var p:present)if(!h.occupants.containsKey(p.id()))personal(p,state(h,3));
            for(var p:h.occupants.values())if(h.occupantTeams.get(p.id())==team&&!occupants.containsKey(p.id())&&current(p))personal(p,state(h,4));
            if(!present.isEmpty()&&!previous&&h.owner!=team&&now-h.shouts.getOrDefault(team,Double.NEGATIVE_INFINITY)>=5){h.shouts.put(team,now);var leader=present.getFirst();String letter=Character.toString('A'+h.zone.index());for(var p:match.lives().players())if(p.team()==team){if(occupants.get(p.id())==p)tell(p,"TC_ENTER_BASE_PLAYER",List.of(letter));else tell(p,"TC_ENTER_BASE_TEAMMATES",List.of(letter,leader.name()));}else if(p.team()==5-team)tell(p,"TC_ENTER_BASE_OPPOSITION",List.of(letter,leader.name()));}
        }storeOccupants(h,occupants);
    }
    /** Piecewise-linear retail percent per 0.5-second capture tick. */
    public static double capturePercent(int players){int[] x={0,1,5,10,15};double[] y={0,1,4,7,9};for(int i=1;i<x.length;i++)if(players<=x[i])return y[i-1]+(y[i]-y[i-1])*(Math.max(0,players)-x[i-1])/(x[i]-x[i-1]);return 9;}
    private void territoryTick(double elapsed,double now){boolean changed=false;record Capture(Hill hill,int team,boolean enemy){}var captures=new ArrayList<Capture>();
        for(var h:active){var occupants=occupants(h);presence(h,occupants,now);long blue=occupants.values().stream().filter(p->p.team()==2).count(),green=occupants.size()-blue;boolean contested=blue>0&&green>0;if(contested!=h.contested){h.contested=contested;broadcast(state(h,contested?6:7));}refill(h,occupants,now);int net=contested?0:(int)(green-blue);h.attacker=net>0?3:net<0?2:1;if(net==0)continue;
            double previous=h.progress;int owner=h.owner;double step=capturePercent(Math.abs(net))/100*.5*settings.captureRate()*elapsed/.5;double progress=h.progress+Math.copySign(step,net);for(double anchor:new double[]{0,.5,1})if(Math.abs(progress-anchor)<1e-9)progress=anchor;h.progress=Math.max(0,Math.min(1,progress));
            if(h.progress<=0)h.owner=2;else if(h.progress>=1)h.owner=3;else if(owner!=1&&(previous-.5)*(h.progress-.5)<=0){h.lastOwner=owner;h.owner=1;}
            if(h.owner!=owner){changed=true;broadcast(zone(h));if(h.owner!=1){boolean enemy=h.lastOwner!=1&&h.lastOwner!=h.owner;captures.add(new Capture(h,h.owner,enemy));for(var p:occupants.values())if(p.team()==h.owner)award(p,enemy?100:150,enemy?22:21);h.lastOwner=h.owner;}}broadcast(state(h,5));
        }
        if(changed){refreshTerritoryScores();for(var c:captures){String prefix=c.enemy?"TC_CAPTURED":"TC_NEUTRALCAPTURED",letter=Character.toString('A'+c.hill.zone.index()),total=Integer.toString(active.size());tellTeam(c.team,prefix+"_CAPTURINGTEAM",List.of(letter,Long.toString(active.size()-team(c.team).score),total));tellTeam(5-c.team,prefix+"_LOSINGTEAM",List.of(letter,Long.toString(team(5-c.team).score),total));relative(c.team);}for(var id:TEAMS)if(team(id).score>=active.size()){mode.endByScore(id);break;}}
    }
    private void refreshTerritoryScores(){for(var id:TEAMS){var t=team(id);t.score=active.stream().filter(h->h.owner==id).count();match.scoreboard().teamScore(t,22);}}
    private void hillControl(double now){for(var h:active){var occupants=occupants(h);storeOccupants(h,occupants);long blue=occupants.values().stream().filter(p->p.team()==2).count(),green=occupants.size()-blue;
            if(!h.firstPaid&&!occupants.isEmpty()){h.firstPaid=true;award(occupants.values().iterator().next(),250,77,h);}
            boolean wasContested=h.contested;h.contested=blue>0&&green>0;if(wasContested&&!h.contested&&!occupants.isEmpty()){int left=blue>0?2:3;match.hillEvent(new ContestWon(occupants.values().stream().filter(p->p.team()==left).toList(),h.owner==left));}
            if(h.contested&&!wasContested){broadcast(zone(h));if(now-h.shouts.getOrDefault(1,Double.NEGATIVE_INFINITY)>=5){h.shouts.put(1,now);broadcast(message("MULTIHILL_CONTESTED",List.of(),false));}}
            refill(h,occupants,now);
            if(occupants.isEmpty()){if(h.owner!=1||wasContested){h.owner=1;broadcast(zone(h));}continue;}if(h.contested)continue;int claimant=blue>0?2:3;
            if(claimant==h.owner){if(wasContested)broadcast(zone(h));continue;}int previous=h.owner;h.owner=claimant;broadcast(zone(h));var claimers=occupants.values().stream().filter(p->p.team()==claimant).toList();for(var p:claimers)award(p,previous==1?150:100,previous==1?78:79,h);var leader=claimers.getFirst();
            for(var p:match.lives().players())if(p==leader)tell(p,"MULTIHILL_OCCUPIED_YOU",List.of(leader.name()));else if(p.team()==claimant)tell(p,"MULTIHILL_OCCUPIED_FRIENDLY",List.of(leader.name()));else if(p.team()==5-claimant)tell(p,previous==p.team()?"MULTIHILL_LOST":"MULTIHILL_OCCUPIED_ENEMY",previous==p.team()?List.of():List.of(leader.name()));relative(claimant);
        }}
    private void hillTeamScores(double until){long ticks=(long)Math.max(0,Math.floor(until-lastTeamScore));if(ticks==0)return;lastTeamScore+=ticks;var changed=new HashSet<Integer>();for(var h:active)if(!h.contested&&h.owner!=1){team(h.owner).addScore(ticks);changed.add(h.owner);}for(var id:TEAMS)if(changed.contains(id))match.scoreboard().teamScore(team(id),79);}
    private void personalScores(double until){if(until<nextPersonal)return;long periods=(long)Math.floor((until-nextPersonal)/5)+1;nextPersonal+=periods*5;for(var h:active)for(var p:h.occupants.values())if(current(p)&&h.occupantTeams.get(p.id())==p.team()&&(settings.territory()||p.alive())&&(h.contested||p.team()==h.owner)){long amount=settings.territory()?h.contested?25:35:h.contested?50:150;int reason=settings.territory()?h.contested?25:20:h.contested?82:76;award(p,periods*amount,reason,h);}}
    public void tick(){owned();if(!open())return;double now=match.wall();if(settings.territory()){if(now>=nextCapture){double elapsed=Math.max(.5,now-nextCapture+.5);nextCapture=now+.5;territoryTick(Math.min(elapsed,1.5),now);}if(open())personalScores(now);return;}
        double deadline=mode.started()&&mode.clock().timeLimit>0?mode.clock().startTime+mode.clock().timeLimit:Double.POSITIVE_INFINITY;boolean inTime=now<deadline;
        if(intermission){if(inTime&&now>=nextActivation)activate(now);}
        else {if(inTime&&now<nextRotation)hillControl(now);double until=Math.min(now,Math.min(nextRotation,deadline));hillTeamScores(until);if(inTime){personalScores(until);if(now>=nextRotation){if(!active.isEmpty())broadcast(message("BASE_DEPLETED",List.of(),true));for(var h:active){match.hillEvent(new Expired(h.zone,h.occupants,h.occupantTeams));airstrike(h.zone.center());}clearActive();intermission=true;nextActivation=now+10;}}}
    }
    public void kill(Scoreboard.Player killer,Scoreboard.Player victim,int type){owned();if(!open()||!settings.territory()&&intermission||!ObjectiveScores.eligible(killer,victim,type))return;Hill k=active.stream().filter(h->h.zone.contains(killer.life.position())).findFirst().orElse(null),v=active.stream().filter(h->h.zone.contains(victim.life.position())).findFirst().orElse(null);if(!settings.territory())match.hillEvent(new HillKill(killer.life,victim.life,k!=null,v!=null));if(k!=null&&k.owner==killer.team())award(killer.life,100,settings.territory()?23:80,k);else if(v!=null&&v.owner!=killer.team())award(killer.life,50,settings.territory()?24:81,v);}
    private void sound(int id,Vector at){try{match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(id,1,new GameplaySounds.Position(at.x(),at.y(),at.z()),.25f),null,true,null);}catch(RuntimeException e){match.failure("hill airstrike sound",e);}}
    private void airstrike(Vector center){if(!open())return;sound(9,center);int[][] pattern={{0,0},{-5,0},{5,0},{0,-5},{0,5}};for(int i=0;i<pattern.length;i++)shells.addLast(new Shell(center,pattern[i][0],pattern[i][1],i==0));if(timer==null){fireShell();scheduleShell();}}
    private void fireShell(){if(shells.isEmpty()||!open())return;var s=shells.removeFirst();if(s.first)sound(GameplaySounds.flyby(match.metadata().environment().skybox()),s.center);try{match.entities().projectiles().serverShot(ProjectileSpec.AIRSTRIKE,new Vector(s.center.x()+s.dx,s.center.y()+s.dy,Math.max(1,s.center.z()-48)),new Vector(0,0,100),null);}catch(RuntimeException e){match.failure("hill airstrike shell",e);}}
    private void scheduleShell(){if(shells.isEmpty()||!open())return;long token=generation;timer=loop.sleep(settings.shellInterval()).toCompletableFuture();timer.whenComplete((unused,error)->{if(token!=generation)return;timer=null;if(error!=null||!open()){shells.clear();return;}fireShell();scheduleShell();});}
    public int pendingShells(){owned();return shells.size();}
    public void cancelShells(){generation++;shells.clear();var old=timer;timer=null;if(old!=null)old.cancel(false);}
    public void forget(PlayerLife p){owned();var refill=refills.get(p.id());if(refill!=null&&refill.player==p)refills.remove(p.id());for(var h:hills)if(h.occupants.remove(p.id(),p))h.occupantTeams.remove(p.id());}
    public void reveal(Consumer<GamePacket> send){owned();for(var h:active){send.accept(zone(h));if(settings.territory()){send.accept(state(h,0));send.accept(state(h,1));if(h.contested)send.accept(state(h,6));}}startCue(send);}
    public void deactivate(){owned();cancelShells();if(settings.territory())for(var h:active)broadcast(state(h,2));else clearActive();}
    public void close(){if(closed)return;cancelShells();hills.clear();active.clear();refills.clear();closed=true;}
}
