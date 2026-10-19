// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Packet 43: raw voxel bounds in x/y/z start, x/y/z end order. */
public record MinimapZone(int visibleTeam,int color,int x0,int x1,int y0,int y1,int z0,int z1,double scale,int icon,int locked) implements GamePacket {
    @Override public int id(){return 43;}
    @Override public void write(Wire.Writer w){w.u8(id()).u8(visibleTeam).color(color).i16(x0).i16(y0).i16(z0).i16(x1).i16(y1).i16(z1).i16(Fixed.encodePosition(scale)).u8(icon).u8(locked);}
    static MinimapZone read(Wire.Reader r){int team=r.u8(),color=r.color(),x0=r.i16(),y0=r.i16(),z0=r.i16(),x1=r.i16(),y1=r.i16(),z1=r.i16();return new MinimapZone(team,color,x0,x1,y0,y1,z0,z1,Fixed.decodePosition(r.i16()),r.u8(),r.u8());}
}
