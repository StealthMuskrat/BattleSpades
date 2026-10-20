# Current administration checkpoint

The administration batch implements password login, one-time room-host claims, verified auto-admin identities, durable bans/admission and moderation on real match-host connections. **6,350 component + 63 integration = 6,413 Java tests** pass across 119 suites, with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. Three new grouped component cases and three actual-UDP cases verify persistence/failure recovery, permissions, secret log redaction, map rollover and suspended-ban safety across compact-ID reuse. Existing component results were reused after correcting only the new UDP fixture.

Unchanged original command/room-admin tests report **45 passed and 3 failed out of 48**; all three failures are GUI configuration imports of the unavailable `tomlkit` package. Original tests were neither changed nor skipped. Native duration/identity/password/token vectors match the Java checks. The six source directories remain exact against `546c548780c400f21d73ca6713f86194ceb3c844`; all 262 existing Java resource paths remain unchanged against this batch's parent.

The CRC-valid application JAR is 2631945 bytes, SHA-256 `cfe106761bc7c21c0353af907e12f24625a3af8d6a7685ba421e3bfadc16e62f`, class major 69. Version/configuration/help checks exit 0; missing UGC projects and ordinary match startup exit 2. All five CI jobs passed for implementation `e50298b4f228319f8ccd2c3eed520573afb0a2ee`, tested tree `90f75340795767b20aea0e4ef1cc380bd01bb95e`, in [run 39345668802](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39345668802). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 78 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T00:13:25.830724+00:00. Steam/master verification, remaining server commands, persistent results/profiles/achievements, bots, Tutorial, normal startup and supported-client acceptance remain incomplete. See [ADMINISTRATION.md](ADMINISTRATION.md).

## Previous verified checkpoints

# Current world creation checkpoint

The Java world-creation workflow passes **6,347 component + 60 integration = 6,407 Java tests** across 118 suites, with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. A generated local archive was rebuilt after the packaged-JAR check detected corruption; the final full build and both complete test tasks pass. No assertion or deadline was weakened.

Fresh unchanged original-source checks report **113 passed, 0 failed and 3 skipped out of 116**, covering UGC, flare support, projectiles, snowball prediction, drill catch-up and map resources. The three skips require retail baseplates absent from this workspace. Four generated codec/catalog outputs reproduce exactly; all 262 frozen resource paths and the six source directories remain unchanged against `546c548780c400f21d73ca6713f86194ceb3c844`.

The CRC-valid application JAR is 2602821 bytes, SHA-256 `08ee5f09df13f9f7d7191b528bcbc308aaf67455335852066f6e0927bb756c2e`, class major 69. Version/configuration/help checks exit 0; missing projects and ordinary match startup exit 2. Dedicated world creation now starts with `--ugc-host`. All five CI jobs passed for implementation `bd17e84603d2ef202df9c03cfca932267e3f4e2c`, tested tree `ecfac3451c2692972131199fc3476542e0375fc6`, in [run 39343084419](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39343084419). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 78 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T23:49:13.244946+00:00. Standalone creator operation uses offline socket ownership; verified-account ownership is supported through the embedding API. Live retail-client acceptance and the broader server migration are not claimed complete.

See [WORLD_CREATION.md](WORLD_CREATION.md).

## Previous verified checkpoints

# Current isolated creator host checkpoint

The isolated creator host passes **6,344 component + 57 integration = 6,401 Java tests** across 117 suites, with zero Java failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. All four codec/catalog outputs reproduce exactly in an isolated tree. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

Fresh unchanged original-source checks report **52 passed, 0 failed and 3 skipped out of 55**; the skips require retail baseplates absent from this workspace. The original native FogColor encoder independently reproduces packet 74 as `4a00563412` for RGB 18/52/86. Previous project/validation differential evidence remains in [UGC_PIPELINE.md](UGC_PIPELINE.md); those larger traces were not rerun for this checkpoint.

The CRC-valid application JAR is 2565206 bytes, SHA-256 `e6b4b2f748ac36b1c5dfda03eaa4db954611aa0e1896b1a0ea2141e1483c7f67`, class major 69. Version/configuration/help checks exit 0; missing-project checks and ordinary hosting exit 2. All five CI jobs passed for implementation `551d2da0ea839ce612286d46343d97d3265cf584`, tested tree `0f0c4bd0fb49bd9ea115dabef8b7b99ba77cc2c3`, in [run 39339635868](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39339635868). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 78 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T23:16:13.611167+00:00. This provides an explicit isolated creator API; complete retail editor tools, production startup/services, supported-client acceptance and the overall migration remain incomplete.

See [CREATOR_HOST.md](CREATOR_HOST.md).

