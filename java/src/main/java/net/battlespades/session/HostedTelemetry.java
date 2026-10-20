// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import net.battlespades.service.RuntimeMetrics;
import static net.battlespades.service.RuntimeMetrics.Counter.*;
/** Samples cumulative owner counters without clearing their source or erasing totals on rollover. */
final class HostedTelemetry {
    private long packets;
    private static final class Counts{long events,committed,rejected,expired,cells,sends,dropped,failed;}
    private final java.util.IdentityHashMap<StandardMatchDomain,Counts> retained=new java.util.IdentityHashMap<>();
    void retire(ApplicationHost host,StandardMatchDomain match,RuntimeMetrics metrics){sample(host,match,metrics);retained.remove(match);}
    void sample(ApplicationHost host,StandardMatchDomain match,RuntimeMetrics metrics){
        long nextPackets=host.droppedPackets();metrics.add(dropped_ingame_packets,nextPackets-packets);packets=nextPackets;
        var c=retained.computeIfAbsent(match,_ ->new Counts());
        long nextEvents=match.events().dropped();metrics.add(dropped_mode_events,nextEvents-c.events);c.events=nextEvents;
        var mutations=match.construction().mutations().metrics();metrics.add(committed_world_mutations,mutations.committed()-c.committed);c.committed=mutations.committed();metrics.add(rejected_world_mutations,mutations.rejected()-c.rejected);c.rejected=mutations.rejected();metrics.add(expired_world_mutations,mutations.expired()-c.expired);c.expired=mutations.expired();metrics.peak(world_mutation_queue_peak,mutations.peak());
        var repair=match.runtime().repair().metrics();metrics.add(terrain_repair_cells,repair.cells()-c.cells);c.cells=repair.cells();metrics.add(terrain_repair_sends,repair.sends()-c.sends);c.sends=repair.sends();metrics.add(dropped_terrain_repairs,repair.dropped()-c.dropped);c.dropped=repair.dropped();metrics.add(failed_terrain_repair_sends,repair.failedSends()-c.failed);c.failed=repair.failedSends();metrics.peak(terrain_repair_queue_peak,repair.queuePeak());
    }
}
