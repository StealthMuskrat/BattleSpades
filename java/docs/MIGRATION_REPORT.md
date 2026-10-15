# Java 25 migration report — initial implementation

Date: 2026-10-15 UTC. **Incomplete migration; not release-ready or playable.**
This report records actual implementation and tests. It does not mark the user's
strict completion criteria or milestones B–F as satisfied.

## Repository and provenance

- Working repository: https://github.com/StealthMuskrat/BattleSpades
- Branch: `port/java25`; default branch and upstream repository were not modified.
- Behavioral source commit: `6216b771282d2d349ccd67ee58c378f035c83553`.
- Published Java implementation commit: `6a4dc5eb52d96812dd9ae565f42de4746b414b1b`.
- Verified implementation tree: `9b9130812b99db91f7786bdb33e1ba700655fc79`.
- Original production Python/Cython source is preserved as the reference.

Direct shell `git push` failed because no CLI GitHub credentials were available.
The connected GitHub app published blobs/tree/commit and advanced only the specified
branch, with an expected-head lease. The server-created tree SHA exactly matched
the tested local Git tree. Public Git fetch verified the resulting branch head.
A later documentation commit may be the branch tip; the implementation SHA above
identifies the tested source tree independently of this report's own commit.

## Completed components

Java25 project under `java/`, Gradle9.1.0 wrapper with ZIP SHA-256 validation,
Java25 toolchain/release, pinned/locked dependencies and verification metadata,
JUnit Jupiter component/integration tasks, portable-dependency/package checks and
reproducible executable diagnostic JAR.

Binary fields, little-endian application serialization, 57 generated typed simple
packet codecs plus ClientData/WorldUpdate/CreateEntity/Damage (61 distinct IDs),
source-specific fixed conversions, literal LZF encoder/full bounded decoder,
application frame handling and inbound ticket XOR. Handwritten entity/snapshot
code preserves separate tool/state fields, signed turret IDs and no-pickup sentinel.

Pure Java ENet command envelopes, optional compressed command region, adaptive
order-2 range coder, modular sequences, bounded fragment-group assembler, standalone
CONNECT/VERIFY_CONNECT/ACK phase and portable nonblocking UDP. These components
do not constitute a complete ENet peer/host or reliable delivery engine.

VXL structural validation, classic64/short-map normalization, solid/fill/color/floor
loading, source CRC and MapSync column serialization. Standard/legacy KV6 layout
codecs and palette-tail retention. No terrain mutation/collapse/editor is implied.
Bounded fixed-step scheduling core retains source catch-up policy, drains messages
before steps, enforces ownership and calls replication at 30 Hz for 60 Hz steps.
Core TOML fields are validated; raw other settings are retained for later ports.

Structural source audit: 1,164 tracked reference files, all module mappings and
file/symbol/import/document-heading inventories. This is **not** a completed
behavioral audit of every one of approximately 277,000 source lines.

## Actual test outcomes

| Verification | Actual result |
|---|---|
| Java compile/test/check/build through committed wrapper | PASS on Linux, Java25 |
| JUnit unit/component tests | 249 passed, 0 failed/errors/skipped |
| JUnit real UDP loopback integration | 1 passed, 0 failed/errors/skipped |
| Simple source packet fixtures | 171 vectors across 57 types |
| ClientData/source complex packet fixtures | 4 ClientData + 3 complex vectors |
| LZF source vectors | 12 sizes, plus overlaps, malformed and 10,000-case fuzz |
| ENet range coder | 30 original C encode/decode vectors, including 32KiB model reset/rescale cases; 1,000-case bounded fuzz |
| VXL | 6 source-generated fixtures, 3,264 solidity/color samples and source MapSync column bytes |
| KV6 | standard/legacy byte roundtrip and malformed bounds |
| Native pyenet -> Java connection phase | native CONNECT event and Java VERIFY_CONNECT ACK received, protocol168, one channel, range coder enabled |
| Root config and version JAR smoke | exit0; config output explicitly limits validation to core settings |
| Normal gameplay startup | intentional exit2: migration incomplete; no playable service is advertised |
| Package inspection | zero native libraries, Python modules or support executables; Main.class major69 (Java25) |
| Rebuilt JAR comparison | identical SHA-256 in two local builds |
| Original game client / multiplayer / full map sync | NOT RUN; no supported game-client session provided, implementation also incomplete |
| Complete Java physics/gameplay differential regression | NOT IMPLEMENTED |
| 50-player capacity / 15-minute game soak | NOT EVALUATED; no functioning Java game server yet |

Golden bytes were produced by executing the original compiled source, not by the
Java implementation. `src/test/resources/golden/provenance.json` records hashes
and oracle/generator provenance. Negative fixed conversion is asymmetric in the
source, so tests compare actual source encoding rather than imposing identity
roundtrip where the source itself does not provide one.

