// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.List;
import net.battlespades.protocol.Wire;

/** Negotiated server/owner policy from server/flight_profile.py; not a recovered client fuel model. */
public record FlightProfile(List<Double> drain,List<Double> refill,boolean groundedRefillOnly,
    double refillIdleSeconds,boolean descendingParachuteOnly,int version,double engineerAcceleration,
    double canopyGravity,boolean freeFallFloor) {
    public FlightProfile {
        drain=List.copyOf(drain);refill=List.copyOf(refill);
        if(drain.size()!=3||refill.size()!=3||version<1||version>2)throw new IllegalArgumentException("Flight profile");
        for(double value:java.util.stream.Stream.concat(drain.stream(),refill.stream()).toList())if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Fuel rate");
        if(!Double.isFinite(refillIdleSeconds)||refillIdleSeconds<0||!(engineerAcceleration>0&&engineerAcceleration<=1)||!(canopyGravity>0&&canopyGravity<=1))throw new IllegalArgumentException("Flight tuning");
    }
    public static final FlightProfile RETAIL=new FlightProfile(List.of(75.,17.,18.),List.of(10.,9.,3.),false,0,false,1,.1,.05,false);
    public static final FlightProfile BALANCED=new FlightProfile(List.of(30.,9.,7.5),List.of(20.,20.,20.),true,1,true,1,.1,.05,false);
    public static final FlightProfile BALANCED_V2=new FlightProfile(BALANCED.drain,BALANCED.refill,true,1,true,2,.25,.15625,true);
    public static FlightProfile forCapability(int version){return version>=2?BALANCED_V2:version==1?BALANCED:RETAIL;}
    private static int unit(double value,int scale){double rounded=Math.rint(value*scale);if(rounded<0||rounded>65535)throw new IllegalArgumentException("Flight wire unit");return (int)rounded;}
    public byte[] encode(){
        int flags=(groundedRefillOnly?1:0)|(descendingParachuteOnly?2:0)|(version>=2&&freeFallFloor?4:0);
        var w=new Wire.Writer().bytes(new byte[]{'B','S','F','P',(byte)version}).u8(flags).i16(unit(refillIdleSeconds,64));
        for(double value:drain)w.i16(unit(value,64));for(double value:refill)w.i16(unit(value,64));
        if(version>=2)w.i16(unit(engineerAcceleration,1024)).i16(unit(canopyGravity,1024));return w.toByteArray();
    }
    double canopyStep(double velocity,double dt,double gravity){
        double divisor=1+dt,stepped=(velocity+dt*gravity*canopyGravity)/divisor;
        return freeFallFloor?Math.max(stepped,Math.min((velocity+dt*gravity)/divisor,canopyGravity*gravity)):stepped;
    }
}
