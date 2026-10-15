// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.protocol.Wire;

/** Immutable compression work, captured before yielding the world owner. No mutable world access. */
public final class MapSyncSnapshot {
    public record Column(int x,int y) implements Comparable<Column> {
        public Column { if(x<0 || x>=512 || y<0 || y>=512) throw new IllegalArgumentException("Column outside world"); }
        @Override public int compareTo(Column other) { int c=Integer.compare(x,other.x);return c==0?Integer.compare(y,other.y):c; }
    }
    private final VxlMap map;
    private final Map<Column,byte[]> overlays;
    private final long revision;
    private final boolean full;
    /** Overlays are canonical world-height columns; raw base columns still need the source Z shift. */
    public MapSyncSnapshot(VxlMap map,Map<Column,byte[]> overlays,long revision,boolean full) {
        this.map=java.util.Objects.requireNonNull(map);this.revision=revision;this.full=full;
        if(revision<0 || overlays.size()>VxlMap.AREA) throw new IllegalArgumentException("Snapshot bounds");
        var frozen=new TreeMap<Column,byte[]>();
        overlays.forEach((key,value)->{var bytes=value.clone();validateColumn(bytes);frozen.put(key,bytes);});
        this.overlays=Collections.unmodifiableMap(frozen);
    }
    public long revision() { return revision; }
    public boolean full() { return full; }
    public int dirtyColumnCount() { return overlays.size(); }
    private static void validateColumn(byte[] data) {
        int pos=0,previousEnd=-1;
        while(true) {
            if(pos>data.length-4) throw new ProtocolException("Truncated snapshot column");
            int words=data[pos]&255,start=data[pos+1]&255,end=data[pos+2]&255,air=data[pos+3]&255;
            if(start>240 || end>=240 || air>240 || start>end+1 || start<=previousEnd) throw new ProtocolException("Invalid snapshot span");
            int top=Math.max(0,end-start+1),size=words==0?4+top*4:words*4;
            if(size<4+top*4 || size>data.length-pos) throw new ProtocolException("Invalid snapshot span size");
            if(words==0) {if(pos+size!=data.length) throw new ProtocolException("Trailing snapshot column data");return;}
            int next=pos+size,bottom=words-top-1;
            if(next>data.length-4 || (data[next+3]&255)-bottom<end+1 || (data[next+1]&255)<(data[next+3]&255))
                throw new ProtocolException("Overlapping snapshot spans");
            previousEnd=end;pos=next;
        }
    }
    /** Streams raw records through zlib, preserving compact underground spans and 1024-byte chunks. */
    public List<byte[]> buildChunks() { return buildChunks(256*1024*1024,64*1024*1024); }
    public List<byte[]> buildChunks(int maxUncompressedBytes,int maxCompressedBytes) {
        if(maxUncompressedBytes<1 || maxCompressedBytes<1) throw new IllegalArgumentException("Compression limits");
        if(!full && overlays.isEmpty()) return List.of();
        var output=new ChunkOutput(maxCompressedBytes);var deflater=new Deflater(6);
        long written=0;
        try(var zip=new DeflaterOutputStream(output,deflater,8192)) {
            if(!full) {
                for(var entry:overlays.entrySet()) written=record(zip,entry.getKey(),entry.getValue(),written,maxUncompressedBytes);
            } else if(map.sourceSize()==512) {
                byte[] raw=map.originalBytes();int pos=0;
                for(int y=0;y<512;y++) for(int x=0;x<512;x++) {
                    int start=pos;
                    while(true) {
                        if(pos>raw.length-4) throw new ProtocolException("Truncated base column");
                        int words=raw[pos]&255,top=Math.max(0,(raw[pos+2]&255)-(raw[pos+1]&255)+1);
                        int length=words==0?4+top*4:4*words;
                        if(length>raw.length-pos) throw new ProtocolException("Truncated base span");
                        pos+=length;if(words==0) break;
                    }
                    var key=new Column(x,y);byte[] column=overlays.get(key);
                    if(column==null) column=shift(Arrays.copyOfRange(raw,start,pos),map.zShift());
                    written=record(zip,key,column,written,maxUncompressedBytes);
                }
                if(pos!=raw.length) throw new ProtocolException("Trailing base columns");
            } else {
                // The source falls back to native canonical column packing for centered small maps.
                for(int y=0;y<512;y++) for(int x=0;x<512;x++) {
                    var key=new Column(x,y);byte[] column=overlays.get(key);
                    if(column==null) column=map.serializeColumn(x,y);
                    written=record(zip,key,column,written,maxUncompressedBytes);
                }
            }
        } catch(IOException exception) { throw new IllegalStateException("In-memory map compression failed",exception); }
        finally { deflater.end(); }
        return output.finish();
    }
    private static long record(OutputStream out,Column key,byte[] column,long written,int limit) throws IOException {
        if(Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Map compression cancelled");
        long next=written+8+column.length;if(next>limit) throw new ProtocolException("Map snapshot exceeds uncompressed limit");
        out.write(new Wire.Writer().i32(key.x).i32(key.y).toByteArray());out.write(column);return next;
    }
    private static byte[] shift(byte[] column,int shift) {
        if(shift==0) return column;
        int pos=0;
        while(pos<column.length) {
            int words=column[pos]&255,top=Math.max(0,(column[pos+2]&255)-(column[pos+1]&255)+1);
            for(int index=1;index<=3;index++) {
                int value=column[pos+index]&255;if(index==3 && value==0) continue;
                if(value+shift>255) throw new ProtocolException("Shifted span exceeds byte range");
                column[pos+index]=(byte)(value+shift);
            }
            pos+=words==0?4+top*4:words*4;
        }
        return column;
    }
    private static final class ChunkOutput extends OutputStream {
        private final List<byte[]> chunks=new ArrayList<>();
        private byte[] current=new byte[1024];
        private int size,total;
        private final int limit;
        ChunkOutput(int limit) { this.limit=limit; }
        @Override public void write(int value) { byte[] one={(byte)value};write(one,0,1); }
        @Override public void write(byte[] data,int offset,int length) {
            if(length>limit-total) throw new ProtocolException("Map snapshot exceeds compressed limit");
            total+=length;
            while(length>0) {
                int count=Math.min(length,1024-size);System.arraycopy(data,offset,current,size,count);
                size+=count;offset+=count;length-=count;
                if(size==1024) {chunks.add(current);current=new byte[1024];size=0;}
            }
        }
        List<byte[]> finish() { if(size>0) chunks.add(Arrays.copyOf(current,size));return List.copyOf(chunks); }
    }
}
