# Current player-control verification

Local Java check/build passes **6,208 component + 38 integration = 6,246 tests**,
with zero failures, errors or skips across 102 suites. This batch adds 24 intact-source
team/class/palette traces, one focused timer/identity boundary, four spectator session
boundaries and one real UDP composition. **317 focused original-source regressions pass**.
The new generator reproduces both outputs byte-for-byte; all 256 earlier frozen paths
retain their exact hashes, for 258 checked paths and 74 configured CI generators.
Unchanged prior 73-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 74.

Two CRC-valid local archives are byte-identical: 1930149 bytes,
SHA-256 `37e41873cf88ada4444234fc5f51fc357b183696c248b130b5acd40a59538815`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four current isolated native transport/packaged-
loader nominal/impaired profiles pass. The loader still uses FixtureScene. Platform
tests run source-built component/integration classes and smoke the uploaded JAR. No
local-to-CI artifact hash identity or complete platform gameplay is claimed. Prior
medpack/Zombie/VIP original-baseline discrepancies remain unwaived; these focused passes
do not establish a passing full original-source baseline.

Review of the first published player-control checkpoint found a loading race: a
spectator may select a playable team before packet 110 or an admission retry. Its last
published CreatePlayer still describes team 0 until respawn. JoinScene/ConfiguredScene
now require the current canonical domain spectator predicate, implemented by LifeRuntime,
instead of inferring it from that old packet. A dedicated session regression retains the
old spectator roster row while changing the current actor's team and verifies that packet
110 cannot bypass normal first-input admission. The unused roster predicate is removed.
The correction has a fresh full suite/artifact/native check and a successful five-job CI run.

All five CI jobs passed for implementation `f9964fe5185dcf36a353411aac9e85971fdf06e5`,
tested/published tree `c7ebef88711a7fd12902a3c607c7c350bf7d56af`, in
[run 39174391954](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39174391954). The native job independently reproduced all 74
configured original generators, including live team/class/palette traces and all earlier
fixtures. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and ran source-built
component/integration tests. Every final job step completed with success or an intentional
skip. No local-to-CI artifact hash identity or complete platform gameplay is claimed.

See [PLAYER_CONTROLS.md](PLAYER_CONTROLS.md) for current scope and limits. Earlier
evidence is in [VOTING_POLICY.md](VOTING_POLICY.md), [ROUND_MEDIA.md](ROUND_MEDIA.md),
[PREPARED_HOST.md](PREPARED_HOST.md) and the preceding runtime reports.
