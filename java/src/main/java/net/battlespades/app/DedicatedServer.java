// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.app;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.admin.*;
import net.battlespades.bot.*;
import net.battlespades.config.ServerConfig;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.plugin.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.*;
import net.battlespades.service.*;
import net.battlespades.session.*;
import net.battlespades.simulation.*;
import net.battlespades.transport.enet.*;
import net.battlespades.world.*;
/** Standalone owner/worker/service composition. One independent instance per configured UDP endpoint. */
public final class DedicatedServer implements AutoCloseable {
    private final Thread owner=Thread.currentThread();private final ServerSettings settings;private final ExecutorService workers;private final OwnerLoop loop;
    private final double epoch=System.currentTimeMillis()/1000.;private long nanos;private boolean started,closed;private volatile boolean stopping;
    private ApplicationHost application;private HostedSceneState state;private HostedSimulation simulation;private MatchTransitions<PreparedMap> transitions;private BanStore bans;private HostedAchievements.Prepared achievementStore;private PluginCatalog plugins;private LoggingRuntime logging;private TelemetryService telemetry;private final HealthMonitor health;
    private record Startup(PreparedMap map,MapCatalog catalog,PluginCatalog plugins,LoggingRuntime logging,List<String> warnings,List<BotPrefabPolicy.Geometry> botGeometry){}
    private final LifeRuntime.Clock clock=new LifeRuntime.Clock(){public double wall(){return epoch+nanos/1e9;}public double monotonic(){return nanos/1e9;}};
    public static DedicatedServer open(ServerConfig config)throws IOException{return open(ServerSettings.from(config),null);}
    public static DedicatedServer open(ServerSettings settings,InetSocketAddress bind)throws IOException{return new DedicatedServer(settings,bind);}
    private DedicatedServer(ServerSettings settings,InetSocketAddress bind)throws IOException{
        this.settings=Objects.requireNonNull(settings);workers=Executors.newFixedThreadPool(2,Thread.ofPlatform().name("server-io-",0).daemon(true).factory());loop=new OwnerLoop(0,16384,2048,error->failure("owner continuation",error));health=new HealthMonitor(window->log(LoggingRuntime.Level.INFO,"health",()->String.format(Locale.ROOT,"tick stats: avg=%.2fms max=%.2fms slow(>10ms)=%d/%d",window.averageMillis(),window.maxMillis(),window.slow(),window.ticks()),null));
        try{
            var raw=settings.core().raw();var votes=VotingSettings.from(raw,settings.scene().rules());var random=new SourceRandom(raw.getLong("bots.seed",()->0));var pluginSettings=PluginCatalog.Settings.from(raw,settings.core().root());
            var startup=new CompletableFuture<Startup>();workers.execute(()->{Startup value;try{value=preflight(votes,pluginSettings);}catch(Throwable failure){startup.completeExceptionally(failure);return;}if(!startup.complete(value)){try{value.plugins().close();}catch(IOException cleanup){failure("cancelled plugin preflight",cleanup);}value.logging().stop();}});Startup prepared=awaitStartup(startup);
            var mapCatalog=votes.shuffle()?prepared.catalog().shuffled(random):prepared.catalog();plugins=prepared.plugins();logging=prepared.logging();telemetry=new TelemetryService(logging);for(String warning:prepared.warnings())log(LoggingRuntime.Level.WARNING,"startup",()->warning,null);
            var pendingPorts=new IdentityHashMap<PreparedMap.Bound,DedicatedPorts>();var factory=new StandardMatchFactory(settings.scene(),raw,clock,random,loop,mapCatalog.available(),creation->{var ports=new DedicatedPorts(this,creation.code()+":"+creation.map());pendingPorts.put(creation.prepared(),ports);return new StandardMatchFactory.Ports(ports,ports,ports,ports,ports,ports);});factory.prefabRoots(List.of(settings.prefabs()));var modes=new LinkedHashMap<String,HostedSceneState.Factory>();for(String code:factory.modes().keySet())modes.put(code,(map,selected,bound,metadata)->{try{var domain=factory.create(map,selected,bound,metadata);pendingPorts.get(bound).bind(domain);return domain;}finally{pendingPorts.remove(bound);}});state=new HostedSceneState(settings.scene().mapName(),settings.scene().modeCode(),prepared.map(),1,settings.journalLimit(),modes);
            var defaults=EnetSettings.defaults();var network=new EnetSettings(settings.core().maxConnections(),defaults.maxPacketBytes(),defaults.maxWaitingBytes(),defaults.maxOutgoingBytes(),defaults.maxPendingCommands(),Math.max(defaults.eventCapacity(),settings.core().maxConnections()*2),defaults.eventBytes(),defaults.receiveBudget(),defaults.sendBudget(),settings.core().requireProtocolVersion());
            application=new ApplicationHost(bind==null?new InetSocketAddress(settings.bind(),settings.core().port()):bind,network,settings.core().maxPlayers(),settings.core().joinSettings(),state.scene(),new BoundedSnapshotCompression(1,settings.core().maxConnections()));application.telemetry(telemetry);application.admissionGate(_->started&&!stopping?null:2);
            var botSettings=BotSettings.from(raw);application.botStarter(()->new HostedBotDirector(application,this::match,botSettings,settings.maps(),prepared.botGeometry(),()->System.nanoTime()/1e9));if(botSettings.enabled())application.bots(new HostedBotDirector(application,this::match,botSettings,settings.maps(),prepared.botGeometry(),()->System.nanoTime()/1e9));
            bans=new BanStore(settings.bans(),clock::wall,workers,error->failure("ban persistence",error));new HostedAdministration(application,AdminAuthority.Settings.from(raw),bans,_ ->Set.of(),clock::monotonic,loop,this::failure);
            if(raw.getBoolean("achievements.enabled",()->true)){Path legacy=settings.core().resolve(raw.getString("achievements.path",()->"state/achievements.sqlite3"));if(Files.exists(legacy)&&Files.size(legacy)>0&&!Files.exists(settings.achievements()))throw new IOException("Existing SQLite achievements need an explicit export/import before standalone hosting: "+legacy);achievementStore=awaitStartup(HostedAchievements.prepare(settings.achievements()).toCompletableFuture());new HostedAchievements(application,achievementStore,_ ->Optional.empty(),clock,loop,raw.getBoolean("achievements.count_bot_kills",()->true));}
            HostedVoting.standard(application,state,mapCatalog,votes,clock,loop);
            var transitionServices=new HostedTransitionServices(state,application,loop,new Transitions());transitions=new MatchTransitions<>(transitionServices,(map,code)->CompletableFuture.supplyAsync(()->{try{return prepareMap(map,code);}catch(IOException error){throw new CompletionException(error);}},workers),loop);application.matchTransitions(transitions);
            new HostedRoundEnding(application,state,transitionServices,loop,HostedRoundEnding.Settings.from(raw),match->application.bots().ifPresent(b->b.phase("end",0)),this::failure);
            new HostedDiscovery(application,this::match,HostedDiscovery.Settings.from(raw,settings.core().port(),settings.core().steamId()),clock::monotonic,new java.security.SecureRandom()::nextInt,error->log(LoggingRuntime.Level.WARNING,"discovery",()->error.getMessage(),null));
            new HostedPlugins(application,this::match,pluginSettings,plugins,workers,loop,()->System.nanoTime()/1e9,clock::monotonic,this::failure);
            simulation=new HostedSimulation(application,loop,this::match,new Frames(),System::nanoTime,IngamePackets.Settings.defaults(settings.core().incomingCapacity(),settings.core().drainBudget()));
            if(AdminAuthority.passwordProblem(raw.getString("admin.password",()->"changeme"))!=null)log(LoggingRuntime.Level.WARNING,"admin",()->"Password login disabled until a secure admin password is configured",null);
            if(raw.getBoolean("updates.update_check_enabled",()->true))net.battlespades.update.ServerUpdates.background(Main.SOURCE_VERSION,raw.getString("updates.update_manifest_url",()->net.battlespades.update.ServerUpdates.DEFAULT_MANIFEST),text->updateNotice(LoggingRuntime.Level.INFO,text),text->updateNotice(LoggingRuntime.Level.WARNING,text),text->updateNotice(LoggingRuntime.Level.DEBUG,text));
        }catch(IOException|RuntimeException|Error failure){try{rollback();}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private Startup preflight(VotingSettings votes,PluginCatalog.Settings pluginSettings)throws IOException{var raw=settings.core().raw();LoggingRuntime log=null;PluginCatalog catalog=null;try{var warnings=new ArrayList<String>();var map=prepareMap(settings.scene().mapName(),settings.scene().modeCode());var maps=MapCatalog.load(settings.maps(),votes.rotation(),false,votes.overrides(),null);catalog=PluginCatalog.prepare(pluginSettings,(name,error)->warnings.add(name+": "+error.getMessage()));log=LoggingRuntime.open(LoggingRuntime.Settings.from(raw,settings.logs()));return new Startup(map,maps,catalog,log,List.copyOf(warnings),BotPrefabPolicy.load(BotPrefabPolicy.names(),new PrefabModels(List.of(settings.prefabs()))));}catch(IOException|RuntimeException|Error failure){if(catalog!=null)try{catalog.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}if(log!=null)log.stop().toCompletableFuture().join();throw failure;}}
    private static <T>T awaitStartup(Future<T> future)throws IOException{try{return future.get(120,TimeUnit.SECONDS);}catch(InterruptedException error){future.cancel(true);Thread.currentThread().interrupt();throw new IOException("Startup interrupted",error);}catch(ExecutionException error){var cause=OwnerSequence.cause(error);if(cause instanceof IOException io)throw io;if(cause instanceof RuntimeException ordinary)throw ordinary;if(cause instanceof Error fatal)throw fatal;throw new IOException("Startup failed",cause);}catch(TimeoutException error){future.cancel(true);throw new IOException("Startup preflight timed out",error);}}
    private PreparedMap prepareMap(String map,String code)throws IOException{if(!StandardMatchFactory.codes().contains(code))throw new IllegalArgumentException("Unimplemented standalone mode: "+code);Path root=settings.maps().toRealPath(),path=settings.map(map).toRealPath();if(!path.startsWith(root))throw new IOException("Map escapes configured root");return PreparedMap.prepare(MapAssembly.load(path,Hardcore.base(code)));}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Server outside owner");if(closed)throw new IllegalStateException("Server closed");}
    public ApplicationHost application(){owned();return application;}public StandardMatchDomain match(){owned();return (StandardMatchDomain)state.domain();}public MatchTransitions<PreparedMap> transitions(){owned();return Objects.requireNonNull(transitions);}public TelemetryService telemetry(){owned();return telemetry;}public boolean stopping(){return stopping;}
    public CompletionStage<Void> ready(){owned();return application.plugins().orElseThrow().ready();}
    public void start(){owned();if(started||stopping)throw new IllegalStateException("Server starts once");simulation.start();application.bots().ifPresent(HostedBotDirector::start);started=true;}
    public void pump(long nowNanos)throws IOException{owned();if(nowNanos<nanos)throw new IllegalArgumentException("Server clock moved backwards");nanos=nowNanos;if(started)simulation.pump(nanos);else{application.service(nanos/1000000,0);loop.pump(nanos);}}
    public CompletionStage<Void> stop(){owned();stopping=true;return simulation.stop();}
    public Map<String,Object> status(){owned();var result=new LinkedHashMap<String,Object>();result.put("state",stopping?"stopping":started?"running":"starting");try{result.put("port",((InetSocketAddress)application.address()).getPort());}catch(IOException error){throw new UncheckedIOException(error);}result.put("map",match().mapName());result.put("mode",match().modeCode());result.put("players",match().lives().players().size());result.put("reserved_slots",application.occupiedSlots());result.put("ticks",simulation.ticks());result.put("metrics",telemetry.snapshot());return result;}
    public CompletionStage<String> command(String line){owned();String[] words=line.strip().split("\\s+",2);String argument=words.length==2?words[1]:"";return switch(words[0].toLowerCase(Locale.ROOT)){case "status"->CompletableFuture.completedFuture(JsonData.encode(status()).stripTrailing());case "stop","quit"->stop().thenApply(_->"Stopped");case "map"->transitions.changeMap(argument).thenApply(Object::toString);case "mode"->transitions.changeMode(argument).thenApply(Object::toString);case "restart"->transitions.restart().thenApply(Object::toString);case "say"->{application.broadcast(Announcements.overlay(argument),Delivery.RELIABLE);yield CompletableFuture.completedFuture("Announced");}case "help"->CompletableFuture.completedFuture("status | map NAME | mode CODE | restart | say MESSAGE | stop");default->CompletableFuture.completedFuture("Unknown console command; use help");};}
    void log(LoggingRuntime.Level level,String name,Supplier<String> message,Throwable error){if(logging!=null)logging.log(level,name,message,error);else if(error!=null)System.getLogger("startup").log(System.Logger.Level.ERROR,name,error);}
    private void updateNotice(LoggingRuntime.Level level,String text){if(!stopping)log(level,"updates",()->text,null);}
    Optional<HostedBotDirector> bots(){return application==null?Optional.empty():application.bots();}
    void failure(String name,Throwable error){log(LoggingRuntime.Level.ERROR,name,()->"Service failure",error);}
    private final class Frames implements SimulationFrame.Services {
        public boolean enabled(SimulationFrame.Phase phase){return phase==SimulationFrame.Phase.BOTS&&application.bots().isPresent();}public CompletionStage<Void> asynchronous(SimulationFrame.Phase phase,int tick){if(phase==SimulationFrame.Phase.BOTS)application.bots().ifPresent(b->b.update(FixedStepRuntime.DELTA));return CompletableFuture.completedFuture(null);}
        public void synchronous(SimulationFrame.Phase phase,int tick){if(phase==SimulationFrame.Phase.BOT_ACTIONS)application.bots().ifPresent(b->b.drainActions(1));}public void subsystem(SimulationFrame.Phase phase,double ms){health.subsystem(phase.name().toLowerCase(Locale.ROOT),ms);}public void failure(String name,Throwable error){DedicatedServer.this.failure(name,error);}public void tickMetrics(double ms){}public void health(double ms){health.tick(ms);}
    }
    private final class Transitions implements HostedTransitionServices.Bindings {
        public void notice(String text){application.broadcast(Announcements.overlay(text),Delivery.RELIABLE);}public void resetVote(){log(LoggingRuntime.Level.DEBUG,"transition",()->"Ballot reset",null);}public CompletionStage<Void> prepareBots(){application.bots().ifPresent(HostedBotDirector::prepareTransition);return CompletableFuture.completedFuture(null);}public double graceSeconds(){return settings.graceSeconds();}public void resetRuntime(){health.reset();application.discardTransitionPackets();}public void resetRepair(){match().runtime().repair().reset();}public void resetJournal(){log(LoggingRuntime.Level.DEBUG,"transition",()->"Fresh world journal installed",null);}public void resetHud(){match().hud().clear();}public void notePlayed(String name){log(LoggingRuntime.Level.INFO,"transition",()->"Map installed: "+name,null);}public CompletionStage<Void> rebindBots(){application.bots().ifPresent(HostedBotDirector::rebind);return CompletableFuture.completedFuture(null);}public CompletionStage<Void> resetBotsAfterRestart(){application.bots().ifPresent(HostedBotDirector::resetAfterRestart);return CompletableFuture.completedFuture(null);}public CompletionStage<Void> restartRound(TransitionMode mode){throw new IllegalStateException("Standard restart must use its owned restart service");}public void failure(String name,Throwable error){DedicatedServer.this.failure(name,error);}
    }
    @FunctionalInterface private interface Cleanup {void run()throws Exception;}
    private static IOException attempt(IOException prior,Cleanup action){try{action.run();}catch(Exception failure){if(prior==null)prior=new IOException("Server cleanup failed",failure);else prior.addSuppressed(failure);}return prior;}
    private void rollback()throws IOException{
        stopping=true;IOException error=null;
        if(application!=null){
            if(simulation!=null)error=attempt(error,()->drain(simulation.stop()));
            else for(Supplier<CompletionStage<Void>> action:List.<Supplier<CompletionStage<Void>>>of(application::shutdownEndings,application::shutdownBots,application::shutdownPlugins,application::shutdownResults,application::shutdownAchievements,application::shutdownReview,application::shutdownTelemetry))error=attempt(error,()->drain(action.get()));
            error=attempt(error,application::close);
        }
        if(state!=null)error=attempt(error,state::close);if(bans!=null)error=attempt(error,bans::close);if(plugins!=null)error=attempt(error,plugins::close);
        if(logging!=null)error=attempt(error,()->logging.stop().toCompletableFuture().get(30,TimeUnit.SECONDS));if(achievementStore!=null)error=attempt(error,achievementStore::close);
        workers.shutdownNow();error=attempt(error,loop::close);closed=true;if(error!=null)throw error;
    }
    private void drain(CompletionStage<Void> stage)throws IOException{var done=stage.toCompletableFuture();long deadline=System.nanoTime()+30000000000L;while(!done.isDone()){if(System.nanoTime()>=deadline)throw new IOException("Server shutdown timed out; retained state was not discarded");try{nanos+=1000000;application.service(nanos/1000000,simulation==null?0:(int)simulation.ticks());loop.pump(nanos);}catch(IOException error){failure("network during shutdown",error);}java.util.concurrent.locks.LockSupport.parkNanos(1000000);}try{done.join();}catch(CompletionException error){throw new IOException("Server shutdown failed",OwnerSequence.cause(error));}}
    @Override public void close()throws IOException{
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Server close outside owner");if(closed)return;
        IOException error=attempt(null,()->drain(stop()));
        // Host guards retain failed durable stores and the owner executor for an operator retry.
        try{application.close();}catch(IOException|RuntimeException failure){if(error!=null)failure.addSuppressed(error);if(failure instanceof IOException io)throw io;throw new IOException("Host still owns undrained services",failure);}
        error=attempt(error,state::close);error=attempt(error,bans::close);if(achievementStore!=null)error=attempt(error,achievementStore::close);
        workers.shutdown();error=attempt(error,()->{try{if(!workers.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Server workers did not stop");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}});error=attempt(error,loop::close);closed=true;if(error!=null)throw error;
    }
}
