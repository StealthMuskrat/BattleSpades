// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.bot;
import java.util.*;
import net.battlespades.physics.PlayerPhysics.Vector;
/** Immutable primitive-only worker input. Neither live players nor mutable authoritative worlds cross this boundary. */
public final class BotState {
    private BotState(){}
    public record Observer(int playerId,int generation){}
    public record Cell(int x,int y,int z){}
    public enum Affordance {WALK,SWIM,CROUCH,JUMP,DROP,JETPACK,JETPACK_CLIMB,BREACH,BUILD_STEP,BUILD_BRIDGE,PLACE_PREFAB;public String code(){return name().toLowerCase(Locale.ROOT);}}
    public enum StimulusKind {SHOT,EXPLOSION,BLOCK_DESTROYED,FOOTSTEP,OBJECTIVE,DEPLOYABLE,TEAM_SIGHTING,DEATH;public String code(){return name().toLowerCase(Locale.ROOT);}}
    public record VoxelChange(int x,int y,int z,boolean solid,int color,double health){public Cell coordinate(){return new Cell(x,y,z);}}
    public record WorldDelta(long mapEpoch,long topologyVersion,List<VoxelChange> cells){public WorldDelta{cells=List.copyOf(cells);}}
    public static final class MapSnapshot {
        private final long mapEpoch,topologyVersion;private final byte[] raw;private final String mode,mapName,sourceFormat,mapDirectory;private final List<VoxelChange> cells;private final List<BotPrefabPolicy.Geometry> prefabs;
        public MapSnapshot(long epoch,long version,byte[] raw,String mode,String name,List<VoxelChange> cells,String format){this(epoch,version,raw,mode,name,cells,format,"",false);}
        public MapSnapshot(long epoch,long version,byte[] raw,String mode,String name,List<VoxelChange> cells,String format,String directory){this(epoch,version,raw,mode,name,cells,format,directory,false);}
        private MapSnapshot(long epoch,long version,byte[] raw,String mode,String name,List<VoxelChange> cells,String format,String directory,boolean retained){this(epoch,version,raw,mode,name,cells,format,directory,retained,List.of());}
        private MapSnapshot(long epoch,long version,byte[] raw,String mode,String name,List<VoxelChange> cells,String format,String directory,boolean retained,List<BotPrefabPolicy.Geometry> prefabs){this.prefabs=List.copyOf(prefabs);mapEpoch=epoch;topologyVersion=version;this.raw=retained?raw:raw.clone();this.mode=Objects.requireNonNull(mode);mapName=Objects.requireNonNull(name);this.cells=List.copyOf(cells);sourceFormat=Objects.requireNonNull(format);mapDirectory=Objects.requireNonNull(directory);}
        public List<BotPrefabPolicy.Geometry> prefabGeometry(){return prefabs;}
        public MapSnapshot withPrefabs(List<BotPrefabPolicy.Geometry> geometries){return new MapSnapshot(mapEpoch,topologyVersion,raw,mode,mapName,cells,sourceFormat,mapDirectory,true,geometries);}
        public long mapEpoch(){return mapEpoch;}public long topologyVersion(){return topologyVersion;}public byte[] rawVxl(){return raw.clone();}public String mode(){return mode;}public String mapName(){return mapName;}public List<VoxelChange> cells(){return cells;}public String sourceFormat(){return sourceFormat;}public String mapDirectory(){return mapDirectory;}
        /** Reuses immutable source bytes; terrain rebasing must not copy megabytes on the producer thread. */
        public MapSnapshot rebase(long version,List<VoxelChange> changes){return new MapSnapshot(mapEpoch,version,raw,mode,mapName,changes,sourceFormat,mapDirectory,true,prefabs);}
    }
    public record Surface(int x,int y,int supportZ){public Vector position(){return new Vector(x+.5,y+.5,supportZ-2.25);}}
    public record Edge(Cell source,Cell target){}
    public record Breach(Cell source,Cell destination,Cell targetCell,List<Cell> blockingCells,int toolId,boolean secondary,double fireInterval,int estimatedSwings){public Breach{blockingCells=List.copyOf(blockingCells);}public Vector target(){return new Vector(targetCell.x()+.5,targetCell.y()+.5,targetCell.z()+.5);}}
    public record RouteStep(Vector waypoint,Affordance affordance,Breach breach,Edge entryEdge){public RouteStep(Vector waypoint,Affordance affordance){this(waypoint,affordance,null,null);}}
    public record Stimulus(StimulusKind kind,Vector position,double createdAt,double expiresAt,int sourceId,int team,double uncertainty){}
    public record FootstepPlayer(int id,int team,boolean alive,boolean spawned,boolean grounded,boolean crouch,boolean sneak,boolean sprint,Vector velocity,Vector position){}
}
