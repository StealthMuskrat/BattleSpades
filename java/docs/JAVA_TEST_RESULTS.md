# Java test results

Local Java check/build passes **6,237 component + 38 integration = 6,275 tests**,
with zero failures, errors or skips across 104 suites. **340 focused original-source
regressions pass**. The batch adds 24 stateful original TeamBalancer/forced-team-change
traces, one grouped gameplay-audio trace and four async/failure boundaries. The existing
three-peer UDP controls composition now also exercises actual HostedGameplayServices:
forced move, retail notice, same-frame respawn on the new team, team-relative cues,
spectator exclusion, unreliable personal/positioned cues and centred hearing stimuli.
No new UDP test duplicates that existing setup.

The new intact-source generator reproduces both outputs byte-for-byte; all 258 earlier
frozen paths retain their hashes, for 260 checked paths and 75 configured CI generators.
Unchanged prior 74-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 75.

Two CRC-valid local archives are byte-identical: 1959105 bytes,
SHA-256 `33830806f66e60a95580d39c9cb2a2eb43ac1af8b2301ad77a395b4293ceee3c`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `9dd88dd9c7dc6bef6e66911d24665077c4980617`,
tested/published tree `bd29520ff81cb0da9116820738dd84bc29645cf6`, in
[run 39175987373](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39175987373). The native job independently reproduced all 75
configured original generators, including the balancing/audio traces and all earlier
fixtures. The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked
the uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

See [GAMEPLAY_SERVICES.md](GAMEPLAY_SERVICES.md) for policy/source scope and pending production domains.
