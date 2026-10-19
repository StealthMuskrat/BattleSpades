# Current Demolition/VIP checkpoint

Demolition and VIP pass **6,314 component + 50 integration = 6,364 Java tests** across 113 suites, with zero failures, errors or skips. Java 25 `check build`, strict compilation, portability and distribution packaging pass. All three packet-generator outputs reproduce exactly; original native packet encoders independently reproduce the packet-18/117 literal bytes used by the boundary tests. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The focused original-source run reports **36 passed and 35 failed out of 71**. All 35 failures occur in unchanged VIP test doubles missing `set_tool`, now required by upstream live promotion. The original code/tests were not edited or skipped; this is not a passing Python-baseline claim.

The CRC-valid application JAR is 2403794 bytes, SHA-256 `0bcfbbfb92d2f0cf47e1034b86429c2fe66306390a4ee35fcfb8e01c6adb22f8`, class major 69. Version/configuration checks exit 0; ordinary hosting remains gated at exit 2. CI for this new checkpoint is pending. The previous carried-objective run [39321095562](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39321095562) has build/native/Windows/Ubuntu passing and macOS still running at the latest observation; no all-five pass is claimed for it. Full migration, production startup/services and supported-client acceptance remain incomplete.

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

1. Additional mode factories and their actual objectives, HUD, rules, selection,
   scoring, carry/drop, revival and round-ending bindings. TDM, Arena, CTF and Classic CTF
   now have host factory bindings; the other modes still require their real owners.
2. Full UGC editor construction/prefab build/erase range handling and brush flow.
   Packet 31 is decoded but the ordinary competitive owner rejects editor erases.
3. Bot AI/director, navigation, motor/action integration and shared slot ownership;
   bind real bot paths into construction safety when the director is installed.
4. Authenticated identity/admin/creator ownership, remaining command families,
   persistent bans/results/profiles/achievements, master/discovery services and
   required production observers. Public command routing does not provide admin
   authentication or the remaining admin command families.
5. Finish explicit startup composition, configured prefab roots, actual service
   owners, end/results/transition/reset bindings and shutdown. Keep the launcher
   gated until these owners exist; do not replace required domains with no-ops.
6. Complete source compatibility reconciliation, supported-client and platform
   validation, and all five CI jobs for a verified published checkpoint. Local
   verification of the current batch does not complete those migration gates.

For that preceding checkpoint, the Python/reference directories and frozen fixtures remained unchanged. Focused
Java regression coverage was added for the accumulated behavior; existing hosted
and UDP test harnesses were extended for runtime and delivery checks.

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
