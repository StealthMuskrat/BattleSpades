# Current combat-timing checkpoint verification

Local clean check/build: **4,671 unit/component + 16 real UDP tests**, no failures,
errors or skips. **152 focused original tests** pass, including the reference
impaired-link lab. New comparisons cover 67,151 source operations. All 112
fixture/catalog hashes reproduce after a fresh original native build.
All five jobs passed for implementation `0f6448231be6e93f3ce58d4682d9cd9963ac95ef` and tested/published tree
`9acb23de8fa14ae82f90188bf67f741bae3f563b` in [run 39073877248](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39073877248): build,
native-enet and platform jobs on Ubuntu, Windows and macOS. The native job
regenerated all original fixtures; the build byte-compared repeated archives;
platform jobs tested one uploaded JAR. No local-to-CI archive hash identity is claimed.

Native transport/packaged-loader probes pass nominal and impaired profiles.
An earlier local impaired transport attempt reported disconnect reason 0 during
a concurrent native rebuild. Its cause is not established; an isolated rerun
passed every unchanged assertion. This observation is retained in
verification-results.json. No gate was waived.

Two CRC-valid local archives are byte-identical: 1427391 bytes, SHA-256
`03e756ac28f212fba56b04c4e547e634ba92eb3a6b2a277f4c07ab5c46a397a2`. Class major 69 and production packaging audits pass;
version/config exits 0 and normal hosting exits 2. No native or Python runtime is
packaged.

The owned UDP tests cover read-only shot preparation and actual reliable reload
boundaries. Source reference-lab success does not imply a Java hit resolver or
supported-client round. Full combat/domains/modes and capacity/soak gates remain
pending. See [COMBAT_TIMING.md](COMBAT_TIMING.md).

# Preceding replication checkpoint verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/39071633850

Implementation: `00d96933315fc201e44234912e6e95c6ed17609d`.
Tested/published implementation tree: `276cf8712f4801de0171f8a8b37380a78c4f04e8`.
All five jobs passed for this exact head: build, native-enet, and platform jobs
on Ubuntu, Windows and macOS. The build repeats and byte-compares its archive;
native-enet rebuilds original source and regenerates all new/prior fixtures;
platform jobs test the same uploaded JAR plus component/UDP and portability gates.
No gate was waived; no local-to-CI artifact hash identity is claimed.

Local clean check/build: **4,588 unit/component + 14 real UDP tests**, no failures,
errors or skips. **154 focused original replication tests** pass. The 33,987 new
source operations and all 99 fixture hashes reproduce after a fresh original
native build. Publication advanced without force from
`c5ae623e4b607b983a3bc5496fed81e88f10cf8e`.

Native transport/packaged-loader probes pass nominal and impaired profiles.
Two CRC-valid local archives are byte-identical: 1385834 bytes, SHA-256
`b66dc523fe89d1b8fe4a095d23e727d9147963fc15629b4db683c944552a53d5`. Java class major 69 and production packaging audits pass;
version/config exits 0 and hosting exits 2. No native or Python runtime is packaged.

The two-client replication UDP test uses actual owned lives and delayed ACKs.
Presentation/entity/corpse/bot/combat/mode domains and supported-client/capacity/soak
gates remain incomplete. See [WORLD_REPLICATION.md](WORLD_REPLICATION.md).

# Preceding flight checkpoint verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/39068854866

Implementation: `0f943c69d4d6634b2eaebacc5512adfaf0399aaa`.
Tested/published implementation tree: `e1742fc548591395ce8e44735ad0f2e543c38bae`.
Observed 2026-10-17 UTC: **all five migration CI jobs succeeded**.
The branch advanced without force from `473ea1fe2d1b6bac1f914dc163e10ebc86577259`.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build, config smoke, repeated JAR byte comparison and artifact upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; original world rebuilt; flight/impact and earlier fixtures/catalogs reproduced exactly |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |

