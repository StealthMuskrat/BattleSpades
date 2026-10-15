// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.List;
import java.util.Optional;

/** Submission and result collection belong to the application owner; workers see frozen snapshots only. */
public interface SnapshotCompression extends AutoCloseable {
    interface Task extends AutoCloseable {
        Optional<List<byte[]>> poll();
        @Override void close();
    }
    default boolean available() { return true; }
    Optional<Task> submit(MapSyncSnapshot snapshot);
    @Override void close();
}
