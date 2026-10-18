// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.scene.ConfiguredScene;
import net.battlespades.session.*;
import net.battlespades.world.MutableWorld;

/** Common admission/death/respawn composition. Modes, identity, input timeline and external life effects are required ports.
 * No mode or gameplay-input implementation is synthesized by this component; the ordinary launcher remains gated.
 */
public final class LifeRuntime implements ConfiguredScene.Gameplay {
    public interface Clock {double wall();double monotonic();}
    @FunctionalInterface public interface DamageModifier {double apply(PlayerLife victim,double amount,PlayerLife source,int killType);}
    public interface Policy {
        AdmissionPolicy.Context admissionContext(int id);
        void prepareSpawn(PlayerLife player);
        PlayerPhysics.Vector spawnPoint(PlayerLife player);
        Optional<PlayerPhysics.Vector> spawnOrientation(PlayerLife player);
        boolean canRespawn(PlayerLife player);
        double respawnDelay(PlayerLife player);
        double walletScale();
        int teamColor(PlayerLife player);
        int score(PlayerLife player);
        boolean opponents(PlayerLife killer,PlayerLife victim);
        int deathType(PlayerLife victim,PlayerLife killer,int weaponType);
        Optional<DamageModifier> damageModifier();
        boolean combatScoringActive();
    }
    /** Effects run synchronously where the source does; mode notifications must be queued by the recipient. */
    public interface Effects {
        void beforeSpawn(PlayerLife player);
        void spawned(PlayerLife player);
        void died(PlayerLife victim,PlayerLife killer,int weaponType,int presentationType,boolean enemyKill);
        void damaged(DamageEvidence damage);
        void healed(HealingEvidence healing);
        /** Source profile/combat/achievement observers occur before the reliable death broadcast. */
        void beforeDeath(DeathEvidence death);
        void transitionCredited(PlayerLife victim,PlayerLife attacker,int requestedType);
        void forget(PlayerLife player);
        void respawnFailed(PlayerLife player,RuntimeException failure);
    }
    public interface Messages {
        void broadcast(GamePacket packet);
        /** Direct reliable sends must reach admitted owners even during map loading. */
        void owner(int playerId,GamePacket packet);
        boolean inGame(int playerId);
    }
    public interface Inputs {
        void reset(PlayerLife player);
        /** Fresh immediate state only. All received rows are recorded by the production timeline first.
         * This port still owns tool/action authorization and conduct; it must never step per arrival.
         */
        void receive(PlayerLife player,LiveClientInput input,int receivedLoop);
        void packet(PlayerLife player,GamePacket packet);
        void forget(PlayerLife player);
    }
    public record Score(int kills,int deaths,int streak,int lastKilledBy,Map<Integer,Bonus> bonuses){public Score{bonuses=Map.copyOf(bonuses);}}
    public record Bonus(boolean revenge,boolean payback){}
    public record DamageEvidence(PlayerLife victim,PlayerLife source,int healthBefore,int removed,int killType,boolean killed){}
    public record HealingEvidence(PlayerLife victim,int healthBefore,int restored){}
    public record DeathEvidence(PlayerLife victim,PlayerLife killer,int weaponType,int presentationType,boolean enemyKill,boolean domination,boolean revenge,List<DamageHistory.Contribution> contributions){public DeathEvidence{contributions=List.copyOf(contributions);}}
    public final class BlastScope implements AutoCloseable {
        private final PlayerLife instigator;
        private boolean closed;
        private BlastScope(PlayerLife instigator){this.instigator=instigator;}
        @Override public void close(){owned();if(closed)return;if(blasts.peekLast()!=this)throw new IllegalStateException("Blast scopes must close in order");blasts.removeLast();closed=true;}
    }
    private static final class Counters {int kills,deaths,streak,lastKilledBy=-1;final Map<Integer,Bonus> bonuses=new HashMap<>();}
    private final Thread owner=Thread.currentThread();
    private final MutableWorld world;
    private final GameRules rules;
    private final String mode;
    private final Clock clock;
    private final Policy policy;
    private final Effects effects;
    private final Messages messages;
    private final Inputs inputs;
    private net.battlespades.replication.ReplicationService replication;
    private net.battlespades.combat.CombatRuntime combat;
    private final PlayerRoster roster=new PlayerRoster();
    private final KillFeed feed=new KillFeed();
    private final Map<Integer,PlayerLife> players=new LinkedHashMap<>();
    private final Map<Integer,Counters> scores=new HashMap<>();
    private final Map<Integer,Long> deadJoin=new HashMap<>();
    private final ArrayDeque<BlastScope> blasts=new ArrayDeque<>();
    public LifeRuntime(MutableWorld world,GameRules rules,String mode,Clock clock,Policy policy,Effects effects,Messages messages,Inputs inputs){
        this.world=Objects.requireNonNull(world);this.rules=Objects.requireNonNull(rules);this.mode=Objects.requireNonNull(mode);this.clock=Objects.requireNonNull(clock);this.policy=Objects.requireNonNull(policy);this.effects=Objects.requireNonNull(effects);this.messages=Objects.requireNonNull(messages);this.inputs=Objects.requireNonNull(inputs);
        if(mode.startsWith("hc_"))throw new IllegalArgumentException("Hardcore lifecycle requires its own policy");
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Life runtime accessed outside owner");}
    @Override public MutableWorld world(){owned();return world;}
    @Override public PlayerRoster roster(){owned();return roster;}
    public PlayerLife player(int id){owned();return Objects.requireNonNull(players.get(id),"Unknown player");}
    public List<PlayerLife> players(){owned();return List.copyOf(players.values());}
    public boolean inGame(int id){owned();return messages.inGame(id);}
    /** Composition hook, installed before admission; no replacement/no-op replication domain is created. */
    public void replication(net.battlespades.replication.ReplicationService value){owned();if(!players.isEmpty()||replication!=null)throw new IllegalStateException("Replication must bind once before admission");replication=Objects.requireNonNull(value);}
    /** Bind complete required combat domain ports once before admission. */
    public void combat(net.battlespades.combat.CombatRuntime value){owned();if(!players.isEmpty()||combat!=null)throw new IllegalStateException("Combat must bind once before admission");combat=Objects.requireNonNull(value);}
    double movementClock(){owned();return clock.monotonic();}
    double movementWallClock(){owned();return clock.wall();}
    @Override public void roundTripMillis(int id,Double measurement){owned();player(id).flight().roundTripMillis(measurement);}
    @Override public void actionLink(int id,ActionClock.Link link){owned();player(id).fire().link(link);}
    void reloadAnnouncement(GamePacket packet){owned();messages.broadcast(packet);}
    public Score score(int id){owned();var s=Objects.requireNonNull(scores.get(id));return new Score(s.kills,s.deaths,s.streak,s.lastKilledBy,s.bonuses);}
    /** Synchronous chained blasts retain the outermost non-null instigator, as conduct.blast_instigator does. */
    public BlastScope blast(Integer instigatorId){owned();return blastSource(instigatorId==null?null:player(instigatorId));}
    public BlastScope blastSource(PlayerLife instigator){owned();PlayerLife current=blasts.isEmpty()?null:blasts.getLast().instigator;var scope=new BlastScope(current!=null?current:instigator);blasts.addLast(scope);return scope;}
    private PlayerLife attributed(PlayerLife victim,PlayerLife source){var instigator=blasts.isEmpty()?null:blasts.getLast().instigator;return instigator!=null&&instigator!=source&&instigator!=victim&&source!=null&&instigator.team()==victim.team()?instigator:source;}
    /** Accepted connected-player/world damage only. Hit authorization and detached projectile actors remain caller work. */
    public boolean damage(int victimId,double amount,Integer sourceId,int killType,Integer hpType){
        owned();var victim=player(victimId);if(!victim.alive()||victim.god())return false;
        var source=attributed(victim,sourceId==null?null:player(sourceId));
        if(source!=null&&source!=victim&&victim.protectionRemaining(clock.monotonic())>0)return false;
        if(source!=null&&source!=victim)source.endProtection(clock.monotonic());
        var modifier=policy.damageModifier();if(modifier.isPresent()){amount=modifier.get().apply(victim,amount,source,killType);if(amount<=0)return false;}
        if(source!=null&&source!=victim){amount*=rules.number("RULE_WEAPON_DAMAGE");if((mode.equals("zom")||mode.equals("zombie"))&&LifeCatalog.ZOMBIES.contains(source.selection().classId()))amount*=rules.number("RULE_ZOMBIE_CLASS_DAMAGE");if(rules.enabled("RULE_ONE_HIT_KILL")&&LifeCatalog.ONE_HIT.contains(killType))amount=Math.max(amount,victim.health());}
        victim.damageStamp(clock.wall());if(!Double.isFinite(amount))throw new IllegalArgumentException("Nonfinite accepted damage");double rounded=Math.max(0,Math.rint(amount));if(rounded<=0)return false;
        int before=victim.health();victim.health((int)Math.max(0,before-rounded));int removed=before-victim.health();
        if(policy.combatScoringActive()&&source!=null&&source!=victim&&source.team()!=0&&victim.team()!=0&&policy.opponents(source,victim)&&removed>0)victim.damageHistory().record(source,removed,clock.monotonic(),players::get);
        effects.damaged(new DamageEvidence(victim,source,before,removed,killType,victim.health()<=0));
        var position=source==null?victim.position():source.position();if(source!=null)victim.combatStamp(source,killType,clock.monotonic());
        if(messages.inGame(victimId))messages.owner(victimId,new LifePackets.SetHP(clamp(victim.health()),hpType!=null?hpType:source==null||source==victim?0:1,new Vector3f((float)position.x(),(float)position.y(),(float)position.z())));
        if(victim.health()>0)return false;
        var killer=source;if(killer==null&&killType==LifeCatalog.FALL_KILL)killer=recentEnemyAttacker(victimId);die(victimId,killer==null?null:killer.id(),killType);return true;
    }
    public boolean damage(int victimId,double amount,Integer sourceId,int killType){return damage(victimId,amount,sourceId,killType,null);}
    public void heal(int id,int amount){
        owned();var p=player(id);if(!p.alive())return;int before=p.health();p.health((int)Math.max(before,Math.min(Math.max(1,p.maxHealth()),(long)before+amount)));int restored=p.health()-before;p.damageHistory().heal(restored);effects.healed(new HealingEvidence(p,before,restored));
        if(messages.inGame(id))messages.owner(id,new LifePackets.SetHP(clamp(p.health()),2,new Vector3f(0,0,0)));
    }
    public PlayerLife recentEnemyAttacker(int id){
        owned();var p=player(id);double last=p.lastCombatHit();if(!p.alive()||last<=0||last<p.spawnedAt()||clock.monotonic()-last>LifeCatalog.INTERACTION_WINDOW||p.lastDamageSource()<0||p.lastDamageSource()==id)return null;
        var source=players.get(p.lastDamageSource());return source!=null&&source!=p&&source.team()!=0&&source.team()!=p.team()?source:null;
    }
    public PlayerLife endLifeForTransition(int id,int transitionType){owned();var p=player(id);if(!p.alive())return null;var source=recentEnemyAttacker(id);if(source!=null)effects.transitionCredited(p,source,transitionType);die(id,source==null?null:source.id(),source==null?transitionType:LifeCatalog.TRANSITION_CREDIT_KILL);return source;}
    @Override public AdmissionPolicy.Context admissionContext(int id){owned();return policy.admissionContext(id);}
    private void spawn(PlayerLife player){
        player.walletScale(policy.walletScale());player.applyPending();policy.prepareSpawn(player);var at=policy.spawnPoint(player);policy.spawnOrientation(player).ifPresent(v->player.orientation(v.x(),v.y(),v.z()));effects.beforeSpawn(player);
        if(replication!=null)replication.forgetPlayer(player.id());player.spawn(at,clock.monotonic(),policy.walletScale(),policy.teamColor(player));inputs.reset(player);
    }
    private void publish(PlayerLife p){roster.create(p.create(),p.color());messages.broadcast(p.create());messages.broadcast(new SimplePackets.SetScore(1,0,p.id(),policy.score(p)));messages.broadcast(new SimplePackets.SetColor(p.id(),p.color()));}
    @Override public JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){
        owned();if(players.containsKey(id))throw new IllegalStateException("Player id already occupied");
        var p=new PlayerLife(id,request.team(),request.local_language(),request.name(),mode,CollisionMap.of(world),rules,selection,policy.walletScale());
        p.flight().negotiate(capability);p.motion().flightCapability(capability>0);
        var after=new ArrayList<GamePacket>();boolean spectator=p.team()==0;
        // The synchronous mode preparation occurs even when its respawn gate rejects the first life.
        if(!spectator)policy.prepareSpawn(p);
        var at=policy.spawnPoint(p);if(!spectator)policy.spawnOrientation(p).ifPresent(v->p.orientation(v.x(),v.y(),v.z()));
        boolean joinsDead=!spectator&&!policy.canRespawn(p);
        players.put(id,p);scores.put(id,new Counters());
        try {
            if(spectator||joinsDead)p.camera(at,policy.teamColor(p));
            else {effects.beforeSpawn(p);if(replication!=null)replication.forgetPlayer(id);p.spawn(at,clock.monotonic(),policy.walletScale(),policy.teamColor(p));inputs.reset(p);}
            roster.create(p.create(),p.color());
            if(joinsDead){int countdown=0;try{countdown=clamp((int)policy.respawnDelay(p));}catch(RuntimeException ignored){/* Original join-death fallback is zero. */}var death=new LifePackets.KillAction(id,id,0,countdown,0,false,false);p.joinedDead(clock.wall(),death);roster.death(death);deadJoin.put(id,p.generation());after.add(death);}
            else {messages.broadcast(p.create());messages.broadcast(new SimplePackets.SetColor(id,p.color()));if(!spectator)after.addAll(p.spawnOwnerPackets());}
            return new JoinScene.Admission(p.create(),p.color(),after);
        } catch(RuntimeException failure){players.remove(id);scores.remove(id);roster.remove(id);deadJoin.remove(id);inputs.forget(p);effects.forget(p);throw failure;}
    }
    @Override public List<GamePacket> reveal(int id){
        owned();var p=player(id);Long token=deadJoin.remove(id);if(token!=null&&p.alive()&&p.spawned()&&p.generation()!=token){var packets=new ArrayList<GamePacket>();packets.add(p.create());packets.addAll(p.spawnOwnerPackets());return List.copyOf(packets);}return List.of();
    }
    public void respawn(int id){owned();var p=player(id);if(p.team()==0)throw new IllegalStateException("Spectator respawn");spawn(p);p.clearDeathTime();publish(p);restock(id,0,false);effects.spawned(p);}
    /** RoundLifecycle captures wall time once and isolates failures only inside each respawn attempt. */
    public void processRespawns(){
        owned();double now=clock.wall();for(var p:List.copyOf(players.values())){
            if(p.alive()||p.spawned()||p.deathTime()<=0||p.team()==0||!policy.canRespawn(p)||now-p.deathTime()<policy.respawnDelay(p))continue;
            try{respawn(p.id());}catch(RuntimeException failure){if(!p.alive())p.joinedDead(now,p.lastDeath());effects.respawnFailed(p,failure);}
        }
    }
    public void restock(int id,int type,boolean bot){owned();var p=player(id);if(!p.alive())return;p.inventory().restock(type,clock.monotonic(),bot);messages.owner(id,new SimplePackets.Restock(id,type));}
    public void restockBlocks(int id){owned();var p=player(id);if(!p.alive())return;p.inventory().addBlocks(p.inventory().maxBlocks());messages.owner(id,new SimplePackets.Restock(id,5));}
    public void restockJetpack(int id){owned();var p=player(id);if(!p.alive()||!LifeCatalog.PACKS.containsKey(p.jetpack()))return;p.flight().restock(p.jetpack());messages.owner(id,new SimplePackets.Restock(id,6));}
    private static int clamp(int value){return Math.max(0,Math.min(255,value));}
    public boolean die(int id,Integer killerId,int weaponType){
        owned();var p=player(id);if(!p.alive())return false;var killer=killerId==null?null:player(killerId);int type=policy.deathType(p,killer,weaponType);p.beginDeath(clock.wall());boolean transition=LifeCatalog.TRANSITIONS.contains(type),enemy=killer!=null&&killer!=p&&policy.opponents(killer,p)&&!transition;
        var victimScore=scores.get(id);if(!transition)victimScore.deaths++;victimScore.streak=0;feed.resetMultikill(id);int count=0;var flags=new KillFeed.Flags(false,false);
        if(enemy){var k=scores.get(killer.id());k.kills++;k.streak=Math.min(255,k.streak+1);count=feed.registerMultikill(killer.id(),clock.monotonic());flags=feed.registerDomination(killer.id(),id);boolean payback=k.lastKilledBy==id;k.bonuses.put(id,new Bonus(flags.revenge(),payback));if(payback)k.lastKilledBy=-1;victimScore.lastKilledBy=killer.id();}
        else if(LifeCatalog.DOMINATION_RESET.contains(type))feed.clearPlayer(id);
        effects.beforeDeath(new DeathEvidence(p,killer,weaponType,type,enemy,flags.domination(),flags.revenge(),p.damageHistory().consume()));
        int delay=clamp((int)Math.ceil(policy.respawnDelay(p)-1e-6));var death=new LifePackets.KillAction(id,killer==null?id:killer.id(),type,delay,count,flags.domination(),flags.revenge());p.rememberDeath(death);roster.death(death);messages.broadcast(death);effects.died(p,killer,weaponType,type,enemy);return true;
    }
    @Override public void input(int id,LiveClientInput input,int loop){
        owned();var p=player(id);p.motion().received(clock.monotonic());var arrival=p.inputTimeline().receive(input,p.alive(),p.spawned(),p.aim(),loop,world.topologyVersion(),false);
        if(arrival.fresh()){p.orientation(input.x(),input.y(),input.z());p.motion().controls(input.movementFlags(),(input.actionFlags()&255)|(input.paletteEnabled()?256:0));inputs.receive(p,input,loop);}
    }
    @Override public void packet(int id,GamePacket packet){
        owned();var p=player(id);
        if(packet instanceof LifePackets.WeaponReload reload){
            // Source handler ignores client completion/tool/player claims; its owned timer and active tool decide.
            if(!reload.done()&&p.alive()&&p.fire().startReload(clock.monotonic()))messages.broadcast(new LifePackets.WeaponReload(id,p.inventory().tool(),false));
            return;
        }
        if(packet instanceof ShootPacket shot&&combat!=null){if(p.alive())combat.shot(p,shot);return;}
        inputs.packet(p,packet);
    }
    @Override public void disconnected(int id){owned();var p=players.get(id);if(p==null)return;inputs.forget(p);effects.forget(p);if(replication!=null)replication.forgetPlayer(id);if(combat!=null)combat.forgetPlayer(id);feed.forget(id);roster.remove(id);deadJoin.remove(id);scores.remove(id);players.remove(id);messages.broadcast(new SimplePackets.PlayerLeft(id));}
}
