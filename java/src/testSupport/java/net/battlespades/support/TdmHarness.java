// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.world.*;

/** Real life/TDM/serial-event composition; explicit audio/vote/achievement/transition ports are excluded from the JAR. */
public final class TdmHarness implements AutoCloseable {
    public final LifeHarness life;
    public final List<TeamState> teams=List.of(new TeamState(2,"Blue",17,29,251),new TeamState(3,"Green",4,5,6));
    public final List<String> effects=new ArrayList<>();
    public final List<GamePacket> packets=new ArrayList<>();
    public final Map<Integer,Set<Integer>> known=new LinkedHashMap<>();
    public BiConsumer<Integer,GamePacket> delivery=(id,p)->packets.add(p);
    public final Scoreboard board;
    public final ProfileTracker profile;
    public final CombatEvidence combat;
    public final MatchLifeBinding binding;
    public final ModeEvents events;
    public final TdmMode mode;
    public boolean stopping=true;
    public Integer scheduledWinner;
    public CompletableFuture<Void> endTask;
    private final Map<Integer,Scoreboard.Peer> peers=new HashMap<>();
    public TdmHarness(TdmMode.Settings settings,int eventBudget){
        TdmMode[] active={null};MatchLifeBinding[] bound={null};LifeHarness[] h={null};
        profile=new ProfileTracker(()->new ProfileTracker.Context(false,"tdm",active[0]!=null&&active[0].clock().ended),(a,b)->a.team()!=b.team());
        board=new Scoreboard(()->bound[0].players(),()->teams,()->Optional.of(List.copyOf(peers.values())),()->active[0]!=null&&active[0].clock().ended,()->stopping,new SourceRandom(0),new Scoreboard.Effects(){
            public void broadcast(GamePacket packet,boolean reliable,Integer playerId){
                if(playerId==null){delivery.accept(null,packet);return;}
                for(var p:bound[0].players().values())if(h[0].runtime.inGame(p.id())&&(!known.containsKey(p.id())||known.get(p.id()).contains(playerId)))delivery.accept(p.id(),packet);
            }
            public void scoreChanged(Scoreboard.Player p,int reason){profile.scoreChanged(p,reason);}public void resetProfileBaseline(Scoreboard.Player p){profile.resetBaseline(p);}public void resetResultBaselines(){effects.add("baseline");}public void scheduleResults(Integer winner){effects.add("result:"+winner);}
        });
        combat=new CombatEvidence(()->new CombatEvidence.Context(active[0]==null||!active[0].clock().ended,true,false,null),()->bound[0].players(),(a,b)->a.team()!=b.team(),board);
        events=new ModeEvents(8192,eventBudget,()->active[0],e->{effects.add("plugin:"+e.name());return CompletableFuture.completedFuture(null);},(name,error)->effects.add("failure:"+name),Runnable::run);
        life=new LifeHarness(GameRules.serverDefaults(),"tdm",new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,10000),delegate->{bound[0]=new MatchLifeBinding(()->h[0].runtime,delegate,profile,combat,events,()->h[0].monotonic);return bound[0];});h[0]=life;binding=bound[0];
        mode=new TdmMode(settings,()->teams,binding::players,p->Optional.of(peer(p.id())),board,()->life.wall,()->life.monotonic,events::size,p->delivery.accept(null,p),new TdmMode.Effects(){
            public void resetLifeCounters(){binding.resetCounters();}public void matchStarted(){effects.add("start");}public void gameplayMusic(){effects.add("music");}public void rebuildResources(){effects.add("resources");}public void matchEnded(Integer winner){effects.add("end:"+winner);}public void endVote(double wall){effects.add("end-vote");}public void scheduleEnd(Integer winner){scheduledWinner=winner;endTask=new CompletableFuture<>();effects.add("schedule:"+winner);}public CompletionStage<Void> cancelEnd(){if(endTask!=null)endTask.cancel(false);effects.add("cancel");return CompletableFuture.completedFuture(null);}
            public void recordDeath(Scoreboard.Player victim){effects.add("death-site:"+victim.id());}
            public void escapeWatch(){effects.add("escape");}public void escapeFailure(RuntimeException error){effects.add("escape-failure");}public void timeoutMusic(){effects.add("timeout-music");}public void vote(double wall){effects.add("vote");}public void cosmeticFailure(RuntimeException failure){effects.add("cue-failure");}public boolean stopping(){return stopping;}
        },Runnable::run);active[0]=mode;
    }
    public Scoreboard.Peer peer(int id){return peers.computeIfAbsent(id,key->new Scoreboard.Peer(){public boolean inGame(){return life.runtime.inGame(id);}public Optional<Set<Integer>> knownPlayers(){return Optional.ofNullable(known.get(id));}public void send(GamePacket p){delivery.accept(id,p);}});}
    public Scoreboard.Player admit(int id,int team){life.admit(id,team);var p=binding.row(life.runtime.player(id));teams.stream().filter(t->t.id==team).findFirst().ifPresent(t->t.add(p.life));peer(id);return p;}
    public void tick(int loop){events.tick(loop).toCompletableFuture().join();}
    @Override public void close(){life.close();}
}
