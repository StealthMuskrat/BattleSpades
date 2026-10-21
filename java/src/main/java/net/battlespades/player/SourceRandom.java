// SPDX-License-Identifier: BSD-3-Clause AND PSF-2.0
// MT19937: Copyright (C) 1997-2002 Makoto Matsumoto and Takuji Nishimura.
// Python-compatible integer seeding/selection adapted from CPython 3.12.
// Full notices: META-INF/licenses/mt19937.txt and python-psf.txt.
package net.battlespades.player;

import java.math.BigInteger;
import java.util.*;

/** Source random stream for seeded spawn and shot replays; not a cryptographic generator. */
public final class SourceRandom {
    private final Thread owner=Thread.currentThread();
    private final int[] state=new int[624];
    private int index=624;
    private Double gaussian;
    public SourceRandom(long seed){this(BigInteger.valueOf(seed));}
    public SourceRandom(BigInteger seed){
        seed=seed.abs();int[] key=new int[Math.max(1,(seed.bitLength()+31)/32)];
        for(int i=0;i<key.length;i++)key[i]=seed.shiftRight(i*32).intValue();
        state[0]=19650218;
        for(int i=1;i<624;i++)state[i]=1812433253*(state[i-1]^(state[i-1]>>>30))+i;
        int i=1,j=0;
        for(int k=Math.max(624,key.length);k>0;k--){
            state[i]=(state[i]^((state[i-1]^(state[i-1]>>>30))*1664525))+key[j]+j;
            if(++i>=624){state[0]=state[623];i=1;}if(++j>=key.length)j=0;
        }
        for(int k=623;k>0;k--){
            state[i]=(state[i]^((state[i-1]^(state[i-1]>>>30))*1566083941))-i;
            if(++i>=624){state[0]=state[623];i=1;}
        }
        state[0]=0x80000000;
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Random stream accessed outside owner");}
    private int word(){
        owned();if(index>=624){
            for(int i=0;i<624;i++){
                int y=(state[i]&0x80000000)|(state[(i+1)%624]&0x7fffffff);
                state[i]=state[(i+397)%624]^(y>>>1)^((y&1)==0?0:0x9908b0df);
            }
            index=0;
        }
        int y=state[index++];y^=y>>>11;y^=(y<<7)&0x9d2c5680;y^=(y<<15)&0xefc60000;y^=y>>>18;return y;
    }
    public long bits(int count){
        owned();if(count<0||count>32)throw new IllegalArgumentException("Bit count outside 0..32");
        return count==0?0:Integer.toUnsignedLong(word())>>>(32-count);
    }
    public double random(){long a=Integer.toUnsignedLong(word())>>>5,b=Integer.toUnsignedLong(word())>>>6;return (a*67108864.0+b)*(1.0/9007199254740992.0);}
    public double uniform(double a,double b){return a+(b-a)*random();}
    /** CPython Random.gauss shares its cached partner with getstate(). */
    public double gauss(double mean,double sigma){owned();double z;if(gaussian!=null){z=gaussian;gaussian=null;}else{double angle=random()*Math.PI*2,scale=Math.sqrt(-2*Math.log(1-random()));z=Math.cos(angle)*scale;gaussian=Math.sin(angle)*scale;}return mean+z*sigma;}
    /** 64-bit CPython 3.12 hash(getstate()), used by the source's isolated loadout second opinion. */
    public long stateHash(){owned();long inner=2870177450012600261L;for(int word:state)inner=hashLane(inner,Integer.toUnsignedLong(word));inner=hashLane(inner,index);inner=hashEnd(inner,625);long outer=hashLane(2870177450012600261L,3);outer=hashLane(outer,inner);outer=hashLane(outer,gaussian==null?0xfca86420L:doubleHash(gaussian));return hashEnd(outer,3);}
    private static long hashLane(long acc,long lane){return Long.rotateLeft(acc+lane*0xc2b2ae3d27d4eb4fL,31)*0x9e3779b185ebca87L;}
    private static long hashEnd(long acc,int length){acc+=length^(2870177450012600261L^3527539L);return acc==-1?1546275796:acc;}
    private static long doubleHash(double value){if(Double.isInfinite(value))return value>0?314159:-314159;if(value==0)return 0;long bits=Double.doubleToRawLongBits(value),mantissa=bits&0xfffffffffffffL;int exponent=(int)(bits>>>52&2047);if(exponent!=0)mantissa|=1L<<52;int shift=Math.floorMod((exponent==0?-1022:exponent-1023)-52,61);long modulus=(1L<<61)-1,result=shift==0?mantissa:((mantissa<<shift)&modulus)|(mantissa>>>(61-shift));if(bits<0)result=-result;return result==-1?-2:result;}
    public int below(int limit){
        owned();if(limit<=0)throw new IllegalArgumentException("Empty choice");int count=32-Integer.numberOfLeadingZeros(limit);long value;
        do{value=bits(count);}while(value>=limit);return (int)value;
    }
    public int below31(){owned();long value;do{value=bits(32);}while(value>=0x80000000L);return (int)value;}
    public <T> T choice(List<T> source){return source.get(below(source.size()));}
    public <T> List<T> sample(List<T> source,int count){
        owned();int n=source.size();if(count<0||count>n)throw new IllegalArgumentException("Sample size");
        int setsize=21;if(count>5){long table=1;while(table<(long)count*3)table*=4;setsize=(int)Math.min(Integer.MAX_VALUE,setsize+table);}
        var result=new ArrayList<T>(count);
        if(n<=setsize){var pool=new ArrayList<>(source);for(int i=0;i<count;i++){int j=below(n-i);result.add(pool.get(j));pool.set(j,pool.get(n-i-1));}}
        else{var selected=new HashSet<Integer>();for(int i=0;i<count;i++){int j;do{j=below(n);}while(!selected.add(j));result.add(source.get(j));}}
        return List.copyOf(result);
    }
    public <T> void shuffle(List<T> source){owned();for(int i=source.size()-1;i>0;i--)Collections.swap(source,i,below(i+1));}
    public int weighted(double[] weights){
        owned();if(weights.length==0)throw new IllegalArgumentException("Empty weights");double[] cumulative=new double[weights.length];double total=0;
        for(int i=0;i<weights.length;i++){total+=weights[i];cumulative[i]=total;}
        if(!Double.isFinite(total)||total<=0)throw new IllegalArgumentException("Weight total");
        double value=random()*total;int lo=0,hi=weights.length-1;
        while(lo<hi){int mid=(lo+hi)>>>1;if(value<cumulative[mid])hi=mid;else lo=mid+1;}return lo;
    }
}
