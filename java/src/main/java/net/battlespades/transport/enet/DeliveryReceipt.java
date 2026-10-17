// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

/** Owner-thread release signal for one reliable application packet.
 * Release means all fragments were acknowledged or the peer discarded the packet;
 * it never proves that the remote game applied the payload.
 */
public interface DeliveryReceipt {
    boolean released();
    /** Register the single owner-thread release observer; false means the adapter cannot report release. */
    default boolean onRelease(Runnable observer){return false;}
}
