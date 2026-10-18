// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import java.util.function.Supplier;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;

/** Explicit blast test policy and observable IO; never a production mode/service implementation. */
public final class BlastPorts implements BlastRuntime.Domain,BlastTerrain.Delivery {
    public final LifeHarness h;public final EntityRegistry registry=new EntityRegistry();public final TerrainPorts terrainPorts=new TerrainPorts();public final List<String> events=new ArrayList<>(),wire=new ArrayList<>();public final List<BlastTerrain.Recipient> recipients=new ArrayList<>();
    public boolean build=true,friendly,hardcore,scopeEnabled,failBegin,failEnd,failStimulus;public java.util.function.Consumer<GamePacket> reliableSink;public java.util.function.IntConsumer destroySink;public java.util.function.BiConsumer<BlastTerrain.Recipient,GamePacket> recipientSink;
    public DeployableRuntime devices;public final BlastRuntime blasts;
    public BlastPorts(LifeHarness h){this.h=h;var random=new net.battlespades.player.SourceRandom(168);var terrain=new TerrainDamage(h.world,terrainPorts,()->random.below(256));blasts=new BlastRuntime(h.runtime,registry,new BlastTerrain(h.world,terrain,this),()->h.monotonic,this,this::context);}
    public EntityContext context(){return new EntityContext(1./60,h.wall,h.runtime.players().stream().filter(p->p.alive()&&p.spawned()).toList(),registry,e->events.add("create:"+e.id()),id->{events.add("destroy:"+id);if(destroySink!=null)destroySink.accept(id);},devices);}
    public DeployableRuntime bindDevices(){devices=new DeployableRuntime(h.runtime,registry,blasts,this::context,failure->events.add("listener-fault"));return devices;}
    public boolean buildDamage(){return build;}public boolean friendlyFire(){return friendly;}public boolean hardcore(PlayerLife p){return hardcore;}
    public void stimulus(Vector at,int source,int team,double radius,double lifetime){events.add("stimulus");if(failStimulus)throw new IllegalStateException("Controlled stimulus fault");}
    public Object begin(PlayerLife source,int kill,Vector at){if(!scopeEnabled)return null;events.add("begin");if(failBegin)throw new IllegalStateException("Controlled scope fault");return new Object();}
    public void end(Object scope){events.add("end");if(failEnd)throw new IllegalStateException("Controlled scope fault");}public void scopeFault(String phase){events.add("fault:"+phase);}
    public List<BlastTerrain.Recipient> recipients(){return recipients;}
    public void reliable(BlastTerrain.Recipient recipient,GamePacket packet){wire.add("R"+recipient.id()+":"+HexFormat.of().formatHex(packet.encode()));if(recipientSink!=null)recipientSink.accept(recipient,packet);}
    public void reliable(GamePacket packet){wire.add("B:"+HexFormat.of().formatHex(packet.encode()));if(reliableSink!=null)reliableSink.accept(packet);}
}
