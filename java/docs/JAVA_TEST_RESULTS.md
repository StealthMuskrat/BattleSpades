# Java test results

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

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

See [HOSTED_INPUTS.md](HOSTED_INPUTS.md) for scope and remaining services.
