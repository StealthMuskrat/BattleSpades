// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.replication.ReplicationService;

/** Live Character snapshot composition; effects, held tool and corpse authorization require their owning domains. */
public final class PlayerSnapshot implements ReplicationService.Player {
    public record Presentation(int tool,Integer pickup,boolean bot,boolean onFire,boolean disguised,boolean touchingGoo,
        boolean replicateCorpse,boolean modeAllowsTool,boolean mountedMachineGunAuthorized) {}
    @FunctionalInterface public interface Domain {Presentation read(PlayerLife player);}
    private final PlayerLife life;
    private final Domain domain;
    public PlayerSnapshot(PlayerLife life,Domain domain){this.life=Objects.requireNonNull(life);this.domain=Objects.requireNonNull(domain);}
    private static Vector3f wire(Vector v){return new Vector3f((float)v.x(),(float)v.y(),(float)v.z());}
    @Override public ReplicationService.State state(int loop,double now){
        var presentation=Objects.requireNonNull(domain.read(life));var motion=life.motion().snapshot();var flight=life.flight().snapshot();
        int input=motion.actions(),action=input&3;
        if(flight.active())action|=4|(life.jetpack()==67?8:0);
        if((input&16)!=0)action|=16;
        if(presentation.onFire)action|=32;
        if((input&4)!=0)action|=64;
        if((input&64)!=0)action|=128;
        int state=(flight.canopy()?1:0)|(presentation.disguised?2:0)|((life.jetpack()==69&&(input&128)!=0)?4:0)|(presentation.touchingGoo?8:0);
        boolean live=life.alive()&&life.spawned();int tool=presentation.tool;
        boolean authorized=live&&tool>=0&&tool<66&&life.toolEnabled(tool)&&presentation.modeAllowsTool&&
            (life.selection().loadout().contains(tool)||(tool==15&&presentation.mountedMachineGunAuthorized));
        var velocity=life.spawned()||life.generation()>0?PlayerMotion.clipped(life.body().state().velocity()):new Vector(0,0,0);
        var row=new WorldUpdate.PlayerRow(life.id(),wire(life.position()),wire(life.aim()),wire(velocity),0,life.worldUpdateAck(),life.health(),motion.movement(),action,state,
            tool,presentation.pickup==null?255:presentation.pickup,flight.fuel(),life.protectionRemaining(now),0);
        var timeline=life.inputTimeline();var frames=timeline.lastFrame();
        return new ReplicationService.State(life.id(),life.name(),presentation.bot,live||presentation.replicateCorpse,timeline.appliedLabel(),timeline.state().synthesized(),
            life.motion().flightCapability(),life.generation()>0&&life.body().state().airborne(),motion.retailJumpLabel(),frames==null?null:frames.label(),
            presentation.bot?0:life.flight().roundTripMillis().orElse(0),flight.active(),flight.physicsActive(),flight.defer(),flight.tail(),
            flight.canopy(),flight.canopyPhysics(),flight.deployPending(),timeline.reorderSpread(loop),row,authorized);
    }
    @Override public void acknowledge(int stamp){life.worldUpdateAck(stamp);}
    @Override public void ownerRowQueued(int stamp,int loop,boolean transition){
        var velocity=life.generation()>0?PlayerMotion.clipped(life.body().state().velocity()):new Vector(0,0,0);
        life.recordOwnerAnchor(stamp,life.position(),velocity,loop,null);
    }
}
