# Java test results

Stage: medpack healing and radar lifecycle, 2026-10-18 UTC.
Implementation: `18f5d79818cefad2f96f5ee1a2f222862cbf989e`; tested tree: `db584d7612c7986e694f16becd1b057cbfad58ef`.
All five jobs passed in [run 39084849726](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39084849726), including source regeneration,
archive reproducibility and one uploaded JAR on three platforms.

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
| net.battlespades.player.MeleeHitEffectsGoldenTest | 17 | 0 | 0 | 0 |
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
| net.battlespades.ApplicationIntegrationTest | 20 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **5,373 Java tests passed: 5,350 component and 23 real UDP; no failures,
errors or skips.** New evidence covers 1,024 unchanged-source lifecycle/healing
operations and 32 actual heal reliable-IO faults. All 56 configured generators
reproduce their outputs after a fresh original native build; all 210 recorded
fixture/catalog hashes remain unchanged. **109 focused original tests pass.**
These focused tests do not establish the full Python baseline, which retains its
documented incomplete runs and VIP discrepancy.

The new three-client UDP test checks shared medpack cooldowns, owner-only healing,
remaining radar fuse replay and owned-shot owner/team teardown. Authored placement,
Hardcore healing policy and scene reveals are explicit test composition. Physical
pickups, complete placement/mode/services/gameplay binding and supported-client/
capacity/soak gates remain pending. Hosting exits 2; packaged native-loader probes
continue to use FixtureScene. See [CARE_RADAR.md](CARE_RADAR.md).
