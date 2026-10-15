# Java test results

Stage: layered map metadata, mode/UGC layouts and map assembly, 2026-10-15 UTC.
Implementation: `9b822d80e40fc390eea3fb471822a1fec0a878cb`. All five independent CI jobs pass: [run38790040711](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38790040711). Details: [CI_STATUS.md](CI_STATUS.md).

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
| net.battlespades.MapMetadataTest | 631 | 0 | 0 | 0 |
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
| net.battlespades.player.SpawnPolicyTest | 36 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.ApplicationIntegrationTest | 5 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **1,935 passed; zero failures, errors or skips.**

The metadata source oracle supplies 332 input groups, 785 complete metadata cases,
659 exact InitialInfo/StateData pairs and 4316 map-pool comparisons. Independent
canonical hashes from the original loader verify all metadata values without
decoding expected numeric values through the Java JSON reader. Source fixtures
and the 97-row production catalog reproduce byte-for-byte.

The file-backed UDP terrain test verifies sidecar gravity, fog and palette before
safe placement and terrain-removal rescue. Existing protocol/session/map/physics,
name/admission, spawn-selection and real-UDP gates remain covered.

Focused original metadata tests: **71 passed, 4 skipped** (retail .txtc absent).
See [MAP_METADATA.md](MAP_METADATA.md) for literal grammar and loading bounds.
Actual identity, Character/death/respawn, mode/entity policies, live input/combat
and playable hosting remain pending.

Fresh packaged-JAR native transport and loader probes pass nominal and impaired/
private profiles, including sidecar layering and changed gravity after reload.
These endpoints retain fixture player lives.
