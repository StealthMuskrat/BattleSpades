// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.combat;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.battlespades.physics.PlayerPhysics.Vector;
import net.battlespades.player.PlayerLife;

/** Read-only target rewind from server/lag_compensation.py. Terrain and authoritative bodies remain live. */
public final class LagCompensation {
    private LagCompensation() {}
    public record Settings(boolean enabled,double maximumMillis,double extraMillis,double viewDelayMillis,double lateShotMillis,int broadcastInterval) {
        public Settings {if(!Double.isFinite(maximumMillis)||!Double.isFinite(extraMillis)||!Double.isFinite(viewDelayMillis)||!Double.isFinite(lateShotMillis))throw new IllegalArgumentException("Nonfinite rewind setting");}
        public static Settings defaults(){return new Settings(true,250,50,0,400,2);}
        public static Settings from(org.tomlj.TomlTable raw){
            Object enabled=raw.get("network.lag_compensation_enabled");if(enabled!=null&&!(enabled instanceof Boolean))throw new IllegalArgumentException("network.lag_compensation_enabled must be boolean");
            return new Settings(enabled==null||(Boolean)enabled,number(raw,"network.lag_compensation_max_ms",250,1000),
                number(raw,"network.lag_compensation_extra_ms",50,250),number(raw,"network.lag_compensation_view_delay_ms",0,250),
                number(raw,"network.lag_compensation_late_shot_ms",400,1000),net.battlespades.replication.ReplicationService.Settings.from(raw).interval());
        }
        private static double number(org.tomlj.TomlTable raw,String key,double fallback,double maximum){Object value=raw.get(key);if(value==null)return fallback;if(value instanceof Number n&&Double.isFinite(n.doubleValue()))return Math.min(maximum,Math.max(0,n.doubleValue()));throw new IllegalArgumentException(key+" must be finite numeric");}
    }
    /** Packet-handler scope equivalent to the source compensated() context manager. */
    public static final class ShotOwner {
        private final Thread owner=Thread.currentThread();
        private Scope current;
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Combat rewind outside owner");}
        public Scope install(Context context){owned();var scope=new Scope(context,current);current=scope;return scope;}
        public Hitbox body(Target target){owned();return current==null||current.context==null?target:current.context.body(target);}
        public final class Scope implements AutoCloseable {
            private final Context context;
            private final Scope previous;
            private boolean closed;
            private Scope(Context context,Scope previous){this.context=context;this.previous=previous;}
            public Context context(){owned();return context;}
            @Override public void close(){owned();if(closed)return;if(current!=this)throw new IllegalStateException("Shot scopes must close in order");current=previous;closed=true;}
        }
    }
    public interface Hitbox {
        Target target();Vector position();Vector orientation();boolean crouched();double rewoundTicks();
    }
    public interface Target extends Hitbox {
        History history();boolean live();long life();
        @Override default Target target(){return this;}
        @Override default double rewoundTicks(){return 0;}
    }
    public record Rewound(Target target,Vector position,Vector orientation,boolean crouched,double rewoundTicks) implements Hitbox {}
    public record Sample(int tick,Vector position,Vector orientation,boolean crouched,boolean live,long life,long epoch) {}
    public static final class History {
        private final Thread owner=Thread.currentThread();
        private final Sample[] ring=new Sample[128];
        private Sample last;private long epoch;
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Lag history outside owner");}
        public Sample sample(int tick){owned();var s=ring[tick&127];return s==null||s.tick!=tick?null:s;}
        public Sample last(){owned();return last;}public long epoch(){owned();return epoch;}
        public List<Sample> samples(){owned();return Collections.unmodifiableList(Arrays.asList(ring.clone()));}
        public void clear(){owned();Arrays.fill(ring,null);last=null;epoch=0;}
        public void record(Target target,int tick){
            owned();try{var at=target.position();long life=target.life();
            if(last!=null&&last.life==life&&last.tick<tick&&tooFar(at,last.position,(long)tick-last.tick))epoch++;
            boolean live=target.live();var sample=new Sample(tick,at,target.orientation(),live&&target.crouched(),live,life,epoch);
            ring[tick&127]=last=sample;}catch(RuntimeException invalid){/* Source history is best effort and cannot break physics. */}
        }
    }
    /** Stable identity for a live owned Character, including its physical crouch/hover state. */
    public static final class PlayerTarget implements Target {
        private final PlayerLife player;
        private final History history=new History();
        public PlayerTarget(PlayerLife player){this.player=Objects.requireNonNull(player);}
        public PlayerLife player(){player.id();return player;}
        public History history(){player.id();return history;}
        public Vector position(){return player.position();}
        public Vector orientation(){return player.aim();}
        public boolean crouched(){return player.generation()>0?player.body().hitboxCrouched():(player.motion().snapshot().movement()&32)!=0;}
        public boolean live(){return player.alive()&&player.spawned();}
        public long life(){return player.generation();}
    }
    public record Diagnostic(String kind,Map<String,Double> detail){public Diagnostic{detail=Map.copyOf(detail);}}
    public static final class Context {
        private final Thread owner=Thread.currentThread();
        private final int currentTick;
        private final double viewTick,rewindMillis,allowedMillis,rttMillis;
        private final Integer snapshotTick;
        private final IdentityHashMap<Target,Hitbox> bodies=new IdentityHashMap<>();
        public Context(int currentTick,double viewTick,double rewindMillis,double allowedMillis,double rttMillis,Integer snapshotTick){this.currentTick=currentTick;this.viewTick=viewTick;this.rewindMillis=rewindMillis;this.allowedMillis=allowedMillis;this.rttMillis=rttMillis;this.snapshotTick=snapshotTick;}
        private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Shot rewind outside owner");}
        public int currentTick(){owned();return currentTick;}public double viewTick(){owned();return viewTick;}
        public double rewindMillis(){owned();return rewindMillis;}public double allowedMillis(){owned();return allowedMillis;}
        public double rttMillis(){owned();return rttMillis;}public Integer snapshotTick(){owned();return snapshotTick;}
        public Hitbox body(Target target){owned();return bodies.computeIfAbsent(target,t->rewound(t,currentTick,viewTick));}
    }
    private static boolean tooFar(Vector a,Vector b,long ticks){double dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z(),limit=5.0*ticks;return dx*dx+dy*dy+dz*dz>limit*limit;}
    private static boolean valid(Sample sample,long life,long epoch){return sample!=null&&sample.live&&sample.life==life&&sample.epoch==epoch;}
    private static Sample liveSample(Target target,int tick,long epoch){return new Sample(tick,target.position(),target.orientation(),target.crouched(),true,target.life(),epoch);}
    private static Vector interpolate(Vector a,Vector b,double fraction){return new Vector(a.x()+(b.x()-a.x())*fraction,a.y()+(b.y()-a.y())*fraction,a.z()+(b.z()-a.z())*fraction);}
    public static Hitbox rewound(Target target,int currentTick,double viewTick){
        var history=target.history();if(history==null||history.last()==null||!target.live())return target;
        long life=target.life(),epoch=history.epoch();var last=history.last();
        if(last.life!=life||tooFar(target.position(),last.position,Math.max(1,(long)currentTick-last.tick)))return target;
        int base=(int)Math.floor(viewTick);double fraction=viewTick-base;
        var first=base>=currentTick?liveSample(target,currentTick,epoch):history.sample(base);
        var second=fraction>1e-6?(base+1>=currentTick?liveSample(target,currentTick,epoch):history.sample(base+1)):null;
        boolean firstOk=valid(first,life,epoch),secondOk=valid(second,life,epoch);
        Vector position,orientation;boolean crouched;
        if(firstOk&&secondOk){
            position=interpolate(first.position,second.position,fraction);var near=fraction>=.5?second:first;
            var aim=interpolate(first.orientation,second.orientation,fraction);double norm=Math.sqrt(aim.x()*aim.x()+aim.y()*aim.y()+aim.z()*aim.z());
            orientation=norm>1e-6?new Vector(aim.x()/norm,aim.y()/norm,aim.z()/norm):near.orientation;crouched=near.crouched;
        }else if(firstOk||secondOk){var near=firstOk?first:second;position=near.position;orientation=near.orientation;crouched=near.crouched;}
        else return target;
        return new Rewound(target,position,orientation,crouched,(double)currentTick-viewTick);
    }
    /** Arrival dates are source InputTimeline.labelArrivalTick, not shot arrival time or a game ACK. */
    public static double lateShotMillis(Settings settings,int serverLoop,Integer label,double tickMillis,IntFunction<Integer> arrivalOf){
        double cap=Math.max(0,settings.lateShotMillis);if(cap<=0||label==null||arrivalOf==null)return 0;
        Long estimate=null;
        for(int offset=0;offset<=8&&estimate==null;offset++){
            long[] candidates=offset==0?new long[]{label}:new long[]{(long)label-offset,(long)label+offset};
            for(long candidate:candidates){if(candidate<Integer.MIN_VALUE||candidate>Integer.MAX_VALUE)continue;var arrived=arrivalOf.apply((int)candidate);if(arrived!=null){estimate=(long)arrived-(candidate-label);break;}}
        }
        if(estimate==null)return 0;long late=(long)serverLoop-estimate-1;return late<=0?0:Math.min(cap,late*tickMillis);
    }
    private static double oneDecimal(double value){return new java.math.BigDecimal(value).setScale(1,java.math.RoundingMode.HALF_EVEN).doubleValue();}
    private static void report(Consumer<Diagnostic> observer,Diagnostic diagnostic){try{observer.accept(diagnostic);}catch(RuntimeException failed){/* Source diagnostics are log-only. */}}
    /** Uses the connection-bound life and its actual ClientData arrival history, never the claimed shooter byte. */
    public static Context prepare(Settings settings,int serverLoop,double tickSeconds,PlayerLife shooter,
        net.battlespades.protocol.ShootPacket packet,Consumer<Diagnostic> report){
        var context=decide(settings,serverLoop,tickSeconds,shooter.bot(),shooter.flight().roundTripMillis().orElse(0),packet.loopCount(),packet.snapshotLoop(),
            shooter.inputTimeline()::labelArrivalTick,report);
        if(context!=null)shooter.lastLagRewindMillis(context.rewindMillis());return context;
    }
    /** The caller owns shot/tool/hit authorization and stores last_lag_rewind_ms only for a returned context. */
    public static Context decide(Settings settings,int serverLoop,double tickSeconds,boolean bot,double rtt,Integer shotLabel,int claimedSnapshot,
        IntFunction<Integer> arrivalOf,Consumer<Diagnostic> report){
        if(!settings.enabled||bot||!Double.isFinite(rtt)||rtt<=0)return null;
        if(tickSeconds<=0)tickSeconds=1.0/60;
        double tickMillis=tickSeconds*1000;int now=serverLoop-1;
        double allowed=Math.min(rtt+Math.max(0,settings.extraMillis),Math.max(0,settings.maximumMillis));
        double late=lateShotMillis(settings,serverLoop,shotLabel,tickMillis,arrivalOf);allowed+=late;
        double desired=rtt+Math.max(0,settings.viewDelayMillis)+late;Integer snapshot=null;
        if(claimedSnapshot>0){
            if((long)claimedSnapshot>(long)now+1)report(report,new Diagnostic("lag_comp_future_snapshot",Map.of("claimed",(double)claimedSnapshot,"now",(double)now)));
            else{
                snapshot=Math.min(claimedSnapshot,now);double age=((long)now-snapshot)*tickMillis;desired=Math.min(desired,age);
                double slack=(2L*Math.max(1,settings.broadcastInterval)+1)*tickMillis;
                if(age>allowed+slack)report(report,new Diagnostic("lag_comp_stale_snapshot",Map.of("claimed_ms",oneDecimal(age),"allowed_ms",oneDecimal(allowed),"rtt_ms",oneDecimal(rtt))));
            }
        }
        if(late>0)report(report,new Diagnostic("lag_comp_late_shot",Map.of("late_ms",oneDecimal(late),"rtt_ms",oneDecimal(rtt))));
        double rewind=Math.max(0,Math.min(desired,allowed));if(rewind<.5*tickMillis)return null;
        return new Context(now,now-rewind/tickMillis,rewind,allowed,rtt,snapshot);
    }
}
