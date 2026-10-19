// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.tomlj.TomlTable;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.scene.*;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;

/** Production TDM, Arena and CTF composition. The scene state owns the prepared world; external services remain required.
 * Attach before admission, and await mode deactivation before disposal. This does not supply missing server domains.
 */
public final class StandardMatchDomain implements HostedSceneState.HostBoundDomain,ConfiguredScene.Gameplay {
    public interface Services {
        boolean admin(int playerId);boolean stopping();
        CompletionStage<Void> plugin(ModeEvents.Event event);
        void matchStarted();void rebuildResources();void matchEnded(Integer winner);
        void endVote(double wall);void vote(double wall);void scheduleEnd(Integer winner);CompletionStage<Void> cancelEnd();
        void escapeWatch();void reveal(PlayerLife player,Consumer<GamePacket> send);
        void resetResultBaselines();void scheduleResults(Integer winner);
        void retireDeployables(PlayerLife player);void failure(String label,Throwable error);
        void close();
    }
    private final Thread owner=Thread.currentThread();
    private final TomlTable raw;private final SpawnGeometry geometry;private HostedCtfRuntime ctf;
    public Optional<HostedCtfRuntime> ctf(){owned();return Optional.ofNullable(ctf);}
    public boolean shootWithIntel(){owned();return mode instanceof CtfMode c&&c.settings().shootWithIntel();}
    public static SceneConfig modeConfig(SceneConfig config){
        if(!config.modeCode().equals("cctf"))return config;
        var rules=config.rules().compatibility(Map.of("RULE_ENABLE_WEAPON_CLASSIC_SMG",true,"RULE_ENABLE_WEAPON_CLASSIC_SHOTGUN",true));
        return new SceneConfig(config.name(),config.port(),config.steamId(),config.mode(),config.mapName(),config.sameTeamCollision(),config.friendlyFire(),config.fallDamage(),config.scoreLimit(),config.fogColor(),config.skybox(),config.team1(),config.team2(),rules,config.modeCode(),config.admission());
    }
    private List<Integer> selectionDisabled(){var blocked=new TreeSet<>(config.rules().disabledTools());if(config.modeCode().equals("cctf"))blocked.add(22);return List.copyOf(blocked);}
    private ClassCatalog.Selection classicSelection(ClassCatalog.Selection selection){return ClassCatalog.normalize(5,selection.loadout(),selection.prefabs(),selection.ugcTools(),5,selectionDisabled());}

