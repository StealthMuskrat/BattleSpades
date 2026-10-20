// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.battlespades.match.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.runtime.OwnerLoop;

/** In-place restart: real mode/objective, entity, score and connected-life owners; bot/HUD services remain explicit. */
final class HostedRoundRestart {
    static RoundRestart<PlayerLife> create(StandardMatchDomain match,ApplicationHost host,OwnerLoop loop,
            Supplier<CompletionStage<Void>> prepareBots,Supplier<CompletionStage<Void>> resetBots,Runnable resetHud){
        var mode=match.mode();
        return new RoundRestart<>(new RoundRestart.Services<PlayerLife>(){
            public boolean scoreboard(){return mode.scoreboardEnabled();}public void forceScores(boolean forced){match.scoreboard().forceScores(forced);}
            public CompletionStage<Void> prepareBots(){return prepareBots.get();}
            public void resetRuntime(){match.hud().clear();resetHud.run();match.lives().resetRoundRuntime();}
            public List<TeamState> teams(){return match.teams();}public CompletionStage<Void> startMode(){return match.events().settleLeaves(mode).thenCompose(unused->{match.events().clear();host.discardTransitionPackets();return mode.startMode();});}
            public void teamScore(TeamState team){match.scoreboard().teamScore(team,null);}
            public List<PlayerLife> players(){return match.lives().players();}public boolean playable(PlayerLife p){return p.team()==2||p.team()==3;}
            public boolean connected(PlayerLife p){return match.lives().players().stream().anyMatch(current->current==p)&&host.addressHost(p.id()).isPresent();}
            public void respawn(PlayerLife p){match.lives().respawn(p.id());}public void respawnFailure(PlayerLife p,Throwable error){match.failure("restart respawn:"+p.id(),error);}
            public CompletionStage<Void> resetBots(){return resetBots.get();}
        },loop);
    }
}