Local clean check/build passes **4,552 unit/component + 13 UDP tests**, zero
failures/errors/skips. All **117 focused original flight tests** pass. Flight
comparisons cover 65,497 original full-path operations in 44 traces plus 10,368
canopy-impact vectors. Seven deterministic gzip parts retain the complete
uncompressed comparison SHA; the unchanged original source regenerates every
part and provenance exactly. The preceding infantry fixture remains byte-identical.

Native transport and packaged-loader probes pass nominal/impaired profiles.
Two CRC-valid local archives are byte-identical: 1,340,629 bytes, SHA-256
`102f64b6c21657a9c6f8a5d803e1d27701a26a759277694baa84f1f216f04fd3`.
The fixture packaging change produces the same production JAR. CI independently
rebuilds/byte-compares its archive and tests one uploaded JAR on three OSes.
No local-to-CI artifact hash comparison is claimed. No CI gate was waived.

Connected flight resources/canopy physics are verified; owner-row notification is
an explicit port, and source timing constants retain their compatibility-heuristic
status. Complete owner WorldUpdate reconciliation, bots/corpse-fuse, combat and
concrete modes remain pending. Native probes retain FixtureScene; production
flight uses the owned UDP test. No supported-client round or capacity/soak result
is inferred. The full Python baseline retains its documented discrepancy and
incomplete runs. Ordinary hosting still exits 2. See [PLAYER_FLIGHT.md](PLAYER_FLIGHT.md).

# Preceding infantry checkpoint verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38834640118

Implementation: `1e4c34fced6e6fc96185fd72b212d62b1e3eab70`.
Tested/published implementation tree: `4a54308beb4e425dbfa9ea0ab2ba0176b619ccf6`.
Observed 2026-10-16 UTC: **all five migration CI jobs succeeded**.
The branch was advanced without force from `87e4892c9b5b5ebe4861a80655b5e21d0fe62492`.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build, config smoke, repeated JAR byte comparison and artifact upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; original world rebuilt; infantry and earlier fixtures/catalogs reproduced exactly |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |

Local clean check/build passes **4,487 unit/component + 12 UDP tests** with zero
failures/errors/skips; **57 focused original tests pass**. The 16,683-operation
full-path fixture and provenance reproduce exactly, locally and in CI. Native
transport and packaged loader pass nominal and impaired profiles. Two CRC-valid
local archives are byte-identical: 1,322,465 bytes, SHA-256
`2c8f3ae85b043cbfff56acf25e5d6cc6693b7ed6f22938354a1a14b221d135f6`.

CI separately rebuilds and byte-compares its archive, then tests one uploaded JAR
unchanged on Linux, Windows and macOS. No local-to-CI artifact hash comparison is
claimed. No current CI gate was waived. The earlier publication approval block is
resolved by explicit authorization for this and future verified branch checkpoints.

This checkpoint binds no-flight infantry pacing, the production mover, eye history,
labelled impulses and fall-health ordering. Required reload/lag/mode/effect hooks
remain incomplete domain work. Flight equipment, upstream rate/conduct admission,
owner WorldUpdate, firing/hit authorization and concrete modes remain pending.
Native probes retain FixtureScene; production-domain movement uses the additional
owned-scheduler UDP test. No supported-client round, capacity or soak result is
inferred. The full Python baseline retains its documented discrepancy/incomplete
runs. Ordinary hosting still exits 2. See [INFANTRY_SIMULATION.md](INFANTRY_SIMULATION.md).

# Preceding input checkpoint verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361

Implementation: `7d0d68e8e3fda4974fe7ebc070e24f7833b74a25`.
Tested/published tree: `e7c13efb769da9833c27e056f69df57832118a30`.
Observed 2026-10-16 UTC: **all five migration CI jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build, config smoke, repeated JAR byte comparison and artifact upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; original codecs rebuilt; input/cadence and earlier fixtures/catalogs reproduced exactly |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; component/UDP tests and portability checks |

