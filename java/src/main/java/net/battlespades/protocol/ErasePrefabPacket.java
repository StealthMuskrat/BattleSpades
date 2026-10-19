// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Packet 31 differs from packet 30: fixed-position anchor, no colour or user-block flag. */
public record ErasePrefabPacket(int loop,String name,int player,int yaw,int pitch,int roll,int from,int to,Vector3f position) implements GamePacket {
    public int id(){return 31;}
    public static ErasePrefabPacket read(Wire.Reader r){return new ErasePrefabPacket(r.i32(),r.string(),r.u8(),r.u8(),r.u8(),r.u8(),r.i32(),r.i32(),Vector3f.readFixed(r));}
    public void write(Wire.Writer w){w.u8(id()).i32(loop).string(name).u8(player).u8(yaw).u8(pitch).u8(roll).i32(from).i32(to);position.writeFixed(w);}
}
