// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
/** Retail personal objective credits and priority geometry, shared by future concrete modes. */
public final class ObjectiveScores {
    public record Mode(boolean ended,boolean retiring){}
    private final Supplier<ProfileTracker.Context> context;
    private final BooleanSupplier retiring;
    private final Supplier<Map<Integer,Scoreboard.Player>> players;
    private final Scoreboard board;
    public ObjectiveScores(Supplier<ProfileTracker.Context> context,BooleanSupplier retiring,Supplier<Map<Integer,Scoreboard.Player>> players,Scoreboard board){this.context=Objects.requireNonNull(context);this.retiring=Objects.requireNonNull(retiring);this.players=Objects.requireNonNull(players);this.board=Objects.requireNonNull(board);}
    public static boolean playable(Scoreboard.Player p){return p!=null&&(p.team()==2||p.team()==3);}
    public boolean open(Optional<Mode> mode){return mode.map(m->!m.ended&&!m.retiring).orElse(true)&&context.get().active()&&!retiring.getAsBoolean();}
    public boolean award(Scoreboard.Player p,long amount,int reason,Integer award,Optional<Mode> mode){if(p==null||amount<=0||!playable(p)||!open(mode))return false;var current=players.get().get(p.id());if(current==null||current.life!=p.life)return false;p.score=Math.addExact(p.score,amount);board.playerScore(p,reason);if(award!=null)p.roundStats.increment(award,1);return true;}
    public boolean killEvent(Scoreboard.Player p,String event,Map<String,Long> amounts,Map<String,Integer> reasons,Optional<Mode> mode){if(event==null||!amounts.containsKey(event)||!reasons.containsKey(event))return false;Integer award=switch(event){case "carrier_defend","defend"->MatchCatalog.constant("MOST_DEFENDS");case "distract"->MatchCatalog.constant("MOST_DISTRACTIONS");default->null;};return award(p,amounts.get(event),reasons.get(event),award,mode);}
    public static boolean eligible(Scoreboard.Player killer,Scoreboard.Player victim,int type){return killer!=null&&victim!=null&&killer.life!=victim.life&&!LifeCatalog.TRANSITIONS.contains(type)&&playable(killer)&&playable(victim)&&killer.team()!=victim.team();}
    public static boolean within(Vector a,Vector b,double radius){if(a==null||b==null)return false;double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();for(double v:new double[]{x,y,z,radius})if(Double.isFinite(v)&&Double.isInfinite(v*v))throw new ArithmeticException("Source squared-distance overflow");return SpawnMath.sumSquares(x,y,z)<=radius*radius;}
    public static String classify(Scoreboard.Player killer,Scoreboard.Player victim,boolean victimCarrying,List<Scoreboard.Player> carriers,List<Vector> defend,List<Vector> attack,double carrierRadius,double radius){
        if(victimCarrying)return "intercept";
        for(var carrier:carriers)if(carrier.life!=killer.life&&carrier.life!=victim.life&&carrier.life.alive()&&within(victim.life.position(),carrier.life.position(),carrierRadius))return "carrier_defend";
        for(var point:defend)if(within(victim.life.position(),point,radius))return "defend";
        for(var point:attack)if(within(killer.life.position(),point,radius))return "assault";
        for(var point:attack)if(within(victim.life.position(),point,radius))return "assault_enemy";
        return null;
    }
    public static boolean distraction(Scoreboard.Player victim,Scoreboard.Player killer,int type,List<Scoreboard.Player> carriers,double radius){return eligible(killer,victim,type)&&carriers.stream().anyMatch(p->p.life!=victim.life&&p.life.alive()&&within(victim.life.position(),p.life.position(),radius));}
}
