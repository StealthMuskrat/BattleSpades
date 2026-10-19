// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.simulation.FixedStepRuntime;

/** Source simulate_tick and connected-player Player.update movement/flight binding.
 * Required hooks expose unported mode/reload/lag/observer work. This is not a mode or hosting loop.
 */
public final class InfantrySimulation {
    public record Settings(double gravity,boolean sameTeamCollision,boolean fallDamage,int gapLimit,
        boolean enforceStarvation,double timeoutSeconds,int airborneTicks,boolean enforceBacklog,int backlogFrames,boolean clientAuthority) {
        public Settings(double gravity,boolean sameTeamCollision,boolean fallDamage,int gapLimit,boolean enforceStarvation,double timeoutSeconds,int airborneTicks,boolean enforceBacklog,int backlogFrames){this(gravity,sameTeamCollision,fallDamage,gapLimit,enforceStarvation,timeoutSeconds,airborneTicks,enforceBacklog,backlogFrames,false);}
        public Settings {if(!Double.isFinite(gravity)||gravity<0||!Double.isFinite(timeoutSeconds))throw new IllegalArgumentException("Simulation settings");}
        public static Settings defaults(){return new Settings(1,false,true,8,false,8,24,false,6);}
    }
    public interface Hooks {
        void lagHistory(PlayerLife player,int tick);
        void housekeeping(PlayerLife player);
        boolean burdened(PlayerLife player);
        int pendingTerrainMutations();
        void report(PlayerLife player,String kind,boolean enforced);
        void disconnect(PlayerLife player,int reason);
        void afterMove(PlayerLife player,double dt);
        void afterTick(PlayerLife player,double dt);
        void failed(PlayerLife player,RuntimeException failure);
    }
    private final Thread owner=Thread.currentThread();
    private final LifeRuntime runtime;
    private final Hooks hooks;
    private Settings settings;
    public InfantrySimulation(LifeRuntime runtime,Settings settings,Hooks hooks){this.runtime=Objects.requireNonNull(runtime);this.settings=Objects.requireNonNull(settings);this.hooks=Objects.requireNonNull(hooks);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Infantry simulation accessed outside owner");}
    public void settings(Settings value){owned();settings=Objects.requireNonNull(value);}
    public Settings settings(){owned();return settings;}
    /** Bind this to the owned 60 Hz scheduler after input drain and before terrain commits/replication. */
    public void tick(int tick){
        owned();for(var p:runtime.players()){
            if(!p.bot()&&!runtime.inGame(p.id()))continue;
            try{simulate(p,tick);hooks.afterTick(p,FixedStepRuntime.DELTA);}catch(RuntimeException failure){hooks.failed(p,failure);}
        }
    }
    private void simulate(PlayerLife p,int tick){
        p.lagTarget().history().record(p.lagTarget(),tick-1);hooks.lagHistory(p,tick);if(!p.alive()||!p.spawned())return;
        if(p.movementMode().equals("crumbling_run"))throw new IllegalStateException("Race momentum requires its own simulation binding");
        var m=p.motion();var timeline=p.inputTimeline();double now=runtime.movementClock();
        Double last=m.lastSimulated;m.lastSimulated=now;if(last==null||now-last>1)m.starvationBaseline=now;
        double silent=now-Math.max(Math.max(m.lastPacket==null?0:m.lastPacket,m.starvationBaseline),p.spawnedAt());
        if(settings.timeoutSeconds>0){
            if(silent<settings.timeoutSeconds)m.timeoutFlagged=false;
            else if(!m.timeoutFlagged){m.timeoutFlagged=true;hooks.report(p,"input_starvation_timeout",settings.enforceStarvation);if(settings.enforceStarvation){hooks.disconnect(p,11);return;}}
        }
        timeline.pruneStale();
        if(timeline.size()==0){
            m.starved++;m.starvedStreak++;m.backlogOver=0;var body=p.body().state();
            if(settings.airborneTicks>0&&m.starvedStreak>=settings.airborneTicks&&body.airborne()&&!body.wade()){
                if(m.starvedStreak==settings.airborneTicks)hooks.report(p,"input_starvation_airborne",settings.enforceStarvation);
                if(settings.enforceStarvation){m.controls(m.movement&32,m.actions&~128);m.gravitySteps++;move(p);return;}
            }
            housekeeping(p);return;
        }
        m.starvedStreak=0;boolean catchup=backlog(p,tick);consume(p,tick);
        Integer applied=timeline.appliedLabel();
        if(catchup&&p.alive()&&p.spawned()&&applied!=null&&applied<Integer.MAX_VALUE&&timeline.frame(applied+1)!=null){m.catchup++;consume(p,tick);}
    }
    private void housekeeping(PlayerLife p){p.motion().housekeeping++;p.fire().advanceAndAnnounce(runtime.movementClock(),runtime::reloadAnnouncement);hooks.housekeeping(p);}
    private void consume(PlayerLife p,int tick){
        var t=p.inputTimeline();var m=p.motion();var first=t.firstFrame();
        var step=t.consumeOne(settings.gapLimit,p.aim()).orElseThrow();
        if(!step.synthesized())m.delay(tick-first.receivedTick());
        p.orientation(step.aim().x(),step.aim().y(),step.aim().z());m.controls(step.movement(),step.actions());
        applyImpulses(p,step.label());if(!step.synthesized())m.applyExplosionsThrough(p,step.inputSequence());
        try{move(p);}finally{if(!step.synthesized()){var aim=t.appliedAim();p.orientation(aim.x(),aim.y(),aim.z());}}
        m.eye(step.label(),p.position(),step.synthesized()?step.aim():t.appliedAim());
    }
    private List<PlayerPhysics.Peer> peers(PlayerLife p){
        var peers=new ArrayList<PlayerPhysics.Peer>();
        for(var other:runtime.players()){
            if(other==p||!other.alive()||!other.spawned()||other.team()==p.team()&&!settings.sameTeamCollision)continue;
            var at=other.position();boolean crouch=(other.motion().movement&32)!=0&&!other.body().state().wade();
            peers.add(new PlayerPhysics.Peer(at.x(),at.y(),at.z(),(crouch?1.35:2.25)+0.45));
        }
        return peers;
    }
    private void move(PlayerLife p){
        if(!p.alive()||!p.spawned())return;
        housekeeping(p);var m=p.motion();var body=p.body();var before=body.state();double dt=FixedStepRuntime.DELTA;
        m.movementTime+=dt;m.triggerJump=(m.movement&16)!=0&&!before.airborne();var peers=peers(p);
        int f=m.movement;body.input(new PlayerPhysics.Input((f&1)!=0,(f&2)!=0,(f&4)!=0,(f&8)!=0,(f&16)!=0,(f&64)!=0,(f&128)!=0));
        p.flight().beforeMove(p,dt,runtime.movementWallClock());if(p.flight().ownsJump())m.retailJumpLabel=null;
        body.flight(p.flight().mover(p));body.burdened(hooks.burdened(p));body.crouch((f&32)!=0,peers);
        double canopyVelocity=body.state().velocity().z();boolean canopyPhysics=p.flight().canopyPhysics();
        m.fallResult=body.update(dt,settings.gravity,peers);var after=body.state();
        if(after.jumped()&&Boolean.FALSE.equals(m.flightCapable)&&!p.flight().ownsJump())m.retailJumpLabel=p.inputTimeline().appliedLabel();
        m.fallResult=p.flight().afterMove(p,dt,before.airborne(),canopyVelocity,canopyPhysics,m.fallResult,settings.gravity);
        if(before.airborne())m.airTime+=dt;else if(after.airborne())m.airborneSince=runtime.movementClock();
        if(m.fallResult>0&&settings.fallDamage&&(after.position().z()<=237||p.waterFallDamage())){
            double fraction=Math.max(0,Math.min(1,(m.airTime-1.0/60)/(4.0/60-1.0/60)));double amount=m.fallResult*fraction;
            if(m.ownBlast!=null&&m.ownBlast>=m.airborneSince-0.5)amount*=0.2;
            if(amount>0)runtime.damage(p.id(),amount,null,7);
        }
        if(!after.airborne()){m.airTime=0;if(before.airborne())m.ownBlast=null;}
        if(settings.clientAuthority)p.positionReports().pin(p,runtime.movementWallClock());
        m.landed=before.airborne()&&!p.body().state().airborne();hooks.afterMove(p,dt);
    }
    /** Connected-player labelled knockback; detached explosion actor/effect timelines are not supplied here. */
    public void queueVelocityImpulse(PlayerLife p,int label,Vector velocity){
        owned();var m=p.motion();var applied=p.inputTimeline().appliedLabel();
        if(applied!=null&&applied>=label||m.impulses.size()>=64)impulse(p,velocity);else m.impulses.addLast(new PlayerMotion.Impulse(label,velocity));
    }
    private static void impulse(PlayerLife p,Vector value){var v=PlayerMotion.clipped(p.body().state().velocity());p.body().velocity(new Vector(v.x()+value.x(),v.y()+value.y(),v.z()+value.z()));}
    private static void applyImpulses(PlayerLife p,int label){var q=p.motion().impulses;for(var iterator=q.iterator();iterator.hasNext();){var effect=iterator.next();if(effect.label()<=label){impulse(p,effect.velocity());iterator.remove();}}}
    private boolean pairSafe(PlayerLife p){
        var t=p.inputTimeline();Integer applied=t.appliedLabel();if(applied==null||applied>Integer.MAX_VALUE-2)return false;
        var first=t.frame(applied+1);var second=t.frame(applied+2);if(first==null||second==null)return false;
        long topology=runtime.world().topologyVersion();if(first.topology()!=topology||second.topology()!=topology||hooks.pendingTerrainMutations()>0||!p.motion().impulses.isEmpty()||!p.motion().pendingExplosions().isEmpty())return false;
        if(p.flight().transitionPending())return false;
        if((p.jetpack()!=0||p.parachute()!=0)&&((first.movement()|second.movement()|t.pendingMovement())&16)!=0)return false;
        return (first.actions()==null||(first.actions()&128)==0)&&(second.actions()==null||(second.actions()&128)==0);
    }
    private boolean idlePairSafe(PlayerLife p){
        var t=p.inputTimeline();var m=p.motion();var s=p.body().state();var v=PlayerMotion.clipped(s.velocity());
        if(!Boolean.FALSE.equals(m.flightCapable)||p.jetpack()!=0||t.size()<=2||s.airborne()||s.wade()||Math.max(Math.max(Math.abs(v.x()),Math.abs(v.y())),Math.abs(v.z()))>1e-7||t.pendingMovement()!=0||t.hasPresses()||(m.movement&191)!=0||(m.actions&128)!=0||!pairSafe(p))return false;
        var first=t.frame(t.appliedLabel()+1);var second=t.frame(t.appliedLabel()+2);Integer actions=first.actions();
        if(first.movement()!=0||second.movement()!=0||!Objects.equals(actions,second.actions())||!Objects.equals(actions,t.appliedActions())||actions!=null&&(actions&~280)!=0)return false;
        var at=p.position();return peers(p).stream().noneMatch(peer->{double x=peer.x()-at.x(),y=peer.y()-at.y(),z=peer.z()-at.z();return x*x+y*y+z*z<16;});
    }
    private boolean backlog(PlayerLife p,int tick){
        var t=p.inputTimeline();var m=p.motion();if(idlePairSafe(p))return true;
        int cap=Math.max(1,settings.backlogFrames);long delay=(long)tick-t.firstFrame().receivedTick();
        if(m.backlogCatchup){if(delay<=Math.max(1,cap/2)){m.backlogCatchup=false;m.backlogOver=0;return false;}}
        else{if(delay<=cap){m.backlogOver=0;return false;}m.backlogOver++;if(m.backlogOver<=60)return false;hooks.report(p,"input_backlog",settings.enforceBacklog);if(!settings.enforceBacklog){m.backlogOver=0;return false;}m.backlogCatchup=true;}
        if(pairSafe(p))return true;
        int excess=t.size()-cap;for(int i=0;i<excess;i++){var frame=t.dropOldest().orElseThrow();if(frame.actions()!=null)m.actions=frame.actions();applyImpulses(p,frame.label());m.applyExplosionsThrough(p,frame.inputSequence());m.backlogDropped++;}
        return false;
    }
}
