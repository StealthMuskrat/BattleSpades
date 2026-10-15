// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

/** Variable-size loading records from shared/packet.pyx. */
public final class LoadingPackets {
    private LoadingPackets() {}
    /** The native MapSyncStart has no size field. */
    public record MapSyncStart() implements GamePacket {
        @Override public int id() { return 55; }
        @Override public void write(Wire.Writer w) { w.u8(id()); }
    }
    public record MapChunk(int id,int percent,byte[] data) implements GamePacket {
        public MapChunk {
            if(id!=56 && id!=57) throw new IllegalArgumentException("Map chunk ID");
            if(percent<0 || percent>255 || data.length>32767) throw new IllegalArgumentException("Map chunk bounds");
            data=data.clone();
        }
        @Override public byte[] data() { return data.clone(); }
        @Override public void write(Wire.Writer w) { w.u8(id).u8(percent).i16(data.length).bytes(data); }
        static MapChunk read(int id,Wire.Reader r) { int percent=r.u8(),size=r.i16();return new MapChunk(id,percent,r.bytes(size)); }
    }
    public record SteamSessionTicket(byte[] data) implements GamePacket {
        public SteamSessionTicket { data=data.clone(); }
        @Override public int id() { return 105; }
        @Override public byte[] data() { return data.clone(); }
        @Override public void write(Wire.Writer w) { w.u8(id()).i32(data.length).bytes(data); }
        static SteamSessionTicket read(Wire.Reader r) { return new SteamSessionTicket(r.bytes(r.i32())); }
    }
}
