// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.function.*;
import net.battlespades.player.SpawnGeometry.Position;
import net.battlespades.physics.PlayerPhysics.Vector;

/** BaseMode smart spawns, WorldManager's plain fallback, and RoundLifecycle's final rescue. */
public final class StandardSpawns {
    private final SpawnGeometry geometry;
    private final SpawnResolver resolver;
    private final SourceRandom random;
    private final Supplier<List<PlayerLife>> players;
    private final DoubleSupplier monotonic;
    public StandardSpawns(SpawnGeometry geometry,Object mode,SourceRandom random,Supplier<List<PlayerLife>> players,DoubleSupplier monotonic){
        this.geometry=Objects.requireNonNull(geometry);this.random=Objects.requireNonNull(random);this.players=Objects.requireNonNull(players);this.monotonic=Objects.requireNonNull(monotonic);resolver=new SpawnResolver(geometry,Objects.requireNonNull(mode),random);
    }
    private static Position position(Vector p){return new Position(p.x(),p.y(),p.z());}
    private static SpawnResolver.Player view(PlayerLife p){return new SpawnResolver.Player(p.id(),p.team(),p.alive(),p.spawned(),position(p.position()),position(p.position()));}
    public Vector spawn(PlayerLife player){double now=monotonic.getAsDouble();return resolver.resolve(player.team(),()->proposal(player,now),now).toPhysics();}
    private Position proposal(PlayerLife player,double now){
        Position smart;try{smart=resolver.choose(view(player),players.get().stream().map(StandardSpawns::view).toList(),now);}catch(RuntimeException failure){smart=null;}
        return smart==null?plain(player.team()):smart;
    }
    private Position plain(int team){
        var region=geometry.region(team);
        if(team!=2&&team!=3){
            // Native VXL get_random_pos uses an exclusive upper bound, 64 dry draws, then y/x scan.
            int x1=Math.max(region.x0(),region.x1()-1),y1=Math.max(region.y0(),region.y1()-1);
            for(int i=0;i<64;i++){int x=region.x0()+random.below(x1-region.x0()+1),y=region.y0()+random.below(y1-region.y0()+1);if(geometry.surface(x,y)<=238)return geometry.columnPosition(x,y);}
            for(int y=region.y0();y<=y1;y++)for(int x=region.x0();x<=x1;x++)if(geometry.surface(x,y)<=238)return geometry.columnPosition(x,y);
            return geometry.columnPosition(region.x0(),region.y0());
        }
        var choices=new ArrayList<>(geometry.candidates(team));var zones=geometry.zones(team);
        if(zones.isEmpty()){
            var base=geometry.fallbackBase(team,choices);
            if(base!=null){int target=Math.min(8,choices.size());List<SpawnGeometry.Candidate> cluster=List.of();for(int radius=24;radius<=64;radius+=4){int r=radius;cluster=choices.stream().filter(c->{long dx=c.x()-base.x(),dy=c.y()-base.y();return dx*dx+dy*dy<=r*r;}).toList();if(cluster.size()>=target)break;}if(!cluster.isEmpty())choices=new ArrayList<>(cluster);}
        }
        random.shuffle(choices);for(var candidate:choices){var p=geometry.candidatePosition(candidate,zones);if(p!=null)return p;}
        int cx=(region.x0()+region.x1())/2,cy=(region.y0()+region.y1())/2,limit=Math.max(region.x1()-region.x0(),region.y1()-region.y0());
        for(int radius=0;radius<=limit;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)if(Math.max(Math.abs(dx),Math.abs(dy))==radius&&region.contains(cx+dx,cy+dy)&&geometry.safeColumn(cx+dx,cy+dy))return geometry.columnPosition(cx+dx,cy+dy);
        return geometry.dryGround(cx,cy,24);
    }
    public void death(PlayerLife player){resolver.recordDeath(view(player),monotonic.getAsDouble());}
    public SpawnResolver resolver(){return resolver;}
}