## Previous verified checkpoints

# Current Map Creator pipeline checkpoint

The Map Creator pipeline passes **6,339 component + 55 integration = 6,394 Java tests** across 116 suites, with zero Java failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. A corrupt generated local JAR was rebuilt; final packaging verification reused the passed tests. All four generated codec/catalog outputs reproduce exactly in an isolated tree. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The unchanged original-source checks report **52 passed, 0 failed and 3 skipped out of 55**; those three cases require retail baseplates absent from this workspace. Independent comparisons match 24 project traces containing 1,920 mutations, 216 mode validations and four VXL capacity samples. Original native encoders reproduce all four packet-68/98/102/118 literals. This is not a full Python-baseline or supported-client acceptance claim.

The CRC-valid application JAR is 2505161 bytes, SHA-256 `c1362d073322ea2fa2699f21cd75d9ad8ee48b2d4c7fed5b04981e5661ba8678`, class major 69. Version/configuration/help checks exit 0; missing-project checks and ordinary hosting exit 2. All five CI jobs passed for implementation `70fb8f129e62ad20d0ac59062dfc9ec99ef3dcb0`, tested tree `af91a0741af178863e9a9ac9c23e03a32c5ce6ce`, in [run 39335344654](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39335344654). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 78 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T22:37:44.952117+00:00. Full UGC hosting, production startup/services and the overall migration remain incomplete.

See [UGC_PIPELINE.md](UGC_PIPELINE.md).

## Previous verified checkpoints

# Current Zombie/Hardcore checkpoint

Zombie and all ten Hardcore variants pass **6,329 component + 52 integration = 6,381 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. All three packet-generator outputs reproduce exactly. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **109 passed and 10 failed out of 119**. Eight failures are unchanged `_Server` test doubles missing `respawn_player`; one exposed-minimap expectation and one reset respawn-call expectation differ from the current reference implementation. Original source/tests were not edited or skipped. This is not a passing full Python-baseline claim.

The CRC-valid application JAR is 2436289 bytes, SHA-256 `a3216142aec5c31808b816aff61a267ed3a4faf772318a98b89bc3927f8e19d3`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. All five CI jobs passed for implementation `078664999ad7ba490994677b857ada05df904e50`, tested tree `a6ed77f797f5cb7ddc4effe64aefc76c3eb8e5a5`, in [run 39331928555](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39331928555). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T22:09:28.070192+00:00. Full migration, production startup/services and supported-client acceptance remain incomplete.

See [ZOMBIE_HARDCORE.md](ZOMBIE_HARDCORE.md).

## Previous verified checkpoints

# Current Demolition/VIP checkpoint

Demolition and VIP pass **6,314 component + 50 integration = 6,364 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distribution packaging pass. All three packet-generator outputs reproduce exactly; original native packet encoders independently reproduce the packet-18/117 literal bytes used by the boundary tests. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **36 passed and 35 failed out of 71**. All 35 failures occur in unchanged VIP test doubles missing `set_tool`, now required by upstream live promotion. The original code/tests were not edited or skipped; this is not a passing Python-baseline claim.

The CRC-valid application JAR is 2403975 bytes, SHA-256 `95eb587b70a35c7fe11aca122a223a4c62df16ed2df549007b844fdf392f3bf9`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. All five CI jobs passed for implementation `abd2f98bc1fcb0f34193d6aeaf4d0d82db4f34b6`, tested tree `f581e54718666562eb185f3006cf9df34fe767cb`, in [run 39327620596](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39327620596). All 65 final steps in completed jobs succeeded or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed; the build byte-compared repeated archives. Completed platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T21:33:51.018887+00:00. The preceding Demolition/VIP checkpoint `ad9917b` passed all five jobs in [run 39326688290](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39326688290). The older carried-objective run 39321095562 ended with macOS reporting failure (its job log remains unavailable), while its other four jobs passed; no all-five pass is claimed for that older run. Full migration, production startup/services and supported-client acceptance remain incomplete.

See [DEMVIP_MODES.md](DEMVIP_MODES.md).

## Previous carried-objective checkpoint

# Current carried-objective checkpoint

The carried-objective batch passes **6,306 component + 48 integration = 6,354 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compiler warnings, portability and distribution packaging pass. All **78 selected original Diamond Mine/Occupation/pickup/objective regressions** pass freshly against source `546c548780c400f21d73ca6713f86194ceb3c844`. The six reference source directories still match that commit, and all 262 frozen fixture/catalog resource paths are unchanged. Packet generation reproduces all three outputs exactly.

