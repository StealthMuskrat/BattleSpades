# Independent CI verification

The new player-name/admission checkpoint passes locally (1,260 unit/component +
7 UDP tests; fresh native probes; two identical JARs). Its independent CI is pending
publication. The completed run below is the preceding configured-scene checkpoint.

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38742439157

Implementation: `9e8b320709b35d6cece3c5078ea59af246f4ccbb`.
Tested/published tree: `eadba7cd03e37328f9e8ce2cbdfc07e452697998`.
Observed 2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean check/build; 688 unit/component + 6 real UDP tests; config smoke; rebuild and byte-compare application JAR before upload |
| native-enet (Ubuntu) | PASS | native transport and packaged/configured loader, loss/password/reload probes; strict original bytes/packet/glm/VXL/world rebuild; reproduce fixtures and production catalogs |
| platform (ubuntu-latest) | PASS | one uploaded JAR smoke; 694 component/UDP tests and portable checks |
| platform (windows-latest) | PASS | one uploaded JAR smoke; 694 component/UDP tests and portable checks |
| platform (macos-latest) | PASS | one uploaded JAR smoke; 694 component/UDP tests and portable checks |

The platform smoke steps downloaded **one Ubuntu-built JAR**, unchanged by OS.
Each runner additionally compiled and tested the source. The native loader runs
its production classes/dependencies/catalogs from the packaged application JAR;
its remaining player domain is a test-only fixture, excluded from that artifact.

Current evidence includes 1958 source rule coercion inputs,336 source selections,
99 source InitialInfo/StateData pairs and 864 rule-adjusted movement profiles
(10368 source ticks), plus earlier 12924 movement ticks/941 world queries. Packet
bytes, value types and exposed motion bits are compared exactly. Production
catalogs include 102 rules, 18 classes, 43 mode metadata entries and 90 registered
names/aliases. Source regeneration requires an empty Git diff.

The first configured-scene run38741477499 passed the build/reproducibility and
three platform jobs, but its source-oracle job hit an unrelated eager server
launcher import of toml. Reference exporters now prepare a package namespace
without changing original policy code/tables and pass with Python -S; the current
run reproduces the fixtures/catalogs successfully without that dependency.

Local incremental archive truncations were detected and rejected. Final local
valid archives matched, and the independent CI rebuild/byte-comparison passed.
Native/Python oracles are development-only; normal Gradle builds and the JAR need
no Python/native compiler. Production dependencies remain pure Java.

This verifies components, not playable hosting, original-client gameplay or
capacity. Actual identity/team/mode authorization, spawn/lifecycle, live input
scheduling/reconciliation, combat/modes/bots and release readiness remain pending.

Evidence: [verification-results.json](verification-results.json),
[JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md), [CONFIGURED_SCENES.md](CONFIGURED_SCENES.md).
The stored native-loader data is from the final local configured-JAR run; the same
asserted scenarios passed in CI. Retry/delay counts vary with scheduling.

Previous movement checkpoint: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38705329003
