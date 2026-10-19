// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;

import java.util.*;
import net.battlespades.match.*;
import net.battlespades.player.*;
import net.battlespades.protocol.*;
import net.battlespades.scene.SceneConfig;

/** Source fresh-arrival equipment and crouch actions; physics remains on the buffered timeline. */
public final class HostedInputRuntime implements LifeRuntime.Inputs,AutoCloseable {
    public interface Ports {
        boolean mountedGun(PlayerLife player);
        void rejectedTool(PlayerLife player,int requested,int held);
        /** Required shared anticheat owner counts every call and rate-limits its diagnostic output. */
        void movementReport(PlayerLife player,String kind,Map<String,Double> detail);
        /** Remaining paint owner receives authorized brush state, including secondary-trigger release. */
        void paintbrush(PlayerLife player,LiveClientInput input);
        void reset(PlayerLife player);void observedInput(PlayerLife player,LiveClientInput input,int receivedLoop);
        void packet(PlayerLife player,GamePacket packet);void forget(PlayerLife player);
    }
    private static final class State {boolean crouch;long rejected;}
    private final Thread owner=Thread.currentThread();private final StandardMatchDomain match;private final Ports ports;
    private final Map<PlayerLife,State> states=new IdentityHashMap<>();private final TeabagTracker teabags;private boolean closed;
    public HostedInputRuntime(StandardMatchDomain match,SceneConfig config,Ports ports){
        this.match=Objects.requireNonNull(match);this.ports=Objects.requireNonNull(ports);
        var objectives=new ObjectiveScores(()->new ProfileTracker.Context(false,config.modeCode(),match.mode().clock().ended),()->match.mode().clock().retiring,()->match.binding().players(),match.scoreboard());
        // Ordinary TDM/Arena inherit BaseMode's grave representation and have no equipped-tool override.
        teabags=new TeabagTracker(()->match.binding().players(),objectives,match.profiles(),()->false,()->config.rules().enabled("RULE_POINTS_FROM_TEABAGGING"));
    }
    private void owned(){if(Thread.currentThread()!=owner||closed)throw new IllegalStateException("Input runtime outside active owner");}
    public boolean allow(PlayerLife p,MovementPacketRates.Kind kind,int loop){
        owned();if(p.packetRates().allow(kind,p.bot(),match.monotonic(),loop))return true;
        ports.movementReport(p,"rate:"+kind.key(),Map.of("rate",kind.rate(),"burst",kind.burst()));return false;
    }
    public void position(PlayerLife p,LivePositionReport report,int loop){
        owned();if(!allow(p,MovementPacketRates.Kind.POSITION_DATA,loop))return;
        if(!p.positionReports().receive(p,report,match.wall()))ports.movementReport(p,"position_nonfinite",Map.of());
    }
    public boolean authorized(PlayerLife p,int tool){owned();if(!p.alive()||!p.spawned()||tool<0||tool>=66||!p.toolEnabled(tool))return false;return ToolSelection.authorized(p,tool,true,tool==15&&!p.selection().loadout().contains(tool)&&ports.mountedGun(p));}
    public Integer ensureLegal(PlayerLife p){owned();return ToolSelection.ensureLegal(p,tool->authorized(p,tool));}
    public Integer unmounted(PlayerLife p){owned();return ToolSelection.unmounted(p,tool->authorized(p,tool));}
    public long rejected(PlayerLife p){owned();var state=states.get(p);return state==null?0:state.rejected;}
    public void reset(PlayerLife p){owned();states.computeIfAbsent(p,key->new State()).crouch=false;teabags.spawned(match.binding().row(p));ports.reset(p);}
    public void receive(PlayerLife p,LiveClientInput input,int receivedLoop){
        owned();var state=states.computeIfAbsent(p,key->new State());boolean crouch=(input.movementFlags()&32)!=0;
        if(crouch&&!state.crouch){state.crouch=true;teabags.crouch(match.binding().row(p),match.monotonic());}else state.crouch=crouch;
        if(authorized(p,input.toolId())){
            p.fire().tool(input.toolId(),true);
            if(input.toolId()==43)ports.paintbrush(p,input);
        }else if(p.alive()&&p.spawned()){
            state.rejected++;ports.rejectedTool(p,input.toolId(),p.inventory().tool());ensureLegal(p);
        }
        ports.observedInput(p,input,receivedLoop);
    }
    public void packet(PlayerLife p,GamePacket packet){owned();ports.packet(p,packet);}
    public void forget(PlayerLife p){owned();states.remove(p);teabags.forget(match.binding().row(p));ports.forget(p);}
    @Override public void close(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Input runtime outside owner");if(closed)return;states.clear();teabags.clear();closed=true;}
}
