# Independent CI verification

Current movement/query tree: **pending independent CI**. The evidence below is for the
previous application checkpoint, not the newly edited tree.

## Previous application checkpoint

Run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38700225938

Implementation: `a9c29842e67cc96eade2be3b2908044e44bd4e53`.
Tested/published tree: `aa4e78c70ea4e965cafc5fa66c81e6fd59580160`.
Observed2026-10-15 UTC: **all five jobs succeeded**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean wrapper check/build;337 unit/component +5 real UDP tests; native/package constraints; core/join config smoke; artifact upload |
| native-enet (Ubuntu) | PASS | compile vendored oracle; native transport loss/reconnect probes; native composed loader/password/XOR/full+delta map/roster/terrain catch-up and scene-reload probes |
| platform (ubuntu-latest) | PASS | same uploaded JAR version/config smoke;342 component/UDP tests and portable checks |
| platform (windows-latest) | PASS | same uploaded JAR version/config smoke;342 component/UDP tests and portable checks |
| platform (macos-latest) | PASS | same uploaded JAR version/config smoke;342 component/UDP tests and portable checks |

All platform smoke steps downloaded **one Ubuntu-built JAR**, unchanged by OS.
Each runner additionally compiled and tested the source. Test-only scene adapters
are excluded from the application artifact; native ENet/Python are test oracles,
not application dependencies. Ordinary wrapper/Maven/pip HTTPS resolution passed.

Local artifact/test details: [verification-results.json](verification-results.json)
and [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md). Native data:
[native-transport-results.json](native-transport-results.json) and
[native-loader-results.json](native-loader-results.json). Scheduling can change
retry counts without changing the assertions or expected reliable bytes.

This verifies the listed components and a test fixture scene. It does **not**
establish an original game-client/UI session, configured playable modes, physics,
combat, bots, capacity or release readiness. Ordinary game startup remains disabled.

Previous stage, also five passing jobs:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38667711995
