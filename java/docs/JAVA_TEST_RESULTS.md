# Java test results

Stage: authoritative no-flight infantry simulation, 2026-10-16 UTC.
Published implementation: `1e4c34fced6e6fc96185fd72b212d62b1e3eab70`; tested tree: `4a54308beb4e425dbfa9ea0ab2ba0176b619ccf6`.
All five migration CI jobs passed in [run 38834640118](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38834640118), including exact
source-fixture regeneration, repeat archive comparison and one uploaded JAR on
Linux, Windows and macOS. See [CI_STATUS.md](CI_STATUS.md).

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
| net.battlespades.player.InfantryGoldenTest | 33 | 0 | 0 | 0 |
| net.battlespades.player.InfantrySimulationTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineGoldenTest | 24 | 0 | 0 | 0 |
| net.battlespades.player.InputTimelineTest | 3 | 0 | 0 | 0 |
| net.battlespades.player.LifeGoldenTest | 1082 | 0 | 0 | 0 |
| net.battlespades.player.LifeRuntimeTest | 5 | 0 | 0 | 0 |
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 9 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **4,499 Java tests passed: 4,487 unit/component and 12 real UDP; zero failures, errors or skips.**

The new full-path oracle executes unchanged original SimulationRuntime/Player and
the real native mover for 16,683 operations in 33 traces. Exact comparisons include
raw body and clipped façade bits, input/pacing/eye history, observer boundaries and
HP/death bytes. The fixture and provenance reproduce exactly. Focused unchanged
original input-loss/movement-anticheat modules: **57 passed**.

The added UDP test uses production session/life/timeline/mover/health code, advances
the owned fixed-step scheduler, verifies real/refilled movement and starvation
freeze, then observes a genuine lethal landing's zero HP before KillAction.
Required mode/reload/lag/effect hooks are explicit test ports; see
[INFANTRY_SIMULATION.md](INFANTRY_SIMULATION.md) for exact scope.

Earlier Java regression suites remain passing. The full Python baseline retains
its documented failures/incomplete runs, including the prior VIP discrepancy.
Flight-equipped bodies and Crumbling Run momentum require separate bindings;
complete modes, owner WorldUpdate, hit authorization, supported-client rounds,
capacity and soak remain pending. Ordinary hosting still exits 2.
