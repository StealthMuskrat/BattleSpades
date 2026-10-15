// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.ArrayList;
import java.util.List;
import net.battlespades.protocol.ProtocolException;
import net.battlespades.protocol.Wire;

/** Standard and legacy KV6 record layouts from aoslib/kv6.pyx, preserving palette tails. */
public record Kv6(int xSize,int ySize,int zSize,float pivotX,float pivotY,float pivotZ,
        List<Voxel> voxels,List<Long> xLengths,List<Integer> columnLengths,byte[] paletteTail,boolean legacy) {
    public record Voxel(int x,int y,int z,int r,int g,int b,int alpha,int visibleFaces,int direction) {}
    public Kv6 {
        voxels=List.copyOf(voxels);xLengths=List.copyOf(xLengths);columnLengths=List.copyOf(columnLengths);paletteTail=paletteTail.clone();
    }
    @Override public byte[] paletteTail() { return paletteTail.clone(); }
    public static Kv6 decode(byte[] data) {
        if(data.length<4 || data[0]!='K' || data[1]!='v' || data[2]!='x' || data[3]!='l') throw new ProtocolException("Invalid KV6 magic");
        try { return parse(data,false); }
        catch(ProtocolException ex) {
            try { return parse(data,true); }
            catch(ProtocolException legacyFailure) { legacyFailure.addSuppressed(ex);throw legacyFailure; }
        }
    }
    private static Kv6 parse(byte[] data,boolean legacy) {
        var r=new Wire.Reader(data);r.bytes(4);
        long x=r.u32(),y=r.u32(),z=legacy?0:r.u32();
        float px=r.f32(),py=r.f32(),pz=r.f32();long n=r.u32();
        long columns=x*y;
        if(x>Integer.MAX_VALUE || y>Integer.MAX_VALUE || z>Integer.MAX_VALUE || columns>Integer.MAX_VALUE ||
           n>Integer.MAX_VALUE || n*8+x*4+columns*2>r.remaining()) throw new ProtocolException("Invalid/truncated KV6 dimensions");
        int count=(int)n;int[][] records=new int[count][7];
        for(int i=0;i<count;i++) { int b=r.u8(),g=r.u8(),red=r.u8(),alpha=r.u8(),height=r.u16(),faces=r.u8(),direction=r.u8();
            records[i]=new int[]{red,g,b,alpha,height,faces,direction}; }
        var xl=new ArrayList<Long>();for(int i=0;i<x;i++) xl.add(r.u32());
        var yl=new ArrayList<Integer>();for(int i=0;i<columns;i++) yl.add(r.u16());
        byte[] tail=r.bytes(r.remaining());
        List<Voxel> voxels=new ArrayList<>();int index=0,inferredZ=0;
        for(int ix=0;ix<x;ix++) for(int iy=0;iy<y;iy++) for(int j=0;j<yl.get(ix*(int)y+iy);j++) {
            if(index>=count) throw new ProtocolException("KV6 columns exceed voxel count");
            int[] v=records[index++];inferredZ=Math.max(inferredZ,v[4]+1);
            voxels.add(new Voxel(ix,iy,v[4],v[0],v[1],v[2],v[3],v[5],v[6]));
        }
        if(index!=count) throw new ProtocolException("KV6 columns do not match voxel count");
        return new Kv6((int)x,(int)y,legacy?inferredZ:(int)z,px,py,pz,voxels,xl,yl,legacy?new byte[0]:tail,legacy);
    }
    public byte[] encode() {
        var w=new Wire.Writer().bytes(new byte[]{'K','v','x','l'}).u32(xSize).u32(ySize);
        if(!legacy) w.u32(zSize);
        w.f32(pivotX).f32(pivotY).f32(pivotZ).u32(voxels.size());
        for(Voxel v:voxels) w.u8(v.b).u8(v.g).u8(v.r).u8(v.alpha).i16(v.z).u8(v.visibleFaces).u8(v.direction);
        for(long n:xLengths) w.u32(n);
        for(int n:columnLengths) w.i16(n);
        if(!legacy) w.bytes(paletteTail);
        return w.toByteArray();
    }
}
