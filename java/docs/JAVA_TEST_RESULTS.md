# Java test results

Stage: firing/reload timing, seeded pellets and read-only target rewind, 2026-10-17 UTC.
Published implementation: `0f6448231be6e93f3ce58d4682d9cd9963ac95ef`; tested tree: `9acb23de8fa14ae82f90188bf67f741bae3f563b`.
All five jobs passed in [run 39073877248](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39073877248), including original-source fixture
regeneration, archive reproducibility and the uploaded JAR on three platforms.
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
| net.battlespades.LagGoldenTest | 26 | 0 | 0 | 0 |
| net.battlespades.LagRuntimeTest | 3 | 0 | 0 | 0 |
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
| net.battlespades.player.LagConnectedGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.LifeGoldenTest | 1082 | 0 | 0 | 0 |
| net.battlespades.player.LifeRuntimeTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.OwnerGoldenTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.PelletGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.player.PlayerFireGoldenTest | 33 | 0 | 0 | 0 |
| net.battlespades.player.PlayerFlightTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.PlayerSnapshotGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 16 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 13 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **4,687 Java tests passed: 4,671 unit/component and 16 real UDP; zero failures, errors or skips.**

New source comparisons cover **67,151 operations**: 16,477 target-history,
7,000 rewind-decision, 1,296 connected-history, 24,882 firing/reload, 16,728
seeded-pellet and 768 ShootPacket vectors. Original policy methods remain
unchanged. A fresh native original world/packet rebuild reproduces all new
fixture/catalog bytes and prior infantry/flight/replication fixtures; all 112
fixture/catalog hashes remain identical. **152 focused original tests pass**,
including the reference impaired-link lab. See [COMBAT_TIMING.md](COMBAT_TIMING.md).

Two additional real UDP tests prepare a read-only view for an arrival-dated
reliable shot and exercise bound reload identity/tool, ignored completion claims,
actual RTT/variance forwarding and reliable completion from owned housekeeping.
The shot test uses an explicit test handler and does not resolve a hit. Native
packaged probes retain FixtureScene. Source lab success describes the reference
server, not complete Java combat or supported-client gameplay.

Origin/aim/tool/hit authorization, special burst/spin-up and actual hit resolution
remain pending, along with other required domains, concrete modes and client/
capacity/soak gates. The full Python baseline retains its documented discrepancy
and incomplete runs. Ordinary hosting still exits 2.
