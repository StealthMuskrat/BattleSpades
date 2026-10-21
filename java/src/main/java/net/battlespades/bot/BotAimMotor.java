// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;

import java.util.Objects;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SourceRandom;

/** Original bounded camera controller and view-relative key feathering. No body movement occurs here. */
public final class BotAimMotor {
    private final Thread owner=Thread.currentThread();private final SourceRandom random;private final BotProfile profile;
    private double yaw,pitch,yawVelocity,pitchVelocity,yawAcceleration,pitchAcceleration,yawNoise,pitchNoise,gazeAge,residualForward,residualSide;
    private Double gazeYaw,gazePitch,gaussian;private String purpose="";
    public record State(double yaw,double pitch,double yawVelocity,double pitchVelocity,double yawAcceleration,double pitchAcceleration,double yawNoise,double pitchNoise,String purpose,Double gazeYaw,Double gazePitch,double gazeAge,double residualForward,double residualSide){}
    public record Keys(int forward,int side){}
    public BotAimMotor(BotProfile profile,SourceRandom random,double yaw){this.profile=Objects.requireNonNull(profile);this.random=Objects.requireNonNull(random);this.yaw=yaw;}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Bot aim outside owner");}
    public State state(){owned();return new State(yaw,pitch,yawVelocity,pitchVelocity,yawAcceleration,pitchAcceleration,yawNoise,pitchNoise,purpose,gazeYaw,gazePitch,gazeAge,residualForward,residualSide);}
    public void hold(){owned();yawVelocity=pitchVelocity=yawAcceleration=pitchAcceleration=0;purpose="";gazeYaw=gazePitch=null;}
    public static double wrap(double angle){return angle-Math.floor((angle+Math.PI)/(Math.PI*2))*(Math.PI*2);}
    private double gauss(double sigma){double z;if(gaussian!=null){z=gaussian;gaussian=null;}else{double angle=random.random()*Math.PI*2,scale=Math.sqrt(-2*Math.log(1-random.random()));z=Math.cos(angle)*scale;gaussian=Math.sin(angle)*scale;}return z*sigma;}
    public void overshoot(double error){owned();if(Math.abs(error)>.35)yawNoise+=Math.copySign(1,error)*random.uniform(.05,.18)*(1-profile.skill());}
    public void recoil(int tool){owned();double kick=BotCombatProfiles.recoil(tool);if(kick<=0)return;pitchNoise-=kick*(1-.75*profile.recoilControl());yawNoise+=gauss(kick*.25*(1-.5*profile.recoilControl()));}
    public Vector update(Vector eye,Vector target,double dt,double noiseFactor,String nextPurpose){
        owned();if(!Double.isFinite(dt)||!Double.isFinite(noiseFactor))throw new IllegalArgumentException("Finite motor timing required");
        double dx=target.x()-eye.x(),dy=target.y()-eye.y(),dz=target.z()-eye.z(),planar=Math.hypot(dx,dy);
        if(planar<=1e-6&&Math.abs(dz)<=1e-6){hold();return orientation();}
        double ye=0,pe=0;
        if(nextPurpose.equals("combat")){double decay=Math.max(0,1-4*dt),scale=profile.aimNoise()*Math.sqrt(Math.max(dt,1e-6))*2*noiseFactor;yawNoise=yawNoise*decay+gauss(scale);pitchNoise=pitchNoise*decay+gauss(scale*.6);ye=yawNoise;pe=pitchNoise;}
        else{yawNoise*=Math.exp(-4*Math.max(0,dt));pitchNoise*=Math.exp(-4*Math.max(0,dt));}
        double desiredYaw=planar>1e-6?Math.atan2(dy,dx)+ye:yaw,desiredPitch=clamp(Math.atan2(dz,Math.max(planar,1e-6))+pe,-1.35,1.35);
        if(nextPurpose.equals("travel")||nextPurpose.equals("focus")||nextPurpose.equals("traverse"))purposeful(desiredYaw,desiredPitch,dt,nextPurpose);
        else{purpose=nextPurpose;gazeYaw=gazePitch=null;yawAcceleration=pitchAcceleration=0;var y=secondOrder(yaw,yawVelocity,desiredYaw,profile.turnSpeed(),profile.turnAcceleration(),dt,true);yaw=y.position; yawVelocity=y.velocity;var p=secondOrder(pitch,pitchVelocity,desiredPitch,profile.turnSpeed()*.75,profile.turnAcceleration()*.75,dt,false);pitch=p.position;pitchVelocity=p.velocity;}
        pitch=clamp(pitch,-1.35,1.35);if(pitch>=1.35&&pitchVelocity>0||pitch<=-1.35&&pitchVelocity<0)pitchVelocity=pitchAcceleration=0;
        return orientation();
    }
    public Vector orientation(){owned();double cp=Math.cos(pitch);return new Vector(Math.cos(yaw)*cp,Math.sin(yaw)*cp,Math.sin(pitch));}
    private void purposeful(double y,double p,double dt,String next){
        double dwell=next.equals("traverse")?0:.16+.10*profile.caution()+.06*(1-profile.skill());
        if(!purpose.equals(next)||gazeYaw==null){gazeYaw=yaw;gazePitch=pitch;gazeAge=dwell;purpose=next;}
        gazeAge+=Math.max(0,dt);double yc=Math.abs(wrap(y-gazeYaw)),pc=Math.abs(p-gazePitch),dead=Math.toRadians(next.equals("travel")?2:1);
        if(Math.max(yc,pc)>=Math.toRadians(12)||gazeAge>=dwell&&(yc>dead||pc>Math.toRadians(.6))){gazeYaw=y;gazePitch=p;gazeAge=0;}
        double stiffness=9+1.6*profile.turnSpeed(),speed=6+profile.turnSpeed();var ya=smooth(yaw,yawVelocity,yawAcceleration,gazeYaw,speed,stiffness,dt,true);yaw=ya.position;yawVelocity=ya.velocity;yawAcceleration=ya.acceleration;var pa=smooth(pitch,pitchVelocity,pitchAcceleration,gazePitch,speed*.75,stiffness,dt,false);pitch=pa.position;pitchVelocity=pa.velocity;pitchAcceleration=pa.acceleration;
    }
    public record Axis(double position,double velocity,double acceleration){}
    public static Axis smooth(double current,double velocity,double acceleration,double target,double speed,double stiffness,double dt,boolean wrapped){
        double elapsed=clamp(dt,0,.25);int steps=Math.max(1,(int)Math.ceil(elapsed*120));double step=elapsed/steps;
        for(int i=0;i<steps;i++){double error=wrapped?wrap(target-current):target-current;acceleration=stiffness*stiffness*error-2*stiffness*velocity;velocity=clamp(velocity+acceleration*step,-speed,speed);current+=velocity*step;if(wrapped)current=wrap(current);if(Math.abs(error)<Math.toRadians(.1)&&Math.abs(velocity)<Math.toRadians(.5))velocity=acceleration=0;}
        return new Axis(current,velocity,acceleration);
    }
    public static Axis secondOrder(double current,double velocity,double target,double speed,double acceleration,double dt,boolean wrapped){
        double elapsed=clamp(dt,0,.25);int steps=Math.max(1,(int)Math.ceil(elapsed*60));double step=elapsed/steps;
        for(int i=0;i<steps;i++){double error=wrapped?wrap(target-current):target-current,wanted=clamp(error*7,-speed,speed);velocity+=clamp(wanted-velocity,-acceleration*step,acceleration*step);current+=velocity*step;if(wrapped)current=wrap(current);}
        return new Axis(current,velocity,0);
    }
    public Keys keys(double forward,double side){
        owned();double length=Math.hypot(forward,side);if(length<=.1){residualForward=residualSide=0;return new Keys(0,0);}
        double wf=forward/length+residualForward,ws=side/length+residualSide,angle=Math.rint(Math.atan2(ws,wf)/(Math.PI/4))*(Math.PI/4),pf=Math.cos(angle),ps=Math.sin(angle);residualForward=wf-pf;residualSide=ws-ps;double owed=Math.hypot(residualForward,residualSide);if(owed>1){residualForward/=owed;residualSide/=owed;}
        return new Keys((pf>.3?1:0)-(pf<-.3?1:0),(ps>.3?1:0)-(ps<-.3?1:0));
    }
    private static double clamp(double n,double low,double high){return Math.max(low,Math.min(high,n));}
}
