// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.protocol.GamePacket;

/** Explicit virtual modes/peers/terminal services around the real transition policy; excluded from the JAR. */
public final class TransitionHarness implements MatchTransitions.Services<Object> {
    public final int scenario,fault;
    public final List<String> log=new ArrayList<>();
    public final List<TeamState> teams=List.of(new TeamState(2,"Blue",0,0,255),new TeamState(3,"Green",0,255,0));
    public final List<SceneRollover.Peer> connections=new ArrayList<>();
    public final Map<Integer,Integer> players=new LinkedHashMap<>();
    public final Probe old=new Probe("old");
    public TransitionMode active=old;
    public final Object oldWorld=new Object(),candidate=new Object();
    public Object world=oldWorld,fog="fog";
    public String map="London",code="tdm";
    public final ModeEvents events;
    public int packets=1;
    public List<SceneRollover.Peer> peerOverride;
    public java.util.function.Consumer<GamePacket> broadcastSink;
    public Runnable flushSink,discardOperation;
    public java.util.function.Consumer<Object> installOperation;
    public java.util.function.Supplier<TransitionMode> createModeOperation;
    public java.util.function.Function<TransitionMode,CompletionStage<Void>> leavesOperation;
    public java.util.function.Function<TransitionMode,CompletionStage<Void>> restartOperation;
    public TransitionHarness(int scenario){
        this.scenario=scenario;fault=scenario%8;teams.forEach(t->t.score=10);events=new ModeEvents(256,256,()->active,event->{log.add("plugin:on_player_leave:"+event.arguments().getFirst());return done();},(label,error)->{},Runnable::run);events.offer(ModeEvents.Event.of("on_player_leave",99));events.offer(ModeEvents.Event.of("other"));
        if(scenario<8||scenario>=16)for(int i=0;i<3;i++){connections.add(new Peer(i));if(scenario>=16)players.put(i,i);}
    }
    public static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    public class Probe implements TransitionMode {
        public final String name;
        public Probe(String name){this.name=name;}
        public RoundClock clock(){throw new UnsupportedOperationException("No tick in transition oracle");}
        public CompletionStage<Void> tick(int loop){throw new UnsupportedOperationException("No tick in transition oracle");}
        public CompletionStage<Void> handle(ModeEvents.Event event){int id=((Number)event.arguments().getFirst()).intValue();log.add(name+":leave:"+id);if(fault==2&&(id==99||scenario>=16&&id==1))throw new IllegalStateException("leave failure");return done();}
        public CompletionStage<Void> cancelEndSequence(){log.add(name+":cancel");return done();}
        public void beginRetirement(boolean ended){log.add(name+":retire:"+(ended?1:0));}
        public CompletionStage<Void> deactivate(){log.add(name+":deactivate");if(fault==3&&name.equals("old")||scenario==13&&name.equals("new"))throw new IllegalStateException("deactivate failure");return done();}
        public CompletionStage<Void> startMode(){log.add(name+":start");if(fault==5&&name.equals("new")||scenario==13&&name.equals("old"))throw new IllegalStateException("start failure");return done();}
    }
    public class Peer implements SceneRollover.Peer {
        public final int id;public boolean live;public final boolean carries;
        public Peer(int id){this.id=id;live=id==0;carries=id<2;}
        public boolean inGame(){return live;}public boolean carriesScene(){return carries;}
        public void arm(){log.add("arm:"+id);}public void send(GamePacket packet){log.add("peer:"+id+":"+HexFormat.of().formatHex(packet.encode()));}public void gate(){live=false;}
        public CompletionStage<Void> detach(TransitionMode mode){if(!players.containsKey(id))return done();return mode.handle(ModeEvents.Event.of("on_player_leave",id)).thenRun(()->{log.add("forget:"+id);players.remove(id);});}
        public CompletionStage<Boolean> reload(){log.add("reload:"+id);if(fault==6&&id==0)throw new IllegalStateException("reload failure");return CompletableFuture.completedFuture(!(fault==7&&id==1));}
        public void disconnect(int reason){log.add("disconnect:"+id+":"+reason);}
    }
    public SceneRollover.State state(){return new SceneRollover.State(map,code,active,world,fog);}
    public List<SceneRollover.Peer> peers(){return List.copyOf(peerOverride==null?connections:peerOverride);}public List<TeamState> teams(){return teams;}
    public boolean hasMode(String requested){return requested.equals("tdm");}public TransitionMode createMode(String requested){return createModeOperation==null?new Probe("new"):createModeOperation.get();}
    public void notice(String message){log.add("notice:"+message);}
    public void resetVote(){log.addAll(List.of("vote-cancel","vote-consume","vote-kicks"));}
    public void broadcast(GamePacket packet){log.add("broadcast:"+HexFormat.of().formatHex(packet.encode()));if(broadcastSink!=null)broadcastSink.accept(packet);}public void flush(){log.add("flush");if(flushSink!=null)flushSink.run();}
    public CompletionStage<Void> prepareBots(){log.add("bots-prepare");if(fault==1)throw new IllegalStateException("prepare failure");return done();}
    public CompletionStage<Void> drainLeaves(TransitionMode mode){return leavesOperation==null?events.settleLeaves(mode):leavesOperation.apply(mode);}
    public void discardTimeline(){events.clear();packets=0;if(discardOperation!=null)discardOperation.run();}
    public double graceSeconds(){return scenario%2;}
    public java.util.function.DoubleFunction<CompletionStage<Void>> sleepOperation;
    public CompletionStage<Void> sleep(double seconds){log.add("sleep:"+(int)seconds);return sleepOperation==null?done():sleepOperation.apply(seconds);}
    public void resetRuntime(){log.add("runtime-reset");if(fault==0&&scenario>=8)throw new IllegalStateException("reset failure");}
    public void resetRepair(){log.add("repair-reset");}public void resetJournal(){log.addAll(List.of("journal-clear","journal-clear"));}public void resetHud(){log.add("hud-reset");}
    public void notePlayed(String oldMap){log.add("played:"+oldMap);}public void installWorld(String next,String mode,Object prepared){map=next;code=mode;world=prepared;log.add("bind-journal");fog=null;if(installOperation!=null)installOperation.accept(prepared);}
    public void setMode(TransitionMode mode){active=mode;}
    public CompletionStage<Void> rebindBots(){log.add("bots-rebind");if(fault==4)throw new IllegalStateException("rebind failure");return done();}
    public void restore(SceneRollover.State previous){map=previous.map();code=previous.code();world=previous.world();log.add("bind-journal");fog=previous.fog();active=previous.mode();}
    public void failure(String label,Throwable error){}
    public CompletionStage<Void> restartRound(TransitionMode mode){return restartOperation==null?mode.startMode():restartOperation.apply(mode);}
}
