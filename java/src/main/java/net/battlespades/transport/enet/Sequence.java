// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

/** Modular arithmetic within an unambiguous half-window of the ENet uint16 clock. */
public final class Sequence {
    private Sequence() {}
    public static int next(int value) { return (value + 1) & 65535; }
    public static int distance(int candidate, int baseline) { return (candidate - baseline) & 65535; }
    public static boolean after(int candidate, int baseline) {
        int d = distance(candidate, baseline);
        return d > 0 && d < 32768;
    }
}
