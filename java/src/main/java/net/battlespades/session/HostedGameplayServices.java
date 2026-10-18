// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.*;
import net.battlespades.match.GameplaySounds;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.simulation.PeriodicServices;
import net.battlespades.transport.enet.Delivery;

/** Concrete host/life/control binding. Round, objective, scoring, bot and hearing domains are supplied by their owners. */
public final class HostedGameplayServices {
    public interface Domain {
        TeamBalancer.Settings settings();TeamBalancer.Status status();boolean transitionBusy();TeamBalancer.Objectives objectives();
        boolean retireSafe(PlayerLife p);boolean allows(PlayerLife p,int team);long captures(PlayerLife p);long score(PlayerLife p);TeamBalancer.Bots bots();
        boolean hearingEnabled();void blockDestroyed(GameplaySounds.Position center,int sourceId,int team,double lifetime);
        void updateBotSkill(double now);void anticheatReport(double now);
    }
    private final TeamBalancer balance;private final GameplaySounds<PlayerLife> sounds;private final PeriodicServices periodic;
    public HostedGameplayServices(ApplicationHost host,LifeRuntime lives,TeamChanges changes,Domain domain,DoubleSupplier monotonic,OwnerLoop loop,BiConsumer<String,Throwable> failure){
        Objects.requireNonNull(host);Objects.requireNonNull(lives);Objects.requireNonNull(changes);Objects.requireNonNull(domain);Objects.requireNonNull(failure);
        balance=new TeamBalancer(lives::players,new TeamBalancer.Policy(){
            public TeamBalancer.Settings settings(){return domain.settings();}public TeamBalancer.Status status(){return domain.status();}public boolean transitionBusy(){return domain.transitionBusy();}public TeamBalancer.Objectives objectives(){return domain.objectives();}public boolean retireSafe(PlayerLife p){return domain.retireSafe(p);}public boolean allows(PlayerLife p,int team){return domain.allows(p,team);}public boolean inGame(PlayerLife p){return host.inGame(p.id());}public long captures(PlayerLife p){return domain.captures(p);}public long score(PlayerLife p){return domain.score(p);}public boolean change(PlayerLife p,int team){return changes.change(p,team,false,true);}public TeamBalancer.Bots bots(){return domain.bots();}public void send(PlayerLife p,GamePacket packet){host.sendPlayer(p.id(),packet);}public void failure(String label,Throwable error){failure.accept(label,error);}
        },monotonic,loop);
        sounds=new GameplaySounds<>(new GameplaySounds.Ports<PlayerLife>(){
            public List<PlayerLife> players(){return lives.players();}public int id(PlayerLife p){return p.id();}public int team(PlayerLife p){return p.team();}public boolean connected(PlayerLife p){return !p.bot();}public boolean settled(PlayerLife p){return host.inGame(p.id());}
            public void broadcast(GamePacket packet,PlayerLife exclude,boolean reliable){var delivery=reliable?Delivery.RELIABLE:Delivery.UNRELIABLE;if(exclude==null)host.broadcast(packet,delivery);else host.broadcastExcept(packet,delivery,exclude.id());}
            public void send(PlayerLife p,GamePacket packet,boolean reliable){if(lives.players().stream().noneMatch(current->current==p)||p.bot())return;host.sendPlayer(p.id(),packet,reliable?Delivery.RELIABLE:Delivery.UNRELIABLE);}
            public boolean hearingEnabled(){return domain.hearingEnabled();}public void blockDestroyed(GameplaySounds.Position center,int id,int team,double lifetime){domain.blockDestroyed(center,id,team,lifetime);}
        });
        periodic=new PeriodicServices(balance::tick,domain::updateBotSkill,domain::anticheatReport,failure,loop);
    }
    public TeamBalancer balance(){return balance;}public GameplaySounds<PlayerLife> sounds(){return sounds;}public PeriodicServices periodic(){return periodic;}
}
