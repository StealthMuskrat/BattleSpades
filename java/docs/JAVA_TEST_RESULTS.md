# Java test results

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

All five CI jobs are required for this checkpoint after publication. Full migration is
incomplete. The existing medpack/Zombie/VIP original-baseline discrepancies remain
unwaived; these focused passes do not establish a passing full original-source baseline.

See [PRODUCTION_ASSEMBLY.md](PRODUCTION_ASSEMBLY.md) for source scope and remaining work.
