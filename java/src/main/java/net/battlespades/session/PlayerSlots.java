// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.BitSet;
import java.util.Optional;

/** IDs promised by StateData stay reserved through admission until disconnect or scene retirement. */
public final class PlayerSlots {
    private final Thread owner=Thread.currentThread();
    private final BitSet used=new BitSet();
    private final int limit;
    public PlayerSlots(int limit) {if(limit<1 || limit>255) throw new IllegalArgumentException("Player slots");this.limit=limit;}
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Slots accessed outside owner");}
    public int limit(){owned();return limit;}
    public int occupied() {owned();return used.cardinality();}
    public Optional<Reservation> reserve() {owned();int id=used.nextClearBit(0);if(id>=limit) return Optional.empty();used.set(id);return Optional.of(new Reservation(id));}
    private java.util.function.BooleanSupplier humanRoom=()->false;
    public void humanRoom(java.util.function.BooleanSupplier callback){owned();humanRoom=java.util.Objects.requireNonNull(callback);}
    /** Peerless allocations use reserve(); only a full human admission may retire a bot. */
    public Optional<Reservation> reserveHuman(){owned();var slot=reserve();if(slot.isEmpty()&&humanRoom.getAsBoolean())slot=reserve();return slot;}
    public final class Reservation implements AutoCloseable {
        private final int id;private boolean closed;
        private Reservation(int id) {this.id=id;}
        public int id() {owned();if(closed) throw new IllegalStateException("Slot released");return id;}
        @Override public void close() {owned();if(!closed) {closed=true;used.clear(id);}}
    }
}