Local clean check/build passes **4,451 unit/component + 11 UDP tests**, with no
failures, errors or skips. The unchanged original input-loss/action-clock modules
pass **31 tests**. Input comparisons cover 14,234 operations in 24 traces; cadence
comparisons cover 26,234 operations in 32 traces. CI regenerates both fixtures and
provenance and checks exact differences, alongside all preceding source gates.

Two CRC-valid local archives are byte-identical: 1,305,475 bytes, SHA-256
`3c828a867a4e0002e802c0a9cd040ace94fdad208e4a692541b15ebb51b273ce`.
Production classes target Java 25, major 69; no native/Python/test code is packaged.
CI separately rebuilds and byte-compares its archive, then uses one Ubuntu-built
JAR unchanged on Linux, Windows and macOS. No local-to-CI artifact hash comparison
is claimed.

The production life receive path now records ClientData chronology and suppresses
stale immediate state. Phase selection and action cadence are source-backed
components. Outer pacing/equipment/impulse/mover/WorldUpdate integration, firing,
reload and hit/friendly-fire/mode authorization remain pending. No raw handler
authorizes damage in this checkpoint. Packaged native probes retain FixtureScene;
the additional UDP chronology test uses production sessions/lives with explicit
test policy ports. No supported-client round, gameplay platform/capacity or soak
result is inferred. Ordinary hosting still exits 2. No CI gate was waived.
See [PLAYER_INPUT.md](PLAYER_INPUT.md) for exact scope.

## Preceding accepted-health checkpoint

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38826339114

Implementation: `e9e06e5af17deb9cb4f939c9ddd9112ad18a9881`.
Tested/published tree: `8195a734f9d048246bb17da572a0f2bf829cd824`.
Observed 2026-10-15 UTC: **all five migration CI jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 4390 unit/component + 10 UDP tests; smoke; repeated JAR byte comparison before upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; original codecs rebuilt; health and earlier source fixtures/catalogs regenerated exactly |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; 4400 component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; 4400 component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; 4400 component/UDP tests and portability checks |

Platform smoke downloads one Ubuntu-built JAR unchanged across OSes; every runner
also compiles/tests the source. CI independently byte-compares its rebuilt archive.
No local-to-CI artifact hash comparison is claimed.

Original-method health comparisons cover 1152 damage cases, 120 healing cases,
80 nested attributions and 832 stateful operations. CI reproduces all four health
fixtures and provenance, plus the updated source-data catalog and earlier gates.
The original functions are not rewritten or replaced; profile observation captures
original observer boundaries. Complete score/award policies remain outside scope.

The additional UDP test uses production domain/session/body code and explicit test
mode/input/effect ports. It verifies gated HP suppression, live damage/healing, god
mode, lethal HP before KillAction and respawn. Reliable backpressure regressions
verify owner/broadcast order and bounded overflow cleanup. Packaged native probes
retain the earlier fixture player domain; no supported-client gameplay is claimed.

Two complete local archives pass ZIP CRC and match byte-for-byte: 1,282,859
bytes, SHA-256 `8493c5e35acc124a936115075626a27b78d21feae22cd8453584c5ef93cdb1dc`. Production code is Java 25 and contains no
native/Python/test fixture code.

Focused original health suites: **46 passed**. The additional original rules audit
retains **45 passes and one existing VIP round-limit failure**, reproduced alone
with source/test files unchanged. These CI results do not make the full Python
baseline pass. See [PYTHON_BASELINE.md](PYTHON_BASELINE.md) for that evidence.

Hit/action/friendly-fire authorization, detached projectiles, full input/flight
timelines, complete awards/assist payouts, concrete modes, authenticated identity
and supported-client/capacity/soak checks remain pending. Hosting still exits 2.
See [PLAYER_HEALTH.md](PLAYER_HEALTH.md), [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md)
and [verification-results.json](verification-results.json).

Previous lifecycle CI (all five passed):
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38795898680.
No current migration CI gate was waived.
