// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.battlespades.physics.PlayerPhysics.Vector;

/** Source world.pyx voxel traversal, including X/Y/Z ray ties and cube-line ties. */
public final class WorldQueries {
    public record Cube(int x,int y,int z) {}
    public record Hit(Vector position,Cube cube,int face) {}
    private WorldQueries() {}

    /** Accurate source DDA. Query bounds prevent untrusted callers requesting unbounded work. */
    public static Optional<Hit> ray(CollisionMap map,Vector start,Vector direction,double length,boolean water) {
        if(!Double.isFinite(length)||length>65536||Math.abs(start.x())>1_000_000||Math.abs(start.y())>1_000_000||Math.abs(start.z())>1_000_000)throw new IllegalArgumentException("Ray bounds");
        if(map==null)return Optional.empty();
        double magnitude=Math.sqrt(direction.x()*direction.x()+direction.y()*direction.y()+direction.z()*direction.z());
        if(magnitude<=0)return Optional.empty();
        double dx=direction.x()/magnitude,dy=direction.y()/magnitude,dz=direction.z()/magnitude;
        int x=(int)Math.floor(start.x()),y=(int)Math.floor(start.y()),z=(int)Math.floor(start.z());
        int sx=dx>0?1:dx<0?-1:0,sy=dy>0?1:dy<0?-1:0,sz=dz>0?1:dz<0?-1:0;
        double nx=sx!=0?((x+(sx>0?1:0))-start.x())/dx:Double.POSITIVE_INFINITY;
        double ny=sy!=0?((y+(sy>0?1:0))-start.y())/dy:Double.POSITIVE_INFINITY;
        double nz=sz!=0?((z+(sz>0?1:0))-start.z())/dz:Double.POSITIVE_INFINITY;
        double ax=sx!=0?Math.abs(1.0/dx):Double.POSITIVE_INFINITY;
        double ay=sy!=0?Math.abs(1.0/dy):Double.POSITIVE_INFINITY;
        double az=sz!=0?Math.abs(1.0/dz):Double.POSITIVE_INFINITY;
        int face=0;double travelled=0;
        while(travelled<=length){
            if(x>=0&&x<512&&y>=0&&y<512&&z>=0&&z<240&&(map.solid(x,y,z)||(water&&z>=238))){
                return Optional.of(new Hit(new Vector(start.x()+dx*travelled,start.y()+dy*travelled,start.z()+dz*travelled),new Cube(x,y,z),face));
            }
            if(nx<=ny&&nx<=nz){travelled=nx;nx+=ax;x+=sx;face=sx>0?0:1;}
            else if(ny<=nx&&ny<=nz){travelled=ny;ny+=ay;y+=sy;face=sy>0?2:3;}
            else {travelled=nz;nz+=az;z+=sz;face=sz>0?4:5;}
        }
        return Optional.empty();
    }
    public static Optional<Hit> ray(CollisionMap map,Vector start,Vector direction) {return ray(map,start,direction,128,false);}
    /** Manhattan cell walk. This intentionally retains the source's unusual tie decisions. */
    public static List<Cube> cubeLine(Cube a,Cube b) {
        long count=1+Math.abs((long)b.x-a.x)+Math.abs((long)b.y-a.y)+Math.abs((long)b.z-a.z);
        if(count>4096)throw new IllegalArgumentException("Cube line exceeds 4096 cells");
        int dx=Math.abs(b.x-a.x),dy=Math.abs(b.y-a.y),dz=Math.abs(b.z-a.z),steps=(int)count;
        int sx=Integer.compare(b.x,a.x),sy=Integer.compare(b.y,a.y),sz=Integer.compare(b.z,a.z);
        int x=a.x,y=a.y,z=a.z,ex=dx,ey=dy,ez=dz;
        var result=new ArrayList<Cube>(steps);
        for(int i=0;i<steps;i++){
            result.add(new Cube(x,y,z));int axis;
            if(ex>ey&&ex>ez)axis=0;
            else if(ey>ex&&ey>ez)axis=1;
            else if(ez>ex&&ez>ey)axis=2;
            else if(dz>=dy&&dz>=dx){if(ez>=ey&&ez>=ex)axis=2;else if(ey>=ex)axis=1;else axis=0;}
            else if(dy>=dx){if(ey>=ex&&ey>=ez)axis=1;else if(ex>=ez)axis=0;else axis=2;}
            else {if(ex>=ey&&ex>=ez)axis=0;else if(ey>=ez)axis=1;else axis=2;}
            if(axis==0){x+=sx;ex-=steps;}else if(axis==1){y+=sy;ey-=steps;}else{z+=sz;ez-=steps;}
            ex+=dx;ey+=dy;ez+=dz;
        }
        return List.copyOf(result);
    }
    public static Cube adjacent(Cube c,int face) {
        return switch(face){case 0->new Cube(c.x-1,c.y,c.z);case 1->new Cube(c.x+1,c.y,c.z);case 2->new Cube(c.x,c.y-1,c.z);case 3->new Cube(c.x,c.y+1,c.z);case 4->new Cube(c.x,c.y,c.z-1);case 5->new Cube(c.x,c.y,c.z+1);default->c;};
    }
    public static Vector faceCenter(Cube c,int face) {
        double x=c.x+0.5,y=c.y+0.5,z=c.z+0.5;
        switch(face){case 0->x=c.x;case 1->x=c.x+1.0;case 2->y=c.y;case 3->y=c.y+1.0;case 4->z=c.z;case 5->z=c.z+1.0;default->{}}
        return new Vector(x,y,z);
    }
    public static boolean centered(Vector position,Vector orientation,Vector target,double tolerance) {
        double sideLength=Math.sqrt(orientation.x()*orientation.x()+orientation.y()*orientation.y());
        double sx=sideLength>0?-orientation.y()/sideLength:0,sy=sideLength>0?orientation.x()/sideLength:0;
        double dx=target.x()-position.x(),dy=target.y()-position.y(),dz=target.z()-position.z();
        double denominator=orientation.z()*dz+orientation.y()*dy+orientation.x()*dx;
        if(denominator==0)return false;
        double along=(sy*dy+sx*dx)/denominator,limit=tolerance/denominator;
        if(!(along-limit<0&&0<along+limit))return false;
        double across=(dz*(orientation.x()*sy-orientation.y()*sx)+dy*(orientation.z()*sx)-dx*(orientation.z()*sy))/denominator;
        return across-limit<0&&0<across+limit;
    }
}
