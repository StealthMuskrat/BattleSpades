// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.match.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.scene.SceneConfig;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;
import org.tomlj.TomlTable;

/** Normal block/line and paint authority with reserved wallets and post-movement topology commits. */
public final class HostedConstructionRuntime implements AutoCloseable {
    private static final int[][] NEIGHBORS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public record Settings(WorldMutations.Settings mutations,boolean enforceInterval){
        public static Settings from(TomlTable raw){return new Settings(new WorldMutations.Settings(integer(raw,"network.world_mutation_queue_limit",2048,64),integer(raw,"network.world_mutation_batch_limit",256,1),integer(raw,"network.world_mutation_cell_budget",4096,64),integer(raw,"network.world_mutation_timeout_ticks",180,30)),raw.getBoolean("anticheat.enforce_block_interval",()->false));}
        private static int integer(TomlTable raw,String key,int fallback,int min){var value=raw.get(key);if(value==null)return fallback;if(value instanceof Long n)return (int)Math.max(min,Math.min(Integer.MAX_VALUE,n));throw new IllegalArgumentException(key+" must be integer");}
    }
    private record Paint(double tokens,double at){}
    private final ConstructionSafety safety;
    public ConstructionSafety safety(){return safety;}
    private final StandardMatchDomain match;private final ApplicationHost host;private final SceneConfig scene;private final Settings settings;private final WorldMutations mutations;
    private final Map<PlayerLife,Double> lastBuild=new IdentityHashMap<>();private final Map<PlayerLife,Paint> paints=new IdentityHashMap<>();
    public HostedConstructionRuntime(StandardMatchDomain match,ApplicationHost host,SceneConfig scene,Settings settings){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);this.scene=Objects.requireNonNull(scene);this.settings=Objects.requireNonNull(settings);mutations=new WorldMutations(settings.mutations);safety=new ConstructionSafety(match::monotonic,()->match.lives().players(),match.world(),c->protectedZone(c,match.metadata().spawnLayout()),(team,owner)->match.friendlyPaths(team,owner));}
    private static boolean protectedZone(Cell c,SpawnGeometry.Layout layout){for(var groups:List.of(layout.spawns(),layout.bases()))for(var zones:groups.values())for(var z:zones)if(c.x()>=(int)(z.x()+z.x0())-1&&c.x()<=(int)(z.x()+z.x1())+1&&c.y()>=(int)(z.y()+z.y0())-1&&c.y()<=(int)(z.y()+z.y1())+1)return true;return false;}
    public WorldMutations mutations(){return mutations;}
    public void tick(){mutations.commit(match.frameLoop(),match.lives().players());}
    private static boolean bounds(Cell c){return c.x()>=0&&c.x()<512&&c.y()>=0&&c.y()<512&&c.z()>=0&&c.z()<240;}
    private boolean solid(Cell c){return match.world().solid(c.x(),c.y(),c.z());}
    private boolean buildable(Cell c){return bounds(c)&&c.z()>=1&&c.z()<=238&&!solid(c);}
    private double distance(PlayerLife p,Cell c){return CombatGeometry.distance(p.position(),new Vector(c.x()+.5,c.y()+.5,c.z()+.5));}
    private boolean visible(PlayerLife p,Cell c,Integer loop,String kind){if(p.bot())return true;boolean visible=CombatGeometry.cellVisible(match.world()::solid,ShotValidation.references(ShotValidation.view(p),loop).eyes(),new CombatGeometry.Cell(c.x(),c.y(),c.z()),Set.of());if(!visible)match.inputs().report(p,kind,Map.of());return visible;}
    private boolean supported(Cell c,Set<Cell> pending){for(var d:NEIGHBORS){var n=new Cell(c.x()+d[0],c.y()+d[1],c.z()+d[2]);if(pending.contains(n)||solid(n))return true;}return false;}
    private void repair(List<Cell> cells){match.runtime().repair().record(cells.stream().filter(HostedConstructionRuntime::bounds).toList(),match.frameLoop());}
    public boolean overlaps(Collection<Cell> cells){return safety.overlaps(cells);}
    private boolean ordinary(PlayerLife p){return match.inputs().authorized(p,5)&&p.inventory().tool()==5&&p.fire().state().raw()&&scene.rules().enabled("RULE_ENABLE_BLOCKS");}
    private boolean cadence(PlayerLife p,Integer loop,List<Cell> cells){double now=match.monotonic();var last=lastBuild.get(p);boolean allowed=p.bot()?last==null||now-last>=.08:p.actionClock().admit("build",loop,.1,now,null,.02,p.fire().link());if(!allowed){match.inputs().report(p,"block_interval",settings.enforceInterval,Map.of("gap",last==null?0:now-last));if(settings.enforceInterval){repair(cells);return false;}}lastBuild.put(p,now);return true;}
    public boolean packet(PlayerLife p,GamePacket packet){return switch(packet){case SimplePackets.BlockBuild b->{single(p,new Cell(b.x(),b.y(),b.z()),b.loop_count());yield true;}case SimplePackets.BlockLine b->{line(p,new Cell(b.x1(),b.y1(),b.z1()),new Cell(b.x2(),b.y2(),b.z2()),b.loop_count());yield true;}case LivePlacement placement when placement.id()==104->{flare(p,placement);yield true;}case SimplePackets.PaintBlockPacket paint->{paint(p,new Cell(paint.x(),paint.y(),paint.z()),paint.color(),paint.loop_count());yield true;}default->false;};}
    public boolean single(PlayerLife p,Cell cell,int loop){
        if(!ordinary(p)||!bounds(cell)||distance(p,cell)>19)return false;var cells=List.of(cell);if(!visible(p,cell,loop,"build_occluded")||!buildable(cell)||!supported(cell,Set.of())||!match.teamRules().infinite(p.team())&&p.inventory().blocks()<=0){repair(cells);return false;}
        boolean charged=!match.teamRules().infinite(p.team());if(!cadence(p,loop,cells)||charged&&!p.inventory().removeBlock())return false;int action=Math.max(0,loop);long generation=p.generation();return mutations.enqueue(new WorldMutations.Mutation(p,generation,action,match.frameLoop(),1,()->{
            if(overlaps(cells)||!buildable(cell)||!supported(cell,Set.of())){cancel(p,generation,cells,charged);return;}commit(cell,p.color());owner(p,new SimplePackets.BlockBuild(action,p.id(),cell.x(),cell.y(),cell.z(),0));observers(p,cells,action,p.color());ownerColor(p,cells,action,p.color());placed(p,cells);
        },()->cancel(p,generation,cells,charged)));
    }
    public boolean line(PlayerLife p,Cell start,Cell end,int loop){
        if(!ordinary(p)||!bounds(start)||!bounds(end))return false;long span=Math.abs(end.x()-start.x())+Math.abs(end.y()-start.y())+Math.abs(end.z()-start.z())+1L;if(span>64||Math.min(distance(p,start),distance(p,end))>19||Math.max(distance(p,start),distance(p,end))>83)return false;
        var cells=WorldQueries.cubeLine(new WorldQueries.Cube(start.x(),start.y(),start.z()),new WorldQueries.Cube(end.x(),end.y(),end.z())).stream().map(c->new Cell(c.x(),c.y(),c.z())).filter(c->!solid(c)).toList();if(cells.isEmpty())return false;
        if(!p.bot()&&!visible(p,start,loop,"build_occluded")&&!visible(p,end,loop,"build_occluded")){repair(cells);return false;}
        var pending=new HashSet<Cell>();for(var c:cells){if(!buildable(c)||!supported(c,pending)){repair(cells);return false;}pending.add(c);}if(!match.teamRules().infinite(p.team())&&p.inventory().blocks()<cells.size()){repair(cells);return false;}if(!cadence(p,loop,cells))return false;
        boolean charged=!match.teamRules().infinite(p.team());if(charged)for(int i=0;i<cells.size();i++)p.inventory().removeBlock();long generation=p.generation();int action=Math.max(0,loop);return mutations.enqueue(new WorldMutations.Mutation(p,generation,action,match.frameLoop(),cells.size(),()->{
            if(overlaps(cells)){cancel(p,generation,cells,charged);return;}int color=p.color();for(var c:cells)commit(c,color);owner(p,new SimplePackets.BlockLine(action,p.id(),start.x(),start.y(),start.z(),end.x(),end.y(),end.z()));observers(p,cells,action,color);ownerColor(p,cells,action,color);placed(p,cells);
        },()->cancel(p,generation,cells,charged)));
    }
    private void commit(Cell cell,int color){match.world().set(new MutableWorld.Cell(cell.x(),cell.y(),cell.z()),true,color,9.0);}
    private void owner(PlayerLife p,GamePacket packet){if(!p.bot()&&!host.sendPlayer(p.id(),packet))throw new IllegalStateException("Construction owner control rejected");}
    private void cancel(PlayerLife p,long generation,List<Cell> cells,boolean charged){if(charged&&p.generation()==generation)p.inventory().addBlocks(cells.size());repair(cells);}
    private void observers(PlayerLife p,List<Cell> cells,int loop,int color){for(var c:cells)host.broadcastExcept(new SimplePackets.BlockBuildColored(loop,p.id(),c.x(),c.y(),c.z(),color),Delivery.RELIABLE,p.id());var users=match.world().terrainHealth().rows(cells.stream().map(c->new MutableWorld.Cell(c.x(),c.y(),c.z())).toList(),false).users();if(!users.isEmpty())host.broadcastExcept(new BlockManagerState(users,List.of()),Delivery.RELIABLE,p.id());}
    private void ownerColor(PlayerLife p,List<Cell> cells,int loop,int color){for(var c:cells)owner(p,new SimplePackets.PaintBlockPacket(loop,c.x(),c.y(),c.z(),color));}
    private void placed(PlayerLife p,List<Cell> cells){match.profiles().add(match.binding().row(p),156,cells.size(),0);match.combat().blocksPlaced(match.binding().row(p),cells.stream().map(Cell::z).toList());match.events().offer(ModeEvents.Event.of("on_blocks_built",match.binding().row(p),List.copyOf(cells)));var at=cells.getFirst();match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(46,1,new GameplaySounds.Position(at.x(),at.y(),at.z()),1),null,false,p);}
    public boolean paint(PlayerLife p,Cell cell,int color,int loop){
        int tool=p.inventory().tool();if(!p.fire().state().raw()||tool!=5&&tool!=22||!match.inputs().authorized(p,tool)||!bounds(cell)||cell.z()>238||distance(p,cell)>19||!visible(p,cell,loop,"paint_occluded"))return false;
        double now=match.monotonic();var previous=paints.getOrDefault(p,new Paint(16,now));double tokens=Math.min(16,previous.tokens+Math.max(0,now-previous.at)*30);if(tokens<1){paints.put(p,new Paint(tokens,now));return false;}paints.put(p,new Paint(tokens-1,now));var c=new MutableWorld.Cell(cell.x(),cell.y(),cell.z());if(!solid(cell)||(match.world().color(c)&0xffffff)==(color&0xffffff))return false;match.world().set(c,true,color,null);host.broadcast(new SimplePackets.PaintBlockPacket(Math.max(0,loop),cell.x(),cell.y(),cell.z(),color),Delivery.RELIABLE);return true;
    }
    public boolean flare(PlayerLife p,LivePlacement packet){
        var c=new Cell(packet.x(),packet.y(),packet.z());if(!match.inputs().authorized(p,22)||p.inventory().tool()!=22||!scene.rules().enabled("RULE_ENABLE_FLARE_BLOCKS")||!bounds(c)||c.z()<1||c.z()>238||distance(p,c)>10||solid(c))return false;
        var registry=match.entities().registry();if(registry.all().stream().anyMatch(e->e.alive&&!e.kind.equals("projectile")&&(int)e.x==c.x()&&(int)e.y==c.y()&&(int)e.z==c.z()))return false;
        var cell=new net.battlespades.entities.EntityRegistry.Cell(c.x(),c.y(),c.z());if(!net.battlespades.entities.FlareBlockBehavior.supported(registry,cell,v->match.world().solid(v.x(),v.y(),v.z()),null)||!visible(p,c,packet.loop(),"placement_occluded"))return false;
        for(var target:match.lives().players()){if(!target.alive()||!target.spawned())continue;var at=target.position();if(Math.abs(c.x()-(int)at.x())<=1&&Math.abs(c.y()-(int)at.y())<=1&&c.z()>=(int)at.z()&&c.z()<=(int)at.z()+2)return false;}
        if(!match.teamRules().infinite(p.team())&&p.inventory().blocks()<10||!cadence(p,packet.loop(),List.of(c)))return false;
        var v=new net.battlespades.entities.EntityRegistry.Placement();v.playerId=p.id();v.state=p.team();v.kind="flare_block";v.color=new Vector(p.color()>>16&255,p.color()>>8&255,p.color()&255);v.behavior=new net.battlespades.entities.FlareBlockBehavior();var e=registry.place(13,c.x(),c.y(),c.z(),v);if(!match.teamRules().infinite(p.team()))for(int i=0;i<10;i++)p.inventory().removeBlock();match.entities().create(e);return true;
    }
    public void forget(PlayerLife p){mutations.cancel(p);safety.forget(p.id());lastBuild.remove(p);paints.remove(p);}
    @Override public void close(){mutations.clear();safety.clear();lastBuild.clear();paints.clear();}
}
