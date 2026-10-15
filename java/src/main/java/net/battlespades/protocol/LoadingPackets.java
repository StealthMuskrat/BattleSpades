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
    public record SteamSessionTicket(byte[] data,byte[] trailer) implements GamePacket {
        public SteamSessionTicket(byte[] data) { this(data,new byte[0]); }
        public SteamSessionTicket { data=data.clone();trailer=trailer.clone(); }
        @Override public int id() { return 105; }
        @Override public byte[] data() { return data.clone(); }
        @Override public byte[] trailer() { return trailer.clone(); }
        /** Trailer is never part of the ticket/XOR key. server/flight_profile.py. */
        public int capability() {
            if(data.length>2048 || trailer.length!=5 || trailer[0]!='B' || trailer[1]!='S' || trailer[2]!='C' || trailer[3]!='F') return 0;
            return trailer[4]>=1 && trailer[4]<=4?trailer[4]:0;
        }
        @Override public void write(Wire.Writer w) { w.u8(id()).i32(data.length).bytes(data).bytes(trailer); }
        static SteamSessionTicket read(Wire.Reader r) { byte[] ticket=r.bytes(r.i32());return new SteamSessionTicket(ticket,r.bytes(r.remaining())); }
    }
}