The CRC-valid JAR is 2352014 bytes, SHA-256 `64189d53329bb538d50268f4b595e3ccc14a6aaf51b1de542537ba98d1dee090`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. Build, native ENet, Ubuntu and Windows passed; **macOS is tests are still running; all-five verification remains incomplete** for implementation `476739b77a55fc7f4b1c63a22c960b5e55c5692e`, tested tree `4a746b3f1c322bc18de810ca4fb2ab0e6b9cd374`, in [run 39321095562](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39321095562). All 53 final steps in the completed jobs succeeded or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed; the build byte-compared repeated archives. Completed platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-19T20:50:28.731613+00:00. Full migration, persistent achievement/results services, startup composition and supported-client acceptance remain incomplete. The preceding capture-zone CI run passed build/native/Ubuntu/Windows, but its queued macOS job was subsequently cancelled; no all-five pass is claimed for that predecessor.

See [CARRY_MODES.md](CARRY_MODES.md).

## Previous capture-zone checkpoint

# Current migration implementation phase

The capture-zone batch passes **6,300 component + 46 integration = 6,346 Java tests** across 113 suites, with zero failures, errors or skips. Consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **65 selected original TC/Multi-Hill/objective/escape-watch tests** pass against updated source `546c548780c400f21d73ca6713f86194ceb3c844`. Earlier CTF/runtime test evidence remains scoped to its recorded snapshots.

The seven new `main` commits and all 29 changed reference/config/workflow files are imported exactly. The six original source directories match `main` at `546c548780c400f21d73ca6713f86194ceb3c844`. All 262 frozen Java fixture/catalog resource paths remain byte-identical; their historical provenance stays intact. Of the preceding 265 checked paths (which also included three generator outputs), only `PacketCodec.java` changes intentionally to add packet 106. The source inventory now audits 1,164 files and 277,001 Python/Cython source lines at the new baseline. The packet generator reproduces all three outputs exactly.

The CRC-valid JAR is 2307094 bytes, SHA-256 `4c4dcd50fa7db004f0f11304df8b7e68e70f0eeaf8e7ee62316bbd0c4a4b2ee4`, with class major 69. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. CI [run 39315247712](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39315247712) targets implementation `13bf1e85f556bc5a9ef2197bd368ea15c33982f5`, merge tree `8dd0a01a8939a34a96fd90f7809277bfc3a8e0b8`. Build, native ENet, Ubuntu and Windows passed; all 53 final steps in those four jobs completed successfully or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed, and the build byte-compared repeated application archives. Ubuntu and Windows smoked the uploaded artifact and passed component/integration tests. **macOS remains queued awaiting a runner; all-five-job verification is pending** (observed 2026-10-19T20:12:28.882372+00:00). No macOS pass, local-to-CI artifact hash comparison or supported-client acceptance is claimed. Full migration, achievement/persistence/service composition and supported-client acceptance remain incomplete.

See [ZONE_RUNTIME.md](ZONE_RUNTIME.md) for this batch and the remaining scope. The following records the preceding runtime implementation phase.

# Migration implementation phase

The user explicitly requested testing the accumulated changes on 2026-10-19.
That request supersedes the earlier instruction to defer testing until all
remaining implementation is complete. Verification is recorded separately from
the remaining migration work below.

The current verified implementation commit is `97b05b197999db083cf70849c6f3c82249cc4d17`.
The accumulated changes are published in that checkpoint and verified locally
and by all five CI jobs. Earlier checkpoint counts and artifact hashes apply
only to their recorded snapshots. The diagnostic launcher remains gated.

## Implemented in the current working tree

- Literal runtime placement decoding and canonical-player dispatch.
- Match-owned medpack, dynamite, mine, C4 and radar placement, stock and cadence.
- Machine-gun placement/relocation, mount/unmount, damage and support retirement.
- Rocket-turret placement, health, tracking, aiming, ammo, launch and replication.
- Disguise activation, inventory debit, delayed input anchor and movement break.
- Blocksucker warm-up, state relay suppression, cooldown and terrain pull.
- Packet 10 decoding, oriented launch validation and launcher/throwable wallets.
- Projectile timers, legacy bounce, swept terrain/body contact, drill contact,
  sticky attachment transitions, launched mines and block-cannon impacts.
- Fire/chemical surface effects, bounded emitters, shared spread budgets,
  fractional damage, friendly-fire policy and owner cleanup.
- Per-peer riot-shield anchors and impact delivery.
- Deferred block/line topology commits, reservation cancellation, owner echoes,
  observer colour/health and owner palette correction.
- Normal paint and flare placement, flare-component support lifetime.
- Construction safety reservations, protected authored zones and exit checks.
- Worker-owned case-insensitive KV6 model loading/quarter-turn expansion and
  whole competitive prefab commits under a tick budget.
