// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.transport.enet.Delivery;

/** Actual canonical life/ENet identity, denial delivery, disconnect and reconnect admission binding. */
public final class HostedVoting implements KickBallot.Context<PlayerLife> {
    private final ApplicationHost host;private final Supplier<LifeRuntime> lives;private final Predicate<PlayerLife> protectedHost;private final DoubleSupplier wall;private final VotingService<PlayerLife> votes;
    public HostedVoting(ApplicationHost host,Supplier<LifeRuntime> lives,MapCatalog catalog,MapRotation.Settings fit,KickBallot.Settings kicks,Supplier<String> current,Supplier<String> mode,Supplier<List<String>> stock,Predicate<PlayerLife> protectedHost,DoubleSupplier wall,OwnerLoop loop,BiConsumer<String,Throwable> failure){
        this.host=Objects.requireNonNull(host);this.lives=Objects.requireNonNull(lives);this.protectedHost=Objects.requireNonNull(protectedHost);this.wall=Objects.requireNonNull(wall);
        votes=new VotingService<>(new MapRotation(catalog,fit),this,kicks,current,mode,stock,()->loop.nowNanos()/1e9,wall,loop::sleep,packet->host.broadcast(packet,Delivery.RELIABLE),failure,loop);host.admissionGate(votes.kick()::matchKickReason);
    }
    public VotingService<PlayerLife> service(){return votes;}
    public Map<Integer,PlayerLife> players(){var result=new LinkedHashMap<Integer,PlayerLife>();for(var player:lives.get().players())result.put(player.id(),player);return result;}
    public int id(PlayerLife player){return player.id();}public Integer team(PlayerLife player){return player.team();}public String name(PlayerLife player){return player.name();}public boolean bot(PlayerLife player){return player.bot();}public boolean eligible(PlayerLife player){return !player.bot()&&host.inGame(player.id());}
    public String host(PlayerLife player){return host.addressHost(player.id()).orElse(null);}public boolean protectedHost(PlayerLife player){return protectedHost.test(player);}public void send(PlayerLife player,GamePacket packet){host.sendPlayer(player.id(),packet);}public void disconnect(PlayerLife player,int reason){host.disconnectPlayer(player.id(),reason);}
    public void packet(PlayerLife player,GamePacket packet){if(players().get(player.id())==player)votes.packet(player,packet,wall.getAsDouble());}
    public void reveal(PlayerLife player){if(players().get(player.id())==player&&host.inGame(player.id()))votes.reveal(packet->host.sendPlayer(player.id(),packet));}
    public void forget(int id){votes.forget(id);}public void reset(){votes.reset();}
}
