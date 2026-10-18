// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.battlespades.player.PlayerLife;

/** One owner-loop frame. Null lifecycle callbacks and registry have the source's optional semantics. */
public record EntityContext(double dt,double now,List<PlayerLife> players,EntityRegistry registry,
        Consumer<MapEntity> create,IntConsumer destroy,DeployableRuntime devices,PickupWorld world,Consumer<MapEntity> move) {
    public EntityContext(double dt,double now,List<PlayerLife> players,EntityRegistry registry,Consumer<MapEntity> create,IntConsumer destroy,DeployableRuntime devices){this(dt,now,players,registry,create,destroy,devices,null,null);}
    public EntityContext(double dt,double now,List<PlayerLife> players,EntityRegistry registry,Consumer<MapEntity> create,IntConsumer destroy){this(dt,now,players,registry,create,destroy,null);}
    public EntityContext { Objects.requireNonNull(players); }
}
