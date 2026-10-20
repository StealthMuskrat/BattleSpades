// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Retail packet 41. Tracking IDs resolve CreateEntity objects on the receiving client. */
public record MinimapBillboard(int entityId,int key,int color,float x,float y,float z,String icon,boolean tracking) implements GamePacket {
    public int id(){return 41;}
    public void write(Wire.Writer w){w.u8(id()).i16(entityId).u8(key).u8(color&255).u8(color>>8&255).u8(color>>16&255).i16(Fixed.encodePosition(x)).i16(Fixed.encodePosition(y)).i16(Fixed.encodePosition(z)).string(icon).u8(tracking?1:0);}
    static MinimapBillboard read(Wire.Reader r){int id=r.i16(),key=r.u8(),b=r.u8(),g=r.u8(),red=r.u8();float x=(float)Fixed.decodePosition(r.i16()),y=(float)Fixed.decodePosition(r.i16()),z=(float)Fixed.decodePosition(r.i16());return new MinimapBillboard(id,key,red<<16|g<<8|b,x,y,z,r.string(),r.u8()!=0);}
}
