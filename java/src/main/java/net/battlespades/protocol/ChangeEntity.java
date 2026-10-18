// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Original packet 16 actions: state, position/velocity/forward, owner, signed target, fuse/ammo. */
public record ChangeEntity(int entityId,int action,int state,Vector3f vector,int playerId,int targetId,float scalar) implements GamePacket {
    @Override public int id(){return 16;}
    @Override public void write(Wire.Writer w){w.u8(id()).i16(entityId).u8(action);switch(action){case 0->w.u8(state);case 1,2,4->vector.writeFixed(w);case 3->w.u8(playerId);case 5->w.u8(targetId&255);case 6,7->w.i16(Fixed.encodePosition(scalar));default->{}}}
    static ChangeEntity read(Wire.Reader r){int id=r.i16(),action=r.u8(),state=0,player=0,target=-1;float scalar=0;var vector=new Vector3f(0,0,0);switch(action){case 0->state=r.u8();case 1,2,4->vector=Vector3f.readFixed(r);case 3->player=r.u8();case 5->{int raw=r.u8();target=raw<128?raw:raw-256;}case 6,7->scalar=(float)Fixed.decodePosition(r.i16());default->{}}return new ChangeEntity(id,action,state,vector,player,target,scalar);}
    public static ChangeEntity position(int id,Vector3f at){return new ChangeEntity(id,1,0,at,0,-1,0);}
}
