// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;
/** Native look-at controller; released by the next death or spawn. */
public record PoiFocus(float x,float y,float z) implements GamePacket {
    public int id(){return 18;}
    public void write(Wire.Writer w){w.u8(id()).i16(Fixed.encodePosition(x)).i16(Fixed.encodePosition(y)).i16(Fixed.encodePosition(z));}
    static PoiFocus read(Wire.Reader r){return new PoiFocus((float)Fixed.decodePosition(r.i16()),(float)Fixed.decodePosition(r.i16()),(float)Fixed.decodePosition(r.i16()));}
}
