# Independent CI verification

The ENet/loader checkpoint has passed local Java25 `clean check build` (292 tests),
JAR/configuration smoke, portable packaging, repeated JAR comparison and native
ENet nominal/impaired relay runs. Its independent GitHub Actions run is pending
publication/observation; no new platform result is claimed yet.

The workflow now has five jobs: one Ubuntu build, the identical uploaded JAR plus
component/UDP tests on Linux,Windows,macOS, and a Linux-only development native
ENet oracle. The latter compiles a test peer; the Java JAR has no native dependency.

Previous foundation run (all four original jobs passed):
https://github.com/StealthMuskrat/BattleSpades/actions/runs/38662913502
Implementation:`6a4dc5eb52d96812dd9ae565f42de4746b414b1b`.

Neither the old nor the new workflow establishes original-client gameplay,
complete joins,physics,combat,modes,bots,capacity or production readiness.
