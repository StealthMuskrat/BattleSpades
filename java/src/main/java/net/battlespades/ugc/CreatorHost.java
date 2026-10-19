// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.ugc;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.nio.file.Files;
import net.battlespades.player.LifeRuntime;
import net.battlespades.replication.ReplicationService;
import net.battlespades.runtime.*;
import net.battlespades.scene.SceneConfig;
import net.battlespades.session.*;
import net.battlespades.simulation.AsyncFixedStepRuntime;
import net.battlespades.transport.enet.EnetSettings;
import net.battlespades.world.*;
import org.tomlj.TomlParseResult;

/** Explicit isolated creator composition; no registration in the ordinary game-mode registry. */
public final class CreatorHost implements AutoCloseable {
    /** Accounts come from an authenticated embedding service, never ticket/name claims. */
    public record Access(String ownerAccount,Function<JoinSession,String> verifiedAccount){
        public Access{Objects.requireNonNull(ownerAccount);Objects.requireNonNull(verifiedAccount);if(ownerAccount.length()>128)throw new IllegalArgumentException("Creator owner account too long");}
        public static Access offline(){return new Access("",s->"");}
    }
    private final AutoCloseable projectLease;
    private final Thread owner=Thread.currentThread();private final OwnerLoop loop;private final PreparedMap.Bound prepared;private final ExecutorService writer,models;private final ApplicationHost host;private final CreatorScene scene;private final AsyncFixedStepRuntime simulation;private final double epoch=System.currentTimeMillis()/1000.;private long now;private double pending;private CompletableFuture<Integer> advancing=CompletableFuture.completedFuture(0);private boolean closed;
    private CreatorHost(InetSocketAddress bind,SceneConfig config,TomlParseResult raw,UgcWorkspace.Opened opened,UgcAssets assets,Consumer<Throwable> failure,Access access,AutoCloseable lease)throws IOException{
        projectLease=lease;var bytes=opened.source();var crc=new java.util.zip.CRC32();crc.update(bytes);
        var metadata=MapMetadata.load(opened.files().vxl(),"ugc",(int)crc.getValue());
        prepared=PreparedMap.prepare(new MapAssembly(VxlMap.loadServer(bytes,"retail"),metadata)).bind(1,65536);
        loop=new OwnerLoop(0,8192,1024,failure);
        writer=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("creator-project-writer").factory());
        models=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("creator-model-prepare").factory());
        ApplicationHost candidate=null;CreatorScene domain=null;
        try{var clock=new LifeRuntime.Clock(){public double wall(){return epoch+now/1000.;}public double monotonic(){return now/1000.;}};domain=new CreatorScene(config,metadata,prepared,opened,assets.compatible(opened.project().prefabTerrain()),List.of(assets.prefabs()),clock,loop,writer,models,failure,access);scene=domain;var base=JoinSettings.from(raw);var joins=new JoinSettings(8*1024*1024+16,base.maxTicketBytes(),base.packetBudget(),base.replayBudget(),base.controlCapacity(),16*1024*1024,base.clockLoopBias(),base.transitionGraceMillis(),true,net.battlespades.protocol.MapSyncTransfer.Mode.FULL,base.password());
            int players=(int)Math.max(2,Math.min(24,raw.getLong("server.max_players",()->24L)));var defaults=EnetSettings.defaults();var network=new EnetSettings(players,defaults.maxPacketBytes(),defaults.maxWaitingBytes(),defaults.maxOutgoingBytes(),defaults.maxPendingCommands(),defaults.eventCapacity(),defaults.eventBytes(),defaults.receiveBudget(),defaults.sendBudget(),true);
            candidate=new ApplicationHost(bind,network,players,joins,scene,new BoundedSnapshotCompression(1,players));host=candidate;scene.attach(host,ReplicationService.Settings.from(raw));host.serialPackets(new IngamePackets.Settings(8192,256,1024),loop,failure);
            simulation=new AsyncFixedStepRuntime(()->!closed,tick->{scene.frame((int)tick);return host.drainPackets((int)tick).thenRunAsync(()->{if(!closed)scene.tick((int)tick);},loop);},()->{},()->{var future=new CompletableFuture<Void>();loop.execute(()->future.complete(null));return future;},(label,error)->failure.accept(error),loop);
        }catch(IOException|RuntimeException|Error error){if(candidate!=null)try{candidate.close();}catch(IOException cleanup){error.addSuppressed(cleanup);}if(domain!=null)try{domain.rollback();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}writer.shutdownNow();models.shutdownNow();prepared.close();loop.close();throw error;}
    }
    public static CreatorHost open(InetSocketAddress bind,SceneConfig config,TomlParseResult raw,UgcWorkspace.Opened opened,UgcAssets assets,Consumer<Throwable> failure)throws IOException{return open(bind,config,raw,opened,assets,failure,Access.offline());}
    public static CreatorHost open(InetSocketAddress bind,SceneConfig config,TomlParseResult raw,UgcWorkspace.Opened opened,UgcAssets assets,Consumer<Throwable> failure,Access access)throws IOException{
        var workspace=new UgcWorkspace(opened.files());workspace.verifySceneMetadata();AutoCloseable lease=workspace.lock();
        try{
            if(!Arrays.equals(opened.source(),Files.readAllBytes(opened.files().vxl()))||!opened.originalSidecar().equals(Files.readString(opened.files().sidecar())))throw new IOException("UGC project changed during preparation; reopen the project");
            return new CreatorHost(bind,config,raw,opened,assets,Objects.requireNonNull(failure),Objects.requireNonNull(access),lease);
        }catch(IOException|RuntimeException|Error error){try{lease.close();}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Creator host outside owner");}
    public ApplicationHost application(){owned();return host;}public CreatorScene scene(){owned();return scene;}public SocketAddress address()throws IOException{owned();return host.address();}
    /** Keep servicing networking while a tick packet drain or a disk continuation is suspended. */
    public void service(long millis)throws IOException{owned();if(closed)throw new IllegalStateException("Creator host closed");if(millis<now||millis>Long.MAX_VALUE/1_000_000)throw new IllegalArgumentException("Creator clock");pending=Math.min(2,pending+(millis-now)/1000.);now=millis;host.service(now,(int)simulation.ticks());loop.pump(now*1_000_000);if(advancing.isDone()){advancing.join();double elapsed=pending;pending=0;advancing=simulation.advance(elapsed).toCompletableFuture();}loop.pump(now*1_000_000);}
    @Override public void close()throws IOException{owned();if(closed)return;closed=true;IOException problem=null;try{host.close();}catch(IOException e){problem=e;}catch(RuntimeException e){problem=new IOException("Creator host close failed",e);}try{var saved=scene.deactivate().toCompletableFuture();long deadline=System.nanoTime()+30_000_000_000L;while(!saved.isDone()){if(System.nanoTime()>=deadline)throw new IOException("Creator final checkpoint timed out");loop.pump(now*1_000_000);java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);}saved.join();}catch(IOException e){if(problem==null)problem=e;else problem.addSuppressed(e);}catch(RuntimeException e){var failed=new IOException("Creator final checkpoint failed",e);if(problem==null)problem=failed;else problem.addSuppressed(failed);}finally{writer.shutdown();models.shutdownNow();prepared.close();loop.close();try{projectLease.close();}catch(Exception e){if(problem==null)problem=new IOException("Could not release creator project",e);else problem.addSuppressed(e);}}if(problem!=null)throw problem;}
}
