// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.catalog.SourceCatalog;
import net.battlespades.combat.*;
import net.battlespades.match.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.scene.SceneConfig;
import net.battlespades.transport.enet.Delivery;
import net.battlespades.world.BlockDamageModel.Cell;

/** Actual ordinary TDM/Arena shot and terrain authorities over the hosted match.
 * Corpse, disguise, mounted-device, shield-anchor, observation and achievement owners remain required.
 */
public final class HostedCombatRuntime implements AutoCloseable {
    public interface Ports {
        Iterable<? extends HitTrace.Corpse> corpses();boolean explodeCorpse(HitTrace.Corpse corpse,PlayerLife attacker,boolean show);
        boolean mountedGun(PlayerLife player);void clearDisguise(PlayerLife player);void shieldImpact(PlayerLife player,Vector at,boolean melee);
        void observeShot(PlayerLife player,ShootPacket packet,double now);void resolved(ShotTally.Resolved result);
        void validationReport(PlayerLife player,ShotValidation.Event report);void rewindReport(PlayerLife player,LagCompensation.Diagnostic report);
        void shotStimulus(Vector at,int source,int team,double radius,double lifetime);
        void dropped(PlayerLife player,ShootPacket packet,String reason);void cadenceDropped(PlayerLife player,ShootPacket packet,double now);
        void nearMiss(PlayerLife player,ShootPacket packet,Vector origin,Vector direction,CombatCatalog.Profile profile);void rejected(PlayerLife player,String reason);
        void blocksDestroyed(PlayerLife player,List<Cell> removed,List<Cell> collapsed,List<List<Cell>> chunks,Integer tool);
    }
    private static final int BLOCKS_DESTROYED=Math.toIntExact(SourceCatalog.load("hosted-combat").getArray("constants").getTable(0).getLong("blocks_destroyed"));
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final ApplicationHost host;private final SceneConfig scene;private final Ports ports;private final boolean buildDamage;
    private final CombatRuntime shots;private final TerrainDamage.Domain terrain=new Terrain();private boolean closed;
    public HostedCombatRuntime(ApplicationHost host,StandardMatchDomain match,SceneConfig scene,boolean buildDamage,CombatRuntime.Settings settings,Ports ports){
        this.host=Objects.requireNonNull(host);this.match=Objects.requireNonNull(match);this.scene=Objects.requireNonNull(scene);this.buildDamage=buildDamage;this.ports=Objects.requireNonNull(ports);
        if(!match.lives().players().isEmpty())throw new IllegalStateException("Combat must attach before admission");
        shots=new CombatRuntime(match.lives(),new CombatRuntime.Timing(){public int loop(){return match.frameLoop();}public double monotonic(){return match.monotonic();}public double tickSeconds(){return 1.0/60;}},settings,new Domain());
        match.lives().combat(shots);
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Combat outside active owner");}
    public CombatRuntime shots(){owned();return shots;}public TerrainDamage.Domain terrain(){owned();return terrain;}
    private final class Domain implements CombatRuntime.Domain {
        public boolean allowsShoot(PlayerLife p){owned();return true;} // TDM/Arena have no allow_action hook.
        public boolean burdensomePickup(PlayerLife p){return p.pickupBurdensome();}public boolean shootWithIntel(){return false;}public boolean mountedGun(PlayerLife p){return ports.mountedGun(p);}
        public boolean enemies(PlayerLife a,PlayerLife b){return a.team()!=b.team()||scene.friendlyFire();}
        public Iterable<? extends HitTrace.Entity> entities(){return match.entities().registry().all();}public Iterable<? extends HitTrace.Corpse> corpses(){return ports.corpses();}
        public void clearDisguise(PlayerLife p){ports.clearDisguise(p);}public void weaponFired(PlayerLife p,int tool){} // No weapon_fired hook in either ordinary mode.
        public void profileShot(PlayerLife p){match.profiles().shot(match.binding().row(p));}public void recordShot(PlayerLife p){match.combat().shot(match.binding().row(p));}
        public void observeShot(PlayerLife p,ShootPacket packet,double now){ports.observeShot(p,packet,now);}public void resolved(ShotTally.Resolved result){ports.resolved(result);}
        public void validationReport(PlayerLife p,ShotValidation.Event report){ports.validationReport(p,report);}public void rewindReport(PlayerLife p,LagCompensation.Diagnostic report){ports.rewindReport(p,report);}
        public void feedback(SimplePackets.ShootFeedbackPacket packet,int excluded){host.broadcastExcept(packet,Delivery.UNRELIABLE,excluded);}
        public void shotStimulus(Vector at,int source,int team,double radius,double lifetime){ports.shotStimulus(at,source,team,radius,lifetime);}
        public boolean dig(PlayerLife p,Vector origin,Vector direction,ShootPacket packet,List<Vector> eyes,double slack){return match.entities().terrain().dig(p,origin,direction,packet,eyes,slack);}
        public void dropped(PlayerLife p,ShootPacket packet,String reason){ports.dropped(p,packet,reason);}public void cadenceDropped(PlayerLife p,ShootPacket packet,double now){ports.cadenceDropped(p,packet,now);}
        public void nearMiss(PlayerLife p,ShootPacket packet,Vector origin,Vector direction,CombatCatalog.Profile profile){ports.nearMiss(p,packet,origin,direction,profile);}
        public boolean hardcore(PlayerLife p){return false;}public void reject(PlayerLife p,String reason){ports.rejected(p,reason);}public void shieldImpact(PlayerLife p,Vector at,boolean melee){ports.shieldImpact(p,at,melee);}
        public void playerHit(PlayerLife a,PlayerLife b,boolean head){match.profiles().hit(match.binding().row(a),match.binding().row(b));}
        public void reliable(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}public void unreliable(GamePacket packet){host.broadcast(packet,Delivery.UNRELIABLE);}
        public void damageEntity(HitTrace.Entity entity,double amount,PlayerLife p){match.entities().registry().damageEntity(entity.id(),amount,p,match.entities().context());}
        public boolean explodeCorpse(HitTrace.Corpse corpse,PlayerLife p,boolean show){return ports.explodeCorpse(corpse,p,show);}
        public boolean damageBlock(PlayerLife p,CombatGeometry.Cell cell,double amount){return match.entities().terrain().gun(p,new Cell(cell.x(),cell.y(),cell.z()),amount);}
    }
    private final class Terrain implements TerrainDamage.Domain {
        public boolean allowsDig(PlayerLife p,Cell cell){owned();return true;} // Ordinary mode_controls.allows default.
        public boolean buildDamage(){owned();return buildDamage;}
        public void repair(List<Cell> cells){match.runtime().repair().record(cells,match.frameLoop());}
        public void reliable(GamePacket packet){host.broadcast(packet,Delivery.RELIABLE);}
        public void sound(int sound,Cell cell,PlayerLife source){match.runtime().gameplay().sounds().play(new GameplaySounds.Cue(sound,1,new GameplaySounds.Position(cell.x(),cell.y(),cell.z()),1),null,false,source);}
        public void reject(PlayerLife p,String reason){p.shotStatistics().reject(p,reason,Map.of(),event->ports.validationReport(p,event));ports.rejected(p,reason);}
        public void profileBlocks(PlayerLife p,int count){if(p!=null)match.profiles().add(match.binding().row(p),BLOCKS_DESTROYED,count,0);}
        public void scoreBlocks(PlayerLife p,int count,List<List<Cell>> chunks){if(p!=null)match.combat().blocksDestroyed(match.binding().row(p),count,chunks.stream().map(List::size).toList());}
        public Integer activeShotTool(PlayerLife p){return p==null?null:shots.activeShotTool(p);}
        public void achievedBlocks(PlayerLife p,List<Cell> removed,List<Cell> collapsed,List<List<Cell>> chunks,Integer tool){ports.blocksDestroyed(p,removed,collapsed,chunks,tool);}
        public void collapsedRepair(List<Cell> cells){match.runtime().repair().recordCollapse(cells,match.frameLoop());}
        public void modeDestroyed(PlayerLife p,List<Cell> cells,boolean mined){match.events().offer(ModeEvents.Event.of("on_blocks_destroyed",match.binding().row(p),List.copyOf(cells),mined));}
    }
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Combat outside owner");closed=true;}
}
