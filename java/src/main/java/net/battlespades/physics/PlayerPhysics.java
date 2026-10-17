// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.physics;

import java.util.List;
import java.util.Objects;

/**
 * Infantry mover from aoslib/world.pyx: Player.update, boxclipmove, ledge nudges,
 * ordered player impulses and crouch clearance. Float stores and double expressions
 * are intentional: changing their order changes which voxel a contact probe selects.
 * This is simulation state; equipment activation, networking and health belong to the game.
 */
public final class PlayerPhysics {
    public record Vector(double x, double y, double z) {
        public Vector { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("Nonfinite vector"); }
    }
    public record Peer(double x, double y, double z, double height) {
        public Peer { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(height)||height<=0) throw new IllegalArgumentException("Peer body"); }
        public Peer(double x,double y,double z) {this(x,y,z,2.699999928474426);}
    }
    public record Input(boolean up,boolean down,boolean left,boolean right,boolean jump,boolean sneak,boolean sprint) {}
    public record Flight(int pack, boolean active, boolean passive, boolean hover,
                         boolean parachute, boolean canopy, float engineerAcceleration,
                         float canopyGravity, boolean freeFallFloor) {
        public Flight {
            if(pack<0||pack>4||!(engineerAcceleration>0&&engineerAcceleration<=1)||!(canopyGravity>0&&canopyGravity<=1)) throw new IllegalArgumentException("Flight profile");
        }
        public static Flight infantry() {return new Flight(0,false,false,false,false,false,0.1f,0.05f,false);}
    }
    public record State(Vector position,Vector velocity,boolean airborne,boolean wade,
                        boolean crouch,boolean jump,boolean jumped,float fallDistance,float climbTimer) {}

    private final Thread owner=Thread.currentThread();
    private final CollisionMap map;
    private double x,y,z,vx,vy,vz,ox=1,oy,oz,sx,sy=1;
    private boolean airborne,wade,crouch,jump,jumped,alive=true,exploded,burdened;
    private Input input=new Input(false,false,false,false,false,false,false);
    private Flight flight=Flight.infantry();
    private MovementProfile profile=MovementProfile.forClass(0);
    private float fallDistance,climbTimer,climbSlowdown=1;
    private Vector lockMin,lockMax;
    public PlayerPhysics(CollisionMap map) {this.map=Objects.requireNonNull(map);}
    private void owned() {if(Thread.currentThread()!=owner)throw new IllegalStateException("Physics accessed outside owner");}
    public State state() {owned();return new State(new Vector(x,y,z),new Vector(vx,vy,vz),airborne,wade,crouch,jump,jumped,fallDistance,climbTimer);}
    public boolean hitboxCrouched(){owned();return crouch&&!flight.hover;}
    public void position(Vector p) {owned();x=p.x;y=p.y;z=p.z;}
    public void velocity(Vector v) {owned();vx=v.x;vy=v.y;vz=v.z;}
    public void orientation(Vector v) {
        owned();ox=v.x;oy=v.y;oz=v.z;double length=Math.sqrt(ox*ox+oy*oy);
        if(length<=0){sx=0;sy=1;}else{sx=-oy/length;sy=ox/length;}
    }
    public Vector orientation() {owned();return new Vector(ox,oy,oz);}
    public void input(Input value) {owned();input=Objects.requireNonNull(value);jump=value.jump;}
    public void flight(Flight value) {owned();flight=Objects.requireNonNull(value);}
    public void profile(MovementProfile value) {owned();profile=Objects.requireNonNull(value);}
    public void alive(boolean value) {owned();alive=value;}
    public void exploded(boolean value) {owned();exploded=value;}
    public void burdened(boolean value) {owned();burdened=value;}
    public void climbSlowdown(float value) {owned();if(!Float.isFinite(value))throw new IllegalArgumentException("Climb slowdown");climbSlowdown=value;}
    public void lock(Vector min,Vector max) {
        owned();if(min.x>max.x||min.y>max.y||min.z>max.z)throw new IllegalArgumentException("Lock bounds");lockMin=min;lockMax=max;
    }
    public void unlock() {owned();lockMin=null;lockMax=null;}

    private boolean clip(float px,float py,float pz) {
        if(px<0 || (int)px>511 || py<0 || (int)py>511)return true;
        int iz=(int)pz;if(iz<0)return false;
        if(iz==239)iz=238;else if(iz>238)return true;
        return map.solid((int)px,(int)py,iz);
    }
    private boolean corners(float px,float py,float pz) {
        return clip((float)(px-0.449999988079071),(float)(py-0.449999988079071),pz)
            ||clip((float)(px-0.449999988079071),(float)(py+0.449999988079071),pz)
            ||clip((float)(px+0.449999988079071),(float)(py-0.449999988079071),pz)
            ||clip((float)(px+0.449999988079071),(float)(py+0.449999988079071),pz);
    }
    private boolean column(float px,float py,float feet,int levels,float shift) {
        for(int i=0;i<levels;i++)if(corners(px,py,(float)((feet-i)- (double)shift)))return true;
        return false;
    }
    /** Source set_crouch includes ordered peer clearance and the hover uncrouch path. */
    public void crouch(boolean target,List<Peer> peers) {
        owned();boolean effective=target;
        if(flight.hover){crouch=target;effective=false;}
        if(effective==crouch)return;
        if(effective){if(!airborne)z=(float)((float)z+0.9);crouch=true;return;}
        float oldZ=(float)z,lower=(float)(oldZ+2.25),upper=(float)(oldZ-1.350000023841858);
        crouch=false;
        boolean stand=airborne&&!corners((float)x,(float)y,lower)&&collide(peers,0,false)==0;
        if(!stand){
            z=(float)(oldZ-(double)0.9f);
            stand=!corners((float)x,(float)y,upper)&&collide(peers,0,false)==0;
            if(!stand)z=(float)((float)z+(double)0.9f);
        }
        crouch=flight.hover||!stand;
    }
    /** Returns source landing damage, -1 for a slowing landing without damage, otherwise zero. */
    public int update(double elapsed,double gravityValue,List<Peer> peers) {
        owned();
        float step=(float)elapsed,gravity=(float)gravityValue;
        if(!Float.isFinite(step)||step<0||!Float.isFinite(gravity)||gravity<0)throw new IllegalArgumentException("Physics step/gravity");
        double dt=step;
        boolean requested=jump,wasAirborne=airborne;
        jumped=requested&&!wasAirborne;
        if(wasAirborne&&flight.pack==0&&!flight.parachute)jump=false;
        if(requested){
            if(flight.active&&flight.pack>=1&&flight.pack<=4){
                double thrust=switch(flight.pack){case 1->(double)0.045f;case 2->(double)0.0125f;case 3->(double)0.020f;default->(double)0.025f;};
                vz=(float)((float)vz-((gravity+1.0)*thrust)*0.5);fallDistance=0;jumped=!wasAirborne;
            }else if(!flight.active&&!wasAirborne){vz=(float)((double)-0.36f*profile.jump());jumped=true;}
        }else if(flight.hover&&flight.pack==4){
            vz=crouch?(float)((float)vz-((gravity+1.0)*-0.02500000037252903)*0.5):0;
        }
        float accel;
        if((crouch&&!flight.hover)||input.sneak)accel=profile.crouchSneak();
        else if(input.sprint&&!burdened)accel=profile.sprint();else accel=profile.acceleration();
        accel=(float)((double)accel*step);
        if(airborne){
            if(flight.active&&!flight.hover&&flight.pack==3)accel=(float)((double)accel*flight.engineerAcceleration);
            else if(flight.active&&!flight.hover&&flight.pack==4)accel=(float)((double)accel*0.1000000014901161);
            else if(!flight.active||flight.hover||flight.pack==1||flight.pack==2)accel=(float)(accel*0.5);
        }
        if((input.up||input.down)&&(input.left||input.right))accel=(float)((double)accel*0.7071067690849304);
        float fx=(float)ox,fy=(float)oy;
        float square=(float)((double)fx*fx+(double)fy*fy);
        if(square>0){float length=(float)Math.sqrt(square),inverse=(float)(1.0/length);fx=(float)((double)fx*inverse);fy=(float)((double)fy*inverse);}
        float sideX=(float)sx,sideY=(float)sy;
        if(input.up){vx=(float)((float)vx+(double)fx*accel);vy=(float)((float)vy+(double)fy*accel);}
        if(input.down){vx=(float)((float)vx-(double)fx*accel);vy=(float)((float)vy-(double)fy*accel);}
        if(input.left){vx=(float)((float)vx-(double)sideX*accel);vy=(float)((float)vy-(double)sideY*accel);}
        if(input.right){vx=(float)((float)vx+(double)sideX*accel);vy=(float)((float)vy+(double)sideY*accel);}
        if(climbTimer>0){vx=(float)((float)vx*(double)climbSlowdown);vy=(float)((float)vy*(double)climbSlowdown);}
        float divisor=(float)(dt+1.0);double gravityStep=dt*gravity,previousVz=vz;
        boolean canopy=false;
        if(flight.passive)gravityStep*=0.75;else if(flight.canopy){gravityStep*=flight.canopyGravity;canopy=true;}
        if(!flight.hover&&(!flight.passive||(flight.pack>=1&&flight.pack<=4)))vz=(float)((float)vz+gravityStep);
        vz=(float)((float)vz/(double)divisor);
        if(canopy&&flight.freeFallFloor&&!flight.hover){
            float free=(float)((float)((float)previousVz+dt*gravity)/(double)divisor);
            float terminal=(float)((double)flight.canopyGravity*gravity);
            float floor=free<terminal?free:terminal;if(floor>vz)vz=floor;
        }
        if(flight.canopy)fallDistance=0;
        float horizontal;
        if(airborne&&(flight.active||flight.passive))horizontal=divisor;
        else if(wade)horizontal=(float)(dt*profile.waterFriction()+1.0);
        else horizontal=(float)(dt*(airborne?2.0:4.0)+1.0);
        vx=(float)((float)vx/(double)horizontal);vy=(float)((float)vy/(double)horizontal);
        float landingSpeed=(float)vz;
        if(!alive&&flight.pack>=1&&flight.pack<=4){
            vz=(float)((float)vz-dt*3.0);float angle=(float)(vz*10.0),trig=(float)Math.sin(angle);
            vx=(float)((float)vx+trig*dt);trig=(float)Math.cos(angle);vy=(float)((float)vy+trig*dt);
        }
        collide(peers,dt,true);double oldZ=z;boolean oldCrouch=crouch;
        boolean climbed=moveBox(dt);
        if(crouch&&!oldCrouch)oldZ=(float)((float)oldZ+(double)0.9f);
        float delta=(float)(z-oldZ);
        if(delta>0)fallDistance+=delta;else if(delta< -0.1)fallDistance=0;
        if(lockMin!=null){x=Math.min(Math.max(x,lockMin.x),lockMax.x);y=Math.min(Math.max(y,lockMin.y),lockMax.y);z=Math.min(Math.max(z,lockMin.z),lockMax.z);}
        if(climbed)climbTimer=0.1f;
        climbTimer=Math.max(0,(float)((double)climbTimer-step));
        if(z>240)z=238;
        if(vz!=0)return 0;
        groundHoles(dt);
        float fall=(float)((double)fallDistance*gravity);
        if(flight.passive&&flight.pack>=1&&flight.pack<=4)fall=(float)(fall*0.75);
        // These operations use Python objects in the Cython source, hence double intermediates.
        double span=profile.fallMaxDistance()-(double)profile.fallMinDistance();
        float ratio=span>0?(float)((fall-profile.fallMinDistance())/span):(fall>=profile.fallMaxDistance()?1:0);
        ratio=Math.min(1,Math.max(0,ratio));
        int damage=(int)(profile.fallMaxDamage()*(double)ratio);
        if(z>237)damage=(int)(damage*(double)profile.waterDamage());fallDistance=0;
        if(landingSpeed<=0.24)return damage;
        float severe=gravity>0?(float)((double)0.8f/gravity):Float.POSITIVE_INFINITY;
        if(landingSpeed>severe){vx*=0.5;vy*=0.5;}
        return damage!=0?damage:-1;
    }

    private boolean moveBox(double dt) {
        float px=(float)x,py=(float)y,pz=(float)z;
        float dx=(float)vx,dy=(float)vy,dz=(float)vz;
        float scale=(float)(dt*32.0),cx=(float)((double)dx*scale+px),cy=(float)((double)dy*scale+py);
        int levels=crouch&&!flight.hover?2:3;
        float m=levels==2?0.89999998f:1.3499999f,rad=levels==2?0.44999999f:0.89999998f;
        float start=pz+rad,feet=m+start,wx=px,wy=py;boolean climbed=false;
        if(!column(cx,py,feet,levels,0))wx=cx;
        else if(!crouch&&!flight.hover&&(!input.sprint||profile.canSprintUphill())){
            if(column(cx,py,feet,levels,1))dx=0;
            else if(!corners(cx,wy,(float)((start-m)-1.0))){wx=cx;climbed=true;}
        }
        float glide=0,mid=start;
        if(column(wx,wy,feet,levels,0)){
            glide=(float)(((double)dx*dx*4.0+(double)0.05f)*scale);
            if(corners(wx,wy,(float)Math.floor(start-m))){wx=px;dx=0;}
            else {mid=start-glide;dz=0;}
        }
        float current=mid,newFeet=m+mid;
        if(!column(wx,cy,newFeet,levels,0))wy=cy;
        else if(!crouch&&!flight.hover&&(!input.sprint||profile.canSprintUphill())){
            if(column(wx,cy,newFeet,levels,1))dy=0;
            else if(!corners(wx,cy,(float)((current-m)-1.0))){wy=cy;climbed=true;}
        }
        if(column(wx,wy,newFeet,levels,0)){
            glide=(float)(((double)dy*dy*4.0+(double)0.05f)*scale);
            if(corners(wx,wy,(float)Math.floor(current-m))){wy=py;dy=0;}
            else {current-=glide;dz=0;}
        }
        x=wx;y=wy;vx=dx;vy=dy;vz=dz;
        if(climbed){z=current-rad;airborne=false;return true;}
        if(glide!=0){
            if(!crouch&&clip(wx,wy,(float)Math.floor(m+current))&&!clip(wx,wy,(float)Math.floor(current))&&!clip(wx,wy,(float)Math.floor(current-m)))crouch=true;
            z=current-rad;return false;
        }
        float vertical=dz>=0?m:-m;
        current=(float)(current+(double)scale*dz);airborne=true;
        if(!corners(wx,wy,(float)Math.floor(vertical+current))){z=current-rad;return false;}
        z=pz;vz=0;
        if(dz>=0){airborne=false;wade=pz>237;}
        return false;
    }

    private int collide(List<Peer> peers,double dt,boolean resolve) {
        if(peers.isEmpty()||!alive||exploded)return 0;
        float scale=(float)((float)dt*32.0);
        float height=(float)((crouch&&!flight.hover?4.0:6.0)*0.449999988079071),half=(float)(height*0.5);
        float offset=(float)(height-0.449999988079071-half);
        float cx=(float)((float)x+(double)(float)vx*scale),cy=(float)((float)y+(double)(float)vy*scale);
        float cz=(float)((float)z+(double)(float)vz*scale+offset);int count=0;
        for(Peer peer:peers){
            float otherHeight=(float)peer.height,otherX=(float)peer.x,otherY=(float)peer.y;
            float otherHalf=(float)(otherHeight*0.5),otherZ=(float)(otherHeight-0.449999988079071-otherHalf);
            otherZ=(float)(peer.z+otherZ);
            float dx=(float)((double)cx-otherX),dy=(float)((double)cy-otherY);
            float square=(float)((double)dx*dx+(double)dy*dy),length=(float)Math.sqrt(square);
            float push=(float)Math.max(0,0.8999999761581421-length);if(push<=0)continue;
            float dz=(float)((double)cz-otherZ),overlap=(float)Math.max(0,(double)otherHalf+half-Math.abs(dz));
            if(overlap<=0)continue;
            if(!resolve){count++;continue;}if(scale<=0)continue;
            if(overlap>push){
                float strength=(float)((double)push/scale),nx,ny;
                if(dx==0||dy==0){nx=1;ny=0;}else{float inverse=(float)(1.0/length);nx=(float)((double)dx*inverse);ny=(float)((double)dy*inverse);}
                float impulse=(float)((double)nx*strength);vx=(float)((float)vx+(double)impulse);
                impulse=(float)((double)ny*strength);vy=(float)((float)vy+(double)impulse);
            }else{
                float strength=(float)((double)overlap/scale),nz;
                if(dz==0)nz=1;else {square=(float)((double)dz*dz);length=(float)Math.sqrt(square);float inverse=(float)(1.0/length);nz=(float)((double)dz*inverse);}
                float impulse=(float)((double)nz*strength);vz=(float)((float)vz+(double)impulse);if(dz<0)fallDistance=0;
            }
            count++;
        }
        return count;
    }
    private boolean raw(int px,int py,int pz) {return px>=0&&px<=511&&py>=0&&py<=511&&pz>=0&&pz<=239&&map.solid(px,py,pz);}
    private void groundHoles(double dt) {
        if(input.up||input.down||input.left||input.right)return;
        float px=(float)x,py=(float)y,pz=(float)z,feet=(float)(pz+(crouch&&!flight.hover?1.3499999:2.25));
        int ix=(int)Math.floor(px),iy=(int)Math.floor(py),iz=(int)Math.floor((float)(feet+1.0));
        if(raw(ix,iy,iz))return;
        float dx=(float)((ix+0.5)-px),ax=Math.abs(dx),dy=(float)((iy+0.5)-py),ay=Math.abs(dy);
        int nx=ax!=0?ix-(int)(dx/ax):-(1<<30),ny=ay!=0?iy-(int)(dy/ay):-(1<<30);
        boolean pushX=raw(nx,iy,iz),pushY=raw(ix,ny,iz);
        if(!pushX&&!pushY&&raw(nx,ny,iz)){if(dy<=dx)pushY=true;else pushX=true;}
        if((!pushX||ax<=0.2000000029802322)&&(!pushY||ay<=0.2000000029802322)){
            if(pushX)vx=(float)((dx*dt)*5.0);if(pushY)vy=(float)((dy*dt)*5.0);
        }
    }
}
