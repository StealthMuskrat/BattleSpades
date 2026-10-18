// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

/** Advisory bot-navigation area reader; inflates only the first three bounded planes. Call off the game loop. */
public final class NavigationAreas {
    private NavigationAreas(){}
    private record Cached(FileTime modified,long size,Integer area){}
    private static final ConcurrentHashMap<Path,Cached> CACHE=new ConcurrentHashMap<>();
    public static Integer read(Path path){
        BasicFileAttributes stat;try{stat=Files.readAttributes(path,BasicFileAttributes.class);}catch(IOException error){return null;}
        var key=path.toAbsolutePath().normalize();var old=CACHE.get(key);if(old!=null&&old.size()==stat.size()&&old.modified().equals(stat.lastModifiedTime()))return old.area();
        Integer area=null;var inflater=new Inflater();
        try(var input=Files.newInputStream(path)){
            byte[] header=input.readNBytes(40);if(header.length!=40)throw new IOException("Short navigation header");var h=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic=new byte[8];h.get(magic);h.getShort();int width=Short.toUnsignedInt(h.getShort()),height=Short.toUnsignedInt(h.getShort());long columns=(long)width*height;
            if(!java.util.Arrays.equals(magic,new byte[]{'B','S','N','A','V','0','1',0})||columns<=0||columns>262144)throw new IOException("Navigation dimensions/magic");
            int target=3*(int)columns,offset=0;byte[] planes=new byte[target],compressed=new byte[8192];long read=0;
            while(offset<target){
                if(inflater.needsInput()){int n=input.read(compressed);if(n<0)break;read+=n;if(read>64*1024*1024L)throw new IOException("Navigation loading bound");inflater.setInput(compressed,0,n);}
                int n=inflater.inflate(planes,offset,target-offset);offset+=n;if(n==0&&(inflater.finished()||inflater.needsDictionary()||!inflater.needsInput()))break;
            }
            if(offset==target){int count=0;for(int i=2*(int)columns;i<target;i++)if((planes[i]&2)!=0)count++;area=count;}
        }catch(IOException|DataFormatException|IllegalArgumentException invalid){area=null;}finally{inflater.end();}
        CACHE.put(key,new Cached(stat.lastModifiedTime(),stat.size(),area));return area;
    }
}
