# Java test results

Stage: concrete TDM and serial packet/mode runtime batch, 2026-10-18 UTC.

Local Java check/build passes **5,971 component + 35 integration = 6,006 tests**,
zero failures, errors or skips. The batch adds 59 component tests and two actual UDP
compositions. **131 focused original TDM/runtime/scoreboard/lifecycle/end-sequence
regressions pass**. All 68 configured generators ran after fresh original five-module
native and ENet builds; all 244 frozen fixture/catalog/codegen hashes match.
The known flight provenance observation recurred: two impact metadata fields were
restored by rerunning the actual original generator's `--impact-only` command,
then all 244 hashes were compared again. No fixture or hash was waived.

Two CRC-valid local archives are byte-identical: 1769051 bytes,
SHA-256 `6823264954c6828247cdcb029aea3225a4c480984a7a5b6298398f368cbd2f7d`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four isolated native transport and
packaged-loader nominal/impaired profiles pass. Loader probes still use FixtureScene.
The full original baseline remains unverified: prior medpack/Zombie/VIP discrepancies
are retained and unwaived. These focused passes do not supersede those observations.

The concrete TDM core, canonical accepted-life score binding, serial mode/plugin
event drain and opt-in actual ENet packet queue are implemented. Complete departure
settlement, asynchronous simulation/bootstrap, delayed end/restart and actual
resource/audio/vote/achievement/result services, all other modes and supported-client/
capacity/soak gates remain pending. See [TDM_RUNTIME.md](TDM_RUNTIME.md).

This checkpoint requires a new five-job CI run after publication.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| net.battlespades.ApplicationIntegrationTest | 32 | 0 | 0 | 0 |
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
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.JoinPolicyTest | 4 | 0 | 0 | 0 |
| net.battlespades.JoinSessionTest | 23 | 0 | 0 | 0 |
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
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldQueriesTest | 2 | 0 | 0 | 0 |
| net.battlespades.match.MatchBoundaryTest | 2 | 0 | 0 | 0 |
| net.battlespades.match.MatchFlowGoldenTest | 74 | 0 | 0 | 0 |
| net.battlespades.match.MatchStateGoldenTest | 49 | 0 | 0 | 0 |
| net.battlespades.match.ObjectiveGuardGoldenTest | 32 | 0 | 0 | 0 |
| net.battlespades.match.ScoreEvidenceGoldenTest | 72 | 0 | 0 | 0 |
| net.battlespades.match.ScoreboardGoldenTest | 48 | 0 | 0 | 0 |
| net.battlespades.match.SerialRuntimeTest | 4 | 0 | 0 | 0 |
| net.battlespades.match.TdmBindingTest | 4 | 0 | 0 | 0 |
| net.battlespades.match.TdmRuntimeGoldenTest | 49 | 0 | 0 | 0 |
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

The preceding test optimization checkpoint has 5,945 passing tests and all five
jobs in run 39145475640. Its removal of duplicate stateless cases is retained;
see TEST_OPTIMIZATION.md.

## Preceding combat economy checkpoint evidence

Local clean check/build passes **5,965 component + 33 real UDP = 5,998 Java tests**,
zero failures, errors or skips. The expanded original suite has **162 passes
and one unchanged medpack/context baseline failure**, with no errors or skips.
That failure is retained and unwaived; this is not a passing full-source baseline.
The batch adds 96 component tests, two real UDP compositions, 10,752 policy/codec/HUD
rows, 1,824 native voxel edits and 6,144 direct escape geometry evaluations. All 67
configured generators reran after fresh original five-module native and ENet builds;
all 241 recorded fixture/catalog/codegen hashes match. The frozen set includes
existing foundation files, without claiming every file is generated by those scripts.

Two CRC-valid local archives are byte-identical: 1731239 bytes,
SHA-256 `30495db15a5e695ccbff378c48e973f6e0306f0131d77e6b7b2c280d9b33e4d1`. Class major 69, packaging/licenses and smokes pass;
version/config exits 0, hosting exits 2. All four isolated native nominal/impaired
transport and packaged-loader profiles pass. No local-to-CI hash identity is claimed.

Concrete mode/canonical-row/reload/bonus/exemption/event and end/result/persistence
bindings remain required. RoundClock implements fixed-budget arbitration; complete
serial asynchronous dispatch and delayed mode end/restart services remain pending.
Original baseline and supported-client/capacity/soak gates remain pending; earlier
unchanged Zombie/VIP and medpack-context discrepancies are unwaived. Hosting exits 2. Native packaged-loader
probes retain FixtureScene. See MATCH_FLOW.md.

The initial implementation `a831f1fe8226cde5dae8c429727eb4f3202409a3` passed the
build, native, Ubuntu and Windows jobs in run 39143288035. macOS component tests
and artifact smokes passed, but the existing UDP replication/respawn test timed
out after ACK release. Its test synchronization now waits for reliable receipts
without expiry, then broadcasts at cadence until the released row is received.
The row-hold assertions and 4s/15s timeouts remain unchanged. This failure and its
logs are retained; the corrected checkpoint passes all five jobs in run 39144143660.
