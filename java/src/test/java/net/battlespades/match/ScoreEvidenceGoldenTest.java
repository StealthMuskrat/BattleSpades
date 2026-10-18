// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.stream.IntStream;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
final class ScoreEvidenceGoldenTest {
    static IntStream traces(){return IntStream.range(0,16);}
    static IntStream groups(){return IntStream.range(0,8);}
    static String profile(Scoreboard.Player p){var s=p.profileStats;if(s==null)return "-";return String.join(";",s.values.isEmpty()?"-":String.join(",",s.values.entrySet().stream().map(e->e.getKey()+":"+e.getValue().count()+":"+e.getValue().score()).toList()),s.remainder.isEmpty()?"-":String.join(",",s.remainder.entrySet().stream().map(e->e.getKey()+":"+Fixtures.bits(e.getValue())).toList()),String.join(",",""+s.shotSerial,""+s.hitSerial,""+s.lastScore,s.activitySeen?"1":"0",""+s.humanOpponents,""+s.botOpponents),String.join(",",Fixtures.bits(s.connectedSeconds),Fixtures.bits(s.activeSeconds),Fixtures.bits(s.idleSeconds),Fixtures.bits(s.afkSeconds),Fixtures.bits(s.opponentSampleSeconds)),s.lastPosition==null?"-":Fixtures.vec(s.lastPosition),s.lastOrientation==null?"-":Fixtures.vec(s.lastOrientation));}
    static String round(Scoreboard.Player p){var s=p.roundStats;return String.join(";",s.awards.isEmpty()?"-":String.join(",",s.awards.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).toList()),s.measures.isEmpty()?"-":String.join(",",s.measures.entrySet().stream().map(e->e.getKey()+":"+Fixtures.bits(e.getValue())).toList()),Fixtures.bits(s.lastAirstrikeSurvivedAt),s.lastPosition==null?"-":Fixtures.vec(s.lastPosition));}
    @ParameterizedTest @MethodSource("traces") void originalConnectionProfileCountersAndActivity(int trace)throws Exception{check("profile",trace);}
    @ParameterizedTest @MethodSource("traces") void originalActivityAfkMapTimeAndPopulation(int trace)throws Exception{check("activity",trace);}
    @ParameterizedTest @MethodSource("traces") void originalAcceptedCombatAwardsAndAssistCredits(int trace)throws Exception{check("combat",trace);}
    @ParameterizedTest @MethodSource("traces") void originalObjectiveCreditsAndPriorityGeometry(int trace)throws Exception{check("objective",trace);}
    void check(String domain,int trace)throws Exception{
        try(var h=new LifeHarness()){
            var roster=new LinkedHashMap<Integer,Scoreboard.Player>();for(int id=0;id<4;id++){h.admit(id,new int[]{2,2,3,0}[id]);roster.put(id,new Scoreboard.Player(h.runtime.player(id)));}var actors=new ArrayList<>(roster.values());var p=actors.getFirst();
            ProfileTracker.Context[] context={new ProfileTracker.Context(false,"tdm",false)};boolean[] retiring={false};var wire=new ArrayList<String>();boolean[] fault={false};var profile=new ProfileTracker(()->context[0],(a,b)->a.team()!=b.team());
            var board=new Scoreboard(()->roster,List::of,Optional::empty,()->context[0].ended(),()->false,new SourceRandom(0),new Scoreboard.Effects(){public void broadcast(GamePacket packet,boolean reliable,Integer known){wire.add(HexFormat.of().formatHex(packet.encode()));if(fault[0])throw new IllegalStateException("controlled terminal send fault");}public void scoreChanged(Scoreboard.Player player,int reason){profile.scoreChanged(player,reason);}public void resetProfileBaseline(Scoreboard.Player player){profile.resetBaseline(player);}public void resetResultBaselines(){throw new AssertionError("No result IO");}public void scheduleResults(Integer winner){throw new AssertionError("No result IO");}});
            var objective=new ObjectiveScores(()->context[0],()->retiring[0],()->roster,board);CombatEvidence.Context[] cc={new CombatEvidence.Context(true,true,false,null)};var combat=new CombatEvidence(()->cc[0],()->roster,(a,b)->a.team()!=b.team(),board);
            for(var r:Fixtures.trace("score-evidence",trace,domain)){
                int step=Integer.parseInt(r[2]),op=Integer.parseInt(r[3]);var at=Fixtures.vector(r[4]);var aim=Fixtures.vector(r[5]);boolean alive=r[6].equals("1"),air=r[8].equals("1"),fire=r[9].equals("1");if(alive&&!p.life.alive())p.life.spawn(at,h.monotonic,1,0);if(!alive)p.life.beginDeath(h.wall);p.life.body().position(at);p.life.orientation(aim.x(),aim.y(),aim.z());p.life.bot(r[7].equals("1"));p.life.inventory().tool(Integer.parseInt(r[10]),false);p.life.apply(new ClassCatalog.Selection(Integer.parseInt(r[11]),List.of(),List.of(),List.of()));p.life.health(Integer.parseInt(r[12]));context[0]=new ProfileTracker.Context(r[15].equals("1"),r[13],r[14].equals("1"));retiring[0]=r[16].equals("1");p.score=Long.parseLong(r[17]);p.killStreak=Long.parseLong(r[18]);int type=Integer.parseInt(r[19]),count=Integer.parseInt(r[22]),extra=Integer.parseInt(r[23]),stat=Integer.parseInt(r[24]);double dt=Fixtures.number(r[20]),amount=Fixtures.number(r[21]);cc[0]=new CombatEvidence.Context(context[0].active(),step%3!=0&&!r[13].equals("zom"),step%5==0,r[13].equals("zom")?2:null);wire.clear();fault[0]=r[28].equals("1");if(domain.equals("activity"))actors.get(2).life.bot(step>=90);if(domain.equals("objective")){roster.remove(0);if(!r[29].equals("1"))roster.put(0,r[29].equals("0")?p:actors.get(1));}String result="-";
                try{
                    if(domain.equals("profile")||domain.equals("activity"))switch(op){
                        case 0->profile.add(p,stat,Math.max(0,count),Math.max(0,(long)amount));
                        case 1->{var state=profile.tracker(p);if(state!=null)state.fractional(stat,amount);}
                        case 2->profile.shot(p);case 3->profile.hit(p,actors.get(extra%3));
                        case 4->profile.death(actors.get(2),extra<2?p:extra==2?actors.get(2):null,type);
                        case 5->profile.scoreChanged(p,new int[]{1,3,5,41,47,55,220,250}[(step/10+trace)%8]);
                        case 6->profile.total(p,stat,Math.max(0,count));case 7->profile.tick(p,dt,air,r[27],actors);case 8->profile.resetBaseline(p);
                        case 9->{if(p.profileStats!=null){var copy=new LinkedHashMap<>(p.profileStats.snapshot());if(!copy.isEmpty())copy.put(copy.keySet().iterator().next(),new ProfileStats.Delta(999,0));}}
                        default->throw new AssertionError(op);
                    }else if(domain.equals("combat"))switch(op){
                        case 0->combat.damageTaken(p,actors.get(extra%3),amount,type,step*.5);case 1->combat.shot(p);
                        case 2->combat.blocksPlaced(p,IntStream.range(239-count,239).boxed().toList());case 3->combat.blocksDestroyed(p,count,List.of(extra,extra+2));case 4->combat.crate(p,extra);case 5->combat.tick(p,dt,air,fire);
                        case 6,7->{var victim=actors.get(2);long gen=step/8+1;while(victim.life.generation()<gen)victim.life.spawn(new Vector(36,36,97),h.monotonic,1,0);victim.life.inventory().tool(18,false);var contributions=List.of(new DamageHistory.Contribution(p.life,2,1,20,step*.5),new DamageHistory.Contribution(actors.get(1).life,2,1,new double[]{49,50,100,75}[extra],step*.5-new double[]{0,5,5.001,-1}[extra]));combat.death(victim,extra==3?victim:p,type,extra==1,contributions,step*.5);}
                        default->throw new AssertionError(op);
                    }else switch(op){
                        case 0->result=objective.award(p,(long)amount,55,14,Optional.of(new ObjectiveScores.Mode(context[0].ended(),retiring[0])))?"True":"False";
                        case 1->result=objective.killEvent(p,new String[]{"defend","distract","intercept","absent"}[extra],Map.of("defend",10L,"distract",20L,"intercept",30L),Map.of("defend",55,"distract",53,"intercept",58),Optional.of(new ObjectiveScores.Mode(context[0].ended(),retiring[0])))?"True":"False";
                        case 2->{var carrier=actors.get(1);if(extra!=2&&!carrier.life.alive())carrier.life.spawn(new Vector(37,36,97),h.monotonic,1,0);if(extra==2)carrier.life.beginDeath(h.wall);carrier.life.body().position(new Vector(37,36,97));result=ObjectiveScores.classify(p,actors.get(2),extra==0,List.of(p,carrier),extra==1?List.of(new Vector(36,36,97)):List.of(),extra!=3?List.of(at):List.of(),extra*.5,extra*.5);if(result==null)result="-";}
                        case 3->result=ObjectiveScores.distraction(p,actors.get(2),type,List.of(p,actors.get(1)),extra*.5)?"True":"False";
                        default->throw new AssertionError(op);
                    }
                }catch(IllegalStateException e){result="RuntimeError";}catch(IllegalArgumentException e){result="ValueError";}catch(ArithmeticException e){result="OverflowError";}
                String snapshot=String.join("|",actors.stream().map(a->(domain.equals("profile")||domain.equals("activity"))?profile(a):round(a)).toList())+"#"+String.join(",",actors.stream().map(a->""+a.score).toList())+"#"+String.join(",",wire);String where=domain+" "+trace+":"+step;assertEquals(r[25],result,where+" outcome");assertEquals(r[26],snapshot,where+" state and native score wire");
            }
        }
    }
    @ParameterizedTest @MethodSource("groups") void originalThreeCoordinateCompensatedNorm(int group)throws Exception{for(var r:Fixtures.rows("score-evidence","norm"))if(Integer.parseInt(r[1])/256==group){var v=Fixtures.vector(r[2]);assertEquals(r[3],Fixtures.bits(SpawnMath.hypot(v.x(),v.y(),v.z())),"norm "+r[1]);}}
}
