// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.util.*;
import net.battlespades.world.MutableWorld;

/** Retail solid/chunk counters; repeated paint notifications never add occupancy. */
public final class UgcCapacity implements AutoCloseable {
    public static final int SOLID_LIMIT=2_800_000,CHUNK_LIMIT=3200,CHUNK_COUNT=15360;
    private final BitSet cells;private final int[] chunks=new int[CHUNK_COUNT];private int solids,occupied;private MutableWorld.RemovalLease subscription;
    private UgcCapacity(BitSet cells){this.cells=(BitSet)cells.clone();for(int bit=cells.nextSetBit(0);bit>=0;bit=cells.nextSetBit(bit+1)){int column=bit/240,z=bit%240,index=chunkIndex(column%512,column/512,z);if(chunks[index]++==0)occupied++;solids++;}}
    public static UgcCapacity empty(){return new UgcCapacity(new BitSet());}
    public int solids(){return solids;}public int chunks(){return occupied;}
    public static int chunkIndex(int x,int y,int z){return (x>>4)+32*((y>>4)+32*(z>>4));}
    private static boolean bounds(int x,int y,int z){return x>=0&&x<512&&y>=0&&y<512&&z>=0&&z<240;}
    public boolean solid(int x,int y,int z){return bounds(x,y,z)&&cells.get((x+y*512)*240+z);}
    public void apply(int x,int y,int z,boolean solid){if(!bounds(x,y,z))return;int bit=(x+y*512)*240+z;if(cells.get(bit)==solid)return;cells.set(bit,solid);int chunk=chunkIndex(x,y,z);if(solid){solids++;if(chunks[chunk]++==0)occupied++;}else{solids--;if(--chunks[chunk]==0)occupied--;}}
    public static boolean hasSpace(int solids,int chunks){return solids<SOLID_LIMIT||chunks<CHUNK_LIMIT;}
    public boolean hasSpace(){return hasSpace(solids,occupied);}
    /** The initial snapshot is the source VXL; the independent lease follows committed cells. */
    public UgcCapacity track(MutableWorld world){if(subscription!=null)throw new IllegalStateException("Capacity already attached");subscription=world.observeTopology((c,s)->apply(c.x(),c.y(),c.z(),s),e->{throw e;});return this;}
    @Override public void close(){if(subscription!=null){subscription.close();subscription=null;}}
    public static UgcCapacity fromVxl(byte[] bytes){
        if(bytes.length==0)throw new IllegalArgumentException("Empty VXL");var raw=new BitSet();int position=0,columns=0,maxRef=0;
        while(position<bytes.length){if(columns>=512*512)throw new IllegalArgumentException("Too many VXL columns");boolean surface=false;while(true){if(position>bytes.length-4)throw new IllegalArgumentException("Truncated VXL header");int words=bytes[position]&255,top=bytes[position+1]&255,end=bytes[position+2]&255,air=bytes[position+3]&255;maxRef=Math.max(maxRef,Math.max(top,Math.max(end,air)));surface|=end>=top;if(words==0){int next=position+(end>=top?4*(end-top+2):4);if(next>bytes.length)throw new IllegalArgumentException("Truncated VXL colors");position=next;if(surface){int start=end>=top?top:end+1;raw.set(columns*256+start,columns*256+256);}break;}int next=position+words*4;if(next>bytes.length-4)throw new IllegalArgumentException("Truncated VXL span");int nextAir=bytes[next+3]&255;if(nextAir>top)raw.set(columns*256+top,columns*256+nextAir);position=next;}columns++;}
        int edge=(int)Math.sqrt(columns);if(edge*edge!=columns||maxRef>=241)throw new IllegalArgumentException("Unsupported VXL dimensions");int offset=(512-edge)/2,shift=Math.max(0,239-maxRef);var normalized=new BitSet();for(int column=0;column<columns;column++){int dest=(column%edge+offset+(column/edge+offset)*512)*240;for(int bit=raw.nextSetBit(column*256);bit>=0&&bit<(column+1)*256;bit=raw.nextSetBit(bit+1)){int z=bit-column*256+shift;if(z<240)normalized.set(dest+z);}}return new UgcCapacity(normalized);
    }
}
