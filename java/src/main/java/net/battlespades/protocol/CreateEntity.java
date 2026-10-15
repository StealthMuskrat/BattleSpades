// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.Objects;

public record CreateEntity(EntityRecord entity) implements GamePacket {
    public CreateEntity { Objects.requireNonNull(entity); }
    @Override public int id() { return 21; }
    @Override public void write(Wire.Writer w) { w.u8(id());entity.write(w); }
}
