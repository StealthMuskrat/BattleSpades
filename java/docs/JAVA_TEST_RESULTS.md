# Java test results

Local Java check/build passes **6,269 component + 41 integration = 6,310 tests**,
with zero failures, errors or skips across 109 suites. **417 focused original-source
regressions pass** freshly: all preceding 353 plus 64 cases from the complete entity
registry, behavior, map-resource and attachment modules. Three grouped component tests
are added; the existing session backpressure test and production-factory UDP scenario
are extended. Existing policy goldens and other integration scenarios remain in use.

All 264 frozen paths are unchanged. Existing 77-generator/native-build evidence is reused
locally; CI independently rebuilds and reproduces all 77. No new generator, production
dependency or duplicate policy matrix is introduced. Two CRC-valid local archives are
byte-identical: 2051464 bytes, SHA-256 `6e6aff40416d134334e8b0fc1ee4aa146a9169744068475981b819569f4c5610`.
Class major 69, packaging/licenses, version/config smokes and all four fresh native
transport/packaged-loader profiles pass. The native loader uses FixtureScene; normal
hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. The initial focused-source subprocess omitted the
already-built native ENet reference directory from PYTHONPATH: four imports failed while
413 cases passed. With that directory restored, all 417 unchanged cases pass. The original
medpack/Zombie/VIP baseline discrepancies remain unwaived; focused passes do not establish
a passing full original baseline.

See [ENTITY_RUNTIME.md](ENTITY_RUNTIME.md) for scope and remaining services.
