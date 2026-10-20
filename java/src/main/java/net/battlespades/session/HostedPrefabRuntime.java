// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.battlespades.combat.*;
import net.battlespades.match.GameplaySounds;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;
import net.battlespades.world.BlockDamageModel.Cell;
import org.tomlj.TomlTable;

/** Worker-prepared model geometry; whole competitive prefabs commit under a shared tick budget. */
public final class HostedPrefabRuntime implements AutoCloseable {
    public record Settings(int queue,int cells){public static Settings from(TomlTable raw){return new Settings(integer(raw,"network.prefab_queue_limit",32,1,128),integer(raw,"network.prefab_competitive_cell_budget",2048,64,8192));}private static int integer(TomlTable raw,String key,int fallback,int min,int max){var value=raw.get(key);if(value==null)return fallback;if(value instanceof Long n)return (int)Math.max(min,Math.min(max,n));throw new IllegalArgumentException(key+" must be integer");}}
    private record Preparing(PlayerLife player,long generation,PrefabPacket packet,Future<List<PrefabModels.Block>> future){}
    private record Pending(PlayerLife player,long generation,PrefabPacket packet,List<PrefabModels.Block> blocks,Long reservation,int charged){}
    private final StandardMatchDomain match;private final ApplicationHost host;private final Settings settings;private final PrefabModels models;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("prefab-model-prepare").factory());
    private final ArrayDeque<Preparing> preparing=new ArrayDeque<>();private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    public HostedPrefabRuntime(StandardMatchDomain match,ApplicationHost host,Settings settings,List<Path> roots){this.match=Objects.requireNonNull(match);this.host=Objects.requireNonNull(host);this.settings=Objects.requireNonNull(settings);models=new PrefabModels(roots);}
    private boolean authorized(PlayerLife p,String name){int tool=p.inventory().tool();String key=name.toLowerCase(Locale.ROOT);return !name.isEmpty()&&p.fire().state().raw()&&Set.of(23,28,22,41,42).contains(tool)&&match.inputs().authorized(p,tool)&&(ClassCatalog.get(p.selection().classId()).prefabs().contains(key)||p.selection().prefabs().stream().anyMatch(n->n.equalsIgnoreCase(name)));}
    public boolean packet(PlayerLife p,GamePacket packet){if(!(packet instanceof PrefabPacket v))return false;if(v.id()==30)place(p,v);return true;}
    public boolean place(PlayerLife p,PrefabPacket packet){
        if(!authorized(p,packet.name())||preparing.size()+pending.size()>=settings.queue||preparing.stream().filter(r->r.player==p).count()+pending.stream().filter(r->r.player==p).count()>=4)return false;
        var anchor=new Cell(packet.x(),packet.y(),packet.z());var future=worker.submit(()->{var model=models.get(packet.name());return model.isEmpty()?List.<PrefabModels.Block>of():PrefabModels.expand(model.get(),anchor,packet.yaw(),packet.pitch(),packet.roll(),packet.color());});preparing.addLast(new Preparing(p,p.generation(),packet,future));return true;
    }
    private boolean current(PlayerLife p,long generation){return match.lives().players().stream().anyMatch(v->v==p)&&p.generation()==generation&&p.alive()&&p.spawned();}
    private boolean touches(List<PrefabModels.Block> blocks){for(var block:blocks){var c=block.cell();for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})if(match.world().solid(c.x()+d[0],c.y()+d[1],c.z()+d[2]))return true;}return false;}
    private boolean visible(PlayerLife p,List<PrefabModels.Block> blocks){if(p.bot())return true;var reference=ShotValidation.references(ShotValidation.view(p),null);var eye=p.position();var ordered=blocks.stream().sorted(Comparator.comparingDouble(b->CombatGeometry.distance(eye,new Vector(b.cell().x()+.5,b.cell().y()+.5,b.cell().z()+.5)))).limit(8).toList();for(var block:ordered){var c=block.cell();if(CombatGeometry.cellVisible(match.world()::solid,reference.eyes(),new CombatGeometry.Cell(c.x(),c.y(),c.z()),Set.of()))return true;}match.inputs().report(p,"prefab_occluded",Map.of());return false;}
    private void admit(Preparing request,List<PrefabModels.Block> blocks){
        var p=request.player;if(!current(p,request.generation)||!authorized(p,request.packet.name())||blocks.isEmpty()||blocks.stream().anyMatch(b->{var c=b.cell();return c.x()<0||c.x()>=512||c.y()<0||c.y()>=512||c.z()<1||c.z()>238;})||!touches(blocks)||!match.teamRules().infinite(p.team())&&p.inventory().blocks()<blocks.size())return;
        if(!p.bot()&&blocks.stream().noneMatch(b->{var c=b.cell();return CombatGeometry.distance(p.position(),new Vector(c.x()+.5,c.y()+.5,c.z()+.5))<=19;}))return;
        if(!visible(p,blocks)||match.construction().overlaps(blocks.stream().map(PrefabModels.Block::cell).toList()))return;
        var decision=match.construction().safety().reserve(p.id(),p.team(),blocks.stream().map(PrefabModels.Block::cell).toList());if(decision.token()==null)return;
        int charged=match.teamRules().infinite(p.team())?0:blocks.size();for(int i=0;i<charged;i++)p.inventory().removeBlock();pending.addLast(new Pending(p,p.generation(),request.packet,blocks,decision.token(),charged));
    }
    public void tick(){
        int scan=preparing.size();for(int i=0;i<scan;i++){var request=preparing.removeFirst();if(!current(request.player,request.generation)){request.future.cancel(true);continue;}if(!request.future.isDone()){preparing.addLast(request);continue;}try{admit(request,request.future.get());}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Prefab adoption interrupted",interrupted);}catch(ExecutionException failure){match.failure("prefab preparation",failure.getCause());}}
        int cells=0;while(!pending.isEmpty()){var job=pending.getFirst();int size=job.blocks.size();if(cells>0&&cells+size>settings.cells)break;pending.removeFirst();if(!current(job.player,job.generation)||match.construction().overlaps(job.blocks.stream().map(PrefabModels.Block::cell).toList())){refund(job);complete(job.player);continue;}
            for(var b:job.blocks){var c=b.cell();match.world().set(new MutableWorld.Cell(c.x(),c.y(),c.z()),true,b.color(),9.0);}var p=job.player;var v=job.packet;host.broadcast(new PrefabPacket(30,match.frameLoop(),v.name(),p.id(),v.yaw()&3,v.pitch()&3,v.roll()&3,0,0,v.x(),v.y(),v.z(),v.color(),true),Delivery.RELIABLE);match.construction().safety().release(job.reservation);complete(p);match.profiles().add(match.binding().row(p),157,1,0);match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(32,1,new GameplaySounds.Position(v.x(),v.y(),v.z()),1),p,false,p);cells+=size;
        }
    }
    private void refund(Pending job){match.construction().safety().release(job.reservation);if(job.player.generation()==job.generation)job.player.inventory().addBlocks(job.charged);}
    private void complete(PlayerLife p){if(!p.bot())host.sendPlayer(p.id(),new SimplePackets.PrefabComplete());}
    public void forget(PlayerLife p){for(var request:new ArrayList<>(preparing))if(request.player==p){preparing.remove(request);request.future.cancel(true);}for(var job:new ArrayList<>(pending))if(job.player==p){pending.remove(job);refund(job);}}
    public void clear(){for(var request:preparing)request.future.cancel(true);preparing.clear();for(var job:pending)refund(job);pending.clear();}
    @Override public void close(){clear();worker.shutdownNow();}
}
