// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.match.*;
import net.battlespades.scene.ModeCatalog;
import net.battlespades.world.*;

/** Owns map/domain/scene identity through host commit, rollback and post-transition resource retirement. */
public final class HostedSceneState implements AutoCloseable {
    public interface Domain extends AutoCloseable {
        JoinScene scene();TransitionMode mode();TransitionRoster transitionRoster();List<TeamState> teams();ModeEvents events();
        @Override void close();
    }
    public interface HostBoundDomain extends Domain {void attach(ApplicationHost host);}
    /** Only explicitly registered concrete modes can be installed. A failing factory owns its partial domain cleanup. */
    @FunctionalInterface public interface Factory {Domain create(String map,String code,PreparedMap.Bound prepared,MapMetadata metadata);}
    private static final class Entry {
        final String map,code;final PreparedMap.Bound prepared;final Domain domain;final JoinScene scene;final TransitionMode mode;
        boolean retired;
        Entry(String map,String code,PreparedMap.Bound prepared,Domain domain){this.map=map;this.code=code;this.prepared=prepared;this.domain=domain;scene=Objects.requireNonNull(domain.scene());mode=Objects.requireNonNull(domain.mode());}
    }
    private final Thread owner=Thread.currentThread();
    private final Map<String,Factory> factories;
    private final Set<Entry> retained=Collections.newSetFromMap(new IdentityHashMap<>());
    private final int journalLimit;
    private Entry active;
    private ApplicationHost host;
    private long epoch;
    private Integer fogOverride;
    private boolean closed;
    public HostedSceneState(String map,String code,PreparedMap initial,long epoch,int journalLimit,Map<String,Factory> factories){
        var registry=new HashMap<String,Factory>();factories.forEach((key,value)->{if(!ModeCatalog.resolve(key).equals(key))throw new IllegalArgumentException("Mode factory must use a canonical code");registry.put(key,Objects.requireNonNull(value));});this.factories=Map.copyOf(registry);this.epoch=epoch;this.journalLimit=journalLimit;
        active=create(map,code,initial,epoch);retained.add(active);
    }
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Scene state outside owner");if(closed)throw new IllegalStateException("Scene state closed");}
    public void attach(ApplicationHost application){owned();if(host!=null)throw new IllegalStateException("Host already attached");if(application.transitionScene()!=active.scene)throw new IllegalArgumentException("Host starts with another scene");if(active.domain instanceof HostBoundDomain bound)bound.attach(application);host=application;}
    private ApplicationHost host(){if(host==null)throw new IllegalStateException("Host not attached");return host;}
    public JoinScene scene(){owned();return active.scene;}public Domain domain(){owned();return active.domain;}public PreparedMap.Bound prepared(){owned();return active.prepared;}
    public boolean hasMode(String code){owned();return factories.containsKey(code);}
    public Integer fogOverride(){owned();return fogOverride;}
    public void fogOverride(Integer rgb){owned();if(rgb!=null&&(rgb<0||rgb>0xffffff))throw new IllegalArgumentException("Fog RGB");fogOverride=rgb;}
    public SceneRollover.State snapshot(){owned();return new SceneRollover.State(active.map,active.code,active.mode,active,fogOverride);}
    private Entry create(String map,String code,PreparedMap candidate,long epoch){
        Objects.requireNonNull(map);Objects.requireNonNull(candidate);var factory=factories.get(code);if(factory==null)throw new IllegalArgumentException("Unknown mode: "+code);
        if(!candidate.assembly().metadata().activeMode().equals(net.battlespades.player.Hardcore.base(code)))throw new IllegalArgumentException("Prepared map belongs to another mode");
        var bound=candidate.bind(epoch,journalLimit);Domain domain=null;
        try{domain=Objects.requireNonNull(factory.create(map,code,bound,candidate.assembly().metadata()));var entry=new Entry(map,code,bound,domain);if(entry.scene.world()!=bound.world())throw new IllegalArgumentException("Domain must use its prepared world");return entry;}
        catch(RuntimeException|Error error){if(domain!=null)try{domain.close();}catch(RuntimeException|Error cleanup){error.addSuppressed(cleanup);}bound.close();throw error;}
    }
    public void install(String map,String code,PreparedMap candidate){
        owned();host();epoch=Math.incrementExact(epoch);var next=create(map,code,candidate,epoch);
        try{if(next.domain instanceof HostBoundDomain bound)bound.attach(host);host.installTransitionScene(next.scene);}catch(RuntimeException|Error failure){dispose(next,failure);throw failure;}
        retained.add(next);active=next;fogOverride=null;
    }
    public void setMode(TransitionMode mode){owned();if(mode!=active.mode)throw new IllegalArgumentException("Mode does not belong to installed domain");}
    public void restore(SceneRollover.State state){
        owned();host();if(!(state.world() instanceof Entry previous)||!retained.contains(previous)||previous.retired||!state.map().equals(previous.map)||!state.code().equals(previous.code)||state.mode()!=previous.mode)throw new IllegalArgumentException("Foreign or retired scene snapshot");
        if(state.fog()!=null&&(!(state.fog() instanceof Integer rgb)||rgb<0||rgb>0xffffff))throw new IllegalArgumentException("Fog RGB");
        host.installTransitionScene(previous.scene);active=previous;fogOverride=(Integer)state.fog();
    }
    private static void dispose(Entry entry,Throwable prior){
        if(entry.retired)return;entry.retired=true;Throwable error=prior;
        try{entry.domain.close();}catch(RuntimeException|Error failure){if(error==null)error=failure;else error.addSuppressed(failure);}
        try{entry.prepared.close();}catch(RuntimeException|Error failure){if(error==null)error=failure;else error.addSuppressed(failure);}
        if(prior==null){if(error instanceof RuntimeException ordinary)throw ordinary;if(error instanceof Error fatal)throw fatal;}
    }
    /** Call after successful reload or completed rollback recovery; the active domain is always retained. */
    public void finish(){
        owned();Throwable error=null;for(var entry:List.copyOf(retained))if(entry!=active){retained.remove(entry);try{dispose(entry,null);}catch(RuntimeException|Error failure){if(error==null)error=failure;else error.addSuppressed(failure);}}
        if(error instanceof RuntimeException ordinary)throw ordinary;if(error instanceof Error fatal)throw fatal;
    }
    /** The caller closes the host first so no loader still holds leases on a retiring world. */
    @Override public void close(){
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Scene state outside owner");if(closed)return;closed=true;Throwable error=null;
        for(var entry:List.copyOf(retained)){try{dispose(entry,null);}catch(RuntimeException|Error failure){if(error==null)error=failure;else error.addSuppressed(failure);}}retained.clear();
        if(error instanceof RuntimeException ordinary)throw ordinary;if(error instanceof Error fatal)throw fatal;
    }
}
