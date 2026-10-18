// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.support;

import java.util.*;
import net.battlespades.physics.PlayerPhysics;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.rules.GameRules;
import net.battlespades.world.*;

/** Explicit test mode/clock/effect ports around the production LifeRuntime; never packaged. */
public final class LifeHarness implements AutoCloseable {
    public final MutableWorld world;
    public final LifeRuntime runtime;
    public double wall=1000,monotonic=100,delay=1.5;
    public final Set<Integer> denied=new HashSet<>(),failing=new HashSet<>();
    public final Set<Integer> loading=new HashSet<>();
    public Optional<LifeRuntime.DamageModifier> damageModifier=Optional.empty();
    public boolean scoringActive=true;
    public java.util.function.Consumer<LifeRuntime.DamageEvidence> damageObserver=d->{};
    public java.util.function.Consumer<LifeRuntime.DeathEvidence> deathObserver=d->{};
    public final List<LifeRuntime.DamageEvidence> damageEvidence=new ArrayList<>();
    public final List<LifeRuntime.HealingEvidence> healingEvidence=new ArrayList<>();
    public final List<LifeRuntime.DeathEvidence> deathEvidence=new ArrayList<>();
    public final List<GamePacket> wire=new ArrayList<>();
    public final List<String> events=new ArrayList<>();
    public int inputCount;
    public LifeRuntime.Messages destination;
    public java.util.function.BiConsumer<PlayerLife,GamePacket> packetHandler=(player,packet)->{};
    public LifeHarness(){this(GameRules.serverDefaults());}
    public LifeHarness(GameRules rules){this(rules,"tdm");}
    public LifeHarness(GameRules rules,String mode){
        this(rules,mode,new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,10000));
    }
    public LifeHarness(GameRules rules,String mode,MutableWorld sourceWorld){this(rules,mode,sourceWorld,java.util.function.UnaryOperator.identity());}
    public LifeHarness(GameRules rules,String mode,MutableWorld sourceWorld,java.util.function.UnaryOperator<LifeRuntime.Effects> decorate){
        world=Objects.requireNonNull(sourceWorld);
        runtime=new LifeRuntime(world,rules,mode,new LifeRuntime.Clock(){public double wall(){return wall;}public double monotonic(){return monotonic;}},new LifeRuntime.Policy(){
            public AdmissionPolicy.Context admissionContext(int id){return AdmissionPolicy.Context.ordinary(runtime.players().stream().map(p->new AdmissionPolicy.Occupant(p.id(),p.create().name(),false,p.team())).toList());}
            public void prepareSpawn(PlayerLife p){events.add("prepare:"+p.id()+":"+p.selection().classId());if(failing.contains(p.id()))throw new IllegalStateException("Controlled prepare failure");}
            public PlayerPhysics.Vector spawnPoint(PlayerLife p){return new PlayerPhysics.Vector(36,36,97);}
            public Optional<PlayerPhysics.Vector> spawnOrientation(PlayerLife p){return Optional.empty();}
            public boolean canRespawn(PlayerLife p){return !denied.contains(p.id());}
            public double respawnDelay(PlayerLife p){events.add("delay:"+p.alive());return delay;}
            public double walletScale(){return 1;}
            public int teamColor(PlayerLife p){return p.team()==2?0x123456:p.team()==3?0x323c46:0x707070;}
            public int score(PlayerLife p){return 0;}
            public boolean opponents(PlayerLife a,PlayerLife b){return a.team()!=b.team();}
            public int deathType(PlayerLife v,PlayerLife k,int type){return type;}
            public Optional<LifeRuntime.DamageModifier> damageModifier(){return damageModifier;}
            public boolean combatScoringActive(){return scoringActive;}
        },decorate.apply(new LifeRuntime.Effects(){
            public void beforeSpawn(PlayerLife p){events.add("before:"+p.id());}
            public void spawned(PlayerLife p){events.add("spawn:"+p.id());}
            public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){events.add("death:"+v.id());}
            public void damaged(LifeRuntime.DamageEvidence d){damageEvidence.add(d);events.add("damage:"+d.victim().id());damageObserver.accept(d);}
            public void healed(LifeRuntime.HealingEvidence h){healingEvidence.add(h);events.add("heal:"+h.victim().id());}
            public void beforeDeath(LifeRuntime.DeathEvidence d){deathEvidence.add(d);events.add("before-death:"+d.victim().id());deathObserver.accept(d);}
            public void transitionCredited(PlayerLife v,PlayerLife a,int type){events.add("transition-credit:"+v.id()+":"+a.id()+":"+type);}
            public void forget(PlayerLife p){events.add("forget:"+p.id());}
            public void respawnFailed(PlayerLife p,RuntimeException failure){events.add("failed:"+p.id());}
        }),new LifeRuntime.Messages(){
            public void broadcast(GamePacket p){wire.add(p);if(destination!=null)destination.broadcast(p);}
            public void owner(int id,GamePacket p){wire.add(p);if(destination!=null)destination.owner(id,p);}
            public boolean inGame(int id){return !loading.contains(id)&&(destination==null||destination.inGame(id));}
        },new LifeRuntime.Inputs(){
            public void reset(PlayerLife p){events.add("reset:"+p.id());}
            public void receive(PlayerLife p,LiveClientInput input,int loop){inputCount++;}
            public void packet(PlayerLife p,GamePacket packet){events.add("packet:"+p.id());packetHandler.accept(p,packet);}
            public void forget(PlayerLife p){events.add("input-forget:"+p.id());}
        });
    }
    public void advance(double elapsed){wall+=elapsed;monotonic+=elapsed;runtime.processRespawns();}
    public net.battlespades.session.JoinScene.Admission admit(int id,int team){return runtime.admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"Life"),new ClassCatalog.Selection(0,List.of(5,6,23),List.of(),List.of()),0);}
    @Override public void close(){world.close();}
}
