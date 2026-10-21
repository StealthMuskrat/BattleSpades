# Current bot worker brain checkpoint

Implemented the complete worker decision loop and retained navigation execution, corridor/escape recovery, water crossing and landing, breach queues, class deployables, charge safety, objective excavation/repair and community mode actions. `BotWorker.simple` constructs the actual brain on the worker thread. Six grouped tests replay 1,366 unchanged-source observations, including 400 action frames across all twenty community modes.

**6,517 Java tests pass** (6,427 component in 134 suites + 90 integration in 7), zero failures/errors/skips. Strict Java 25 `check build`, portability and distributions pass. The CRC-valid JAR is 3763834 bytes, SHA-256 `e8029c7515d4f655780e183b8aade29000c1ed10cbe3aac304ac10f9c18b0b5e`, class major 69. Configuration/version and expected incomplete-launcher smokes pass. All eight original source directories and all 284 prior resources are unchanged. The three reproducible new resources bring the total to 287; CI configures 94 source generators. Local regeneration reproduced both new fixtures and the community catalog exactly. All four required CI jobs (build, native ENet, Linux and Windows) and all 53 final steps passed or were intentionally skipped for implementation `639eac64ce751afb588899b6a0de1c3a49c3ca49`, tree `5c79b206828544bf5bfb569581e981bf48d98564`, in [run 39604813233](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39604813233). The initial build hit the previously recorded standalone UDP map-reload timeout; one unchanged-code retry passed. No timeout assertion was changed and that intermittent issue is not claimed fixed. Native transport/loader, all 94 source generators and repeated archive comparison passed. No local-to-CI archive hash identity is claimed.

**Bot migration remains unfinished and live bots remain gated.** Full director perception/scheduling, live collision/movement/action motor, population policy and transition/startup integration remain necessary. Scripted replay positions do not establish native physics or live-client/capacity acceptance. No macOS job has run; macOS testing remains deferred until the entire migration is complete. The existing intermittent standalone UDP map-reload timeout is not claimed fixed. See [BOT_BRAIN.md](docs/BOT_BRAIN.md).

## Previous verified checkpoints

# Current bot gameplay gateway and director-state checkpoint

Bound bot proposals to ordinary combat, reload, construction, prefab, device and projectile services. Added shared human/bot slot leases, bot identity before spawn effects, fail-closed stale-occupant checks, bounded aim/gaze/key controllers and life/epoch/priority/task fences. The connected host regression covers firing cadence/ammo, reload, deferred palette construction, retirement and slot reuse. Original director methods supply 768 aim and 600 movement-key observations.

**6,511 Java tests pass** (6,421 component in 132 suites + 90 integration in 7 suites), zero failures/errors/skips. Strict Java 25 `check build`, portability and distributions pass. The CRC-valid local JAR is 3639035 bytes, SHA-256 `fa4741b4160ced912b2defc64ed4110060e6f30e100584e49de9af10da4e8fa2`, class major 69; configuration/version and expected incomplete-launcher smokes pass. All eight original source directories and all 283 prior resources are unchanged. The reproducible director fixture brings the count to 284, with 92 configured source generators.

All four CI jobs—build, native ENet, Linux and Windows—and all 53 final steps passed or were intentionally skipped for implementation `b867db6492e56868e134b7050d8db594fc4a489b`, tested tree `27d2e389d211a3b6ad1cb6c6332cd90c498dcb5a`, in [run 39598334595](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39598334595). This includes source regeneration, archive reproducibility, native transport/loader probes and platform checks. No macOS job ran. No local-to-CI archive hash identity is claimed.

**Bot migration is unfinished and live bots remain gated.** The concrete brain orchestration and navigation/community/gadget executors, full director perception/scheduling and collision/movement/action motor, population policy and transition/startup integration remain necessary. This checkpoint supplies owner components and host service boundaries; it does not establish supported-client/capacity acceptance, the full Python baseline or overall migration completion. The pre-existing intermittent standalone UDP map-reload timeout is not claimed fixed. See [BOT_GATEWAY.md](BOT_GATEWAY.md).

## Previous verified checkpoints

Migration CI policy: build, native ENet, Linux and Windows only. **Defer macOS testing until the full migration is complete**, then run macOS acceptance.

Current checkpoint: [bot horde and combat controller components](docs/BOT_LIVE.md). Local verification: 6,507 Java tests, 71 selected original source tests and all four required CI jobs pass. Live bots remain gated and the migration remains incomplete.

# Standard hosted voting checkpoint

Ranked rotation, map ballots and vote-kick policy now share the standard host lifecycle, admission and frame scheduler. See [HOSTED_VOTING.md](docs/HOSTED_VOTING.md). The broader migration remains incomplete.

# Hosted runtime-control checkpoint

Same-map restart/reset, teleport and persistent live netcode tuning now bind to hosted runtime owners. See [RUNTIME_CONTROLS.md](docs/RUNTIME_CONTROLS.md). The broader migration remains incomplete.

# Hosted server-control checkpoint

Map/mode requests, round ending, announcements, reconnect-safe fog, remaining time and safe team balance now bind to the real hosted owners. See [SERVER_CONTROLS.md](docs/SERVER_CONTROLS.md). The broader migration remains incomplete.

