# Java test optimization

2026-10-18 UTC. This checkpoint changes test execution and fixture setup only.
Production sources, source generators, golden fixture bytes, workflows and all 33
integration tests are unchanged.

## Duplicate independent cases removed

| Cases | Before | Unique retained | Repeated executions removed |
|---|---:|---:|---:|
| Binary64 decimal rounding | 8,192 | 7,688 | 504 |
| Timeout/score winners | 512 | 36 | 476 |
| HUD builders and safe names | 1,024 | 72 | 952 |
| Terrain rays | 1,600 | 876 | 724 |
| Immutable snapshot transforms | 2,235 | 1,474 | 761 |
| Rule coercion | 1,958 | 1,780 | 178 |
| Total | 15,521 | 11,926 | 3,595 |

Deduplication compares exact input and expected-output strings. Rounding, winner
and HUD keys omit only the fixture row number; HUD keys additionally retain both
flags derived from that number. Conflicting expected outputs remain separate
cases. No numeric tolerance or failure assertion was relaxed. Golden files remain
unchanged so native regeneration still checks the complete source evidence.

The rounding, winner and HUD tests each use one loop with per-case diagnostics,
replacing 32, 8 and 16 artificial partitions. This removes 53 JUnit invocations;
the 3,595 removed fixture executions are cases inside tests, not 3,595 JUnit tests.
The suite now contains 5,912 component tests plus 33 integration tests (5,945 total).

## Repeated setup removed

The five match fixtures are decoded once per test worker and indexed by kind and
trace number, replacing 328 gzip reads and repeated whole-file parsing. Native
terrain edits stay interleaved with their original operations. All 224 stateful
trace selections were independently compared with the original filters: every
row and its order match. Stateful traces are never deduplicated, and each test
retains its own mutable gameplay harness. Admission tests reuse four immutable
rule variants, menu inputs and normalized selections while retaining all 14,256
mode/class policy rows and their assertions.

## Local verification

`clean check build --offline` exited 0 with 5,945 tests passing and no failures,
errors or skips; Java compilation retains `-Xlint:all -Werror`. All 86 test suites
remain present. Production archive: 1,731,239 bytes, SHA-256
`30495db15a5e695ccbff378c48e973f6e0306f0131d77e6b7b2c280d9b33e4d1`,
byte-identical to the previous checkpoint.

The five affected match suites measured 4.424s before and 1.238s after in the
available local JUnit results. These are single observations, not a benchmark:
all-suite reported time was 41.215s before and 43.334s after, so no whole-suite
speedup is claimed. Admission time was 3.946s before and 3.884s after; its complete
Unicode sweep remains intact.

All five jobs passed in [run 39145475640](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39145475640) for implementation `f14407e5596d3f2f8a3869e2ad1c8505970c3f58`,
tested tree `93a158fd85cadd67710ad5bac64e4ad2bde174f8`. Every final job step completed with success or an
intentional skip. CI regenerated the original fixtures and checked reproducible
archives; Ubuntu, Windows and macOS smoked one uploaded JAR and ran component/UDP
tests from source-built classes. No local-to-CI artifact hash identity is claimed.
The migration remains incomplete; prior original-source baseline failures remain
recorded and unwaived.
