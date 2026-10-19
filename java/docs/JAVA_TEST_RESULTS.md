# Java test results

Local Java check/build passes **6,266 component + 41 integration = 6,307 tests**,
with zero failures, errors or skips across 108 suites. **353 focused original-source
regressions pass** freshly: the preceding 350 plus three configuration integration and
clamp tests. This batch adds three grouped component boundaries and one real UDP test
for the production factory/player runtime. Existing movement, replication, repair,
team-balance, profile/combat and scene-transition policy suites remain in use.

All 264 frozen paths are unchanged. The existing 77-generator/native-build evidence is
reused locally; CI independently rebuilds and reproduces all 77. No new generator,
production dependency or duplicate policy matrix is introduced. Two CRC-valid local
archives are byte-identical: 2036880 bytes, SHA-256
`f7fcea3f608af9d877c84c5201eb6f58933a47cf9b955c98965f3f8b254244ee`. Class major 69, packaging/licenses, version/config smokes and
all four fresh native transport/packaged-loader profiles pass. The native loader uses
FixtureScene; normal hosting exits 2. No supported retail-client test, complete platform
gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `f96bbca34a9880450ecc27e81a444a8ba9d35977`, tested/published tree
`a781f34b3178525f352f01399a1ff8897fb71788`, in [run 39183158502](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39183158502). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. A truncated intermediate repeat archive was rejected;
a subsequent rebuild passed CRC and byte-equality checks without source/assertion changes. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

See [PLAYER_RUNTIME.md](PLAYER_RUNTIME.md) for scope and remaining services.
