// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.simulation;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Bounded fixed-step core from simulation_runtime.py; caller owns a dedicated execution context. */
public final class FixedStepRuntime {
    public static final double DELTA = 1.0 / 60;
    public static final int MAX_CATCH_UP_STEPS = 5;
    private static final double MAX_CATCH_UP_SECONDS = 2;
    private final ArrayBlockingQueue<Runnable> incoming;
    private final int drainBudget;
    private final Runnable step;
    private final Runnable replicate;
    private final Consumer<RuntimeException> failure;
    private final LongAdder rejected = new LongAdder();
    private final AtomicReference<Thread> owner = new AtomicReference<>();
    private long ticks;
    private double accumulator;
    public FixedStepRuntime(int capacity, int drainBudget, Runnable step, Runnable replicate,
            Consumer<RuntimeException> failure) {
        if (drainBudget <= 0) throw new IllegalArgumentException("Invalid drain budget");
        incoming = new ArrayBlockingQueue<>(capacity);
        this.drainBudget = drainBudget;
        this.step = Objects.requireNonNull(step);
        this.replicate = Objects.requireNonNull(replicate);
        this.failure = Objects.requireNonNull(failure);
    }
    /** Producers offer messages. They never execute against authoritative state. */
    public boolean offer(Runnable operation) {
        boolean accepted = incoming.offer(Objects.requireNonNull(operation));
        if (!accepted) rejected.increment();
        return accepted;
    }
    /** Advance one bounded batch; retained debt is capped at two seconds, as in the source. */
    public int advance(double elapsedSeconds) {
        assertOwner();
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) throw new IllegalArgumentException("Invalid elapsed time");
        accumulator = Math.min(MAX_CATCH_UP_SECONDS, accumulator + elapsedSeconds);
        int steps = 0;
        while (accumulator >= DELTA && steps < MAX_CATCH_UP_STEPS) {
            accumulator -= DELTA;
            for (int i = 0; i < drainBudget; i++) {
                Runnable message = incoming.poll();
                if (message == null) break;
                guarded(message);
            }
            ticks++;
            steps++;
            guarded(step);
            // The replication service owns cadence and odd-tick urgent transitions.
            guarded(replicate);
        }
        return steps;
    }
    private void guarded(Runnable operation) {
        try { operation.run(); } catch (RuntimeException exception) { failure.accept(exception); }
    }
    private void assertOwner() {
        owner.compareAndSet(null, Thread.currentThread());
        if (owner.get() != Thread.currentThread()) throw new IllegalStateException("Simulation state accessed outside owner");
    }
    public long ticks() { assertOwner(); return ticks; }
    public long rejectedMessages() { return rejected.sum(); }
    public int queueDepth() { return incoming.size(); }
}
