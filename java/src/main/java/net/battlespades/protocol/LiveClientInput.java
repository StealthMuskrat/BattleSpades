// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Receive-side layout from protocol/runtime_packets.py, distinct from the reconstructed codec. */
public record LiveClientInput(int loopCount,int playerId,boolean paletteEnabled,int toolId,
        double x,double y,double z,int ooo,int movementFlags,int actionFlags,double deploymentYaw) {
    public static LiveClientInput decode(byte[] body) {
        var r=new Wire.Reader(body);if(r.u8()!=4) throw new ProtocolException("Expected ClientData");
        int loop=r.i32(),player=r.u8(),tool=r.u8();
        double x=orientation(r.u16()),y=orientation(r.u16()),z=orientation(r.u16());
        int ooo=r.u8(),movement=r.u8(),action=r.u8();
        double yaw=r.remaining()>=4?r.f32():r.remaining()>=2?Fixed.decodePosition(r.u16()):0;
        if(!Double.isFinite(yaw)) throw new ProtocolException("Non-finite client yaw");
        // The reference runtime accepts an omitted yaw and ignores additional input tail bytes.
        return new LiveClientInput(loop,player&127,(player&128)!=0,tool,x,y,z,ooo,movement,action,yaw);
    }
    private static double orientation(int raw) {
        int magnitude=raw&32767;double value=magnitude>=16384?(magnitude-8192)/8192.0:magnitude/8192.0;
        return (raw&32768)==0?value:-value;
    }
}
