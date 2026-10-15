# Java 25 migration report

Date: 2026-10-15 UTC. **Incomplete migration; not playable or release-ready.**
This stage adds composed application sessions and live map catch-up. The ordinary
JAR launcher still refuses gameplay because actual game scenes, simulation and
modes are missing. Test fixtures are not shipped as game implementations.

## Repository and provenance

- Fork: https://github.com/StealthMuskrat/BattleSpades
- Branch: `port/java25`; upstream and the fork's default branch are unchanged.
- Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`.
- Prior ENet/loader implementation: `93f2ea2e934ffd776c0c01b25d3594c658cf1acb`.
- Current implementation commit, tested tree and CI execution: [CI_STATUS.md](CI_STATUS.md).
- Original production Python/Cython sources remain unchanged and available.

CLI push credentials are unavailable. The connected GitHub app publishes tested
Git objects with an expected-head lease; local/remote tree equality is checked by
public fetch. No release, upstream change or default-branch change is made.

## Current implementation

Java 25, Gradle 9.1, the checksum-pinned wrapper, locked/verified pure-Java graph,
portable package checks and executable diagnostic JAR remain in place. The
fixed-step 60/30 Hz scheduler is still a component; full gameplay is not attached.

**71 distinct application packet IDs** are implemented. This stage adds class
loadouts, CreatePlayer, ExistingPlayer and the actual retail BlockManagerState
repair format. Packet38 follows `server/prefab_actions.py`, not the reconstructed
Cython ServerBlockItem layout. Occupied rows are unsupported. The receive path
uses the source runtime ClientData variants and tolerant optional loadout tail.
Ticket capability trailers stay outside the XOR key; BSFP profile bytes match
source fixtures. Existing binary/LZF/framing, world-update and loader codecs remain.

`ApplicationHost` now composes ENet events with owned sessions and `JoinScene`
domain hooks. Password policy, key installation, capability gating, stable ID
reservation, CRC/loading, skybox/roster, admission, clock replies, first-input
reveal and scene replacement are connected. Real UDP tests use those production
classes. The provided scene is **test-only**; actual mode/rule/class/identity and
spawn/death implementations must be supplied by the continuing domain port.
Tickets currently implement XOR/capability behavior, **not Steam ownership auth**.

`MutableWorld` adds committed cell edits, opaque dynamic colors, exposed-surface
materialization, initial-health preservation, immutable snapshot capture and a
bounded coordinate journal. Replay coalesces final cells in first-occurrence order
and resumes partial enqueues. Its lease survives roster/reveal backpressure until
the live gate opens. Source-exact solid/paint/health and non-collapsing air repair
packets close the loading gap. Frozen air repairs are optional. Overflow fails the
join closed; disconnect/retirement release slots, tasks and leases. The seabed is
kept solid. Gameplay placement rules, damage/shade history and collapse are absent.

`PlayerRoster` reconciles concrete life tokens, palettes and removed IDs; it does
not mistake identical-looking lives for the same spawn. Known state is committed
only after a complete reliable plan is accepted. A reentrant scene change cannot
publish an old admission. Full death/corpse, entity and gameplay replication remain.

A bounded compression pool accepts immutable snapshots only; cancellation checks
record boundaries, and uncollected results still consume capacity. Core TOML
validation now includes join password/ranges, map-sync mode, reload grace, clock
bias and air-replay settings. The rest of the configuration/rules port is pending.

Existing ENet ACK/retransmission/windows/RTT throttling/session semantics, pure
Java range coder, VXL/KV6 formats and structural audit inventories remain verified.
ENet host bandwidth-epoch redistribution remains pending. The 1164-file structural
inventory is not a complete behavioral audit of 277000 source lines.

See [APPLICATION_LOADING.md](APPLICATION_LOADING.md) for ownership, ordering and
remaining integration boundaries.

## Actual verification

| Check | Result |
|---|---|
| Wrapper clean check/build, Java 25/Linux | PASS |
| Unit/component tests | 337 passed; 0 failures/errors/skips |
| Real UDP integration tests | 5 passed; 0 failures/errors/skips |
| Packet/flight fixtures added this stage | 4 packet layouts  +4 source profiles |
| Live ClientData runtime variants | 12 source vectors |
| Committed world mutation oracle | 8 operations, 792 solidity/color/health samples, 72 serialized columns |
| Existing packet/world/compression fixtures | PASS; provenance manifest retains complete hashes |
| Native ENet transport | Nominal + loss/reorder/duplicate/reconnect profiles PASS |
| Native composed application | Open/nominal + password-protected/impaired profiles PASS |
| Full map wire stream | 453 chunks; 262144 canonical records independently decoded/checked |
| CRC-matched delta | Zero chunks, with mandatory start/end |
| Concurrent loader identity / capacity | IDs 0 and 1 remain distinct; third peer refused with reason 4 before InitialInfo |
| Capability/XOR/password | Source profile bytes, encrypted/compressed input and private-server gating PASS |
| Live catch-up | Exact build/paint/initial health/removal and remote roster reconciliation PASS |
| Scene replacement | Same ENet/key/password state, fresh CRC handshake, no packet110 requirement PASS |
| Malformed peer isolation | Bad real UDP client refused; other loader continues |
| JAR version/core+join config smoke | exit 0 |
| Ordinary gameplay launch | deliberate exit 2, explicitly incomplete |
| Portable JAR inspection | no native binaries, Python or fixture scene/probe classes; class major 69 |
| Repeated local JAR build | identical SHA-256 |
| Original supported game client / game UI | NOT RUN |
| Complete physics, game round, 50-player capacity,15-minute soak | NOT IMPLEMENTED/EVALUATED |

Exact suites: [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md).
Native evidence: [native-transport-results.json](native-transport-results.json) and
[native-loader-results.json](native-loader-results.json). Random seeds are fixed;
retry/delivery counts can vary with scheduler timing. Native probes exercise
production protocol components with a fixture scene, not original-client gameplay.
Source fixture provenance: `src/test/resources/golden/provenance.json`.

The original Python baseline remains incomplete: two Tk collection errors;
11795 tests collected when excluding those modules; interrupted execution reached
1087 pass symbols/3 failures. Bot breach independently reproduced 1 pass/3 failures
(missing observer.can_dig). The focused compatibility suite completed 79 pass/1
psutil environment failure without usable `/proc`. These are not passing complete
reference results. See [PYTHON_BASELINE.md](PYTHON_BASELINE.md).

## Build and runtime evidence

From `java/`, with a JDK 25:

```sh
./gradlew clean check build
```

Windows: `gradlew.bat clean check build`. Output: `build/libs/BattleSpades.jar`.
No Python or C compiler is needed for the Java application or committed fixtures.
For development-only native interoperability, build `integrationTestClasses`, then
from repository root with Python 3.12/Cython 3.0.12/setuptools 80.9.0 and a C compiler:

```sh
python java/tools/build_reference_enet.py
python java/tools/test_native_transport.py
python java/tools/test_native_loader.py
```

`src/testSupport` contains source-fixture adapters only. It is compiled for tests
and both native probes, and is excluded from the application JAR. The CI native
job builds only the vendored pyenet oracle, outside production packaging.

Local builds use a verified Temurin 25.0.4.1 JDK, writable private temp directory,
loader path, verified cached Gradle ZIP and local Maven init script because direct
Java dependency downloads are blocked in this sandbox. The cache's missing wrapper
extraction and reference-venv interpreter link were restored from the same verified
local resources this turn. These adapters/caches are uncommitted; independent CI
uses ordinary wrapper/Maven HTTPS resolution and standard Java 25 runners.

JAR: 1011877 bytes. SHA-256:
`9b6c9f4b203ea1a21626a0e18eb58d5dc3bff6f117b8af88740e83d52362ed87`.
This verifies local repeatability, not cross-JDK byte identity or game portability.
Production dependencies remain tomlj 1.1.1, ANTLR Java runtime 4.11.1 and
checker-qual 3.21.2. Their checksums, licenses and ENet attribution remain packaged.
No proprietary client assets were added.

## Remaining work and readiness

[FEATURE_PARITY.md](FEATURE_PARITY.md) retains the full backlog. Next: implement
actual configured game scenes and mode/rule/class data, complete player identity
and lifecycle, and connect original strict-float movement/collision to the owned
60 Hz loop. Continue marker cleanup, terrain validation/damage/collapse, entities,
all game modes, bots, creator/tutorial, plugins, admin, persistence and discovery.
UGC pre-validation source transfers, full scene rollback and transport bandwidth
redistribution are not covered by this stage. Native Steam alternatives and SQLite
compatibility remain unresolved under the pure-Java constraint.

No game tick-p99, CPU/heap/GC, 50-player or 900-second soak measurements exist. Loader
fixtures and UDP tests are not substituted for that documented workload. An actual
supported original-client session remains a required compatibility gate.

**Release readiness: NO.** Keep the reference server for gameplay.
