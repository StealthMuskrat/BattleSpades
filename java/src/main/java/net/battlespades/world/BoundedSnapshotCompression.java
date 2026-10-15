// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.world;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class BoundedSnapshotCompression implements SnapshotCompression {
    private final Thread owner=Thread.currentThread();
    private final ThreadPoolExecutor pool;
    private final Set<Job> jobs=new HashSet<>();
    private final int capacity;
    private boolean closed;
    public BoundedSnapshotCompression(int workers,int queueCapacity) {
        if(workers<1 || queueCapacity<1) throw new IllegalArgumentException("Compression pool bounds");
        capacity=Math.addExact(workers,queueCapacity);
        pool=new ThreadPoolExecutor(workers,workers,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon(true).name("map-compression-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    }
    private void owned() {if(Thread.currentThread()!=owner) throw new IllegalStateException("Compression handle accessed outside owner");}
    public int outstanding() {owned();return jobs.size();}
    @Override public boolean available() { owned();return !closed && jobs.size()<capacity && (pool.getActiveCount()<pool.getCorePoolSize() || pool.getQueue().remainingCapacity()>0); }
    @Override public Optional<Task> submit(MapSyncSnapshot snapshot) {
        owned();if(closed) throw new IllegalStateException("Compression pool closed");if(jobs.size()>=capacity) return Optional.empty();
        var job=new Job(snapshot);
        try {pool.execute(job.future);jobs.add(job);return Optional.of(job);}
        catch(RejectedExecutionException full) {return Optional.empty();}
    }
    private final class Job implements Task {
        private final FutureTask<List<byte[]>> future;
        private boolean released;
        Job(MapSyncSnapshot snapshot) {future=new FutureTask<>(snapshot::buildChunks);}
        @Override public Optional<List<byte[]>> poll() {
            owned();if(released) throw new IllegalStateException("Compression result already released");if(!future.isDone()) return Optional.empty();
            try {return Optional.of(future.get());}
            catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted collecting map",interrupted);}
            catch(ExecutionException failed) {throw new IllegalStateException("Map compression failed",failed.getCause());}
            finally {released=true;jobs.remove(this);}
        }
        @Override public void close() {owned();if(!released) {released=true;future.cancel(true);pool.remove(future);jobs.remove(this);}}
    }
    @Override public void close() {owned();if(closed) return;closed=true;for(var job:List.copyOf(jobs)) job.close();pool.shutdownNow();}
}
