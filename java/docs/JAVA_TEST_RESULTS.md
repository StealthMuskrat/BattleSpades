# Java test results

Stage: registered objective drop requests, 2026-10-18 UTC.
Implementation: `f84595b3b762d54b73107a9c17b4c84d927c2a5f`; tested tree: `3ff2290a794a17c0c32d00c7b72e91c47c4ac61c`.
All five jobs passed in [run 39091489230](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39091489230), including source regeneration,
archive reproducibility and one uploaded JAR on three platforms.
The initial Windows attempt stopped before tests on a Maven Central HTTP 403;
only that job was retried.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| net.battlespades.BlockDamageGoldenTest | 21 | 0 | 0 | 0 |
| net.battlespades.CollapseGoldenTest | 16 | 0 | 0 | 0 |
| net.battlespades.CombatGeometryGoldenTest | 49 | 0 | 0 | 0 |
| net.battlespades.CombatRayGoldenTest | 1 | 0 | 0 | 0 |
| net.battlespades.ComplexPacketTest | 3 | 0 | 0 | 0 |
| net.battlespades.CompressionOwnershipTest | 2 | 0 | 0 | 0 |
| net.battlespades.CompressionTest | 16 | 0 | 0 | 0 |
| net.battlespades.ConfigTest | 4 | 0 | 0 | 0 |
| net.battlespades.DirectDamageGoldenTest | 65 | 0 | 0 | 0 |
| net.battlespades.EnetTest | 7 | 0 | 0 | 0 |
| net.battlespades.EntityIdSetGoldenTest | 33 | 0 | 0 | 0 |
| net.battlespades.EntityRegistryGoldenTest | 53 | 0 | 0 | 0 |
| net.battlespades.ExplosionGeometryGoldenTest | 66 | 0 | 0 | 0 |
| net.battlespades.GoldenPacketTest | 178 | 0 | 0 | 0 |
| net.battlespades.HandshakeTest | 2 | 0 | 0 | 0 |
| net.battlespades.HitEffectPacketsGoldenTest | 12 | 0 | 0 | 0 |
| net.battlespades.HitTraceGoldenTest | 20 | 0 | 0 | 0 |
| net.battlespades.JoinPolicyTest | 4 | 0 | 0 | 0 |
| net.battlespades.JoinSessionTest | 21 | 0 | 0 | 0 |
| net.battlespades.LagGoldenTest | 26 | 0 | 0 | 0 |
| net.battlespades.LagRuntimeTest | 3 | 0 | 0 | 0 |
| net.battlespades.LoadingPacketTest | 12 | 0 | 0 | 0 |
| net.battlespades.MapMarkersTest | 76 | 0 | 0 | 0 |
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
| net.battlespades.ShieldGoldenTest | 14 | 0 | 0 | 0 |
| net.battlespades.ShotValidationGoldenTest | 16 | 0 | 0 | 0 |
| net.battlespades.SimulationTest | 4 | 0 | 0 | 0 |
| net.battlespades.SnapshotHoldsGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.TerrainHealthDeliveryTest | 3 | 0 | 0 | 0 |
| net.battlespades.TerrainHealthGoldenTest | 36 | 0 | 0 | 0 |
| net.battlespades.TerrainRepairGoldenTest | 32 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldQueriesTest | 2 | 0 | 0 | 0 |
| net.battlespades.player.ActionClockTest | 34 | 0 | 0 | 0 |
| net.battlespades.player.AdmissionPolicyTest | 571 | 0 | 0 | 0 |
| net.battlespades.player.BlastCommitGoldenTest | 37 | 0 | 0 | 0 |
| net.battlespades.player.BlastTerrainGoldenTest | 24 | 0 | 0 | 0 |
| net.battlespades.player.CareRadarGoldenTest | 32 | 0 | 0 | 0 |
| net.battlespades.player.CombatCommitGoldenTest | 16 | 0 | 0 | 0 |
| net.battlespades.player.CombatRuntimeTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.CrateGoldenTest | 64 | 0 | 0 | 0 |
| net.battlespades.player.DeployableGoldenTest | 32 | 0 | 0 | 0 |
| net.battlespades.player.ExplosionTimelineGoldenTest | 12 | 0 | 0 | 0 |
| net.battlespades.player.FiringPatternsGoldenTest | 20 | 0 | 0 | 0 |
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
| net.battlespades.player.MapResourceGoldenTest | 48 | 0 | 0 | 0 |
| net.battlespades.player.MeleeHitEffectsGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.player.ObjectiveCarryGoldenTest | 80 | 0 | 0 | 0 |
| net.battlespades.player.ObjectiveDropGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.player.OwnerGoldenTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.PelletGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.player.PlayerFireGoldenTest | 33 | 0 | 0 | 0 |
| net.battlespades.player.PlayerFlightTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.PlayerHitEffectsGoldenTest | 17 | 0 | 0 | 0 |
| net.battlespades.player.PlayerSnapshotGoldenTest | 4 | 0 | 0 | 0 |
| net.battlespades.player.ShotTallyGoldenTest | 16 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.player.TerrainCommitGoldenTest | 16 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 16 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 24 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **5,662 Java tests passed: 5,635 component and 27 real UDP; no failures,
errors or skips.** New comparisons cover 2,048 actual original registered-handler
requests, including 448 suspended callbacks and 176 hook/send faults. All 62
configured generators reproduce outputs after a fresh original native build;
all 223 recorded fixture/catalog/codegen hashes remain unchanged.
**6 focused original tests pass.** The complete original baseline remains
incomplete/failing; earlier Zombie cue-lead and VIP discrepancies are not waived.

The new three-client UDP test routes decoded DropPickup through LifeRuntime and
ObjectiveDropRequests, checking bound authority, mode consumption, registry-only
fallback placement and suspended death with a newer server loop. Mode, owner
executor and outer completion ports remain explicit. Concrete modes, complete
outer async packet draining, dropped-object simulation, full snapshot/mode/gameplay
binding and supported-client/capacity/soak gates remain pending. Hosting exits 2;
packaged native-loader probes retain FixtureScene. See OBJECTIVE_DROP_REQUESTS.md.
