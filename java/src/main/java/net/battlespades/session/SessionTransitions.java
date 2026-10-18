// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.protocol.GamePacket;
import net.battlespades.runtime.OwnerSequence;

/** Actual retained ENet peers adapted to the source rollover policy; domain leave remains explicitly bound. */
public final class SessionTransitions {
    private final ApplicationHost host;
    private final Supplier<JoinScene> replacement;
    private final TransitionRoster roster;
    private final OwnerSequence owner;
    public SessionTransitions(ApplicationHost host,Supplier<JoinScene> replacement,TransitionRoster roster,Executor resume){this.host=Objects.requireNonNull(host);this.replacement=Objects.requireNonNull(replacement);this.roster=Objects.requireNonNull(roster);owner=new OwnerSequence(resume);}
    public List<SceneRollover.Peer> peers(){owner.owned();return host.transitionSessions().stream().<SceneRollover.Peer>map(session->new SceneRollover.Peer(){
        public boolean inGame(){return session.inGame();}public boolean carriesScene(){return session.carriesScene();}public void arm(){session.armSceneTransition();}public void send(GamePacket packet){session.transitionPacket(packet);}public void gate(){session.gateSceneTransition();}
        public CompletionStage<Void> detach(TransitionMode mode){return owner.run(List.<Supplier<CompletionStage<Void>>>of(()->roster.detach(session.playerId().orElse(-1),mode),()->{session.detachScenePlayer();return CompletableFuture.completedFuture(null);}).iterator());}
        public CompletionStage<Boolean> reload(){return session.reloadScene(replacement.get());}public void disconnect(int reason){session.fail(reason);}
    }).toList();}
}
