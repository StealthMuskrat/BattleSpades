// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.runtime;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerLoopTest {
    @Test void workerAndOwnerSubmissionsAreDeferredAndEachPumpHasAFixedBudget()throws Exception{
        var calls=new ArrayList<Integer>();try(var loop=new OwnerLoop(0,16,2,error->fail(error))){
            loop.execute(()->{calls.add(1);loop.execute(()->calls.add(4));});var worker=new Thread(()->loop.execute(()->calls.add(2)));worker.start();worker.join();loop.execute(()->calls.add(3));assertTrue(calls.isEmpty());assertEquals(2,loop.pump(0));assertEquals(List.of(1,2),calls);assertEquals(2,loop.pump(0));assertEquals(List.of(1,2,3,4),calls);
        }
    }
    @Test void timersAreMonotonicOrderedCancellableAndZeroWaitYields(){
        var calls=new ArrayList<Integer>();try(var loop=new OwnerLoop(100,16,8,error->fail(error))){
            loop.sleep(1).thenRun(()->calls.add(1));var cancelled=loop.sleep(1).toCompletableFuture();cancelled.thenRun(()->calls.add(99));loop.sleep(1).thenRun(()->calls.add(2));assertTrue(cancelled.cancel(false));assertEquals(2,loop.pending());loop.pump(1_000_000_099);assertTrue(calls.isEmpty());loop.pump(1_000_000_100);assertEquals(List.of(1,2),calls);
            var zero=loop.sleep(0).toCompletableFuture();assertFalse(zero.isDone());loop.pump(1_000_000_100);assertTrue(zero.isDone());assertThrows(IllegalArgumentException.class,()->loop.pump(0));assertThrows(IllegalArgumentException.class,()->loop.sleep(Double.NaN));assertThrows(IllegalArgumentException.class,()->loop.sleep(Double.POSITIVE_INFINITY));assertThrows(IllegalArgumentException.class,()->loop.sleep(-1));assertThrows(IllegalArgumentException.class,()->loop.sleep(Double.MAX_VALUE));
        }
    }
    @Test void capacityFaultIsolationFatalPropagationAndShutdownKeepTheirContracts(){
        var errors=new ArrayList<Throwable>();var loop=new OwnerLoop(0,2,8,errors::add);var timer=loop.sleep(1).toCompletableFuture();loop.execute(()->{throw new IllegalStateException("ordinary");});assertThrows(RejectedExecutionException.class,()->loop.execute(()->{}));timer.cancel(false);loop.execute(()->{throw new AssertionError("fatal");});assertThrows(AssertionError.class,()->loop.pump(0));assertEquals(1,errors.size());
        var waiting=loop.sleep(1).toCompletableFuture();var ran=new boolean[1];loop.execute(()->ran[0]=true);loop.close();assertTrue(waiting.isCancelled());assertThrows(RejectedExecutionException.class,()->loop.execute(()->{}));assertEquals(1,loop.pump(0));assertTrue(ran[0]);loop.close();
    }
    @Test void ownerOnlyTimersAndPumpsRejectWorkerAccessAndReentrancy()throws Exception{
        try(var loop=new OwnerLoop(0,8,8,error->fail(error));var worker=Executors.newSingleThreadExecutor()){
            assertInstanceOf(IllegalStateException.class,worker.submit(()->{try{loop.sleep(0);return null;}catch(RuntimeException e){return e;}}).get());assertInstanceOf(IllegalStateException.class,worker.submit(()->{try{loop.pump(0);return null;}catch(RuntimeException e){return e;}}).get());loop.execute(()->assertThrows(IllegalStateException.class,()->loop.pump(0)));loop.pump(0);
        }
    }
}
