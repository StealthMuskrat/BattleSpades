// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;

/** Per-peer riot shield anchor creation/target-before-impact and life/team retirement. */
public final class ShieldAnchors {
    private record Anchor(PlayerLife bearer,long generation,MapEntity entity){}
    private final StandardMatchDomain match;private final ApplicationHost host;private final Map<Integer,Anchor> anchors=new HashMap<>();
    public ShieldAnchors(StandardMatchDomain match,ApplicationHost host){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);}
    public void hit(PlayerLife p,Vector position,boolean melee){
        var anchor=anchors.get(p.id());if(anchor!=null&&(anchor.bearer!=p||anchor.generation!=p.generation()||match.entities().registry().get(anchor.entity.id())!=anchor.entity)){forget(p.id());anchor=null;}
        if(anchor==null){var v=new EntityRegistry.Placement();v.playerId=p.id();v.state=p.team();v.kind="projectile";var at=p.position();var e=match.entities().registry().place(39,at.x(),at.y(),at.z(),v);anchor=new Anchor(p,p.generation(),e);anchors.put(p.id(),anchor);}
        var e=anchor.entity;for(var session:host.transitionSessions()){
            if(!session.inGame()||!session.knownPlayers().contains(p.id()))continue;
            if(!session.knownEntities().contains(e.id())){if(!session.gameplay(new CreateEntity(e.toWireEntity()),Delivery.RELIABLE))continue;session.gameplay(new ChangeEntity(e.id(),5,0,new Vector3f(0,0,0),0,p.id(),0),Delivery.RELIABLE);}
            if(session.knownEntities().contains(e.id()))session.gameplay(new CombatPackets.HitEntity(e.id(),new Vector3f((float)position.x(),(float)position.y(),(float)position.z()),melee?2:0),Delivery.RELIABLE);
        }
    }
    public void sweep(){for(var entry:new ArrayList<>(anchors.entrySet())){var a=entry.getValue();if(match.entities().registry().get(a.entity.id())!=a.entity||match.lives().players().stream().noneMatch(p->p==a.bearer)||!a.bearer.alive()||!a.bearer.spawned()||a.bearer.generation()!=a.generation||a.entity.state!=a.bearer.team())forget(entry.getKey());}}
    public void forget(int id){var a=anchors.remove(id);if(a!=null&&match.entities().registry().get(a.entity.id())==a.entity){match.entities().registry().remove(a.entity.id());match.entities().destroy(a.entity.id());}}
    public void clear(){anchors.clear();}
}
