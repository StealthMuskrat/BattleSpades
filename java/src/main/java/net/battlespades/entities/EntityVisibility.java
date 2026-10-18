// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.battlespades.protocol.CreateEntity;
import net.battlespades.protocol.GamePacket;
import net.battlespades.protocol.SimplePackets;

/** Per-connection known IDs. The required sender queues reliable packets before an ID becomes known. */
public final class EntityVisibility {
    private final Thread owner=Thread.currentThread();
    private final Set<Integer> known=new HashSet<>();
    private final Consumer<GamePacket> reliable;
    public EntityVisibility(Consumer<GamePacket> reliable){this.reliable=Objects.requireNonNull(reliable);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Entity visibility outside owner");}
    public boolean reveal(MapEntity entity){owned();if(!entity.alive||!entity.wireVisible||known.contains(entity.id()))return false;reliable.accept(new CreateEntity(entity.toWireEntity()));known.add(entity.id());return true;}
    public boolean destroy(int id){owned();if(!known.contains(id))return false;reliable.accept(new SimplePackets.DestroyEntity(id));known.remove(id);return true;}
    public boolean knows(int id){owned();return known.contains(id);}
    public void forget(int id){owned();known.remove(id);}
    public void clear(){owned();known.clear();}
}
