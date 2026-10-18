// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.stream.IntStream;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
final class ScoreboardGoldenTest {
    static IntStream traces(){return IntStream.range(0,16);}
    static String hex(GamePacket packet){return HexFormat.of().formatHex(packet.encode());}
    @ParameterizedTest @MethodSource("traces") void originalScoreboardRosterPhaseAwardsCacheCreditAndFaultOrdering(int trace)throws Exception{
        try(var h=new LifeHarness()){
            h.admit(0,2);h.admit(1,2);h.admit(2,3);for(var p:h.runtime.players())p.endProtection(h.monotonic);for(int id:new int[]{0,1,2})h.runtime.damage(id,1.,id==2?1:2,0);var old=h.runtime.player(0);h.runtime.disconnected(0);h.admit(0,2);h.runtime.player(0).endProtection(h.monotonic);h.runtime.damage(0,1.,2,0);
            var actors=List.of(new Scoreboard.Player(old),new Scoreboard.Player(h.runtime.player(1)),new Scoreboard.Player(h.runtime.player(2)),new Scoreboard.Player(h.runtime.player(0)));var roster=new LinkedHashMap<Integer,Scoreboard.Player>();for(int i=0;i<3;i++)roster.put(actors.get(i).id(),actors.get(i));
            for(int i=0;i<4;i++){var p=actors.get(i);p.score=i*25;p.kills=i;p.deaths=i;p.captures=1;p.killStreak=2;p.life.bot(trace%3==0);assertFalse(p.life.damageHistory().snapshot().isEmpty());}
            var teams=List.of(new TeamState(2,"Blue",1,2,3),new TeamState(3,"Green",4,5,6));var wire=new ArrayList<String>();var events=new ArrayList<String>();long[] profile={0,0,0,0};boolean[] failure={false},ended={false},stopping={false},absent={false};var known=new ArrayList<Optional<Set<Integer>>>();known.add(Optional.empty());
            java.util.function.BiConsumer<GamePacket,String> send=(packet,tag)->{wire.add(tag+":"+hex(packet));if(failure[0])throw new IllegalStateException("send-fault");};boolean[] peer2={true};var peers=new ArrayList<Scoreboard.Peer>();for(int i=0;i<3;i++){int key=i;peers.add(new Scoreboard.Peer(){public boolean inGame(){return key!=2||peer2[0];}public Optional<Set<Integer>> knownPlayers(){return known.getFirst();}public void send(GamePacket packet){send.accept(packet,"P"+key);}});}
            var board=new Scoreboard(()->roster,()->teams,()->absent[0]?Optional.empty():Optional.of(peers),()->ended[0],()->stopping[0],new SourceRandom(816700+trace),new Scoreboard.Effects(){
                public void broadcast(GamePacket packet,boolean reliable,Integer player){send.accept(packet,"B"+(reliable?1:0)+","+(player==null?"-":player));}
                public void scoreChanged(Scoreboard.Player p,int reason){if(!p.life.bot()&&!ended[0])profile[actors.indexOf(p)]=p.score;}
                public void resetProfileBaseline(Scoreboard.Player p){profile[actors.indexOf(p)]=0;}
                public void resetResultBaselines(){events.add("baseline");}
                public void scheduleResults(Integer winner){events.add("result:"+winner);}
            });
            for(var r:Fixtures.trace("scoreboard",trace,"op")){
                int step=Integer.parseInt(r[2]),op=Integer.parseInt(r[3]);var actor=actors.get(Integer.parseInt(r[4]));long value=Long.parseLong(r[5]);Integer reason=r[6].equals("-")?null:Integer.valueOf(r[6]);double seconds=Fixtures.number(r[7]);failure[0]=r[8].equals("1");ended[0]=r[9].equals("1");stopping[0]=r[10].equals("1");known.set(0,r[11].equals("-")?Optional.empty():Optional.of(new HashSet<>(Fixtures.ids(r[11]))));absent[0]=r[12].equals("1");peer2[0]=step%4!=0;wire.clear();events.clear();String result="-",outcome="ok";
                try{
                    switch(op){
                        case 0->{actor.score=value;board.playerScore(actor,reason);}
                        case 1->{var team=teams.get(step%2);team.score=value;board.teamScore(team,reason);}
                        case 2->board.timer(seconds,step%2==0);
                        case 3->board.gameStats(2+step%2);
                        case 4->board.reveal(peers.get(step%3));
                        case 5->board.resetRound();
                        case 6->{switch(step/12%5){case 0->board.showGameStats();case 1->board.forceScores(step%2==0);case 2->board.lockTeam(new int[]{-1,0,1,2,3,255}[step%6],step%2==0);case 3->board.lockClass(new int[]{-1,0,1,2,3,255}[step%6],step%2==0);case 4->board.mapEnded();default->throw new AssertionError();}}
                        case 7->board.text(step%15-3,seconds);
                        case 8->{if(step%3==0)roster.put(0,actors.get(3));else if(step%3==1)roster.remove(0);else roster.put(0,actors.getFirst());actors.getFirst().score=value;board.playerScore(actors.getFirst(),reason);}
                        case 9->{for(int i=0;i<4;i++){var p=actors.get(i);for(int award=0;award<30;award++)p.roundStats.awards.put(award,(long)(step+award+i*3)%9);p.roundStats.measures.put("shots",(double)(step+i*7)%40);}var winners=board.teamAwards(2);result=winners.isEmpty()?"-":String.join("|",winners.stream().map(row->row.playerId()+","+row.award()).toList());}
                        case 10->{teams.getFirst().addCapture();teams.getLast().addKill();board.teamScore(teams.getFirst(),null);}
                        case 11->{actor.score=value;board.playerScore(actor,null);}
                        default->throw new AssertionError(op);
                    }
                }catch(IllegalStateException error){outcome=error.getMessage();}
                String cache=board.statsPackets().isEmpty()?"-":String.join("|",board.statsPackets().stream().map(ScoreboardGoldenTest::hex).toList());
                String snapshot=String.join(";",board.statsSent()?"1":"0",cache,String.join(",",roster.entrySet().stream().map(e->e.getKey()+":"+actors.indexOf(e.getValue())).toList()),String.join(",",Arrays.stream(profile).mapToObj(Long::toString).toList()),String.join("|",actors.stream().map(p->String.join(",",""+p.score,""+p.kills,""+p.deaths,""+p.captures,""+p.killStreak,p.life.damageHistory().snapshot().isEmpty()?"0":"1")+":"+String.join(",",p.roundStats.awards.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList())+":"+String.join(",",p.roundStats.measures.entrySet().stream().map(e->e.getKey()+"="+Fixtures.bits(e.getValue())).toList())).toList()),teams.getFirst().score+","+teams.getLast().score);
                String where="scoreboard "+trace+":"+step;assertEquals(r[13],result,where+" awards");assertEquals(r[14],outcome,where+" result");assertEquals(r[15],wire.isEmpty()?"-":String.join("|",wire),where+" wire");assertEquals(r[16],events.isEmpty()?"-":String.join(",",events),where+" bridge IO");assertEquals(r[17],snapshot,where+" state");
            }
        }
    }
    @ParameterizedTest @MethodSource("traces") void originalNativeGameStatsCodec(int group)throws Exception{
        assertTrue(PacketCodec.supports(67));for(var r:Fixtures.rows("scoreboard","codec")){if(Integer.parseInt(r[1])/32!=group)continue;var ids=Fixtures.ids(r[4]);var types=Fixtures.ids(r[5]);var rows=new ArrayList<ScorePackets.Row>();for(int i=0;i<ids.size();i++)rows.add(new ScorePackets.Row(ids.get(i),types.get(i)));var packet=new ScorePackets.GameStats(Integer.parseInt(r[2]),Integer.parseInt(r[3]),rows);assertEquals(r[6],hex(packet),"native stats "+r[1]);assertEquals(r[7],hex(PacketCodec.decode(HexFormat.of().parseHex(r[6]))),"native decoded stats "+r[1]);}
    }
    @ParameterizedTest @MethodSource("traces") void originalNativeScoreboardTextCodec(int group)throws Exception{
        assertTrue(PacketCodec.supports(73));for(var r:Fixtures.rows("scoreboard","text")){if(Integer.parseInt(r[1])/32!=group)continue;var packet=new ScorePackets.ShowTextMessage(Integer.parseInt(r[2]),(float)Fixtures.number(r[3]));assertEquals(r[4],hex(packet));var decoded=(ScorePackets.ShowTextMessage)PacketCodec.decode(HexFormat.of().parseHex(r[4]));assertEquals(r[5],hex(decoded));assertEquals(r[6],Fixtures.bits(decoded.duration()));}
    }
}
