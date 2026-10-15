# Independent CI verification

The application-session/catch-up stage passes342 local JUnit tests, portable package
checks, repeated JAR comparison, native ENet transport and native application loader
probes. The updated independent CI run is pending publication/observation.

The workflow still builds one Ubuntu artifact for diagnostic smoke on Linux,
Windows and macOS, runs component/UDP tests on each, and has a separate Linux native
oracle job. That job now additionally runs the composed loader/password/journal/
roster/reload probe. The native binaries are development oracles only.

Previous ENet/loader checkpoint: all five jobs passed for
`93f2ea2e934ffd776c0c01b25d3594c658cf1acb`:
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38667711995

No CI or fixture probe establishes original-client gameplay or production readiness.
