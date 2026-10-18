# BattleSpades Java 25 migration

**Development components, not a playable server.** This executable deliberately
returns exit code 2 for normal game startup. Common scene/lifecycle and owned
movement/flight and WorldUpdate replication components exist; combat and concrete modes
remain incomplete.
Owned application sessions and live catch-up are tested with a domain fixture.
See [FEATURE_PARITY.md](docs/FEATURE_PARITY.md) and the
[migration plan](../JAVA25_MIGRATION_PLAN.md). Do not deploy this JAR as a game server.

## Build

Install a Java **25 JDK**, set JAVA_HOME to it, then from `java/`:

```sh
./gradlew clean check build
```

Windows PowerShell:

```powershell
.\gradlew.bat clean check build
```

The checked-in wrapper pins Gradle 9.1.0 (official Java 25 support), validates its
ZIP SHA-256, and locks dependencies with SHA-256 verification metadata. Normal
builds use Maven Central. No Python, C compiler, native ENet, JNI/JNA, preview
features, OS-specific executable or support library is required by the Java JAR.
JDK/Gradle's own runtime/build internals are not application dependencies.

Output: `build/libs/BattleSpades.jar`. From the repository root:

```sh
java -jar java/build/libs/BattleSpades.jar --version
java -jar java/build/libs/BattleSpades.jar --check-config config.toml
```

`java -jar BattleSpades.jar` will be the final hosting command once parity is
implemented. Today it fails explicitly with the incomplete-migration message.

## Current components

Medpack and radar behaviors now preserve shared healing cooldowns, per-pack uses,
live radar fuse and idempotent owner/team teardown. Unchanged-source comparisons
and a three-client UDP test cover healing, late replay and owned-shot cleanup.
Full placement/mode/gameplay assembly remains pending; see
[CARE_RADAR.md](docs/CARE_RADAR.md).

Shared blasts now compose native/exact terrain delivery, accepted player/entity
damage, dense-input predicted pushes and timed/mine/C4/grave behavior. A real
three-client owned-shot chain test covers instigator credit and known-causer
fallback. Placement policy, remaining devices and complete gameplay integration
remain pending. See [BLAST_RUNTIME.md](docs/BLAST_RUNTIME.md).

The concrete entity registry now preserves uint16 allocation, source support/tick/
touch/respawn callback order, base damage/destruction and per-scene visibility.
New source traces and a three-client owned-shot UDP test cover the component;
concrete pickups/devices/projectile/corpse and full gameplay integration remain
pending. See [ENTITY_REGISTRY.md](docs/ENTITY_REGISTRY.md).

Bounded terrain repair scheduling now preserves regular/collapse lane order,
quiet periods, current canonical health and per-recipient reliable sends. See
[TERRAIN_REPAIR.md](docs/TERRAIN_REPAIR.md) for evidence and remaining integration.

Terrain damage now composes owned health/shades, seeded footprints, melee digging,
collapse and late-join health reveal, with 10,483 new source operations and a real
three-client UDP test. Required concrete gameplay services remain pending; see
[TERRAIN_DAMAGE.md](docs/TERRAIN_DAMAGE.md).

The owned shot commit now composes validation, cadence, seeded pellets, target
rewind and accepted player damage/death, with 540,416 additional source operations
and two additional real UDP tests. Concrete terrain/entity/corpse effects and
mode/scoring domains remain required; see [COMBAT_RUNTIME.md](docs/COMBAT_RUNTIME.md).

- Typed codecs for 77 distinct application packet IDs: 57 straight-line source
  definitions plus ClientData, WorldUpdate, ShootPacket, ShootResponse, HitEntity, CreateEntity, Damage, InitialInfo,
  StateData, MapSyncStart, both map chunks, SteamSessionTicket, class loadouts,
  CreatePlayer, ExistingPlayer and actual retail health-state packets.
- Little-endian primitives, signed/unsigned values, UTF-8 strings, BGR colors,
  asymmetric signed-magnitude 1/64, orientation and quarter-unit conversions.
