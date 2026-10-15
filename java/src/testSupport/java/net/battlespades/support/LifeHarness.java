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
    public final List<GamePacket> wire=new ArrayList<>();
    public final List<String> events=new ArrayList<>();
    public int inputCount;
    public LifeRuntime.Messages destination;
    public LifeHarness(){this(GameRules.serverDefaults());}
    public LifeHarness(GameRules rules){
        world=new MutableWorld(VxlMap.load(new byte[]{0,(byte)240,(byte)239,0},"auto"),1,10000);
        runtime=new LifeRuntime(world,rules,"tdm",new LifeRuntime.Clock(){public double wall(){return wall;}public double monotonic(){return monotonic;}},new LifeRuntime.Policy(){
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
        },new LifeRuntime.Effects(){
            public void beforeSpawn(PlayerLife p){events.add("before:"+p.id());}
            public void spawned(PlayerLife p){events.add("spawn:"+p.id());}
            public void died(PlayerLife v,PlayerLife k,int weapon,int presentation,boolean enemy){events.add("death:"+v.id());}
            public void forget(PlayerLife p){events.add("forget:"+p.id());}
            public void respawnFailed(PlayerLife p,RuntimeException failure){events.add("failed:"+p.id());}
        },new LifeRuntime.Messages(){
            public void broadcast(GamePacket p){wire.add(p);if(destination!=null)destination.broadcast(p);}
            public void owner(int id,GamePacket p){wire.add(p);if(destination!=null)destination.owner(id,p);}
        },new LifeRuntime.Inputs(){
            public void reset(PlayerLife p){events.add("reset:"+p.id());}
            public void receive(PlayerLife p,LiveClientInput input,int loop){inputCount++;}
            public void packet(PlayerLife p,GamePacket packet){events.add("packet:"+p.id());}
            public void forget(PlayerLife p){events.add("input-forget:"+p.id());}
        });
    }
    public void advance(double elapsed){wall+=elapsed;monotonic+=elapsed;runtime.processRespawns();}
    public net.battlespades.session.JoinScene.Admission admit(int id,int team){return runtime.admit(id,new SimplePackets.NewPlayerConnection(team,0,0,0,"Life"),new ClassCatalog.Selection(0,List.of(5,6,23),List.of(),List.of()),0);}
    @Override public void close(){world.close();}
}
