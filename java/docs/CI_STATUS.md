# Independent CI verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38795898680

Implementation: `d03cb25c420323ef6a94379f06de349cd58ded0e`.
Tested/published tree: `f46b2a5736cd9dc49afae067df58f35e1239212a`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 3016 unit/component + 9 UDP tests; config smoke; repeat JAR byte comparison before upload |
| native-enet (Ubuntu) | PASS | native transport/packaged loader; original source rebuild; exact new life fixtures/catalog and earlier source gates |
| platform (ubuntu-latest) | PASS | same uploaded JAR smoke; 3025 component/UDP tests and portability checks |
| platform (windows-latest) | PASS | same uploaded JAR smoke; 3025 component/UDP tests and portability checks |
| platform (macos-latest) | PASS | same uploaded JAR smoke; 3025 component/UDP tests and portability checks |

The platform smoke steps download one Ubuntu-built JAR unchanged across OSes.
Each runner also compiles/tests the source. CI independently rebuilds and
byte-compares its archive before upload; no local-to-CI hash comparison is claimed.

New original-source comparisons: 756 all-class spawn/pre-spawn states, 3624
inventory operations, 4000 kill-feed operations/observations, 300 encoded/decoded
packets, 183 death/respawn transitions and 592 roster operations. CI reproduces
all six fixtures, provenance and the 69-row production catalog exactly.

The new real-UDP test uses production LifeRuntime/PlayerLife and fresh world-bound
movers, with explicit test mode/input/effect ports. It verifies loading-time
respawn, one owner repair, quiet peer death catch-up and a later fresh body.
Packaged native transport/loader probes retain their earlier fixture player domain.
The original methods execute unchanged; no policy substitution is used.

Two complete local archives pass ZIP CRC and match byte-for-byte: 1,270,671
bytes, SHA-256 `28a91a2f05c44df1fc0c296406854f0080a3d7c07c2e01969ac41f0bc883fd8a`. The JAR contains Java 25 classes and source
data, without native/Python/test fixture code. Focused original life/inventory
regressions: **128 passed, zero skips**.

Full input chronology, combat damage/healing, concrete modes/effects, authenticated
identity, supported-client sessions, capacity and soak remain pending. Ordinary
hosting remains disabled (exit 2). See [PLAYER_LIFECYCLE.md](PLAYER_LIFECYCLE.md),
[JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md) and [verification-results.json](verification-results.json).

Previous metadata CI (all five passed):
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38790040711.
No packaging, platform or source-regeneration gate was waived.
