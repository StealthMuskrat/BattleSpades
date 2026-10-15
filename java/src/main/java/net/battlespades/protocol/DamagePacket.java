// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

public record DamagePacket(int playerId,int type,float damage,int face,int chunkCheck,int seed,int causerId,Vector3f position) implements GamePacket {
    @Override public int id() { return 37; }
    @Override public void write(Wire.Writer w) {
        w.u8(id()).u8(playerId).u8(type).u8(Fixed.encodeQuarter(damage)).u8(face).u8(chunkCheck).u8(seed).i16(causerId);position.write(w);
    }
    static DamagePacket read(Wire.Reader r) {
        return new DamagePacket(r.u8(),r.u8(),Fixed.decodeQuarter(r.u8()),r.u8(),r.u8(),r.u8(),r.i16(),Vector3f.read(r));
    }
}
