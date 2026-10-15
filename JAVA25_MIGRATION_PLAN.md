# BattleSpades Java 25 migration plan

Reference: StealthMuskrat/BattleSpades `port/java25`, source commit
`6216b771282d2d349ccd67ee58c378f035c83553`. Original Python/Cython sources
remain unchanged and available for differential testing. License: AGPL-3.0-or-later;
retain LICENSE, LICENSING.md and THIRD_PARTY_NOTICES.md.

## Scope and audit evidence

This is a large, incomplete migration. The structural audit covers every tracked
file, its SHA-256, size, source symbols/imports and documentation headings in
`java/docs/source-inventory.json`. Every Python/Cython module and its proposed
Java destination is enumerated in `java/docs/SOURCE_INVENTORY.md`.
A structural inventory is not a completed behavioral audit of all 276,648 source
lines. Detailed behavioral review and golden tests must accompany each port.
The production server is not replaced until all compatibility gates pass.

Reviewed boundaries: docs/ARCHITECTURE.md, docs/PROTOCOL.md, docs/RUNBOOK.md,
docs/ADMIN_GUIDE.md, config.toml, fleet.toml, setup.py, requirements.txt,
pytest.ini, modes registry, shared bytes/packet core, connection framing,
ENet protocol header, VXL/KV6 loaders, world numerical constants, licensing.
Documentation and workflow inventory includes every additional tracked file.

## Feature inventory and package boundaries

| Source feature | Proposed Java package | Acceptance |
|---|---|---|
| run_server, tutorial, map creator, fleet launchers | app | lifecycle, shutdown, independent instances |
| server/config, lobby, game_rules, mode_data | config, catalog | TOML overlays, 102 rules, aliases, all validation |
| native pyenet + vendor ENet | transport.enet | pure Java wire compatibility including range coder |
| shared/bytes, shared/packet, protocol, handlers | protocol | exact ids, binary layouts and receive/send semantics |
| simulation_runtime, replication, match transitions | simulation, replication | 60 Hz ownership, bounded queues, 30 Hz snapshots |
| aoslib/world, shared/glm, player movement | physics | original fixtures, float rounding and update ordering |
| aoslib/vxl, world manager, collapse, map mutations | world | 512x512x240, raw-z conventions, collapse and join journal |
| aoslib/kv6, prefabs, ugc, map creator | assets, creator | both KV6 layouts, palettes, editing, atomic persistence |
| player, teams, classes, inventory, lifecycle | player | atomic life selections, respawn and roster ordering |
| combat, weapons, projectiles, deployables | combat | cadence, hit registration, damage, stock and terrain |
| objective modes, scores, votes, match lifecycle | mode | complete playable rounds, late join and reset |
| bot_ai, bot director, simple voxel planner | bot | fairness, perception, navigation, bounded workers |
| plugins + commands | plugin, admin | Java ServiceLoader lifecycle, isolated callbacks, commands |
| server GUI and tray | admin | portable management interface; original GUI not reused |
| LAN/A2S, master/revival, Steam, steam_host | discovery | preserve portable queries; native SDK optional gaps explicit |
| sqlite progress, bans, demos, result delivery | persistence | atomic durability, retry identity, format compatibility |
| logs, metrics, diagnostics, soak scripts | telemetry | bounded producer queues, profiling and accurate gates |

### Modes and capabilities

Registry: CTF, Classic CTF, TDM, Arena, VIP, Zombie, Multi Hill, Territory Control,
Diamond Mine, Demolition and Occupation. UGC/Map Creator and Tutorial have dedicated
launchers. Hardcore wraps the ten retail lobby modes. Twenty community variants
are registered, including retired-but-loadable variants: Block Hunt, False Wall,
Crumbling Run, Last Exit, Night Shift, Tunnel Rats, Bridge Chicken, Towerfall,
Floor Tax, Hot Potato, Lockdown, Paint Raid, Smugglers Run, One in the Spade,
Borrowed Arsenal, Reverse Siege, Kingmaker, Red Light Raid, Scrapyard Engineers,
Spadeball. Catalog aliases and revisions must remain configuration-compatible.
None is considered ported solely because its identifier exists.

## Dependency replacement inventory

