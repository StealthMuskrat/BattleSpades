// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Source packet 6. These are untrusted claims; decoding never authorizes a shot or damage. */
public record ShootPacket(int loopCount,int shooterId,int snapshotLoop,Vector3f origin,Vector3f direction,
    int damage,int penetration,boolean affectShooter,boolean secondary,int seed) implements GamePacket {
    @Override public int id(){return 6;}
    @Override public void write(Wire.Writer w){
        w.u8(id()).i32(loopCount).u8(shooterId).i32(snapshotLoop);origin.write(w);direction.write(w);
        w.i16(damage).i16(penetration).u8((affectShooter?1:0)|(secondary?2:0)).u8(seed);
    }
    static ShootPacket read(Wire.Reader r){
        int loop=r.i32(),shooter=r.u8(),snapshot=r.i32();var origin=Vector3f.read(r);var direction=Vector3f.read(r);
        int damage=r.i16(),penetration=r.i16(),flags=r.u8(),seed=r.u8();
        return new ShootPacket(loop,shooter,snapshot,origin,direction,damage,penetration,(flags&1)!=0,(flags&2)!=0,seed);
    }
}
