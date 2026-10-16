# Java 25 migration report

Date: 2026-10-16 UTC. **Incomplete migration; not playable or release-ready.**
The current stage adds ClientData receive chronology, one-frame phase/loss-salvage
selection and labelled action evidence, plus source action cadence. The lifecycle
records actual session input but the complete equipment/mover/WorldUpdate loop is
still pending. Hit authorization, firing/reload, detached projectiles, authenticated
identity and concrete modes remain pending. Ordinary hosting deliberately exits 2.

## Repository and provenance

- Fork: https://github.com/StealthMuskrat/BattleSpades
- Branch: `port/java25`; upstream/default branch remain unchanged.
- Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`.
- Previous movement implementation: `9cfe665bbb3340f2606a2486a3f337dc4c6f3386`.
- Previous documented checkpoint: `1f4755d6d212a5c125cf723c393221c029c5a8e8`.
- Previous configured-scene implementation: `9e8b320709b35d6cece3c5078ea59af246f4ccbb`.
- Previous configured-scene tree: `eadba7cd03e37328f9e8ce2cbdfc07e452697998`.
- Previous admission implementation: `4c9b6014a1d6aab45a7ee8ee3e9232693de0f855`.
- Previous admission tree: `2dcb997a71ff93209d754d657f7c8bc68c503843`.
- Previous spawn implementation: `6792f538924d0bbd52a62e0b1fa85d8ead0c5b5e`.
- Previous spawn tested/published tree: `5608362cd28586e9d89adef82719d4ac2b8235f6`.
- Metadata feature implementation: `091c3408bb23bfa13a04535a17476544eac110a3`.
- Previous metadata implementation, including strict JSON whitespace: `9b822d80e40fc390eea3fb471822a1fec0a878cb`.
- Previous metadata tested/published tree: `b4581018e5647ab630fd3b808dfae4577a3d15a7`.
- Previous lifecycle implementation: `d03cb25c420323ef6a94379f06de349cd58ded0e`.
- Previous lifecycle tested/published tree: `f46b2a5736cd9dc49afae067df58f35e1239212a`.
- Previous accepted-health implementation: `e9e06e5af17deb9cb4f939c9ddd9112ad18a9881`.
- Previous accepted-health tested/published tree: `8195a734f9d048246bb17da572a0f2bf829cd824`.
- Current input implementation: `7d0d68e8e3fda4974fe7ebc070e24f7833b74a25`.
- Current input tested tree: `e7c13efb769da9833c27e056f69df57832118a30`.
- Current input CI: [run 38830815361](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361); all five jobs passed.
- Exact job evidence and limitations: [CI_STATUS.md](CI_STATUS.md).
- Original Python/Cython production source is retained unchanged.

The clean checkout was cloned from the fork and matched the authoritative
21e5f91d52796069e9eb8f2a4c9de1385febc3c7 documentation HEAD and full tree.
CLI push lacks credentials; publication uses the connected GitHub app with an
expected-head lease and exact tested-tree comparison. No release is created.

## Current implementation

InputTimeline records bounded immutable frames and receive metadata, preserves the
fresh immediate-state gate, selects source-latched movement phases, salvages late
presses and retains connection action evidence across life resets. ActionClock
preserves label/arrival cadence and server-time token budgets. This does not bind
outer input policies, equipment, impulses, eye/lag history or WorldUpdate, and does
not authorize raw hit packets. Original methods execute unchanged for 14,234 input
and 26,234 cadence comparisons. See [PLAYER_INPUT.md](PLAYER_INPUT.md).

LifeRuntime now applies accepted connected-player/world damage and integer healing
to PlayerLife, with protection/mode/scale/rounding order, DamageHistory identity and
life evidence, proportional healing, source blast attribution and fall/transition
credit. Reliable HP and gameplay broadcasts share bounded ordering under transport
backpressure. Required observers receive pre-broadcast death contributions; full
awards/assist payouts and hit/action authorization remain pending. New comparisons
execute 1152 damage, 120 healing, 80 attribution and 832 stateful source operations.
See [PLAYER_HEALTH.md](PLAYER_HEALTH.md).

PlayerLife/LifeRuntime now compose common admission, fresh Character bodies, death
and wall-clock respawn scheduling behind required mode/identity/input/effect ports.
LifeInventory and KillFeed preserve source wallet grants, timing and kill banners.
PlayerRoster now distinguishes death, respawn and reused identities, preserving
source ledger order; dead-join owner repair crosses the real UDP loading gate.
See [PLAYER_LIFECYCLE.md](PLAYER_LIFECYCLE.md) for exact source comparisons and
remaining integration requirements. Ordinary hosting remains disabled.

MapMetadata/MetadataDocument read prioritized JSON/UGC/legacy sidecars into
immutable atmosphere, layout, objective/entity and catalogue data. MapAssembly
validates VXL format/source CRC before returning a new map; ConfiguredScene rejects
a different mode/world. Source comparisons cover 785 full metadata cases, 659
packet pairs and 4316 map pools, with independent canonical source hashes.
File-backed sidecars now drive the UDP terrain test and packaged native loader
including changed gravity after reload. See [MAP_METADATA.md](MAP_METADATA.md)
for strict loading bounds, literal grammar coverage and remaining gameplay scope.

SpawnGeometry/SpawnResolver now port authored floors/sea spawns, live body/support
checks, roof/pocket rejection, stable base anchors, threat scoring and bounded
rescue from invalid/raising mode proposals. MutableWorld supplies cached surface
scans and committed revision invalidation. SourceRandom/SpawnMath retain Python
3.12 seeded selection and compensated arithmetic. Typed raw positions reach
rescue before strict physics-vector construction. See [SPAWNING.md](SPAWNING.md)
for behavior and remaining layout/mode/Character boundaries.

PlayerNames/NameUnicode/AdmissionPolicy now run name allocation, common team
resolution/balancing and rule/mode class fallback before configured admission.
The reference Unicode 15 database is pinned independently of host JDK tables.
Source comparisons cover every Unicode scalar plus 18,568 name, 6,192 team and
14,256 class vectors. The binding still delegates actual life creation and mode
hooks; see [PLAYER_ADMISSION.md](PLAYER_ADMISSION.md). The preceding admission
checkpoint passed all five CI jobs. The preceding spawn checkpoint also passes all
five jobs, including source fixture regeneration and the same JAR on three OSes.

Java 25, pinned Gradle 9.1 wrapper, locked/checksummed pure-Java dependencies and
portable package checks remain in place. Production dependencies are tomlj 1.1.1,
ANTLR Java runtime 4.11.1 and checker-qual 3.21.2; licenses/attribution are packaged.

New GameRules/ClassCatalog/ModeCatalog and SceneConfig/ScenePackets/ConfiguredScene
components port all 102 Match Lobby definitions, stock atomic selection for all
18 classes, 43 mode metadata entries and actual config/map/team packet builders.
The binding uses the source amendment order and retains explicit domain callbacks
for verified identity, concrete mode hooks and spawn policy. Rule-adjusted profiles preserve source double
multiplication before native float stores; canonical gravity matches StateData.
The native loader now runs the packaged JAR with root config and production
metadata. Its earlier native-probe player domain remains a test fixture, excluded from the JAR;
the new UDP lifecycle test uses production LifeRuntime with explicit test ports.
See [CONFIGURED_SCENES.md](CONFIGURED_SCENES.md) for exact behavior and limits.

Existing physics components port original Player.update, boxclipmove, crouch clearance,
ordered peer collision, ledge drift, class tables and world queries. The mover
retains float stores and double intermediates, jump/flight/canopy behavior,
climb/glide/auto-crouch, water-row and boundary rules, lock-box ordering, corpse
flight and fall damage/slowdown. Caller-supplied ordered peer rows and the live
MutableWorld adapter preserve simulation ownership and immediate terrain reads.
Ray/cube/face queries retain source traversal ties. See [MOVEMENT_PHYSICS.md](MOVEMENT_PHYSICS.md)
for exact scope, query work bounds and integration requirements.

Previous components plus life codecs now cover 74 packet IDs, framing/LZF/XOR, range coder, bounded
ENet host/peers, ACK/retry/fragmentation/RTT/ordering, application loader/password/
capability/ID reservation, full/delta map snapshots and committed terrain/roster
journals with a first-input live gate. VXL/KV6 codecs, core/join TOML validation and
the 60/30 Hz scheduler are available. They do not provide the missing game domain.
See [APPLICATION_LOADING.md](APPLICATION_LOADING.md) and [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Actual verification

| Check | Current local result |
|---|---|
| Java 25 clean check/build | PASS |
| Unit/component tests | 4451 passed; 0 failures/errors/skips |
| Real UDP integration tests | 11 passed; 0 failures/errors/skips |
| ClientData / cadence source comparisons | 14,234 / 26,234 original operations; exact regeneration |
| Focused original input/cadence regressions | 31 passed |
| Accepted HP/history source comparisons | 1152 damage, 120 heal, 80 attribution and 832 stateful operations |
| Focused original health regressions | 46 passed; broader original audit 45 passed / 1 existing VIP failure |
| Common lifecycle source comparisons | 756 spawn/pre-spawn, 3624 inventory, 4000 kill-feed, 300 packet, 183 scheduler, 592 roster vectors |
| Focused original lifecycle/inventory regressions | 128 passed, zero skips |
| Layered metadata / scene packets / map pools | 785 metadata cases, 659 packet pairs, 4316 pool vectors; 332 input groups |
| Focused original metadata regressions | 71 passed, 4 missing retail .txtc skips |
| Spawn geometry / selection / arithmetic | 18,124 geometry, 822 event, 16,000 random, 3,064 hypot and 3,000 sum vectors |
| Spawn-to-mover settling | 8,640 exact source ticks across all 18 class profiles |
| Name normalization/allocation | 1,112,064 Unicode scalars and 18,568 source vectors |
| Pre-life team/class admission | 6,192 team and 14,256 class source vectors |
| Rule coercion / class selection | 1958 source coercion inputs +336 source selections |
| Configured scene builders | 99 source InitialInfo/StateData packet pairs, byte-for-byte |
| Rule-adjusted movement | 864 walk/sprint/sneak profiles,10368 source ticks; exact final state bits |
| Original movement oracle | 118 cases, 13745 commands, 12924 ticks; exact exposed position/velocity bits, flags and landing returns |
| Original world-query oracle | 941 vectors; exact hit position bits, voxels/faces, lines and decisions |
| Fresh source oracle build | bytes/packet/glm/VXL/world rebuilt with strict float flags; all new fixtures and production catalogs reproduce exactly |
| Focused original movement regressions | 1640 passed: test_retail_world_branches.py and test_retail_binary_movement.py |
| Live terrain collision adapter | Builds/removals affect the same mover; ownership checks pass |
| Existing protocol/session/map/ENet tests | PASS in full check |
| Native ENet transport/application oracles | Fresh local and CI transport/packaged loader nominal + impaired/private PASS |
| Version/core+join config smoke | Recorded with current artifact verification |
| Ordinary gameplay launch | Still deliberately disabled, exit 2 |
| Portable JAR inspection | No native/Python/test fixture classes; Java 25 major 69 |
| Repeated build and artifact hash | Exact details in verification-results.json |
| Current-tree independent platform CI | All five PASS: Ubuntu build, native/source oracle, Linux/Windows/macOS |
| Supported original game client / UI | NOT RUN |
| Complete mode, 50-player capacity, 15-minute soak | NOT IMPLEMENTED/EVALUATED |

Exact suites: [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md). Fixture hashes, source
and generators: src/test/resources/golden/provenance.json. The original source
oracle is development-only; committed fixtures run in Java without Python. CI also
rebuilds original bytes/packet/glm/VXL/world and requires identical regenerated
fixtures and production catalogs.
The native loader probes still use a fixture scene, not a supported game client.

The earlier Python baseline remains incomplete: two Tk collection errors; 11795
tests collected with those modules excluded; interrupted run reached 1087 pass
symbols/3 failures. Bot breach reproduced 1 pass/3 failures, and an earlier focused
suite had 79 pass/1 psutil environment failure. The 1640-test movement subset
passes; the preceding scene stage passed 78 focused rule/class/builder regressions.
The admission stage passed 60 focused name/mode-lifecycle/class regressions.
The spawn stage passes 42 focused retail-box/TDM/smart-spawn/spawn-lock regressions.
The metadata stage passes 71 focused tests with four missing retail .txtc skips.
The lifecycle stage passes 128 focused ammo/kill-feed/deployable/lifecycle/reset/handshake
tests with no skips. The health stage passes 46 focused original tests. Its broader
rules audit has 45 passes and one existing VIP-round termination failure, reproduced
in isolation (expected callback [3], observed []). Original server/shared/aoslib/
protocol/mode files remain unchanged; this reference discrepancy is retained,
not corrected in the Python source. These subsets do not replace a full reference run. See [PYTHON_BASELINE.md](PYTHON_BASELINE.md).

## Build and runtime

With JDK 25, from java/:

```sh
./gradlew clean check build
```

Windows: gradlew.bat clean check build. Artifact: build/libs/BattleSpades.jar.
No Python/native compiler is needed. From repository root:

```sh
java -jar java/build/libs/BattleSpades.jar --version
java -jar java/build/libs/BattleSpades.jar --check-config config.toml
```

Normal hosting remains disabled. Local sandbox builds use a verified Temurin 25
JDK, private temp/cache and local Maven adapter; these host helpers are uncommitted.
Independent CI uses ordinary Java 25 runners and wrapper/Maven resolution. Platform
smoke downloads one Ubuntu-built JAR; source tests additionally run on all three OSes.
Native development-only probes are described in APPLICATION_LOADING.md and
MOVEMENT_PHYSICS.md. No proprietary client assets are added.

Current input/cadence CI: [run 38830815361](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361), all five migration
jobs passed on `7d0d68e8e3fda4974fe7ebc070e24f7833b74a25`. It reproduces new input/cadence fixtures and
all earlier source gates, byte-compares its rebuilt archive, and smokes the same
uploaded JAR on three OSes. Complete gameplay remains unverified.

Previous accepted-health CI: [run38826339114](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38826339114), all five migration jobs passed.
It reproduces original health fixtures, verifies HP/death order through UDP,
byte-compares its rebuilt JAR and tests one uploaded artifact on three OSes.
The separately documented original VIP reference failure remains unresolved.

Previous lifecycle CI: [run38795898680](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38795898680), all five jobs passed.
It reproduces life fixtures/catalogs, runs the production-domain UDP lifecycle test,
rebuilds/byte-compares its archive and tests the same uploaded JAR on three OSes.

Previous metadata CI: [run38790040711](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38790040711), all five jobs passed.
It includes strict JSON whitespace regression coverage, source metadata/catalog
regeneration, packaged native sidecar/reload checks, reproducible packaging and
the same JAR on all three OSes. The initial metadata implementation also passed
all five jobs in [run38789238339](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38789238339).

Previous spawn CI: [run38784215231](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38784215231), all five jobs passed.
The source-oracle job reproduced all spawn fixtures/provenance without a diff,
alongside the earlier movement/query/scene/catalog/name/admission gates.
The build rebuilt and byte-compared its JAR before upload; all three OSes tested
that same artifact and passed their source/component/UDP suites.

Previous admission CI: [run38749518170](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38749518170), all five jobs passed.
The build rebuilt/byte-compared the JAR before upload. The source-oracle job
reproduced name/admission fixtures, provenance and the pinned Unicode table with
an empty diff, alongside all earlier movement/query/scene/catalog gates.
The preceding configured-scene implementation passed all five jobs in
[run38742439157](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38742439157),
including archive byte comparison, native probes and source regeneration.

The first configured-scene attempt,
run38741477499, passed the build/reproducibility and all three platform jobs, but
its source-oracle job failed when server/__init__ eagerly imported an unrelated
toml dependency. Policy exporters now isolate that launcher namespace and pass
with Python -S, preserving original policy/table behavior. The previous movement
checkpoint passed all five jobs in run38705329003.
Local JAR verification found truncated incremental archives despite reported
Gradle task success. Two final valid local builds matched exactly;
CI independently passed an explicit rebuild and byte comparison before upload.
The earlier local archive failures remain an environment limitation, not a waived
packaging check. Exact final SHA/size are in verification-results.json.

## Remaining work and readiness

Next: bind the input phase selector to outer pacing, equipment/impulse policies,
accepted-frame simulation and WorldUpdate; implement hit/action authorization; connect detached projectile actors and full scoring/award observers; wire
the common Character lifecycle to concrete modes/effects and authenticated identity,
and connect the verified mover and configured rule profile to input/equipment policy,
the owned 60 Hz loop, prediction history/anticheat and WorldUpdate construction.
Continue remaining packets and entity/projectile physics, terrain validation,
damage/shades/collapse, combat, all modes, bots, creator/tutorial, plugins, admin,
persistence and discovery. UGC pre-validation transfers and ENet bandwidth-epoch
redistribution remain pending. Native Steam alternatives and SQLite compatibility
remain unresolved under the pure-Java constraint.

The 1164-file structural inventory is not a complete behavioral audit of 277000
source lines. General GLM matrices/quaternions/transforms are not ported by the
movement Vector record. No full gameplay capacity, tick-p99, CPU/heap/GC or 900s
soak measurements exist. Original-client multiplayer remains a required gate.

**Release readiness: NO.** Keep the reference server for gameplay.
