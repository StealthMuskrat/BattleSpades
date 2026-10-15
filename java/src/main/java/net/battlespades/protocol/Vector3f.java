// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Binary32 wire vector. Arithmetic order for physics is owned by the physics port. */
public record Vector3f(float x,float y,float z) {
    public static Vector3f read(Wire.Reader r) { return new Vector3f(r.f32(),r.f32(),r.f32()); }
    public void write(Wire.Writer w) { w.f32(x).f32(y).f32(z); }
    static Vector3f readFixed(Wire.Reader r) { return new Vector3f((float)Fixed.decodePosition(r.i16()),(float)Fixed.decodePosition(r.i16()),(float)Fixed.decodePosition(r.i16())); }
    void writeFixed(Wire.Writer w) { w.i16(Fixed.encodePosition(x)).i16(Fixed.encodePosition(y)).i16(Fixed.encodePosition(z)); }
}
