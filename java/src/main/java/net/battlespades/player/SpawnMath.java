// SPDX-License-Identifier: PSF-2.0
// Compensated two-coordinate norm adapted from CPython 3.12 mathmodule.c.
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
    public static double hypot(double a,double b){
        a=Math.abs(a);b=Math.abs(b);
        if(Double.isInfinite(a)||Double.isInfinite(b))return Double.POSITIVE_INFINITY;
        if(Double.isNaN(a)||Double.isNaN(b))return Double.NaN;
        double max=Math.max(a,b);if(max==0)return max;
        int exponent=max<Double.MIN_NORMAL?Math.getExponent(Math.scalb(max,54))-53:Math.getExponent(max)+1;
        if(exponent<-1023)return Double.MIN_NORMAL*hypot(a/Double.MIN_NORMAL,b/Double.MIN_NORMAL);
        double scale=Math.scalb(1.0,-exponent),sum=1,frac1=0,frac2=0;
        for(double value:new double[]{a,b}){
            double x=value*scale,hi=x*x,lo=Math.fma(x,x,-hi),next=sum+hi,error=(sum-next)+hi;
            sum=next;frac1+=lo;frac2+=error;
        }
        double h=Math.sqrt(sum-1.0+(frac1+frac2)),hi=-h*h,lo=Math.fma(-h,h,-hi),next=sum+hi,error=(sum-next)+hi;
        sum=next;frac1+=lo;frac2+=error;double correction=sum-1.0+(frac1+frac2);
        h+=correction/(2.0*h);return h/scale;
    }
}
