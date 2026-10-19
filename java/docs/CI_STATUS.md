# Current hosted-input verification

Local Java check/build passes **6,273 component + 41 integration = 6,314 tests**,
with zero failures, errors or skips across 111 suites. **795 focused original-source
regressions pass freshly**, including complete movement-anticheat, ammunition, score-event
and reversed-WorldUpdate modules. Two grouped component tests were added; the existing
production-factory UDP scenario was extended. Existing policy/golden matrices were retained.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2069837 bytes, SHA-256
`083adcac909e4f6e14cad039548a3d24eac1a012a27d1c4d196d40d9f0c597cb`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `ab156d494ff15da6649b8cdd0f925eef2963f66b`, tested/published tree
`6794d160e3ddac77d8b92dc15aa34a0c59328373`, in [run 39189298397](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39189298397). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

See [HOSTED_INPUTS.md](HOSTED_INPUTS.md). Previous checkpoint: [hosted combat](HOSTED_COMBAT.md).
