// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.*;

/** Actual music, scoreboard wire packets, ballot waits and timers for the ending policy. */
public final class RoundPresentation implements RoundEndSequence.Services {
    public interface Bindings {
        boolean scoreboard();void botsEnd();void statistics(Integer winner);double screenSeconds();Integer headline(Integer winner);
        RoundEndSequence.Transition transition();CompletionStage<Void> restartInPlace();CompletionStage<Void> cancelPending();
        boolean recoverable();void failed();void failure(String label,Throwable error);
    }
    private final MusicService music;private final MapBallot<?> ballot;private final OwnerLoop loop;private final OwnerSequence owner;
    private final Supplier<String> map;private final Consumer<GamePacket> broadcast;private final Bindings bindings;
    public RoundPresentation(MusicService music,MapBallot<?> ballot,OwnerLoop loop,Supplier<String> map,Consumer<GamePacket> broadcast,Bindings bindings){this.music=Objects.requireNonNull(music);this.ballot=Objects.requireNonNull(ballot);this.loop=Objects.requireNonNull(loop);owner=new OwnerSequence(loop);this.map=Objects.requireNonNull(map);this.broadcast=Objects.requireNonNull(broadcast);this.bindings=Objects.requireNonNull(bindings);}
    public void endingMusic(){music.ending();}public boolean scoreboard(){return bindings.scoreboard();}public void forceScores(boolean forced){broadcast.accept(new SimplePackets.ForceShowScores(forced?1:0));}public void botsEnd(){bindings.botsEnd();}
    public CompletionStage<Void> sleep(double seconds){return loop.sleep(seconds);}public void statistics(Integer winner){bindings.statistics(winner);}
    public CompletionStage<Void> waitForVote(){
        owner.owned();var source=ballot.waitForResult().toCompletableFuture();var result=new CompletableFuture<Void>();
        source.whenComplete((value,error)->owner.resume(()->{if(error==null)result.complete(null);else result.completeExceptionally(OwnerSequence.cause(error));},result));
        result.whenComplete((unused,error)->{if(result.isCancelled())source.cancel(false);});return result;
    }
    public String consumeMap(){return ballot.consume();}public String currentMap(){return map.get();}public double screenSeconds(){return bindings.screenSeconds();}public Integer headline(Integer winner){return bindings.headline(winner);}
    public RoundEndSequence.Transition transition(){return bindings.transition();}public CompletionStage<Void> restartInPlace(){return bindings.restartInPlace();}public CompletionStage<Void> cancelPending(){return bindings.cancelPending();}
    public boolean recoverable(){return bindings.recoverable();}public void failed(){bindings.failed();}public void failure(String label,Throwable error){bindings.failure(label,error);}
}
