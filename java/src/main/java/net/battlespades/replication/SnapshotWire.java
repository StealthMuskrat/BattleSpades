// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.replication;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** Recipient transforms from server/replication.py; player rows remain opaque immutable bytes. */
public final class SnapshotWire {
    private SnapshotWire() {}
    private static final byte[] EMPTY=new byte[4];
    public record Parsed(byte[] prefix,Map<Integer,byte[]> rows,byte[] tail) {}
    private static int u16(byte[] data,int at){return (data[at]&255)|((data[at+1]&255)<<8);}
    private static void count(ByteArrayOutputStream out,int n){out.write(n);out.write(n>>>8);}
    public static Parsed parse(byte[] data){
        if(data.length<7||data[0]!=2)return null;
        int count=u16(data,5),end=7+count*56;
        if(end+4>data.length)return null;
        var rows=new LinkedHashMap<Integer,byte[]>();
        for(int at=7;at<end;at+=56)if(rows.put(data[at]&255,Arrays.copyOfRange(data,at,at+56))!=null)return null;
        return new Parsed(Arrays.copyOf(data,5),Collections.unmodifiableMap(rows),Arrays.copyOfRange(data,end,data.length));
    }
    public static byte[] assemble(byte[] prefix,List<byte[]> rows,byte[] tail){
        var out=new ByteArrayOutputStream();out.writeBytes(prefix);count(out,rows.size());
        rows.forEach(out::writeBytes);out.writeBytes(tail);return out.toByteArray();
    }
    public static byte[] tailWithout(byte[] tail,Set<Integer> held){
        try{
            int at=2,n=u16(tail,0);var entities=new ArrayList<byte[]>();var turrets=new ArrayList<byte[]>();
            for(int i=0;i<n;i++){
                if(at+33>tail.length)return EMPTY.clone();
                int size=33+4*(tail[at+30]&255)+2*(tail[at+31]&255);
                if(at+size>tail.length)return EMPTY.clone();
                if(!held.contains((int)(short)u16(tail,at)))entities.add(Arrays.copyOfRange(tail,at,at+size));at+=size;
            }
            n=u16(tail,at);at+=2;if(at+6*n!=tail.length)return EMPTY.clone();
            for(int i=0;i<n;i++,at+=6)if(!held.contains((int)(short)u16(tail,at)))turrets.add(Arrays.copyOfRange(tail,at,at+6));
            var out=new ByteArrayOutputStream();count(out,entities.size());entities.forEach(out::writeBytes);
            count(out,turrets.size());turrets.forEach(out::writeBytes);return out.toByteArray();
        }catch(IndexOutOfBoundsException e){return EMPTY.clone();}
    }
    public static List<byte[]> observerParts(byte[] prefix,List<byte[]> rows,byte[] tail,int limit){
        int room=limit-7,beside=Math.max(0,Math.floorDiv(room-tail.length,56)),per=Math.max(1,Math.floorDiv(room-4,56));
        var parts=new ArrayList<byte[]>();parts.add(assemble(prefix,rows.subList(0,Math.min(beside,rows.size())),tail));
        for(int at=beside;at<rows.size();at+=per)parts.add(assemble(prefix,rows.subList(at,Math.min(at+per,rows.size())),EMPTY));
        return List.copyOf(parts);
    }
    public static byte[] ownerPrefix(Integer observerLoop,byte[] prefix){
        if(observerLoop==null)return prefix;
        byte[] result=prefix.clone();int loop=Math.max(0,observerLoop);
        for(int i=0;i<4;i++)result[i+1]=(byte)(loop>>>(8*i));return result;
    }
    public static byte[] ownerPayload(byte[] prefix,byte[] row){
        byte[] owned=row.clone();owned[48]=(byte)255;return assemble(prefix,List.of(owned),EMPTY);
    }
    public static Map<Integer,Integer> toolOffsets(byte[] data){
        if(data.length<7||data[0]!=2)return Map.of();
        var parsed=parse(data);if(parsed==null)throw new IllegalArgumentException("Incomplete or duplicate WorldUpdate player section");
        var offsets=new LinkedHashMap<Integer,Integer>();int at=55;
        for(int id:parsed.rows.keySet()){offsets.put(id,at);at+=56;}return Collections.unmodifiableMap(offsets);
    }
    public static byte[] localOverride(byte[] data,Map<Integer,Integer> offsets,int id){
        Integer offset=offsets.get(id);if(offset==null||(data[offset]&255)==255)return data;
        byte[] result=data.clone();result[offset]=(byte)255;return result;
    }
}
