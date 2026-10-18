// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Native objective drop: binary64 tuple inputs go directly to the fixed-point writer. */
public record DropPickup(int loop,int playerId,int pickupId,Position position,Position velocity) implements GamePacket {
    public record Position(double x,double y,double z) {
        private void write(Wire.Writer writer){writer.i16(Fixed.encodePosition(x)).i16(Fixed.encodePosition(y)).i16(Fixed.encodePosition(z));}
        private static Position read(Wire.Reader reader){return new Position(Fixed.decodePosition(reader.i16()),Fixed.decodePosition(reader.i16()),Fixed.decodePosition(reader.i16()));}
    }
    public DropPickup {java.util.Objects.requireNonNull(position);java.util.Objects.requireNonNull(velocity);}
    @Override public int id(){return 71;}
    @Override public void write(Wire.Writer writer){writer.u8(id()).i32(loop).u8(playerId).u8(pickupId);position.write(writer);velocity.write(writer);}
    static DropPickup read(Wire.Reader reader){return new DropPickup(reader.i32(),reader.u8(),reader.u8(),Position.read(reader),Position.read(reader));}
}
