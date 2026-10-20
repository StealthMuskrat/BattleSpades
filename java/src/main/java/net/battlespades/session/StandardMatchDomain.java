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

/** Production standard round and capture-zone composition. The scene state owns the prepared world; external services remain required.
 * Attach before admission, and await mode deactivation before disposal. This does not supply missing server domains.
 */
public final class StandardMatchDomain implements HostedSceneState.HostBoundDomain,ConfiguredScene.Gameplay {
    public interface Services {
        boolean admin(int playerId);boolean stopping();
        CompletionStage<Void> plugin(ModeEvents.Event event);
        void matchStarted();void rebuildResources();void matchEnded(Integer winner);
        void endVote(double wall);void vote(double wall);void scheduleEnd(Integer winner);CompletionStage<Void> cancelEnd();
        void zombieRound(HostedZombieRuntime.RoundObservation event);void demolitionRepair(PlayerLife player,int count);void vipRound(HostedVipRuntime.RoundObservation event);
        void hillEvent(HostedZoneRuntime.HillEvent event);void carryObservation(HostedCarryRuntime.Observation event);
        void escapeWatch();void reveal(PlayerLife player,Consumer<GamePacket> send);
        void resetResultBaselines();void scheduleResults(Integer winner);
        void retireDeployables(PlayerLife player);void failure(String label,Throwable error);
        void close();
    }
    private final Thread owner=Thread.currentThread();
    private final TomlTable raw;private final SpawnGeometry geometry;private HostedZombieRuntime zombie;private HostedDemolitionRuntime demolition;private HostedVipRuntime vip;private HostedCarryRuntime carry;private HostedCtfRuntime ctf;private HostedZoneRuntime zones;private HostedEscapeWatch escapeWatch;
    public Optional<HostedZombieRuntime> zombie(){owned();return Optional.ofNullable(zombie);}
    public void zombieRound(HostedZombieRuntime.RoundObservation event){owned();services.zombieRound(event);}
    public boolean hardcore(){return Hardcore.enabled(config.modeCode());}
    public String baseCode(){return Hardcore.base(config.modeCode());}
    @Override public int requiredCapability(){return hardcore()?3:0;}
    public Optional<HostedDemolitionRuntime> demolition(){owned();return Optional.ofNullable(demolition);}
    public Optional<HostedVipRuntime> vip(){owned();return Optional.ofNullable(vip);}
    public void demolitionRepair(PlayerLife player,int count){owned();services.demolitionRepair(player,count);}
    public void vipRound(HostedVipRuntime.RoundObservation event){owned();services.vipRound(event);}
    public void restoreGameplayMusic(){media().music().gameplay(musicEnabled);}
    public void timeoutMusic(){media().music().timeout();}
    public Optional<HostedCarryRuntime> carry(){owned();return Optional.ofNullable(carry);}
    public void carryObservation(HostedCarryRuntime.Observation event){owned();services.carryObservation(event);}
    public boolean friendlyFire(){owned();return config.friendlyFire();}
    public void openMapVote(){owned();try{services.vote(wall());if(!media().ballot().active())media().ballot().start(maps,wall());}catch(RuntimeException e){failure("objective map vote",e);}}
    public Optional<HostedZoneRuntime> zones(){owned();return Optional.ofNullable(zones);}
    public HostedEscapeWatch escapeWatch(){owned();return Objects.requireNonNull(escapeWatch,"Escape watch not attached");}
    public Optional<HostedCtfRuntime> ctf(){owned();return Optional.ofNullable(ctf);}
    public void hillEvent(HostedZoneRuntime.HillEvent event){owned();services.hillEvent(event);}
    public boolean shootWithIntel(){owned();return mode instanceof CtfMode c&&c.settings().shootWithIntel();}
    public static SceneConfig modeConfig(SceneConfig config){
        String base=Hardcore.base(config.modeCode());var rules=Hardcore.enabled(config.modeCode())?Hardcore.rules(config.rules()):base.equals("cctf")?config.rules().compatibility(Map.of("RULE_ENABLE_WEAPON_CLASSIC_SMG",true,"RULE_ENABLE_WEAPON_CLASSIC_SHOTGUN",true)):config.rules();
        return new SceneConfig(config.name(),config.port(),config.steamId(),config.mode(),config.mapName(),config.sameTeamCollision(),base.equals("zom")?false:config.friendlyFire(),config.fallDamage(),config.scoreLimit(),config.fogColor(),config.skybox(),base.equals("zom")?new SceneConfig.Team("ZOMBIE_TEAM",config.team1().color()):config.team1(),base.equals("zom")?new SceneConfig.Team("SURVIVOR_TEAM",config.team2().color()):config.team2(),rules,config.modeCode(),config.admission());
    }
    private List<Integer> selectionDisabled(){var blocked=new TreeSet<>(config.rules().disabledTools());if(config.modeCode().equals("cctf"))blocked.add(22);return List.copyOf(blocked);}
    private ClassCatalog.Selection classicSelection(ClassCatalog.Selection selection){return ClassCatalog.normalize(5,selection.loadout(),selection.prefabs(),selection.ugcTools(),5,selectionDisabled());}

