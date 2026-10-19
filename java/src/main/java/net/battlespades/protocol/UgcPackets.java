// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.protocol;

import java.util.*;

/** Variable editor packets: raw voxel shorts, native u32 counts and bounded opaque previews. */
public final class UgcPackets {
    private UgcPackets(){}
    public record Item(int mode,int x,int y,int z,int item){}
    public record Batch(List<Item> items)implements GamePacket{
        public Batch{items=List.copyOf(items);if(items.size()>8192)throw new IllegalArgumentException("UGC batch too large");}
        @Override public int id(){return 98;}
        @Override public void write(Wire.Writer w){w.u8(id()).i32(items.size());for(var p:items)w.u8(p.mode).i16(p.x).i16(p.y).i16(p.z).u8(p.item);}
        static Batch read(Wire.Reader r){int n=r.i32();if(n<0||n>8192||(long)n*8>r.remaining())throw new ProtocolException("Invalid UGC batch count");var rows=new ArrayList<Item>(n);for(int i=0;i<n;i++)rows.add(new Item(r.u8(),r.i16(),r.i16(),r.i16(),r.u8()));return new Batch(rows);}
    }
    public record Objective(String id,int value){public Objective{Objects.requireNonNull(id);if(id.length()>256||id.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid UGC objective id");}}
    public record Objectives(int mode,List<Objective> rows)implements GamePacket{
        public Objectives{rows=List.copyOf(rows);if(rows.size()>64)throw new IllegalArgumentException("Too many UGC objectives");}
        @Override public int id(){return 68;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(mode).i32(rows.size());for(var row:rows)w.string(row.id).i32(row.value);}
        static Objectives read(Wire.Reader r){int mode=r.u8(),n=r.i32();if(n<0||n>64||(long)n*5>r.remaining())throw new ProtocolException("Invalid UGC objective count");var rows=new ArrayList<Objective>(n);for(int i=0;i<n;i++)rows.add(new Objective(r.string(),r.i32()));return new Objectives(mode,rows);}
    }
    public record Preview(byte[] data)implements GamePacket{
        public Preview{if(data.length>8*1024*1024)throw new IllegalArgumentException("UGC preview too large");data=data.clone();}
        @Override public byte[] data(){return data.clone();}
        @Override public int id(){return 102;}
        @Override public void write(Wire.Writer w){w.u8(id()).i32(data.length).bytes(data);}
        static Preview read(Wire.Reader r){int n=r.i32();if(n<0||n>8*1024*1024||n>r.remaining())throw new ProtocolException("Invalid UGC preview length");return new Preview(r.bytes(n));}
    }
    public record Color(int r,int g,int b,int a){public Color{if(r<0||r>255||g<0||g>255||b<0||b>255||a<0||a>255)throw new IllegalArgumentException("Invalid RGBA");}}
    public record GroundColors(List<Color> colors)implements GamePacket{
        public GroundColors{colors=List.copyOf(colors);if(colors.size()>255)throw new IllegalArgumentException("Too many RGBA colors");}
        @Override public int id(){return 118;}
        @Override public void write(Wire.Writer w){w.u8(id()).u8(colors.size());for(var c:colors)w.u8(c.r).u8(c.g).u8(c.b).u8(c.a);}
        static GroundColors read(Wire.Reader r){int n=r.u8();if(n*4>r.remaining())throw new ProtocolException("Truncated RGBA palette");var rows=new ArrayList<Color>(n);for(int i=0;i<n;i++)rows.add(new Color(r.u8(),r.u8(),r.u8(),r.u8()));return new GroundColors(rows);}
    }
}
