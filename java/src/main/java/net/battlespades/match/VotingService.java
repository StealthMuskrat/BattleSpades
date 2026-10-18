// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import net.battlespades.protocol.*;

/** Single retail overlay owner combining rotation, map waiters, kick policy and match-boundary reset. */
public final class VotingService<P> {
    private final Thread owner=Thread.currentThread();private final MapRotation rotation;private final KickBallot.Context<P> context;
    private final Supplier<String> current,mode;private final Supplier<List<String>> stock;private final MapBallot<P> map;private final KickBallot<P> kick;
    public VotingService(MapRotation rotation,KickBallot.Context<P> context,KickBallot.Settings settings,Supplier<String> current,Supplier<String> mode,Supplier<List<String>> stock,DoubleSupplier monotonic,DoubleSupplier wall,Function<Double,CompletionStage<Void>> sleep,Consumer<GamePacket> broadcast,BiConsumer<String,Throwable> failure,Executor resume){
        this.rotation=Objects.requireNonNull(rotation);this.context=Objects.requireNonNull(context);this.current=Objects.requireNonNull(current);this.mode=Objects.requireNonNull(mode);this.stock=Objects.requireNonNull(stock);
        var linked=new AtomicReference<KickBallot<P>>();map=new MapBallot<>(rotation.catalog().available(),context::players,context::id,context::eligible,monotonic,sleep,broadcast,resume,()->linked.get()!=null&&linked.get().active());kick=new KickBallot<>(context,settings,map::active,monotonic,wall,broadcast,failure);linked.set(kick);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Voting outside owner");}
    public MapBallot<P> map(){owned();return map;}public KickBallot<P> kick(){owned();return kick;}public MapRotation rotation(){owned();return rotation;}
    public boolean ensureMapVote(double wall){
        owned();if(map.active()||kick.active()||map.nextMap()!=null)return false;String active=current.get();rotation.notePlayed(active);int humans=0,bots=0;for(var player:context.players().values())if(context.bot(player))bots++;else humans++;
        var choices=rotation.rank(rotation.catalog().forMode(mode.get(),stock.get()),active,humans,bots);return !choices.isEmpty()&&map.start(choices.subList(0,Math.min(3,choices.size())),wall);
    }
    public boolean ensureRoundEndVote(double wall){owned();if(map.active())return false;if(kick.active())kick.cancel(false,null);return ensureMapVote(wall);}
    public void tick(Double wall){owned();kick.tick(wall);map.tick(wall);}
    public void packet(P player,GamePacket packet,double wall){
        owned();if(packet instanceof SimplePackets.InitiateKickMessage request){if(request.reason()==3){if(kick.active()&&Objects.equals(kick.starter(),context.id(player)))kick.cancel(true,null);}else kick.start(player,context.players().get(request.target_id()),request.reason(),wall);}
        else if(packet instanceof VotePacket vote&&vote.messageType()==1&&!vote.candidates().isEmpty()){if(kick.active())kick.castWire(player,vote.candidates().getFirst().name());else map.castWire(player,vote.candidates().getFirst().name());}
    }
    public void reveal(Consumer<GamePacket> send){owned();if(kick.active())kick.reveal(send);else map.reveal(send);}
    public void forget(int id){owned();kick.forget(id);map.forget(id);}
    public void reset(){owned();kick.cancel(false,null);map.cancel();map.consume();kick.clearMatchKicks();}
}
