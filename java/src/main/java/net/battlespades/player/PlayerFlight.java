// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import net.battlespades.physics.PlayerPhysics;

/** Owner-confined resource and canopy timelines from the original Player flight methods.
 * Retail handoff constants are the source server's compatibility heuristics, not network acknowledgements.
 */
public final class PlayerFlight {
    public record Settings(int activationDefer,int exhaustionTail,boolean latencyAware,
        double minimumClearance,double maximumOpenSeconds,double maximumRiseVelocity,int canopyHandoff) {
        public Settings {if(!Double.isFinite(minimumClearance)||!Double.isFinite(maximumOpenSeconds)||!Double.isFinite(maximumRiseVelocity))throw new IllegalArgumentException("Canopy settings");}
        public static Settings defaults(){return new Settings(2,3,true,6,30,.05,3);}
    }
    public record CanopyPhase(long label,boolean active) {}
    public record Event(boolean active,String reason,Integer label) {}
    public record Snapshot(double fuel,boolean active,boolean physicsActive,int defer,int tail,boolean requiresRelease,
        double heldSeconds,double idleSeconds,boolean canopy,boolean canopyPhysics,boolean canopyOwner,
        int unsentFrames,boolean hoverHeld,boolean jumpHeld,boolean deployPending,boolean usedThisFall,
        long openFrames,boolean fallTouched,long fallbackClock,List<CanopyPhase> schedule,Event event) {}
    private final Thread owner=Thread.currentThread();
    private FlightProfile profile=FlightProfile.RETAIL;
    private Settings settings=Settings.defaults();
    private boolean capable;
    private Double rtt;
    private double fuel=100,heldSeconds,idleSeconds;
    private boolean active,physicsActive,requiresRelease;
    private int defer,tail;
    private boolean canopy,canopyPhysics,canopyOwner,hoverHeld,jumpHeld,deployPending,usedThisFall,fallTouched;
    private int unsentFrames;
    private long openFrames,fallbackClock;
    private final ArrayDeque<CanopyPhase> schedule=new ArrayDeque<>();
    private Event event;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Flight accessed outside owner");}
    public void negotiate(int capability){owned();capable=capability>0;profile=FlightProfile.forCapability(capability);}
    public FlightProfile profile(){owned();return profile;}
    public void profile(FlightProfile value){owned();profile=Objects.requireNonNull(value);}
    /** Current ENet peer measurement; null means no measurement. No application ACK is inferred. */
    public void roundTripMillis(Double value){owned();rtt=value!=null&&Double.isFinite(value)?value:null;}
    public OptionalDouble roundTripMillis(){owned();return rtt==null?OptionalDouble.empty():OptionalDouble.of(rtt);}
    public void settings(Settings value){owned();settings=Objects.requireNonNull(value);}
    void resetLife(){owned();fuel=100;heldSeconds=idleSeconds=0;clearPack();clearCanopy();}
    void died(){owned();clearPack();clearCanopy();}
    private void clearPack(){active=physicsActive=requiresRelease=false;defer=tail=0;}
    private void clearCanopy(){canopy=canopyPhysics=canopyOwner=hoverHeld=jumpHeld=deployPending=usedThisFall=fallTouched=false;unsentFrames=0;openFrames=fallbackClock=0;schedule.clear();event=null;}
    public Snapshot snapshot(){owned();return new Snapshot(fuel,active,physicsActive,defer,tail,requiresRelease,heldSeconds,idleSeconds,canopy,canopyPhysics,canopyOwner,unsentFrames,hoverHeld,jumpHeld,deployPending,usedThisFall,openFrames,fallTouched,fallbackClock,List.copyOf(schedule),event);}
    boolean ownsJump(){return active||physicsActive||defer!=0||tail!=0||canopy||canopyPhysics||deployPending;}
    boolean transitionPending(){return ownsJump()||!schedule.isEmpty();}
    boolean canopyPhysics(){return canopyPhysics;}
    void restock(int pack){owned();fuel=LifeCatalog.PACK_FUEL.get(pack);}
    void beforeMove(PlayerLife p,double dt,double wall){owned();updatePack(p,dt,wall);updateCanopy(p,dt);}
    private int handoff(PlayerLife p,int fallback,boolean latest){
        int frames=capable?fallback:Math.max(0,Math.min(30,latest?settings.exhaustionTail:settings.activationDefer));
        if(capable||!settings.latencyAware)return frames;
        Integer applied=p.inputTimeline().appliedLabel();long ahead=0;
        if(applied!=null&&p.inputTimeline().size()>0)ahead=Math.max(0,Math.min(10,(long)p.inputTimeline().lastFrame().label()-applied));
        double network=Math.max(0,(rtt==null?0:Math.max(0,rtt))-8)*60/1000;
        return (int)Math.max(0,Math.min(30,Math.max(frames,ahead+(latest?3:2)-1)+(latest?Math.ceil(network):Math.floor(network))));
    }
    private void updatePack(PlayerLife p,double dt,double wall){
        var props=LifeCatalog.PACKS.get(p.jetpack());
        if(props==null||!p.alive()){clearPack();heldSeconds=0;return;}
        boolean combat=p.jetpack()>=66&&p.jetpack()<=68;
        double drain=combat?profile.drain().get(p.jetpack()-66):props.drain();
        double refill=combat?profile.refill().get(p.jetpack()-66):props.refill();
        boolean held=p.jetpack()==69?(p.motion().actions&128)!=0:(p.motion().movement&16)!=0;
        if(held||active||physicsActive)idleSeconds=0;else idleSeconds+=dt;
        if(held&&requiresRelease&&tail>0){active=false;physicsActive=true;defer=0;tail--;fuel=0;return;}
        boolean previous=active;
        if(previous&&defer>0){physicsActive=false;defer--;}else physicsActive=previous;
        boolean newly=false;
        if(held){
            heldSeconds+=dt;
            if(!previous&&!requiresRelease&&heldSeconds>=props.startDelay()&&fuel>=Math.max(props.activationCost(),1)){
                active=true;fuel-=props.activationCost();physicsActive=false;defer=handoff(p,2,false);tail=0;newly=true;
            }
        }else{heldSeconds=0;clearPack();}
        if(!newly&&(active||physicsActive)){
            fuel-=drain*dt;
            if(fuel<=0){fuel=0;active=false;requiresRelease=true;defer=0;tail=physicsActive?handoff(p,3,true):0;}
        }
        var body=p.body().state();
        if(!active&&!physicsActive&&fuel<props.maximumFuel()&&(!combat||!profile.groundedRefillOnly()||((!body.airborne()||body.wade())&&idleSeconds+1e-9>=profile.refillIdleSeconds()))&&wall-p.lastDamageWall()>=props.damageDelay())fuel=Math.min(props.maximumFuel(),fuel+refill*dt);
    }
    private boolean immediate(){return profile.descendingParachuteOnly();}
    private void advertise(PlayerLife p,boolean value,String reason){
        if(value==canopy)return;canopy=value;
        if(value){openFrames=0;usedThisFall=true;deployPending=false;}
        event=new Event(value,reason,p.inputTimeline().appliedLabel());
    }
    private void updateCanopy(PlayerLife p,double dt){
        boolean hover=(p.motion().actions&128)!=0,jump=(p.motion().movement&16)!=0;
        boolean pressed=hover&&!hoverHeld||jump&&!jumpHeld;hoverHeld=hover;jumpHeld=jump;
        var body=p.body().state();boolean grounded=!body.airborne()||body.wade();
        if(grounded)usedThisFall=false;
        if(grounded||!p.alive()||p.parachute()!=72||LifeCatalog.PACKS.containsKey(p.jetpack())){
            deployPending=false;if(canopy)advertise(p,false,grounded?"grounded":"unequipped");
        }else if(canopy){
            openFrames++;
            if(settings.maximumOpenSeconds>0&&openFrames*dt>=settings.maximumOpenSeconds)advertise(p,false,"timeout");
            else if(PlayerMotion.clipped(body.velocity()).z() < -settings.maximumRiseVelocity)advertise(p,false,"lifted");
        }else{
            if(pressed&&!usedThisFall)deployPending=true;
            if(deployPending&&PlayerMotion.clipped(body.velocity()).z()>=0&&p.canopyClearance()>=settings.minimumClearance)advertise(p,true,"deploy");
        }
        advance(p);
    }
    private long clock(PlayerLife p){var label=p.inputTimeline().appliedLabel();return label==null?fallbackClock:label;}
    private int canopyHandoff(PlayerLife p){
        long frames=settings.canopyHandoff;var t=p.inputTimeline();var label=t.appliedLabel();
        if(label!=null&&t.size()>0)frames=Math.max(frames,Math.min((long)t.lastFrame().label()-label,10)+settings.canopyHandoff-1L);
        double network=rtt==null?0:Math.rint(Math.max(0,rtt)*60/1000);
        return (int)Math.max(1,Math.min(30,frames+network));
    }
    private void queue(PlayerLife p,boolean value){canopyOwner=value;unsentFrames=0;schedule.addLast(new CanopyPhase(clock(p)+canopyHandoff(p),value));}
    /** Call only after an owner self row carrying the advertised state was actually queued. */
    public void ownerRowQueued(PlayerLife p){owned();if(p.flight()!=this)throw new IllegalArgumentException("Owner row belongs to a different player");if(canopy==canopyOwner)return;if(immediate())canopyOwner=canopy;else queue(p,canopy);}
    private void advance(PlayerLife p){
        fallbackClock++;
        if(immediate()){schedule.clear();canopyOwner=canopy;unsentFrames=0;canopyPhysics=canopy;return;}
        while(!schedule.isEmpty()&&clock(p)>=schedule.getFirst().label)canopyPhysics=schedule.removeFirst().active;
        if(canopy!=canopyOwner){unsentFrames++;if(unsentFrames>=30)queue(p,canopy);}else unsentFrames=0;
    }
    PlayerPhysics.Flight mover(PlayerLife p){return new PlayerPhysics.Flight(p.jetpack()==0?0:p.jetpack()-65,physicsActive,p.jetpack()==67&&physicsActive,p.alive()&&p.jetpack()==69&&(p.motion().actions&128)!=0,p.parachute()!=0,canopyPhysics,(float)profile.engineerAcceleration(),(float)profile.canopyGravity(),profile.freeFallFloor());}
    int afterMove(PlayerLife p,double dt,boolean wasAirborne,double preVelocity,boolean usedPhysics,int nativeResult,double gravity){
        var body=p.body().state();if(wasAirborne&&(usedPhysics||canopy))fallTouched=true;
        if(body.airborne()&&!body.wade())return nativeResult;
        if(canopy)advertise(p,false,body.wade()?"water":"landed");deployPending=usedThisFall=false;
        if(immediate()){schedule.clear();canopyOwner=canopy;canopyPhysics=canopy;p.body().flight(mover(p));}
        boolean touched=fallTouched;fallTouched=false;
        if(!touched||!wasAirborne)return nativeResult;
        int damage=speedDamage(p,dt,preVelocity,usedPhysics,gravity);return damage>Math.max(0,nativeResult)?damage:nativeResult;
    }
    int speedDamage(PlayerLife p,double dt,double preVelocity,boolean usedPhysics,double gravity){
        if(gravity<=0)return 0;
        double speed=usedPhysics?profile.canopyStep(preVelocity,dt,gravity):(preVelocity+dt*gravity)/(1+dt);
        if(speed<=0)return 0;
        double velocity=0,distance=0;
        for(int i=0;i<1800;i++){velocity=(velocity+dt*gravity)/(1+dt);if(velocity>=speed)break;distance+=velocity*dt*32;}
        var movement=p.movementProfile();double fall=distance*gravity,span=(double)movement.fallMaxDistance()-movement.fallMinDistance();
        double ratio=span>0?(fall-movement.fallMinDistance())/span:fall>=movement.fallMaxDistance()?1:0;
        int damage=(int)(movement.fallMaxDamage()*Math.min(1,Math.max(0,ratio)));
        // Source canopy policy uses the class multiplier even when the later HP rule disables water damage.
        return p.position().z()>237?(int)(damage*p.canopyWaterDamage()):damage;
    }
}
