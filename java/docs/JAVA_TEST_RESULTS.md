# Java test results

Stage: ClientData chronology and action cadence, 2026-10-16 UTC.
Current implementation publication and all five CI jobs are pending. The preceding
accepted-health checkpoint passed all five jobs in run 38826339114.
See [CI_STATUS.md](CI_STATUS.md) for exact implementation/CI evidence.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| net.battlespades.ComplexPacketTest | 3 | 0 | 0 | 0 |
| net.battlespades.CompressionOwnershipTest | 2 | 0 | 0 | 0 |
| net.battlespades.CompressionTest | 16 | 0 | 0 | 0 |
| net.battlespades.ConfigTest | 4 | 0 | 0 | 0 |
| net.battlespades.EnetTest | 7 | 0 | 0 | 0 |
| net.battlespades.GoldenPacketTest | 178 | 0 | 0 | 0 |
| net.battlespades.HandshakeTest | 2 | 0 | 0 | 0 |
| net.battlespades.JoinPolicyTest | 4 | 0 | 0 | 0 |
| net.battlespades.JoinSessionTest | 19 | 0 | 0 | 0 |
| net.battlespades.LoadingPacketTest | 12 | 0 | 0 | 0 |
| net.battlespades.MapMetadataTest | 631 | 0 | 0 | 0 |
| net.battlespades.MapSyncTest | 15 | 0 | 0 | 0 |
| net.battlespades.MutableWorldTest | 5 | 0 | 0 | 0 |
| net.battlespades.PlayerPhysicsTest | 120 | 0 | 0 | 0 |
| net.battlespades.PlayerRosterTest | 4 | 0 | 0 | 0 |
| net.battlespades.RangeCoderTest | 31 | 0 | 0 | 0 |
| net.battlespades.ScenePolicyTest | 230 | 0 | 0 | 0 |
| net.battlespades.SessionProtocolTest | 18 | 0 | 0 | 0 |
| net.battlespades.SimulationTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldQueriesTest | 2 | 0 | 0 | 0 |
| net.battlespades.player.ActionClockTest | 34 | 0 | 0 | 0 |
| net.battlespades.player.AdmissionPolicyTest | 571 | 0 | 0 | 0 |
| net.battlespades.player.HealthGoldenTest | 1368 | 0 | 0 | 0 |
| net.battlespades.player.HealthRuntimeTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineGoldenTest | 24 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.LifeGoldenTest | 1082 | 0 | 0 | 0 |
| net.battlespades.player.LifeRuntimeTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 8 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **4,462 Java tests passed: 4,451 unit/component and 11 real UDP; zero failures, errors or skips.**

Original input comparisons cover 14,234 operations in 24 traces and 26,234
action-clock operations in 32 traces. Both new fixtures/provenance reproduce
exactly from unchanged original methods. Focused original input-loss/action-clock
regressions: **31 passed**. Source production and test files remain unchanged.

The new UDP test exercises production session/life/receive-history code with
explicit test policies; it verifies freshness, phase metadata, no movement on raw
arrival, and respawn state. No complete movement/equipment/replication loop is
claimed. Cadence success does not authorize a shot or damage. See [PLAYER_INPUT.md](PLAYER_INPUT.md).

Retained earlier health/lifecycle/movement tests pass in this full Java run. The
full Python baseline remains incomplete, including the existing VIP discrepancy
recorded in [PYTHON_BASELINE.md](PYTHON_BASELINE.md). Packaged native probes still
use FixtureScene. Complete modes, supported-client rounds, capacity and soak
remain pending; ordinary hosting still exits 2.
