// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.*;
import net.battlespades.session.*;
import net.battlespades.simulation.*;
import net.battlespades.world.*;

/** Controlled external service boundaries for the production assembly, never packaged. */
public final class StandardPorts implements StandardMatchDomain.Services,LifeRuntime.Effects,LifeRuntime.Inputs,LifeRuntime.Clock,SimulationFrame.Services {
    public final List<String> log=new ArrayList<>();public final List<Throwable> errors=new ArrayList<>();public final List<GamePacket> packets=new ArrayList<>();
    public double wall=1000,monotonic=100;public int closes;public boolean failSpawn,stopping=true;
    public CompletionStage<Void> playerPhase=CompletableFuture.completedFuture(null);
    public Function<ModeEvents.Event,CompletionStage<Void>> plugin=e->{log.add(e.name());return CompletableFuture.completedFuture(null);};
    public StandardMatchDomain create(String map,String code,PreparedMap.Bound bound,MapMetadata metadata,OwnerLoop loop){
        var config=new SceneConfig("Production assembly",27015,0,ModeCatalog.get(code),map,false,false,true,999,0,"plain.txt",new SceneConfig.Team("Blue",0x123456),new SceneConfig.Team("Green",0x323c46),GameRules.serverDefaults(),code);
        return new StandardMatchDomain(config,metadata,bound,org.tomlj.Toml.parse("[modes.tdm]\nscore_limit=10\ntime_limit=0\n[modes.arena]\nrounds_to_win=2\nround_time_limit=10\n[game]\nrespawn_time=1.5\n"),this,new SourceRandom(0),loop,List.of(map),this,this,this);
    }
    public static PreparedMap prepared(Path path,String code)throws Exception{
        // Dry, thick central island. Two source spans retain retail z=100 without classic-height shifting.
        byte[] column={2,100,100,0,1,2,3,4,0,(byte)239,(byte)239,(byte)239,1,2,3,4};byte[] data=new byte[32*32*column.length];for(int i=0;i<32*32;i++)System.arraycopy(column,0,data,i*column.length,column.length);Files.write(path,data);return PreparedMap.prepare(MapAssembly.load(path,code));
    }
    public double wall(){return wall;}public double monotonic(){return monotonic;}public boolean admin(int id){return false;}public boolean stopping(){return stopping;}
    public CompletionStage<Void> plugin(ModeEvents.Event event){return plugin.apply(event);}public void matchStarted(){log.add("start");}public void rebuildResources(){log.add("resources");}public void matchEnded(Integer winner){log.add("end:"+winner);}public void endVote(double wall){log.add("vote-end");}public void vote(double wall){log.add("vote");}public void scheduleEnd(Integer winner){log.add("schedule-end");}public CompletionStage<Void> cancelEnd(){log.add("cancel-end");return CompletableFuture.completedFuture(null);}
    public void escapeWatch(){log.add("escape");}public void reveal(PlayerLife p,Consumer<GamePacket> send){log.add("reveal:"+p.id());}public void resetResultBaselines(){log.add("baseline");}public void scheduleResults(Integer winner){log.add("results");}public void retireDeployables(PlayerLife p){log.add("deployables:"+p.id());}public void failure(String label,Throwable error){log.add("failure:"+label);errors.add(error);}public void close(){closes++;}
    public void beforeSpawn(PlayerLife p){if(failSpawn)throw new IllegalStateException("Controlled spawn failure");log.add("prepare:"+p.id());}public void spawned(PlayerLife p){log.add("spawn:"+p.id());}public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){log.add("death:"+v.id());}public void damaged(LifeRuntime.DamageEvidence d){log.add("damage");}public void healed(LifeRuntime.HealingEvidence h){log.add("heal");}public void beforeDeath(LifeRuntime.DeathEvidence d){log.add("before-death");}public void transitionCredited(PlayerLife v,PlayerLife a,int type){log.add("transition-credit");}public void forget(PlayerLife p){log.add("forget:"+p.id());}public void respawnFailed(PlayerLife p,RuntimeException error){failure("respawn",error);}
    public void reset(PlayerLife p){log.add("reset:"+p.id());}public void receive(PlayerLife p,LiveClientInput input,int loop){log.add("input:"+p.id());}public void packet(PlayerLife p,GamePacket packet){packets.add(packet);}
    public boolean enabled(SimulationFrame.Phase phase){return false;}
    public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int tick){log.add(phase.name());return phase==SimulationFrame.Phase.PLAYERS?playerPhase:CompletableFuture.completedFuture(null);}
    public void synchronous(SimulationFrame.Phase phase,int tick){log.add(phase.name());}public void subsystem(SimulationFrame.Phase phase,double ms){}public void tickMetrics(double ms){log.add("metrics");}public void health(double ms){log.add("health");}
    public PeriodicServices periodic(OwnerLoop loop){return new PeriodicServices(now->{log.add("balance");return CompletableFuture.completedFuture(false);},now->log.add("skill"),now->log.add("report"),this::failure,loop);}
    public TerrainRepair repair(ApplicationHost host,StandardMatchDomain match){return new TerrainRepair(match.world(),()->new TerrainRepair.Settings(true,64,1,1,1,8,8),()->match.lives().players().stream().map(p->new TerrainRepair.Recipient(p.id(),host.inGame(p.id()),true)).toList(),(recipient,packet)->host.sendPlayer(recipient.playerId(),packet));}
}
