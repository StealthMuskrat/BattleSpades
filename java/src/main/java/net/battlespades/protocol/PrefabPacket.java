// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Native packet 30 model ranges, rotations and raw voxel anchor. */
public record PrefabPacket(int id,int loop,String name,int player,int yaw,int pitch,int roll,int from,int to,int x,int y,int z,int color,boolean userBlocks) implements GamePacket {
    public PrefabPacket{if(id!=30)throw new IllegalArgumentException("Prefab packet id");}
    public static PrefabPacket read(int id,Wire.Reader r){return new PrefabPacket(id,r.i32(),r.string(),r.u8(),r.u8(),r.u8(),r.u8(),r.i32(),r.i32(),r.i16(),r.i16(),r.i16(),r.color(),r.u8()!=0);}
    @Override public void write(Wire.Writer w){w.u8(id).i32(loop).string(name).u8(player).u8(yaw).u8(pitch).u8(roll).i32(from).i32(to).i16(x).i16(y).i16(z).color(color).u8(userBlocks?1:0);}
}
