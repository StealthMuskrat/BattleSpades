# Independent CI verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38790040711

Implementation: `9b822d80e40fc390eea3fb471822a1fec0a878cb`.
Tested/published tree: `b4581018e5647ab630fd3b808dfae4577a3d15a7`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 1927 unit/component + 8 UDP tests; config smoke; rebuild and byte-compare JAR before upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; sidecar layering and metadata reload; original source rebuild and exact fixture/catalog/Unicode regeneration |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; 1935 component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; 1935 component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; 1935 component/UDP tests and portability checks |

Platform smoke steps download **one Ubuntu-built JAR**, unchanged by OS.
Each runner also compiles/tests the source. The native loader uses production
classes, dependencies and catalogs from the JAR. Its player lives remain fixtures
excluded from that artifact.

New comparisons execute the original map metadata loader and common scene
builders: 332 input groups, 785 metadata cases, 659 exact packet pairs and 4316
map-pool vectors. Independent source digests verify complete metadata values
without decoding expected numbers through the Java JSON reader. CI reproduces
all metadata fixtures/provenance and the 97-row production catalog without a diff.
Earlier movement/query/scene/catalog/name/admission/spawn gates also pass.
The correction retains the original JSON reader's ASCII whitespace grammar.

The real-UDP terrain test now reads layered sidecars and verifies gravity, fog,
palette and safe live placement. Fresh local and independent native loader probes
verify sidecar layering and changed gravity after scene reload in nominal and
impaired/private profiles. Transport probes also pass. Compiled original packet,
VXL/world and ENet oracles are development-only, excluded from the production JAR.

Two final local archives pass CRC and match byte-for-byte: 1,227,317 bytes,
SHA-256 `a1955c5f48f010c3cd761b0b80b433c31f0280d8ec28c76dace65cf06b36eb06`.
CI independently passes its own rebuild/byte comparison before upload.
Focused original metadata tests: **71 passed, 4 skipped** (retail .txtc absent).

This verifies metadata, map/layout assembly, packet and placement components.
Authenticated identity, Character/death/respawn, concrete mode/entity policies,
live gameplay, original-game-client sessions and capacity remain incomplete.
See [MAP_METADATA.md](MAP_METADATA.md), [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md)
and [verification-results.json](verification-results.json).

The initial metadata implementation passed all five jobs in
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38789238339.
Previous spawn CI:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38784215231.
No packaging or source-regeneration gate was waived.
