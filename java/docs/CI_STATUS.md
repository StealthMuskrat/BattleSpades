# Independent CI verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38705329003

Implementation: `9cfe665bbb3340f2606a2486a3f337dc4c6f3386`.
Tested/published tree: `04c4c8b042a71ec31ff172e79b8d971f2613e35b`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 459 unit/component + 5 real UDP tests; portable package checks, core/join config smoke, artifact upload |
| native-enet (Ubuntu) | PASS | native ENet transport and application-loader loss/reconnect/catch-up probes; rebuild original Cython glm/VXL/world and reproduce all new fixtures exactly |
| platform (ubuntu-latest) | PASS | one uploaded application JAR version/config smoke; 464 component/UDP tests and portable checks |
| platform (windows-latest) | PASS | one uploaded application JAR version/config smoke; 464 component/UDP tests and portable checks |
| platform (macos-latest) | PASS | one uploaded application JAR version/config smoke; 464 component/UDP tests and portable checks |

All platform smoke steps downloaded **one Ubuntu-built JAR**, unchanged by OS.
Each runner additionally compiled and tested the source. Movement tests replay
118 cases/12924 source ticks with exact exposed position/velocity bits, flags and
landing results. Queries compare 941 original vectors exactly. The native job
regenerates the compressed traces with strict source compiler flags and requires
an empty Git diff for all four fixture files.

The native/Python source oracles and test scenes are excluded from the JAR.
The normal Java build needs no Python/native compiler. Dependencies remain pure
Java. This is component verification; ordinary game hosting is still disabled.
Configured scenes, live player lifecycle/physics integration, combat/modes/bots,
original-client gameplay, capacity and release readiness remain pending.

Local artifact and suite evidence: [verification-results.json](verification-results.json),
[JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md) and [MOVEMENT_PHYSICS.md](MOVEMENT_PHYSICS.md).
Previous-stage native protocol data: [native-transport-results.json](native-transport-results.json)
and [native-loader-results.json](native-loader-results.json); the same asserted
scenarios passed again in the run above. Timing/retry counts are scheduler-dependent.

Previous application checkpoint, also five passing jobs:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38700225938
