# Current input checkpoint verification

Publication and all five CI jobs for the ClientData/action-cadence checkpoint are
**pending**. Local clean check/build passes 4,451 unit/component and 11 UDP tests.
Both source fixtures regenerate exactly; 31 focused original tests pass.
No current-tree CI result is inferred from the preceding checkpoint below.

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
