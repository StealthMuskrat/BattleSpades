// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import net.battlespades.world.MetadataDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class RoundRuntimeGoldenTest {
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private static final List<?> ROWS=load();
    private static List<?> load(){try(var in=Objects.requireNonNull(RoundRuntimeGoldenTest.class.getResourceAsStream("/golden/round-runtime.json"))){return (List<?>)((Map<?,?>)MetadataDocument.json(new String(in.readAllBytes(),StandardCharsets.UTF_8))).get("cases");}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
    static Stream<Map<?,?>> leaves(){return rows("leave");}static Stream<Map<?,?>> ends(){return rows("end");}
    private static Stream<Map<?,?>> rows(String domain){return ROWS.stream().<Map<?,?>>map(row->(Map<?,?>)row).filter(row->domain.equals(row.get("domain")));}
    private static final class Probe implements ModeEvents.Mode {
        final java.util.function.Function<ModeEvents.Event,CompletionStage<Void>> hook;
        Probe(java.util.function.Function<ModeEvents.Event,CompletionStage<Void>> hook){this.hook=hook;}
        public RoundClock clock(){throw new AssertionError("Leave hooks have no tick clock");}public CompletionStage<Void> tick(int loop){throw new AssertionError("Leave hooks are immediate");}public CompletionStage<Void> handle(ModeEvents.Event event){return hook.apply(event);}
    }
    @ParameterizedTest @MethodSource("leaves") void originalDeparturePrefixIdentityQueueAndRetainedRemainders(Map<?,?> row){
        int c=((Number)row.get("case")).intValue();var log=new ArrayList<String>();var gates=new ArrayList<CompletableFuture<Void>>();
        record Actor(int id){}var actors=List.of(new Actor(0),new Actor(1),new Actor(0));var modes=new Probe[2];ModeEvents.Mode[] active={null};
        for(int i=0;i<2;i++){int id=i;modes[i]=new Probe(event->{String name=event.name();log.add("M"+id+":"+name);
            if(name.equals("on_player_death")&&c%3==0)active[0]=modes[1];if(c%5==0&&name.equals("on_player_kill"))throw new IllegalStateException("mode fault");
            if(c%2==0)return done();var gate=new CompletableFuture<Void>();gates.add(gate);return gate.thenRun(()->log.add("A"+id+":"+name));});}
        active[0]=modes[0];var queue=new ModeEvents(32,32,()->active[0],event->{String name=event.name();log.add("P:"+name);if(c%7==0&&name.equals("on_player_death"))throw new IllegalStateException("plugin fault");if(c%2==0)return done();var gate=new CompletableFuture<Void>();gates.add(gate);return gate.thenRun(()->log.add("AP:"+name));},(label,error)->{},Runnable::run);
        String[] names={"on_player_spawn","on_player_team_change","on_player_death","on_player_kill","block"};
        for(int k=0;k<10;k++)queue.offer(ModeEvents.Event.of(names[k%5],actors.get((k+c)%3),actors.get((k/2+c)%3),actors.getFirst()));
        queue.leave(actors.getFirst());assertEquals(row.get("prefix"),log);assertEquals(((Number)row.get("pending")).intValue(),queue.pendingDepartures());
        String queued=queue.snapshot().stream().map(event->event.name()+":"+event.arguments().stream().map(a->a==actors.get(2)?"2":a==actors.get(1)?"1":"0").collect(Collectors.joining(","))).collect(Collectors.joining("|"));assertEquals(row.get("queue"),queued);
        int prefix=log.size();var settled=queue.departures().toCompletableFuture();for(var gate:gates)gate.complete(null);settled.join();
        assertEquals(row.get("suffix"),log.subList(prefix,log.size()).stream().sorted().toList());assertEquals(((Number)row.get("final_pending")).intValue(),queue.pendingDepartures());
    }
    @ParameterizedTest @MethodSource("ends") void originalEndVoteMapFallbackAndActualInPlaceRestartPolicy(Map<?,?> row){
        int c=((Number)row.get("case")).intValue(),kind=c%3,facade=c>=36?1:(c/3)%3,fault=(c/9)%4;boolean scoreboard=c%2==0,badRestart=c%13==0;var log=new ArrayList<String>();boolean[] ended={true},sequence={true};
        var teams=List.of(new TeamState(2,"Blue",0,0,255),new TeamState(3,"Green",0,255,0));teams.getFirst().score=17;
        var restart=new RoundRestart<Integer>(new RoundRestart.Services<>(){
            public boolean scoreboard(){return scoreboard;}public void forceScores(boolean forced){log.add("scores:"+(forced?1:0));}public CompletionStage<Void> prepareBots(){log.add("prepare");return done();}
            public void resetRuntime(){log.add("reset");if(badRestart)throw new IllegalStateException("reset failed");}public List<TeamState> teams(){return teams;}
            public CompletionStage<Void> startMode(){log.add("start");ended[0]=false;return done();}public void teamScore(TeamState team){assertEquals(0,team.score);log.add("team:"+team.id);}
            public List<Integer> players(){return List.of();}public boolean playable(Integer player){throw new AssertionError();}public boolean connected(Integer player){throw new AssertionError();}public void respawn(Integer player){throw new AssertionError();}public void respawnFailure(Integer player,Throwable error){fail(error);}public CompletionStage<Void> resetBots(){log.add("reset-bots");return done();}
        },Runnable::run);
        var transition=new RoundEndSequence.Transition(){
            private CompletionStage<RoundEndSequence.Result> outcome(){if(fault==3)throw new IllegalStateException("transition failed");return CompletableFuture.completedFuture(new RoundEndSequence.Result(fault==0,fault==2,"transition failed"));}
            public boolean supportsEndScreen(){return facade==1;}public CompletionStage<RoundEndSequence.Result> changeAfterScores(String map,double seconds,Integer headline){log.add("change:"+map+":"+(int)seconds+":"+(headline==null?"-":headline));return outcome();}
            public CompletionStage<RoundEndSequence.Result> changeMap(String map){log.add("change:"+map);return outcome();}
            public CompletionStage<RoundEndSequence.Result> restart(){log.add("transition-restart");return CompletableFuture.completedFuture(new RoundEndSequence.Result(!badRestart,false,"restart failed"));}
            public boolean busyAllowingCurrentRequest(){return c%11==0;}
        };
        var ending=new RoundEndSequence(new RoundEndSequence.Services(){
            public void endingMusic(){log.add("music");}public boolean scoreboard(){return scoreboard;}public void forceScores(boolean forced){log.add("scores:"+(forced?1:0));}public void botsEnd(){log.add("bots:end");}
            public CompletionStage<Void> sleep(double seconds){log.add("sleep:"+(int)seconds);return done();}public void statistics(Integer winner){log.add("stats:"+winner);}
            public CompletionStage<Void> waitForVote(){log.add("vote-wait");return done();}public String consumeMap(){log.add("consume");return c>=36?new String[]{"STRASSE","","ος","different"}[c-36]:new String[]{null,"Arena","Other"}[kind];}public String currentMap(){return c>=36?new String[]{"Straße","arena","Οσ","current"}[c-36]:"arena";}public double screenSeconds(){return c>=36?(c==39?Double.NaN:7):new int[]{-5,0,7,130}[c%4];}
            public Integer headline(Integer winner){return c%5==0?null:4;}public RoundEndSequence.Transition transition(){return facade==0?null:transition;}public CompletionStage<Void> restartInPlace(){return restart.run();}
            public CompletionStage<Void> cancelPending(){return done();}public boolean recoverable(){return c%7!=0&&ended[0];}public void failed(){sequence[0]=false;}public void failure(String label,Throwable error){}
        },Runnable::run);
        ending.run(2).toCompletableFuture().join();assertEquals(row.get("log"),log,"case "+c);assertEquals(row.get("ended"),ended[0]);assertEquals(row.get("sequence"),sequence[0]);assertFalse(ending.running());
    }
}
