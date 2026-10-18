// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.entities;

import java.util.List;
import net.battlespades.combat.HitTrace;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.EntityRecord;
import net.battlespades.protocol.Vector3f;

/** Owner-loop entity state. Runtime fields never enter the wire record. */
public final class MapEntity implements HitTrace.Entity {
    private final int id;
    public int type,state=1,playerId,face=4,ugcMode;
    public double x,y,z,yaw,radius,fuse,respawnAt,terrainOffsetZ,terrainCheckAt,fallAccumulator,fallClock;
    public Integer terrainSupportZ;
    public Vector color,home=new Vector(0,0,0),velocity=new Vector(0,0,0);
    public String kind="crate";
    public boolean alive=true,wireVisible=true,falling,parachuteDeployed,parachuteRemoved;
    public EntityRegistry.Cell supportCell;
    public EntityBehavior behavior;
    public List<Integer> intProperties=List.of();
    MapEntity(int id,int type,double x,double y,double z){this.id=id;this.type=type;this.x=x;this.y=y;this.z=z;}
    @Override public int id(){return id;}
    @Override public boolean alive(){return alive;}
    @Override public boolean takesDamage(){return behavior!=null&&behavior.takesDamage;}
    @Override public double hitRadius(){return behavior==null?0:behavior.hitRadius;}
    @Override public Vector hitCenter(){return behavior==null?new Vector(x,y,z):behavior.hitCenter(this);}
    private static Vector3f wire(Vector value){return new Vector3f((float)value.x(),(float)value.y(),(float)value.z());}
    public EntityRecord toWireEntity(){return new EntityRecord(id,type,state,playerId,new Vector3f((float)x,(float)y,(float)z),wire(velocity),(float)yaw,wire(color==null?new Vector(0,0,0):color),(float)radius,face,(float)fuse,ugcMode&255,intProperties,List.of());}
}
