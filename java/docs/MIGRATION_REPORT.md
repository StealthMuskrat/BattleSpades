# Java 25 migration report

Date: 2026-10-15 UTC. **Incomplete migration; not playable or release-ready.**
This checkpoint implements ENet delivery and loader components. It does not satisfy
full original-client compatibility, feature parity or milestones B–F.

## Repository and provenance

- Fork: https://github.com/StealthMuskrat/BattleSpades
- Branch: `port/java25`; upstream and the fork's default branch are unchanged.
- Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`.
- Previous foundation: `6a4dc5eb52d96812dd9ae565f42de4746b414b1b`.
- Current implementation: `93f2ea2e934ffd776c0c01b25d3594c658cf1acb`.
- Tested/published tree: `86aa49fea0b4b27a652477a0dce5631c52468766`.
- Independent execution: see [CI_STATUS.md](CI_STATUS.md).
- Original production Python/Cython source remains available and unchanged.

The connected GitHub app publishes the tested tree with an expected-head lease;
local/remote tree equality is checked after publication. CLI push credentials are
unavailable. No release, default-branch change or upstream change is made.

## Implemented components

The Java 25/Gradle 9.1 project includes the committed checksum-pinned wrapper,
locked/verified pure-Java dependencies, an executable diagnostic JAR, JUnit tasks,
portable packaging checks and the fixed-step 60/30 Hz scheduling core. The scheduler
has no gameplay implementation yet. Core TOML validation retains other raw settings
for their later domain ports.

Protocol coverage is now **67 distinct IDs**: 57 generated straight-line codecs,
ClientData, WorldUpdate, CreateEntity, Damage, InitialInfo, StateData, MapSyncStart,
MapDataChunk, MapSyncChunk and SteamSessionTicket. Source fixtures verify unsigned
query ports, RGBA ground colors, counted loadouts/rules, 373-entry prefab catalogs,
embedded entities and ZYX fixed vectors. MapSyncStart is the bare ID byte.
Binary fields, asymmetric source fixed conversions, bounded LZF, outbound framing
and inbound ticket XOR remain implemented.

A portable, owner-confined ENet server host/peer now provides CONNECT verification,
protocol rejection, reliable delivery and ACKs, bounded pending/in-flight queues,
reordering and duplicate suppression, reliable/unreliable fragment groups,
unsequenced windows, retry/timeout timers, RTT-based throttling, send windows,
pings, graceful/immediate disconnect and session-safe reconnect. Socket enqueue
backpressure does not discard reliable data; host shutdown frees peer queues even
when an application retains the peer handle. Range coding matches original C
fixtures. Host bandwidth-epoch redistribution is still pending; the default host
advertises zero/unlimited bandwidth, as the original server does. This is a tested
server transport component, not a claim of complete ENet certification or gameplay.

Immutable map snapshots freeze canonical dirty columns and revision. Full transfer
preserves original compact spans and applies source height shifts only to base
columns. Small maps use canonical column fallback. Delta ordering follows the
source; zlib level6 streams are split into 1024-byte chunks with explicit byte limits.
Tests compare uncompressed records to source hashes rather than requiring identical
zlib output from every JDK. The transfer cursor preserves the server's CRC,
five-second validation timeout,100 ms loader delay, full/auto decision, packet
prefixes, progress rounding, generation cancellation and enqueue retry. Even an
empty delta sends start/end. Capture of mutable-world epochs, marker cleanup,
catch-up journals, identity and player reveal are **not wired into an application**.

Existing VXL loading/normalization/floor/fill/color and column serialization,
standard/legacy KV6 formats and the structural source inventories remain in place.
The 1164-file inventory is not a complete behavioral audit of 277000 source lines.

## Actual verification

| Check | Result |
|---|---|
| Committed wrapper `clean check build`, Java 25/Linux | PASS |
| Unit/component tests | 289 passed ; 0 failures/errors/skips |
| Real UDP integration tests | 3 passed ; 0 failures/errors/skips |
| Reliable sequence and sent-time wrap | 66000-message test PASS |
| Source application fixtures | 171 simple + 4 ClientData + 3 complex + 11 loading vectors |
| Source map-transfer records | 12 uncompressed record digests PASS: compact full, delta, overlays, centered small-map fallback |
| Source world formats | 6 VXL fixtures, 3264 source point samples/column bytes; standard/legacy KV6 |
| Compression | 12 LZF source vectors/10000-case fuzz; 30 native range vectors/1000-case fuzz |
| Native ENet client through real UDP relay | Nominal and 9%loss/delay/reorder/duplicate profiles PASS |
| Native protocol mismatch | 167 -> reason10;169 -> reason3; no peer allocated |
| Native reliable data | 80 ordered payloads up to 131072 bytes plus reconnect marker, byte-exact/no duplicates in each profile |
| Native unreliable/unsequenced/fragment data | Valid subsets, duplicate/corruption checks; delivery is intentionally not guaranteed |
| Native lifecycle | Verify retry, server disconnect reason 18, reconnect and old-session datagram injection PASS |
| JAR version/core config smoke | exit 0 |
| Normal gameplay launch | intentional exit 2, explicitly incomplete |
| Portable package scan | no native binaries, Python modules or support executables; class major 69 |
| Repeated JAR build | identical SHA-256 |
| Supported original game client / full late join | NOT RUN; application is incomplete |
| Full physics/gameplay regression, 50-player capacity,15-minute soak | NOT IMPLEMENTED/EVALUATED |

Exact JUnit suites: [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md).
Native metrics: [native-transport-results.json](native-transport-results.json).
Random seed is fixed; retry/delivery counts vary with OS scheduling. The native
probe is a development echo endpoint, not a supported game-client test.
Golden provenance/hashes: `src/test/resources/golden/provenance.json`.

The original Python suite had two Tk collection errors. Excluding those collected
11795 tests; an interrupted run reached 1087 pass symbols and 3 failures. A bot
breach file independently reproduced 1 pass/3 failures (missing observer.can_dig).
The focused reference suite completed 79 passed/1 environment failure (psutil RSS
without usable `/proc`). This is not a passing complete reference baseline.
See [PYTHON_BASELINE.md](PYTHON_BASELINE.md).

## Build and development oracles

From `java/`, with a JDK 25:

```sh
./gradlew clean check build
```

Windows: `gradlew.bat clean check build`. No Python/native build is needed for the
Java application or committed golden tests. The development-only Linux native
oracle additionally uses Python 3.12, Cython 3.0.12, setuptools 80.9.0 and a C compiler:

```sh
python java/tools/build_reference_enet.py
python java/tools/test_native_transport.py
```

Run those two commands from the repository root after `integrationTestClasses`.
The native CI job builds only the vendored pyenet test oracle, outside production.
Original compiled-source fixture regeneration is described in [README.md](../README.md).

Local sandbox builds use a verified Temurin 25.0.4.1 JDK, private Java temp directory,
loader path, checksum-verified preseeded Gradle distribution and an uncommitted
local Maven repository init script because direct Java dependency downloads are
blocked here. The portable committed build uses ordinary Maven Central/Gradle
HTTPS; independent CI verifies that path. Caches, oracle binaries and host adapters
are not committed or application requirements.

JAR: 937171 bytes. SHA-256:
`1ade4eecc5651e6ec6307a8a875eeb7ffc92e6059d973fba036a0ba151148a15`.
This proves repeated local packaging, not cross-JDK byte identity or gameplay.
Production dependencies remain tomlj 1.1.1, ANTLR Java runtime 4.11.1 and
checker-qual 3.21.2. Lock/checksum files and packaged license texts retain attribution.
No proprietary client assets were added.

## Independent platform verification

All five jobs passed in [CI run 38667711995](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38667711995):
clean Ubuntu build, Linux native ENet interoperability, and same-artifact diagnostic
smoke plus component/UDP tests on Linux, macOS and Windows. This does not verify
original-client gameplay on those platforms. See [CI_STATUS.md](CI_STATUS.md).

## Remaining work and readiness

[FEATURE_PARITY.md](FEATURE_PARITY.md) records exact scope and ordered backlog.
Next: integrate bounded transport dispatch, identity/password/ticket handling,
configuration/rules and live map epoch/journal/reveal gates. Then port original
strict-float movement/collision, terrain mutations and player/combat/mode lifecycle.
All modes, bots, creator/tutorial, plugins, admin, persistence and discovery remain.
Steam SDK/relay alternatives and SQLite compatibility are unresolved under the
pure-Java constraint. Full bandwidth redistribution/capacity need additional work.

No gameplay tick-p99,CPU/heap/GC, 50-player or900-second soak measurements exist.
Scheduler or echo throughput is not substituted for the documented game workload.
Cross-platform component/JAR smoke is separate from game compatibility.

**Release readiness: NO.** Keep the reference server for actual gameplay.
