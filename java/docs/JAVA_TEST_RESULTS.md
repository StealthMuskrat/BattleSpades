# Java test results

Local Java check/build passes **6,207 component + 38 integration = 6,245 tests**,
with zero failures, errors or skips across 102 suites. This batch adds 24 intact-source
team/class/palette traces, one focused timer/identity boundary, three spectator session
boundaries and one real UDP composition. **317 focused original-source regressions pass**.
The new generator reproduces both outputs byte-for-byte; all 256 earlier frozen paths
retain their exact hashes, for 258 checked paths and 74 configured CI generators.
Unchanged prior 73-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 74.

Two CRC-valid local archives are byte-identical: 1930115 bytes,
SHA-256 `b5b427617552143c4a548720f69bfd26cf5b570544be2f9772d706f5424b9f74`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four current isolated native transport/packaged-
loader nominal/impaired profiles pass. The loader still uses FixtureScene. Platform
tests run source-built component/integration classes and smoke the uploaded JAR. No
local-to-CI artifact hash identity or complete platform gameplay is claimed. Prior
medpack/Zombie/VIP original-baseline discrepancies remain unwaived; these focused passes
do not establish a passing full original-source baseline.

This checkpoint requires a new five-job CI run after publication.

See [PLAYER_CONTROLS.md](PLAYER_CONTROLS.md) for current scope and limits. Earlier
evidence is in [VOTING_POLICY.md](VOTING_POLICY.md), [ROUND_MEDIA.md](ROUND_MEDIA.md),
[PREPARED_HOST.md](PREPARED_HOST.md) and the preceding runtime reports.
