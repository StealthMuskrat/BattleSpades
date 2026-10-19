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

/** Production TDM/Arena composition. The scene state owns the prepared world; external services remain required.
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
    private final SceneConfig config;private final MapMetadata metadata;private final Services services;
    private final LifeRuntime.Clock clock;private final OwnerLoop loop;private final SourceRandom random;
    private final List<String> maps;private final boolean musicEnabled;private final double cooldown,respawn;
    private final List<TeamState> teams;private final ModeEvents events;private final Scoreboard board;
    private final ProfileTracker profiles;private final CombatEvidence combat;private final MatchLifeBinding binding;
    private final LifeRuntime lives;private final StandardSpawns spawns;private final BaseRoundMode mode;
    private final TransitionRoster transition;private final ConfiguredScene scene;
    private ApplicationHost host;private HostedMedia media;private HostedPlayerControls controls;private boolean closed,closing;
    private Function<ApplicationHost,HostedPlayerRuntime> runtimeFactory;private HostedPlayerRuntime runtime;
    /** Concrete factory binding, registered before the host or any actor exists. */
    public void runtime(Function<ApplicationHost,HostedPlayerRuntime> factory){owned();if(host!=null||runtimeFactory!=null)throw new IllegalStateException("Runtime must bind once before host attachment");runtimeFactory=Objects.requireNonNull(factory);}
    public HostedPlayerRuntime runtime(){owned();return Objects.requireNonNull(runtime,"Player runtime not bound");}
    public StandardMatchDomain(SceneConfig config,MapMetadata metadata,PreparedMap.Bound prepared,TomlTable raw,
            LifeRuntime.Clock clock,SourceRandom random,OwnerLoop loop,List<String> maps,LifeRuntime.Effects lifeEffects,LifeRuntime.Inputs inputs,Services services){
        this.config=Objects.requireNonNull(config);this.metadata=Objects.requireNonNull(metadata);this.clock=Objects.requireNonNull(clock);this.random=Objects.requireNonNull(random);this.loop=Objects.requireNonNull(loop);this.maps=List.copyOf(maps);this.services=Objects.requireNonNull(services);Objects.requireNonNull(lifeEffects);Objects.requireNonNull(inputs);
        if(!Set.of("tdm","arena").contains(config.modeCode())||!metadata.activeMode().equals(config.modeCode())||metadata.environment().sourceCrc()!=prepared.world().sourceCrc())throw new IllegalArgumentException("Standard match requires matching TDM/Arena map and config");
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
            public AdmissionPolicy.Context admissionContext(int id){return AdmissionPolicy.Context.ordinary(lives().players().stream().map(p->new AdmissionPolicy.Occupant(p.id(),p.name(),services.admin(p.id()),p.team())).toList());}
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
            public void reset(PlayerLife p){inputs.reset(p);}public void receive(PlayerLife p,LiveClientInput input,int receivedLoop){inputs.receive(p,input,receivedLoop);}
            public void packet(PlayerLife p,GamePacket packet){if(packet instanceof VotePacket)media().packet(p,packet);else inputs.packet(p,packet);}public void forget(PlayerLife p){inputs.forget(p);}
        });
        var effects=new Effects();
        mode=switch(config.modeCode()){
            case "tdm"->new TdmMode(TdmMode.Settings.from(raw,config.rules()),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,p->send(p,true,null),effects,loop);
            case "arena"->new ArenaMode(ArenaMode.Settings.from(raw),()->teams,this::rows,this::peer,board,clock::wall,clock::monotonic,events::size,()->60,p->send(p,true,null),effects,loop);
            default->throw new IllegalArgumentException("Standard mode");
        };
        spawns=new StandardSpawns(prepared.spawns(),mode,random,lives::players,clock::monotonic);
        transition=new TransitionRoster(lives,binding,()->teams,loop);scene=new ConfiguredScene(config,metadata,mode::sceneState,this);
    }
    private static double number(TomlTable raw,String key,double fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0)return n.doubleValue();throw new IllegalArgumentException(key+" must be finite and nonnegative");}
    private static TeamState team(int id,SceneConfig.Team t){return new TeamState(id,t.name(),t.color()>>>16&255,t.color()>>>8&255,t.color()&255);}
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Standard match outside active owner");}
    private ApplicationHost host(){owned();return Objects.requireNonNull(host,"Match host not attached");}
    public LifeRuntime lives(){owned();return lives;}public BaseRoundMode mode(){owned();return mode;}public StandardSpawns spawns(){owned();return spawns;}
    public double monotonic(){owned();return clock.monotonic();}
    public Scoreboard scoreboard(){owned();return board;}public MatchLifeBinding binding(){owned();return binding;}public CombatEvidence combat(){owned();return combat;}public ProfileTracker profiles(){owned();return profiles;}
    public HostedMedia media(){owned();return Objects.requireNonNull(media,"Media not attached");}public HostedPlayerControls controls(){owned();return Objects.requireNonNull(controls,"Controls not attached");}
    private Map<Integer,Scoreboard.Player> rows(){return binding.players();}
    private Optional<Scoreboard.Peer> peer(PlayerLife p){return p.bot()||lives.players().stream().noneMatch(current->current==p)?Optional.empty():host().scorePeer(p.id());}
    private static Integer knownId(GamePacket p){return switch(p){case SimplePackets.SetScore s when s.typeValue()==1->s.specifier();case SimplePackets.SetColor c->c.player_id();case SimplePackets.PlayerLeft l->l.player_id();default->null;};}
    private void send(GamePacket p,boolean reliable,Integer known){var delivery=reliable?Delivery.RELIABLE:Delivery.UNRELIABLE;if(known==null)host().broadcast(p,delivery);else host().broadcastKnown(p,delivery,known);}
    @Override public void attach(ApplicationHost application){
        owned();if(host!=null||!lives.players().isEmpty())throw new IllegalStateException("Match must attach once before admission");host=Objects.requireNonNull(application);
        var byId=new LinkedHashMap<Integer,TeamState>();for(var t:teams)byId.put(t.id,t);
        controls=new HostedPlayerControls(host,lives,byId,events,new HostedPlayerControls.Mode(){
            public Object eventPlayer(PlayerLife p){return binding.row(p);}public boolean allowsTeam(PlayerLife p,int team){return true;}public boolean locked(int team){return false;}public boolean assignsTeams(){return false;}public void retireDeployables(PlayerLife p){services.retireDeployables(p);}
            public ClassCatalog.Selection normalize(int id,List<Integer> tools,List<String> prefabs,List<Integer> ugc,int fallback){return ClassCatalog.normalize(id,tools,prefabs,ugc,fallback,config.rules().disabledTools());}
            public boolean selectable(int id){return AdmissionPolicy.classSelectable(id,config.rules(),config.mode(),false,false);}public boolean allowsSelection(PlayerLife p,ClassCatalog.Selection s){return true;}public boolean liveUgc(PlayerLife p,ClassCatalog.Selection s){return false;}
        },config.rules(),config.admission(),cooldown,clock::monotonic,clock::wall,loop,services::failure);
        media=new HostedMedia(host,this::lives,maps,random,loop);media.joinAudio(()->metadata,this::mode,()->musicEnabled,services::failure);
        if(runtimeFactory!=null)runtime=Objects.requireNonNull(runtimeFactory.apply(host));
    }
    private final class Effects implements TdmMode.Effects,ArenaMode.Effects {
        public void resetLifeCounters(){binding.resetCounters();}public void matchStarted(){services.matchStarted();}public void gameplayMusic(){media().music().gameplay(musicEnabled);}public void rebuildResources(){services.rebuildResources();}
        public void matchEnded(Integer winner){services.matchEnded(winner);}public void endVote(double wall){services.endVote(wall);}public void scheduleEnd(Integer winner){services.scheduleEnd(winner);}public CompletionStage<Void> cancelEnd(){return services.cancelEnd();}
        public void recordDeath(Scoreboard.Player p){spawns.death(p.life);}public void escapeWatch(){services.escapeWatch();}public void escapeFailure(RuntimeException e){services.failure("escape",e);}public void timeoutMusic(){media().music().timeout();}public void vote(double wall){services.vote(wall);media().ballot().tick(wall);}public void cosmeticFailure(RuntimeException e){services.failure("mode cosmetic",e);}public boolean stopping(){return services.stopping();}
        public void respawn(PlayerLife p){lives.respawn(p.id());}public void respawnFailure(PlayerLife p,RuntimeException e){services.failure("arena respawn:"+p.id(),e);}
    }
    private final class LifeEffects implements LifeRuntime.Effects {
        private final LifeRuntime.Effects delegate;LifeEffects(LifeRuntime.Effects delegate){this.delegate=delegate;}
        public void beforeSpawn(PlayerLife p){delegate.beforeSpawn(p);}public void spawned(PlayerLife p){delegate.spawned(p);events.offer(ModeEvents.Event.of("on_player_spawn",binding.row(p)));}
        public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){delegate.died(v,k,weapon,presentation,enemy);}
        public void damaged(LifeRuntime.DamageEvidence d){delegate.damaged(d);}public void healed(LifeRuntime.HealingEvidence h){delegate.healed(h);}public void beforeDeath(LifeRuntime.DeathEvidence d){delegate.beforeDeath(d);}
        public void transitionCredited(PlayerLife v,PlayerLife a,int type){delegate.transitionCredited(v,a,type);}public void forget(PlayerLife p){delegate.forget(p);}public void transitionForget(PlayerLife p){delegate.transitionForget(p);}public void respawnFailed(PlayerLife p,RuntimeException error){delegate.respawnFailed(p,error);}
    }
    public JoinScene scene(){owned();return scene;}public TransitionRoster transitionRoster(){owned();return transition;}public List<TeamState> teams(){owned();return teams;}public ModeEvents events(){owned();return events;}
    public MutableWorld world(){return lives().world();}public PlayerRoster roster(){return lives().roster();}public boolean spectator(int id){return lives().spectator(id);}public AdmissionPolicy.Context admissionContext(int id){return lives().admissionContext(id);}
    public JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){
        host();var admission=lives.admit(id,request,selection,capability);var player=lives.player(id);teams.stream().filter(t->t.id==player.team()).findFirst().ifPresent(t->t.add(player));return admission;
    }
    public void beforeReplay(int id){lives().beforeReplay(id);}
    public List<GamePacket> reveal(int id){
        var packets=new ArrayList<>(lives().reveal(id));var target=host().scorePeer(id).orElseThrow();
        var collecting=new Scoreboard.Peer(){public boolean inGame(){return target.inGame();}public Optional<Set<Integer>> knownPlayers(){return target.knownPlayers();}public void send(GamePacket p){packets.add(p);}};
        board.reveal(collecting);mode.reveal(collecting,()->services.reveal(lives.player(id),packets::add));media().ballot().reveal(packets::add);return List.copyOf(packets);
    }
    public void input(int id,LiveClientInput input,int receivedLoop){lives().input(id,input,receivedLoop);}public void roundTripMillis(int id,Double value){lives().roundTripMillis(id,value);}public void actionLink(int id,ActionClock.Link link){lives().actionLink(id,link);}
    public void packet(int id,GamePacket packet){lives().packet(id,packet);}public CompletionStage<Void> packetAsync(int id,GamePacket packet){return lives().packetAsync(id,packet);}
    public void transitionDetach(int id){detach(id,true);}public void disconnected(int id){detach(id,false);}
    private void detach(int id,boolean transitionOnly){
        var player=lives().players().stream().filter(p->p.id()==id).findFirst().orElse(null);if(player==null)return;
        try{if(transitionOnly)lives.transitionDetach(id);else lives.disconnected(id);}finally{if(lives.players().stream().noneMatch(p->p==player)){for(var team:teams)team.remove(player);if(media!=null)media.forget(id);}}
    }
    @Override public void close(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Match outside owner");if(closed||closing)return;closing=true;Throwable error=null;
        var cleanup=new ArrayList<Runnable>();if(runtime!=null)cleanup.add(runtime::close);if(media!=null)cleanup.add(()->media.ballot().close());cleanup.add(events::clear);cleanup.add(services::close);
        try{for(var operation:cleanup)try{operation.run();}catch(RuntimeException|Error failure){if(error==null)error=failure;else if(error!=failure)error.addSuppressed(failure);}}finally{closed=true;}
        if(error instanceof Error fatal)throw fatal;if(error instanceof RuntimeException ordinary)throw ordinary;
    }
}
