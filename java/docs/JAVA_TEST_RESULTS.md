# Java test results

Local Java check/build passes **6,256 component + 39 integration = 6,295 tests**,
with zero failures, errors or skips across 106 suites. **350 focused original-source
regressions pass**. The batch adds 14 stateful native Arena/lifecycle traces, two seeded
join-audio traces, one grouped settings check, one audio identity/fatal/owner boundary,
one admission-audio backpressure boundary and one four-peer real UDP composition.
Existing TDM/media source fixtures and tests remain in use after lifecycle extraction.

The new intact-source generator reproduces both outputs byte-for-byte; all 260 earlier
frozen paths retain their hashes, for 262 checked paths and 76 configured CI generators.
Unchanged prior 75-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 76. The broad earlier test-optimization reductions remain intact;
no duplicate full-suite or extra UDP setup is added for shared BaseRoundMode behavior.

Two CRC-valid local archives are byte-identical: 1975623 bytes,
SHA-256 `0ac9955af9fff6bc9f33111dfafb589fe393642baf3f26ab223da53f26ffeb59`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

This checkpoint requires a new five-job CI run after publication.

See [ARENA_ROUNDS.md](ARENA_ROUNDS.md) for source scope and remaining domains.