The reference Python full suite stopped with two Tk collection errors. Excluding
those two modules collected 11,795 tests; an interrupted execution reached 1,087
pass symbols and 3 failures, not a complete baseline. The three bot-breach failures
were reproduced independently (1 passed, 3 failed: missing observer.can_dig).
A focused reference compatibility run finished 79 passed, 1 failed because psutil
could not access process RSS without usable `/proc`. Full details, commands and
completed XML reports: [PYTHON_BASELINE.md](PYTHON_BASELINE.md).

## Commands and environment

Normal developer build from `java/`, with a JDK25 installed:

```sh
./gradlew clean check build
```

Windows: `gradlew.bat clean check build`. Output: `build/libs/BattleSpades.jar`.
Source fixture regeneration uses the original Python/Cython environment only for
development, as documented in [README.md](../README.md) and the generator scripts.
The reference was built with `CC=gcc CXX=g++ python setup.py build_ext --inplace`.

This Linux workspace needed a private Java temp directory, a verified downloaded
Temurin25.0.4.1 JDK, and `LD_LIBRARY_PATH` for that host's dynamic-library loader.
Java network dependency downloads could not reach Maven Central directly in the
sandbox, so artifacts were fetched with curl into a local Maven cache and supplied
by an **uncommitted local Gradle init script**. The committed wrapper was exercised
with the checksum-verified distribution preseeded in its user cache. The final
clean verification command was equivalent to:

```sh
./gradlew --no-daemon -Dorg.gradle.native=false \
  -Dorg.gradle.internal.instrumentation.agent=false \
  -I /workspace/scratch/041f2df87ada/local-maven.init.gradle clean check build
```

Host-specific adapters, downloaded toolchains, caches and native oracle binaries
are not production dependencies and are not committed. A first incomplete JDK
archive was rejected and replaced before any passing results were recorded.
The portable build uses normal Maven Central/Gradle HTTPS sources; GitHub Actions
is the independent check of that normal network/build path.

Runtime diagnostics from repository root:

```sh
java -jar java/build/libs/BattleSpades.jar --version
java -jar java/build/libs/BattleSpades.jar --check-config config.toml
```

The requested final hosting command `java -jar BattleSpades.jar` is not functional
for gameplay at this stage. It reports missing ENet sessions/map sync/gameplay
and exits2 rather than pretending to host players.

## Artifact and dependencies

JAR: 870,943 bytes.
SHA-256: `a3053fcdd85c6accbba9750cf0ae6e94e20b6b8487f36b88bc8e8ffe5184f985`.
Repeated forced JAR builds produced identical bytes. This verifies local packaging
reproducibility, not gameplay portability or cross-JDK binary identity.

Production graph: tomlj1.1.1 (Apache2), ANTLR Java runtime4.11.1 (BSD3),
checker-qual3.21.2 (MIT). Development: JUnit Jupiter5.11.4 / Platform1.11.4,
OpenTest4J1.3.0, API Guardian1.1.2; Gradle9.1.0. Lock/verification files identify
the full graph. Individual license texts, root AGPL license and notices are
packaged. The altered ENet range-coder port retains its MIT attribution.
No proprietary client assets were added.

## Platform and CI status

- Linux x86_64: component build/tests, UDP, native ENet handshake, config and JAR
  smoke verified. Full game functionality is not verified on Linux.
- Windows/macOS: no local execution environment; independent same-artifact smoke and component/UDP CI **passed**. This does
  not verify gameplay there.
- Workflow: `.github/workflows/java25.yml`. One Ubuntu-built JAR is downloaded and
  smoke-tested unchanged on Linux/Windows/macOS. Each runner also runs component
  and UDP tests. No public release was created.

Initial CI run: https://github.com/StealthMuskrat/BattleSpades/actions/runs/38662913502
Final observation: **all four jobs succeeded**, including same-artifact diagnostic
startup/config smoke and component/UDP tests on Linux, Windows and macOS.
See [CI_STATUS.md](CI_STATUS.md) for exact scope; full gameplay remains unverified.

## Performance, missing work and readiness

No Java gameplay CPU/heap/GC/tick-p99/throughput/capacity measurements exist yet.
The source runbook confirms 50 players, >=58Hz, overall tick-p99 <=12ms, zero
runtime gameplay drops and a 900-second soak. A scheduler-only benchmark would
not evaluate this workload, so it was not substituted for the real gate.

The full backlog is [FEATURE_PARITY.md](FEATURE_PARITY.md). Principal missing work:
complete ENet reliable/unreliable/unsequenced peer lifecycle and congestion,
application packets/dispatch and loading/map journals, complete configuration,
original numerical movement/collision/projectiles, player/combat/terrain lifecycle,
every game mode, bot AI, creator/tutorial, Java plugins, admin, persistence,
query/discovery and portable hosting. Native-only Steam SDK/relay alternatives
and SQLite persistence compatibility remain unresolved.

**Release-readiness: NO.** This is a verified initial port and compatibility
foundation, not a complete server migration. No original-client compatibility,
playable round, full feature parity, capacity or cross-platform gameplay claim
is made. Continue implementation from the ordered backlog; retain the original
server until every strict completion gate has actual evidence.
