// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.transport.enet;

/** Portable resource limits. Wire timings/windows use the vendored ENet defaults. */
public record EnetSettings(int peerCount, int maxPacketBytes, int maxWaitingBytes,
        int maxOutgoingBytes, int maxPendingCommands, int eventCapacity, int eventBytes,
        int receiveBudget, int sendBudget, boolean requireProtocol168) {
    public EnetSettings {
        if (peerCount < 1 || peerCount > 4095 || maxPacketBytes < 1 || maxWaitingBytes < maxPacketBytes
                || maxOutgoingBytes < maxPacketBytes || maxPendingCommands < 32 || maxPendingCommands > 28672
                || eventCapacity < peerCount * 2 || eventBytes < maxPacketBytes || receiveBudget < 1 || sendBudget < 1) {
            throw new IllegalArgumentException("Invalid ENet resource limits");
        }
    }
    public static EnetSettings defaults() {
        return new EnetSettings(64, 32*1024*1024, 32*1024*1024, 32*1024*1024,
                8192, 4096, 32*1024*1024, 256, 256, true);
    }
}
