// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.player.*;
import net.battlespades.runtime.OwnerSequence;

/** Await old mode ownership release, then forget lifecycle/team/roster state without a PlayerLeft broadcast. */
public final class TransitionRoster {
    private final OwnerSequence owner;
    private final LifeRuntime lives;
    private final MatchLifeBinding scores;
    private final Supplier<List<TeamState>> teams;
    public TransitionRoster(LifeRuntime lives,MatchLifeBinding scores,Supplier<List<TeamState>> teams,Executor resume){this.lives=Objects.requireNonNull(lives);this.scores=Objects.requireNonNull(scores);this.teams=Objects.requireNonNull(teams);owner=new OwnerSequence(resume);}
    public CompletionStage<Void> detach(int id,TransitionMode mode){
        owner.owned();var player=lives.players().stream().filter(p->p.id()==id).findFirst().orElse(null);if(player==null)return CompletableFuture.completedFuture(null);
        var actor=scores.row(player);return owner.run(List.<Supplier<CompletionStage<Void>>>of(
            ()->mode==null?CompletableFuture.completedFuture(null):mode.handle(ModeEvents.Event.of("on_player_leave",actor)),
            ()->{if(lives.players().stream().anyMatch(p->p==player))lives.transitionDetach(id,p->teams.get().stream().filter(t->t.id==p.team()).findFirst().ifPresent(t->t.remove(p)));return CompletableFuture.completedFuture(null);}).iterator());
    }
}
