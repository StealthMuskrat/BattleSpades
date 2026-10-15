# Java test results

Verification stage: ENet peer/host and loader components, 2026-10-15 UTC.
Implementation: `93f2ea2e934ffd776c0c01b25d3594c658cf1acb`.
Independent Linux, macOS, Windows and native ENet CI: [all five jobs passed](CI_STATUS.md).

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| net.battlespades.ComplexPacketTest | 3 | 0 | 0 | 0 |
| net.battlespades.CompressionTest | 16 | 0 | 0 | 0 |
| net.battlespades.ConfigTest | 4 | 0 | 0 | 0 |
| net.battlespades.EnetTest | 7 | 0 | 0 | 0 |
| net.battlespades.GoldenPacketTest | 178 | 0 | 0 | 0 |
| net.battlespades.HandshakeTest | 2 | 0 | 0 | 0 |
| net.battlespades.LoadingPacketTest | 12 | 0 | 0 | 0 |
| net.battlespades.MapSyncTest | 15 | 0 | 0 | 0 |
| net.battlespades.RangeCoderTest | 31 | 0 | 0 | 0 |
| net.battlespades.SimulationTest | 4 | 0 | 0 | 0 |
| net.battlespades.WorldFormatTest | 4 | 0 | 0 | 0 |
| net.battlespades.transport.enet.PeerDeliveryTest | 13 | 0 | 0 | 0 |
| net.battlespades.HostIntegrationTest | 2 | 0 | 0 | 0 |
| net.battlespades.UdpIntegrationTest | 1 | 0 | 0 | 0 |

Total: **292 passed; zero failures, errors or skips.**

Additional native ENet interoperability runs passed both nominal and impaired UDP
profiles, including protocol167/169 refusal, fragmentation, ordered reliable delivery,
duplicates/reordering, graceful disconnect and reconnect. Actual metrics are recorded
in [native-transport-results.json](native-transport-results.json). Native ENet/Python
are development oracles only and are not application dependencies.

These tests verify components, not original-client gameplay, full late joins or capacity.
