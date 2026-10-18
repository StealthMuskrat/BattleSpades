// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.concurrent.*;
/** BaseMode clock, countdown windows and fixed timeout remainder. Full mode end/restart and event drain are required owners. */
public final class RoundClock {
    public interface Effects {
        void escapeWatch();
        void escapeFailure(RuntimeException failure);
        void timeoutMusic();
        void announce(FlowCatalog.Cue cue);
        void ensureVote(double wall);
        CompletionStage<Void> endByTime();
    }
    public boolean ended,retiring,escapeEnabled=true,timeoutMusicPlayed;
    public double startTime,elapsed,timeLimit;
    private Integer timeoutRemaining;
    private final Set<Double> fired=new LinkedHashSet<>();
    private double lastRemaining=Double.POSITIVE_INFINITY;
    private final Effects effects;
    public RoundClock(Effects effects){this.effects=Objects.requireNonNull(effects);}
    public void reset(double wall){ended=false;retiring=false;timeoutMusicPlayed=false;startTime=wall;elapsed=0;timeoutRemaining=null;fired.clear();lastRemaining=Double.POSITIVE_INFINITY;}
    public Set<Double> fired(){return Collections.unmodifiableSet(new LinkedHashSet<>(fired));}
    public double lastRemaining(){return lastRemaining;}public Integer timeoutRemaining(){return timeoutRemaining;}
    public int drainBudget(int queued,int configured){if(queued<0||configured<0)throw new IllegalArgumentException("Negative event budget");int budget=Math.min(queued,configured);return timeoutRemaining!=null&&!ended?Math.min(budget,timeoutRemaining):budget;}
    public void drained(int budget){if(budget<0)throw new IllegalArgumentException("Negative event budget");if(timeoutRemaining!=null&&!ended)timeoutRemaining=Math.max(0,timeoutRemaining-budget);}
    public void countdown(double remaining){
        if(remaining>lastRemaining+1)fired.clear();lastRemaining=remaining;
        for(var cue:FlowCatalog.CUES){double t=cue.threshold();if(fired.contains(t)||remaining>t)continue;fired.add(t);if(remaining<=t-5)continue;effects.announce(cue);}
        for(int second=Math.toIntExact(FlowCatalog.value("finalCountdown"));second>0;second--){double t=second;if(fired.contains(t)||remaining>t)continue;fired.add(t);if(remaining<=t-1)continue;effects.announce(new FlowCatalog.Cue(t,"COUNTDOWN_FROM_TEN",List.of(""+second)));}
    }
    public CompletionStage<Void> tick(double wall,int queued){
        if(ended)return CompletableFuture.completedFuture(null);elapsed=wall-startTime;if(escapeEnabled)try{effects.escapeWatch();}catch(RuntimeException e){effects.escapeFailure(e);}
        if(timeLimit>0&&!timeoutMusicPlayed&&timeLimit-elapsed<=FlowCatalog.number("timeoutMusicSeconds")){timeoutMusicPlayed=true;effects.timeoutMusic();}if(timeLimit>0)countdown(timeLimit-elapsed);if(timeLimit>0&&timeLimit-elapsed<=FlowCatalog.number("voteLeadSeconds"))effects.ensureVote(wall);
        if(timeLimit>0&&elapsed>=timeLimit){if(timeoutRemaining==null)timeoutRemaining=queued;if(timeoutRemaining==0)return Objects.requireNonNull(effects.endByTime());}else timeoutRemaining=null;return CompletableFuture.completedFuture(null);
    }
    public static Integer scoreWinner(List<TeamState> teams,long limit){if(limit<=0)return null;for(var t:teams)if(t.score>=limit)return t.id;return null;}
    public static Integer timeWinner(List<TeamState> teams){var sorted=teams.stream().sorted(Comparator.comparingLong((TeamState t)->t.score).reversed()).toList();return !sorted.isEmpty()&&(sorted.size()==1||sorted.getFirst().score>sorted.get(1).score)?sorted.getFirst().id:null;}
    public static int headline(Integer winner,long first,long second){if(winner==null)return Math.toIntExact(FlowCatalog.value("drawHeadline"));Integer leader=first>second?Integer.valueOf(2):second>first?Integer.valueOf(3):null;if(Objects.equals(winner,leader))return Math.toIntExact(FlowCatalog.value("scoreHeadline"));return Math.toIntExact(FlowCatalog.value(winner==2?"firstHeadline":winner==3?"secondHeadline":"scoreHeadline"));}
}
