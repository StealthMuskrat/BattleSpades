// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.player;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.battlespades.protocol.*;
import net.battlespades.runtime.OwnerLoop;

/** Commit every live palette choice; bounded trailing owner timer relays only the newest color, excluding its actor. */
public final class PaletteRelay {
    private static final class Row {Double last;CompletableFuture<Void> timer;}
    private final Thread owner=Thread.currentThread();private final LifeRuntime lives;private final BooleanSupplier enabled;private final DoubleSupplier clock;private final OwnerLoop loop;private final BiConsumer<PlayerLife,GamePacket> broadcastExcept;private final IdentityHashMap<PlayerLife,Row> rows=new IdentityHashMap<>();
    public PaletteRelay(LifeRuntime lives,BooleanSupplier enabled,DoubleSupplier clock,OwnerLoop loop,BiConsumer<PlayerLife,GamePacket> broadcastExcept){this.lives=Objects.requireNonNull(lives);this.enabled=Objects.requireNonNull(enabled);this.clock=Objects.requireNonNull(clock);this.loop=Objects.requireNonNull(loop);this.broadcastExcept=Objects.requireNonNull(broadcastExcept);}
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Palette outside owner");}
    private void relay(PlayerLife p,Row row){row.last=clock.getAsDouble();broadcastExcept.accept(p,new SimplePackets.SetColor(p.id(),p.color()));}
    public void set(PlayerLife p,int color){
        owned();if(!lives.current(p)||!p.alive()||!p.spawned()||!enabled.getAsBoolean())return;lives.palette(p,color);var row=rows.computeIfAbsent(p,key->new Row());double now=clock.getAsDouble(),elapsed=row.last==null?Double.POSITIVE_INFINITY:now-row.last;
        if(elapsed>=.1){if(row.timer!=null){row.timer.cancel(false);row.timer=null;}relay(p,row);return;}if(row.timer!=null)return;
        var timer=loop.sleep(Math.max(0,.1-elapsed)).toCompletableFuture();row.timer=timer;timer.whenComplete((unused,error)->{if(error==null)loop.execute(()->{if(rows.get(p)!=row||row.timer!=timer)return;row.timer=null;if(lives.current(p)&&p.alive()&&p.spawned())relay(p,row);});});
    }
    public void forget(PlayerLife p){owned();var row=rows.remove(p);if(row!=null&&row.timer!=null)row.timer.cancel(false);}
    public void reset(){owned();for(var p:List.copyOf(rows.keySet()))forget(p);}
}
