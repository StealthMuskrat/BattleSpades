# Independent CI verification

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38667711995

Implementation: `93f2ea2e934ffd776c0c01b25d3594c658cf1acb`.
Tested/published tree: `86aa49fea0b4b27a652477a0dce5631c52468766`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean wrapper check/build; 289 component + 3 UDP tests; packaging/native constraints; configuration smoke; artifact upload |
| native-enet (Ubuntu) | PASS | compile vendored development oracle; native ENet nominal and impaired real-UDP relay runs, protocol refusal, fragmentation/reliable order, disconnect and reconnect |
| platform (ubuntu-latest) | PASS | same uploaded JAR version/config smoke; component/UDP and portable package checks |
| platform (windows-latest) | PASS | same uploaded JAR version/config smoke; component/UDP and portable package checks |
| platform (macos-latest) | PASS | same uploaded JAR version/config smoke; component/UDP and portable package checks |

The platform smoke steps downloaded **one Ubuntu-built JAR**, unchanged by OS.
Each runner additionally compiled and tested the Java source. Ordinary Gradle and
Maven HTTPS resolution passed without the local sandbox's repository workaround.
Native ENet/Python were used only as test oracles in the separate Linux job; the
application artifact contains neither and uses no native application dependency.

The 292 local tests, repeated local JAR SHA and native metrics are recorded in
[JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md),
[verification-results.json](verification-results.json) and
[native-transport-results.json](native-transport-results.json).
Native packet/retry counts can differ by scheduler timing while assertions pass.

This verifies components, diagnostic startup and configuration. It does **not**
establish a supported original-client join, live map journals, physics/combat,
modes, bots, capacity, a full round or production readiness. Normal game startup
still deliberately exits2 because the application migration is incomplete.

Previous foundation run (all four original jobs passed):
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38662913502