- Source-equivalent LZF framing and inbound ticket XOR.
- ENet network-endian command envelope, range coding, modular sequences,
  bounded peer/host delivery, ACK/retransmission timers, RTT throttling, sequence
  windows, fragment groups, lifecycle/disconnect and reconnect.
- Pure Java nonblocking UDP endpoint; real loopback integration tests.
- Immutable full/delta map compression and a bounded transfer cursor with server CRC,
  five-second validation timeout, loader delay, generation cancellation and prefixes.
- VXL loader, classic64 normalization, floor/fill behavior and MapSync column
  serialization. Standard/legacy KV6 decoding and byte-preserving serialization.
- Composed ENet application sessions, password/XOR/capability handling, promised IDs,
  asynchronous snapshots, committed cell journals and concrete roster life catch-up.
- Core/join/replication TOML validation and an owned 60 Hz scheduling core with source-cadenced replication.
- All 102 Match Lobby rules, stock selection for all 18 classes and configured
  InitialInfo/StateData builders bound to sessions. Rule-adjusted movement uses
  the same scale as the menus. See [configured scene evidence and limits](docs/CONFIGURED_SCENES.md).
- Unicode-safe retail names and common pre-life team/class checks, bound before
  configured admission. See [source comparisons and remaining identity/lifecycle work](docs/PLAYER_ADMISSION.md).
- Common Character creation/death/respawn, fresh world-bound movers, life inventory
  and quiet roster repair behind explicit mode/input/effect ports. See
  [lifecycle evidence and remaining integration](docs/PLAYER_LIFECYCLE.md).
- Accepted damage/healing, contribution history and recent-enemy/blast attribution,
  connected to life/death and ordered reliable HP delivery. See
  [health evidence and remaining combat work](docs/PLAYER_HEALTH.md).
- ClientData receive chronology, bounded label/action histories, one-frame phase
  selection/loss salvage and source action-cadence gates. See
  [input evidence and required gameplay integration](docs/PLAYER_INPUT.md).
- Authoritative no-flight infantry steps, source starvation/backlog policy,
  labelled impulses, eye history and fall-health integration. See
  [infantry evidence and remaining loop/equipment work](docs/INFANTRY_SIMULATION.md).
- Connected jetpack fuel/thrust and parachute deployment/landing timelines,
  negotiated flight profiles and real ENet RTT input. See
  [flight evidence and owner replication limits](docs/PLAYER_FLIGHT.md).
- Owned WorldUpdate cadence, split owner/observer delivery, causal owner history and
  actual ENet lifecycle release barriers. See [replication evidence and domain limits](docs/WORLD_REPLICATION.md).
- Source gun/reload cadence and wallets, own-frame seeded pellets, read-only target
  rewind and reliable owned reload messages. See [combat timing evidence and remaining hit validation](docs/COMBAT_TIMING.md).
- Layered inert map metadata, mode/UGC layout assembly and stock catalogue filtering,
  connected to scene packets and spawn geometry. See [metadata evidence and limits](docs/MAP_METADATA.md).
- Authored spawn geometry, threat-aware selection and bounded rescue, with source
  random/math parity and all-class settling traces. See [spawn evidence and integration limits](docs/SPAWNING.md).
- Source-exact player movement/collision, all 18 class profiles, flight/crouch/peer
  and landing behavior, plus ray/cube/face queries. See [movement evidence and
  integration limits](docs/MOVEMENT_PHYSICS.md):12924 original-engine ticks and 941
  query vectors; the live gameplay domain remains pending.

A codec does not mean the corresponding gameplay feature or packet handler exists.

## Configuration

Core validation covers server name, port, player limit, Steam identity bits,
60 Hz requirement, connection/queue/drain bounds and version-check flag. It retains
all other TOML values for later domain ports but does **not** claim to validate
or implement those domains. Join settings additionally validate password policy,
map sync mode, clock bias, reload grace and air-replay settings. Startup rejects enabled native `steam`/`steam_host`
integrations instead of silently ignoring them. The original root config remains
the behavioral reference. Match Lobby rules, common scene settings and admission
name/team settings are validated; overlays, fleet and full domain validation remain pending.

