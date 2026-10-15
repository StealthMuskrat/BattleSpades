// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

/** ENet packet semantics; oversized ordinary unreliable sends become reliable fragments. */
public enum Delivery { RELIABLE, UNRELIABLE, UNSEQUENCED, UNRELIABLE_FRAGMENT }
