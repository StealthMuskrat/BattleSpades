// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;
/** Native base-health row, with conditional fixed16 percent or signed integer counters. */
public record TeamProgress(int team,int numerator,int denominator,float percent,int icon,boolean visible,boolean particle,boolean previous,boolean asPercent) implements GamePacket {
    public int id(){return 117;}
    public void write(Wire.Writer w){w.u8(id()).u8(team).u8((visible?1:0)|(particle?2:0)|(previous?4:0)|(asPercent?8:0));if(asPercent)w.i16(Fixed.encodePosition(percent));else w.i32(numerator).i32(denominator);w.u8(icon);}
    static TeamProgress read(Wire.Reader r){int team=r.u8(),flags=r.u8(),n=0,d=0;float p=0;if((flags&8)!=0)p=(float)Fixed.decodePosition(r.i16());else{n=r.i32();d=r.i32();}return new TeamProgress(team,n,d,p,r.u8(),(flags&1)!=0,(flags&2)!=0,(flags&4)!=0,(flags&8)!=0);}
}
