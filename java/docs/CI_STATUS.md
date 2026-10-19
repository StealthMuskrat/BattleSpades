# Current hosted-entity verification

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

All five CI jobs passed for implementation `69442fee5df3a733d9ab73f87b1721e13857bf54`, tested/published tree
`12580a72376f10cabb0068d55c179f26248271b0`, in [run 39185377778](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39185377778). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. The initial focused-source subprocess omitted the
already-built native ENet reference directory from PYTHONPATH: four imports failed while
413 cases passed. With that directory restored, all 417 unchanged cases pass. The original
medpack/Zombie/VIP baseline discrepancies remain unwaived; focused passes do not establish
a passing full original baseline.

See [ENTITY_RUNTIME.md](ENTITY_RUNTIME.md). Previous checkpoint: [player runtime](PLAYER_RUNTIME.md).
