// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Server-side direct-hit presentation packets. Their fields do not authorize client damage. */
public final class CombatPackets {
    private CombatPackets() {}
    public record HitEntity(int entityId,Vector3f position,int type) implements GamePacket {
        @Override public int id(){return 20;}
        @Override public void write(Wire.Writer w){w.u8(id()).i16(entityId);position.writeFixed(w);w.u8(type);}
        static HitEntity read(Wire.Reader r){return new HitEntity(r.i16(),Vector3f.readFixed(r),r.u8());}
    }
    public record ShootResponse(int damageBy,int damaged,int blood,Vector3f position) implements GamePacket {
        @Override public int id(){return 9;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(damageBy).u8(damaged).u8(blood);position.writeFixed(w);}
        static ShootResponse read(Wire.Reader r){return new ShootResponse(r.u8(),r.u8(),r.u8(),Vector3f.readFixed(r));}
    }
}
