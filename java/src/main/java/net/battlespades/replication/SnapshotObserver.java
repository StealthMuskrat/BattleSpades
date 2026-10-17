// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.replication;

/** Per-connection observer stream state; retained across Character respawns. */
public final class SnapshotObserver {
    private final Thread owner=Thread.currentThread();
    private Integer loop;
    private boolean sequenced;
    private Long barrier;
    private void owned(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Observer state accessed outside owner");}
    public Integer loop(){owned();return loop;}
    public boolean sequenced(){owned();return sequenced;}
    public Long orderedBarrier(){owned();return barrier;}
    public void loop(int value){owned();loop=value;}
    public void sent(boolean unsequenced,long reliableIndex){owned();sequenced=!unsequenced;if(sequenced)barrier=reliableIndex;}
}
