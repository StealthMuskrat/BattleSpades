# Independent CI verification

Workflow run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38662913502

Implementation commit: `6a4dc5eb52d96812dd9ae565f42de4746b414b1b`.
Observed 2026-10-15 UTC: all four jobs completed **successfully**.

| Job | Result | Scope |
|---|---|---|
| build (Ubuntu) | PASS | clean wrapper check/build, dependency verification, packaging/native constraints, root config smoke, test/artifact upload |
| platform (ubuntu-latest) | PASS | same uploaded application JAR version/config smoke; component and UDP tests |
| platform (windows-latest) | PASS | same uploaded application JAR version/config smoke; component and UDP tests |
| platform (macos-latest) | PASS | same uploaded application JAR version/config smoke; component and UDP tests |

The platform jobs downloaded the **one artifact built by the Ubuntu build job**;
they did not customize it by operating system. Each platform additionally compiled
and tested the source with its Java25 environment. Normal Maven Central and wrapper
network resolution passed outside the local sandbox's dependency-download workaround.

This establishes cross-platform **component, diagnostic startup and configuration**
coverage. It does not establish original-client connection/map sync, movement,
combat, terrain edits, bots, modes, plugins, admin, persistence or performance.
Those features are incomplete or unverified as recorded in FEATURE_PARITY.md.
No full-game cross-platform or production-readiness claim is made.
