// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

public interface GamePacket {
    int id();
    void write(Wire.Writer writer);
    default byte[] encode() { var writer = new Wire.Writer(); write(writer); return writer.toByteArray(); }
}
