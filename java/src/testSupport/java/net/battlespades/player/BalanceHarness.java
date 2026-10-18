// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.TeamState;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.support.LifeHarness;

/** Controlled mode/bot ports around real life and forced team moves, shared by source and async boundary checks. */
public final class BalanceHarness implements AutoCloseable,TeamBalancer.Policy,TeamBalancer.Bots {
    public final LifeHarness h=new LifeHarness();public final OwnerLoop loop=new OwnerLoop(100_000_000_000L,1024,1024,error->{throw new AssertionError(error);});
    public TeamBalancer balance;public final TeamChanges changes;public final Map<Integer,TeamState> teams=new LinkedHashMap<>();
    public final Map<String,Object> config=new HashMap<>(),mode=new HashMap<>();public final Set<Integer> denied=new HashSet<>(),retireDenied=new HashSet<>(),directorDenied=new HashSet<>(),removeFalse=new HashSet<>(),zero=new HashSet<>();
    public final List<PlayerLife> bots=new ArrayList<>(),vips=new ArrayList<>();public final Map<Integer,Long> scores=new HashMap<>(),captures=new HashMap<>();public Integer last;public boolean assigns,busy,reconnecting,stopped,addFailure,removeFailure,allowFailure,transitionFailure;
    public final List<List<Object>> log=new ArrayList<>(),direct=new ArrayList<>();public final List<String> failures=new ArrayList<>();public CompletableFuture<Boolean> removal;public CompletableFuture<Void> addition;
    public BalanceHarness(){
        teams.put(2,new TeamState(2,"Team",18,52,86));teams.put(3,new TeamState(3,"Team",18,52,86));
        changes=new TeamChanges(h.runtime,net.battlespades.rules.GameRules.serverDefaults(),new AdmissionPolicy.Settings(true,2,List.of(),"[BOT]"),5,()->h.monotonic,()->h.wall,new TeamChanges.Effects(){
            public boolean allows(PlayerLife p,int team){return BalanceHarness.this.allows(p,team);}public boolean locked(int team){return false;}public boolean assignsTeams(){return assigns;}public void retireDeployables(PlayerLife p){log.add(List.of("cleanup",(long)p.id(),(long)p.team()));}public void removeTeam(PlayerLife p){teams.get(p.team()).remove(p);}public void addTeam(PlayerLife p){teams.get(p.team()).add(p);}public void changed(PlayerLife p,int old,int team){log.add(List.of("change",(long)p.id(),(long)old,(long)team));}public void send(PlayerLife p,GamePacket packet){BalanceHarness.this.send(p,packet);}public void failure(String label,RuntimeException error){BalanceHarness.this.failure(label,error);}
        });newBalancer();for(int i=0;i<5;i++)add(i,i==4?3:2,false,false);log.clear();direct.clear();
    }
    public void newBalancer(){balance=new TeamBalancer(h.runtime::players,this,()->h.monotonic,loop);}
    public PlayerLife add(int id,int team,boolean alive,boolean bot){h.runtime.admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"P"+id),ClassCatalog.normalize(0,List.of(),net.battlespades.rules.GameRules.serverDefaults()),0);var p=h.runtime.player(id);teams.get(team).add(p);if(!alive)h.runtime.die(id,null,0);p.bot(bot);if(bot)bots.add(p);return p;}
    public void detach(PlayerLife p){teams.get(p.team()).remove(p);h.runtime.transitionDetach(p.id());bots.remove(p);}
    private boolean flag(Map<String,Object> m,String name,boolean value){return (Boolean)m.getOrDefault(name,value);}private double number(String name,double value){return ((Number)config.getOrDefault(name,value)).doubleValue();}
    public TeamBalancer.Settings settings(){return new TeamBalancer.Settings(flag(config,"auto_balance",true),flag(config,"balance_mid_match",true),(int)number("balance_threshold",2),number("balance_check_interval",1),number("balance_grace_seconds",0),number("balance_player_cooldown",600),number("balance_bot_wait_seconds",10));}
    public TeamBalancer.Status status(){return new TeamBalancer.Status(flag(mode,"started",true),flag(mode,"ended",false),flag(mode,"retiring",false),assigns,flag(mode,"auto_balance_enabled",true));}
    public boolean transitionBusy(){if(transitionFailure)throw new IllegalStateException("busy hook");return busy;}public TeamBalancer.Objectives objectives(){return new TeamBalancer.Objectives(vips,zero,last);}public boolean retireSafe(PlayerLife p){return !retireDenied.contains(p.id());}public boolean allows(PlayerLife p,int team){if(allowFailure)throw new IllegalStateException("allows hook");return !denied.contains(p.id());}public boolean inGame(PlayerLife p){return !h.loading.contains(p.id());}public long captures(PlayerLife p){return captures.getOrDefault(p.id(),0L);}public long score(PlayerLife p){return scores.getOrDefault(p.id(),0L);}public boolean change(PlayerLife p,int team){return changes.change(p,team,false,true);}public TeamBalancer.Bots bots(){return this;}public void send(PlayerLife p,GamePacket packet){direct.add(List.of((long)p.id(),HexFormat.of().formatHex(packet.encode())));}public void failure(String label,Throwable error){failures.add(label);}
    public boolean started(){return !stopped;}public boolean reconnecting(){return reconnecting;}public List<PlayerLife> players(){return List.copyOf(bots);}public boolean safeToRetire(PlayerLife p){return !directorDenied.contains(p.id());}
    public CompletionStage<Boolean> remove(PlayerLife p){log.add(List.of("remove",(long)p.id()));if(removeFailure)throw new IllegalStateException("remove");if(removal!=null)return removal;if(removeFalse.contains(p.id()))return CompletableFuture.completedFuture(false);detach(p);return CompletableFuture.completedFuture(true);}
    public CompletionStage<Void> add(int team){log.add(List.of("add",(long)team));if(addition!=null)return addition;if(addFailure)throw new IllegalStateException("replacement");int id=h.runtime.players().stream().mapToInt(PlayerLife::id).max().orElse(0)+1;add(id,team,true,true);return CompletableFuture.completedFuture(null);}
    @Override public void close(){loop.close();h.close();}
}
