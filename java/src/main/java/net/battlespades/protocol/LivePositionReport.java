// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Runtime packet 116 float/fixed receive layouts, independently of the generated packet codec. */
public record LivePositionReport(double x,double y,double z) implements GamePacket {
    @Override public int id(){return 116;}
    @Override public void write(Wire.Writer writer){writer.u8(id()).f32((float)x).f32((float)y).f32((float)z);}
    public static LivePositionReport decode(byte[] body){
        var r=new Wire.Reader(body);if(r.u8()!=116)throw new ProtocolException("Expected PositionData");
        if(r.remaining()>=12)return new LivePositionReport(r.f32(),r.f32(),r.f32());
        if(r.remaining()>=6)return new LivePositionReport(Fixed.decodePosition(r.u16()),Fixed.decodePosition(r.u16()),Fixed.decodePosition(r.u16()));
        throw new ProtocolException("PositionData payload too short");
    }
}