# BattleSpades Java 25 migration

The standalone Java world creator is available through `--ugc-host`, including native editor tools, authored objects/lights, previews, saves and reopen. See [WORLD_CREATION.md](docs/WORLD_CREATION.md) for launch options and verification limits. Offline human-only standard/hardcore hosting is available through `--serve` using the explicit [standalone profile](docs/STANDALONE_HOSTING.md). Bots, tutorial/community modes, online and fleet services remain incomplete and unsupported startup settings fail explicitly.

See [FEATURE_PARITY.md](docs/FEATURE_PARITY.md) and the [migration plan](../JAVA25_MIGRATION_PLAN.md).

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

The world-creation workflow now has a standalone launcher, complete builder/projectile/flare bindings, verified ownership API, canonical VXL/light metadata persistence and graceful shutdown. See [WORLD_CREATION.md](docs/WORLD_CREATION.md).

Zombie outbreak/round ownership and all ten Hardcore variants now share the concrete mode runtime, native role/loadout/movement state and real infection/respawn/catch-up owners. See [ZOMBIE_HARDCORE.md](docs/ZOMBIE_HARDCORE.md).

Demolition and VIP now have concrete terrain/base-health/build-lock/airstrike and boss/subround/respawn/sudden-death owners. See [DEMVIP_MODES.md](docs/DEMVIP_MODES.md).


Diamond Mine and Occupation now have concrete mining, cash-in, bomb fuse/interception/disposal, real blast and carried-state replay owners. See [CARRY_MODES.md](docs/CARRY_MODES.md).


The capture-zone batch adds Territory Control and Multi-Hill factories, shared resupply, live escape-watch eligibility, packet-106 territory HUD state and cancellable staggered objective airstrikes. See [ZONE_RUNTIME.md](docs/ZONE_RUNTIME.md) for scope and evidence. The reference baseline is now upstream `main` at `546c548`. Required achievement and production services remain explicit boundaries.


The latest batch adds generic kill/teamplay economy, teabag credit, complete
escape-watch detection/marker policy, and round countdown/timeout arbitration,
plus HUD builders and native marker/localized-message codecs. Two UDP compositions
bind these to actual lives, damage ledgers, terrain, objectives and late joins.
Full concrete mode/event/end/result integration remains required. See
[MATCH_FLOW.md](docs/MATCH_FLOW.md).

The preceding batch adds connection profile counters/events/activity sampling,
accepted combat award/assist evidence, and identity-gated objective score events.
Two real UDP compositions connect actual accepted damage/death and scoreboard
delivery. Concrete mode/event/presentation/result integration remains required.
See [SCORE_EVIDENCE.md](docs/SCORE_EVIDENCE.md).


The preceding batch adds TeamState, EscortTracker, ObjectiveGuard/TrapTimer,
RoundCombatStats and Scoreboard, plus native GameStats/ShowTextMessage codecs.
These share actual player identity, live canonical terrain and reliable delivery;
two new three-client UDP tests exercise objective recovery and end-round/late-replay
flows. Concrete mode, profile/result and full gameplay composition remain required.
See [MATCH_FOUNDATIONS.md](docs/MATCH_FOUNDATIONS.md).

ObjectiveDropRequests now validates decoded requests against the bound carrier,
awaits the explicit mode port and places generic fallback drops in the registry.
It preserves mode consumption and post-await carry/life ordering; owner execution
and outer completion handling remain required ports. Concrete objective modes,
dropped-object simulation and full gameplay integration remain pending. See
[OBJECTIVE_DROP_REQUESTS.md](docs/OBJECTIVE_DROP_REQUESTS.md).

ObjectiveCarry now preserves native carry/burden state, finite/radius-limited drop
vectors, throw-speed caps, spawn reset and reliable send-failure boundaries.
DropPickup(71) keeps binary64 tuples until fixed-point encoding. Dedicated carrier
reveal is an explicit composition port; concrete objective mode ownership and
dropped-object simulation remain pending. The request component is verified
separately. See
[OBJECTIVE_CARRY.md](docs/OBJECTIVE_CARRY.md).

MapResourceService now builds map-owned supplies and static lights from metadata
and the canonical owned world. Server VXL loading strips eligible chroma markers;
map sync includes their finalized columns, and flare restoration preserves the
original load-state/journal boundary. Required score, achievement, audio and
broadcast domains remain explicit; full gameplay hosting stays gated. See
[MAP_RESOURCES.md](docs/MAP_RESOURCES.md).

Physical crates now preserve live support settling, dry rescue, parachute descent,
refill and respawn. Source comparisons and a three-client UDP test check health,
ammo/blocks, late replay and reliable position changes. MapResourceService assembly
is verified separately above; full gameplay binding remains pending; see [PHYSICAL_CRATES.md](docs/PHYSICAL_CRATES.md).

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

Administration embedding, room-host claims and durable bans are documented in [ADMINISTRATION.md](docs/ADMINISTRATION.md). Ordinary gameplay startup remains gated pending the remaining service owners.

Durable round-result accounting, master submission, shutdown and SQLite import are documented in [RESULTS_PIPELINE.md](docs/RESULTS_PIPELINE.md).
