// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Reference Unicode 15 normalization/properties, independent of JDK Unicode upgrades. */
public final class NameUnicode {
    private static final int[] STARTS, PROPERTIES;
    private static final Map<Integer,String> DECOMPOSITION, CASEFOLD;
    private static final Map<Long,Integer> COMPOSITION;
    static final Map<Integer,String> CONFUSABLES;
    static final Set<String> RESERVED_NAMES, RESERVED_TAGS;
    static {
        try(var raw=Objects.requireNonNull(NameUnicode.class.getResourceAsStream("name-unicode.bin.gz"));
            var in=new DataInputStream(new GZIPInputStream(raw))){
            if(in.readInt()!=0x4e414d45||!readString(in).equals("15.0.0"))throw new IOException("Invalid name Unicode database");
            int count=count(in);STARTS=new int[count];PROPERTIES=new int[count];
            for(int i=0;i<count;i++){STARTS[i]=in.readInt();PROPERTIES[i]=in.readInt();}
            DECOMPOSITION=mapping(in);CASEFOLD=mapping(in);
            var pairs=new HashMap<Long,Integer>();count=count(in);
            for(int i=0;i<count;i++)pairs.put(key(in.readInt(),in.readInt()),in.readInt());COMPOSITION=Map.copyOf(pairs);
            CONFUSABLES=mapping(in);RESERVED_NAMES=strings(in);RESERVED_TAGS=strings(in);
            if(in.read()!=-1)throw new IOException("Trailing name Unicode data");
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    private NameUnicode(){}
    private static int count(DataInputStream in)throws IOException{
        int n=in.readInt();if(n<0||n>0x110000)throw new IOException("Invalid name table count");return n;
    }
    private static String readString(DataInputStream in)throws IOException{
        int n=count(in);byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();return new String(bytes,StandardCharsets.UTF_8);
    }
    private static Map<Integer,String> mapping(DataInputStream in)throws IOException{
        var result=new HashMap<Integer,String>();int n=count(in);
        for(int i=0;i<n;i++)result.put(in.readInt(),readString(in));return Map.copyOf(result);
    }
    private static Set<String> strings(DataInputStream in)throws IOException{
        var result=new HashSet<String>();int n=count(in);for(int i=0;i<n;i++)result.add(readString(in));return Set.copyOf(result);
    }
    static int properties(int cp){int i=Arrays.binarySearch(STARTS,cp);return PROPERTIES[i<0?-i-2:i];}
    static boolean invisible(int cp){return (properties(cp)&1)!=0;}
    static boolean mark(int cp){return (properties(cp)&2)!=0;}
    static boolean space(int cp){return (properties(cp)&4)!=0;}
    static boolean alnum(int cp){return (properties(cp)&8)!=0;}
    private static int combining(int cp){return properties(cp)>>>8;}
    public static String casefold(String text){
        var out=new StringBuilder();text.codePoints().forEach(cp->out.append(CASEFOLD.getOrDefault(cp,new String(Character.toChars(cp)))));return out.toString();
    }
    /** Python str.strip using the pinned reference Unicode whitespace property. */
    public static String strip(String text){
        int start=0,end=text.length();while(start<end&&space(text.codePointAt(start)))start+=Character.charCount(text.codePointAt(start));
        while(end>start&&space(text.codePointBefore(end)))end-=Character.charCount(text.codePointBefore(end));return text.substring(start,end);
    }
    /** Python str.lstrip with the same pinned whitespace table as strip. */
    public static String stripLeading(String text){int start=0;while(start<text.length()&&space(text.codePointAt(start)))start+=Character.charCount(text.codePointAt(start));return text.substring(start);}
    static String nfkd(String text){return string(decompose(text));}
    static String nfkc(String text){
        int[] source=decompose(text);if(source.length==0)return "";
        int[] result=new int[source.length];int size=1,starter=0,last=0;result[0]=source[0];
        for(int i=1;i<source.length;i++){
            int cp=source[i],cc=combining(cp),composite=compose(result[starter],cp);
            if(composite>=0&&(last<cc||last==0))result[starter]=composite;
            else{if(cc==0)starter=size;result[size++]=cp;last=cc;}
        }
        return new String(result,0,size);
    }
    private static int[] decompose(String text){
        var expanded=new StringBuilder();
        text.codePoints().forEach(cp->{
            if(cp>=0xac00&&cp<=0xd7a3){
                int s=cp-0xac00;expanded.appendCodePoint(0x1100+s/588).appendCodePoint(0x1161+(s%588)/28);
                if(s%28!=0)expanded.appendCodePoint(0x11a7+s%28);
            }else expanded.append(DECOMPOSITION.getOrDefault(cp,new String(Character.toChars(cp))));
        });
        int[] points=expanded.toString().codePoints().toArray();
        for(int i=1;i<points.length;i++){
            int cp=points[i],cc=combining(cp);if(cc==0)continue;int j=i;
            while(j>0&&combining(points[j-1])>cc){points[j]=points[j-1];j--;}
            points[j]=cp;
        }
        return points;
    }
    private static int compose(int a,int b){
        if(a>=0x1100&&a<0x1113&&b>=0x1161&&b<0x1176)return 0xac00+(a-0x1100)*588+(b-0x1161)*28;
        if(a>=0xac00&&a<=0xd7a3&&(a-0xac00)%28==0&&b>0x11a7&&b<0x11c3)return a+b-0x11a7;
        return COMPOSITION.getOrDefault(key(a,b),-1);
    }
    private static long key(int a,int b){return ((long)a<<21)|b;}
    private static String string(int[] cps){return new String(cps,0,cps.length);}
}
