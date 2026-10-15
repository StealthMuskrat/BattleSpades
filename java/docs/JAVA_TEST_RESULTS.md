# Java test results

Stage: player names and common pre-life team/class admission, 2026-10-15 UTC.
Current-tree independent CI is pending; earlier evidence is in [CI_STATUS.md](CI_STATUS.md).

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
| net.battlespades.JoinSessionTest | 17 | 0 | 0 | 0 |
| net.battlespades.LoadingPacketTest | 12 | 0 | 0 | 0 |
| net.battlespades.MapSyncTest | 15 | 0 | 0 | 0 |
| net.battlespades.MutableWorldTest | 5 | 0 | 0 | 0 |
| net.battlespades.PlayerPhysicsTest | 120 | 0 | 0 | 0 |
| net.battlespades.PlayerRosterTest | 2 | 0 | 0 | 0 |
| net.battlespades.RangeCoderTest | 31 | 0 | 0 | 0 |
| net.battlespades.ScenePolicyTest | 230 | 0 | 0 | 0 |
| net.battlespades.SessionProtocolTest | 18 | 0 | 0 | 0 |
| net.battlespades.SimulationTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldQueriesTest | 2 | 0 | 0 | 0 |
| net.battlespades.player.AdmissionPolicyTest | 571 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 4 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **1,267 passed; zero failures, errors or skips.**

New source comparisons: 1,112,064 Unicode scalar values; 18,568 name vectors;
6,192 team decisions; 14,256 class admission vectors. The four-client UDP test
checks names, balancing, neutral/spectator teams, forged forced_team and class
fallback using each connection's StateData-reserved identity. Earlier packet,
rule/scene and exact movement/query comparisons remain covered.

The focused original name/mode-lifecycle/class subset passes 60 tests.
See [PLAYER_ADMISSION.md](PLAYER_ADMISSION.md) and [CONFIGURED_SCENES.md](CONFIGURED_SCENES.md).
Authenticated identity, actual mode hooks, spawning/lifecycle, live input timing,
combat and playable hosting remain pending.

The packaged JAR passed fresh native ENet transport and configured-loader probes,
including loss/reorder/duplicates, password handling, full/delta synchronization,
roster/cell catch-up and scene reloads. Two valid final archives match byte-for-byte:
1,150,799 bytes; SHA-256
`841212008c429f3da0832a6daf9e76ca6d2e0d739d154acdd97880e1b19b1b17`.
These probes are not supported-original-game-client tests.
