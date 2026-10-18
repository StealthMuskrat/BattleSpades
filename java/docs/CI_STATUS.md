# Current voting-policy verification

Local Java check/build passes **6,177 component + 37 integration = 6,214 tests**,
with zero failures, errors or skips across 100 suites. This batch adds 49 intact-source
scenario groups, two focused boundary tests and one real UDP integration scenario;
the existing retained-peer rollover composition is upgraded. **273 focused original-source
regressions pass**. The new generator reproduces both outputs byte-for-byte; all 254
prior frozen paths retain their exact hashes, for 256 checked paths. There are 73
configured CI generators. Unchanged prior 72-generator/native-build evidence is reused
locally; CI independently rebuilds/reproduces all 73.

Two CRC-valid local archives are byte-identical: 1911352 bytes,
SHA-256 `abb8d1fbb46296797f3de2ec72a9ba36f0cd4cb65cf2e12b9e5723172ec1753a`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader still uses FixtureScene. Platform tests run
source-built component/integration classes and smoke the uploaded JAR. No local-to-CI
artifact hash identity or complete platform gameplay is claimed. Prior medpack/Zombie/
VIP original-baseline discrepancies remain unwaived; these focused passes do not
establish a passing full original-source baseline.

This checkpoint requires a new five-job CI run after publication.

See [VOTING_POLICY.md](VOTING_POLICY.md) for current scope and limits. Earlier evidence
is in [ROUND_MEDIA.md](ROUND_MEDIA.md), [PREPARED_HOST.md](PREPARED_HOST.md),
[TRANSITIONS.md](TRANSITIONS.md), [ROUND_RUNTIME.md](ROUND_RUNTIME.md) and [TDM_RUNTIME.md](TDM_RUNTIME.md).
