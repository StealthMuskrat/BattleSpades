// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.scene.SceneConfig;
import net.battlespades.transport.enet.Delivery;

/** Generation-bound Character corpses, packet-36 cleanup and normal jetpack-to-grave handoff. */
public final class HostedCorpseRuntime {
    public static final class Corpse implements HitTrace.Corpse {
        public final PlayerLife owner;public final long generation;private final Vector position,orientation;private boolean exploded;
        Corpse(PlayerLife p){owner=p;generation=p.generation();position=p.position();orientation=p.aim();}
        public Vector position(){return position;}public Vector orientation(){return orientation;}
    }
    private static final class Normal {final PlayerLife owner;final long generation;double remaining;boolean exploded;Normal(PlayerLife p){owner=p;generation=p.generation();remaining=LifeCatalog.PACKS.containsKey(p.jetpack())?1:0;}}
    private final StandardMatchDomain match;private final ApplicationHost host;private final SceneConfig scene;private final double gravity;private final boolean classic,wireReady;
    private final Map<Integer,Corpse> corpses=new LinkedHashMap<>();private final Map<Integer,Normal> normal=new LinkedHashMap<>();private final Map<PlayerLife,Integer> graves=new IdentityHashMap<>();
    public HostedCorpseRuntime(StandardMatchDomain match,ApplicationHost host,SceneConfig scene,double gravity,boolean wireReady,boolean classic){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);this.scene=Objects.requireNonNull(scene);this.gravity=gravity;this.wireReady=wireReady;this.classic=classic;}
    public void died(PlayerLife p){died(p,false);}
    public void died(PlayerLife p,boolean converting){corpses.remove(p.id());normal.remove(p.id());if(classic||converting){corpses.put(p.id(),new Corpse(p));return;}var state=new Normal(p);normal.put(p.id(),state);if(state.remaining<=0)complete(state);}
    private boolean current(PlayerLife p,long generation){return p.generation()==generation&&match.lives().players().stream().anyMatch(v->v==p);}
    public boolean replicate(PlayerLife p){var state=normal.get(p.id());return state!=null&&state.owner==p&&current(p,state.generation)&&!state.exploded&&state.remaining>0;}
    public List<Corpse> hittable(){if(!scene.rules().enabled("RULE_ENABLE_CORPSE_EXPLOSION"))return List.of();return corpses.values().stream().filter(c->!c.exploded&&current(c.owner,c.generation)).toList();}
    public boolean explode(HitTrace.Corpse supplied,PlayerLife attacker,boolean show){if(!(supplied instanceof Corpse c)||corpses.get(c.owner.id())!=c||c.exploded||!current(c.owner,c.generation))return false;send(c.owner.id(),show);c.exploded=true;if(show&&scene.rules().enabled("RULE_ENABLE_CORPSE_EXPLOSION"))blast(c.position,attacker);return true;}
    private void send(int id,boolean effect){host.broadcastKnown(new SimplePackets.ExplodeCorpse(id,effect?1:0),Delivery.RELIABLE,id);}
    private void blast(Vector at,PlayerLife attacker){var request=new BlastRuntime.Request(at,0,1,12,attacker);request.radius=3;request.minimum=.05;request.maximum=.1;request.forceDestroy=false;match.entities().blasts().apply(request);}
    public void tick(){for(var state:new ArrayList<>(normal.values())){if(!current(state.owner,state.generation)||state.owner.alive()){normal.remove(state.owner.id());continue;}if(state.exploded)continue;double step=Math.min(1.0/60,state.remaining);if(step>0){state.owner.body().update(step,gravity,List.of());state.remaining=Math.max(0,state.remaining-step);}if(state.remaining<=1e-9)complete(state);}}
    private void complete(Normal state){if(normal.get(state.owner.id())!=state||state.exploded)return;boolean show=scene.rules().enabled("RULE_ENABLE_CORPSE_EXPLOSION");send(state.owner.id(),show);state.exploded=true;state.owner.body().exploded(true);if(show)blast(state.owner.position(),state.owner);if(scene.rules().enabled("RULE_ENABLE_GRAVESTONES"))grave(state.owner,show);}
    private void grave(PlayerLife p,boolean explosive){
        if(!wireReady)return;var registry=match.entities().registry();var existing=registry.get(graves.get(p));if(existing!=null&&existing.alive)return;var at=p.position();var center=new PickupWorld(match.world()).drySurfaceAnchor(at.x(),at.y(),0);
        var spec=new EntityRegistry.Placement();spec.kind="grave";spec.playerId=p.id();spec.state=p.team();spec.velocity=PlayerMotion.clipped(p.body().state().velocity());var team=match.teams().stream().filter(t->t.id==p.team()).findFirst().orElse(null);if(team!=null)spec.color=new Vector(team.red,team.green,team.blue);
        spec.behavior=new GraveBehavior(new DeviceBlast(p.id(),explosive?25:0,explosive?3:0,1,13,explosive?3:0,false,.5,1,null,false),7,center);var e=registry.place(11,at.x(),at.y(),at.z(),spec);graves.put(p,e.id());match.entities().create(e);
    }
    public void beforeSpawn(PlayerLife p){var state=normal.get(p.id());if(state!=null&&state.owner==p){if(!state.exploded)send(p.id(),false);normal.remove(p.id());}var c=corpses.get(p.id());if(c!=null&&c.owner==p){if(!c.exploded)send(p.id(),false);corpses.remove(p.id());}}
    public List<GamePacket> catchup(){var result=new ArrayList<GamePacket>();for(var state:normal.values())if(state.exploded&&current(state.owner,state.generation))result.add(new SimplePackets.ExplodeCorpse(state.owner.id(),0));for(var state:corpses.values())if(state.exploded&&current(state.owner,state.generation))result.add(new SimplePackets.ExplodeCorpse(state.owner.id(),0));return List.copyOf(result);}
    public void forget(PlayerLife p){normal.remove(p.id());corpses.remove(p.id());graves.remove(p);}
    public void reset(){for(var c:corpses.values())if(!c.exploded)send(c.owner.id(),false);for(var c:normal.values())if(!c.exploded)send(c.owner.id(),false);clear();}
    public void clear(){normal.clear();corpses.clear();graves.clear();}
}
