# Current movement-network verification

Local Java check/build passes **6,276 component + 41 integration = 6,317 tests**,
with zero failures, errors or skips across 112 suites. **852 focused original-source
regressions pass freshly**: the preceding 795 plus the complete reversed movement engine
module. Three grouped component tests were added. Existing ClockSync session coverage and
the existing production-factory two-peer UDP scenario were extended.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2079943 bytes, SHA-256
`0254c72e2cecbfd3fc86a1a9dda90f9f91996624e04cfcbc7c96cb370a0c7c07`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

See [MOVEMENT_NETWORK.md](MOVEMENT_NETWORK.md). Previous checkpoint: [hosted inputs](HOSTED_INPUTS.md).
