// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;

/** Actual life/team/event/ENet binding. Mode authorization, selection extensions and deployable retirement are required. */
public final class HostedPlayerControls {
    public interface Mode {
        Object eventPlayer(PlayerLife p);boolean allowsTeam(PlayerLife p,int team);boolean locked(int team);boolean assignsTeams();void retireDeployables(PlayerLife p);
        ClassCatalog.Selection normalize(int requested,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback);
        boolean selectable(int classId);boolean allowsSelection(PlayerLife p,ClassCatalog.Selection selection);boolean liveUgc(PlayerLife p,ClassCatalog.Selection selection);
    }
    private final PlayerControls controls;private final TeamChanges changes;
    public HostedPlayerControls(ApplicationHost host,LifeRuntime lives,Map<Integer,TeamState> teams,ModeEvents events,Mode mode,GameRules rules,AdmissionPolicy.Settings balance,double cooldown,DoubleSupplier monotonic,DoubleSupplier wall,OwnerLoop loop,BiConsumer<String,Throwable> failure){
        Objects.requireNonNull(host);Objects.requireNonNull(teams);Objects.requireNonNull(events);Objects.requireNonNull(mode);Objects.requireNonNull(failure);
        changes=new TeamChanges(lives,rules,balance,cooldown,monotonic,wall,new TeamChanges.Effects(){
            public boolean allows(PlayerLife p,int team){return mode.allowsTeam(p,team);}public boolean locked(int team){return mode.locked(team);}public boolean assignsTeams(){return mode.assignsTeams();}
            public void retireDeployables(PlayerLife p){mode.retireDeployables(p);}public void removeTeam(PlayerLife p){var team=teams.get(p.team());if(team!=null)team.remove(p);}public void addTeam(PlayerLife p){var team=teams.get(p.team());if(team!=null)team.add(p);}
            public void changed(PlayerLife p,int oldTeam,int newTeam){events.offer(ModeEvents.Event.of("on_player_team_change",mode.eventPlayer(p),oldTeam,newTeam));}
            public void send(PlayerLife p,GamePacket packet){host.sendPlayer(p.id(),packet);}public void failure(String label,RuntimeException error){failure.accept(label,error);}
        });
        var selections=new SelectionChanges(lives,new SelectionChanges.Policy(){
            public ClassCatalog.Selection normalize(int id,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback){return mode.normalize(id,tools,prefabs,ugc,fallback);}public boolean selectable(int id){return mode.selectable(id);}public boolean allows(PlayerLife p,ClassCatalog.Selection s){return mode.allowsSelection(p,s);}public boolean liveUgc(PlayerLife p,ClassCatalog.Selection s){return mode.liveUgc(p,s);}
            public void send(PlayerLife p,GamePacket packet){host.sendPlayer(p.id(),packet);}public void broadcast(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}public void failure(String label,RuntimeException error){failure.accept(label,error);}
        },monotonic);
        controls=new PlayerControls(changes,selections,new PaletteRelay(lives,()->rules.enabled("RULE_ENABLE_COLOUR_PICKER"),monotonic,loop,(p,packet)->host.broadcastKnownExcept(packet,Delivery.RELIABLE,p.id(),p.id())));lives.controls(controls);
    }
    public TeamChanges teams(){return changes;}public void resetPalette(){controls.resetPalette();}
}