- Normal corpse/jetpack death flight, packet-36 ordering, grave creation,
  generation-bound classic corpse state, hit/cleanup and join catch-up.
- Round reset and disconnect cleanup for the newly owned state.
- Shared per-connection anticheat counters with separately throttled diagnostics.
- Accepted teammate-harm incidents, grief decay/warnings and real-input AFK clocks.
- Chat lane canonicalization, known-roster delivery, chat/command token buckets,
  asynchronous command registry, secret-safe command logging, and public commands
  (`help`, `kill`, `team`, `score`, `players`, `pm`, `me`, `ping`, `stats`).

This inventory records implementation scope. Passing local tests does not establish
full migration compatibility or production readiness.

## Required remaining implementation

1. Dedicated Tutorial host, authored lesson/lane restoration flow and the remaining unofficial mode families.
2. Bot AI/director, navigation, motor/action integration, shared slot ownership and real bot-path construction safety.
3. Durable profiles/results/achievements and their accepted-event and round-end service bindings.
4. Steam/master identity verification, discovery, remaining admin/server command families and required production observers. The administration embedding and moderation batch is described in ADMINISTRATION.md; it does not provide those external services.
5. Complete normal startup composition, production observers, persistent service ownership and shutdown. Keep the ordinary launcher gated until these owners exist.
6. Source compatibility reconciliation, live supported-client/capacity validation and all five CI jobs for each verified checkpoint. Passing a batch does not complete those migration gates.

## Local verification — 2026-10-19

The consolidated Java 25 `check build` passed in 49 seconds with strict compiler
warnings enabled: **6,288 component tests and 42 integration tests**, with no
failures, errors or skips. `verifyPortable`, JAR packaging and both distribution
archives passed. All **326 selected original Python tests** across 20 affected
modules passed; this does not establish a passing full Python baseline.

The packet generator reproduced `SimplePackets.java`, `PacketCodec.java` and
`simple-packets.json` exactly in a temporary directory. The six original source
directories and frozen Java fixtures are unchanged from the published checkpoint.

Testing corrected turret stock ownership: generic inventory commit retains its
source cadence-only semantics, while the actual turret placement consumes stock
once, including the last available turret. Chemical-goo splash now includes the
source radius-plus-half-cell offsets. Neither fix changes golden fixtures.

Thirteen focused Java tests cover literal wire fields, projectile bounce and swept
contacts, sticky attachment/death ordering, mutation readiness and cancellation,
disguise and anticheat lifetimes, prefab rounding/rotation/blending, turret/MG
stock and retirement, canonical launch/C4 cleanup, goo contact damage, corpse
completion, accepted conduct harm, and command permissions/rate limits/owner
resumption. Construction runs over actual UDP through the production domain and
checks canonical owner echoes, health, profile credit, refunds and life boundaries.

The first Python prefab attempt lacked the compiled `aoslib.kv6` reference module.
Compiling the original module into the build directory resolved that environment
issue; the final complete selected run passed without changing reference policy.

See [implementation-local-verification.json](implementation-local-verification.json)
for counts and the tested JAR/source fingerprints. This local snapshot predates the CTF additions. See [CTF_RUNTIME.md](CTF_RUNTIME.md)
and [ctf-local-verification.json](ctf-local-verification.json) for the newer checkpoint.
The remaining implementation above is still required.

## CTF and Classic CTF implementation batch

The current accumulated implementation batch passes **6,294 component + 44 integration = 6,338 Java tests** across 113 suites, with zero failures, errors or skips. The consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **47 selected original CTF/Classic/objective regressions** pass freshly. The preceding 326 selected Python checks were run for the preceding implementation snapshot and were not rerun here.

Packet-generator reproduction is exact for all three outputs. Frozen Java fixtures/catalogs and the six original source directories remain unchanged. The CRC-valid JAR is 2261083 bytes, SHA-256 `3206345496e67d6a95b54772b888b3b24c8262cdad26f3b942ea77ffc6853091`. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. All five CI jobs passed for implementation `97b05b197999db083cf70849c6f3c82249cc4d17`, tree `83b43cd911c4f3d4ea8b516e571bb8e71d546b14`, in [run 39308869800](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39308869800). Every final job step completed successfully or was intentionally skipped. The build verified repeated JAR bytes; the native job passed impaired transport/loader interoperability and rebuilt all 77 configured reference generators; Ubuntu, Windows and macOS each smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash comparison or supported-client gameplay acceptance is claimed. Full migration, production service composition and supported-client acceptance remain incomplete.

The factory now binds `ctf` and `cctf` to actual objective and lifecycle owners. See [CTF_RUNTIME.md](CTF_RUNTIME.md).
