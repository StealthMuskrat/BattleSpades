// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.stream.IntStream;
import net.battlespades.player.PlayerLife;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
final class MatchStateGoldenTest {
    static IntStream traces(){return IntStream.range(0,16);}
    static IntStream groups(){return IntStream.range(0,32);}
    static List<PlayerLife> actors(LifeHarness h){h.admit(0,2);h.admit(1,2);h.admit(2,3);var old=h.runtime.player(0);h.runtime.disconnected(0);h.admit(0,2);return List.of(old,h.runtime.player(1),h.runtime.player(2),h.runtime.player(0));}
    static String keys(List<PlayerLife> values,List<PlayerLife> actors){return values.isEmpty()?"-":String.join(",",values.stream().map(p->""+actors.indexOf(p)).toList());}
    @ParameterizedTest @MethodSource("traces") void originalTeamState(int trace)throws Exception{
        try(var h=new LifeHarness()){
            var actors=actors(h);var team=new TeamState(new int[]{2,3,0,1}[trace%4],"Blue",17,29,251);
            for(var r:Fixtures.rows("match-state")){if(!r[0].equals("team")||Integer.parseInt(r[1])!=trace)continue;int step=Integer.parseInt(r[2]),op=Integer.parseInt(r[3]);var actor=actors.get(Integer.parseInt(r[4]));var at=Fixtures.vector(r[6]);String result="-";
                switch(op){case 0->team.add(actor);case 1->team.remove(actor);case 2->team.addSpawn(at);case 3->result=Fixtures.vec(team.spawn());case 4->{team.lockedScore=(step/12+trace)%2==1;team.addScore(Long.parseLong(r[5]));}case 5->team.addCapture();case 6->team.addKill();case 7->team.reset();case 8->team.intel(at);case 9->team.pickup(actor);case 10->team.drop(at);case 11->team.returnHome(at);default->throw new AssertionError(op);}
                String actual=String.join(";",""+team.color(),""+team.score,""+team.captures,""+team.kills,""+team.spawnIndex,team.lockedScore?"1":"0",keys(team.players(),actors),team.intelPosition==null?"-":Fixtures.vec(team.intelPosition),team.intelHolder==null?"-":""+actors.indexOf(team.intelHolder),team.spawns().isEmpty()?"-":String.join("|",team.spawns().stream().map(Fixtures::vec).toList()));assertEquals(r[7],result,"team result "+trace+":"+step);assertEquals(r[8],actual,"team state "+trace+":"+step);
            }
        }
    }
    @ParameterizedTest @MethodSource("traces") void originalEscortIdentityOrderAndHysteresis(int trace)throws Exception{
        try(var h=new LifeHarness()){
            var actors=actors(h);var tracker=new EscortTracker(new double[]{-2,0,3,5}[trace%4],new double[]{-1,0,.5,2}[trace%4]);
            for(var r:Fixtures.rows("match-state")){if(!r[0].equals("escort")||Integer.parseInt(r[1])!=trace)continue;var p=actors.get(Integer.parseInt(r[3]));var at=Fixtures.vector(r[4]);boolean alive=r[5].equals("1");if(!alive)p.beginDeath(h.wall);else if(!p.alive())p.spawn(at,h.monotonic,1,0);p.body().position(at);int action=Integer.parseInt(r[6]);if(action==0)tracker.reset();else if(action==1)tracker.forget(actors.getFirst());var order=Fixtures.ids(r[7]).stream().map(actors::get).toList();assertEquals(r[8],keys(tracker.escorts(actors.getFirst(),order),actors),"escorts "+trace+":"+r[2]);}
        }
    }
    @ParameterizedTest @MethodSource("traces") void originalRoundAwardAccumulators(int trace)throws Exception{
        var state=new RoundCombatStats();for(var r:Fixtures.rows("match-state")){if(!r[0].equals("stats")||Integer.parseInt(r[1])!=trace)continue;int op=Integer.parseInt(r[3]),stat=Integer.parseInt(r[4]);double amount=Fixtures.number(r[5]);if(op==0)state.increment(stat,(long)amount);else if(op==1)state.raiseTo(stat,(long)amount);else state.accumulate(stat,amount);String actual=String.join(";",state.awards.isEmpty()?"-":String.join(",",state.awards.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList()),state.measures.isEmpty()?"-":String.join(",",state.measures.entrySet().stream().map(e->e.getKey()+":"+Fixtures.bits(e.getValue())).toList()),Fixtures.bits(state.lastAirstrikeSurvivedAt));assertEquals(r[6],actual,"stats "+trace+":"+r[2]);}
    }
    @ParameterizedTest @MethodSource("groups") void originalBinary64DecimalRounding(int group)throws Exception{for(var r:Fixtures.rows("match-state"))if(r[0].equals("round")&&Integer.parseInt(r[1])/256==group)assertEquals(r[4],Fixtures.bits(ObjectiveGuard.rounded(Fixtures.number(r[2]),Integer.parseInt(r[3]))),"round "+r[1]);}
}
