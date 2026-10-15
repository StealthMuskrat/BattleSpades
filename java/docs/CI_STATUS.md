# Independent CI verification

The new map-metadata checkpoint passes 1927 unit/component and 8 UDP tests locally.
Its independent CI is pending publication. The completed spawn run below remains
previous evidence until this checkpoint finishes CI.

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38785215231

Implementation: `6792f538924d0bbd52a62e0b1fa85d8ead0c5b5e`.
Tested/published tree: `5608362cd28586e9d89adef82719d4ac2b8235f6`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 1296 unit/component + 8 UDP tests; config smoke; rebuild and byte-compare JAR before upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; loss/password/reload; original source rebuild and exact fixture/catalog/Unicode regeneration |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; 1304 component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; 1304 component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; 1304 component/UDP tests and portability checks |

Platform smoke steps download **one Ubuntu-built JAR**, unchanged by OS.
Each runner also compiles/tests the source. The native loader uses production
classes, dependencies, catalogs and Unicode data from the JAR; its player
lifecycle remains a test-only fixture excluded from that artifact.

New source comparisons cover 18,124 geometry/segment vectors, 822 selection,
score and rescue events, 16,000 random operations, 3,064 hypot and 3,000 compensated
sum vectors. Resolved spawns settle through 8,640 exact original-engine ticks
across all 18 classes. The source-oracle job executes original policy modules
and compiled VXL/world, then requires an empty diff for every spawn fixture and
its provenance. Earlier movement/query/scene/catalog/name/admission gates pass.
Python -S passes without extra policy dependencies; compiled reference extensions
are development-only and excluded from the production JAR.

The new real-UDP test removes terrain below a first placement and verifies that
the next configured admission gets a safe live position. Fresh local native
transport and packaged-loader probes pass nominal and impaired/private runs.
Two final local archives pass CRC and match byte-for-byte: 1,187,524 bytes,
SHA-256 `a3b38a6cf981b039835ef9d71afa0ce4c5cc28c795ab050a5f1d315ac11b0a07`.
CI also passes its own rebuild/byte comparison before upload.
The focused original spawn subset passes 42 local pytest tests.

This verifies placement components. Playable hosting, authenticated identity,
sidecar assembly, full Character/death/respawn lifecycle, concrete mode hooks,
original-client gameplay and capacity remain unverified or unimplemented.
See [SPAWNING.md](SPAWNING.md), [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md)
and [verification-results.json](verification-results.json).

Previous admission CI:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38749518170.
Previous configured-scene CI:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38742439157.
Earlier local truncated archives were rejected; no packaging gate was waived.
