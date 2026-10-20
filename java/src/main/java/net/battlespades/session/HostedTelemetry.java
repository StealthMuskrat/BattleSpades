// SPDX-License-Identifier: AGPL-3.0-or-later
package net.battlespades.session;
import net.battlespades.service.RuntimeMetrics;
import static net.battlespades.service.RuntimeMetrics.Counter.*;
/** Samples cumulative owner counters without clearing their source or erasing totals on rollover. */
final class HostedTelemetry {
    private StandardMatchDomain previous;private long packets,events,committed,rejected,expired,cells,sends,dropped,failed;
    void sample(ApplicationHost host,StandardMatchDomain match,RuntimeMetrics metrics){
        long nextPackets=host.droppedPackets();metrics.add(dropped_ingame_packets,nextPackets-packets);packets=nextPackets;
        if(previous!=match){previous=match;events=committed=rejected=expired=cells=sends=dropped=failed=0;}
        long nextEvents=match.events().dropped();metrics.add(dropped_mode_events,nextEvents-events);events=nextEvents;
        var mutations=match.construction().mutations().metrics();metrics.add(committed_world_mutations,mutations.committed()-committed);committed=mutations.committed();metrics.add(rejected_world_mutations,mutations.rejected()-rejected);rejected=mutations.rejected();metrics.add(expired_world_mutations,mutations.expired()-expired);expired=mutations.expired();metrics.peak(world_mutation_queue_peak,mutations.peak());
        var repair=match.runtime().repair().metrics();metrics.add(terrain_repair_cells,repair.cells()-cells);cells=repair.cells();metrics.add(terrain_repair_sends,repair.sends()-sends);sends=repair.sends();metrics.add(dropped_terrain_repairs,repair.dropped()-dropped);dropped=repair.dropped();metrics.add(failed_terrain_repair_sends,repair.failedSends()-failed);failed=repair.failedSends();metrics.peak(terrain_repair_queue_peak,repair.queuePeak());
    }
}
