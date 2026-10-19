# Java test results

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

All five CI jobs passed for implementation `45fb8a93a8412ce11fc23c44938870ec6ac5e7c8`, tested/published tree
`5a28c2fe4fc0a8319903dd9f929e4f5797eb8247`, in [run 39191321035](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39191321035). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

See [MOVEMENT_NETWORK.md](MOVEMENT_NETWORK.md) for scope and remaining services.
