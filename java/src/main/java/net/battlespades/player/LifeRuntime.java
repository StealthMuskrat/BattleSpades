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
    }
    /** Effects run synchronously where the source does; mode notifications must be queued by the recipient. */
    public interface Effects {
        void beforeSpawn(PlayerLife player);
        void spawned(PlayerLife player);
        void died(PlayerLife victim,PlayerLife killer,int weaponType,int presentationType,boolean enemyKill);
        void forget(PlayerLife player);
        void respawnFailed(PlayerLife player,RuntimeException failure);
    }
    public interface Messages {
        void broadcast(GamePacket packet);
        /** Direct reliable sends must reach admitted owners even during map loading. */
        void owner(int playerId,GamePacket packet);
    }
    public interface Inputs {
        void reset(PlayerLife player);
        void receive(PlayerLife player,LiveClientInput input,int receivedLoop);
        void packet(PlayerLife player,GamePacket packet);
        void forget(PlayerLife player);
    }
    public record Score(int kills,int deaths,int streak,int lastKilledBy,Map<Integer,Bonus> bonuses){public Score{bonuses=Map.copyOf(bonuses);}}
    public record Bonus(boolean revenge,boolean payback){}
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
    private final PlayerRoster roster=new PlayerRoster();
    private final KillFeed feed=new KillFeed();
    private final Map<Integer,PlayerLife> players=new LinkedHashMap<>();
    private final Map<Integer,Counters> scores=new HashMap<>();
    private final Map<Integer,Long> deadJoin=new HashMap<>();
    public LifeRuntime(MutableWorld world,GameRules rules,String mode,Clock clock,Policy policy,Effects effects,Messages messages,Inputs inputs){
        this.world=Objects.requireNonNull(world);this.rules=Objects.requireNonNull(rules);this.mode=Objects.requireNonNull(mode);this.clock=Objects.requireNonNull(clock);this.policy=Objects.requireNonNull(policy);this.effects=Objects.requireNonNull(effects);this.messages=Objects.requireNonNull(messages);this.inputs=Objects.requireNonNull(inputs);
        if(mode.startsWith("hc_"))throw new IllegalArgumentException("Hardcore lifecycle requires its own policy");
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Life runtime accessed outside owner");}
    @Override public MutableWorld world(){owned();return world;}
    @Override public PlayerRoster roster(){owned();return roster;}
    public PlayerLife player(int id){owned();return Objects.requireNonNull(players.get(id),"Unknown player");}
    public List<PlayerLife> players(){owned();return List.copyOf(players.values());}
    public Score score(int id){owned();var s=Objects.requireNonNull(scores.get(id));return new Score(s.kills,s.deaths,s.streak,s.lastKilledBy,s.bonuses);}
    @Override public AdmissionPolicy.Context admissionContext(int id){owned();return policy.admissionContext(id);}
    private void spawn(PlayerLife player){
        player.walletScale(policy.walletScale());player.applyPending();policy.prepareSpawn(player);var at=policy.spawnPoint(player);policy.spawnOrientation(player).ifPresent(v->player.orientation(v.x(),v.y(),v.z()));effects.beforeSpawn(player);
        player.spawn(at,clock.monotonic(),policy.walletScale(),policy.teamColor(player));inputs.reset(player);
    }
    private void publish(PlayerLife p){roster.create(p.create(),p.color());messages.broadcast(p.create());messages.broadcast(new SimplePackets.SetScore(1,0,p.id(),policy.score(p)));messages.broadcast(new SimplePackets.SetColor(p.id(),p.color()));}
    @Override public JoinScene.Admission admit(int id,SimplePackets.NewPlayerConnection request,ClassCatalog.Selection selection,int capability){
        owned();if(players.containsKey(id))throw new IllegalStateException("Player id already occupied");
        var p=new PlayerLife(id,request.team(),request.local_language(),request.name(),mode,CollisionMap.of(world),rules,selection,policy.walletScale());
        var after=new ArrayList<GamePacket>();boolean spectator=p.team()==0;
        // The synchronous mode preparation occurs even when its respawn gate rejects the first life.
        if(!spectator)policy.prepareSpawn(p);
        var at=policy.spawnPoint(p);if(!spectator)policy.spawnOrientation(p).ifPresent(v->p.orientation(v.x(),v.y(),v.z()));
        boolean joinsDead=!spectator&&!policy.canRespawn(p);
        players.put(id,p);scores.put(id,new Counters());
        try {
            if(spectator||joinsDead)p.camera(at,policy.teamColor(p));
            else {effects.beforeSpawn(p);p.spawn(at,clock.monotonic(),policy.walletScale(),policy.teamColor(p));inputs.reset(p);}
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
    private static int clamp(int value){return Math.max(0,Math.min(255,value));}
    public boolean die(int id,Integer killerId,int weaponType){
        owned();var p=player(id);if(!p.alive())return false;var killer=killerId==null?null:player(killerId);int type=policy.deathType(p,killer,weaponType);p.beginDeath(clock.wall());boolean transition=LifeCatalog.TRANSITIONS.contains(type),enemy=killer!=null&&killer!=p&&policy.opponents(killer,p)&&!transition;
        var victimScore=scores.get(id);if(!transition)victimScore.deaths++;victimScore.streak=0;feed.resetMultikill(id);int count=0;var flags=new KillFeed.Flags(false,false);
        if(enemy){var k=scores.get(killer.id());k.kills++;k.streak=Math.min(255,k.streak+1);count=feed.registerMultikill(killer.id(),clock.monotonic());flags=feed.registerDomination(killer.id(),id);boolean payback=k.lastKilledBy==id;k.bonuses.put(id,new Bonus(flags.revenge(),payback));if(payback)k.lastKilledBy=-1;victimScore.lastKilledBy=killer.id();}
        else if(LifeCatalog.DOMINATION_RESET.contains(type))feed.clearPlayer(id);
        int delay=clamp((int)Math.ceil(policy.respawnDelay(p)-1e-6));var death=new LifePackets.KillAction(id,killer==null?id:killer.id(),type,delay,count,flags.domination(),flags.revenge());p.rememberDeath(death);roster.death(death);messages.broadcast(death);effects.died(p,killer,weaponType,type,enemy);return true;
    }
    @Override public void input(int id,LiveClientInput input,int loop){owned();inputs.receive(player(id),input,loop);}
    @Override public void packet(int id,GamePacket packet){owned();inputs.packet(player(id),packet);}
    @Override public void disconnected(int id){owned();var p=players.get(id);if(p==null)return;inputs.forget(p);effects.forget(p);feed.forget(id);roster.remove(id);deadJoin.remove(id);scores.remove(id);players.remove(id);messages.broadcast(new SimplePackets.PlayerLeft(id));}
}
