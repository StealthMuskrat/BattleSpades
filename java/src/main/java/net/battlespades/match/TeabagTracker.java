// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
/** Three crouch press edges near an actual dead enemy; each (id, death time) counts once per connection. */
public final class TeabagTracker {
    private record Death(int id,double time){}
    private static final class State {List<Double> presses=new ArrayList<>();final Set<Death> done=new HashSet<>();}
    private final Map<net.battlespades.player.PlayerLife,State> states=new IdentityHashMap<>();
    private final Supplier<Map<Integer,Scoreboard.Player>> players;
    private final ObjectiveScores objectives;
    private final ProfileTracker profiles;
    private final BooleanSupplier classic,points;
    public TeabagTracker(Supplier<Map<Integer,Scoreboard.Player>> players,ObjectiveScores objectives,ProfileTracker profiles,BooleanSupplier classic,BooleanSupplier points){this.players=Objects.requireNonNull(players);this.objectives=Objects.requireNonNull(objectives);this.profiles=Objects.requireNonNull(profiles);this.classic=Objects.requireNonNull(classic);this.points=Objects.requireNonNull(points);}
    public void forget(Scoreboard.Player p){states.remove(p.life);}
    /** Source spawn clears completed deaths but retains the rolling press timestamps. */
    public void spawned(Scoreboard.Player p){var s=states.get(p.life);if(s!=null)s.done.clear();}
    public void clear(){states.clear();}
    public List<Double> presses(Scoreboard.Player p){var s=states.get(p.life);return s==null?List.of():List.copyOf(s.presses);}
    public int completed(Scoreboard.Player p){var s=states.get(p.life);return s==null?0:s.done.size();}
    public boolean crouch(Scoreboard.Player p,double now){
        if(p==null||!p.life.alive()||!ObjectiveScores.playable(p)||!objectives.open(Optional.empty()))return false;var s=states.computeIfAbsent(p.life,k->new State());if(!s.presses.isEmpty()&&now-s.presses.getLast()>FlowCatalog.number("teabagGap"))s.presses.clear();s.presses.add(now);int count=Math.toIntExact(FlowCatalog.value("teabagPresses"));if(s.presses.size()>count)s.presses=new ArrayList<>(s.presses.subList(s.presses.size()-count,s.presses.size()));if(s.presses.size()<count)return false;var at=p.life.position();
        for(var victim:List.copyOf(players.get().values())){if(victim.life==p.life||victim.life.alive()||!ObjectiveScores.playable(victim)||victim.team()==p.team())continue;double time=victim.life.deathTime();var key=new Death(victim.id(),time);if(time<=0||s.done.contains(key))continue;var spot=victim.life.position();double x=spot.x()-at.x(),y=spot.y()-at.y();for(double v:new double[]{x,y})if(Double.isFinite(v)&&Double.isInfinite(v*v))throw new ArithmeticException("Source teabag squared-distance overflow");double horizontal=Math.pow(x*x+y*y,.5);if(horizontal>FlowCatalog.number("teabagDistance")||Math.abs(spot.z()-at.z())>3)continue;
            s.done.add(key);s.presses.clear();boolean old=classic.getAsBoolean();profiles.add(p,Math.toIntExact(FlowCatalog.value(old?"teabagClassicTotal":"teabagTotal")),1,0);p.roundStats.increment(Math.toIntExact(FlowCatalog.value("teabagAward")),1);boolean pay=false;try{pay=points.getAsBoolean();}catch(RuntimeException ignored){/* Source rule lookup faults cannot break input. */}if(pay)objectives.award(p,FlowCatalog.value(old?"teabagClassicScore":"teabagScore"),Math.toIntExact(FlowCatalog.value("teabagReason")),null,Optional.empty());return true;
        }return false;
    }
}
