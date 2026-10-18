// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;

/** Actual Arena/life/score/event composition. External terminal services are controlled and never packaged. */
public final class ArenaHarness implements AutoCloseable {
    public final LifeHarness life;public final ArenaMode mode;public final ModeEvents events;public final MatchLifeBinding binding;public final Scoreboard board;
    public final List<TeamState> teams=List.of(new TeamState(2,"Blue",18,52,86),new TeamState(3,"Green",50,60,70));
    public final List<List<Object>> wire=new ArrayList<>();public final List<String> effects=new ArrayList<>();public final List<Integer> respawned=new ArrayList<>();
    public BiConsumer<Integer,GamePacket> delivery=(id,packet)->wire.add(Arrays.asList(id==null?"B":"P",id==null?null:(long)id,HexFormat.of().formatHex(packet.encode())));
    public final MusicService audio=new MusicService(p->delivery.accept(null,p),new SourceRandom(0));
    public boolean stopping=true;public Runnable music=()->delivery.accept(null,new SimplePackets.StopMusic());public final Map<Integer,Scoreboard.Peer> peers=new HashMap<>();
    public ArenaHarness(ArenaMode.Settings settings){
        ArenaMode[] active={null};MatchLifeBinding[] rows={null};LifeHarness[] h={null};var profile=new ProfileTracker(()->new ProfileTracker.Context(false,"arena",active[0]!=null&&active[0].clock().ended),(a,b)->a.team()!=b.team());
        board=new Scoreboard(()->rows[0].players(),()->teams,()->Optional.of(List.copyOf(peers.values())),()->active[0]!=null&&active[0].clock().ended,()->stopping,new SourceRandom(0),new Scoreboard.Effects(){public void broadcast(GamePacket p,boolean reliable,Integer known){delivery.accept(null,p);}public void scoreChanged(Scoreboard.Player p,int reason){profile.scoreChanged(p,reason);}public void resetProfileBaseline(Scoreboard.Player p){profile.resetBaseline(p);}public void resetResultBaselines(){effects.add("baseline");}public void scheduleResults(Integer winner){effects.add("results");}});
        var combat=new CombatEvidence(()->new CombatEvidence.Context(active[0]==null||!active[0].clock().ended,true,false,null),()->rows[0].players(),(a,b)->a.team()!=b.team(),board);
        events=new ModeEvents(8192,1024,()->active[0],e->CompletableFuture.completedFuture(null),(name,error)->{throw new AssertionError(name,error);},Runnable::run);
        life=new LifeHarness(GameRules.serverDefaults(),"arena",new net.battlespades.world.MutableWorld(net.battlespades.world.VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,10000),delegate->{rows[0]=new MatchLifeBinding(()->h[0].runtime,delegate,profile,combat,events,()->h[0].monotonic);return rows[0];});h[0]=life;binding=rows[0];
        mode=new ArenaMode(settings,()->teams,binding::players,p->p.bot()?Optional.empty():Optional.of(peer(p.id())),board,()->life.wall,()->life.monotonic,events::size,()->60,p->delivery.accept(null,p),new ArenaMode.Effects(){
            public void resetLifeCounters(){binding.resetCounters();}public void matchStarted(){effects.add("start");}public void gameplayMusic(){music.run();}public void rebuildResources(){effects.add("resources");}public void matchEnded(Integer winner){effects.add("end:"+Objects.toString(winner,"None"));}public void endVote(double now){effects.add("vote-end");}public void scheduleEnd(Integer winner){effects.add("schedule:"+winner);}public CompletionStage<Void> cancelEnd(){return CompletableFuture.completedFuture(null);}public void recordDeath(Scoreboard.Player p){}public void escapeWatch(){}public void escapeFailure(RuntimeException error){throw error;}public void timeoutMusic(){audio.timeout();}public void vote(double now){effects.add("vote");}public void cosmeticFailure(RuntimeException error){effects.add("cosmetic-failure");}public boolean stopping(){return stopping;}public void respawn(PlayerLife p){respawned.add(p.id());life.runtime.respawn(p.id());}public void respawnFailure(PlayerLife p,RuntimeException error){effects.add("respawn-failure:"+p.id());}
        },Runnable::run);active[0]=mode;mode.clock().escapeEnabled=false;life.scoreValue=p->Math.toIntExact(binding.row(p).score);life.respawnGate=mode::canRespawn;life.respawnDelay=p->mode.respawnTime(life.delay);life.damageModifier=Optional.of((victim,amount,source,type)->mode.damage(amount));
        life.destination=new LifeRuntime.Messages(){public void broadcast(GamePacket p){delivery.accept(null,p);}public void owner(int id,GamePacket p){delivery.accept(id,p);}public boolean inGame(int id){return !life.loading.contains(id);}};
    }
    public Scoreboard.Peer peer(int id){return peers.computeIfAbsent(id,key->new Scoreboard.Peer(){public boolean inGame(){return life.runtime.inGame(id);}public Optional<Set<Integer>> knownPlayers(){return Optional.empty();}public void send(GamePacket p){delivery.accept(id,p);}});}
    public PlayerLife admit(int id,int team){life.runtime.admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"P"+id),ClassCatalog.normalize(0,List.of(),GameRules.serverDefaults()),0);var p=life.runtime.player(id);teams.stream().filter(t->t.id==team).findFirst().ifPresent(t->t.add(p));peer(id);return p;}
    @Override public void close(){life.close();}
}
