// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.*;
import net.battlespades.combat.CombatGeometry;
import net.battlespades.entities.PickupWorld;
import net.battlespades.physics.CollisionMap;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.world.MutableWorld;

/** Original objective LOS, buried/sealed/floating recovery, AFK presence and protection policy. */
public final class ObjectiveGuard {
    public record Settings(double entombSeconds,double afkSeconds,boolean requiresLos,boolean endsProtection){
        public static Settings defaults(){return new Settings(5,60,true,true);}
    }
    private record Cell(int x,int y,int z){}
    private record Column(int x,int y){}
    private record Idle(PlayerLife player,Vector position,Vector orientation,double since){}
    public static final class TrapTimer<K> {
        private final Map<K,Double> since=new HashMap<>();
        public boolean due(K key,boolean trapped,double now,double seconds){if(!trapped){since.remove(key);return false;}double start=since.computeIfAbsent(key,k->now);if(now-start>=seconds){since.remove(key);return true;}return false;}
        public void clear(){since.clear();}
    }
    private static final int[][] NEIGHBORS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final Thread owner=Thread.currentThread();
    private final MutableWorld world;
    private final Settings settings;
    private final DoubleSupplier monotonic;
    private final Predicate<PlayerLife> flagged;
    private final Map<Integer,Idle> idle=new HashMap<>();
    public ObjectiveGuard(MutableWorld world,Settings settings,DoubleSupplier monotonic,Predicate<PlayerLife> flagged){this.world=world;this.settings=Objects.requireNonNull(settings);this.monotonic=Objects.requireNonNull(monotonic);this.flagged=Objects.requireNonNull(flagged);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Objective guard outside owner");}
    public double entombSeconds(){owned();return Double.isNaN(settings.entombSeconds)?0:Math.max(0,settings.entombSeconds);}
    public boolean pickupVisible(PlayerLife player,Vector objective,boolean playerSpace,double lift){
        owned();if(!settings.requiresLos||world==null)return true;if(objective==null)return true;
        var p=player.position();var target=new Vector(objective.x(),objective.y(),objective.z()+(playerSpace?2.25:0)-lift);var raw=CollisionMap.of(world);
        for(double z:new double[]{p.z(),p.z()+1,p.z()+2.25-.4})if(CombatGeometry.segmentClear(raw::solid,new Vector(p.x(),p.y(),z),target,.05,.05,Set.of()))return true;
        return false;
    }
    private boolean solid(int x,int y,int z){if(x<0||x>=512||y<0||y>=512)return true;if(z<0)return false;if(z>=240)return true;return world.solid(new MutableWorld.Cell(x,y,z));}
    private int nativeZ(int x,int y,int start){if(x<0||x>=512||y<0||y>=512)return 239;int top=world.surfaceZ(x,y);if(start<=0)return top;if(start>=240)return 239;for(int z=start;z<240;z++)if(solid(x,y,z))return z;return top;}
    public String trapped(Vector position,boolean playerSpace){
        owned();if(world==null||!CombatGeometry.finite(position))return null;
        try {
            double surface=position.z()+(playerSpace?2.25:0);int x=(int)Math.floor(position.x()),y=(int)Math.floor(position.y()),support=(int)Math.floor(surface+1e-6),cell=support-1;
            if(solid(x,y,cell))return "buried";
            if(support<239&&!solid(x,y,support)&&!solid(x,y,support+1))return "floating";
            var start=new Cell(x,y,cell);var seen=new HashSet<Cell>();seen.add(start);var queue=new ArrayDeque<Cell>();queue.addLast(start);var tops=new HashMap<Column,Integer>();
            while(!queue.isEmpty()){
                if(seen.size()>48)return null;var current=queue.removeLast();int top=tops.computeIfAbsent(new Column(current.x,current.y),c->nativeZ(c.x,c.y,0));
                if(current.z<top||current.z<=0)return null;
                for(var d:NEIGHBORS){var next=new Cell(current.x+d[0],current.y+d[1],current.z+d[2]);if(seen.contains(next)||solid(next.x,next.y,next.z))continue;seen.add(next);queue.addLast(next);}
            }
            return "sealed";
        }catch(RuntimeException error){return null;}
    }
    public Vector resettle(Vector position,boolean playerSpace){
        owned();Objects.requireNonNull(world);if(!CombatGeometry.finite(position))throw new IllegalArgumentException("Nonfinite objective position");
        double x=position.x(),y=position.y(),surface=position.z()+(playerSpace?2.25:0);int cx=(int)Math.floor(x),cy=(int)Math.floor(y),support=(int)Math.floor(surface+1e-6);
        int top=solid(cx,cy,support)?nativeZ(cx,cy,0):nativeZ(cx,cy,support);double newSurface=top;
        if(top>238){var anchor=new PickupWorld(world).drySurfaceAnchor(x,y,24);x=anchor.x();y=anchor.y();newSurface=anchor.z();}
        return new Vector(x,y,newSurface-(playerSpace?2.25:0));
    }
    /** CPython's round(binary64, ndigits), including halfway values and signed zero. */
    public static double rounded(double value,int digits){if(!Double.isFinite(value))return value;double result=new BigDecimal(value).setScale(digits,RoundingMode.HALF_EVEN).doubleValue();return result==0?Math.copySign(0,value):result;}
    private static Vector rounded(Vector value,int digits){return new Vector(rounded(value.x(),digits),rounded(value.y(),digits),rounded(value.z(),digits));}
    private static boolean same(Vector a,Vector b){return a.x()==b.x()&&a.y()==b.y()&&a.z()==b.z();}
    public double idleSeconds(PlayerLife player,double now){
        owned();try{int id=player.id();var position=rounded(player.position(),2);var orientation=rounded(player.aim(),3);var before=idle.get(id);
            if(before==null||before.player!=player||!same(before.position,position)||!same(before.orientation,orientation)){idle.put(id,new Idle(player,position,orientation,now));return 0;}return now-before.since;
        }catch(RuntimeException error){return 0;}
    }
    public boolean presenceEligible(PlayerLife player,double now){owned();if(settings.afkSeconds>0&&idleSeconds(player,now)>=settings.afkSeconds)return false;try{if(flagged.test(player))return false;}catch(RuntimeException ignored){/* Original escape-watch observer failures do not reject presence. */}return true;}
    public void endProtection(PlayerLife player){owned();if(settings.endsProtection)try{player.endProtection(monotonic.getAsDouble());}catch(RuntimeException ignored){/* Source optional protection callback isolates failures. */}}
}
