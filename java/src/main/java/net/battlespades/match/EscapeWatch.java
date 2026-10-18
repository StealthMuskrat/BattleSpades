// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.match;
import java.util.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.MatchPackets.ChangePlayer;
import net.battlespades.world.MutableWorld;
/** Original low-frequency escape detector and marker ownership/replay state. */
public final class EscapeWatch {
    public record Settings(boolean enabled,double interval,double skySeconds,double embeddedSeconds,double entombSeconds){public static Settings defaults(){return new Settings(true,1,5,3,5);}}
    public interface Mode {boolean enabled();boolean marks(PlayerLife player);boolean objective(PlayerLife player);}
    public record Snapshot(PlayerLife player,String condition,double since,String flagged,int cleanChecks,boolean modeMarked){}
    private static final class Track {final PlayerLife player;String condition,flagged;double since;int cleanChecks;boolean modeMarked;Track(PlayerLife p){player=p;}}
    private record Cell(int x,int y,int z){}
    private record Column(int x,int y){}
    private static final int[][] NEIGHBORS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private final Thread owner=Thread.currentThread();
    private final MutableWorld world;
    private final Supplier<Settings> settings;
    private final Supplier<Map<Integer,PlayerLife>> players;
    private final Supplier<Optional<Mode>> mode;
    private final Consumer<ChangePlayer> broadcast;
    private Object modeKey;private boolean initialized;private double nextCheck;
    private final Map<Integer,Track> tracks=new LinkedHashMap<>();
    public EscapeWatch(MutableWorld world,Supplier<Settings> settings,Supplier<Map<Integer,PlayerLife>> players,Supplier<Optional<Mode>> mode,Consumer<ChangePlayer> broadcast){this.world=world;this.settings=Objects.requireNonNull(settings);this.players=Objects.requireNonNull(players);this.mode=Objects.requireNonNull(mode);this.broadcast=Objects.requireNonNull(broadcast);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Escape watch outside owner");}
    private boolean connected(PlayerLife p){return players.get().get(p.id())==p;}
    private void marker(PlayerLife p,boolean visible){if(connected(p))broadcast.accept(ChangePlayer.marker(p.id(),visible));}
    private static boolean marks(Mode m,PlayerLife p){try{return m!=null&&m.marks(p);}catch(RuntimeException ignored){return false;}}
    private static boolean objective(Mode m,PlayerLife p){try{return m.objective(p);}catch(RuntimeException ignored){return false;}}
    public boolean initialized(){owned();return initialized;}
    public double nextCheck(){owned();return nextCheck;}
    public List<Snapshot> snapshots(){owned();return tracks.values().stream().map(t->new Snapshot(t.player,t.condition,t.since,t.flagged,t.cleanChecks,t.modeMarked)).toList();}
    public String flagged(PlayerLife p){owned();var t=tracks.get(p.id());return t!=null&&t.player==p?t.flagged:null;}
    public void reveal(Consumer<ChangePlayer> send){owned();if(!initialized)return;for(var t:List.copyOf(tracks.values()))if(t.flagged!=null&&connected(t.player))send.accept(ChangePlayer.marker(t.player.id(),true));}
    public void forget(PlayerLife p){owned();if(!initialized)return;var t=tracks.remove(p.id());if(t==null||t.player!=p||t.flagged==null)return;if(!marks(mode.get().orElse(null),p))marker(p,false);}
    private boolean solid(int x,int y,int z){if(x<0||x>=512||y<0||y>=512)return true;if(z<0)return false;if(z>=240)return true;return world.solid(new MutableWorld.Cell(x,y,z));}
    private static int floor(double v){double value=Math.floor(v);if(!Double.isFinite(value)||value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new ArithmeticException("Cell outside signed int range");return (int)value;}
    public boolean embedded(Vector at){owned();int x=floor(at.x()),y=floor(at.y());return solid(x,y,floor(at.z()))&&solid(x,y,floor(at.z()+1));}
    public boolean sealed(Vector at,int budget){
        owned();int x=floor(at.x()),y=floor(at.y());Cell start=null;for(double d:new double[]{0,1,2}){int z=floor(at.z()+d);if(!solid(x,y,z)){start=new Cell(x,y,z);break;}}if(start==null)return true;
        var tops=new HashMap<Column,Integer>();var seen=new HashSet<Cell>();var queue=new ArrayDeque<Cell>();seen.add(start);queue.addLast(start);
        while(!queue.isEmpty()){if(seen.size()>budget)return false;var c=queue.removeLast();if(c.z<=0)return false;int top=tops.computeIfAbsent(new Column(c.x,c.y),key->world.surfaceZ(key.x,key.y));if(c.z<top)return false;
            for(var d:NEIGHBORS){int nx=c.x+d[0],ny=c.y+d[1],nz=c.z+d[2];var next=new Cell(nx,ny,nz);if(seen.contains(next))continue;if(nx<0||nx>=512||ny<0||ny>=512||nz>=239)continue;if(nz<0)return false;if(world.solid(new MutableWorld.Cell(nx,ny,nz)))continue;seen.add(next);queue.addLast(next);}
        }return true;
    }
    public String classify(Vector at,boolean crouched,boolean objective){
        owned();double x=at.x(),y=at.y(),z=at.z();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||x<0||x>=512||y<0||y>=512)return "out_of_bounds";if(z+(crouched?1.35:2.25)>241)return "below_floor";if(z<0)return "sky";if(world==null)return null;
        try{if(embedded(at))return "embedded";if(objective&&sealed(at,256))return "entombed";}catch(RuntimeException ignored){/* Source isolates terrain observer failures. */}return null;
    }
    public void tick(Optional<Mode> override,double now,boolean force){
        owned();var config=settings.get();if(!config.enabled)return;Mode active=override.orElseGet(()->mode.get().orElse(null));if(active==null||!active.enabled())return;Object key=mode.get().orElse(null);
        if(!initialized||modeKey!=key){if(initialized)for(var t:List.copyOf(tracks.values()))if(t.flagged!=null)marker(t.player,false);tracks.clear();nextCheck=0;modeKey=key;initialized=true;}
        if(!force&&now<nextCheck)return;double interval=config.interval>.1?config.interval:.1;nextCheck=now+interval;var roster=players.get();tracks.entrySet().removeIf(e->roster.get(e.getKey())!=e.getValue().player);
        for(var p:List.copyOf(roster.values())){
            var t=tracks.get(p.id());if(!p.alive()||!p.spawned()||(p.team()!=2&&p.team()!=3)){if(t!=null){if(t.flagged!=null&&!marks(active,p))marker(p,false);tracks.remove(p.id());}continue;}if(t==null){t=new Track(p);tracks.put(p.id(),t);}
            String condition=classify(p.position(),(p.motion().snapshot().movement()&32)!=0,objective(active,p));boolean marked=marks(active,p);
            if(condition==null){t.condition=null;if(t.flagged!=null){t.cleanChecks++;if(t.cleanChecks>=2){t.flagged=null;if(!marked)marker(p,false);}}t.modeMarked=marked;continue;}
            t.cleanChecks=0;if(!condition.equals(t.condition)){t.condition=condition;t.since=now;}double hold=switch(condition){case "sky"->config.skySeconds;case "embedded"->config.embeddedSeconds;case "entombed"->config.entombSeconds;default->0;};
            if(t.flagged==null&&now-t.since>=hold){t.flagged=condition;if(!marked)marker(p,true);}else if(t.flagged!=null&&t.modeMarked&&!marked){marker(p,true);}else if(t.flagged!=null)t.flagged=condition;t.modeMarked=marked;
        }
    }
}
