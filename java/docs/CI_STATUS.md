# Current infantry checkpoint verification

Publication and all five current implementation CI jobs are **pending**.
Local clean check/build passes 4,487 unit/component and 12 UDP tests; 57 focused
original tests pass. The 16,683-operation full-path source fixture reproduces
exactly. No current-tree CI result is inferred from the preceding checkpoint.

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
