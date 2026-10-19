# Current hosted-combat verification

Local Java check/build passes **6,271 component + 41 integration = 6,312 tests**,
with zero failures, errors or skips across 110 suites. **673 focused original-source
regressions pass** freshly: the preceding 417 and seven complete combat regression modules.
This batch adds two grouped component boundaries and extends the existing production-factory
UDP scenario. Existing combat/terrain/firing/rewind policy goldens remain in use.

All 264 preceding frozen paths are unchanged. The existing domain data exporter now also
exports the original block-destruction profile statistic into one new catalog; repeating
that export is byte-identical. There are 265 frozen paths and still 77 configured generators.
The other 76 generator/native-build evidence is reused locally; CI independently rebuilds
and reproduces all 77. No new policy oracle, duplicated matrix or production dependency is
introduced. Two CRC-valid local archives are byte-identical: 2063142 bytes,
SHA-256 `2c2e418f3699783bf2763ac28616e7fc354f668cd9d37f118075b0b290770a3f`. Class major 69, packaging/licenses,
version/config smokes and all four fresh native transport/packaged-loader profiles pass.
The native loader uses FixtureScene; ordinary hosting exits 2. No supported retail-client
test, complete platform gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `3a61a755aaf90b86a14d8f7bfecd32b4ed25a52e`, tested/published tree
`8b360bd0bfe3fb6c757f55df05e9c8e4c08bd123`, in [run 39187427065](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39187427065). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

See [HOSTED_COMBAT.md](HOSTED_COMBAT.md). Previous checkpoint: [hosted entities](ENTITY_RUNTIME.md).
