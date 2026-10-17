// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import net.battlespades.combat.*;
import net.battlespades.physics.*;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.*;
import net.battlespades.protocol.*;

/** Explicit ordinary-mode test policy and observed service ports; never a production gameplay implementation. */
public final class CombatPorts implements CombatRuntime.Domain,CombatRuntime.Timing {
    public final LifeHarness harness;public int loop=1000;public boolean allows=true,burden,intel,mounted,disguised=true,rewindEnabled,failObserve,failResolved,failNearMiss,failPlayerHit;
    public java.util.function.Consumer<GamePacket> reliableSink,unreliableSink;
    public java.util.function.BiConsumer<GamePacket,Integer> feedbackSink;
    public final List<LagCompensation.Diagnostic> rewindEvents=new ArrayList<>();
    public final List<String> events=new ArrayList<>(),wire=new ArrayList<>();
    public CombatPorts(LifeHarness harness){this.harness=harness;harness.destination=new LifeRuntime.Messages(){
        public void broadcast(GamePacket p){capture("B",true,null,p);}public void owner(int id,GamePacket p){capture("O"+id,true,null,p);}public boolean inGame(int id){return true;}
    };}
    public int loop(){return loop;}public double monotonic(){return harness.monotonic;}public double tickSeconds(){return 1./60;}
    public CombatRuntime bind(){var runtime=new CombatRuntime(harness.runtime,this,new CombatRuntime.Settings(ShotValidation.Settings.defaults(),new LagCompensation.Settings(false,250,50,0,400,2)),this);harness.runtime.combat(runtime);return runtime;}
    public boolean allowsShoot(PlayerLife p){return allows;}public boolean burdensomePickup(PlayerLife p){return burden;}public boolean shootWithIntel(){return intel;}public boolean mountedGun(PlayerLife p){return mounted;}
    public boolean enemies(PlayerLife a,PlayerLife b){return a.team()!=b.team();}public Iterable<HitTrace.Entity> entities(){return List.of();}public Iterable<HitTrace.Corpse> corpses(){return List.of();}
    public void clearDisguise(PlayerLife p){disguised=false;}public void weaponFired(PlayerLife p,int tool){events.add("fired");}public void profileShot(PlayerLife p){events.add("profile");}public void recordShot(PlayerLife p){events.add("record");}public void observeShot(PlayerLife p,ShootPacket packet,double now){events.add("observe");if(failObserve)throw new IllegalStateException("Controlled aim observer fault");}public void resolved(ShotTally.Resolved r){events.add("resolved");if(failResolved)throw new IllegalStateException("Controlled achievement fault");}
    public void validationReport(PlayerLife p,ShotValidation.Event r){events.add("report:"+r.kind());}public void rewindReport(PlayerLife p,LagCompensation.Diagnostic r){if(!rewindEnabled)throw new AssertionError("Rewind disabled in these explicit test settings");rewindEvents.add(r);}
    public void feedback(SimplePackets.ShootFeedbackPacket p,int owner){capture("B",false,owner,p);if(feedbackSink!=null)feedbackSink.accept(p,owner);}public void shotStimulus(Vector at,int id,int team,double radius,double lifetime){if(radius!=72||lifetime!=1.25)throw new AssertionError("Stimulus contract");events.add("stimulus");}
    public boolean dig(PlayerLife p,Vector origin,Vector direction,ShootPacket packet,List<Vector> eyes,double slack){events.add("dig");if(HitTrace.terrainRay(CollisionMap.of(harness.world),origin,direction,DirectDamage.MELEE_RANGE)!=null)throw new AssertionError("This explicit test domain has no terrain on dig rays");return false;}
    public void dropped(PlayerLife p,ShootPacket packet,String reason){events.add("drop:"+reason);}public void cadenceDropped(PlayerLife p,ShootPacket packet,double now){dropped(p,packet,p.inventory().reloading()?"reloading":p.inventory().ammo().clip()<=0?"empty_clip":"fire_rate");}public void nearMiss(PlayerLife p,ShootPacket packet,Vector origin,Vector direction,CombatCatalog.Profile profile){events.add("near");if(failNearMiss)throw new IllegalStateException("Controlled log fault");}
    public boolean hardcore(PlayerLife p){return false;}public void reject(PlayerLife p,String reason){/* Counter/report observed through validationReport. */}public void shieldImpact(PlayerLife p,Vector at,boolean melee){events.add("shield");}public void playerHit(PlayerLife a,PlayerLife v,boolean head){events.add("hit");if(failPlayerHit)throw new IllegalStateException("Controlled profile fault");}
    public void reliable(GamePacket p){capture("B",true,null,p);if(reliableSink!=null)reliableSink.accept(p);}public void unreliable(GamePacket p){capture("B",false,null,p);if(unreliableSink!=null)unreliableSink.accept(p);}
    public void damageEntity(HitTrace.Entity e,double amount,PlayerLife p){throw new AssertionError("No entity domain in this fixture");}public boolean explodeCorpse(HitTrace.Corpse c,PlayerLife p,boolean show){throw new AssertionError("No corpse domain in this fixture");}public boolean damageBlock(PlayerLife p,CombatGeometry.Cell c,double amount){throw new AssertionError("No terrain on these fixture rays");}
    public void capture(String lane,boolean reliable,Integer excluded,GamePacket p){if(Set.of(5,46,8,9,20).contains(p.id()))wire.add(lane+":"+(reliable?1:0)+":"+(excluded==null?"-":excluded)+":"+HexFormat.of().formatHex(p.encode()));}
}
