// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.*;
import net.battlespades.protocol.ScorePackets.GameStats;
import net.battlespades.protocol.ScorePackets.Row;

/** Original scoreboard/HUD/award policy. Profile credit, known-player delivery and result IO are required domains. */
public final class Scoreboard {
    public static final class Player {
        public final PlayerLife life;
        public long score,kills,deaths,captures,killStreak;
        public RoundCombatStats roundStats=new RoundCombatStats();
        public Player(PlayerLife life){this.life=Objects.requireNonNull(life);}
        public int id(){return life.id();}public int team(){return life.team();}
    }
    public interface Peer {
        boolean inGame();
        /** Empty Optional means the source connection has no known-player filter. */
        Optional<Set<Integer>> knownPlayers();
        void send(GamePacket packet);
    }
    public interface Effects {
        void broadcast(GamePacket packet,boolean reliable,Integer knownPlayerId);
        void scoreChanged(Player player,int reason);
        void resetProfileBaseline(Player player);
        void resetResultBaselines();
        void scheduleResults(Integer winner);
    }
    private final Thread owner=Thread.currentThread();
    private final Supplier<Map<Integer,Player>> players;
    private final Supplier<List<TeamState>> teams;
    private final Supplier<Optional<List<Peer>>> peers;
    private final BooleanSupplier ended,stopping;
    private final SourceRandom random;
    private final Effects effects;
    private boolean statsSent;
    private List<GameStats> statsPackets=List.of();
    public Scoreboard(Supplier<Map<Integer,Player>> players,Supplier<List<TeamState>> teams,Supplier<Optional<List<Peer>>> peers,
            BooleanSupplier ended,BooleanSupplier stopping,SourceRandom random,Effects effects){
        this.players=Objects.requireNonNull(players);this.teams=Objects.requireNonNull(teams);this.peers=Objects.requireNonNull(peers);this.ended=Objects.requireNonNull(ended);this.stopping=Objects.requireNonNull(stopping);this.random=Objects.requireNonNull(random);this.effects=Objects.requireNonNull(effects);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Scoreboard outside owner");}
    public boolean statsSent(){owned();return statsSent;}
    public List<GameStats> statsPackets(){owned();return statsPackets;}
    public static int wireTeam(int team){return team==0||team==2||team==3?team:2;}
    public static double hudSeconds(double value){return Double.isNaN(value)?0:Math.min(3599.999,Math.max(0,value));}
    public static SimplePackets.SetScore playerPacket(Player p,int reason){return new SimplePackets.SetScore(1,reason,p.id(),Math.toIntExact(p.score));}
    public void playerScore(Player p,Integer reason){
        owned();int why=reason==null?1:reason;effects.scoreChanged(p,why);var current=players.get().get(p.id());if(current!=null&&current.life!=p.life)return;
        effects.broadcast(playerPacket(p,why),true,p.id());
    }
    public void teamScore(TeamState team,Integer reason){owned();effects.broadcast(new SimplePackets.SetScore(0,reason==null?1:reason,wireTeam(team.id),Math.toIntExact(team.score)),true,null);}
    public void timer(double remaining,boolean reliable){owned();effects.broadcast(new SimplePackets.DisplayCountdown((float)hudSeconds(remaining)),reliable,null);}
    private static GameStats filter(GameStats packet,Optional<Set<Integer>> known){return known.isEmpty()?packet:new GameStats(packet.team(),packet.rows().stream().filter(r->known.get().contains(r.playerId())).toList());}
    public void reveal(Peer peer){
        owned();var known=peer.knownPlayers();for(var p:players.get().values())if(known.isEmpty()||known.get().contains(p.id()))peer.send(playerPacket(p,0));
        for(var team:teams.get())peer.send(new SimplePackets.SetScore(0,0,wireTeam(team.id),Math.toIntExact(team.score)));
        if(ended.getAsBoolean())for(var packet:statsPackets)peer.send(filter(packet,known));
    }
    public void resetRound(){
        owned();for(var p:players.get().values()){
            p.score=0;p.kills=0;p.deaths=0;p.captures=0;p.killStreak=0;p.roundStats=new RoundCombatStats();p.life.damageHistory().clear();effects.resetProfileBaseline(p);
            effects.broadcast(playerPacket(p,0),true,p.id());
        }
        effects.resetResultBaselines();statsSent=false;statsPackets=List.of();
    }
    private static Long amount(Player p,int award){
        var state=p.roundStats;if(award==24){long kills=state==null?0:state.awards.getOrDefault(5,0L);double shots=state==null?0:state.measures.getOrDefault("shots",0.0);if(kills<3||shots<=0)return null;if(!Double.isFinite(shots)||shots>=0x1.0p63)throw new ArithmeticException("Shot count outside signed 64-bit range");return -(long)shots;}
        long value=state==null?0:state.awards.getOrDefault(award,0L);return value>=(award==11?2:1)?value:null;
    }
    private static boolean better(Player p,long amount,Player best,long bestAmount){if(best==null||amount!=bestAmount)return best==null||amount>bestAmount;if(p.score!=best.score)return p.score>best.score;if(p.deaths!=best.deaths)return p.deaths<best.deaths;return p.id()<best.id();}
    public List<Row> teamAwards(int team){
        owned();var eligible=players.get().values().stream().filter(p->p.team()==team).sorted(Comparator.comparingInt(Player::id)).toList();var winners=new ArrayList<Row>();
        for(int award=0;award<30;award++){Player best=null;long bestAmount=0;for(var p:eligible){Long value=amount(p,award);if(value!=null&&better(p,value,best,bestAmount)){best=p;bestAmount=value;}}if(best!=null)winners.add(new Row(best.id(),award));}
        List<Row> selected=winners.size()>3?random.sample(winners,3):winners;return selected.stream().sorted(Comparator.comparingInt(Row::award)).toList();
    }
    private void broadcastRows(GameStats packet){
        var connections=peers.get();if(connections.isEmpty()){effects.broadcast(packet,true,null);return;}if(stopping.getAsBoolean())return;
        for(var peer:List.copyOf(connections.get())){if(!peer.inGame())continue;peer.send(filter(packet,peer.knownPlayers()));}
    }
    public void gameStats(Integer winner){
        owned();if(statsSent)return;statsSent=true;var packets=new ArrayList<GameStats>();for(int team:new int[]{2,3}){var packet=new GameStats(team,teamAwards(team));packets.add(packet);broadcastRows(packet);}statsPackets=List.copyOf(packets);effects.scheduleResults(winner);
    }
    public void showGameStats(){owned();effects.broadcast(new SimplePackets.ShowGameStats(),true,null);}
    public void text(int message,double seconds){owned();double bounded=Double.isNaN(seconds)?120:Math.max(0,Math.min(120,seconds));effects.broadcast(new ScorePackets.ShowTextMessage(Math.max(0,Math.min(8,message)),(float)bounded),true,null);}
    public void forceScores(boolean forced){owned();effects.broadcast(new SimplePackets.ForceShowScores(forced?1:0),true,null);}
    public void lockTeam(int team,boolean locked){owned();effects.broadcast(new SimplePackets.LockTeam(wireTeam(team),locked?1:0),true,null);}
    public void lockClass(int team,boolean locked){owned();effects.broadcast(new SimplePackets.TeamLockClass(wireTeam(team),locked?1:0),true,null);}
    public void mapEnded(){owned();effects.broadcast(new SimplePackets.MapEnded(),true,null);}
}
