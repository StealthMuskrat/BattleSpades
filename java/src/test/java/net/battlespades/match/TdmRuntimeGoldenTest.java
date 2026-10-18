// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.runtime.IngamePackets;
import net.battlespades.support.LifeHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tomlj.Toml;
import static org.junit.jupiter.api.Assertions.*;

class TdmRuntimeGoldenTest {
    private static final List<String[]> ROWS=load();
    private static List<String[]> load(){try(var in=new GZIPInputStream(Objects.requireNonNull(TdmRuntimeGoldenTest.class.getResourceAsStream("/golden/tdm-runtime.tsv.gz")))){return new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().map(s->s.split("\t",-1)).toList();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    private static List<String[]> trace(String kind,int trace){return ROWS.stream().filter(r->r[0].equals(kind)&&Integer.parseInt(r[1])==trace).toList();}
    static IntStream traces(){return IntStream.range(0,16);}
    private static String hex(GamePacket packet){return HexFormat.of().formatHex(packet.encode());}
    private static String logs(List<String> log){return log.isEmpty()?"-":String.join("|",log);}
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private static String n(Object value){return value==null?"None":value.toString();}
    private static void verify(String[] r,String result,String state,List<String> log){String where=r[0]+" "+r[1]+":"+r[2]+" "+r[3];assertAll(where,()->assertEquals(r[5],result,"outcome"),()->assertEquals(r[6],state,"state"),()->assertEquals(r[7],logs(log),"IO order"));}
    private static final class Connection {final int id;boolean connected=true,player=true;Connection(int id){this.id=id;}}
    @ParameterizedTest @MethodSource("traces") void originalFixedPacketBatchSharesAndPostAwaitIdentity(int trace){
        var log=new ArrayList<String>();var conns=IntStream.range(0,3).mapToObj(Connection::new).toList();int capacity=new int[]{1,4,8,16}[trace%4];
        @SuppressWarnings("unchecked") IngamePackets<Connection>[] holder=(IngamePackets<Connection>[])new IngamePackets<?>[1];
        var queue=new IngamePackets<Connection>(new IngamePackets.Settings(capacity,Math.min(capacity,1+trace%4),1+trace%5),c->c.connected&&c.player,(c,data)->{
            int v=data[0]&255;log.add("R:"+c.id+":"+v);
            if(v%7==0)throw new IllegalStateException("controlled packet IO fault");if(v%11==0)c.connected=false;if(v%13==0)c.player=false;
            if(v%17==0){holder[0].offer(c,new byte[]{(byte)255});}
            log.add("A:"+c.id+":"+v);return done();
        },failure->{},Runnable::run);holder[0]=queue;
        for(var r:trace("packets",trace)){
            int step=Integer.parseInt(r[2]);var a=r[4].split(",");var c=conns.get(Integer.parseInt(a[0]));int value=Integer.parseInt(a[1]);String result="-";log.clear();
            switch(r[3]){
                case "offer"->{boolean accepted=queue.offer(c,new byte[]{(byte)value});result=accepted?"1":"0";}
                case "drain"->{queue.drain().toCompletableFuture().join();}
                case "restore"->{c.connected=true;c.player=true;}
                case "forget"->{queue.forget(c);c.connected=false;}
                case "clear"->{queue.clear();}
                default->throw new AssertionError(r[3]);
            }
            String queued=queue.snapshot().stream().map(row->row.connection().id+":"+HexFormat.of().formatHex(row.data())).collect(Collectors.joining(","));String actualQueue=queued.isEmpty()?"-":queued;
            String state=actualQueue+";"+conns.stream().map(x->""+queue.share(x)).collect(Collectors.joining(","))+";"+conns.stream().map(x->x.connected&&x.player?"1":"0").collect(Collectors.joining())+";"+queue.dropped();
            assertEquals(actualQueue.equals("-")?0:actualQueue.split(",").length,queue.size(),"queued count "+step);verify(r,result,state,log);
        }
    }
    private static final class Probe implements ModeEvents.Mode {
        final int index;final RoundClock clock;final java.util.function.Function<ModeEvents.Event,CompletionStage<Void>> handler;final List<String> log;
        Probe(int index,java.util.function.Function<ModeEvents.Event,CompletionStage<Void>> handler,List<String> log){this.index=index;this.handler=handler;this.log=log;clock=new RoundClock(new RoundClock.Effects(){public void escapeWatch(){}public void escapeFailure(RuntimeException e){throw e;}public void timeoutMusic(){}public void announce(FlowCatalog.Cue c){}public void ensureVote(double wall){}public CompletionStage<Void> endByTime(){throw new AssertionError("Probe has a terminal on_tick, not a clock policy");}});}
        public RoundClock clock(){return clock;}public CompletionStage<Void> handle(ModeEvents.Event e){return handler.apply(e);}
        public CompletionStage<Void> tick(int loop){log.add("T:"+index+":"+loop);if(loop%37==0)throw new IllegalStateException("controlled on_tick fault");return done();}
    }
    @ParameterizedTest @MethodSource("traces") void originalSerialModeHandlersPluginsReplacementAndTimeoutBudget(int trace){
        var log=new ArrayList<String>();var errors=new TreeMap<String,Integer>();var probes=new Probe[2];ModeEvents.Mode[] active={null};ModeEvents[] queue={null};
        for(int index=0;index<2;index++){int id=index;probes[id]=new Probe(id,e->{String name=e.name();int arg=(Integer)e.arguments().getFirst();if(name.equals("missing"))return done();log.add("H:"+id+":"+name+":"+arg);switch(name){case "fault"->throw new IllegalStateException("controlled mode handler fault");case "append"->queue[0].offer(ModeEvents.Event.of("work",arg+1000));case "swap"->active[0]=probes[1-id];case "clear"->queue[0].clear();default->{}}log.add("A:"+id+":"+name+":"+arg);return done();},log);}
        active[0]=probes[0];queue[0]=new ModeEvents(4+trace%5,1+trace%4,()->active[0],e->{int arg=(Integer)e.arguments().getFirst();log.add("P:"+e.name()+":"+arg);if(arg%11==0)throw new IllegalStateException("controlled plugin fault");if(arg%17==0)queue[0].offer(ModeEvents.Event.of("work",arg+2000));return done();},(name,error)->errors.merge(name,1,Integer::sum),Runnable::run);
        for(var r:trace("events",trace)){
            int step=Integer.parseInt(r[2]);String result="-";log.clear();switch(r[3]){
                case "offer"->{var a=r[4].split(",");queue[0].offer(ModeEvents.Event.of(a[0],Integer.valueOf(a[1])));}
                case "tick"->{try{queue[0].tick(step).toCompletableFuture().join();}catch(CompletionException failure){result="RuntimeError";}}
                case "cutoff"->{for(int i=0;i<2;i++){probes[i].clock.timeoutRemainder((step+trace+i)%5);probes[i].clock.ended=(step+trace+i)%3==0;}}
                case "select"->active[0]=switch((step/11+trace)%3){case 0->probes[0];case 1->probes[1];default->null;};
                case "reset"->{for(var probe:probes)probe.clock.reset(0);}
                default->throw new AssertionError(r[3]);
            }
            String queued=queue[0].snapshot().stream().map(e->e.name()+":"+e.arguments().getFirst()).collect(Collectors.joining(","));String clocks=Arrays.stream(probes).map(p->(p.clock.ended?1:0)+":"+Objects.toString(p.clock.timeoutRemaining(),"-")).collect(Collectors.joining(","));String failures=errors.entrySet().stream().map(e->e.getKey()+":"+e.getValue()).collect(Collectors.joining(","));
            verify(r,result,(queued.isEmpty()?"-":queued)+";"+(active[0]==null?-1:((Probe)active[0]).index)+";"+clocks+";"+queue[0].dropped()+";"+(failures.isEmpty()?"-":failures),log);
        }
    }
    @ParameterizedTest @MethodSource("traces") void originalConcreteTdmLifecyclePersonalTeamScoreAndHudFailureOrder(int trace){
        try(var h=new LifeHarness()){
            var log=new ArrayList<String>();boolean[] fault={false},peerFault={false};var players=new LinkedHashMap<Integer,Scoreboard.Player>();var peers=new HashMap<Integer,Scoreboard.Peer>();boolean[] present={true,true,true,true},inGame={true,true,true,true};
            for(int i=0;i<4;i++){h.admit(i,new int[]{2,3,2,0}[i]);var p=new Scoreboard.Player(h.runtime.player(i));p.score=10+i;players.put(i,p);int id=i;peers.put(i,new Scoreboard.Peer(){public boolean inGame(){return inGame[id];}public Optional<Set<Integer>> knownPlayers(){return Optional.empty();}public void send(GamePacket packet){if(!present[id])return;log.add("P"+id+":"+hex(packet));if(peerFault[0])throw new IllegalStateException("controlled personal send fault");}});}
            var teams=List.of(new TeamState(2,"Blue",17,29,251),new TeamState(3,"Green",4,5,6));TdmMode[] mode={null};java.util.function.Consumer<GamePacket> broadcast=p->{log.add("B:"+hex(p));if(fault[0])throw new IllegalStateException("controlled score/HUD send fault");};
            var profile=new ProfileTracker(()->new ProfileTracker.Context(false,"tdm",mode[0]!=null&&mode[0].clock().ended),(a,b)->a.team()!=b.team());
            var board=new Scoreboard(()->players,()->teams,Optional::empty,()->mode[0]!=null&&mode[0].clock().ended,()->true,new SourceRandom(0),new Scoreboard.Effects(){public void broadcast(GamePacket packet,boolean reliable,Integer known){broadcast.accept(packet);}public void scoreChanged(Scoreboard.Player p,int reason){profile.scoreChanged(p,reason);}public void resetProfileBaseline(Scoreboard.Player p){profile.resetBaseline(p);}public void resetResultBaselines(){log.add("Z");}public void scheduleResults(Integer winner){throw new AssertionError("No result stage in this mode trace");}});
            var settings=new TdmMode.Settings(new long[]{0,1,3,200}[trace%4],new double[]{0,3,90,900}[trace%4],new long[]{-1,0,1,3}[trace%4],trace%3,trace%2==0);
            mode[0]=new TdmMode(settings,()->teams,()->players,p->present[p.id()]?Optional.of(peers.get(p.id())):Optional.empty(),board,()->h.wall,()->h.monotonic,()->0,broadcast,new TdmMode.Effects(){
                public void resetLifeCounters(){h.runtime.resetRoundCounters();}public void matchStarted(){log.add("AS");}public void gameplayMusic(){}public void rebuildResources(){log.add("R");}public void matchEnded(Integer winner){log.add("AE:"+n(winner));}public void endVote(double wall){log.add("VE:"+Fixtures.bits(wall));}public void scheduleEnd(Integer winner){throw new AssertionError("Source stopping flag suppresses delayed end task");}public CompletionStage<Void> cancelEnd(){return done();}public void recordDeath(Scoreboard.Player p){}
                public void escapeWatch(){throw new AssertionError("Escape disabled in this mode trace");}public void escapeFailure(RuntimeException e){throw e;}public void timeoutMusic(){}public void vote(double wall){log.add("V:"+Fixtures.bits(wall));}public void cosmeticFailure(RuntimeException e){}public boolean stopping(){return true;}
            },Runnable::run);mode[0].clock().escapeEnabled=false;
            for(var r:trace("tdm",trace)){
                int step=Integer.parseInt(r[2]);var a=r[4].split(",");int ki=Integer.parseInt(a[0]),vi=Integer.parseInt(a[1]),kind=Integer.parseInt(a[2]);boolean flag=a[3].equals("1");h.wall=h.monotonic=100.+step*1.125;
                for(int i=0;i<4;i++){var p=players.get(i);p.life.bot((step+i+trace)%17==0);p.diedReloading=(step+i)%4==0;p.deathPenaltyExempt=(step+i)%23==0;present[i]=(step+i+trace)%19!=0;inGame[i]=(step+i)%13!=0;}
                for(int i=0;i<2;i++)teams.get(i).lockedScore=(step+i)%5==0;fault[0]=step%37==0;peerFault[0]=step%41==0;log.clear();String result="-";
                try{switch(r[3]){case "start"->mode[0].start();case "kill"->mode[0].kill(players.get(ki),players.get(vi),kind);case "death"->mode[0].death(players.get(vi),flag?players.get(ki):null,kind);case "retire"->mode[0].beginRetirement(flag);case "time"->mode[0].endByTime();case "score"->mode[0].endByScore(new int[]{2,3}[ki%2]);case "deactivate"->mode[0].deactivate().toCompletableFuture().join();case "reveal"->{if(present[vi])mode[0].reveal(peers.get(vi),()->{});else mode[0].reveal(new Scoreboard.Peer(){public boolean inGame(){return true;}public Optional<Set<Integer>> knownPlayers(){return Optional.empty();}public void send(GamePacket p){log.add("P"+vi+":"+hex(p));}},()->{});}case "tick"->mode[0].tick(step).toCompletableFuture().join();default->throw new AssertionError(r[3]);}}catch(RuntimeException failure){result="RuntimeError";}
                var c=mode[0].clock();String scores=players.values().stream().map(p->""+p.score).collect(Collectors.joining(","));String teamState=teams.stream().map(t->t.score+":"+t.captures+":"+t.kills+":"+t.spawnIndex).collect(Collectors.joining(","));String state=String.join(";",scores,teamState,mode[0].started()?"1":"0",c.ended?"1":"0",c.retiring?"1":"0",n(mode[0].winner()),Fixtures.bits(c.startTime),Fixtures.bits(c.elapsed),n(c.timeoutRemaining()),mode[0].endSequenceRunning()?"1":"0");verify(r,result,state,log);
            }
        }
    }
    @Test void originalTdmConfigurationPrecedence(){
        for(var r:ROWS)if(r[0].equals("settings")){
            @SuppressWarnings("unchecked") var overlay=(Map<String,Object>)net.battlespades.world.MetadataDocument.json(r[2]);
            var text=new StringBuilder("[game]\nscore_limit="+(1000+Integer.parseInt(r[1]))+"\n");if(!r[3].equals("-"))text.append("[lobby]\nmatch_length_minutes=").append(r[3]).append('\n');text.append("[modes.tdm]\n");overlay.forEach((key,value)->text.append(key).append('=').append(value).append('\n'));
            var settings=TdmMode.Settings.from(Toml.parse(text.toString()),GameRules.serverDefaults().apply(Map.of("RULE_TDM_SCORE_TARGET",Integer.parseInt(r[4]))));
            assertEquals(Long.parseLong(r[5]),settings.scoreLimit());assertEquals(Double.parseDouble(r[6]),settings.timeLimit());assertEquals(Long.parseLong(r[7]),settings.killPoints());assertEquals(Long.parseLong(r[8]),settings.headshotBonus());
        }
    }
}
