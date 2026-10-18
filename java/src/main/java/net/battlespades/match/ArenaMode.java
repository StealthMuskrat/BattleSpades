// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.tomlj.TomlTable;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.GamePacket;

/** Original Arena elimination rounds. Match ending and economy use the shared BaseMode lifecycle. */
public final class ArenaMode extends BaseRoundMode {
    public record Settings(int roundsToWin,double roundTimeLimit,double timeLimit,boolean endRoundScoreboard){
        public Settings {if(!Double.isFinite(roundTimeLimit)||!Double.isFinite(timeLimit))throw new IllegalArgumentException("Nonfinite Arena clock");roundsToWin=Math.max(1,roundsToWin);roundTimeLimit=Math.max(1,roundTimeLimit);timeLimit=Math.max(0,timeLimit);}
        public static Settings from(TomlTable raw){Object rounds=raw.get("modes.arena.rounds_to_win");if(rounds==null)rounds=raw.get("modes.arena.score_limit");Object round=raw.get("modes.arena.round_time_limit"),time=raw.get("modes.arena.time_limit"),scores=raw.get("lobby.end_round_scoreboard");if(rounds!=null&&!(rounds instanceof Long)||round!=null&&!(round instanceof Number)||time!=null&&!(time instanceof Number)||scores!=null&&!(scores instanceof Boolean))throw new IllegalArgumentException("Arena settings types");return new Settings(rounds==null?5:Math.toIntExact((Long)rounds),round==null?180:((Number)round).doubleValue(),time==null?0:((Number)time).doubleValue(),!Boolean.FALSE.equals(scores));}
    }
    public interface Effects extends BaseRoundMode.Effects {void respawn(PlayerLife player);void respawnFailure(PlayerLife player,RuntimeException error);}
    public record State(int round,Map<Integer,Integer> wins,boolean started,boolean ended,double start,boolean countdown,double countdownEnd,Double nextRound,boolean waiting,List<PlayerLife> fighters){public State{wins=Map.copyOf(wins);fighters=List.copyOf(fighters);}}
    private final Settings settings;private final Effects arena;private final IntSupplier tickRate;
    private int round;private final Map<Integer,Integer> wins=new LinkedHashMap<>();private final List<PlayerLife> fighters=new ArrayList<>();
    private boolean roundStarted,roundEnded,countdown,waiting;private double roundStart,countdownEnd;private Double nextRound;
    public ArenaMode(Settings settings,Supplier<List<TeamState>> teams,Supplier<Map<Integer,Scoreboard.Player>> players,Function<PlayerLife,Optional<Scoreboard.Peer>> peer,Scoreboard board,DoubleSupplier wall,DoubleSupplier monotonic,IntSupplier queued,IntSupplier tickRate,Consumer<GamePacket> reliable,Effects effects,Executor resume){
        super("arena",settings.roundsToWin(),settings.timeLimit(),settings.endRoundScoreboard(),teams,players,peer,board,wall,monotonic,queued,reliable,effects,resume);this.settings=settings;arena=effects;this.tickRate=Objects.requireNonNull(tickRate);wins.put(2,0);wins.put(3,0);
    }
    public Settings settings(){return settings;}public State state(){sequence.owned();return new State(round,wins,roundStarted,roundEnded,roundStart,countdown,countdownEnd,nextRound,waiting,fighters);}
    @Override public void start(){super.start();round=0;wins.put(2,0);wins.put(3,0);for(var team:teams.get())if(playable(team.id))team.score=0;newRound(false);}
    private static boolean playable(int team){return team==2||team==3;}
    private void message(String text){reliable.accept(Announcements.overlay(text));}
    private void newRound(boolean respawn){round++;roundStarted=false;roundEnded=false;nextRound=null;fighters.clear();waiting=false;message("Round "+round+" - Get ready!");if(respawn)for(var row:List.copyOf(players.get().values()))if(playable(row.team()))try{arena.respawn(row.life);}catch(RuntimeException error){arena.respawnFailure(row.life,error);}countdown=true;countdownEnd=wall.getAsDouble()+5;}
    public boolean canRespawn(PlayerLife player){sequence.owned();return !roundStarted;}
    public double respawnTime(double configured){sequence.owned();if(!roundStarted)return configured;double now=wall.getAsDouble();double remaining=nextRound!=null?nextRound-now:roundEnded?5:settings.roundTimeLimit()-(now-roundStart)+5;return Math.max(0,Math.min(255,remaining));}
    public double damage(double amount){sequence.owned();return !roundStarted||roundEnded?0:amount<0?Math.ceil(amount):Math.floor(amount);}
    private boolean ready(){boolean blue=false,green=false;for(var p:players.get().values())if(p.life.alive()&&p.life.spawned()){if(p.team()==2)blue=true;if(p.team()==3)green=true;}return blue&&green;}
    private void begin(){roundStarted=true;countdown=false;roundStart=wall.getAsDouble();fighters.clear();for(var p:players.get().values())if(playable(p.team())&&p.life.alive()&&p.life.spawned())fighters.add(p.life);message("FIGHT!");}
    public boolean botRetireSafe(PlayerLife p){sequence.owned();return !roundStarted||roundEnded||clock.ended||!p.alive()||fighters.stream().noneMatch(f->f==p);}
    private Map<Integer,Integer> aliveCounts(){var counts=new LinkedHashMap<Integer,Integer>();counts.put(2,0);counts.put(3,0);var roster=players.get();for(var p:fighters){var current=roster.get(p.id());if(current!=null&&current.life==p&&p.alive()&&playable(p.team()))counts.merge(p.team(),1,Integer::sum);}return counts;}
    private void elimination(){if(!roundStarted||roundEnded||clock.ended)return;var counts=aliveCounts();if(counts.get(2)==0||counts.get(3)==0)endRound(counts.get(2)>0?Integer.valueOf(2):counts.get(3)>0?Integer.valueOf(3):null);}
    private TeamState team(int id){return teams.get().stream().filter(t->t.id==id).findFirst().orElseThrow();}
    private void endRound(Integer winner){if(roundEnded||clock.ended)return;roundEnded=true;if(winner!=null){wins.merge(winner,1,Integer::sum);message(team(winner).name+" wins the round!");}else message("Round draw!");for(int id:new int[]{2,3}){var t=team(id);t.score=wins.get(id);board.teamScore(t,null);}message("Score - "+team(2).name+": "+wins.get(2)+" | "+team(3).name+": "+wins.get(3));for(int id:new int[]{2,3})if(wins.get(id)>=settings.roundsToWin()){nextRound=null;endByScore(id);return;}if(!clock.ended)nextRound=wall.getAsDouble()+5;}
    @Override public void death(Scoreboard.Player victim,Scoreboard.Player killer,int type){super.death(victim,killer,type);if(!roundStarted||roundEnded)return;fighters.removeIf(p->p==victim.life);elimination();}
    @Override public CompletionStage<Void> handle(ModeEvents.Event event){sequence.owned();if(event.name().equals("on_player_leave")){var row=(Scoreboard.Player)event.arguments().getFirst();fighters.removeIf(p->p==row.life);elimination();return CompletableFuture.completedFuture(null);}return super.handle(event);}
    @Override public CompletionStage<Void> tick(int loop){sequence.owned();return sequence.run(List.<Supplier<CompletionStage<Void>>>of(()->super.tick(loop),()->{arenaTick(loop);return CompletableFuture.completedFuture(null);}).iterator());}
    private void arenaTick(int loop){if(clock.ended)return;double now=wall.getAsDouble();if(nextRound!=null){if(now>=nextRound)newRound(true);return;}if(countdown&&!roundStarted){if(!ready()){if(!waiting){waiting=true;message("Waiting for players on both teams...");}countdownEnd=now+5;return;}if(waiting){waiting=false;countdownEnd=now+5;}double left=countdownEnd-now;if(left<=0)begin();else if(loop%tickRate.getAsInt()==0){int seconds=(int)left;if(seconds>0)message(seconds+"...");}}if(roundStarted&&!roundEnded&&now-roundStart>=settings.roundTimeLimit()){var counts=aliveCounts();message("Time's up!");endRound(counts.get(2)>counts.get(3)?Integer.valueOf(2):counts.get(3)>counts.get(2)?Integer.valueOf(3):null);}}
}
