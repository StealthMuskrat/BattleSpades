// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.Supplier;
import net.battlespades.player.SpawnGeometry.Position;
import net.battlespades.player.SpawnGeometry.Candidate;

/** Owner-confined source threat selection and bounded round-lifecycle rescue. */
public final class SpawnResolver {
    public record Player(int id,int team,boolean alive,boolean spawned,Position position,Position eye){
        public Position view(){return eye==null?position:eye;}
    }
    public record Memory(double stamp,int team,double x,double y){}
    public record Scored(double score,Position position){}
    private record Enemy(double distance,Position eye){}
    private record Nearby(Candidate candidate,double distance){}
    private final Thread owner=Thread.currentThread();
    private final SpawnGeometry geometry;
    private final SourceRandom global;
    private Object mode;
    private final ArrayDeque<Memory> deaths=new ArrayDeque<>(),spawns=new ArrayDeque<>();
    private final Map<Integer,List<Candidate>> regions=new HashMap<>();
    private List<Candidate> dryGrid;
    private double gridStamp;
    private long gridVersion;
    public SpawnResolver(SpawnGeometry geometry,Object mode,SourceRandom global){this.geometry=Objects.requireNonNull(geometry);this.mode=mode;this.global=Objects.requireNonNull(global);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Spawn resolver accessed outside owner");}
    /** A different concrete mode identity discards all per-mode/map intel, as the source does. */
    public void mode(Object identity){owned();if(mode!=identity){mode=identity;deaths.clear();spawns.clear();regions.clear();dryGrid=null;}}
    public void recordDeath(Player player,double now){owned();if(playable(player.team)){append(deaths,new Memory(now,player.team,player.position.x(),player.position.y()),128);}}
    public List<Memory> deaths(){owned();return List.copyOf(deaths);}
    public List<Memory> spawns(){owned();return List.copyOf(spawns);}
    private static void append(ArrayDeque<Memory> queue,Memory value,int limit){if(queue.size()==limit)queue.removeFirst();queue.addLast(value);}
    private static boolean playable(int team){return team==2||team==3;}
    public Position choose(Player player,List<Player> players,double now){return choose(player,players,now,global);}
    public Position choose(Player player,List<Player> players,double now,SourceRandom random){
        owned();int team=player.team;if(!playable(team))return null;
        List<Candidate> columns;try{columns=geometry.candidates(team);}catch(RuntimeException e){return null;}
        if(columns.isEmpty())return emergency(team,null,now);
        var enemies=new ArrayList<Position>();var allies=new ArrayList<Position>();
        for(var other:players)if(other.id!=player.id&&other.alive&&other.spawned&&playable(other.team))(other.team==team?allies:enemies).add(other.view());
        var scored=best(columns,geometry.zones(team),team,enemies,allies,now,random);
        if(scored.isEmpty()&&columns.size()>20)scored=best(columns,geometry.zones(team),team,enemies,allies,now,random);
        Scored choice=pick(scored,random);
        if(choice==null||(!enemies.isEmpty()&&choice.score<-3)){
            var region=regions.computeIfAbsent(team,t->geometry.fallbackCandidates(t));
            if(!region.isEmpty()){
                var wider=pick(best(region,List.of(),team,enemies,allies,now,random),random);
                if(wider!=null&&(choice==null||wider.score>choice.score+1.0))choice=wider;
            }
        }
        Position position=choice==null?emergency(team,null,now):choice.position;
        append(spawns,new Memory(now,team,position.x(),position.y()),64);return position;
    }
    private List<Scored> best(List<Candidate> columns,List<SpawnGeometry.Zone> zones,int team,List<Position> enemies,List<Position> allies,double now,SourceRandom random){
        var scored=new ArrayList<Scored>();for(var candidate:random.sample(columns,Math.min(columns.size(),20))){
            var p=geometry.candidatePosition(candidate,zones);if(p!=null)scored.add(new Scored(score(p,team,enemies,allies,now,random),p));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());return scored;
    }
    public double score(Position position,int team,List<Position> enemies,List<Position> allies,double now,SourceRandom random){
        owned();var distances=new ArrayList<Enemy>();for(var enemy:enemies)distances.add(new Enemy(SpawnMath.hypot(enemy.x()-position.x(),enemy.y()-position.y()),enemy));
        distances.sort(Comparator.comparingDouble(Enemy::distance).thenComparingDouble(e->e.eye.x()).thenComparingDouble(e->e.eye.y()).thenComparingDouble(e->e.eye.z()));
        double nearest=distances.isEmpty()?64:distances.getFirst().distance,score=3.0*Math.min(nearest,64)/64;
        if(nearest<14)score-=6;int checks=0;
        for(var enemy:distances){if(enemy.distance>72||checks>=6)break;checks++;if(segmentClear(enemy.eye,new Position(position.x(),position.y(),position.z()-0.5)))score-=4;}
        for(var death:deaths)if(death.team==team&&now-death.stamp<=20&&SpawnMath.hypot(death.x-position.x(),death.y-position.y())<=10)score-=1.5;
        for(var spawn:spawns)if(now-spawn.stamp<=6&&SpawnMath.hypot(spawn.x-position.x(),spawn.y-position.y())<=3)score-=2;
        for(var ally:allies)if(SpawnMath.hypot(ally.x()-position.x(),ally.y()-position.y())<=24){score+=0.5;break;}
        return score+random.uniform(0,0.6);
    }
    private static Scored pick(List<Scored> scores,SourceRandom random){
        int n=Math.min(scores.size(),3);if(n==0)return null;double floor=Double.POSITIVE_INFINITY;
        for(int i=0;i<n;i++)floor=Math.min(floor,scores.get(i).score);double[] weights=new double[n];
        for(int i=0;i<n;i++)weights[i]=scores.get(i).score-floor+0.5;return scores.get(random.weighted(weights));
    }
    /** Source combat_runtime.segment_clear, whose endpoint shrink differs from native hitscan. */
    public boolean segmentClear(Position start,Position end){
        owned();if(!SpawnGeometry.finite(start)||!SpawnGeometry.finite(end))return false;
        double[] delta={end.x()-start.x(),end.y()-start.y(),end.z()-start.z()};
        double length=Math.sqrt(SpawnMath.sumSquares(delta)),usable=length-0.3-0.3;if(usable<=1e-6)return true;
        double[] origin={start.x(),start.y(),start.z()},unit=new double[3],next=new double[3],increment=new double[3];int[] cell=new int[3],last=new int[3],step=new int[3];
        for(int i=0;i<3;i++){
            unit[i]=delta[i]/length;origin[i]+=unit[i]*0.3;cell[i]=(int)Math.floor(origin[i]);last[i]=(int)Math.floor(origin[i]+unit[i]*usable);next[i]=increment[i]=Double.POSITIVE_INFINITY;
            if(unit[i]>1e-12){step[i]=1;next[i]=(cell[i]+1-origin[i])/unit[i];increment[i]=1.0/unit[i];}
            else if(unit[i]<-1e-12){step[i]=-1;next[i]=(origin[i]-cell[i])/-unit[i];increment[i]=-1.0/unit[i];}
        }
        for(int i=0;i<(int)(usable*3.0)+8;i++){
            if(geometry.solid(cell[0],cell[1],cell[2]))return false;if(Arrays.equals(cell,last))return true;
            int axis=next[0]<=next[1]&&next[0]<=next[2]?0:next[1]<=next[2]?1:2;if(next[axis]>usable)return true;cell[axis]+=step[axis];next[axis]+=increment[axis];
        }
        return true;
    }
    private List<Candidate> grid(double now){
        long version=geometry.world().topologyVersion();
        if(dryGrid!=null&&(version==gridVersion||now-gridStamp<15))return dryGrid;
        var result=new ArrayList<Candidate>();for(int x=4;x<511;x+=8)for(int y=4;y<511;y+=8)if(geometry.surface(x,y)<=238)result.add(new Candidate(x,y));
        dryGrid=List.copyOf(result);gridStamp=now;gridVersion=version;return dryGrid;
    }
    public Position waterFallback(double x,double y){
        owned();int cx=Math.min(Math.max((int)Math.floor(x),1),510),cy=Math.min(Math.max((int)Math.floor(y),1),510);
        double z=geometry.surface(cx,cy)-2.75;for(int i=0;i<240;i++){if(geometry.bodyClear(new Position(cx+0.5,cy+0.5,z)))break;z-=1;}
        return new Position(cx+0.5,cy+0.5,z);
    }
    public Position emergency(int team,Position near,double now){
        owned();var anchor=geometry.baseAnchor(team);double x=near!=null&&Double.isFinite(near.x())&&Double.isFinite(near.y())?near.x():anchor.x(),y=near!=null&&Double.isFinite(near.x())&&Double.isFinite(near.y())?near.y():anchor.y();
        var ranked=new ArrayList<Nearby>();for(var c:grid(now))ranked.add(new Nearby(c,emergencyDistance(c.x()+0.5,c.y()+0.5,x,y)));
        ranked.sort(Comparator.comparingDouble(Nearby::distance));var ordered=ranked.stream().map(Nearby::candidate).toList();
        var region=geometry.region(team);var side=ordered.stream().filter(c->region.contains(c.x(),c.y())).toList();
        for(int pass=playable(team)?0:1;pass<3;pass++){
            var columns=pass==0?side:ordered;var found=new ArrayList<Position>();
            for(int i=0;i<Math.min(96,columns.size());i++){
                var c=columns.get(i);if(pass<2&&!geometry.safeColumn(c.x(),c.y()))continue;var p=geometry.columnPosition(c.x(),c.y());
                if(pass==2&&!geometry.safePosition(p))continue;found.add(p);if(found.size()==3)break;
            }
            if(!found.isEmpty())return global.choice(found);
        }
        return waterFallback(anchor.x(),anchor.y());
    }
    public Position rescue(int team,Position candidate,double now){
        owned();if(candidate!=null&&playable(team)&&geometry.safePosition(candidate,team))return candidate;
        boolean finite=candidate!=null&&Double.isFinite(candidate.x())&&Double.isFinite(candidate.y());
        if(finite){var local=geometry.nearestSafe(candidate.x(),candidate.y(),6);if(local!=null)return local;}
        return emergency(team,finite?candidate:null,now);
    }
    /** RoundLifecycle.resolve_player_spawn: invalid/raising mode proposals take the bounded rescue path. */
    public Position resolve(int team,Supplier<Position> modeProposal,double now){
        owned();Position candidate;try{candidate=modeProposal.get();}catch(RuntimeException e){candidate=null;}
        if(geometry.safePosition(candidate))return candidate;
        try{return rescue(team,candidate,now);}catch(RuntimeException e){return emergency(team,null,now);}
    }
    private static double emergencyDistance(double x,double y,double ox,double oy){
        double dx=x-ox,dy=y-oy,xx=dx*dx,yy=dy*dy;
        // Python's float ** 2 raises on finite overflow; RoundLifecycle retries at the base.
        if((Double.isFinite(dx)&&Double.isInfinite(xx))||(Double.isFinite(dy)&&Double.isInfinite(yy)))throw new ArithmeticException("Spawn distance overflow");
        return xx+yy;
    }
}
