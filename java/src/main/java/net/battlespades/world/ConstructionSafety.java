// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.*;
import java.util.function.*;
import net.battlespades.player.PlayerLife;
import net.battlespades.world.BlockDamageModel.Cell;

/** Bounded intent reservations for prefab footprints and friendly bot paths. Never mutates terrain. */
public final class ConstructionSafety {
    public record Reservation(long token,int owner,int team,Set<Cell> cells,double expires,boolean path){public Reservation{cells=Set.copyOf(cells);}}
    public record Decision(Long token,String reason){}
    private final DoubleSupplier clock;private final Supplier<List<PlayerLife>> players;private final MutableWorld world;private final Predicate<Cell> protectedCell;private final BiFunction<Integer,Integer,Set<Cell>> friendlyPaths;
    private final Map<Long,Reservation> reservations=new LinkedHashMap<>();private final Map<Integer,Long> paths=new HashMap<>();private long next=1;
    public ConstructionSafety(DoubleSupplier clock,Supplier<List<PlayerLife>> players,MutableWorld world,Predicate<Cell> protectedCell,BiFunction<Integer,Integer,Set<Cell>> friendlyPaths){this.clock=Objects.requireNonNull(clock);this.players=Objects.requireNonNull(players);this.world=Objects.requireNonNull(world);this.protectedCell=Objects.requireNonNull(protectedCell);this.friendlyPaths=Objects.requireNonNull(friendlyPaths);}
    private void expire(){for(var r:new ArrayList<>(reservations.values()))if(r.expires<=clock.getAsDouble())release(r.token);}
    public int active(){expire();return reservations.size();}
    public void release(Long token){if(token==null)return;var r=reservations.remove(token);if(r!=null&&r.path&&Objects.equals(paths.get(r.owner),token))paths.remove(r.owner);}
    public void forget(int owner){for(var r:new ArrayList<>(reservations.values()))if(r.owner==owner)release(r.token);}
    public void clear(){reservations.clear();paths.clear();}
    private static Set<Cell> normalize(Collection<Cell> cells){var result=new HashSet<Cell>();for(var c:cells){if(c.x()<0||c.x()>=512||c.y()<0||c.y()>=512||c.z()<1||c.z()>238)return Set.of();result.add(c);if(result.size()>2048)return Set.of();}return Set.copyOf(result);}
    public Long path(int owner,int team,Collection<Cell> cells,double ttl){release(paths.remove(owner));var normalized=normalize(cells);if(normalized.isEmpty())return null;var token=store(owner,team,normalized,Math.max(.05,ttl),true);if(token!=null)paths.put(owner,token);return token;}
    private Long store(int owner,int team,Set<Cell> cells,double ttl,boolean path){expire();if(reservations.size()>=256)return null;long token=next++;reservations.put(token,new Reservation(token,owner,team,cells,clock.getAsDouble()+ttl,path));return token;}
    public Decision reserve(int owner,int team,Collection<Cell> cells){var proposed=normalize(cells);String reason=reason(owner,team,proposed);if(!reason.isEmpty())return new Decision(null,reason);var token=store(owner,team,proposed,1.5,false);return new Decision(token,token==null?"reservation capacity reached":"");}
    public String reason(int owner,int team,Collection<Cell> cells){var proposed=normalize(cells);if(proposed.isEmpty())return "empty or oversized footprint";expire();if(overlaps(proposed))return "player body overlap";if(proposed.stream().anyMatch(protectedCell))return "spawn or objective zone";for(var r:reservations.values())if(r.owner!=owner&&(!r.path||r.team==team)&&!Collections.disjoint(proposed,r.cells))return "reserved construction or friendly path";if(!Collections.disjoint(proposed,friendlyPaths.apply(team,owner)))return "reserved construction or friendly path";if(seals(team,proposed))return "sole friendly exit";return "";}
    public boolean overlaps(Collection<Cell> cells){var proposed=new HashSet<>(cells);for(var p:players.get()){if(!p.alive()||!p.spawned())continue;var at=p.position();for(int x=(int)Math.floor(at.x()-.45);x<=(int)Math.floor(at.x()+.45);x++)for(int y=(int)Math.floor(at.y()-.45);y<=(int)Math.floor(at.y()+.45);y++)for(int z=(int)Math.floor(at.z());z<=(int)Math.floor(at.z()+2);z++)if(proposed.contains(new Cell(x,y,z)))return true;}return false;}
    private boolean seals(int team,Set<Cell> proposed){int minX=proposed.stream().mapToInt(Cell::x).min().orElseThrow()-2,maxX=proposed.stream().mapToInt(Cell::x).max().orElseThrow()+2,minY=proposed.stream().mapToInt(Cell::y).min().orElseThrow()-2,maxY=proposed.stream().mapToInt(Cell::y).max().orElseThrow()+2;for(var p:players.get()){if(p.team()!=team||!p.alive()||!p.spawned())continue;var at=p.position();int x=(int)Math.floor(at.x()),y=(int)Math.floor(at.y()),z=(int)Math.floor(at.z());if(x<minX||x>maxX||y<minY||y>maxY)continue;int blocked=0;for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){boolean hit=false;for(int dz=0;dz<2;dz++)hit|=proposed.contains(new Cell(x+d[0],y+d[1],z+dz))||world.solid(x+d[0],y+d[1],z+dz);if(hit)blocked++;}if(blocked==4)return true;}return false;}
}
