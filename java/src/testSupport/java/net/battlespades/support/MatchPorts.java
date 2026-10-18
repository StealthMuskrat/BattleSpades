// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;
import java.util.*;
import net.battlespades.match.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.player.SourceRandom;
import net.battlespades.protocol.GamePacket;
import net.battlespades.session.ApplicationHost;
import net.battlespades.transport.enet.Delivery;
/** Explicit known-roster/profile/result ports for real-UDP match-component tests; never packaged. */
public final class MatchPorts {
    public final LinkedHashMap<Integer,Scoreboard.Player> players=new LinkedHashMap<>();
    public final List<TeamState> teams=List.of(new TeamState(2,"Blue",17,29,251),new TeamState(3,"Green",4,5,6));
    public final List<String> events=new ArrayList<>();
    public final Map<Integer,Set<Integer>> known=new LinkedHashMap<>();
    public boolean ended,stopping;
    public final Scoreboard board;
    private final ApplicationHost host;
    private final List<Scoreboard.Peer> peers=new ArrayList<>();
    public MatchPorts(ApplicationHost host,long seed){
        this.host=Objects.requireNonNull(host);
        board=new Scoreboard(()->players,()->teams,()->Optional.of(peers),()->ended,()->stopping,new SourceRandom(seed),new Scoreboard.Effects(){
            public void broadcast(GamePacket packet,boolean reliable,Integer playerId){if(playerId==null){host.broadcast(packet,reliable?Delivery.RELIABLE:Delivery.UNRELIABLE);return;}if(!reliable)throw new AssertionError("No unreliable player-score helper");for(var entry:known.entrySet())if(host.inGame(entry.getKey())&&entry.getValue().contains(playerId))if(!host.sendPlayer(entry.getKey(),packet))throw new AssertionError("Known player score send rejected");}
            public void scoreChanged(Scoreboard.Player p,int reason){events.add("credit:"+p.id()+":"+reason);}
            public void resetProfileBaseline(Scoreboard.Player p){events.add("profile-reset:"+p.id());}
            public void resetResultBaselines(){events.add("baseline");}
            public void scheduleResults(Integer winner){events.add("result:"+winner);}
        });
    }
    public Scoreboard.Player add(PlayerLife life){var p=new Scoreboard.Player(life);players.put(life.id(),p);if(life.team()==2||life.team()==3)teams.get(life.team()-2).add(life);return p;}
    /** The test supplies ids confirmed by the native CreatePlayer stream, including its own admission packet. */
    public Scoreboard.Peer peer(int id,Set<Integer> ids){known.put(id,new HashSet<>(ids));var peer=new Scoreboard.Peer(){public boolean inGame(){return host.inGame(id);}public Optional<Set<Integer>> knownPlayers(){return Optional.of(known.get(id));}public void send(GamePacket packet){if(!host.sendPlayer(id,packet))throw new AssertionError("Direct scoreboard send rejected");}};peers.add(peer);return peer;}
}
