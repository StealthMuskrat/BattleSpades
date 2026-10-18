// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import net.battlespades.protocol.GamePacket;
import java.util.concurrent.*;
/** Awaitable gameplay packet boundary shared by loader scenes and their actual game domain. */
public interface AsyncPackets {
    void packet(int playerId,GamePacket packet);
    default CompletionStage<Void> packetAsync(int playerId,GamePacket packet){packet(playerId,packet);return CompletableFuture.completedFuture(null);}
}
