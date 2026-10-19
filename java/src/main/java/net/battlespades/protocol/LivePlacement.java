// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Literal voxel layouts from protocol.runtime_packets, independent of generated fixed-point loaders. */
public record LivePlacement(int id,int loop,int claimedPlayer,int x,int y,int z,int face,double yaw,int item,int placing) implements GamePacket {
    public static boolean supports(int id){return id==1||id>=87&&id<=92||id==97||id==104;}
    private static boolean player(int id){return id>=87&&id<=91;}
    private static boolean face(int id){return id==1||id==90||id==92;}
    private static boolean yaw(int id){return id==87||id==88;}
    public static LivePlacement decode(byte[] body){
        var r=new Wire.Reader(body);int id=r.u8();if(!supports(id))throw new ProtocolException("Unknown placement");
        int expected=switch(id){case 1,89,91,92->11;case 87,88->13;case 90,97->12;case 104->10;default->throw new ProtocolException("Unknown placement");};
        if(r.remaining()!=expected)throw new ProtocolException("Placement length");
        int loop=r.i32(),claimed=player(id)?r.u8():-1,x=r.u16(),y=r.u16(),z=r.u16(),face=face(id)?r.u8():4;
        double yaw=yaw(id)?Fixed.decodePosition(r.u16()):0;int item=id==97?r.u8():-1,placing=id==97?r.u8():-1;
        return new LivePlacement(id,loop,claimed,x,y,z,face,yaw,item,placing);
    }
    @Override public void write(Wire.Writer w){w.u8(id).i32(loop);if(player(id))w.u8(claimedPlayer);w.i16(x).i16(y).i16(z);if(face(id))w.u8(face);if(yaw(id))w.i16(Fixed.encodePosition(yaw));if(id==97)w.u8(item).u8(placing);}
}
