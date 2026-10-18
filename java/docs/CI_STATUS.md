# Current prepared-map and owned-host verification

Local Java check/build passes **6,097 component + 36 integration = 6,133 tests**,
with zero failures, errors or skips across 96 suites. This batch adds 15 targeted
component cases and upgrades two existing compositions instead of duplicating them.
**200 focused original-source regressions pass**. The new actual-source generator
records four real worker loads and eight team caches, reproducing both outputs
byte-for-byte on a second run. All 249 previous frozen paths remain unchanged
(251 checked paths total). Prior unchanged 70-generator/native-build evidence is
reused locally; CI is configured to independently reproduce all 71 generators.

Two CRC-valid local archives are byte-identical: 1845795 bytes,
SHA-256 `ccbb032a76c5b70ced0064e8716daaf6d27645e39de8a61346bb93e3e1499955`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader probe still uses FixtureScene. Platform
component/integration tests run source-built classes and smoke the uploaded JAR;
no local-to-CI artifact hash identity or complete platform gameplay is claimed.
The prior medpack/Zombie/VIP original-baseline discrepancies remain unwaived.
These focused passes do not establish a passing full original-source baseline.

This checkpoint requires a new five-job CI run after publication.

See [PREPARED_HOST.md](PREPARED_HOST.md) for current scope and limits. Earlier
checkpoints are documented in [TRANSITIONS.md](TRANSITIONS.md),
[ROUND_RUNTIME.md](ROUND_RUNTIME.md) and [TDM_RUNTIME.md](TDM_RUNTIME.md).
