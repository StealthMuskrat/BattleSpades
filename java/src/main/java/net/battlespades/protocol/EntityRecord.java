// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.ArrayList;
import java.util.List;

/** Embedded Entity wire record, not prefixed by packet ID 21. */
public record EntityRecord(int entityId,int type,int state,int playerId,Vector3f position,Vector3f velocity,
        float yaw,Vector3f color,float radius,int face,float fuse,int ugcMode,
        List<Integer> intProperties,List<Double> floatProperties) {
    public EntityRecord {
        intProperties=List.copyOf(intProperties);floatProperties=List.copyOf(floatProperties);
        if(intProperties.size()>255 || floatProperties.size()>255) throw new ProtocolException("Too many entity properties");
    }
    public void write(Wire.Writer w) {
        w.i16(entityId).u8(type).u8(state).u8(playerId);position.writeFixed(w);velocity.writeFixed(w);
        w.i16(Fixed.encodePosition(yaw));color.writeFixed(w);w.i16(Fixed.encodePosition(radius)).u8(face)
            .i16(Fixed.encodePosition(fuse)).u8(intProperties.size()).u8(floatProperties.size()).u8(ugcMode);
        for(int value:intProperties) w.i32(value);
        for(double value:floatProperties) w.i16(Fixed.encodePosition(value));
    }
    public static EntityRecord read(Wire.Reader r) {
        int id=r.i16(),type=r.u8(),state=r.u8(),player=r.u8();
        var pos=Vector3f.readFixed(r);var vel=Vector3f.readFixed(r);float yaw=(float)Fixed.decodePosition(r.i16());
        var color=Vector3f.readFixed(r);float radius=(float)Fixed.decodePosition(r.i16());int face=r.u8();float fuse=(float)Fixed.decodePosition(r.i16());
        int ints=r.u8(),floats=r.u8(),ugc=r.u8();
        if((long)ints*4+floats*2>r.remaining()) throw new ProtocolException("Truncated entity properties");
        var intValues=new ArrayList<Integer>();for(int i=0;i<ints;i++) intValues.add(r.i32());
        var floatValues=new ArrayList<Double>();for(int i=0;i<floats;i++) floatValues.add(Fixed.decodePosition(r.i16()));
        return new EntityRecord(id,type,state,player,pos,vel,yaw,color,radius,face,fuse,ugc,intValues,floatValues);
    }
}
