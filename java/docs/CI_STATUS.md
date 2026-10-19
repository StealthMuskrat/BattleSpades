# Current production-assembly verification

Local Java check/build passes **6,263 component + 40 integration = 6,303 tests**, with
zero failures, errors or skips across 107 suites. All **350 focused original-source
regressions pass** on a fresh run. Seven new component checks cover three shared-stream
source spawn traces, admission/score/team/respawn ownership, TDM/Arena installation and
rollback, shutdown and reliable roster visibility. One four-peer real UDP check uses the
production Arena assembly and asynchronous frame pump. Existing policy traces remain
in use; an interior spawn trace that duplicated the dry-map result was removed before
publication. Earlier broad test reductions remain intact.

The new intact-source spawn generator reproduces both outputs byte-for-byte, with 18
positions and shared random continuations across three traces. All 262 preceding frozen
paths are unchanged: 264 checked paths and 77 configured CI generators. The preceding
76-generator/native-build evidence is reused locally; CI must rebuild/reproduce all 77.
Two CRC-valid local archives are byte-identical: 2014236 bytes, SHA-256
`fcfc54c4ef6e1cb064271c2d35dafad72a4826a8ce0b1a3a45b4cebd8e5e9f87`. Class major 69, packaging/licenses, version/config smokes and
all four fresh isolated native transport/packaged-loader profiles pass. The native loader
still uses FixtureScene. Normal hosting exits 2. No complete platform gameplay,
supported retail-client test or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `aea96236a466b48d5307941385f8eb01951f6eb2`, tested/published tree
`6c278e641b9d1808625e3d03b94901007879b4ef`, in [run 39180327801](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39180327801). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators, including the new
BaseMode/native-WorldManager/final-rescue spawn-chain traces. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and passed
source-built component/integration tests. Every final step completed successfully or
was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed.

The first impaired-native attempt returned disconnect reason 0 instead of required 18.
A fresh local repeat and the same-commit native CI rerun passed with assertions and
deadlines unchanged. The initial failure is retained in verification-results.json; no
transport fix or broader reliability conclusion is inferred from a successful retry. Full migration is
incomplete. The existing medpack/Zombie/VIP original-baseline discrepancies remain
unwaived; these focused passes do not establish a passing full original-source baseline.

See [PRODUCTION_ASSEMBLY.md](PRODUCTION_ASSEMBLY.md). Previous checkpoint: [Arena and early audio](ARENA_ROUNDS.md).
