// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import java.util.zip.CRC32;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.protocol.Wire;

/** Source-compatible VXL loading and column serialization. Gameplay mutations/collapse are separate work. */
public final class VxlMap {
    public static final int SIZE=512, HEIGHT=240, AREA=SIZE*SIZE;
    private final BitSet solid=new BitSet(AREA*HEIGHT);
    private final IntColors colors=new IntColors();
    private final int[] fill=new int[AREA];
    private final byte[] source;
    private final int sourceSize, zShift;
    private final boolean classic;
    private record Scan(int columns,int maxZ,boolean classicHeight) {}
    private VxlMap(byte[] bytes,Scan scan,String format) {
        source=bytes.clone();sourceSize=(int)Math.sqrt(scan.columns);
        if(sourceSize*sourceSize!=scan.columns || sourceSize>SIZE) throw new ProtocolException("Non-square VXL");
        classic=format.equals("classic64") || (format.equals("auto") && sourceSize==SIZE && scan.maxZ<=64 && scan.classicHeight);
        if(classic && (sourceSize!=SIZE || scan.maxZ>64 || !scan.classicHeight)) throw new ProtocolException("Invalid classic64 VXL");
        zShift=classic?176:Math.max(0,239-scan.maxZ);
        parse();
        solid.set(239*AREA,240*AREA);
    }
    public static VxlMap load(byte[] data,String format) {
        if(!format.equals("auto") && !format.equals("retail") && !format.equals("classic64")) throw new IllegalArgumentException("Unknown VXL source format");
        return new VxlMap(data,scan(data),format);
    }
    private static int u8(byte[] data,int offset) {
        if(offset<0 || offset>=data.length) throw new ProtocolException("Truncated VXL");
        return data[offset]&255;
    }
    private static int topLength(int start,int end) { return Math.max(0,end-start+1); }
    private static Scan scan(byte[] data) {
        if(data.length==0) throw new ProtocolException("Empty VXL");
        int pos=0,columns=0,maxZ=0;boolean classicHeight=true;
        while(pos<data.length) {
            int words=u8(data,pos),start=u8(data,pos+1),end=u8(data,pos+2),air=u8(data,pos+3);
            while(true) {
                maxZ=Math.max(maxZ,Math.max(start,Math.max(end,air)));classicHeight &= end<64;
                if(start>240 || end>=240 || air>240 || start>end+1) throw new ProtocolException("Invalid VXL span coordinates");
                int top=topLength(start,end);
                if(words==0) {
                    pos += 4+4*top;
                    if(pos>data.length) throw new ProtocolException("Truncated VXL colors");
                    break;
                }
                int bottom=words-top-1,next=pos+4*words;
                if(bottom<0 || next>data.length-4) throw new ProtocolException("Invalid VXL span length");
                int nextStart=u8(data,next+1),nextAir=u8(data,next+3);
                if(nextAir-bottom<end+1 || nextStart<nextAir || nextStart<=start) throw new ProtocolException("Overlapping VXL spans");
                pos=next;words=u8(data,pos);start=u8(data,pos+1);end=u8(data,pos+2);air=u8(data,pos+3);
            }
            if(++columns>AREA) throw new ProtocolException("Too many VXL columns");
        }
        return new Scan(columns,maxZ,classicHeight);
    }
    private void parse() {
        int pos=0,offset=(SIZE-sourceSize)/2;
        for(int sy=0;sy<sourceSize;sy++) for(int sx=0;sx<sourceSize;sx++) {
            int x=sx+offset,y=sy+offset,col=x+y*SIZE,surfaceColor=0;boolean hasSurface=false;
            while(true) {
                int words=u8(source,pos),start=u8(source,pos+1),end=u8(source,pos+2);pos+=4;
                int top=topLength(start,end);
                for(int i=0;i<top;i++) {surfaceColor=colorWord(pos+i*4);explicit(x,y,start+zShift+i,surfaceColor);}
                pos+=top*4;hasSurface|=top>0;
                if(words==0) {
                    if(hasSurface) {fill[col]=surfaceColor;for(int z=end+1+zShift;z<HEIGHT;z++) solid.set(index(x,y,z));}
                    break;
                }
                int bottom=words-top-1;
                int nextAir=u8(source,pos+bottom*4+3),bottomStart=nextAir-bottom;
                fill[col]=surfaceColor;
                for(int z=end+1+zShift;z<bottomStart+zShift;z++) if(z>=0 && z<HEIGHT) solid.set(index(x,y,z));
                for(int i=0;i<bottom;i++) explicit(x,y,bottomStart+zShift+i,colorWord(pos+i*4));
                pos+=bottom*4;
            }
        }
    }
    private int colorWord(int pos) { return u8(source,pos)|u8(source,pos+1)<<8|u8(source,pos+2)<<16|u8(source,pos+3)<<24; }
    private static int index(int x,int y,int z) { return x+(y<<9)+(z<<18); }
    private static boolean bounds(int x,int y,int z) { return x>=0 && x<SIZE && y>=0 && y<SIZE && z>=0 && z<HEIGHT; }
    private void explicit(int x,int y,int z,int color) {
        if(bounds(x,y,z)) { int key=index(x,y,z);solid.set(key);colors.put(key,color); }
    }
    public boolean solid(int x,int y,int z) { return bounds(x,y,z) && solid.get(index(x,y,z)); }
    /** Full BGRA bit pattern as in the native color table, including alpha. */
    public int color(int x,int y,int z) {
        if(!bounds(x,y,z)) return 0;
        return colors.get(index(x,y,z),solid(x,y,z)?fill[x+y*SIZE]:0);
    }
    public int sourceSize() { return sourceSize; }
    public int zShift() { return zShift; }
    public boolean classic() { return classic; }
    public byte[] originalBytes() { return source.clone(); }
    public long sourceCrc() { var crc=new CRC32();crc.update(source);return crc.getValue(); }
    public byte[] serializeColumn(int x,int y) {
        if(x<0 || x>=SIZE || y<0 || y>=SIZE) throw new IllegalArgumentException("Column outside map");
        var out=new Wire.Writer();int previousAir=0,z=0;
        while(z<HEIGHT) {
            while(z<HEIGHT && !solid(x,y,z)) z++;
            if(z==HEIGHT) break;
            int start=z;while(z+1<HEIGHT && solid(x,y,z+1)) z++;
            int end=z;
            boolean last=end==239; // source always force-fills the bed
            int topEnd=start,bottomStart=end;
            if(last) {
                for(int v=end;v>start;v--) if(colors.contains(index(x,y,v))) {topEnd=v;break;}
                out.u8(0).u8(start).u8(topEnd).u8(previousAir);
                for(int v=start;v<=topEnd;v++) out.i32(color(x,y,v));
            } else {
                while(bottomStart>start+1 && colors.contains(index(x,y,bottomStart-1))) bottomStart--;
                for(int v=bottomStart-1;v>start;v--) if(colors.contains(index(x,y,v))) {topEnd=v;break;}
                int bottomLength=bottomStart>topEnd?end-bottomStart+1:0;
                out.u8(1+topEnd-start+1+bottomLength).u8(start).u8(topEnd).u8(previousAir);
                for(int v=start;v<=topEnd;v++) out.i32(color(x,y,v));
                if(bottomLength>0) for(int v=bottomStart;v<=end;v++) out.i32(color(x,y,v));
            }
            previousAir=end+1;z++;
        }
        return out.toByteArray();
    }
}
