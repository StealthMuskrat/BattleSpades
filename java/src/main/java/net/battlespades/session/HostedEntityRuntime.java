// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.entities.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.scene.SceneConfig;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.*;
import org.tomlj.TomlTable;

/** Match-owned static entities, map resources, blast delivery and synchronous support-loss callbacks.
 * Terrain authorization/scoring bind through HostedCombatRuntime; achievement scopes remain required.
 * Static registry entries are not the separate moving-entity WorldUpdate stream.
 */
public final class HostedEntityRuntime implements AutoCloseable {
    public interface Ports {
        Object crateBaseline(PlayerLife player);void crateCollected(PlayerLife player,int award,Object baseline);
        void resourcesRebuilt(MapResourceService.Result result);void skippedEntityTicks(long count);
        void blastStimulus(Vector position,int source,int team,double radius,double lifetime);
        Object beginBlast(PlayerLife thrower,int killType,Vector origin);void endBlast(Object scope);void blastScopeFault(String phase);
        void failure(String label,Throwable error);
    }
    public record Settings(boolean wireReady,boolean buildDamage,int tickLimit,double radarLifetime){
        public Settings(boolean wireReady,boolean buildDamage,int tickLimit){this(wireReady,buildDamage,tickLimit,45);}
        public Settings{if(!Double.isFinite(radarLifetime)||radarLifetime<1||radarLifetime>250)throw new IllegalArgumentException("Radar lifetime");if(tickLimit<64)throw new IllegalArgumentException("Entity tick limit must be at least 64");}
        public static Settings from(TomlTable raw){
            Object limit=raw.get("network.entity_tick_batch_limit");if(limit!=null&&!(limit instanceof Long))throw new IllegalArgumentException("Entity tick limit must be integer");
            return new Settings(flag(raw,"world.entities_wire_ready",true),flag(raw,"game.build_damage",true),(int)Math.max(64,Math.min(Integer.MAX_VALUE,limit==null?8192:(Long)limit)),radar(raw));
        }
        private static double radar(TomlTable raw){Object value=raw.get("game.radar_station_lifetime_seconds");if(value==null)return 45;if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return Math.max(1,Math.min(250,n.doubleValue()));throw new IllegalArgumentException("Radar lifetime must be finite numeric");}
        private static boolean flag(TomlTable raw,String key,boolean fallback){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Boolean b)return b;throw new IllegalArgumentException(key+" must be boolean");}
    }
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final ApplicationHost host;private final LifeRuntime.Clock clock;private final Settings settings;private final Ports ports;
    private final EntityRegistry registry=new EntityRegistry();private final PickupWorld world;private final TerrainDamage terrain;private final BlastTerrain blastTerrain;private final BlastRuntime blasts;private final DeployableRuntime devices;private final MapResourceService resources;
    private final ShieldAnchors shields;
    public ShieldAnchors shields(){owned();return shields;}
    private DeployableActions actions;private final SurfaceEffects effects;private final HostedProjectileRuntime projectiles;
    public boolean wireReady(){owned();return settings.wireReady();}
    public SurfaceEffects effects(){owned();return effects;}
    public HostedProjectileRuntime projectiles(){owned();return projectiles;}
    public boolean packet(PlayerLife p,GamePacket packet){owned();return projectiles.packet(p,packet)||actions.packet(p,packet);}
    public void known(int id,GamePacket packet){owned();for(var session:host.transitionSessions())if(session.inGame()&&session.knownEntities().contains(id))session.gameplay(packet,Delivery.RELIABLE);}
    public List<WorldUpdate.TurretRow> turrets(){owned();return registry.all().stream().filter(e->e.alive&&e.behavior instanceof RocketTurretBehavior).map(e->((RocketTurretBehavior)e.behavior).row(e)).toList();}
    public void turretTick(){owned();for(var e:registry.all())if(e.alive&&e.behavior instanceof RocketTurretBehavior turret)turret.update(e,1.0/60);}
    public void forget(PlayerLife p){owned();actions.forget(p);shields.forget(p.id());projectiles.forget(p.id());effects.forget(p.id());actions.retire(p);}
    public DeployableActions actions(){owned();return Objects.requireNonNull(actions);}
    public void reliable(GamePacket packet){owned();host.broadcast(packet,Delivery.RELIABLE);}
    private boolean closed;
    public HostedEntityRuntime(ApplicationHost host,StandardMatchDomain match,PreparedMap.Bound prepared,MapMetadata metadata,SceneConfig scene,Settings settings,LifeRuntime.Clock clock,SourceRandom random,Ports ports){
        this.host=Objects.requireNonNull(host);this.match=Objects.requireNonNull(match);this.settings=Objects.requireNonNull(settings);this.clock=Objects.requireNonNull(clock);this.ports=Objects.requireNonNull(ports);Objects.requireNonNull(random);
        if(!match.lives().players().isEmpty()||match.world()!=prepared.world()||metadata.environment().sourceCrc()!=prepared.world().sourceCrc())throw new IllegalArgumentException("Entity runtime requires its empty prepared match");
        world=new PickupWorld(match.world());terrain=new TerrainDamage(match.world(),match.combatRuntime().terrain(),()->random.below(256));
        blastTerrain=new BlastTerrain(match.world(),terrain,new BlastTerrain.Delivery(){
            public List<BlastTerrain.Recipient> recipients(){return host.transitionSessions().stream().filter(s->s.playerId().isPresent()).map(s->new BlastTerrain.Recipient(s.playerId().orElseThrow(),s.inGame(),s.knownEntities())).toList();}
            public void reliable(BlastTerrain.Recipient recipient,GamePacket packet){if(!host.sendPlayer(recipient.id(),packet))throw new IllegalStateException("Blast terrain control rejected");}
            public void reliable(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}
        });
        blasts=new BlastRuntime(match.lives(),registry,blastTerrain,clock::monotonic,new BlastRuntime.Domain(){
            public boolean buildDamage(){return settings.buildDamage();}public boolean friendlyFire(){return scene.friendlyFire();}public boolean hardcore(PlayerLife player){return false;}
            public void stimulus(Vector at,int source,int team,double radius,double lifetime){ports.blastStimulus(at,source,team,radius,lifetime);}
            public Object begin(PlayerLife source,int type,Vector at){return ports.beginBlast(source,type,at);}public void end(Object scope){ports.endBlast(scope);}public void scopeFault(String phase){ports.blastScopeFault(phase);}
        },this::context);
        resources=new MapResourceService(match.lives(),registry,metadata,team->{var p=prepared.spawns().baseAnchor(team);return new Vector(p.x(),p.y(),p.z());},new MapResourceService.Effects(){
            public boolean wireReady(){return settings.wireReady();}public boolean pickupsEnabled(){return true;}public double respawnDelay(){return scene.rules().number("RULE_CRATES_SPAWN_TIME");}
            public Object crateBaseline(PlayerLife player){return ports.crateBaseline(player);}public void recordCrate(PlayerLife player,int award){match.combat().crate(match.binding().row(player),award);}public void crateCollected(PlayerLife player,int award,Object baseline){ports.crateCollected(player,award,baseline);}
            public void create(MapEntity entity){HostedEntityRuntime.this.create(entity);}public void destroy(int id){HostedEntityRuntime.this.destroy(id);}
            public void personalSound(PlayerLife player,int sound){match.runtime().gameplay().sounds().personal(player,new net.battlespades.match.GameplaySounds.Cue(sound,1,null,0),true);}
            public void flyby(AudioPackets.PlaySound packet){host.broadcast(packet,Delivery.RELIABLE);}public void rebuilt(MapResourceService.Result result){ports.resourcesRebuilt(result);}
        });
        // Install last: failed construction cannot leave a world listener behind.
        devices=new DeployableRuntime(match.lives(),registry,blasts,this::context,error->ports.failure("entity support",error));
        shields=new ShieldAnchors(match,host);
        effects=new SurfaceEffects(match,this,scene.friendlyFire(),new SourceRandom(Double.doubleToLongBits(clock.wall())));
        projectiles=new HostedProjectileRuntime(match,this,host);
        actions=new DeployableActions(match,this,settings.radarLifetime());
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Entity runtime outside active owner");}
    public EntityContext context(){owned();return new EntityContext(1.0/60,clock.wall(),match.lives().players().stream().filter(p->p.alive()&&p.spawned()).toList(),registry,this::create,this::destroy,devices,world,this::move);}
    public void tick(){owned();long skipped=registry.tick(context(),settings.tickLimit());if(skipped!=0)ports.skippedEntityTicks(skipped);}
    public void resetRound(){owned();for(var e:registry.all())if(e.wireVisible)destroy(e.id());registry.clear();actions.clear();shields.clear();projectiles.clear();effects.clear();devices.reset();for(var p:match.lives().players()){p.mountedEntityId(null);p.chargeIds(List.of());p.radarEntityId(null);}}
    public MapResourceService.Result rebuild(){owned();return resources.rebuild();}
    public int create(MapEntity entity){owned();return host.broadcastCreateEntity(entity);}
    public int destroy(int id){owned();return host.broadcastDestroyEntity(id);}
    public void move(MapEntity entity){owned();host.broadcast(ChangeEntity.position(entity.id(),new Vector3f((float)entity.x,(float)entity.y,(float)entity.z)),Delivery.RELIABLE);}
    /** The normal loader queues this list atomically and records knowledge on reliable acceptance. */
    public List<GamePacket> reveal(int playerId){owned();if(!settings.wireReady())return List.of();var known=host.knownEntities(playerId);var result=new ArrayList<GamePacket>();for(var e:registry.staticEntities())if(!known.contains(e.id())){result.add(new CreateEntity(e.toWireEntity()));if(e.behavior instanceof RocketTurretBehavior turret)result.addAll(turret.properties(e));}return List.copyOf(result);}
    public EntityRegistry registry(){owned();return registry;}public TerrainDamage terrain(){owned();return terrain;}public BlastTerrain blastTerrain(){owned();return blastTerrain;}public BlastRuntime blasts(){owned();return blasts;}public DeployableRuntime devices(){owned();return devices;}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Entity runtime outside owner");if(closed)return;devices.close();actions.clear();shields.clear();projectiles.clear();effects.clear();registry.clear();closed=true;}
}