| Existing dependency | Pure Java strategy |
|---|---|
| CPython, Cython, setuptools | Java 25 + Gradle 9.1.0 wrapper; no production Python |
| pyenet / ENet C | inspect pure Java candidates; otherwise direct ENet implementation under MIT attribution |
| native bytes/glm/packet/world/vxl/kv6 | Java primitives, IEEE float/double, standard buffers/collections |
| toml / tomllib / tomlkit | pinned tomlj and ANTLR Java parser; preserve raw settings; validation ported by domain |
| py_trees, pydot, pyparsing | Java behavior/tree planning with equivalent node lifecycle; optional diagnostic export |
| Recast/Detour C++ | production uses SimpleBotBrain/SimpleVoxelWorld; direct voxel planner in Java |
| Pillow | java.desktop ImageIO for PNG/JPEG |
| customtkinter, pystray, tkinter | portable management UI/API; no native tray requirement |
| SQLite via Python stdlib | investigate pure Java persistence; common sqlite-jdbc bundles JNI and is prohibited |
| Steam SDK / relay helpers / ctypes | no native production adapter; registration/auth/relay parity unresolved |
| pytest / pytest-asyncio | JUnit Jupiter + differential reference fixture generator (development only) |
| psutil / soak tooling | ManagementFactory, JFR, bounded Java metrics |
| requests/HTTP services | java.net.http.HttpClient with bounded background execution |

No pure Java ENet implementation is accepted without source, dependency, license
and actual interoperability validation. Native C may be used only in reference
tests, never packaged or called by Java production code.

## Protocol requirements

ENet connect data 168, one channel, network-endian ENet headers, range-coder host
compression, reliable/unreliable/unsequenced semantics, modular sequence arithmetic,
ACKs, fragmentation, retransmission/congestion, peer/session validation, timeouts.
Application fields are little-endian. Outbound frames always use literal LZF
chunks even with prefix 0x30/0x32; inbound LZF decoding occurs only for 0x31.
After ticket receipt inbound application data is XORed with the repeating ticket.
Strict bounds are required before allocation, decompression or dispatch.
Map CRC is zlib CRC32 over raw source VXL; map transfer and MapSync are separate
streams. Follow InitialInfo -> map validation -> MapSync -> StateData, with exact
loading gates, frozen column snapshots and late mutation journals. Preserve error
codes, raw voxel coordinates and documented unsafe-packet suppression.

## Numerical and world compatibility

Java always-strict IEEE operations must retain original expression order: use
float for C floats, double for Python arithmetic and C doubles; do not use FMA.
Port unsigned conversion, wraparound, truncation toward zero, signed-magnitude
1/64 positions (including asymmetric +0.5 rounding), signed 1/8192 orientation,
quarter-unit damage, raw int16 voxel coordinates, and binary32 vector fields.
Preserve source RNG algorithms (Python MT19937 for seeded damage), collision
thresholds, water-plane conversions, movement integration and entity ordering.
VXL is 512x512x240; z=239 is protected. KV6 has standard and legacy variants,
column spans and optional palette trailers. No generic voxel format substitution.

## Baseline and tests

333 tracked files under tests (not all test modules). pytest.ini restricts
collection to tests; conftest explicitly ignores five old modules. Record actual
collection, pass/fail/error/skip results in MIGRATION_REPORT.md; do not enable
ignored tests silently. Existing native movement, jump, movebox, collision, bot
navigation and damage fixtures under tests/fixtures remain reference inputs.
Scripts cover protocol probes, loop/capacity/soak, anticheat and client validation;
full script inventory is in SOURCE_INVENTORY.md.

Test layers: source-generated golden bytes; binary/compression malformed-input
and deterministic fuzz tests; physics differential trajectories; game round and
bot regressions; UDP loss/reorder/reconnect integration; actual unmodified client;
50-player 15-minute soak; same-artifact Windows/Linux/macOS verification.
Source timing gates require >=58 Hz and overall tick p99 <=12 ms under the
specified acceptance load, with zero gameplay drops. Synthetic scheduler timing
is not evidence of gameplay capacity.

## Milestones and risks

A. Audit/baseline, Gradle/JDK25, typed core configuration, binary primitives,
   buildable executable diagnostic launcher and honest parity matrix.
B. Pure Java ENet + compression and complete application codecs; golden fixtures;
   real original-client connection and full map synchronization.
C. VXL/KV6 and physics with fixture parity, authoritative players/WorldUpdates.
D. Combat/terrain/respawn/teams and one verified full game round.
E. Every mode, bots, creator/tutorial, Java plugins, admin, portable hosting.
F. Regression, real-client multiplayer, capacity soak, identical cross-platform
   artifact and production documentation. Public release needs separate approval.

Main risks: ENet range coder and sequencing; loading/reveal ordering crashing
native clients; float expression parity; huge gameplay surface; no client asset
or graphical game session supplied; native-only Steam registration/relay;
persistence format migration; CPU/memory for 240-high VXL and bots; test suites
that monkeypatch shared import state; unavailable Windows/macOS execution.

## Strict acceptance and backlog

Only mark complete when the user specification's twelve completion gates pass.
`java/docs/FEATURE_PARITY.md` is the explicit component backlog. Missing transport,
physics or gameplay must fail visibly rather than advertise a working game server.
Keep reference sources. Use only port/java25, commit verified milestones, never
write upstream or the fork's main branch. Record source and implementation SHAs,
commands/results, limitations and release-readiness. Cross-platform CI is useful
coverage, not evidence that unrun client tests succeeded.
