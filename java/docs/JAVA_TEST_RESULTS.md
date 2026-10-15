# Java test results

Stage: accepted damage/healing, history and reliable HP/death ordering, 2026-10-15 UTC.
Current-tree CI pending; previous lifecycle evidence is in [CI_STATUS.md](CI_STATUS.md).

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
| net.battlespades.player.AdmissionPolicyTest | 571 | 0 | 0 | 0 |
| net.battlespades.player.HealthGoldenTest | 1368 | 0 | 0 | 0 |
| net.battlespades.player.HealthRuntimeTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.LifeGoldenTest | 1082 | 0 | 0 | 0 |
| net.battlespades.player.LifeRuntimeTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 7 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **4,400 Java tests passed; zero failures, errors or skips.**

Original-method comparisons: 1152 damage cases, 120 healing cases, 80 nested
attribution cases and 832 stateful operations. Expected values include complete
owned health/history state, HP/death bytes and original observer-boundary evidence.
See [PLAYER_HEALTH.md](PLAYER_HEALTH.md) for exact scope and controlled inputs.

The additional real-UDP test exercises production HP/loading/death/respawn code.
Backpressure regressions verify that owner HP cannot be overtaken by reliable
death traffic and that bounded queue overflow retires the session. Packaged
native probes retain fixture player lives.

Focused original kill-feed/combat-score/conduct/explosion suites: **46 passed**.
Additional original rules audit: **45 passed, one existing VIP round-limit failure**,
reproduced in isolation with the original source unchanged. This is retained
reference evidence, not a passing full Python baseline.

Hit/action/friendly-fire authorization, detached projectiles, complete awards and
assist payouts, input timelines, concrete modes and supported-client/capacity
verification remain pending. Ordinary hosting still exits 2.
