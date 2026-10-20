// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.replication.*;
import net.battlespades.runtime.OwnerLoop;
import net.battlespades.scene.SceneConfig;
import net.battlespades.world.*;
import org.tomlj.TomlTable;

/** Concrete connected-player physics, profile/combat ticks, replication, repair and periodic services.
 * Match-owned devices, effects and corpses supply their presentation; mode presentation, bots and observation remain required.
 */
public final class HostedPlayerRuntime implements AutoCloseable {
    public interface Ports {
        PlayerSnapshot.Presentation presentation(PlayerLife player);
        List<EntityRecord> entities();List<WorldUpdate.TurretRow> turrets();void delivered(int packets,long bytes);
        void lagHistory(PlayerLife player,int tick);void housekeeping(PlayerLife player);int pendingTerrainMutations();
        void report(PlayerLife player,String kind,boolean enforced);void afterMove(PlayerLife player,double dt);void observePhysics(PlayerLife player,double now);
        boolean transitionBusy();TeamBalancer.Bots bots();boolean hearingEnabled();
        void blockDestroyed(GameplaySounds.Position center,int sourceId,int team,double lifetime);
        void updateBotSkill(double now);void anticheatReport(double now);void failure(String label,Throwable error);
    }
    public record Settings(InfantrySimulation.Settings infantry,ReplicationService.Settings replication,TerrainRepair.Settings repair,TeamBalancer.Settings balance){
        public Settings{Objects.requireNonNull(infantry);Objects.requireNonNull(replication);Objects.requireNonNull(repair);Objects.requireNonNull(balance);}
        public static Settings from(TomlTable raw,SceneConfig scene,MapMetadata metadata){
            var infantry=new InfantrySimulation.Settings(Hardcore.enabled(scene.modeCode())?1:metadata.environment().gravity(),scene.sameTeamCollision(),scene.fallDamage(),integer(raw,"debug.input_gap_fill_limit",8,0,9),flag(raw,"anticheat.enforce_input_starvation",false),number(raw,"anticheat.starvation_timeout_seconds",8,0,Double.MAX_VALUE),integer(raw,"anticheat.starvation_airborne_ticks",24,0,Integer.MAX_VALUE),flag(raw,"anticheat.enforce_input_backlog",false),integer(raw,"anticheat.backlog_max_frames",6,0,Integer.MAX_VALUE),"client".equalsIgnoreCase(String.valueOf(raw.get("game.movement_authority"))));
            var repair=new TerrainRepair.Settings(flag(raw,"network.terrain_repair_enabled",true),integer(raw,"network.terrain_repair_queue_limit",8192,64,Integer.MAX_VALUE),integer(raw,"network.terrain_repair_delay_ticks",120,1,Integer.MAX_VALUE),integer(raw,"network.terrain_collapse_repair_delay_ticks",18,1,Integer.MAX_VALUE),integer(raw,"network.terrain_repair_interval_ticks",3,1,Integer.MAX_VALUE),integer(raw,"network.terrain_repair_batch_limit",8,1,Integer.MAX_VALUE),integer(raw,"network.terrain_collapse_repair_batch_limit",8,1,Integer.MAX_VALUE));
            var balance=new TeamBalancer.Settings(scene.admission().autoBalance(),flag(raw,"teams.balance_mid_match",true),scene.admission().balanceThreshold(),number(raw,"teams.balance_check_interval",1,.1,60),number(raw,"teams.balance_grace_seconds",5,0,600),number(raw,"teams.balance_player_cooldown",600,0,86400),number(raw,"teams.balance_bot_wait_seconds",10,0,600));
            return new Settings(infantry,ReplicationService.Settings.from(raw),repair,balance);
        }
        private static boolean flag(TomlTable raw,String key,boolean fallback){Object v=raw.get(key);if(v==null)return fallback;if(v instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
        private static int integer(TomlTable raw,String key,int fallback,int min,int max){Object v=raw.get(key);if(v==null)return fallback;if(v instanceof Long n)return (int)Math.max(min,Math.min((long)max,n));throw new IllegalArgumentException(key+" must be integer");}
        private static double number(TomlTable raw,String key,double fallback,double min,double max){Object v=raw.get(key);if(v==null)return fallback;if(v instanceof Number n&&Double.isFinite(n.doubleValue()))return Math.max(min,Math.min(max,n.doubleValue()));throw new IllegalArgumentException(key+" must be finite numeric");}
    }
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final LifeRuntime.Clock clock;
    private final InfantrySimulation infantry;private final ReplicationService replication;private final TerrainRepair repair;private final HostedGameplayServices gameplay;
    private final Map<Integer,PlayerSnapshot> snapshots=new LinkedHashMap<>();private final Map<Integer,PlayerLife> identities=new HashMap<>();
    private boolean closed;
    public HostedPlayerRuntime(ApplicationHost host,StandardMatchDomain match,SceneConfig scene,Settings settings,LifeRuntime.Clock clock,OwnerLoop loop,Ports ports){
        this.match=Objects.requireNonNull(match);this.clock=Objects.requireNonNull(clock);Objects.requireNonNull(host);Objects.requireNonNull(scene);Objects.requireNonNull(settings);Objects.requireNonNull(loop);Objects.requireNonNull(ports);
        var lives=match.lives();if(!lives.players().isEmpty())throw new IllegalStateException("Player runtime must bind before admission");
        replication=new ReplicationService(new ReplicationService.World(){
            public List<PlayerSnapshot> players(){return currentSnapshots(ports);}public List<? extends WorldUpdateLink> connections(){return host.snapshotLinks();}
            public List<EntityRecord> entities(){return ports.entities();}public List<WorldUpdate.TurretRow> turrets(){return match.entities().turrets();}public void delivered(int packets,long bytes){ports.delivered(packets,bytes);}
        },settings.replication());
        infantry=new InfantrySimulation(lives,settings.infantry(),new InfantrySimulation.Hooks(){
            public void lagHistory(PlayerLife p,int tick){ports.lagHistory(p,tick);}public void housekeeping(PlayerLife p){ports.housekeeping(p);}public boolean burdened(PlayerLife p){return p.pickupBurdensome();}public int pendingTerrainMutations(){return match.construction().mutations().metrics().pending();}
            public void report(PlayerLife p,String kind,boolean enforced){match.inputs().account(p,kind,enforced,Map.of());ports.report(p,kind,enforced);}public void disconnect(PlayerLife p,int reason){if(current(p))host.disconnectPlayer(p.id(),reason);}public void afterMove(PlayerLife p,double dt){ports.afterMove(p,dt);}
            public void afterTick(PlayerLife p,double dt){var row=match.binding().row(p);boolean airborne=p.generation()>0&&p.body().state().airborne();match.profiles().tick(row,dt,airborne,scene.mapName(),match.binding().players().values());match.combat().tick(row,dt,airborne,match.entities().effects().onFire(p));ports.observePhysics(p,clock.monotonic());}
            public void failed(PlayerLife p,RuntimeException error){ports.failure("player:"+p.id(),error);}
        });
        repair=new TerrainRepair(lives.world(),settings::repair,()->lives.players().stream().map(p->new TerrainRepair.Recipient(p.id(),!p.bot()&&host.inGame(p.id()),true)).toList(),(recipient,packet)->{if(!host.sendPlayer(recipient.playerId(),packet))throw new IllegalStateException("Terrain repair control rejected");});
        gameplay=new HostedGameplayServices(host,lives,match.controls().teams(),new HostedGameplayServices.Domain(){
            public TeamBalancer.Settings settings(){return settings.balance();}public TeamBalancer.Status status(){var mode=match.mode();return new TeamBalancer.Status(mode.started(),mode.clock().ended,mode.clock().retiring,match.zombie().isPresent(),scene.admission().autoBalance());}
            public boolean transitionBusy(){return ports.transitionBusy()||host.matchTransitions().map(t->t.busy()).orElse(false);}public TeamBalancer.Objectives objectives(){return new TeamBalancer.Objectives(List.of(),Set.of(),null);}
            public boolean retireSafe(PlayerLife p){return match.zombie().map(z->z.botRetireSafe(p)).orElseGet(()->match.vip().map(v->v.botRetireSafe(p)).orElseGet(()->!(match.mode() instanceof ArenaMode arena)||arena.botRetireSafe(p)));}public boolean allows(PlayerLife p,int team){return match.zombie().map(z->z.allowsTeam(p,team)).orElseGet(()->match.vip().map(v->v.allowsTeam(p)).orElse(true));}
            public long captures(PlayerLife p){return match.binding().row(p).captures;}public long score(PlayerLife p){return match.binding().row(p).score;}public TeamBalancer.Bots bots(){return ports.bots();}
            public boolean hearingEnabled(){return ports.hearingEnabled();}public void blockDestroyed(GameplaySounds.Position center,int source,int team,double lifetime){ports.blockDestroyed(center,source,team,lifetime);}public void updateBotSkill(double now){ports.updateBotSkill(now);}public void anticheatReport(double now){ports.anticheatReport(now);}
        },clock::monotonic,loop,ports::failure);
        host.reconciliation().ifPresent(replication::tune);lives.replication(replication);
    }
    private boolean current(PlayerLife p){return match.lives().players().stream().anyMatch(actor->actor==p);}
    private List<PlayerSnapshot> currentSnapshots(Ports ports){
        owned();var roster=match.lives().players();var ids=new HashSet<Integer>();var result=new ArrayList<PlayerSnapshot>();
        for(var p:roster){ids.add(p.id());if(identities.get(p.id())!=p){identities.put(p.id(),p);snapshots.put(p.id(),new PlayerSnapshot(p,actor->{var v=ports.presentation(actor);return new PlayerSnapshot.Presentation(actor.inventory().tool(),actor.pickupId(),match.entities().effects().onFire(actor),actor.disguise().active(),match.entities().effects().touchingGoo(actor),match.corpses().replicate(actor),v.modeAllowsTool(),match.inputs().mounted(actor)||v.mountedMachineGunAuthorized());}));}result.add(snapshots.get(p.id()));}
        snapshots.keySet().retainAll(ids);identities.keySet().retainAll(ids);return List.copyOf(result);
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Player runtime outside active owner");}
    public void players(int tick){owned();infantry.tick(tick);}public void replicate(int tick){owned();replication.broadcast(tick,clock.monotonic());}
    public InfantrySimulation infantry(){owned();return infantry;}public ReplicationService replication(){owned();return replication;}public TerrainRepair repair(){owned();return repair;}public HostedGameplayServices gameplay(){owned();return gameplay;}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Player runtime outside owner");if(closed)return;repair.reset();snapshots.clear();identities.clear();closed=true;}
}