    private final boolean logCommands;private HostedSocialRuntime social;
    public HostedSocialRuntime social(){owned();return Objects.requireNonNull(social);}
    public boolean stopping(){owned();return services.stopping();}
    private final HostedConductRuntime.Settings conductSettings;private HostedConductRuntime conduct;
    public HostedConductRuntime conduct(){owned();return Objects.requireNonNull(conduct);}
    public boolean admin(PlayerLife p){owned();return services.admin(p.id());}
    private final HostedConstructionRuntime.Settings constructionSettings;private final HostedPrefabRuntime.Settings prefabSettings;
    private List<java.nio.file.Path> prefabRoots=List.of(java.nio.file.Path.of("prefabs").toAbsolutePath());private HostedPrefabRuntime prefabs;
    public void prefabRoots(List<java.nio.file.Path> roots){owned();if(host!=null)throw new IllegalStateException("Prefab roots bind before attachment");prefabRoots=List.copyOf(roots);}
    public HostedPrefabRuntime prefabs(){owned();return Objects.requireNonNull(prefabs);}
    public void failure(String label,Throwable error){owned();services.failure(label,error);}
    public MapMetadata metadata(){owned();return metadata;}
    private BiFunction<Integer,Integer,Set<BlockDamageModel.Cell>> friendlyPaths=(team,owner)->Set.of();
    public Set<BlockDamageModel.Cell> friendlyPaths(int team,int owner){owned();return Set.copyOf(friendlyPaths.apply(team,owner));}
    public void friendlyPaths(BiFunction<Integer,Integer,Set<BlockDamageModel.Cell>> provider){owned();friendlyPaths=Objects.requireNonNull(provider);}
    private HostedConstructionRuntime construction;private HostedCorpseRuntime corpses;
    public HostedConstructionRuntime construction(){owned();return Objects.requireNonNull(construction);}
    public HostedCorpseRuntime corpses(){owned();return Objects.requireNonNull(corpses);}
    private final SceneConfig config;private final MapMetadata metadata;private final Services services;
    private final LifeRuntime.Clock clock;private final OwnerLoop loop;private final SourceRandom random;
    private final List<String> maps;private final boolean musicEnabled;private final double cooldown,respawn;
    private final List<TeamState> teams;private final ModeEvents events;private final Scoreboard board;
    private final ProfileTracker profiles;private final CombatEvidence combat;private final MatchLifeBinding binding;
    private final LifeRuntime lives;private final StandardSpawns spawns;private final BaseRoundMode mode;
    private final TransitionRoster transition;private final ConfiguredScene scene;
    private ApplicationHost host;private HostedMedia media;private HostedPlayerControls controls;private boolean closed,closing;
    private Function<ApplicationHost,HostedPlayerRuntime> runtimeFactory;private HostedPlayerRuntime runtime;
    private final HostedInputRuntime inputRuntime;
    public HostedInputRuntime inputs(){owned();return inputRuntime;}
    private int frameLoop;
    public int frameLoop(){owned();return frameLoop;}public void frameLoop(int tick){owned();frameLoop=tick;}
    private Function<ApplicationHost,HostedCombatRuntime> combatFactory;private HostedCombatRuntime combatRuntime;
    public void combatRuntime(Function<ApplicationHost,HostedCombatRuntime> factory){owned();if(host!=null||combatFactory!=null)throw new IllegalStateException("Combat must bind once before host attachment");combatFactory=Objects.requireNonNull(factory);}
    public HostedCombatRuntime combatRuntime(){owned();return Objects.requireNonNull(combatRuntime,"Combat runtime not bound");}
    private Function<ApplicationHost,HostedEntityRuntime> entityFactory;private HostedEntityRuntime entities;
    public void entities(Function<ApplicationHost,HostedEntityRuntime> factory){owned();if(host!=null||entityFactory!=null)throw new IllegalStateException("Entities must bind once before host attachment");entityFactory=Objects.requireNonNull(factory);}
    public HostedEntityRuntime entities(){owned();return Objects.requireNonNull(entities,"Entity runtime not bound");}
    /** Concrete factory binding, registered before the host or any actor exists. */
    public void runtime(Function<ApplicationHost,HostedPlayerRuntime> factory){owned();if(host!=null||runtimeFactory!=null)throw new IllegalStateException("Runtime must bind once before host attachment");runtimeFactory=Objects.requireNonNull(factory);}
    public HostedPlayerRuntime runtime(){owned();return Objects.requireNonNull(runtime,"Player runtime not bound");}
    public StandardMatchDomain(SceneConfig requestedConfig,MapMetadata metadata,PreparedMap.Bound prepared,TomlTable raw,
            LifeRuntime.Clock clock,SourceRandom random,OwnerLoop loop,List<String> maps,LifeRuntime.Effects lifeEffects,HostedInputRuntime.Ports inputs,Services services){
        var config=modeConfig(requestedConfig);this.raw=raw;this.geometry=prepared.spawns();
        logCommands=raw.getBoolean("admin.log_commands",()->true);conductSettings=HostedConductRuntime.Settings.from(raw);prefabSettings=HostedPrefabRuntime.Settings.from(raw);constructionSettings=HostedConstructionRuntime.Settings.from(raw);this.config=Objects.requireNonNull(config);this.metadata=Objects.requireNonNull(metadata);this.clock=Objects.requireNonNull(clock);this.random=Objects.requireNonNull(random);this.loop=Objects.requireNonNull(loop);this.maps=List.copyOf(maps);this.services=Objects.requireNonNull(services);Objects.requireNonNull(lifeEffects);Objects.requireNonNull(inputs);
        if(!Set.of("tdm","arena","ctf","cctf").contains(config.modeCode())||!metadata.activeMode().equals(config.modeCode())||metadata.environment().sourceCrc()!=prepared.world().sourceCrc())throw new IllegalArgumentException("Standard match requires matching prepared mode and config");
        cooldown=5;respawn=number(raw,"game.respawn_time",5);musicEnabled=raw.getBoolean("audio.mode_start_music",()->true);
        teams=List.of(team(2,config.team1()),team(3,config.team2()));
        events=new ModeEvents(8192,1024,this::mode,services::plugin,services::failure,loop);
        profiles=new ProfileTracker(()->new ProfileTracker.Context(false,config.modeCode(),mode().clock().ended),(a,b)->a.team()!=b.team());
        board=new Scoreboard(this::rows,()->teams,()->Optional.of(host().scorePeers()),()->mode().clock().ended,services::stopping,random,new Scoreboard.Effects(){
            public void broadcast(GamePacket p,boolean reliable,Integer known){send(p,reliable,known);}public void scoreChanged(Scoreboard.Player p,int reason){profiles.scoreChanged(p,reason);}public void resetProfileBaseline(Scoreboard.Player p){profiles.resetBaseline(p);}public void resetResultBaselines(){services.resetResultBaselines();}public void scheduleResults(Integer winner){services.scheduleResults(winner);}
        });
        combat=new CombatEvidence(()->new CombatEvidence.Context(!mode().clock().ended,true,false,null),this::rows,(a,b)->a.team()!=b.team(),board);
        binding=new MatchLifeBinding(this::lives,new LifeEffects(lifeEffects),profiles,combat,events,clock::monotonic);
        lives=new LifeRuntime(prepared.world(),config.rules(),config.modeCode(),clock,new LifeRuntime.Policy(){
            public AdmissionPolicy.Context admissionContext(int id){return new AdmissionPolicy.Context(lives().players().stream().map(p->new AdmissionPolicy.Occupant(p.id(),p.name(),services.admin(p.id()),p.team())).toList(),null,new AdmissionPolicy.ModePolicy(){public ClassCatalog.Selection prepareSelection(int team,ClassCatalog.Selection s){return config.modeCode().equals("cctf")&&(team==2||team==3)?classicSelection(s):s;}});}
            public void prepareSpawn(PlayerLife p){} // Ordinary BaseMode has no prepare_player_spawn hook.
            public Vector spawnPoint(PlayerLife p){return spawns().spawn(p);}public Optional<Vector> spawnOrientation(PlayerLife p){return Optional.empty();}
            public boolean canRespawn(PlayerLife p){return !(mode() instanceof ArenaMode arena)||arena.canRespawn(p);}
            public double respawnDelay(PlayerLife p){return mode() instanceof ArenaMode arena?arena.respawnTime(respawn):respawn;}
            public double walletScale(){return config.rules().number("RULE_CHARACTER_BLOCK_WALLETS");}
            public int teamColor(PlayerLife p){return teams.stream().filter(t->t.id==p.team()).findFirst().map(TeamState::color).orElse(0x707070);}
            public int score(PlayerLife p){return Math.toIntExact(binding.row(p).score);}public boolean opponents(PlayerLife a,PlayerLife b){return a.team()!=b.team();}
            public int deathType(PlayerLife v,PlayerLife k,int type){return type;}
            public Optional<LifeRuntime.DamageModifier> damageModifier(){return mode() instanceof ArenaMode arena?Optional.of((v,amount,s,type)->arena.damage(amount)):Optional.empty();}
            public boolean combatScoringActive(){return !mode().clock().ended;}
        },binding,new LifeRuntime.Messages(){
            public void broadcast(GamePacket p){send(p,true,knownId(p));}public void owner(int id,GamePacket p){host().sendPlayer(id,p);}public boolean inGame(int id){return host().inGame(id);}
        },new LifeRuntime.Inputs(){
            public void reset(PlayerLife p){inputRuntime.reset(p);}public void receive(PlayerLife p,LiveClientInput input,int receivedLoop){inputRuntime.receive(p,input,receivedLoop);}
            public void packet(PlayerLife p,GamePacket packet){if(packet instanceof VotePacket)media().packet(p,packet);else inputRuntime.packet(p,packet);}public void forget(PlayerLife p){inputRuntime.forget(p);}
        });
        var effects=new Effects();
        mode=switch(config.modeCode()){
            case "tdm"->new TdmMode(TdmMode.Settings.from(raw,config.rules()),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "arena"->new ArenaMode(ArenaMode.Settings.from(raw),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,()->60,p->send(p,true,null),effects,loop);
            case "ctf","cctf"->new CtfMode(CtfMode.Settings.from(raw,config.rules(),config.modeCode()),()->Objects.requireNonNull(ctf,"CTF owner not attached"),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            default->throw new IllegalArgumentException("Standard mode");
        };
        spawns=new StandardSpawns(prepared.spawns(),mode,random,lives::players,clock::monotonic);
        transition=new TransitionRoster(lives,binding,()->teams,loop);scene=new ConfiguredScene(config,metadata,mode::sceneState,this);
        inputRuntime=new HostedInputRuntime(this,config,inputs);
    }
    private static double number(TomlTable raw,String key,double fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0)return n.doubleValue();throw new IllegalArgumentException(key+" must be finite and nonnegative");}
    private static TeamState team(int id,SceneConfig.Team t){return new TeamState(id,t.name(),t.color()>>>16&255,t.color()>>>8&255,t.color()&255);}
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Standard match outside active owner");}
    private ApplicationHost host(){owned();return Objects.requireNonNull(host,"Match host not attached");}
    public LifeRuntime lives(){owned();return lives;}public BaseRoundMode mode(){owned();return mode;}public StandardSpawns spawns(){owned();return spawns;}
    public double monotonic(){owned();return clock.monotonic();}public double wall(){owned();return clock.wall();}
    public Scoreboard scoreboard(){owned();return board;}public MatchLifeBinding binding(){owned();return binding;}public CombatEvidence combat(){owned();return combat;}public ProfileTracker profiles(){owned();return profiles;}
    public HostedMedia media(){owned();return Objects.requireNonNull(media,"Media not attached");}public HostedPlayerControls controls(){owned();return Objects.requireNonNull(controls,"Controls not attached");}
    private Map<Integer,Scoreboard.Player> rows(){return binding.players();}
    private Optional<Scoreboard.Peer> peer(PlayerLife p){return p.bot()||lives.players().stream().noneMatch(current->current==p)?Optional.empty():host().scorePeer(p.id());}
    private static Integer knownId(GamePacket p){return switch(p){case SimplePackets.SetScore s when s.typeValue()==1->s.specifier();case SimplePackets.SetColor c->c.player_id();case SimplePackets.PlayerLeft l->l.player_id();default->null;};}
    private void send(GamePacket p,boolean reliable,Integer known){var delivery=reliable?Delivery.RELIABLE:Delivery.UNRELIABLE;if(known==null)host().broadcast(p,delivery);else host().broadcastKnown(p,delivery,known);}
    @Override public void attach(ApplicationHost application){
        owned();if(host!=null||!lives.players().isEmpty())throw new IllegalStateException("Match must attach once before admission");host=Objects.requireNonNull(application);conduct=new HostedConductRuntime(this,host,conductSettings);
        var byId=new LinkedHashMap<Integer,TeamState>();for(var t:teams)byId.put(t.id,t);
        controls=new HostedPlayerControls(host,lives,byId,events,new HostedPlayerControls.Mode(){
            public Object eventPlayer(PlayerLife p){return binding.row(p);}public boolean allowsTeam(PlayerLife p,int team){return true;}public boolean locked(int team){return false;}public boolean assignsTeams(){return false;}public void retireDeployables(PlayerLife p){if(entities!=null)entities.actions().retire(p);services.retireDeployables(p);}
            public ClassCatalog.Selection normalize(int id,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback){return ClassCatalog.normalize(id,tools,prefabs,ugc,fallback,selectionDisabled());}
            public boolean selectable(int id){return AdmissionPolicy.classSelectable(id,config.rules(),config.mode(),false,false);}public boolean allowsSelection(PlayerLife p,ClassCatalog.Selection s){return !config.modeCode().equals("cctf")||(p.team()==2||p.team()==3)&&s.equals(classicSelection(s));}public boolean liveUgc(PlayerLife p,ClassCatalog.Selection s){return false;}
        },config.rules(),config.admission(),cooldown,clock::monotonic,clock::wall,loop,services::failure);
        social=new HostedSocialRuntime(this,host,loop,logCommands);media=new HostedMedia(host,this::lives,maps,random,loop);media.joinAudio(()->metadata,this::mode,()->musicEnabled,services::failure);
        if(runtimeFactory!=null)runtime=Objects.requireNonNull(runtimeFactory.apply(host));
        if(combatFactory!=null)combatRuntime=Objects.requireNonNull(combatFactory.apply(host));
        if(entityFactory!=null){entities=Objects.requireNonNull(entityFactory.apply(host));construction=new HostedConstructionRuntime(this,host,config,constructionSettings);corpses=new HostedCorpseRuntime(this,host,config,metadata.environment().gravity(),entities.wireReady(),config.modeCode().equals("cctf"));prefabs=new HostedPrefabRuntime(this,host,prefabSettings,prefabRoots);if(mode instanceof CtfMode c)ctf=new HostedCtfRuntime(this,host,c,geometry,raw);
            lives.objectives(new ObjectiveDropRequests(()->ctf==null?Optional.empty():Optional.of((p,at,velocity)->CompletableFuture.completedFuture(ctf.drop(p,at,velocity,false))),entities.registry(),this::frameLoop,p->send(p,true,null),loop));
            inputRuntime.actions((p,packet)->construction.packet(p,packet)||prefabs.packet(p,packet)||entities.packet(p,packet)); }
    }
    private final class Effects implements TdmMode.Effects,ArenaMode.Effects {
        public void resetLifeCounters(){if(entities!=null){construction.mutations().clear();construction.safety().clear();prefabs.clear();entities.resetRound();corpses.reset();}binding.resetCounters();}public void matchStarted(){services.matchStarted();}public void gameplayMusic(){media().music().gameplay(musicEnabled);}public void rebuildResources(){if(entities!=null)entities.rebuild();services.rebuildResources();}
        public void matchEnded(Integer winner){services.matchEnded(winner);}public void endVote(double wall){services.endVote(wall);}public void scheduleEnd(Integer winner){services.scheduleEnd(winner);}public CompletionStage<Void> cancelEnd(){return services.cancelEnd();}
        public void recordDeath(Scoreboard.Player p){spawns.death(p.life);}public void escapeWatch(){services.escapeWatch();}public void escapeFailure(RuntimeException e){services.failure("escape",e);}public void timeoutMusic(){media().music().timeout();}public void vote(double wall){services.vote(wall);media().ballot().tick(wall);}public void cosmeticFailure(RuntimeException e){services.failure("mode cosmetic",e);}public boolean stopping(){return services.stopping();}
        public void respawn(PlayerLife p){lives.respawn(p.id());}public void respawnFailure(PlayerLife p,RuntimeException e){services.failure("arena respawn:"+p.id(),e);}
    }
    private final class LifeEffects implements LifeRuntime.Effects {
        private final LifeRuntime.Effects delegate;LifeEffects(LifeRuntime.Effects delegate){this.delegate=delegate;}
        public void beforeSpawn(PlayerLife p){if(ctf!=null)ctf.retire(p);if(entities!=null){entities.effects().extinguish(p.id());construction.mutations().cancel(p);prefabs.forget(p);corpses.beforeSpawn(p);}delegate.beforeSpawn(p);}public void spawned(PlayerLife p){delegate.spawned(p);events.offer(ModeEvents.Event.of("on_player_spawn",binding.row(p)));}
        public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){if(corpses!=null)corpses.died(v);delegate.died(v,k,weapon,presentation,enemy);}
        public void damaged(LifeRuntime.DamageEvidence d){if(conduct!=null)conduct.harm(d);delegate.damaged(d);}public void healed(LifeRuntime.HealingEvidence h){delegate.healed(h);}public void beforeDeath(LifeRuntime.DeathEvidence d){delegate.beforeDeath(d);}
        public void transitionCredited(PlayerLife v,PlayerLife a,int type){delegate.transitionCredited(v,a,type);}public void forget(PlayerLife p){delegate.forget(p);}public void transitionForget(PlayerLife p){delegate.transitionForget(p);}public void respawnFailed(PlayerLife p,RuntimeException error){delegate.respawnFailed(p,error);}
    }
    @Override public InitialInfo configureInitialInfo(InitialInfo p,int capability){
        if(!(mode instanceof CtfMode c))return p;var disabled=new TreeSet<>(p.disabledTools());if(c.settings().classic())disabled.add(22);
        return new InitialInfo(p.identity(),p.mode(),p.map(),p.features(),p.blockWalletMultiplier(),p.blockHealthMultiplier(),List.copyOf(disabled),p.disabledClasses(),p.movementSpeedMultipliers(),p.ugcPrefabSets(),p.enablePlayerScore(),p.serverName(),p.groundColors(),p.loadouts(),c.settings().shootWithIntel()?1:0,p.friendlyFire(),p.customRules(),p.enableCorpseExplosion(),p.ugcMode());
    }
    public JoinScene scene(){owned();return scene;}public TransitionRoster transitionRoster(){owned();return transition;}public List<TeamState> teams(){owned();return teams;}public ModeEvents events(){owned();return events;}
    public MutableWorld world(){return lives().world();}public PlayerRoster roster(){return lives().roster();}public boolean spectator(int id){return lives().spectator(id);}public AdmissionPolicy.Context admissionContext(int id){return lives().admissionContext(id);}
    public JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){
        host();if(config.modeCode().equals("cctf")&&(request.team()==2||request.team()==3))selection=classicSelection(selection==null?ClassCatalog.normalize(5,List.of(),config.rules()):selection);var admission=lives.admit(id,request,selection,capability);var player=lives.player(id);teams.stream().filter(t->t.id==player.team()).findFirst().ifPresent(t->t.add(player));return admission;
    }
    public void beforeReplay(int id){lives().beforeReplay(id);}
    public List<GamePacket> reveal(int id){
        var packets=new ArrayList<>(lives().reveal(id));var target=host().scorePeer(id).orElseThrow();
        var collecting=new Scoreboard.Peer(){public boolean inGame(){return target.inGame();}public Optional<Set<Integer>> knownPlayers(){return target.knownPlayers();}public void send(GamePacket p){packets.add(p);}};
        board.reveal(collecting);if(entities!=null){packets.addAll(entities.reveal(id));packets.addAll(corpses.catchup());}mode.reveal(collecting,()->services.reveal(lives.player(id),packets::add));media().ballot().reveal(packets::add);return List.copyOf(packets);
    }
    public void input(int id,LiveClientInput input,int receivedLoop){if(inputs().allow(lives().player(id),MovementPacketRates.Kind.CLIENT_DATA,receivedLoop))lives().input(id,input,receivedLoop);}
    public boolean clockAllowed(int id,int serverLoop){return lives().players().stream().filter(p->p.id()==id).findFirst().map(p->inputs().allow(p,MovementPacketRates.Kind.CLOCK_SYNC,serverLoop)).orElse(true);}
    public void position(int id,LivePositionReport report,int serverLoop){inputs().position(lives().player(id),report,serverLoop);}public void roundTripMillis(int id,Double value){lives().roundTripMillis(id,value);}public void actionLink(int id,ActionClock.Link link){lives().actionLink(id,link);}
    public void packet(int id,GamePacket packet){if(packet instanceof DropPickup){lives().packetAsync(id,packet).whenComplete((unused,error)->{if(error!=null)services.failure("objective drop",error);});return;}if(packet instanceof SimplePackets.ChatMessage c){social().chat(lives().player(id),c).whenComplete((unused,error)->{if(error!=null)services.failure("chat",error);});return;}lives().packet(id,packet);}
    public CompletionStage<Void> packetAsync(int id,GamePacket packet){return packet instanceof SimplePackets.ChatMessage c?social().chat(lives().player(id),c):lives().packetAsync(id,packet);}
    public void transitionDetach(int id){detach(id,true);}public void disconnected(int id){detach(id,false);}
    private void detach(int id,boolean transitionOnly){
        var player=lives().players().stream().filter(p->p.id()==id).findFirst().orElse(null);if(player==null)return;
        try{if(entities!=null){social.forget(player);conduct.forget(player);construction.forget(player);prefabs.forget(player);corpses.forget(player);entities.forget(player);}if(transitionOnly)lives.transitionDetach(id);else lives.disconnected(id);}finally{if(lives.players().stream().noneMatch(p->p==player)){for(var team:teams)team.remove(player);if(media!=null)media.forget(id);}}
    }
    @Override public void close(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Match outside owner");if(closed||closing)return;closing=true;Throwable error=null;
        var cleanup=new ArrayList<Runnable>();if(ctf!=null)cleanup.add(ctf::close);if(social!=null)cleanup.add(social::clear);if(conduct!=null)cleanup.add(conduct::clear);if(prefabs!=null)cleanup.add(prefabs::close);if(construction!=null)cleanup.add(construction::close);if(corpses!=null)cleanup.add(corpses::clear);cleanup.add(inputRuntime::close);if(runtime!=null)cleanup.add(runtime::close);if(entities!=null)cleanup.add(entities::close);if(combatRuntime!=null)cleanup.add(combatRuntime::close);if(media!=null)cleanup.add(()->media.ballot().close());cleanup.add(events::clear);cleanup.add(services::close);
        try{for(var operation:cleanup)try{operation.run();}catch(RuntimeException|Error failure){if(error==null)error=failure;else if(error!=failure)error.addSuppressed(failure);}}finally{closed=true;}
        if(error instanceof Error fatal)throw fatal;if(error instanceof RuntimeException ordinary)throw ordinary;
    }
}
