// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.function.Consumer;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import org.tomlj.TomlTable;

/** Source escape-watch holds, bounded sealed-pocket scan, two-clean-pass hysteresis and marker replay. */
public final class HostedEscapeWatch {
    private static final int[][] NEIGHBORS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private record Cell(int x,int y,int z){}
    private static final class Track {final PlayerLife player;String condition,flag;double since;int clean;boolean modeMarked;Track(PlayerLife p){player=p;}}
    private final StandardMatchDomain match;private final ApplicationHost host;private final boolean enabled;private final double interval,sky,embedded,entombed;private double next;
    private final Map<Integer,Track> tracks=new LinkedHashMap<>();
    public HostedEscapeWatch(StandardMatchDomain match,ApplicationHost host,TomlTable raw){this.match=match;this.host=host;enabled=CtfMode.Settings.flag(raw,"objectives.escape_watch_enabled",true);interval=number(raw,"interval",1,.1,60);sky=number(raw,"sky_seconds",5,0,600);embedded=number(raw,"embedded_seconds",3,0,600);entombed=number(raw,"entomb_seconds",5,0,600);}
    private static double number(TomlTable raw,String key,double value,double min,double max){return Math.max(min,Math.min(max,CtfMode.Settings.number(raw,"objectives.escape_watch_"+key,value)));}
    private boolean modeMarked(PlayerLife p){return match.ctf().map(c->c.exposed(p)).orElse(false);}
    private boolean objective(PlayerLife p){return match.carry().map(c->c.carrying(p)).orElseGet(()->match.zones().map(z->z.objective(p)).orElseGet(()->match.ctf().map(c->c.carrying(p)).orElse(false)));}
    private void marker(PlayerLife p,boolean visible){host.broadcastKnown(MatchPackets.ChangePlayer.marker(p.id(),visible),Delivery.RELIABLE,p.id());}
    private boolean solid(int x,int y,int z){if(x<0||x>=512||y<0||y>=512||z>=240)return true;return z>=0&&match.world().solid(x,y,z);}
    private boolean sealed(Vector p){
        int x=(int)Math.floor(p.x()),y=(int)Math.floor(p.y());Cell start=null;for(int dz=0;dz<3;dz++){int z=(int)Math.floor(p.z()+dz);if(!solid(x,y,z)){start=new Cell(x,y,z);break;}}if(start==null)return true;
        var seen=new HashSet<Cell>();var pending=new ArrayDeque<Cell>();seen.add(start);pending.add(start);var tops=new HashMap<Long,Integer>();
        while(!pending.isEmpty()){if(seen.size()>256)return false;var c=pending.removeLast();if(c.z<=0)return false;long key=((long)c.x<<32)|(c.y&0xffffffffL);int top=tops.computeIfAbsent(key,k->match.world().surfaceZ(c.x,c.y));if(c.z<top)return false;
            for(var d:NEIGHBORS){var n=new Cell(c.x+d[0],c.y+d[1],c.z+d[2]);if(seen.contains(n)||n.x<0||n.x>=512||n.y<0||n.y>=512||n.z>=239)continue;if(n.z<0)return false;if(solid(n.x,n.y,n.z))continue;seen.add(n);pending.add(n);}}
        return true;
    }
    public String classify(PlayerLife player){var p=player.position();if(!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z())||p.x()<0||p.x()>=512||p.y()<0||p.y()>=512)return "out_of_bounds";if(p.z()+(player.body().state().crouch()?1.35:2.25)>241)return "below_floor";if(p.z()<0)return "sky";int x=(int)Math.floor(p.x()),y=(int)Math.floor(p.y());if(solid(x,y,(int)Math.floor(p.z()))&&solid(x,y,(int)Math.floor(p.z()+1)))return "embedded";return objective(player)&&sealed(p)?"entombed":null;}
    public boolean flagged(PlayerLife p){var t=tracks.get(p.id());return t!=null&&t.player==p&&t.flag!=null;}
    public void tick(){if(!enabled||match.monotonic()<next)return;double now=match.monotonic();next=now+interval;var players=match.lives().players();tracks.values().removeIf(t->players.stream().noneMatch(p->p==t.player));
        for(var p:players){var t=tracks.get(p.id());if(!p.alive()||!p.spawned()||p.team()!=2&&p.team()!=3){forget(p);continue;}if(t==null){t=new Track(p);tracks.put(p.id(),t);}String condition=classify(p);boolean marked=modeMarked(p);
            if(condition==null){t.condition=null;if(t.flag!=null&&++t.clean>=2){t.flag=null;if(!marked)marker(p,false);}t.modeMarked=marked;continue;}
            t.clean=0;if(!condition.equals(t.condition)){t.condition=condition;t.since=now;}double hold=switch(condition){case "sky"->sky;case "embedded"->embedded;case "entombed"->entombed;default->0;};
            if(t.flag==null&&now-t.since>=hold){t.flag=condition;if(!marked)marker(p,true);}else if(t.flag!=null){if(t.modeMarked&&!marked)marker(p,true);else t.flag=condition;}t.modeMarked=marked;
        }
    }
    public void reveal(Consumer<GamePacket> send){for(var t:tracks.values())if(t.flag!=null&&match.lives().players().stream().anyMatch(p->p==t.player))send.accept(MatchPackets.ChangePlayer.marker(t.player.id(),true));}
    public void forget(PlayerLife p){var t=tracks.get(p.id());if(t==null||t.player!=p)return;tracks.remove(p.id());if(t.flag!=null&&!modeMarked(p))marker(p,false);}
}
