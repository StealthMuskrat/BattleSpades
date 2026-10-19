// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Packet 10's seven sign-magnitude fixed values; the claimed player is never an authority. */
public record OrientedItemPacket(int loop,int player,int tool,double fuse,Vector3f position,Vector3f velocity) implements GamePacket {
    @Override public int id(){return 10;}
    public static OrientedItemPacket read(Wire.Reader r){return new OrientedItemPacket(r.i32(),r.u8(),r.u8(),Fixed.decodePosition(r.u16()),vector(r),vector(r));}
    private static Vector3f vector(Wire.Reader r){return new Vector3f((float)Fixed.decodePosition(r.u16()),(float)Fixed.decodePosition(r.u16()),(float)Fixed.decodePosition(r.u16()));}
    @Override public void write(Wire.Writer w){w.u8(id()).i32(loop).u8(player).u8(tool).i16(Fixed.encodePosition(fuse));vector(w,position);vector(w,velocity);}
    private static void vector(Wire.Writer w,Vector3f v){w.i16(Fixed.encodePosition(v.x())).i16(Fixed.encodePosition(v.y())).i16(Fixed.encodePosition(v.z()));}
}
