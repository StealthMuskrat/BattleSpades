// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.match.HudState;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;

/** Hosted HUD delivery and connection-scoped audiences around the live loader barrier. */
public final class HostedHud implements AutoCloseable {
    private final Map<PlayerLife,Object> tokens=new IdentityHashMap<>();private final ApplicationHost host;private final StandardMatchDomain match;private final HudState state;
    public HostedHud(ApplicationHost host,StandardMatchDomain match){this.host=Objects.requireNonNull(host);this.match=Objects.requireNonNull(match);state=new HudState(()->host.transitionSessions().stream().map(this::peer).toList());}
    public HudState state(){return state;}
    private HudState.Peer peer(JoinSession s){match.lives().players().stream().filter(p->p.id()==s.playerId().orElse(-1)).findFirst().ifPresent(p->tokens.put(p,s));return new HudState.Peer(){public Object token(){return s;}public boolean inGame(){return s.inGame();}public boolean knows(int id){return s.knownEntities().contains(id&65535);}public boolean send(GamePacket p){return s.gameplay(p,Delivery.RELIABLE);}};}
    private List<HudState.Peer> recipients(Collection<PlayerLife> players){if(players==null)return null;return host.transitionSessions().stream().filter(s->s.playerId().isPresent()&&players.stream().anyMatch(p->p.id()==s.playerId().orElse(-1)&&match.lives().players().stream().anyMatch(current->current==p))).map(this::peer).toList();}
    public boolean add(int id,int key,String icon,Vector at,boolean tracking,Integer color,Collection<PlayerLife> players){return state.add(id,key,icon,at,tracking,color,recipients(players));}
    public boolean remove(int id,Collection<PlayerLife> players){return state.remove(id,recipients(players));}
    public boolean focus(Vector at,Collection<PlayerLife> players){return state.focus(at,recipients(players));}
    public List<GamePacket> reveal(int id,List<GamePacket> preceding){var session=host.transitionSessions().stream().filter(s->s.playerId().orElse(-1)==id).findFirst().orElse(null);if(session==null)return List.of();var known=new HashSet<>(session.knownEntities());for(var packet:preceding){if(packet instanceof CreateEntity created)known.add(created.entity().entityId()&65535);else if(packet instanceof SimplePackets.DestroyEntity removed)known.remove(removed.entity_id()&65535);}return state.reveal(peer(session),entity->known.contains(entity&65535));}
    public void forget(PlayerLife player){var token=tokens.remove(player);if(token!=null)state.forget(token);}
    public void clear(){state.clear();}
    @Override public void close(){state.close();tokens.clear();}
}
