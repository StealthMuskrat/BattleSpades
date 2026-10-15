// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Packet 4. Palette bit and action flags follow ClientData, not WorldUpdate. */
public record ClientInput(int loopCount, int playerId, boolean paletteEnabled, int toolId,
        float x, float y, float z, int ooo, int movementFlags, int actionFlags,
        double deploymentYaw) implements GamePacket {
    public ClientInput {
        if (playerId < 0 || playerId > 127) throw new ProtocolException("Invalid ClientData player id");
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Double.isFinite(deploymentYaw)) {
            throw new ProtocolException("Non-finite ClientData orientation");
        }
    }
    @Override public int id() { return 4; }
    public boolean up() { return (movementFlags & 1) != 0; }
    public boolean jump() { return (movementFlags & 16) != 0; }
    public boolean crouch() { return (movementFlags & 32) != 0; }
    public boolean sprint() { return (movementFlags & 128) != 0; }
    public boolean primary() { return (actionFlags & 1) != 0; }
    public boolean zoom() { return (actionFlags & 4) != 0; }
    public boolean deployed() { return (actionFlags & 64) != 0; }
    public boolean hover() { return (actionFlags & 128) != 0; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).i32(loopCount).u8(playerId | (paletteEnabled ? 128 : 0)).u8(toolId)
            .i16(Fixed.encodeOrientation(x)).i16(Fixed.encodeOrientation(y)).i16(Fixed.encodeOrientation(z))
            .u8(ooo).u8(movementFlags).u8(actionFlags).i16(Fixed.encodePosition(deploymentYaw));
    }
    static ClientInput read(Wire.Reader r) {
        int loop = r.i32(), player = r.u8(), tool = r.u8();
        return new ClientInput(loop, player & 127, (player & 128) != 0, tool,
            Fixed.decodeOrientation(r.i16()), Fixed.decodeOrientation(r.i16()), Fixed.decodeOrientation(r.i16()),
            r.u8(), r.u8(), r.u8(), Fixed.decodePosition(r.i16()));
    }
}
