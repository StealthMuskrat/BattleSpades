// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

/** Immutable transport event. Gameplay consumes these through its own bounded ownership boundary. */
public record EnetEvent(Type type, EnetPeer peer, Delivery delivery, byte[] data, int reason, boolean timedOut) {
    public enum Type { CONNECT, RECEIVE, DISCONNECT }
    public EnetEvent { data = data.clone(); }
    @Override public byte[] data() { return data.clone(); }
    int dataLength() { return data.length; }
}
