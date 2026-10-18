// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.entities.EntityRegistry;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.protocol.DropPickup.Position;

/** Source packet-71 policy. The caller must await completion before draining the next packet.
 * Concrete objective ownership belongs to the required, explicitly optional mode port.
 */
public final class ObjectiveDropRequests {
    @FunctionalInterface public interface Mode {
        CompletionStage<Boolean> drop(PlayerLife player,Position position,Position velocity);
    }
    private final Thread owner=Thread.currentThread();
    private final Supplier<Optional<Mode>> mode;
    private final EntityRegistry registry;
    private final IntSupplier loop;
    private final Consumer<GamePacket> reliable;
    private final Executor resumeOnOwner;
    public ObjectiveDropRequests(Supplier<Optional<Mode>> mode,EntityRegistry registry,IntSupplier loop,
            Consumer<GamePacket> reliable,Executor resumeOnOwner){
        this.mode=Objects.requireNonNull(mode);this.registry=Objects.requireNonNull(registry);
        this.loop=Objects.requireNonNull(loop);this.reliable=Objects.requireNonNull(reliable);
        this.resumeOnOwner=Objects.requireNonNull(resumeOnOwner);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Objective drop outside owner");}
    public CompletionStage<Void> handle(PlayerLife player,DropPickup packet){
        owned();Objects.requireNonNull(player);Objects.requireNonNull(packet);
        var done=new CompletableFuture<Void>();
        try {
            if(!player.alive()||!player.spawned()||player.pickupId()==null||packet.pickupId()!=player.pickupId()){
                done.complete(null);return done.minimalCompletionStage();
            }
            var handler=Objects.requireNonNull(mode.get());
            if(handler.isEmpty()){fallback(player,packet);done.complete(null);}
            else Objects.requireNonNull(handler.get().drop(player,packet.position(),packet.velocity())).whenComplete((handled,failure)->{
                Runnable resume=()->{
                    try {
                        owned();
                        if(failure!=null)done.completeExceptionally(failure);
                        else {if(!Boolean.TRUE.equals(handled))fallback(player,packet);done.complete(null);}
                    }catch(RuntimeException error){done.completeExceptionally(error);}
                };
                if(Thread.currentThread()==owner)resume.run();
                else try{resumeOnOwner.execute(resume);}catch(RuntimeException error){done.completeExceptionally(error);}
            });
        }catch(RuntimeException error){done.completeExceptionally(error);}
        return done.minimalCompletionStage();
    }
    private void fallback(PlayerLife player,DropPickup packet){
        // Do not repeat the pre-await guards or retain the old carried type/state/loop.
        var drop=ObjectiveCarry.drop(player,packet.position(),packet.velocity(),loop,reliable);
        if(drop==null)return;
        var settings=new EntityRegistry.Placement();settings.state=drop.state();settings.kind="pickup";
        settings.velocity=new Vector(drop.velocity().x(),drop.velocity().y(),drop.velocity().z());settings.radius=.5;
        var at=drop.position();registry.place(drop.type(),at.x(),at.y(),at.z(),settings);
        // The original fallback creates registry state only, without an immediate CreateEntity.
    }
}