## Architecture and ownership

Packages include `app`, `config`, `protocol`, `transport.enet`, `session`,
`simulation` and `world`. The current application composition services UDP, sessions
and scene callbacks on one event-loop owner. Compression workers receive immutable
bytes only. The scheduling component accepts bounded external messages, which only
its simulation owner drains before a fixed step. Catch-up
retains at most two seconds and processes five steps per batch. Replication
callbacks run every second completed step. The class is a scheduling component,
not a complete server loop or gameplay implementation.

The model port retains strict floating-point expression semantics. Numerical
conversion source quirks are intentional, including negative fixed-point +0.5
truncation and ClientData orientation saturation remapping. Negative fixed
encode/decode is not an identity in the reference; tests compare the source's
actual bytes rather than replacing its rounding rules.

## Testing and reference fixtures

`./gradlew test integrationTest verifyPortable` runs JUnit and scans the packaged
artifact plus production source/dependency allowlist for prohibited native/runtime
content. Golden files are committed, so Java builds do not need Python or C.
`docs/native-transport-results.json` records separate native ENet delivery probes
with loss, delay, duplicates and reconnect. This is not an original game-client test.

To regenerate reference fixtures, build the original Python/Cython environment
at the recorded source commit, then run from repository root:

```sh
python java/tools/generate_simple_packets.py
python java/tools/generate_world_golden.py
python java/tools/generate_loading_golden.py
python java/tools/generate_session_golden.py
python java/tools/generate_golden.py
```

Run `generate_golden.py` last to refresh the full fixture provenance manifest.
Range fixtures use the development-only `range_oracle.c` adapter with the vendored
ENet C source and `generate_range_golden.py`. Native code is never linked into or
loaded by the Java application. See those scripts for exact invocation.

For the development-only native interoperability test on Linux, after building
`integrationTestClasses`, install Cython3.0.12 and setuptools80.9.0 in a Python3.12
test environment and run from the repository root:

```sh
python java/tools/build_reference_enet.py
python java/tools/test_native_transport.py
python java/tools/test_native_loader.py
```

Both probes are test endpoints, not gameplay hosting commands. The application
probe uses a source-fixture scene under `src/testSupport`, excluded from the JAR.
MutableWorld captures dirty columns and journals committed cells through the live
admission boundary. [APPLICATION_LOADING.md](docs/APPLICATION_LOADING.md) describes
the state machine, ownership, roster reconciliation and remaining domain work.
Marker cleanup, full repair-loop/domain integration, entity/corpse effects and complete modes remain pending. ENet host
bandwidth epoch redistribution remains pending; the current default host advertises
unlimited bandwidth, matching the original server configuration.

## Cross-platform verification and limitations

The workflow builds one JAR and downloads that same artifact to Windows, Linux
and macOS for startup/config smoke tests. It also runs component and UDP tests
on each platform, plus the native ENet impaired-relay oracle on Linux. It does not claim original-client movement/combat verification,
mode rounds, bot capacity, plugin behavior or a 15-minute game soak.

A Java-native plugin API has not been delivered; existing Python plugins do not
run in this JAR. Steam SDK discovery/authentication/relay replacements and SQLite
format migration remain unresolved under the no-native constraint. Complete construction/projectile terrain integration, gameplay, bot AI, creator/tutorial and administration
are still in the backlog. No release-readiness or performance-parity claim is made.

## Troubleshooting

Use a JDK25 for Gradle and Java25 to run the JAR. A wrong JDK can cause a toolchain
or class-version error. Dependency downloads require access to Maven Central and
the Gradle distribution server; enterprise proxies must be configured in the local
Gradle user properties. Configure a writable Java temp directory when the host has
no `/tmp` equivalent. These host settings are not committed into production code.
Read the actual test reports under `build/reports/tests`, and do not count a failed
or interrupted reference suite as passing. See [MIGRATION_REPORT.md](docs/MIGRATION_REPORT.md).

License: AGPL-3.0-or-later for the port, with ENet-derived range coder under MIT.
Third-party notices and individual dependency license texts are packaged in the JAR.
