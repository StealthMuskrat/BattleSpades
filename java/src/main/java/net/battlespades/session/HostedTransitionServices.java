// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import net.battlespades.match.*;
import net.battlespades.protocol.GamePacket;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.PreparedMap;

/** Concrete state, sessions, wire delivery, event queue and timers around required remaining subsystem bindings. */
public final class HostedTransitionServices implements MatchTransitions.Services<PreparedMap> {
    public interface Bindings {
        void notice(String message);void resetVote();CompletionStage<Void> prepareBots();
        double graceSeconds();void resetRuntime();void resetRepair();void resetJournal();void resetHud();
        void notePlayed(String map);CompletionStage<Void> rebindBots();CompletionStage<Void> resetBotsAfterRestart();CompletionStage<Void> restartRound(TransitionMode mode);
        void failure(String label,Throwable error);
    }
    private final HostedSceneState state;
    private final ApplicationHost host;
    private final OwnerLoop loop;
    private final Bindings bindings;
    public HostedTransitionServices(HostedSceneState state,ApplicationHost host,OwnerLoop loop,Bindings bindings){this.state=Objects.requireNonNull(state);this.host=Objects.requireNonNull(host);this.loop=Objects.requireNonNull(loop);this.bindings=Objects.requireNonNull(bindings);state.attach(host);}
    public SceneRollover.State state(){return state.snapshot();}
    public List<SceneRollover.Peer> peers(){return new SessionTransitions(host,state::scene,state.domain().transitionRoster(),loop).peers();}
    public List<TeamState> teams(){return List.copyOf(state.domain().teams());}
    public boolean hasMode(String code){return state.hasMode(code);}
    public TransitionMode createMode(String code){if(!state.snapshot().code().equals(code))throw new IllegalArgumentException("Mode differs from installed domain");return state.domain().mode();}
    public void setMode(TransitionMode mode){state.setMode(mode);}
    public void installWorld(String map,String code,PreparedMap candidate){state.install(map,code,candidate);}
    public void restore(SceneRollover.State old){state.restore(old);}
    public void transitionFinished(){state.finish();}
    public CompletionStage<Void> drainLeaves(TransitionMode mode){return state.domain().events().settleLeaves(mode);}
    public void discardTimeline(){state.domain().events().clear();host.discardTransitionPackets();}
    public void broadcast(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}
    public void flush(){host.flushTransition();}
    public CompletionStage<Void> sleep(double seconds){return loop.sleep(seconds);}
    public void notice(String message){bindings.notice(message);}public void resetVote(){host.voting().ifPresent(HostedVoting::reset);bindings.resetVote();}
    public CompletionStage<Void> prepareBots(){return bindings.prepareBots();}public double graceSeconds(){return bindings.graceSeconds();}
    public void resetRuntime(){bindings.resetRuntime();}public void resetRepair(){bindings.resetRepair();}public void resetJournal(){bindings.resetJournal();}public void resetHud(){bindings.resetHud();}
    public void notePlayed(String map){host.voting().ifPresent(voting->voting.service().rotation().notePlayed(map));bindings.notePlayed(map);}public CompletionStage<Void> rebindBots(){return bindings.rebindBots();}
    public CompletionStage<Void> restartRound(TransitionMode mode){if(state.domain() instanceof StandardMatchDomain match){if(mode!=match.mode())throw new IllegalArgumentException("Restart mode is not the active domain");return HostedRoundRestart.run(match,host,loop,bindings::prepareBots,bindings::resetBotsAfterRestart,bindings::resetHud);}return bindings.restartRound(mode);}public void failure(String label,Throwable error){bindings.failure(label,error);}
}
