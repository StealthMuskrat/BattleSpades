# Java test results

Stage: owned WorldUpdate replication, 2026-10-17 UTC.
Published implementation: `00d96933315fc201e44234912e6e95c6ed17609d`; tested tree: `276cf8712f4801de0171f8a8b37380a78c4f04e8`.
All five jobs passed in [run 39071633850](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39071633850), including exact fixture regeneration,
archive reproducibility and one uploaded JAR tested on three platforms.
See [CI_STATUS.md](CI_STATUS.md).

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
| net.battlespades.JoinSessionTest | 21 | 0 | 0 | 0 |
| net.battlespades.LoadingPacketTest | 12 | 0 | 0 | 0 |
| net.battlespades.MapMetadataTest | 631 | 0 | 0 | 0 |
| net.battlespades.MapSyncTest | 15 | 0 | 0 | 0 |
| net.battlespades.MutableWorldTest | 5 | 0 | 0 | 0 |
| net.battlespades.PlayerPhysicsTest | 120 | 0 | 0 | 0 |
| net.battlespades.PlayerRosterTest | 4 | 0 | 0 | 0 |
| net.battlespades.RangeCoderTest | 31 | 0 | 0 | 0 |
| net.battlespades.ReplicationGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.ReplicationWireGoldenTest | 1 | 0 | 0 | 0 |
| net.battlespades.ScenePolicyTest | 230 | 0 | 0 | 0 |
| net.battlespades.SessionProtocolTest | 18 | 0 | 0 | 0 |
| net.battlespades.SimulationTest | 4 | 0 | 0 | 0 |
| net.battlespades.SnapshotHoldsGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldQueriesTest | 2 | 0 | 0 | 0 |
| net.battlespades.player.ActionClockTest | 34 | 0 | 0 | 0 |
| net.battlespades.player.AdmissionPolicyTest | 571 | 0 | 0 | 0 |
| net.battlespades.player.FlightGoldenTest | 62 | 0 | 0 | 0 |
| net.battlespades.player.HealthGoldenTest | 1368 | 0 | 0 | 0 |
| net.battlespades.player.HealthRuntimeTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.InfantryGoldenTest | 33 | 0 | 0 | 0 |
| net.battlespades.player.InfantrySimulationTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineGoldenTest | 24 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.LifeGoldenTest | 1082 | 0 | 0 | 0 |
| net.battlespades.player.LifeRuntimeTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.OwnerGoldenTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.PlayerFlightTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.PlayerSnapshotGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 16 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 11 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **4,602 Java tests passed: 4,588 unit/component and 14 real UDP; zero failures, errors or skips.**

New source comparisons cover **33,987 operations**: 18,448 replication-service,
2,235 wire-transform, 1,132 live Player snapshot, 3,714 owner-history and 8,458
Connection lifecycle-hold operations. Original policy methods remain unchanged;
the hold generator extracts unchanged AST methods and constants. A fresh original
native world/packet rebuild reproduces all new fixture/provenance bytes and the
preceding full infantry/flight fixtures. **154 focused original replication tests
pass.** See [WORLD_REPLICATION.md](WORLD_REPLICATION.md).

The new composed two-client UDP test checks owner history/tool sentinel versus
observer tool/unsequenced delivery, delays actual ENet ACKs across respawn, and
verifies observer rows remain withheld until the reliable CreatePlayer releases.
Fragment, disconnect, queue-pressure, ordering and rejection tests cover the new
receipt/hold boundary. Native packaged probes still use FixtureScene.

The full Python baseline retains documented failures/incomplete runs, including
the existing VIP discrepancy. Complete combat, presentation/effect/entity/corpse/
bot domains, concrete modes and supported-client/capacity/soak gates remain pending.
Ordinary hosting exits 2.
