// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Original optional loop/position fields and sign-magnitude fixed stores for PlaySound. */
public final class AudioPackets {
    private AudioPackets() {}
    public record PlaySound(int soundId,int loopingBits,int positionedBits,float volume,float time,int loopId,Vector3f position,float attenuation) implements GamePacket {
        public PlaySound(int soundId,boolean looping,boolean positioned,float volume,float time,int loopId,Vector3f position,float attenuation){this(soundId,looping?1:0,positioned?1:0,volume,time,loopId,position,attenuation);}
        // Cython read stores flags & 2 in its bint field without normalizing it.
        // Preserve that observable re-encode quirk; directly authored true stores 1.
        public boolean looping(){return loopingBits!=0;}public boolean positioned(){return positionedBits!=0;}
        @Override public int id(){return 23;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(soundId).u8(loopingBits|(positionedBits<<1)).i16(Fixed.encodePosition(volume)).i16(Fixed.encodePosition(time));if(looping())w.u8(loopId);if(positioned()){position.writeFixed(w);w.i16(Fixed.encodePosition(attenuation));}}
        static PlaySound read(Wire.Reader r){int id=r.u8(),flags=r.u8();float volume=(float)Fixed.decodePosition(r.i16()),time=(float)Fixed.decodePosition(r.i16());boolean looping=(flags&1)!=0,positioned=(flags&2)!=0;int loop=looping?r.u8():0;var position=positioned?Vector3f.readFixed(r):new Vector3f(0,0,0);float attenuation=positioned?(float)Fixed.decodePosition(r.i16()):0;return new PlaySound(id,flags&1,flags&2,volume,time,loop,position,attenuation);}
        public static PlaySound personal(int soundId){return new PlaySound(soundId,false,false,1,0,0,new Vector3f(0,0,0),0);}
    }
}
