// SPDX-License-Identifier: PSF-2.0
// Compensated coordinate norm adapted from CPython 3.12 mathmodule.c.
package net.battlespades.player;

/** Retains source hypot rounding where scores and inclusive radius gates depend on it. */
public final class SpawnMath {
    private SpawnMath(){}
    /** Python 3.12 sum(float generator), including its compensated accumulation. */
    public static double sumSquares(double... values){
        double sum=0,correction=0;
        for(double value:values){double x=value*value,next=sum+x;correction+=Math.abs(sum)>=Math.abs(x)?(sum-next)+x:(x-next)+sum;sum=next;}
        return correction!=0&&Double.isFinite(correction)?sum+correction:sum;
    }
    /** Python 3.12 compensated sum, also used for source three-component dot products. */
    public static double sum(double... values){
        double sum=0,correction=0;
        for(double x:values){double next=sum+x;correction+=Math.abs(sum)>=Math.abs(x)?(sum-next)+x:(x-next)+sum;sum=next;}
        return correction!=0&&Double.isFinite(correction)?sum+correction:sum;
    }
    public static double hypot(double a,double b){return hypot(new double[]{a,b});}
    public static double hypot(double... values){
        double max=0;boolean nan=false;for(double value:values){if(Double.isInfinite(value))return Double.POSITIVE_INFINITY;if(Double.isNaN(value))nan=true;else max=Math.max(max,Math.abs(value));}if(nan)return Double.NaN;if(max==0)return max;
        int exponent=max<Double.MIN_NORMAL?Math.getExponent(Math.scalb(max,54))-53:Math.getExponent(max)+1;
        if(exponent<-1023){double[] scaled=values.clone();for(int i=0;i<scaled.length;i++)scaled[i]/=Double.MIN_NORMAL;return Double.MIN_NORMAL*hypot(scaled);}
        double scale=Math.scalb(1.0,-exponent),sum=1,frac1=0,frac2=0;
        for(double value:values){
            double x=value*scale,hi=x*x,lo=Math.fma(x,x,-hi),next=sum+hi,error=(sum-next)+hi;
            sum=next;frac1+=lo;frac2+=error;
        }
        double h=Math.sqrt(sum-1.0+(frac1+frac2)),hi=-h*h,lo=Math.fma(-h,h,-hi),next=sum+hi,error=(sum-next)+hi;
        sum=next;frac1+=lo;frac2+=error;double correction=sum-1.0+(frac1+frac2);
        h+=correction/(2.0*h);return h/scale;
    }
}
