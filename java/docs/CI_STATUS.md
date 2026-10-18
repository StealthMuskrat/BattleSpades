# Current voting-policy verification

Local Java check/build passes **6,179 component + 37 integration = 6,216 tests**,
with zero failures, errors or skips across 100 suites. This batch adds 51 intact-source
scenario groups, two focused boundary tests and one real UDP integration scenario;
the existing retained-peer rollover composition is upgraded. **273 focused original-source
regressions pass**. The new generator reproduces both outputs byte-for-byte; all 254
prior frozen paths retain their exact hashes, for 256 checked paths. There are 73
configured CI generators. Unchanged prior 72-generator/native-build evidence is reused
locally; CI independently rebuilds/reproduces all 73.

Two CRC-valid local archives are byte-identical: 1911484 bytes,
SHA-256 `527dbe0f7151212c238739b89f8188978a64324946ad27f073c477e1faa0a3d1`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader still uses FixtureScene. Platform tests run
source-built component/integration classes and smoke the uploaded JAR. No local-to-CI
artifact hash identity or complete platform gameplay is claimed. Prior medpack/Zombie/
VIP original-baseline discrepancies remain unwaived; these focused passes do not
establish a passing full original-source baseline.

The initial [CI run 39170464457](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39170464457)
passed build, native, Ubuntu and Windows but failed macOS because the Unicode ordering
fixture tried to create a U+FFFF filename (Illegal byte sequence). It is retained as a
failed attempt, not waived. Valid private-use BMP filenames preserve the same astral/BMP
code-point-order assertion on all platforms; no scenario is skipped. Review also found
a source floating-point edge: equal human/bot counts in different roster orders can
cross a fit threshold. Two intact-source traces now distinguish those orders, and
MapRotation adds each roster weight in order rather than multiplying aggregate counts.
The corrected implementation is verified by a fresh full suite, repeated artifact,
native profiles and a new five-job CI run.

This checkpoint requires a new five-job CI run after publication.

See [VOTING_POLICY.md](VOTING_POLICY.md) for current scope and limits. Earlier evidence
is in [ROUND_MEDIA.md](ROUND_MEDIA.md), [PREPARED_HOST.md](PREPARED_HOST.md),
[TRANSITIONS.md](TRANSITIONS.md), [ROUND_RUNTIME.md](ROUND_RUNTIME.md) and [TDM_RUNTIME.md](TDM_RUNTIME.md).
