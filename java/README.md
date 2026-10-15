# BattleSpades Java 25 migration

**Development components, not a playable server.** This executable deliberately
returns exit code 2 for normal game startup. ENet delivery/session management,
application join/map transfer, physics and gameplay remain unimplemented.
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

- Typed codecs for 61 distinct application packet IDs: 57 straight-line source
  definitions plus ClientData, WorldUpdate, CreateEntity and Damage.
- Little-endian primitives, signed/unsigned values, UTF-8 strings, BGR colors,
  asymmetric signed-magnitude 1/64, orientation and quarter-unit conversions.
- Source-equivalent LZF framing and inbound ticket XOR.
- ENet network-endian command envelope, range coding, modular sequences,
  bounded fragment-group assembler, and standalone connection handshake phase.
- Pure Java nonblocking UDP endpoint; real loopback integration test.
- VXL loader, classic64 normalization, floor/fill behavior and MapSync column
  serialization. Standard/legacy KV6 decoding and byte-preserving serialization.
- Core TOML composition/validation and bounded, owned 60/30 Hz scheduling core.

A codec does not mean the corresponding gameplay feature or packet handler exists.

## Configuration

Core validation covers server name, port, player limit, Steam identity bits,
60 Hz requirement, connection/queue/drain bounds and version-check flag. It retains
all other TOML values for later domain ports but does **not** claim to validate
or implement those domains. Startup rejects enabled native `steam`/`steam_host`
integrations instead of silently ignoring them. The original root config remains
the behavioral reference. Full 102-rule, lobby, overlays and fleet validation is pending.

## Architecture and ownership

Packages: `app`, `config`, `protocol`, `transport`, `transport.enet`, `simulation`,
`world`. UDP ownership stays outside authoritative state. Producers offer bounded
messages; only the simulation owner drains them before a fixed step. Catch-up
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
`docs/native-handshake-result.json` records the separate native ENet connection
probe. This is not an original game-client compatibility result.

To regenerate reference fixtures, build the original Python/Cython environment
at the recorded source commit, then run from repository root:

```sh
python java/tools/generate_simple_packets.py
python java/tools/generate_golden.py
python java/tools/generate_world_golden.py
```

Run `generate_golden.py` last to refresh the full fixture provenance manifest.
Range fixtures use the development-only `range_oracle.c` adapter with the vendored
ENet C source and `generate_range_golden.py`. Native code is never linked into or
loaded by the Java application. See those scripts for exact invocation.

## Cross-platform verification and limitations

The workflow builds one JAR and downloads that same artifact to Windows, Linux
and macOS for startup/config smoke tests. It also runs component and UDP tests
on each platform. It does not claim original-client movement/combat verification,
mode rounds, bot capacity, plugin behavior or a 15-minute game soak.

A Java-native plugin API has not been delivered; existing Python plugins do not
run in this JAR. Steam SDK discovery/authentication/relay replacements and SQLite
format migration remain unresolved under the no-native constraint. Full terrain
mutation, collapse, physics, gameplay, bot AI, creator/tutorial and administration
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
