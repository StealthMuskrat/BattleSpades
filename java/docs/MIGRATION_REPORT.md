# Java 25 migration report

Date: 2026-10-15 UTC. **Incomplete migration; not playable or release-ready.**
The current stage adds source-exact player movement and world-query components.
Configured scenes, live player lifecycle/physics integration and modes remain
pending. The ordinary JAR launcher deliberately returns exit 2 for hosting.

## Repository and provenance

- Fork: https://github.com/StealthMuskrat/BattleSpades
- Branch: `port/java25`; upstream/default branch remain unchanged.
- Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`.
- Previous application implementation: `a9c29842e67cc96eade2be3b2908044e44bd4e53`.
- Previous documented checkpoint: `e4a221097ce7596b473533c6d3dea29bcbda33a4`.
- Current implementation SHA/tree: recorded after publication in verification-results.json.
- Independent execution: [CI_STATUS.md](CI_STATUS.md).
- Original Python/Cython production source is retained unchanged.

The local checkout was restored from the published application checkpoint using
SHA-checked blobs, exact Git commit objects and a matching complete tree. GitHub
publication uses the connected app, with an expected-head lease; normal CLI push
credentials/direct GitHub networking are unavailable. No release is created.

## Current implementation

Java 25, pinned Gradle 9.1 wrapper, locked/checksummed pure-Java dependencies and
portable package checks remain in place. Production dependencies are tomlj 1.1.1,
ANTLR Java runtime 4.11.1 and checker-qual 3.21.2; licenses/attribution are packaged.

New physics components port original Player.update, boxclipmove, crouch clearance,
ordered peer collision, ledge drift, class tables and world queries. The mover
retains float stores and double intermediates, jump/flight/canopy behavior,
climb/glide/auto-crouch, water-row and boundary rules, lock-box ordering, corpse
flight and fall damage/slowdown. Caller-supplied ordered peer rows and the live
MutableWorld adapter preserve simulation ownership and immediate terrain reads.
Ray/cube/face queries retain source traversal ties. See [MOVEMENT_PHYSICS.md](MOVEMENT_PHYSICS.md)
for exact scope, query work bounds and integration requirements.

Previous components remain: 71 packet IDs, framing/LZF/XOR, range coder, bounded
ENet host/peers, ACK/retry/fragmentation/RTT/ordering, application loader/password/
capability/ID reservation, full/delta map snapshots and committed terrain/roster
journals with a first-input live gate. VXL/KV6 codecs, core/join TOML validation and
the 60/30 Hz scheduler are available. They do not provide the missing game domain.
See [APPLICATION_LOADING.md](APPLICATION_LOADING.md) and [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Actual verification

| Check | Current local result |
|---|---|
| Java 25 clean check/build | PASS |
| Unit/component tests | 459 passed; 0 failures/errors/skips |
| Real UDP integration tests | 5 passed; 0 failures/errors/skips |
| Original movement oracle | 118 cases, 13745 commands, 12924 ticks; exact exposed position/velocity bits, flags and landing returns |
| Original world-query oracle | 941 vectors; exact hit position bits, voxels/faces, lines and decisions |
| Fresh source oracle build | glm/VXL/world rebuilt with original strict float compiler flags; four new fixtures reproduce exactly |
| Focused original movement regressions | 1640 passed: test_retail_world_branches.py and test_retail_binary_movement.py |
| Live terrain collision adapter | Builds/removals affect the same mover; ownership checks pass |
| Existing protocol/session/map/ENet tests | PASS in full check |
| Native ENet transport/application oracles | Previous checkpoint passed; rerun in current CI |
| Version/core+join config smoke | Recorded with current artifact verification |
| Ordinary gameplay launch | Still deliberately disabled, exit 2 |
| Portable JAR inspection | No native/Python/test fixture classes; Java 25 major 69 |
| Repeated build and artifact hash | Exact details in verification-results.json |
| Current-tree independent platform CI | Pending; see CI_STATUS.md |
| Supported original game client / UI | NOT RUN |
| Complete mode, 50-player capacity, 15-minute soak | NOT IMPLEMENTED/EVALUATED |

Exact suites: [JAVA_TEST_RESULTS.md](JAVA_TEST_RESULTS.md). Fixture hashes, source
and generators: src/test/resources/golden/provenance.json. The original source
oracle is development-only; committed fixtures run in Java without Python. CI also
rebuilds original glm/VXL/world and requires identical regenerated fixture files.
The native loader probes still use a fixture scene, not a supported game client.

The earlier Python baseline remains incomplete: two Tk collection errors; 11795
tests collected with those modules excluded; interrupted run reached 1087 pass
symbols/3 failures. Bot breach reproduced 1 pass/3 failures, and an earlier focused
suite had 79 pass/1 psutil environment failure. The new 1640-test movement subset
passes; it does not replace a full reference run. See [PYTHON_BASELINE.md](PYTHON_BASELINE.md).

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

## Remaining work and readiness

Next: implement configured JoinScene, mode/rule/class data, identity and actual
player admission/spawn/death; connect the verified mover to input/equipment policy,
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
