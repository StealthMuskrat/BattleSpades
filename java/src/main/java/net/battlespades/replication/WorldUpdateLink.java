// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.replication;

import java.util.Set;
import net.battlespades.session.SnapshotHolds;
import net.battlespades.transport.enet.Delivery;

/** Owned connection boundary for actual queue acceptance, packet-release barriers and per-peer sizing. */
public interface WorldUpdateLink {
    default boolean inGame(){return true;}
    Integer localPlayerId();
    SnapshotObserver observerState();
    boolean snapshot(byte[] body,Delivery delivery,int loop);
    boolean snapshotUnsequenced();
    Set<SnapshotHolds.Row> heldSnapshotRows(int loop);
    int snapshotPayloadLimit();
    long reliableSendIndex();
    boolean reliableUnackedThrough(long index,int loop);
    void flushSnapshots();
}
