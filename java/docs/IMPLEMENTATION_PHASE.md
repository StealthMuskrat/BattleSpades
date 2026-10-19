# Migration implementation phase

The user explicitly requested testing the accumulated changes on 2026-10-19.
That request supersedes the earlier instruction to defer testing until all
remaining implementation is complete. Verification is recorded separately from
the remaining migration work below.

The last verified commit is `f8e96396b4656d60dd602a795d3384f03d541c6c`.
The working tree contains subsequent production changes that passed local
verification on 2026-10-19. Previous checkpoint counts, CI runs and artifact hashes do not cover
these changes. The diagnostic launcher remains gated.

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

The Python/reference directories and frozen fixtures remain unchanged. Focused
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

Packet-generator reproduction is exact for all three outputs. Frozen Java fixtures/catalogs and the six original source directories remain unchanged. The CRC-valid JAR is 2261083 bytes, SHA-256 `3206345496e67d6a95b54772b888b3b24c8262cdad26f3b942ea77ffc6853091`. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. Cross-platform/native CI is pending for this checkpoint. Full migration, production service composition and supported-client acceptance remain incomplete.

The factory now binds `ctf` and `cctf` to actual objective and lifecycle owners. See [CTF_RUNTIME.md](CTF_RUNTIME.md).
