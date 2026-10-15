# Independent CI verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38749518170

Implementation: `4c9b6014a1d6aab45a7ee8ee3e9232693de0f855`.
Tested/published tree: `2dcb997a71ff93209d754d657f7c8bc68c503843`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 1260 unit/component + 7 UDP tests; config smoke; rebuild and byte-compare JAR before upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; loss/password/reload; original source rebuild and fixture/catalog/Unicode regeneration |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; 1267 component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; 1267 component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; 1267 component/UDP tests and portability checks |

Platform smoke steps download **one Ubuntu-built JAR**, unchanged by OS.
Each runner also compiles/tests the source. The native loader uses production
classes, dependencies, catalogs and Unicode data from the JAR; its player
lifecycle remains a test-only fixture excluded from that artifact.

New source comparisons cover 1,112,064 Unicode scalar values, 18,568 name vectors,
6,192 team decisions and 14,256 class vectors. CI executes original name policy,
unmodified selected connection AST methods and original equipment/rules/selection,
then requires an empty diff for fixtures/provenance and production Unicode data.
Python -S passes without extra policy dependencies; original compiled packet/world
extensions are development-only. Earlier scene/rule/movement/query gates also pass.

The four-client UDP test covers name uniqueness, reserved fallback, neutral/team
balance, spectators, class fallback, forged forced_team and reserved identities.
Fresh local native probes pass nominal and impaired/private runs. Two final local
archives pass CRC and match byte-for-byte: 1,150,799 bytes, SHA-256
`841212008c429f3da0832a6daf9e76ca6d2e0d739d154acdd97880e1b19b1b17`.
CI also passes its own rebuild/byte comparison before upload.

This verifies components, not playable hosting, authenticated identity, concrete
mode hooks, spawning/lifecycle, original-client gameplay or capacity.
See [PLAYER_ADMISSION.md](PLAYER_ADMISSION.md),
[JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md) and
[verification-results.json](verification-results.json).

Previous configured-scene CI:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38742439157.
Earlier local truncated archives were rejected; no packaging gate was waived.