    private final boolean logCommands;private HostedSocialRuntime social;
    public HostedSocialRuntime social(){owned();return Objects.requireNonNull(social);}
    public boolean stopping(){owned();return services.stopping();}
    private final HostedConductRuntime.Settings conductSettings;private HostedConductRuntime conduct;
    public HostedConductRuntime conduct(){owned();return Objects.requireNonNull(conduct);}
    public boolean admin(PlayerLife p){owned();return lives.players().stream().anyMatch(current->current==p)&&(services.admin(p.id())||host!=null&&host.administration().map(a->a.admin(p.id())).orElse(false));}
    private final HostedConstructionRuntime.Settings constructionSettings;private final HostedPrefabRuntime.Settings prefabSettings;
    private List<java.nio.file.Path> prefabRoots=List.of(java.nio.file.Path.of("prefabs").toAbsolutePath());private HostedPrefabRuntime prefabs;
    public void prefabRoots(List<java.nio.file.Path> roots){owned();if(host!=null)throw new IllegalStateException("Prefab roots bind before attachment");prefabRoots=List.copyOf(roots);}
    public HostedPrefabRuntime prefabs(){owned();return Objects.requireNonNull(prefabs);}
    public void failure(String label,Throwable error){owned();services.failure(label,error);}
    public MapMetadata metadata(){owned();return metadata;}public String modeCode(){owned();return config.modeCode();}
    public String mapName(){owned();return config.mapName();}
    public void fogColor(int rgb){owned();scene.fogOverride(rgb);}
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
    private ApplicationHost host;private HostedRoundResults roundResults;private HostedMedia media;private HostedPlayerControls controls;private boolean closed,closing;
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
    public Optional<HostedPlayerRuntime> playerRuntime(){owned();return Optional.ofNullable(runtime);}
    public StandardMatchDomain(SceneConfig requestedConfig,MapMetadata metadata,PreparedMap.Bound prepared,TomlTable raw,
            LifeRuntime.Clock clock,SourceRandom random,OwnerLoop loop,List<String> maps,LifeRuntime.Effects lifeEffects,HostedInputRuntime.Ports inputs,Services services){
        var config=modeConfig(requestedConfig);this.raw=raw;this.geometry=prepared.spawns();
        logCommands=raw.getBoolean("admin.log_commands",()->true);conductSettings=HostedConductRuntime.Settings.from(raw);prefabSettings=HostedPrefabRuntime.Settings.from(raw);constructionSettings=HostedConstructionRuntime.Settings.from(raw);this.config=Objects.requireNonNull(config);this.metadata=Objects.requireNonNull(metadata);this.clock=Objects.requireNonNull(clock);this.random=Objects.requireNonNull(random);this.loop=Objects.requireNonNull(loop);this.maps=List.copyOf(maps);this.services=Objects.requireNonNull(services);Objects.requireNonNull(lifeEffects);Objects.requireNonNull(inputs);
        if(!StandardMatchFactory.codes().contains(config.modeCode())||!metadata.activeMode().equals(Hardcore.base(config.modeCode()))||metadata.environment().sourceCrc()!=prepared.world().sourceCrc())throw new IllegalArgumentException("Standard match requires matching prepared mode and config");
        cooldown=5;respawn=number(raw,"game.respawn_time",5);musicEnabled=raw.getBoolean("audio.mode_start_music",()->true);
        teams=List.of(team(2,config.team1()),team(3,config.team2()));
        events=new ModeEvents(8192,1024,this::mode,services::plugin,services::failure,loop);
        profiles=new ProfileTracker(()->new ProfileTracker.Context(false,config.modeCode(),mode().clock().ended),(a,b)->a.team()!=b.team());
        board=new Scoreboard(this::rows,()->teams,()->Optional.of(host().scorePeers()),()->mode().clock().ended,services::stopping,random,new Scoreboard.Effects(){
            public void broadcast(GamePacket p,boolean reliable,Integer known){send(p,reliable,known);}public void scoreChanged(Scoreboard.Player p,int reason){profiles.scoreChanged(p,reason);}public void resetProfileBaseline(Scoreboard.Player p){profiles.resetBaseline(p);}public void resetResultBaselines(){if(host!=null)host.results().ifPresent(r->r.resetScoreboard(StandardMatchDomain.this));services.resetResultBaselines();}public void scheduleResults(Integer winner){host().results().ifPresent(r->r.schedule(StandardMatchDomain.this,winner));services.scheduleResults(winner);}
        });
        combat=new CombatEvidence(()->new CombatEvidence.Context(!mode().clock().ended,true,false,null),this::rows,(a,b)->a.team()!=b.team(),board);
        binding=new MatchLifeBinding(this::lives,new LifeEffects(lifeEffects),profiles,combat,events,clock::monotonic);
        lives=new LifeRuntime(prepared.world(),config.rules(),config.modeCode(),clock,new LifeRuntime.Policy(){
            public AdmissionPolicy.Context admissionContext(int id){return new AdmissionPolicy.Context(lives().players().stream().map(p->new AdmissionPolicy.Occupant(p.id(),p.name(),admin(p),p.team())).toList(),null,new AdmissionPolicy.ModePolicy(){public java.util.function.IntUnaryOperator prepareTeam(){return zombie==null?null:team->zombie.joinTeam();}public boolean customClassPolicy(){return hardcore()||zombie!=null;}public ClassCatalog.Selection prepareSelection(int team,ClassCatalog.Selection s){return zombie!=null?zombie.selection(team,s):hardcore()?Hardcore.normalize(Hardcore.CLASSES.contains(s.classId())?s.classId():5,s.loadout()):vip!=null&&(team==2||team==3)?vip.ordinary(s):config.modeCode().equals("cctf")&&(team==2||team==3)?classicSelection(s):s;}});}
            public void prepareSpawn(PlayerLife p){if(vip!=null)vip.prepareSpawn(p);else if(hardcore()&&p.team()!=0&&!(zombie!=null&&p.team()==2))p.apply(Hardcore.normalize(p.selection().classId(),p.selection().loadout()));}
            public Vector spawnPoint(PlayerLife p){return zombie!=null?zombie.spawn(p).orElseGet(()->spawns().spawn(p)):vip!=null?vip.spawn(p).orElseGet(()->spawns().spawn(p)):spawns().spawn(p);}public Optional<Vector> spawnOrientation(PlayerLife p){return Optional.empty();}
            public boolean canRespawn(PlayerLife p){return zombie!=null?!mode().clock().ended:vip!=null?vip.canRespawn(p):!(mode() instanceof ArenaMode arena)||arena.canRespawn(p);}
            public double respawnDelay(PlayerLife p){return zombie!=null?zombie.respawnDelay(p,respawn):vip!=null?vip.respawnDelay(p,respawn):mode() instanceof ArenaMode arena?arena.respawnTime(respawn):respawn;}
            public double walletScale(){return config.rules().number("RULE_CHARACTER_BLOCK_WALLETS");}
            public int teamColor(PlayerLife p){return teams.stream().filter(t->t.id==p.team()).findFirst().map(TeamState::color).orElse(0x707070);}
            public int score(PlayerLife p){return Math.toIntExact(binding.row(p).score);}public boolean opponents(PlayerLife a,PlayerLife b){return a.team()!=b.team();}
            public int deathType(PlayerLife v,PlayerLife k,int type){return vip!=null&&vip.active()&&vip.liveVip(v)?26:type;}
            public Optional<LifeRuntime.DamageModifier> damageModifier(){return zombie!=null?Optional.of((v,amount,s,type)->s!=null&&s!=v&&s.team()==v.team()?0:amount):vip!=null?Optional.of((v,amount,s,type)->vip.incoming(v,amount,s)):mode() instanceof ArenaMode arena?Optional.of((v,amount,s,type)->arena.damage(amount)):Optional.empty();}
            public boolean combatScoringActive(){return !mode().clock().ended;}
        },binding,new LifeRuntime.Messages(){
            public void broadcast(GamePacket p){send(p,true,knownId(p));}public void owner(int id,GamePacket p){host().sendPlayer(id,p);}public boolean inGame(int id){return host().inGame(id);}
        },new LifeRuntime.Inputs(){
            public void reset(PlayerLife p){inputRuntime.reset(p);}public void receive(PlayerLife p,LiveClientInput input,int receivedLoop){inputRuntime.receive(p,input,receivedLoop);}
            public void packet(PlayerLife p,GamePacket packet){if(packet instanceof VotePacket)media().packet(p,packet);else inputRuntime.packet(p,packet);}public void forget(PlayerLife p){inputRuntime.forget(p);}
        });
        var effects=new Effects();
        mode=switch(Hardcore.base(config.modeCode())){
            case "tdm"->new TdmMode(TdmMode.Settings.from(raw,config.rules()),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "arena"->new ArenaMode(ArenaMode.Settings.from(raw),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,()->60,p->send(p,true,null),effects,loop);
            case "ctf","cctf"->new CtfMode(CtfMode.Settings.from(raw,config.rules(),Hardcore.base(config.modeCode())),()->Objects.requireNonNull(ctf,"CTF owner not attached"),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "tc","mh"->new ZoneMode(ZoneMode.Settings.from(raw,config.rules(),Hardcore.base(config.modeCode())),()->Objects.requireNonNull(zones,"Zone owner not attached"),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "dia","oc"->new CarryMode(CarryMode.Settings.from(raw,config.rules(),Hardcore.base(config.modeCode())),()->Objects.requireNonNull(carry,"Carry objectives not attached"),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "dem"->new DemolitionMode(DemolitionMode.Settings.from(raw,config.rules()),()->Objects.requireNonNull(demolition),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "vip"->new VipMode(VipMode.Settings.from(raw,config.rules()),()->Objects.requireNonNull(vip),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "zom"->new ZombieMode(ZombieMode.Settings.from(raw,config.rules()),()->Objects.requireNonNull(zombie),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
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
        owned();if(host!=null||!lives.players().isEmpty())throw new IllegalStateException("Match must attach once before admission");host=Objects.requireNonNull(application);roundResults=host.results().orElse(null);escapeWatch=new HostedEscapeWatch(this,host,raw);conduct=new HostedConductRuntime(this,host,conductSettings);
        var byId=new LinkedHashMap<Integer,TeamState>();for(var t:teams)byId.put(t.id,t);
        controls=new HostedPlayerControls(host,lives,byId,events,new HostedPlayerControls.Mode(){
            public Object eventPlayer(PlayerLife p){return binding.row(p);}public boolean allowsTeam(PlayerLife p,int team){return zombie!=null?zombie.allowsTeam(p,team):vip==null||vip.allowsTeam(p);}public boolean locked(int team){return zombie!=null&&(team==2?!zombie.active():team==3&&zombie.active());}public boolean assignsTeams(){return zombie!=null;}public void retireDeployables(PlayerLife p){if(entities!=null)entities.actions().retire(p);services.retireDeployables(p);}
            public ClassCatalog.Selection normalize(int id,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback){return hardcore()?Hardcore.normalize(id,tools):ClassCatalog.normalize(id,tools,prefabs,ugc,fallback,selectionDisabled());}
            public boolean selectable(int id){return zombie!=null&&id==4||hardcore()?Hardcore.CLASSES.contains(id)||zombie!=null&&id==4:AdmissionPolicy.classSelectable(id,config.rules(),config.mode(),false,false);}public boolean allowsSelection(PlayerLife p,ClassCatalog.Selection s){return zombie!=null?zombie.allowsSelection(p,s):hardcore()?(p.team()==2||p.team()==3)&&Hardcore.CLASSES.contains(s.classId())&&!(vip!=null&&vip.vip(p.team()).filter(v->v==p).isPresent()):vip==null&&(!config.modeCode().equals("cctf")||(p.team()==2||p.team()==3)&&s.equals(classicSelection(s)));}public boolean liveUgc(PlayerLife p,ClassCatalog.Selection s){return false;}
        },config.rules(),config.admission(),cooldown,clock::monotonic,clock::wall,loop,services::failure);
        social=new HostedSocialRuntime(this,host,loop,logCommands);host.administration().ifPresent(a->new HostedAdminCommands(this,host,a));new HostedServerCommands(this,host);media=new HostedMedia(host,this::lives,maps,random,loop);media.joinAudio(()->metadata,this::mode,()->musicEnabled,services::failure);
        if(runtimeFactory!=null)runtime=Objects.requireNonNull(runtimeFactory.apply(host));
        if(combatFactory!=null)combatRuntime=Objects.requireNonNull(combatFactory.apply(host));
        if(entityFactory!=null){entities=Objects.requireNonNull(entityFactory.apply(host));construction=new HostedConstructionRuntime(this,host,config,constructionSettings);corpses=new HostedCorpseRuntime(this,host,config,hardcore()?1:metadata.environment().gravity(),entities.wireReady(),hardcore()||config.modeCode().equals("cctf"));prefabs=new HostedPrefabRuntime(this,host,prefabSettings,prefabRoots);if(mode instanceof CtfMode c)ctf=new HostedCtfRuntime(this,host,c,geometry,raw);if(mode instanceof ZoneMode z)zones=new HostedZoneRuntime(this,host,z,geometry,raw,loop);if(mode instanceof CarryMode c)carry=c.settings().code().equals("dia")?new HostedDiamondRuntime(this,host,c,geometry,raw,config.rules(),new SourceRandom(new java.security.SecureRandom().nextLong())):new HostedOccupationRuntime(this,host,c,geometry,raw,config.rules());
            if(mode instanceof ZombieMode z)zombie=new HostedZombieRuntime(this,host,z,loop,random,config.rules());
            if(mode instanceof DemolitionMode d)demolition=new HostedDemolitionRuntime(this,host,d,geometry);if(mode instanceof VipMode v)vip=new HostedVipRuntime(this,host,v,loop,random,config.rules(),geometry);
            lives.objectives(new ObjectiveDropRequests(()->carry!=null?Optional.of((p,at,velocity)->CompletableFuture.completedFuture(carry.drop(p,at,velocity,false,null))):ctf==null?Optional.empty():Optional.of((p,at,velocity)->CompletableFuture.completedFuture(ctf.drop(p,at,velocity,false))),entities.registry(),this::frameLoop,p->send(p,true,null),loop));
            inputRuntime.actions((p,packet)->construction.packet(p,packet)||prefabs.packet(p,packet)||entities.packet(p,packet)); }
    }
    private final class Effects implements TdmMode.Effects,ArenaMode.Effects {
        public void resetLifeCounters(){host().results().ifPresent(r->r.beforeReset(StandardMatchDomain.this));if(entities!=null){construction.mutations().clear();construction.safety().clear();prefabs.clear();entities.resetRound();corpses.reset();}binding.resetCounters();}public void matchStarted(){host().results().ifPresent(r->r.begin(StandardMatchDomain.this));services.matchStarted();}public void gameplayMusic(){media().music().gameplay(musicEnabled);}public void rebuildResources(){if(entities!=null)entities.rebuild();services.rebuildResources();}
        public void matchEnded(Integer winner){services.matchEnded(winner);}public void endVote(double wall){services.endVote(wall);}public void scheduleEnd(Integer winner){services.scheduleEnd(winner);}public CompletionStage<Void> cancelEnd(){return services.cancelEnd();}
        public void recordDeath(Scoreboard.Player p){spawns.death(p.life);}public void escapeWatch(){StandardMatchDomain.this.escapeWatch().tick();services.escapeWatch();}public void escapeFailure(RuntimeException e){services.failure("escape",e);}public void timeoutMusic(){media().music().timeout();}public void vote(double wall){services.vote(wall);media().ballot().tick(wall);}public void cosmeticFailure(RuntimeException e){services.failure("mode cosmetic",e);}public boolean stopping(){return services.stopping();}
        public void respawn(PlayerLife p){lives.respawn(p.id());}public void respawnFailure(PlayerLife p,RuntimeException e){services.failure("arena respawn:"+p.id(),e);}
    }
    private final class LifeEffects implements LifeRuntime.Effects {
        private final LifeRuntime.Effects delegate;LifeEffects(LifeRuntime.Effects delegate){this.delegate=delegate;}
        public void beforeSpawn(PlayerLife p){if(carry!=null)carry.retire(p,null);if(escapeWatch!=null)escapeWatch.forget(p);if(zones!=null)zones.forget(p);if(ctf!=null)ctf.retire(p);if(entities!=null){entities.effects().extinguish(p.id());construction.mutations().cancel(p);prefabs.forget(p);corpses.beforeSpawn(p);}delegate.beforeSpawn(p);}public void spawned(PlayerLife p){if(zombie!=null)zombie.spawned(p);if(hardcore()&&(p.bot()||host.inGame(p.id())))grantSupplies(p,packet->host.sendPlayer(p.id(),packet));if(demolition!=null)demolition.lock(p);delegate.spawned(p);events.offer(ModeEvents.Event.of("on_player_spawn",binding.row(p)));}
        public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){if(escapeWatch!=null)escapeWatch.forget(v);if(corpses!=null)corpses.died(v,zombie!=null&&zombie.converting(v));delegate.died(v,k,weapon,presentation,enemy);}
        public void damaged(LifeRuntime.DamageEvidence d){if(conduct!=null)conduct.harm(d);delegate.damaged(d);}public void healed(LifeRuntime.HealingEvidence h){delegate.healed(h);}public void beforeDeath(LifeRuntime.DeathEvidence d){delegate.beforeDeath(d);}
        public void transitionCredited(PlayerLife v,PlayerLife a,int type){delegate.transitionCredited(v,a,type);}public void forget(PlayerLife p){delegate.forget(p);}public void transitionForget(PlayerLife p){delegate.transitionForget(p);}public void respawnFailed(PlayerLife p,RuntimeException error){delegate.respawnFailed(p,error);}
    }
    @Override public InitialInfo configureInitialInfo(InitialInfo p,int capability){p=objectiveInitial(p,capability);if(zombie!=null){var f=p.features();var features=new InitialInfo.Features(f.classic(),f.minimap(),f.sameTeamCollision(),f.maxDrawDistance(),f.colourPicker(),f.colourPalette(),f.deathcam(),f.sniperBeam(),f.spectator(),0,f.numericHp(),f.textureSkin(),f.beachZModifiable(),f.minimapHeightIcons(),f.fallOnWaterDamage());p=new InitialInfo(p.identity(),p.mode(),p.map(),features,p.blockWalletMultiplier(),p.blockHealthMultiplier(),p.disabledTools(),p.disabledClasses(),p.movementSpeedMultipliers(),p.ugcPrefabSets(),p.enablePlayerScore(),p.serverName(),p.groundColors(),p.loadouts(),p.allowShootingHoldingIntel(),0,p.customRules(),p.enableCorpseExplosion(),p.ugcMode());}return hardcore()?Hardcore.initial(p):p;}
    private InitialInfo objectiveInitial(InitialInfo p,int capability){
        if(!(mode instanceof CtfMode c))return p;var disabled=new TreeSet<>(p.disabledTools());if(c.settings().classic()&&!hardcore())disabled.add(22);
        return new InitialInfo(p.identity(),p.mode(),p.map(),p.features(),p.blockWalletMultiplier(),p.blockHealthMultiplier(),List.copyOf(disabled),p.disabledClasses(),p.movementSpeedMultipliers(),p.ugcPrefabSets(),p.enablePlayerScore(),p.serverName(),p.groundColors(),p.loadouts(),c.settings().shootWithIntel()?1:0,p.friendlyFire(),p.customRules(),p.enableCorpseExplosion(),p.ugcMode());
    }
    @Override public StateData configureStateData(StateData p,int capability){p=objectiveState(p,capability);if(zombie!=null){p=new StateData(p.playerId(),p.fogColor(),p.gravity(),p.lighting(),p.timeScale(),p.scoreLimit(),p.modeType(),0,zombieTeam(p.team1(),2),zombieTeam(p.team2(),3),true,p.lockSpectatorSwap(),p.prefabs(),p.entities(),p.cameraPoints(),p.cameraRotations(),p.hasMapEnded());}return hardcore()?Hardcore.state(p,zombie!=null):p;}
    private StateData.Team zombieTeam(StateData.Team t,int id){boolean active=zombie.active();return new StateData.Team(id==2?"ZOMBIE_TEAM":"SURVIVOR_TEAM",t.color(),t.score(),id==2?!active:active,id==2,false,false,t.infiniteBlocks(),id==2||active,t.lockedScore(),id==2?List.of(4):zombie.survivorClasses());}
    private StateData objectiveState(StateData p,int capability){
        if(mode instanceof DemolitionMode)return new StateData(p.playerId(),p.fogColor(),p.gravity(),p.lighting(),p.timeScale(),p.scoreLimit(),p.modeType(),p.teamHeadcountType(),withoutScores(p.team1()),withoutScores(p.team2()),p.lockTeamSwap(),p.lockSpectatorSwap(),p.prefabs(),p.entities(),p.cameraPoints(),p.cameraRotations(),p.hasMapEnded());
        if(!(mode instanceof ZoneMode z)||z.settings().territory())return p;
        return new StateData(p.playerId(),p.fogColor(),p.gravity(),p.lighting(),p.timeScale(),0,p.modeType(),p.teamHeadcountType(),withoutMaximum(p.team1()),withoutMaximum(p.team2()),p.lockTeamSwap(),p.lockSpectatorSwap(),p.prefabs(),p.entities(),p.cameraPoints(),p.cameraRotations(),p.hasMapEnded());
    }
    private static StateData.Team withoutScores(StateData.Team t){return new StateData.Team(t.name(),t.color(),t.score(),t.locked(),t.canSeeOther(),false,false,t.infiniteBlocks(),t.lockedClass(),t.lockedScore(),t.classes());}
    private static StateData.Team withoutMaximum(StateData.Team t){return new StateData.Team(t.name(),t.color(),t.score(),t.locked(),t.canSeeOther(),t.showScore(),false,t.infiniteBlocks(),t.lockedClass(),t.lockedScore(),t.classes());}
    public JoinScene scene(){owned();return scene;}public TransitionRoster transitionRoster(){owned();return transition;}public List<TeamState> teams(){owned();return teams;}public ModeEvents events(){owned();return events;}
    public MutableWorld world(){return lives().world();}public PlayerRoster roster(){return lives().roster();}public boolean spectator(int id){return lives().spectator(id);}public AdmissionPolicy.Context admissionContext(int id){return lives().admissionContext(id);}
    public JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){
        host();if(config.modeCode().equals("cctf")&&(request.team()==2||request.team()==3))selection=classicSelection(selection==null?ClassCatalog.normalize(5,List.of(),config.rules()):selection);if(hardcore()&&(request.team()==2||request.team()==3))selection=zombie!=null?zombie.selection(request.team(),selection==null?Hardcore.normalize(5,List.of()):selection):Hardcore.normalize(selection==null?5:selection.classId(),selection==null?List.of():selection.loadout());else if(vip!=null&&(request.team()==2||request.team()==3))selection=vip.ordinary(selection==null?ClassCatalog.normalize(6,List.of(),config.rules()):selection);var admission=lives.admit(id,request,selection,capability);var player=lives.player(id);host().results().ifPresent(r->r.admitted(this,player));teams.stream().filter(t->t.id==player.team()).findFirst().ifPresent(t->t.add(player));if(vip!=null||zombie!=null)events.offer(ModeEvents.Event.of("on_player_join",binding.row(player)));if(demolition!=null)demolition.lock(player);return admission;
    }
    private final Map<PlayerLife,Long> supplied=new IdentityHashMap<>();
    private void grantSupplies(PlayerLife p,Consumer<GamePacket> send){if(!p.alive()||p.selection().classId()!=5||Objects.equals(supplied.get(p),p.generation()))return;p.inventory().restock(3,monotonic(),p.bot());send.accept(new SimplePackets.Restock(p.id(),3));supplied.put(p,p.generation());}
    public void hardcoreRules(PlayerLife p,Consumer<GamePacket> send){if(!hardcore()||p.bot())return;for(var line:List.of("Classes: Deuce=Soldier, Commando=Heavy, Marksman=Recon, Miner=Demo, Medic=Medic.","Choose frag or fire grenades; everyone also carries a shovel and parachute.","Heavy: body armour. Medic: heal after 3s safe. Soldier: extra spawn ammunition.","Movement: one-block automatic steps, two-block jump ledges; no super jumps.","Teabag an enemy body: 3 crouches within 0.5s each = 2 points, once per death.","No gravestones or corpse explosions. /hcrules repeats this help."))send.accept(new SimplePackets.ChatMessage(255,2,line));}
    public void beforeReplay(int id){lives().beforeReplay(id);}
    public List<GamePacket> reveal(int id){
        var packets=new ArrayList<>(lives().reveal(id));var target=host().scorePeer(id).orElseThrow();
        var collecting=new Scoreboard.Peer(){public boolean inGame(){return target.inGame();}public Optional<Set<Integer>> knownPlayers(){return target.knownPlayers();}public void send(GamePacket p){packets.add(p);}};
        board.reveal(collecting);if(entities!=null){packets.addAll(entities.reveal(id));packets.addAll(corpses.catchup());}mode.reveal(collecting,()->{escapeWatch().reveal(packets::add);services.reveal(lives.player(id),packets::add);});if(carry!=null)carry.startCue(lives.player(id),packets::add);if(demolition!=null)demolition.revealLock(lives.player(id),packets::add);if(vip!=null)vip.lockedOut(lives.player(id),packets::add);if(zombie!=null)zombie.reveal(lives.player(id),packets::add);if(hardcore()){grantSupplies(lives.player(id),packets::add);hardcoreRules(lives.player(id),packets::add);}media().ballot().reveal(packets::add);return List.copyOf(packets);
    }
    public void input(int id,LiveClientInput input,int receivedLoop){if(inputs().allow(lives().player(id),MovementPacketRates.Kind.CLIENT_DATA,receivedLoop))lives().input(id,input,receivedLoop);}
    public boolean clockAllowed(int id,int serverLoop){return lives().players().stream().filter(p->p.id()==id).findFirst().map(p->inputs().allow(p,MovementPacketRates.Kind.CLOCK_SYNC,serverLoop)).orElse(true);}
    public void position(int id,LivePositionReport report,int serverLoop){inputs().position(lives().player(id),report,serverLoop);}public void roundTripMillis(int id,Double value){lives().roundTripMillis(id,value);}public void actionLink(int id,ActionClock.Link link){lives().actionLink(id,link);}
    public void packet(int id,GamePacket packet){if(packet instanceof DropPickup){lives().packetAsync(id,packet).whenComplete((unused,error)->{if(error!=null)services.failure("objective drop",error);});return;}if(packet instanceof SimplePackets.ChatMessage c){social().chat(lives().player(id),c).whenComplete((unused,error)->{if(error!=null)services.failure("chat",error);});return;}lives().packet(id,packet);}
    public CompletionStage<Void> packetAsync(int id,GamePacket packet){return packet instanceof SimplePackets.ChatMessage c?social().chat(lives().player(id),c):lives().packetAsync(id,packet);}
    public void transitionDetach(int id){detach(id,true);}public void disconnected(int id){detach(id,false);}
    private void detach(int id,boolean transitionOnly){
        var player=lives().players().stream().filter(p->p.id()==id).findFirst().orElse(null);if(player==null)return;
        try{host().results().ifPresent(r->r.depart(this,player));supplied.remove(player);if(escapeWatch!=null)escapeWatch.forget(player);if(zones!=null)zones.forget(player);if(carry!=null)carry.retire(player,null);if(entities!=null){social.forget(player);conduct.forget(player);construction.forget(player);prefabs.forget(player);corpses.forget(player);entities.forget(player);}if(transitionOnly)lives.transitionDetach(id);else lives.disconnected(id);}finally{if(lives.players().stream().noneMatch(p->p==player)){for(var team:teams)team.remove(player);if(media!=null)media.forget(id);}}
    }
    @Override public void close(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Match outside owner");if(closed||closing)return;if(roundResults!=null)roundResults.retire(this);closing=true;Throwable error=null;
        var cleanup=new ArrayList<Runnable>();if(zombie!=null)cleanup.add(zombie::close);if(demolition!=null)cleanup.add(demolition::close);if(vip!=null)cleanup.add(vip::close);if(ctf!=null)cleanup.add(ctf::close);if(zones!=null)cleanup.add(zones::close);if(carry!=null)cleanup.add(carry::close);if(social!=null)cleanup.add(social::clear);if(conduct!=null)cleanup.add(conduct::clear);if(prefabs!=null)cleanup.add(prefabs::close);if(construction!=null)cleanup.add(construction::close);if(corpses!=null)cleanup.add(corpses::clear);cleanup.add(inputRuntime::close);if(runtime!=null)cleanup.add(runtime::close);if(entities!=null)cleanup.add(entities::close);if(combatRuntime!=null)cleanup.add(combatRuntime::close);if(media!=null)cleanup.add(()->media.ballot().close());cleanup.add(events::clear);cleanup.add(services::close);
        try{for(var operation:cleanup)try{operation.run();}catch(RuntimeException|Error failure){if(error==null)error=failure;else if(error!=failure)error.addSuppressed(failure);}}finally{closed=true;}
        if(error instanceof Error fatal)throw fatal;if(error instanceof RuntimeException ordinary)throw ordinary;
    }
}
