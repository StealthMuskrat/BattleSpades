// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.CollisionMap;

import net.battlespades.physics.WorldQueries.Cube;
import net.battlespades.world.MutableWorld;

/** Authored-volume and live terrain checks from WorldManager. Layout parsing is separate. */
public final class SpawnGeometry {
    /** Mode proposals may be invalid; validate/rescue before constructing a physics vector. */
    public record Position(double x,double y,double z){
        public net.battlespades.physics.PlayerPhysics.Vector toPhysics(){return new net.battlespades.physics.PlayerPhysics.Vector(x,y,z);}
    }
    public record Region(int x0,int y0,int x1,int y1){
        public Region {if(x0<0||y0<0||x1>=512||y1>=512||x0>x1||y0>y1)throw new IllegalArgumentException("Spawn region");}
        public boolean contains(int x,int y){return x0<=x&&x<=x1&&y0<=y&&y<=y1;}
    }
    public record Zone(String kind,int team,double x,double y,double z,double x0,double x1,double y0,double y1,double z0,double z1,String item){
        public Zone {
            Objects.requireNonNull(kind);Objects.requireNonNull(item);
            for(double value:new double[]{x,y,z,x0,x1,y0,y1,z0,z1})if(!Double.isFinite(value))throw new IllegalArgumentException("Zone coordinates");
        }
        int left(){return (int)(x+x0);}int right(){return (int)(x+x1);}
        int front(){return (int)(y+y0);}int back(){return (int)(y+y1);}
        public boolean contains(int cx,int cy){return left()<=cx&&cx<=right()&&front()<=cy&&cy<=back();}
        public boolean containsSurface(int surface){return z+z0<=surface&&surface<=z+z1;}
        public boolean water(){return item.equals("zombie_spawn_area");}
        int top(){return (int)Math.floor(z+z0);}
        int bottom(){return Math.min(239,(int)Math.ceil(z+(item.endsWith("spawn_area")&&z1>-z0?-z0:z1)));}
    }
    public record Candidate(int x,int y,Integer floor){
        public Candidate(int x,int y){this(x,y,null);}
    }
    public record Layout(Map<Integer,List<Zone>> spawns,Map<Integer,List<Zone>> bases,Map<Integer,Region> regions){
        public Layout {spawns=freeze(spawns);bases=freeze(bases);regions=Map.copyOf(regions);}
        private static Map<Integer,List<Zone>> freeze(Map<Integer,List<Zone>> source){var out=new HashMap<Integer,List<Zone>>();source.forEach((k,v)->out.put(k,List.copyOf(v)));return Map.copyOf(out);}
        public static Layout defaults(){return new Layout(Map.of(),Map.of(),Map.of(2,new Region(64,128,192,384),3,new Region(320,128,448,384)));}
    }
    private final Thread owner=Thread.currentThread();
    private final MutableWorld world;
    private final CollisionMap map;
    private final Layout layout;
    private final Map<Integer,List<Candidate>> candidates=new HashMap<>();
    private final Map<Integer,Position> anchors=new HashMap<>();
    public SpawnGeometry(MutableWorld world,Layout layout){this.world=Objects.requireNonNull(world);this.map=CollisionMap.of(world);this.layout=Objects.requireNonNull(layout);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Spawn geometry accessed outside owner");}
    public MutableWorld world(){owned();return world;}
    public List<Zone> zones(int team){owned();return layout.spawns.getOrDefault(team,List.of());}
    public Region region(int team){owned();return layout.regions.getOrDefault(team,new Region(192,192,320,320));}
    public int surface(int x,int y){owned();return world.surfaceZ(x,y);}
    public boolean solid(int x,int y,int z){owned();return map.solid(x,y,z);}
    public boolean clipbox(double x,double y,double z){
        owned();
        if(x<0||x>=512||y<0||y>=512)return true;if(z<0)return false;
        int sz=(int)Math.floor(z);if(sz==239)sz--;else if(sz>=240)return true;
        return solid((int)Math.floor(x),(int)Math.floor(y),sz);
    }
    public boolean bodyClear(Position p){
        owned();double[] offsets={0,-0.45,0.45};
        for(double dx:offsets)for(double dy:offsets)for(int h=0;h<=2;h++)if(clipbox(p.x()+dx,p.y()+dy,p.z()+h))return false;return true;
    }
    public boolean supported(Position p){
        owned();double r=0.45*0.75;double[][] probes={{0,0},{-r,0},{r,0},{0,-r},{0,r}};
        for(var d:probes)for(double settle:new double[]{0,0.25,0.5,0.75})if(clipbox(p.x()+d[0],p.y()+d[1],p.z()+2.25+settle))return true;return false;
    }
    public boolean safePosition(Position p,Integer team){
        owned();if(!finite(p)||p.x()<0||p.x()>=512||p.y()<0||p.y()>=512||p.z()<-240||p.z()>=240)return false;
        int x=(int)Math.floor(p.x()),y=(int)Math.floor(p.y());
        if(surface(x,y)>238){
            if(team==null||zones(team).stream().noneMatch(z->z.water()&&z.contains(x,y)))return false;
            return bodyClear(p)&&p.z()+2.25>=238-0.75;
        }
        return bodyClear(p)&&supported(p);
    }
    public boolean safePosition(Position p){return safePosition(p,null);}
    public static boolean finite(Position p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    public Position columnPosition(int x,int y){return new Position(x+0.5,y+0.5,surface(x,y)-2.75);}
    public boolean safeColumn(int x,int y,Zone zone,boolean rejectRoofs){
        owned();if(x<1||x>=511||y<1||y>=511)return false;int z=surface(x,y);
        if(z>238||(zone!=null&&!zone.containsSurface(z)))return false;
        int low=240,high=-1;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++){int h=surface(x+dx,y+dy);if(h>238)return false;low=Math.min(low,h);high=Math.max(high,h);}
        if(high-low>2)return false;
        for(int h=z+1;h<Math.min(240,z+9);h++)if(!solid(x,y,h))return false;
        if(rejectRoofs){
            var ring=new ArrayList<Integer>();
            for(int r:new int[]{8,16})for(int[] d:new int[][]{{-r,0},{r,0},{0,-r},{0,r},{-r,-r},{-r,r},{r,-r},{r,r}}){
                int rx=x+d[0],ry=y+d[1];if(rx<0||rx>=512||ry<0||ry>=512)continue;int h=surface(rx,ry);if(h<=238)ring.add(h);
            }
            if(!ring.isEmpty()){ring.sort(null);if(ring.get(ring.size()*3/4)-z>4)return false;}
        }
        return bodyClear(new Position(x+0.5,y+0.5,z-2.75));
    }
    public boolean safeColumn(int x,int y){return safeColumn(x,y,null,true);}
    public List<Integer> floors(int x,int y,Zone zone){
        owned();var result=new ArrayList<Integer>();Integer start=null;
        for(int z=Math.max(0,zone.top());z<240;z++){
            if(!solid(x,y,z)){if(start==null)start=z;continue;}
            if(start!=null&&start<=zone.bottom())result.add(z);start=null;if(z>=zone.bottom())break;
        }
        return List.copyOf(result);
    }
    public boolean safeFloor(int x,int y,int floor,Zone zone){
        owned();if(x<1||x>=511||y<1||y>=511||floor<=0||floor>=240||!solid(x,y,floor))return false;
        if(floor>238&&!zone.water())return false;int z=floor-1;
        if(z<zone.top()||solid(x,y,z))return false;
        while(z-1>=zone.top()&&!solid(x,y,z-1))z--;if(z>zone.bottom())return false;
        for(int h=1;h<=3;h++)if(solid(x,y,floor-h))return false;
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0){
            boolean support=false;for(int h=floor-3;h<Math.min(240,floor+3);h++)if(solid(x+dx,y+dy,h)){support=true;break;}if(!support)return false;
        }
        if(!bodyClear(new Position(x+0.5,y+0.5,floor-2.75)))return false;
        return floor==surface(x,y)||!sealedPocket(x,y,floor-1,64);
    }
    public boolean sealedPocket(int x,int y,int z,int limit){
        owned();var start=new Cube(x,y,z);var seen=new HashSet<Cube>();seen.add(start);var frontier=new ArrayDeque<Cube>();frontier.add(start);
        while(!frontier.isEmpty()){
            var c=frontier.removeLast();for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,-1},{0,0,1}}){
                var n=new Cube(c.x()+d[0],c.y()+d[1],c.z()+d[2]);if(seen.contains(n)||n.x()<0||n.x()>=512||n.y()<0||n.y()>=512||n.z()<0||n.z()>=240||solid(n.x(),n.y(),n.z()))continue;
                seen.add(n);if(seen.size()>limit)return false;frontier.add(n);
            }
        }
        return true;
    }
    public Position candidatePosition(Candidate candidate,List<Zone> zones){
        owned();int x=candidate.x,y=candidate.y,z;
        if(candidate.floor!=null){z=candidate.floor;if(zones.stream().noneMatch(zone->zone.contains(x,y)&&safeFloor(x,y,z,zone)))return null;}
        else{Zone zone=zones.stream().filter(v->v.contains(x,y)).findFirst().orElse(null);if(!safeColumn(x,y,zone,zone==null))return null;z=surface(x,y);}
        return new Position(x+0.5,y+0.5,z-2.75);
    }
    public List<Candidate> zoneCandidates(Zone zone){
        owned();var result=new ArrayList<Candidate>();
        for(int x=Math.max(1,zone.left());x<=Math.min(510,zone.right());x++)for(int y=Math.max(1,zone.front());y<=Math.min(510,zone.back());y++){
            boolean top=safeColumn(x,y,zone,false);if(top)result.add(new Candidate(x,y));int surface=surface(x,y);
            for(int floor:floors(x,y,zone)){if(floor==surface&&top)continue;if(safeFloor(x,y,floor,zone))result.add(new Candidate(x,y,floor));}
        }
        return List.copyOf(result);
    }
    public List<Candidate> authoredCandidates(int team){
        owned();var result=new ArrayList<Candidate>();var zones=zones(team);
        for(var zone:zones)if(zone.water())result.addAll(zoneCandidates(zone));if(!result.isEmpty())return List.copyOf(result);
        for(var zone:zones)result.addAll(zoneCandidates(zone));return List.copyOf(result);
    }
    public List<Candidate> fallbackCandidates(int team){
        owned();var strict=new ArrayList<Candidate>();var dry=new ArrayList<Candidate>();var r=region(team);
        for(int x=r.x0;x<=r.x1;x+=4)for(int y=r.y0;y<=r.y1;y+=4){if(safeColumn(x,y))strict.add(new Candidate(x,y));if(surface(x,y)<=238)dry.add(new Candidate(x,y));}
        return List.copyOf(strict.isEmpty()?dry:strict);
    }
    public List<Candidate> candidates(int team){
        owned();var cached=candidates.get(team);if(cached!=null&&!cached.isEmpty())return cached;
        var result=authoredCandidates(team);if(result.isEmpty())result=fallbackCandidates(team);candidates.put(team,result);return result;
    }
    public Position nearestSafe(double x,double y,int search){
        owned();if(x<-(double)Math.max(0,search)-1||y<-(double)Math.max(0,search)-1||x>512.0+Math.max(0,search)||y>512.0+Math.max(0,search))return null;
        int cx=(int)Math.floor(x),cy=(int)Math.floor(y);
        for(int radius=0;radius<=Math.max(0,search);radius++){
            var ring=new ArrayList<Candidate>();
            for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))==radius){int px=cx+dx,py=cy+dy;if(px>=1&&px<511&&py>=1&&py<511)ring.add(new Candidate(px,py));}
            ring.sort(Comparator.comparingDouble((Candidate c)->distance2(c.x+0.5,c.y+0.5,x,y)).thenComparingInt(Candidate::x).thenComparingInt(Candidate::y));
            for(var c:ring)if(safeColumn(c.x,c.y))return columnPosition(c.x,c.y);
        }
        return null;
    }
    public Position dryGround(double x,double y,int search){
        owned();int bx=(int)x,by=(int)y;
        for(int r=0;r<=search;r++)for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))==r){
            int cx=bx+dx,cy=by+dy;if(cx<0||cx>=512||cy<0||cy>=512)continue;int z=surface(cx,cy);if(z<=238)return new Position(cx+0.5,cy+0.5,z-2.25);
        }
        return new Position(x,y,surface(bx,by)-2.25);
    }
    public Candidate fallbackBase(int team,List<Candidate> choices){
        var r=region(team);double x=(r.x0+r.x1)/2.0,y=(r.y0+r.y1)/2.0;
        return choices.stream().min(Comparator.comparingDouble((Candidate c)->distance2(c.x,c.y,x,y)).thenComparingInt(Candidate::x).thenComparingInt(Candidate::y)).orElse(null);
    }
    public Position baseAnchor(int team){
        owned();var cached=anchors.get(team);if(cached!=null)return cached;var resolved=resolveBase(team);anchors.put(team,resolved);return resolved;
    }
    private Position resolveBase(int team){
        for(var zone:layout.bases.getOrDefault(team,List.of())){
            Candidate best=null;double distance=Double.POSITIVE_INFINITY;
            for(int x=Math.max(1,zone.left());x<=Math.min(510,zone.right());x++)for(int y=Math.max(1,zone.front());y<=Math.min(510,zone.back());y++)if(safeColumn(x,y,zone,false)){
                double d=distance2(x,y,zone.x,zone.y);if(d<distance){distance=d;best=new Candidate(x,y);}
            }
            if(best!=null)return dryGround(best.x,best.y,24);
        }
        if(!zones(team).isEmpty()){
            var zone=zones(team).getFirst();var candidates=authoredCandidates(team);var top=candidates.stream().filter(c->c.floor==null).toList();var pool=top.isEmpty()?candidates:top;
            var best=pool.stream().min(Comparator.comparingDouble(c->distance2(c.x,c.y,zone.x,zone.y))).orElse(null);
            if(best!=null){if(best.floor!=null&&best.floor<=238)return new Position(best.x+0.5,best.y+0.5,best.floor-2.25);return dryGround(best.x,best.y,24);}
        }
        var best=fallbackBase(team,candidates(team));if(best!=null)return dryGround(best.x,best.y,24);
        return dryGround(team==2?64:448,256,24);
    }
    public void prewarm(){owned();for(int team:new int[]{2,3}){candidates(team);baseAnchor(team);}}
    static double distance2(double x,double y,double bx,double by){double dx=x-bx,dy=y-by;return dx*dx+dy*dy;}
}
