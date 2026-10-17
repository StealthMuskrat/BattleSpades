// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.battlespades.simulation.FixedStepRuntime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationTest {
    @Test void everyPhysicsStepInvokesReplicationPolicyIncludingOddTransitions() {
        var steps=new AtomicInteger();var snapshots=new AtomicInteger();
        var runtime=new FixedStepRuntime(16,8,steps::incrementAndGet,snapshots::incrementAndGet,e->fail(e));
        for(int i=0;i<600;i++) runtime.advance(FixedStepRuntime.DELTA);
        assertEquals(600,steps.get());assertEquals(600,snapshots.get());assertEquals(600,runtime.ticks());
    }
    @Test void debtSurvivesFiveStepBatchAndSuspensionIsCapped() {
        var runtime=new FixedStepRuntime(2,1,()->{},()->{},e->fail(e));
        assertEquals(5,runtime.advance(1));
        while(runtime.advance(0)>0) { /* bounded batches drain the retained hitch */ }
        assertTrue(runtime.ticks()>=59 && runtime.ticks()<=60);
        long before=runtime.ticks();runtime.advance(1000);
        while(runtime.advance(0)>0) { /* never replay minutes after suspend */ }
        assertTrue(runtime.ticks()-before<=120);
    }
    @Test void queueIsBoundedDrainsBeforePhysicsAndIsolatesFailures() {
        List<String> events=new ArrayList<>();List<RuntimeException> failures=new ArrayList<>();
        var runtime=new FixedStepRuntime(2,1,()->events.add("step"),()->events.add("snapshot"),failures::add);
        assertTrue(runtime.offer(()->events.add("input")));
        assertTrue(runtime.offer(()->{throw new IllegalStateException("bad message");}));
        assertFalse(runtime.offer(()->fail("overflow was executed")));
        assertTrue(events.isEmpty());runtime.advance(FixedStepRuntime.DELTA);
        assertEquals(List.of("input","step","snapshot"),events);
        runtime.advance(FixedStepRuntime.DELTA);
        assertEquals(List.of("input","step","snapshot","step","snapshot"),events);
        assertEquals(1,failures.size());assertEquals(1,runtime.rejectedMessages());
    }
    @Test void stateOwnershipRejectsOtherThread() throws InterruptedException {
        var runtime=new FixedStepRuntime(2,1,()->{},()->{},e->fail(e));runtime.advance(0);
        var failure=new AtomicReference<Throwable>();
        Thread t=Thread.ofPlatform().unstarted(()->{try{runtime.advance(0);}catch(Throwable e){failure.set(e);}});
        t.start();t.join();assertInstanceOf(IllegalStateException.class,failure.get());
    }
}
