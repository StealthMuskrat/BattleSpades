// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.SpawnGeometry.Zone;
import net.battlespades.protocol.*;

/** Shared inclusive capture volume and exact packet 43/44 identity. */
public record ObjectiveZone(int index,int x0,int x1,int y0,int y1,int z0,int z1,Vector center) {
    private static int bound(double value,int max){return Math.max(0,Math.min(max,(int)Math.rint(value)));}
    private static ObjectiveZone bounds(int index,double x0,double x1,double y0,double y1,double z0,double z1,Vector center){
        int a=bound(Math.min(x0,x1),511),b=bound(Math.max(x0,x1),511),c=bound(Math.min(y0,y1),511),d=bound(Math.max(y0,y1),511),e=bound(Math.min(z0,z1),239),f=bound(Math.max(z0,z1),239);
        return new ObjectiveZone(index,a,b,c,d,e,f,center==null?new Vector((a+b)*.5,(c+d)*.5,(e+f)*.5):center);
    }
    public static ObjectiveZone from(int index,Zone z,int shift){return bounds(index,z.x()+z.x0(),z.x()+z.x1(),z.y()+z.y0(),z.y()+z.y1(),z.z()+z.z0()+shift,z.z()+z.z1()+shift,null);}
    public static ObjectiveZone around(int index,Vector p,double radius,double above,double below){return bounds(index,p.x()-radius,p.x()+radius,p.y()-radius,p.y()+radius,p.z()-above,p.z()+below,p);}
    public ObjectiveZone indexed(int value){return new ObjectiveZone(value,x0,x1,y0,y1,z0,z1,center);}
    public boolean contains(Vector p){return p.x()>=x0&&p.x()<=x1&&p.y()>=y0&&p.y()<=y1&&p.z()>=z0&&p.z()<=z1;}
    public MinimapZone packet(int color,int icon){return new MinimapZone(1,color,x0,x1,y0,y1,z0,z1,1,icon,0);}
    public SimplePackets.MinimapZoneClear clear(){return new SimplePackets.MinimapZoneClear(x0,y0,z0,x1,y1,z1);}
}
