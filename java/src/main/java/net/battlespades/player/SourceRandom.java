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
    public int below(int limit){
        owned();if(limit<=0)throw new IllegalArgumentException("Empty choice");int count=32-Integer.numberOfLeadingZeros(limit);long value;
        do{value=bits(count);}while(value>=limit);return (int)value;
    }
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
