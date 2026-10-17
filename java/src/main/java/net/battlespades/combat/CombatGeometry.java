// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SpawnMath;
import net.battlespades.catalog.SourceCatalog;

/** Source combat_runtime voxel visibility and oriented KV6 bounds. This class does not admit shots. */
public final class CombatGeometry {
    private CombatGeometry() {}
    public record Cell(int x,int y,int z) {}
    @FunctionalInterface public interface Terrain {boolean solid(int x,int y,int z);}
    public record Entry(double distance,Vector position) {}
    public record BodyHit(Entry entry,int part) {public boolean headshot(){return part==0;}}
    public record Bounds(Vector size,Vector pivot) {}
    private static final Map<Integer,List<Bounds>> PROFILES;
    private static final Bounds CROUCH_TORSO,CROUCH_LEG,CORPSE;
    private static final double ARMS_Z,LEG_Y;
    private static final int SOLDIER;
    static {
        var data=SourceCatalog.load("hitboxes");var settings=data.getArray("settings").getTable(0);
        SOLDIER=Math.toIntExact(settings.getLong("soldier"));ARMS_Z=((Number)settings.get("arms_z")).doubleValue();LEG_Y=((Number)settings.get("leg_y")).doubleValue();
        CROUCH_TORSO=bounds(settings,"crouch_torso");CROUCH_LEG=bounds(settings,"crouch_leg");CORPSE=bounds(settings,"corpse");
        var profiles=new HashMap<Integer,List<Bounds>>();var rows=data.getArray("profile");
        for(int j=0;j<rows.size();j++){var row=rows.getTable(j);var parts=new ArrayList<Bounds>();for(int p=0;p<5;p++)parts.add(bounds(row,"part"+p));profiles.put(Math.toIntExact(row.getLong("id")),List.copyOf(parts));}PROFILES=Map.copyOf(profiles);
    }
    private static Bounds bounds(org.tomlj.TomlTable row,String key){var a=row.getArray(key);return new Bounds(new Vector(((Number)a.get(0)).doubleValue(),((Number)a.get(1)).doubleValue(),((Number)a.get(2)).doubleValue()),new Vector(((Number)a.get(3)).doubleValue(),((Number)a.get(4)).doubleValue(),((Number)a.get(5)).doubleValue()));}
    public static boolean finite(Vector v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    public static double dot(Vector a,Vector b){return SpawnMath.sum(a.x()*b.x(),a.y()*b.y(),a.z()*b.z());}
    public static Vector subtract(Vector a,Vector b){return new Vector(a.x()-b.x(),a.y()-b.y(),a.z()-b.z());}
    public static Vector along(Vector origin,Vector direction,double at){return new Vector(origin.x()+direction.x()*at,origin.y()+direction.y()*at,origin.z()+direction.z()*at);}
    public static double distance(Vector a,Vector b){var d=subtract(a,b);return Math.sqrt(d.x()*d.x()+d.y()*d.y()+d.z()*d.z());}
    public static Vector normalize(Vector v){double length=Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z());return length<=1e-6?null:new Vector(v.x()/length,v.y()/length,v.z()/length);}
    public static boolean segmentClear(Terrain terrain,Vector start,Vector end){return segmentClear(terrain,start,end,.3,.3,Set.of());}
    public static boolean segmentClear(Terrain terrain,Vector start,Vector end,double shrinkStart,double shrinkEnd,Set<Cell> ignore){
        if(terrain==null)return true;
        if(!finite(start)||!finite(end))return false;
        var delta=subtract(end,start);double length=Math.sqrt(SpawnMath.sumSquares(delta.x(),delta.y(),delta.z())),usable=length-shrinkStart-shrinkEnd;
        if(usable<=1e-6)return true;
        double[] unit={delta.x()/length,delta.y()/length,delta.z()/length},s={start.x(),start.y(),start.z()},origin=new double[3],maximum={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},increment=maximum.clone();
        int[] cell=new int[3],last=new int[3],step=new int[3];
        for(int axis=0;axis<3;axis++){
            origin[axis]=s[axis]+unit[axis]*shrinkStart;cell[axis]=(int)Math.floor(origin[axis]);last[axis]=(int)Math.floor(origin[axis]+unit[axis]*usable);
            if(unit[axis]>1e-12){step[axis]=1;maximum[axis]=(cell[axis]+1.0-origin[axis])/unit[axis];increment[axis]=1.0/unit[axis];}
            else if(unit[axis]<-1e-12){step[axis]=-1;maximum[axis]=(origin[axis]-cell[axis])/-unit[axis];increment[axis]=-1.0/unit[axis];}
        }
        for(long count=0,guard=(long)(usable*3)+8;count<guard;count++){
            var current=new Cell(cell[0],cell[1],cell[2]);if(!ignore.contains(current)&&terrain.solid(current.x,current.y,current.z))return false;
            if(Arrays.equals(cell,last))return true;int axis=0;for(int a=1;a<3;a++)if(maximum[a]<maximum[axis])axis=a;
            if(maximum[axis]>usable)return true;cell[axis]+=step[axis];maximum[axis]+=increment[axis];
        }
        return true;
    }
    public static List<Vector> samplePoints(Cell cell,double inset){
        double[] base={cell.x,cell.y,cell.z},offsets={inset,1-inset};var points=new ArrayList<Vector>();points.add(new Vector(cell.x+.5,cell.y+.5,cell.z+.5));
        for(int axis=0;axis<3;axis++)for(double offset:offsets){double[] p={cell.x+.5,cell.y+.5,cell.z+.5};p[axis]=base[axis]+offset;points.add(new Vector(p[0],p[1],p[2]));}
        for(double x:offsets)for(double y:offsets)for(double z:offsets)points.add(new Vector(cell.x+x,cell.y+y,cell.z+z));return List.copyOf(points);
    }
    public static boolean cellVisible(Terrain terrain,List<Vector> eyes,Cell cell,Set<Cell> ignore){var skip=new HashSet<>(ignore);skip.add(cell);for(var point:samplePoints(cell,.1))for(var eye:eyes)if(segmentClear(terrain,eye,point,.3,0,skip))return true;return false;}
    public static Entry sphere(Vector origin,Vector direction,double maximum,Vector center,double radius){
        var relative=subtract(center,origin);double projection=dot(relative,direction),radiusSq=radius*radius,closestSq=SpawnMath.sumSquares(relative.x(),relative.y(),relative.z())-Math.pow(projection,2);
        if(closestSq>radiusSq)return null;double half=Math.sqrt(Math.max(0,radiusSq-closestSq)),entry=projection-half,exit=projection+half;if(exit<0)return null;entry=Math.max(0,entry);return entry>maximum?null:new Entry(entry,along(origin,direction,entry));
    }
    public static Entry model(Vector origin,Vector direction,double maximum,Vector position,Vector orientation,Vector offset,Bounds bounds){
        double yaw=Math.atan2(orientation.x(),orientation.y()),cos=Math.cos(yaw),sin=Math.sin(yaw);
        var at=new Vector(position.x()-offset.x()*cos+offset.y()*sin,position.y()+offset.x()*sin-offset.y()*cos,position.z()+offset.z());
        Vector[] axes={new Vector(-cos*.05,sin*.05,0),new Vector(sin*.05,cos*.05,0),new Vector(0,0,.05)};
        double[] sizes={bounds.size.x(),bounds.size.y(),bounds.size.z()},pivots={bounds.pivot.x(),bounds.pivot.y(),bounds.pivot.z()};var relative=subtract(origin,at);double enter=0,leave=maximum;
        for(int a=0;a<3;a++){var axis=axes[a];double length=SpawnMath.sumSquares(axis.x(),axis.y(),axis.z()),coordinate=dot(relative,axis)/length+pivots[a],rate=dot(direction,axis)/length;
            if(Math.abs(rate)<1e-9){if(coordinate<0||coordinate>sizes[a])return null;continue;}
            double first=-coordinate/rate,second=(sizes[a]-coordinate)/rate;if(first>second){double swap=first;first=second;second=swap;}enter=Math.max(enter,first);leave=Math.min(leave,second);if(enter>leave)return null;
        }
        return new Entry(enter,along(origin,direction,enter));
    }
    public static Entry corpse(Vector origin,Vector direction,double maximum,Vector position,Vector orientation){return model(origin,direction,maximum,position,orientation,new Vector(0,0,0),CORPSE);}
    /** The stock tests torso before head, even when the head intersection is closer. */
    public static BodyHit body(Vector origin,Vector direction,double maximum,int classId,LagCompensation.Hitbox target){
        var profile=PROFILES.getOrDefault(classId,PROFILES.get(SOLDIER));boolean crouch=target.crouched();int[] order={1,0,2,3,4};
        Vector[] offsets={new Vector(0,0,.3),new Vector(0,0,.3),new Vector(0,0,crouch?ARMS_Z:.5),new Vector(.25,crouch?LEG_Y:0,crouch?.7:1.1),new Vector(-.25,crouch?LEG_Y:0,crouch?.7:1.1)};
        for(int j=0;j<5;j++){int part=order[j];var bounds=crouch&&part==1?CROUCH_TORSO:crouch&&part>=3?CROUCH_LEG:profile.get(part);var entry=model(origin,direction,maximum,target.position(),target.orientation(),offsets[j],bounds);if(entry!=null)return new BodyHit(entry,part);}return null;
    }
}
