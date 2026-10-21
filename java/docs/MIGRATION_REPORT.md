# Current bot combat and awareness checkpoint

Ported source weapon engagement/recoil doctrine, committed combat footwork, sight and accepted-fire feedback, collision-checked relocation/cover/shelter/hazard escape, and the complete per-life awareness policy. Awareness includes incoming-fire timing, sound glances, ally-death cues, losing-fight retreat/regrouping, visible-fire steering and own-Molotov prediction; intent overlays retain action, route and generation/life/map/mode/topology fences. **6,483 Java tests pass** (6,393 component + 90 integration), zero failures/errors/skips. All 124 selected unchanged-source combat-tactics, natural-locomotion and awareness regressions pass. Differential evidence includes nineteen weapon envelopes, 495 footwork transitions, 72 tactical queries and eighteen awareness traces at 731 boundaries; 337 immutable input records are deduplicated. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 3207951 bytes, SHA-256 `c72e4b7a8fe222e50314457430e9a0ead9b8f9b52324da7de70dd8c0de3a5230`, class major 69. Eight original source directories and all 274 previous resources remain unchanged; two new resources bring the total to 276, with 87 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `c62c577bd4a83748de79ff630343acbe7714ba17`, tested tree `9e34ad7fcfaec9ea4656ca0f330ead45196c56fb`, in [run 39517916904](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39517916904). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 87 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. **Live bots remain gated:** production brain orchestration, mode/cooperative policies, advanced recovery skills, director/motor/action gateway and host composition remain unfinished. The full migration remains incomplete. See [BOT_COMBAT.md](BOT_COMBAT.md).

## Previous verified checkpoints

# Current bot local navigation checkpoint

Ported the remaining active local navigation policies: segmented surface A*, capsule support recovery, parallel-wall separation, height-preserving route compaction, walking/crouch/jump/drop/jetpack edges, tool- and health-aware excavation, live water and shore recovery, waterline breaches and supported bridge/shoulder proposals. Ordinary traversal and excavation share one fair planning job with two searches and 512 total expansions; each search is bounded to 256 expansions. **6,478 Java tests pass** (6,388 component + 90 integration), zero failures/errors/skips. All 110 selected unchanged-source navigation, built-wall, jetpack and water regressions pass. Source differential evidence compares 58 exact route/neighbor queries across 18 terrain scenes, 14 connected water/construction observations, thirteen tool profiles and eleven tool-selection cases. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 3155506 bytes, SHA-256 `8c575e6f917e99e6e5b40a01746c4f2fcc4233daf32d0ec6ed16de4e8872c5b7`, class major 69. Eight original source directories and all 272 previous resources remain unchanged; two new resources bring the total to 274, with 86 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `faa77d64ef8a26fce2f71d4810223c8a99e51065`, tested tree `bbabe41eda3547cc0852c4aa6c2daddfe7c7af09`, in [run 39497419697](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39497419697). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 86 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. The initial Linux attempt timed out in the existing standalone UDP map-reload integration; a single-job retry passed without code changes. The intermittent timing failure remains recorded. **Live bots remain gated:** the real brain, director, motor/action gateway, mode/cooperative policies and host composition remain unfinished. The full migration remains incomplete. See [BOT_NAVIGATION.md](BOT_NAVIGATION.md).

## Previous verified checkpoints

# Current bot foundations checkpoint

Implemented immutable bot state and messages, seeded profiles, hearing and footsteps, fair planning credits, adaptive skill balancing, collision-only VXL snapshots, semantic navigation atlases and source-compatible cache encoding, retained corridor search, path-following helpers and a private-world worker lifecycle. Restart and crash recovery preserve terrain overlays; snapshots, stale observations, bounded mailboxes and close timeouts have explicit ownership rules. **6,474 Java tests pass** (6,384 component + 90 integration), zero failures/errors/skips. All 73 selected unchanged-source planning, voxel, atlas, skill and path-following regressions pass. New source observations cover 120 profiles, 789 planning boundaries, 21 sensory actions, 12 adaptive updates, 12 complete atlases, five compact snapshots, 10 world transitions and four retained corridor searches; the shipped MayanJungle cache decodes against its raw map. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 3124266 bytes, SHA-256 `81b91376f3d753637c348bccc89a57bc07139e9b5608bdd9514450daa18c0005`, class major 69. Eight original source directories and all 270 previous resources remain unchanged; two new resources bring the total to 272, with 85 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `c0c5302572f5230151b79fbebeeb0c9545aa9f33`, tested tree `e178a3b9ed1c1e03102916df64f3ff322e65635b`, in [run 39494617775](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39494617775). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 85 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. **Live bots remain gated:** the detailed local planner, real brain, director, gateway and host composition remain unfinished. The full migration remains incomplete. See [BOT_FOUNDATIONS.md](BOT_FOUNDATIONS.md).

## Previous verified checkpoints

# Current standalone hosting and update checkpoint

Added production standalone owner/worker/service composition, a long-running launcher, bounded console control, accepted-transition shutdown, startup recovery, final retiring-domain telemetry and an offline human-only configuration. Ported schema-2 update checks, semantic-version precedence, bounded HTTP, mirror fallback, exact size/SHA-256 verification, cache reuse and opt-in archive staging. **6,463 Java tests pass** (6,375 component + 88 integration), zero failures/errors/skips. Real UDP peers survive map/mode reloads; shutdown settles accepted preflight; packaged console and timed shutdown pass, including the shipped MayanJungle map. Update evidence includes 400 unchanged-source version comparisons, 17 manifest observations and real local HTTP/CLI checks, including corrupt mirrors, oversized responses, traversal refusal and unchanged installation files. All 52 selected original update/capacity/simulation-order tests pass. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 2992433 bytes, SHA-256 `dea963efb8ebd8867c45c389707d00904aa19c7663a43577f0eb444872a9b2cf`, class major 69. Eight original source directories and all 269 previous resources remain unchanged; one fixture brings resources to 270, with 84 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `c39c1a5b1ce29647d5f8bfff9690d211c5d52ce3`, tested tree `b1ca68059e0ef9de8ee9eda2a6a77fe6fba5e12d`, in [run 39487222400](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39487222400). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 84 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. **The full migration remains incomplete:** bots, tutorial/community modes, online services, native integrations, fleet orchestration and supported-client/capacity acceptance remain outstanding. See [STANDALONE_HOSTING.md](STANDALONE_HOSTING.md).

## Previous verified checkpoints

# Current portable plugins checkpoint

Implemented the Java plugin SPI and lifecycle manager, trusted local JAR discovery, budgeted hosted callbacks, retained shutdown and an opt-in port of the original example. **6,459 Java tests pass** (6,374 component + 85 integration), zero failures/errors/skips. A new intact-source fixture records 24 lifecycle/budget boundaries and 16 example kill boundaries. Actual JAR tests cover ServiceLoader discovery, broken neighbors, enabled/filter policies and source announcement behavior. The ENet host test keeps loading traffic alive across an awaited external hook, records skipped-callback telemetry, retains plugin identity across map rollover and drains suspended work before unload/classloader disposal. All 34 selected unchanged plugin loader, capacity and simulation-order source tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2924496 bytes, SHA-256 `38fab342f3e6b0232fc9d5ebe2e74b5bdcd3d68d6d6e7d1783ad7b42d221c497`, class major 69. Eight original source directories and all 268 previous resources remain unchanged; one fixture brings resources to 269, with 83 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `ae1d93c1fb1d9a9ce1a69a9ba0b193ee71527bdb`, tested tree `55e3db7c71cb5dec710238e83cf15edaadeb4e15`, in [run 39481556100](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39481556100). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 83 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Ordinary competitive startup, bots/remaining composition, supported-client acceptance and the broader migration remain incomplete. See [PLUGINS.md](PLUGINS.md).

## Previous verified checkpoints

# Current runtime services checkpoint

Added portable A2S/LAN discovery, bounded runtime metrics, asynchronous rotating logging and standard host lifecycle bindings. **6,455 Java tests pass** (6,371 component + 84 integration), zero failures/errors/skips. A new intact-source fixture covers 16 query requests, elapsed/replaced-player responses, six mode tags and six metric windows. The real UDP integration checks discovery alongside ENet loading and map rollover, reserved population, exclusive fallback ports, game-port-only LAN replies and host telemetry. Full-queue logging drains asynchronously and formatting/sink I/O stay on its worker. All 39 selected unchanged A2S, Steam advertisement and telemetry source tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2898559 bytes, SHA-256 `78a547acb47716ce66a653062601215537dce5ca899c83b67f45f3043491d9d1`, class major 69. Seven original source directories and all 267 previous resources remain unchanged; one fixture brings resources to 268, with 82 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `94607af934953fcf456222bd903517f92a2589a5`, tested tree `0b5369f96b3a5dbae05321496d303aaad4d86472`, in [run 39476916153](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39476916153). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 82 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Ordinary competitive startup, bot/plugin composition, supported-client acceptance and the broader migration remain incomplete. See [RUNTIME_SERVICES.md](RUNTIME_SERVICES.md).

## Previous verified checkpoints

# Current environmental achievement checkpoint

Completed the remaining environmental achievement families: nested blast groups, buried mines, below-feet dynamite and credited falls, direct airborne rocket hits, turret identity/accuracy/evasion, terrain structures, zombie block destruction, crate resupply, demolition damage/repair/final damage, and authored kill/destroy regions. **6,451 Java tests pass** (6,368 component + 83 integration), zero failures/errors/skips. Twenty-eight intact original-engine traces match at 227 owner boundaries; an actual production-owner check covers a triple-kill blast with nested corpse blasts and real map-crate refills. All 206 selected unchanged original HUD/achievement tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2856522 bytes, SHA-256 `554aa03a4c1a41edb96c7b9550c7ebf8c63e95f483adf4e5decfc9b27f17a0ff`, class major 69. The seven source directories and all 266 existing resources remain unchanged; one new fixture brings resources to 267, with 81 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `9925a23ecc6c70655d6f8b9323dfb7ea9707f7b4`, tested tree `40a1b3fcb0dfa6cac8344e884431f6053f82148e`, in [run 39470430093](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39470430093). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 81 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Six original achievements still lack recovered retail volumes; none are invented. Ordinary standalone hosting, supported-client acceptance and the broader migration remain incomplete. See [ACHIEVEMENT_ENVIRONMENT.md](ACHIEVEMENT_ENVIRONMENT.md).

## Previous verified checkpoints

# Current objective achievements and asynchronous result checkpoint

Completed a connected achievement batch across Zombie, VIP, Diamond Mine, Occupation, CTF and Multi-Hill, with match/sub-round scopes and asynchronous round-result composition. Result capture reserves immutable scores and capture-time unlock eligibility before worker waits; ordered handoffs retain retries and drain before shutdown. **6,449 Java tests pass** (6,366 component + 83 integration), zero failures/errors/skips. Fourteen new intact original-engine objective/sub-round traces cover awards, bot competition, carry recovery, reset boundaries, intel returns and hill survival; the actual-UDP integration suspends the achievement worker across two results and a round reset while another client joins. The next round's unlock stays with its own result. All 206 selected unchanged original HUD/achievement tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2835407 bytes, SHA-256 `53ad2600de8a453917d045bec07e0aaa8826788a8ae5f01a185edaf4b7dc5588`, class major 69. The seven source directories and all 265 existing resources remain unchanged; one new fixture brings resources to 266, with 80 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `dbf32c0c680a3a37d746fc1c70c512aefa5d74e9`, tested tree `ee45094b723cd3fc1c2ea76dc12d304b08f03d5e`, in [run 39466139668](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39466139668). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 80 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Explosion/region/terrain/crate/demolition achievement rules, ordinary standalone hosting and the broader migration remain incomplete. See [ACHIEVEMENT_OBJECTIVES.md](ACHIEVEMENT_OBJECTIVES.md).

## Previous verified checkpoints

# Current hosted HUD and achievement foundations checkpoint

Completed a connected HUD/achievement batch: native minimap billboard codec and hosted marker audiences/replay/reset cleanup; achievement catalog and portable transactional persistence; legacy SQLite export; hosted generic combat, lifetime counters, verified identity, durable notifications and asynchronous handoff. **6,447 Java tests pass** (6,365 component + 82 integration), zero failures/errors/skips. Differential evidence includes 134 intact-engine tier observations, eight generic combat traces, an original SQLite export and 68 native billboard vectors. All 206 selected original HUD/achievement source tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2822576 bytes, SHA-256 `bcff59bb232ff56112594c712aabd48ea28441ef2757874f20179f8f1a9b39af`, class major 69. The seven source directories and all 263 existing resources remain unchanged; two new resources and one generator bring the configured source generators to 80. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `b06d723de0346773b2eb4e3e901347fd51c57ae5`, tested tree `225596e18557bafccb450c26518b563ea73be789`, in [run 39460810065](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39460810065). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 80 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. The initial native run failed because -S hid the installed TOML dependency; the generator invocation was corrected without changing production code or resources. Mode-specific achievement families, round-result handoff composition, ordinary standalone hosting and the broader migration remain incomplete. See [HUD_ACHIEVEMENTS.md](HUD_ACHIEVEMENTS.md).

## Previous verified checkpoints

# Current hosted HUD and anticheat review checkpoint

Completed a connected hosted batch: runtime team score locks and unlimited block wallets; accepted-shot aim observations and weighted suspicion analysis; administrator review commands and a bounded, rotating worker journal with connection-safe scene retirement. **6,441 Java tests pass** (6,361 component + 80 integration), zero failures/errors/skips, including wallet authorities/reservation refunds, late-join packets, permissions, real accepted shots, map reload and awaited journal rotation. All 41 selected unchanged original HUD/anticheat tests pass. Strict Java 25 check/build, portability, distributions and launcher checks pass. The CRC-valid JAR is 2766818 bytes, SHA-256 `83e278b9ea41ab2b48de428a23170b80e211bed03f423b49e89878f19e6647bb`, class major 69. All seven source directories and 263 existing resources remain unchanged. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `1cbc0007a715a93d7b2027c23080f041ce167f81`, tested tree `ad25807f899e00f50283723f85a58d24aed2b194`, in [run 39449497503](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39449497503). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Ordinary standalone hosting and the broader migration remain incomplete. See [HUD_AND_ANTICHEAT_REVIEW.md](HUD_AND_ANTICHEAT_REVIEW.md).

## Previous verified checkpoints

# Current hosted round-ending checkpoint

Completed the standard hosted round-ending composition: ending music, forced scores, final rows/results, shared-ballot waiting, same-world restart, voted-map rollover, mode-specific headlines and ordered shutdown. Actual nested restarts are retained and cancelled; pure preflight/display waits are retired, and destructive scene transactions are awaited. **6,435 Java tests pass** (6,357 component + 78 integration), with zero failures/errors/skips. Strict Java 25 check/build, portability, distributions and launcher smoke checks pass. The CRC-valid JAR is 2,715,579 bytes, SHA-256 `f6d06ee3ca3543d5e82c6c2aabff2857ff61df800af17282f51e17614d4c2304`, class major 69. Of 36 selected unchanged Python tests, 35 pass and one existing Zombie fixture fails because its server facade lacks `respawn_player`; the full Python baseline remains unresolved. All seven source directories and 263 existing resources are unchanged. The four currently required CI jobs—build, native ENet, Linux and Windows—passed for implementation `d62fca366e9b578273e09b82be2990482bb433cd`, tested tree `b1ecfd5074ae96dd2092e69732a763702f7f7586`, in [run 39440226295](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39440226295). All 53 required final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. Per the latest user instruction, macOS is deferred until the full migration is complete and has been removed from the routine CI matrix. The earlier macOS timeout and runner-allocation cancellation remain recorded; no all-five-jobs success is claimed. The matrix-only workflow change was parsed and inspected without another test run. Ordinary standalone hosting and the broader migration remain incomplete. See [HOSTED_ROUND_ENDING.md](HOSTED_ROUND_ENDING.md).

## Previous verified checkpoints

# Current standard hosted voting checkpoint

Completed standard-host composition of ranked map rotation, map ballots and retail vote-kick policy. Startup configuration/catalog work is isolated from gameplay; votes now share host ownership across scene retirement, route real client packets and late-join overlays, compose with existing admission policies, retain address cooldowns, reset match exclusions, and expire through the independent second scheduler even after mode ending. Verified room-creator principals are protected. One grouped component and three actual-UDP cases cover settings/worker catalog, ranked countdown candidates, late joins, voted-map reload and continued voting, room protection, admission composition, restart/slot reuse/cooldowns, independent timeout and end-of-round overlay priority, and host shutdown waiter retirement. 6,431 Java tests pass (6,356 component + 75 integration), zero failures/errors/skips; all 77 unchanged source audio/vote/lobby/ranking tests pass. All 263 existing resources and seven source directories remain unchanged. The CRC-valid application archive is 2702490 bytes, SHA-256 `8f5555d985d67d5a2917b6b792d1a8b1be7146f8615cce60bbec8b1e317f4968`, class major 69. Version/configuration/help exit 0; missing UGC project and ordinary startup exit 2. All five CI jobs passed for implementation `036fcd1f4b32fc3de0859d8af27552a5a9250f72`, tested tree `9ecf0840c9f565bdcf703182caa09a02038cdc77`, in [run 39356365544](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39356365544). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T02:06:45.398527+00:00. The broader migration remains incomplete; see [HOSTED_VOTING.md](HOSTED_VOTING.md).

## Previous verified checkpoints

# Current hosted runtime-control checkpoint

Implemented same-map restart/reset on actual round/mode/entity/score/life owners, bounded teleport with water/parachute/telemetry state consistency, and live self-row inclusion/offset/interval tuning that persists through restart and scene reload. Bot preparation/reset and HUD clearing remain explicit required embedding services. Two grouped components and two actual-UDP integrations cover cadence/anchor retention and wire-label bounds, water/per-fall/input/life preservation, suspended restart with continuing networking and slot reuse, score/terrain/fog/spectator preservation, authorization, teleport, atomic tuning and real map reload. 6,427 Java tests pass (6,355 component + 72 integration), zero failures/errors/skips; all 44 unchanged source command/transition/runtime-reset tests pass. All 263 existing resources and seven source directories remain unchanged. The CRC-valid application archive is 2695255 bytes, SHA-256 `d8218da12e347fc667101cffb0c9d0db53fa664a2e2075e3a61ea4f6e57e6e06`, class major 69. Version/configuration/help exit 0; missing UGC project and ordinary startup exit 2. All five CI jobs passed for implementation `14d6f239a10684699874d103f918bd61738b4f67`, tested tree `3430dcb1b8953cb209cee4d1265a4813a02e83a6`, in [run 39353628603](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39353628603). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T01:35:20.853725+00:00. The broader migration remains incomplete; see [RUNTIME_CONTROLS.md](RUNTIME_CONTROLS.md).

## Previous verified checkpoints

# Current hosted server-control checkpoint

Implemented map/mode retained requests, normal round ending, server announcements, live/reconnect/rollback-consistent fog, a fresh remaining-time window and the existing safe manual balancer. Administrator permissions and current player-life notices remain authoritative. Map/mode requests leave the serial packet drain before loader rollover. Missing owners return unavailable. Three grouped actual-UDP integration cases cover permissions, packet lanes and bounds, reconnect/snapshot/rollback/new-map fog, clock mutation, explicit/leader-based ending, dead-only/no-extra-death balancing, held preflight with continuing clock packets, busy/failure replies and actual scene reload. 6,423 Java tests pass (6,353 component + 70 integration), zero failures/errors/skips; all 23 unchanged source command tests pass. All 263 existing resources and the seven source directories are unchanged against their baselines. The CRC-valid application archive is 2686422 bytes, SHA-256 `cde5abf755c3a139c82114721d69f4ab9571a2274281ad24f99a060f370bb418`, class major 69. Version/configuration/help exit 0; missing UGC project and ordinary startup exit 2. All five CI jobs passed for implementation `501feba8721287d90df1aba60a4933fad1fa25fc`, tested tree `1229178fcea07a98ae6fad5991b69604a907a4c6`, in [run 39351559790](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39351559790). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T01:14:15.414842+00:00. The broader migration remains incomplete; see [SERVER_CONTROLS.md](SERVER_CONTROLS.md).

## Previous verified checkpoints

# Current round-result pipeline checkpoint

The round-result batch implements owner-side counter reservation, disconnected-account credit, portable durable outbox/SQLite import, acknowledged master HTTP delivery and host reset/shutdown/offline integration. **6,353 component + 67 integration = 6,420 Java tests** pass across 121 suites with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. Final full component and integration runs pass after removing automatic unindexed-payload cleanup; unrelated spool files are preserved. The earlier UDP clock-driver correction did not weaken assertions or deadlines.

All **48 targeted unchanged original-source tests pass** across result outbox, profile statistics and Revival master. Two fixture traces execute the original reservation methods and reproduce four event envelopes. An actual original SQLite outbox was exported read-only and imported twice by the packaged Java application; both reports, original database bytes and idempotent IDs were verified. Three grouped component and four new integration cases cover native accounting, durable transactions/mirrors/cancellation, actual HTTP acknowledgements/retries, packaged import, UDP departure/slot reuse/reset/shutdown and offline suppression. CI now configures 79 reference generators. The six source directories remain exact against `546c548780c400f21d73ca6713f86194ceb3c844`; all 262 existing resources are unchanged, with one new result fixture added.

The CRC-valid application JAR is 2679721 bytes, SHA-256 `ec52b48ce0aa4bbcf1da0873d3f34759125461f4624a60800a6933fc1ee22ca5`, class major 69. Version/configuration/help checks exit 0; missing UGC projects and ordinary match startup exit 2. All five CI jobs passed for implementation `bfb8c0f22a6106e3e78dd6ac77b26c1bdcd5b580`, tested tree `15a8a2b046bd31a6f12636887e11ca3a35ed358f`, in [run 39349329396](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39349329396). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 79 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T00:51:26.625001+00:00. This supplies the results pipeline embedding; master registration/authentication, achievement evaluation/local profile persistence, bots, Tutorial, other commands and complete standalone startup remain incomplete. See [RESULTS_PIPELINE.md](RESULTS_PIPELINE.md).

## Previous verified checkpoints

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

# Current capture-zone checkpoint

The capture-zone batch passes **6,300 component + 46 integration = 6,346 Java tests** across 113 suites, with zero failures, errors or skips. Consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **65 selected original TC/Multi-Hill/objective/escape-watch tests** pass against updated source `546c548780c400f21d73ca6713f86194ceb3c844`. Earlier CTF/runtime test evidence remains scoped to its recorded snapshots.

The seven new `main` commits and all 29 changed reference/config/workflow files are imported exactly. The six original source directories match `main` at `546c548780c400f21d73ca6713f86194ceb3c844`. All 262 frozen Java fixture/catalog resource paths remain byte-identical; their historical provenance stays intact. Of the preceding 265 checked paths (which also included three generator outputs), only `PacketCodec.java` changes intentionally to add packet 106. The source inventory now audits 1,164 files and 277,001 Python/Cython source lines at the new baseline. The packet generator reproduces all three outputs exactly.

The CRC-valid JAR is 2307094 bytes, SHA-256 `4c4dcd50fa7db004f0f11304df8b7e68e70f0eeaf8e7ee62316bbd0c4a4b2ee4`, with class major 69. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. CI [run 39315247712](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39315247712) targets implementation `13bf1e85f556bc5a9ef2197bd368ea15c33982f5`, merge tree `8dd0a01a8939a34a96fd90f7809277bfc3a8e0b8`. Build, native ENet, Ubuntu and Windows passed; all 53 final steps in those four jobs completed successfully or were intentionally skipped. Native impaired transport/loader checks and all 77 configured reference generators passed, and the build byte-compared repeated application archives. Ubuntu and Windows smoked the uploaded artifact and passed component/integration tests. **macOS remains queued awaiting a runner; all-five-job verification is pending** (observed 2026-10-19T20:12:28.882372+00:00). No macOS pass, local-to-CI artifact hash comparison or supported-client acceptance is claimed. Full migration, achievement/persistence/service composition and supported-client acceptance remain incomplete.

See [ZONE_RUNTIME.md](ZONE_RUNTIME.md).

## Previous CTF checkpoint

# Current CTF and runtime checkpoint

The current accumulated implementation batch passes **6,294 component + 44 integration = 6,338 Java tests** across 113 suites, with zero failures, errors or skips. The consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **47 selected original CTF/Classic/objective regressions** pass freshly. The preceding 326 selected Python checks were run for the preceding implementation snapshot and were not rerun here.

Packet-generator reproduction is exact for all three outputs. Frozen Java fixtures/catalogs and the six original source directories remain unchanged. The CRC-valid JAR is 2261083 bytes, SHA-256 `3206345496e67d6a95b54772b888b3b24c8262cdad26f3b942ea77ffc6853091`. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. All five CI jobs passed for implementation `97b05b197999db083cf70849c6f3c82249cc4d17`, tree `83b43cd911c4f3d4ea8b516e571bb8e71d546b14`, in [run 39308869800](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39308869800). Every final job step completed successfully or was intentionally skipped. The build verified repeated JAR bytes; the native job passed impaired transport/loader interoperability and rebuilt all 77 configured reference generators; Ubuntu, Windows and macOS each smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash comparison or supported-client gameplay acceptance is claimed. Full migration, production service composition and supported-client acceptance remain incomplete.

See [CTF_RUNTIME.md](CTF_RUNTIME.md).

## Previous published checkpoint

# Movement-network checkpoint

Local Java check/build passes **6,276 component + 41 integration = 6,317 tests**,
with zero failures, errors or skips across 112 suites. **852 focused original-source
regressions pass freshly**: the preceding 795 plus the complete reversed movement engine
module. Three grouped component tests were added. Existing ClockSync session coverage and
the existing production-factory two-peer UDP scenario were extended.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2079943 bytes, SHA-256
`0254c72e2cecbfd3fc86a1a9dda90f9f91996624e04cfcbc7c96cb370a0c7c07`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `45fb8a93a8412ce11fc23c44938870ec6ac5e7c8`, tested/published tree
`5a28c2fe4fc0a8319903dd9f929e4f5797eb8247`, in [run 39191321035](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39191321035). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

## Previous hosted-input checkpoint

# Hosted input checkpoint

Local Java check/build passes **6,273 component + 41 integration = 6,314 tests**,
with zero failures, errors or skips across 111 suites. **795 focused original-source
regressions pass freshly**, including complete movement-anticheat, ammunition, score-event
and reversed-WorldUpdate modules. Two grouped component tests were added; the existing
production-factory UDP scenario was extended. Existing policy/golden matrices were retained.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2069837 bytes, SHA-256
`083adcac909e4f6e14cad039548a3d24eac1a012a27d1c4d196d40d9f0c597cb`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `ab156d494ff15da6649b8cdd0f925eef2963f66b`, tested/published tree
`6794d160e3ddac77d8b92dc15aa34a0c59328373`, in [run 39189298397](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39189298397). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

## Previous hosted-combat checkpoint

# Hosted shot and terrain checkpoint

Local Java check/build passes **6,271 component + 41 integration = 6,312 tests**,
with zero failures, errors or skips across 110 suites. **673 focused original-source
regressions pass** freshly: the preceding 417 and seven complete combat regression modules.
This batch adds two grouped component boundaries and extends the existing production-factory
UDP scenario. Existing combat/terrain/firing/rewind policy goldens remain in use.

All 264 preceding frozen paths are unchanged. The existing domain data exporter now also
exports the original block-destruction profile statistic into one new catalog; repeating
that export is byte-identical. There are 265 frozen paths and still 77 configured generators.
The other 76 generator/native-build evidence is reused locally; CI independently rebuilds
and reproduces all 77. No new policy oracle, duplicated matrix or production dependency is
introduced. Two CRC-valid local archives are byte-identical: 2063142 bytes,
SHA-256 `2c2e418f3699783bf2763ac28616e7fc354f668cd9d37f118075b0b290770a3f`. Class major 69, packaging/licenses,
version/config smokes and all four fresh native transport/packaged-loader profiles pass.
The native loader uses FixtureScene; ordinary hosting exits 2. No supported retail-client
test, complete platform gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `3a61a755aaf90b86a14d8f7bfecd32b4ed25a52e`, tested/published tree
`8b360bd0bfe3fb6c757f55df05e9c8e4c08bd123`, in [run 39187427065](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39187427065). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

## Previous hosted-entity checkpoint

# Hosted entity and map-resource checkpoint

Local Java check/build passes **6,269 component + 41 integration = 6,310 tests**,
with zero failures, errors or skips across 109 suites. **417 focused original-source
regressions pass** freshly: all preceding 353 plus 64 cases from the complete entity
registry, behavior, map-resource and attachment modules. Three grouped component tests
are added; the existing session backpressure test and production-factory UDP scenario
are extended. Existing policy goldens and other integration scenarios remain in use.

All 264 frozen paths are unchanged. Existing 77-generator/native-build evidence is reused
locally; CI independently rebuilds and reproduces all 77. No new generator, production
dependency or duplicate policy matrix is introduced. Two CRC-valid local archives are
byte-identical: 2051464 bytes, SHA-256 `6e6aff40416d134334e8b0fc1ee4aa146a9169744068475981b819569f4c5610`.
Class major 69, packaging/licenses, version/config smokes and all four fresh native
transport/packaged-loader profiles pass. The native loader uses FixtureScene; normal
hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `69442fee5df3a733d9ab73f87b1721e13857bf54`, tested/published tree
`12580a72376f10cabb0068d55c179f26248271b0`, in [run 39185377778](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39185377778). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. The initial focused-source subprocess omitted the
already-built native ENet reference directory from PYTHONPATH: four imports failed while
413 cases passed. With that directory restored, all 417 unchanged cases pass. The original
medpack/Zombie/VIP baseline discrepancies remain unwaived; focused passes do not establish
a passing full original baseline.

## Previous factory/player-runtime checkpoint

# Factory and player-runtime checkpoint

Local Java check/build passes **6,266 component + 41 integration = 6,307 tests**,
with zero failures, errors or skips across 108 suites. **353 focused original-source
regressions pass** freshly: the preceding 350 plus three configuration integration and
clamp tests. This batch adds three grouped component boundaries and one real UDP test
for the production factory/player runtime. Existing movement, replication, repair,
team-balance, profile/combat and scene-transition policy suites remain in use.

All 264 frozen paths are unchanged. The existing 77-generator/native-build evidence is
reused locally; CI independently rebuilds and reproduces all 77. No new generator,
production dependency or duplicate policy matrix is introduced. Two CRC-valid local
archives are byte-identical: 2036880 bytes, SHA-256
`f7fcea3f608af9d877c84c5201eb6f58933a47cf9b955c98965f3f8b254244ee`. Class major 69, packaging/licenses, version/config smokes and
all four fresh native transport/packaged-loader profiles pass. The native loader uses
FixtureScene; normal hosting exits 2. No supported retail-client test, complete platform
gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `f96bbca34a9880450ecc27e81a444a8ba9d35977`, tested/published tree
`a781f34b3178525f352f01399a1ff8897fb71788`, in [run 39183158502](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39183158502). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. A truncated intermediate repeat archive was rejected;
a subsequent rebuild passed CRC and byte-equality checks without source/assertion changes. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

## Previous production-assembly checkpoint

# Production-assembly checkpoint

Local Java check/build passes **6,263 component + 40 integration = 6,303 tests**, with
zero failures, errors or skips across 107 suites. All **350 focused original-source
regressions pass** on a fresh run. Seven new component checks cover three shared-stream
source spawn traces, admission/score/team/respawn ownership, TDM/Arena installation and
rollback, shutdown and reliable roster visibility. One four-peer real UDP check uses the
production Arena assembly and asynchronous frame pump. Existing policy traces remain
in use; an interior spawn trace that duplicated the dry-map result was removed before
publication. Earlier broad test reductions remain intact.

The new intact-source spawn generator reproduces both outputs byte-for-byte, with 18
positions and shared random continuations across three traces. All 262 preceding frozen
paths are unchanged: 264 checked paths and 77 configured CI generators. The preceding
76-generator/native-build evidence is reused locally; CI must rebuild/reproduce all 77.
Two CRC-valid local archives are byte-identical: 2014236 bytes, SHA-256
`fcfc54c4ef6e1cb064271c2d35dafad72a4826a8ce0b1a3a45b4cebd8e5e9f87`. Class major 69, packaging/licenses, version/config smokes and
all four fresh isolated native transport/packaged-loader profiles pass. The native loader
still uses FixtureScene. Normal hosting exits 2. No complete platform gameplay,
supported retail-client test or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `aea96236a466b48d5307941385f8eb01951f6eb2`, tested/published tree
`6c278e641b9d1808625e3d03b94901007879b4ef`, in [run 39180327801](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39180327801). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators, including the new
BaseMode/native-WorldManager/final-rescue spawn-chain traces. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and passed
source-built component/integration tests. Every final step completed successfully or
was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed.

The first impaired-native attempt returned disconnect reason 0 instead of required 18.
A fresh local repeat and the same-commit native CI rerun passed with assertions and
deadlines unchanged. The initial failure is retained in verification-results.json; no
transport fix or broader reliability conclusion is inferred from a successful retry. Full migration is
incomplete. The existing medpack/Zombie/VIP original-baseline discrepancies remain
unwaived; these focused passes do not establish a passing full original-source baseline.

## Previous Arena checkpoint

# Arena and early-audio checkpoint

Local Java check/build passes **6,256 component + 39 integration = 6,295 tests**,
with zero failures, errors or skips across 106 suites. **350 focused original-source
regressions pass**. The batch adds 14 stateful native Arena/lifecycle traces, two seeded
join-audio traces, one grouped settings check, one audio identity/fatal/owner boundary,
one admission-audio backpressure boundary and one four-peer real UDP composition.
Existing TDM/media source fixtures and tests remain in use after lifecycle extraction.

The new intact-source generator reproduces both outputs byte-for-byte; all 260 earlier
frozen paths retain their hashes, for 262 checked paths and 76 configured CI generators.
Unchanged prior 75-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 76. The broad earlier test-optimization reductions remain intact;
no duplicate full-suite or extra UDP setup is added for shared BaseRoundMode behavior.

Two CRC-valid local archives are byte-identical: 1975623 bytes,
SHA-256 `0ac9955af9fff6bc9f33111dfafb589fe393642baf3f26ab223da53f26ffeb59`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `03a94ffc6c9988fa92c29ad4e0b2fbaa9bb8d3a9`,
tested/published tree `66ed8b6cc3e0f7e11a4b4d4b8cf821007f5ec5dc`, in
[run 39177842753](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39177842753). The native job independently reproduced all 76
configured original generators, including the Arena/early-audio traces and all earlier
fixtures. The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked
the uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

## Previous gameplay-services checkpoint

# Gameplay services checkpoint

Local Java check/build passes **6,237 component + 38 integration = 6,275 tests**,
with zero failures, errors or skips across 104 suites. **340 focused original-source
regressions pass**. The batch adds 24 stateful original TeamBalancer/forced-team-change
traces, one grouped gameplay-audio trace and four async/failure boundaries. The existing
three-peer UDP controls composition now also exercises actual HostedGameplayServices:
forced move, retail notice, same-frame respawn on the new team, team-relative cues,
spectator exclusion, unreliable personal/positioned cues and centred hearing stimuli.
No new UDP test duplicates that existing setup.

The new intact-source generator reproduces both outputs byte-for-byte; all 258 earlier
frozen paths retain their hashes, for 260 checked paths and 75 configured CI generators.
Unchanged prior 74-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 75.

Two CRC-valid local archives are byte-identical: 1959105 bytes,
SHA-256 `33830806f66e60a95580d39c9cb2a2eb43ac1af8b2301ad77a395b4293ceee3c`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `9dd88dd9c7dc6bef6e66911d24665077c4980617`,
tested/published tree `bd29520ff81cb0da9116820738dd84bc29645cf6`, in
[run 39175987373](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39175987373). The native job independently reproduced all 75
configured original generators, including the balancing/audio traces and all earlier
fixtures. The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked
the uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

## Previous player-control checkpoint

# Java 25 migration report

Date: 2026-10-18 UTC. **Incomplete migration; not playable or release-ready.**
The current batch implements live team switches, atomic class/loadout changes, bounded
palette relays, spectator admission and actual life/team/event/host bindings. Production
factories, remaining gameplay and services remain pending; normal hosting exits 2.
See [PLAYER_CONTROLS.md](PLAYER_CONTROLS.md).

Local Java check/build passes **6,208 component + 38 integration = 6,246 tests**,
with zero failures, errors or skips across 102 suites. This batch adds 24 intact-source
team/class/palette traces, one focused timer/identity boundary, four spectator session
boundaries and one real UDP composition. **317 focused original-source regressions pass**.
The new generator reproduces both outputs byte-for-byte; all 256 earlier frozen paths
retain their exact hashes, for 258 checked paths and 74 configured CI generators.
Unchanged prior 73-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 74.

Two CRC-valid local archives are byte-identical: 1930149 bytes,
SHA-256 `37e41873cf88ada4444234fc5f51fc357b183696c248b130b5acd40a59538815`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four current isolated native transport/packaged-
loader nominal/impaired profiles pass. The loader still uses FixtureScene. Platform
tests run source-built component/integration classes and smoke the uploaded JAR. No
local-to-CI artifact hash identity or complete platform gameplay is claimed. Prior
medpack/Zombie/VIP original-baseline discrepancies remain unwaived; these focused passes
do not establish a passing full original-source baseline.

Review of the first published player-control checkpoint found a loading race: a
spectator may select a playable team before packet 110 or an admission retry. Its last
published CreatePlayer still describes team 0 until respawn. JoinScene/ConfiguredScene
now require the current canonical domain spectator predicate, implemented by LifeRuntime,
instead of inferring it from that old packet. A dedicated session regression retains the
old spectator roster row while changing the current actor's team and verifies that packet
110 cannot bypass normal first-input admission. The unused roster predicate is removed.
The correction has a fresh full suite/artifact/native check and a successful five-job CI run.

All five CI jobs passed for implementation `f9964fe5185dcf36a353411aac9e85971fdf06e5`,
tested/published tree `c7ebef88711a7fd12902a3c607c7c350bf7d56af`, in
[run 39174391954](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39174391954). The native job independently reproduced all 74
configured original generators, including live team/class/palette traces and all earlier
fixtures. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and ran source-built
component/integration tests. Every final job step completed with success or an intentional
skip. No local-to-CI artifact hash identity or complete platform gameplay is claimed.

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
- Previous input implementation: `7d0d68e8e3fda4974fe7ebc070e24f7833b74a25`.
- Previous input tested tree: `e7c13efb769da9833c27e056f69df57832118a30`.
- Previous input CI: [run 38830815361](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361); all five jobs passed.
- Previous infantry implementation: `1e4c34fced6e6fc96185fd72b212d62b1e3eab70`.
- Previous infantry tested/published implementation tree: `4a54308beb4e425dbfa9ea0ab2ba0176b619ccf6`.
- Previous infantry CI: [run 38834640118](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38834640118); all five jobs passed.
- Previous flight implementation: `0f943c69d4d6634b2eaebacc5512adfaf0399aaa`.
- Previous flight tested/published tree: `e1742fc548591395ce8e44735ad0f2e543c38bae`.
- Previous flight CI: [run 39068854866](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39068854866); all five jobs passed.
- Previous replication implementation: `00d96933315fc201e44234912e6e95c6ed17609d`.
- Previous replication tested/published tree: `276cf8712f4801de0171f8a8b37380a78c4f04e8`.
- Previous replication CI: [run 39071633850](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39071633850); all five jobs passed.
- Previous combat-timing implementation: `0f6448231be6e93f3ce58d4682d9cd9963ac95ef`.
- Previous combat-timing tested/published tree: `9acb23de8fa14ae82f90188bf67f741bae3f563b`.
- Previous combat-timing CI: [run 39073877248](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39073877248); all five jobs passed.
- Previous shot-geometry implementation: `29156d7ccb3c47b582f7ae5570dcfde204c13282`.
- Previous shot-geometry tested/published tree: `a0ab3bedac6a0777a30c620267bdb8188e4dc1f9`.
- Previous shot-geometry CI: [run 39075436598](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39075436598); all five jobs passed.
- Previous owned-combat implementation: `76a80d9f4b7840ab6730157215e69995d66323fd`.
- Previous owned-combat tested/published tree: `07ce07657fc3a84285fd212673dcef0e8d7ccab2`.
- Previous owned-combat CI: [run 39078168462](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39078168462); all five jobs passed.
- Previous terrain implementation: `939ac63a822d0ceacfb873ee0acc881e717457f8`.
- Previous terrain tested/published tree: `b338c0bc04772517821ab0b32e5c93199877c27f`.
- Previous terrain CI: [run 39079408578](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39079408578); all five jobs passed.
- Previous terrain-repair implementation: `d6990d6e33181547bfa38068bb228372245e79bb`.
- Previous terrain-repair tested/published tree: `64cf08a198b804bda991d1886d15cff051c3ff8a`.
- Previous terrain-repair CI: [run 39080190230](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39080190230); all five jobs passed.
- Previous entity-registry implementation: `82f63e3f03900cbbd75e365680d91ee2b766a84e`.
- Previous entity-registry tested/published tree: `a437b5b53ec912b75898600d69caba2114da647d`.
- Previous entity-registry CI: [run 39081721193](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39081721193); all five jobs passed.
- Previous shared-blast implementation: `a3cbfff3738acf5ece8012113106633e2ef68463`.
- Previous shared-blast tested/published tree: `78f4a9f75500e105e316df6c04a547a8e0309a4f`.
- Previous shared-blast CI: [run 39083832978](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39083832978); all five jobs passed.
- Previous medpack/radar implementation: `18f5d79818cefad2f96f5ee1a2f222862cbf989e`.
- Previous medpack/radar tested/published tree: `db584d7612c7986e694f16becd1b057cbfad58ef`.
- Previous medpack/radar CI: [run 39084849726](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39084849726); all five jobs passed.
- Previous physical-crate implementation: `991258442ac28a276edf8cf4ae95a5fb01b96a81`.
- Previous physical-crate tested/published tree: `790ff768096e07abc44ebd936894bce4bf661452`.
- Previous physical-crate CI: [run 39086168143](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39086168143); all five jobs passed.
- Previous map-resource implementation: `56bb37003980c55de8e37afdba4ed1ac98b2af71`.
- Previous map-resource tested/published tree: `4346f7694523cdac88ee6408168218536667f317`.
- Previous map-resource CI: [run 39088025414](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39088025414); all five jobs passed.
- Previous objective-carry helper implementation: `c9a79aa31e5355acab2ae3c35a49dfd12dd4f4a0`.
- Previous objective-carry helper tested/published tree: `0a6179e3ac36a641d2a554e9af60ec96ab5239eb`.
- Previous objective-carry helper CI: [run 39089220082](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39089220082); all five jobs passed.
- Previous objective-drop-request implementation: `f84595b3b762d54b73107a9c17b4c84d927c2a5f`.
- Previous objective-drop-request tested/published tree: `3ff2290a794a17c0c32d00c7b72e91c47c4ac61c`.
- Previous objective-drop-request CI: [run 39091489230](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39091489230); all five jobs passed.
- Previous match-foundations implementation: `f85249decdfda99581ab49bf12f7a54cfa04bbf8`.
- Previous match-foundations tested/published tree: `5026f58785d08d3e9e93538d833ba80542dda1d7`.
- Previous match-foundations CI: [run 39136499560](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39136499560); all five jobs passed.
- Previous profile/combat/objective score implementation: `dd76bffca873499b9b598436b741c11fb14ff102`.
- Previous profile/combat/objective score tested/published tree: `93af458453543adc629241612467e984ff1c3105`.
- Previous profile/combat/objective score CI: [run 39139463520](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39139463520); all five jobs passed.
- Current combat-economy/escape/clock implementation: `6f904c4f28917a03e3d0a94de259e84213bd9025`.
- Current combat-economy/escape/clock tested/published tree: `7803354387083bbf86a98bfaea387ad65d8098ed`.
- Current combat-economy/escape/clock CI: [run 39144143660](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39144143660); all five jobs passed.
- Exact evidence and limitations: [CI_STATUS.md](CI_STATUS.md).
- Original Python/Cython production source is retained unchanged.

The clean checkout was cloned from the fork and matched the authoritative
21e5f91d52796069e9eb8f2a4c9de1385febc3c7 documentation HEAD and full tree.
CLI push lacks credentials; publication uses the connected GitHub app with an
expected-head lease and exact tested-tree comparison. No release is created.

## Historical TDM and serial runtime

Local Java check/build passes **5,971 component + 35 integration = 6,006 tests**,
zero failures, errors or skips. The batch adds 59 component tests and two actual UDP
compositions. **131 focused original TDM/runtime/scoreboard/lifecycle/end-sequence
regressions pass**. All 68 configured generators ran after fresh original five-module
native and ENet builds; all 244 frozen fixture/catalog/codegen hashes match.
The known flight provenance observation recurred: two impact metadata fields were
restored by rerunning the actual original generator's `--impact-only` command,
then all 244 hashes were compared again. No fixture or hash was waived.

Two CRC-valid local archives are byte-identical: 1769051 bytes,
SHA-256 `6823264954c6828247cdcb029aea3225a4c480984a7a5b6298398f368cbd2f7d`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four isolated native transport and
packaged-loader nominal/impaired profiles pass. Loader probes still use FixtureScene.
The full original baseline remains unverified: prior medpack/Zombie/VIP discrepancies
are retained and unwaived. These focused passes do not supersede those observations.

The concrete TDM core, canonical accepted-life score binding, serial mode/plugin
event drain and opt-in actual ENet packet queue are implemented. Complete departure
settlement, asynchronous simulation/bootstrap, delayed end/restart and actual
resource/audio/vote/achievement/result services, all other modes and supported-client/
capacity/soak gates remain pending. See [TDM_RUNTIME.md](TDM_RUNTIME.md).

All five CI jobs passed for implementation `e34737309f70ba099006e163f137f58b88af39e4`,
tested/published tree `e0d4db0939fe99e3c4ba9f11003cb3124bea8f94`, in
[run 39149867337](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39149867337). The native job reproduced the original fixtures and the
build byte-compared repeated archives. Ubuntu, Windows and macOS smoked one
uploaded JAR and ran source-built component/integration tests. Every final job
step completed with success or an intentional skip. No local-to-CI artifact hash
identity is claimed.

## Preceding test optimization

Local clean check/build passes **5,912 component + 33 real UDP = 5,945 Java tests**,
with zero failures, errors or skips. All five jobs passed for implementation
`f14407e5596d3f2f8a3869e2ad1c8505970c3f58`, tested tree `93a158fd85cadd67710ad5bac64e4ad2bde174f8`, in [run 39145475640](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39145475640).
CI regenerated the original fixtures, checked archive reproducibility, smoked the
same uploaded JAR on three platforms and ran component/UDP tests from source-built
classes on Ubuntu, Windows and macOS. No local-to-CI artifact hash identity is claimed.

The optimization removes **3,595 duplicate independent fixture executions** and
53 artificial JUnit partitions, preserving every distinct case, all 224 ordered
stateful trace selections and all 33 integration tests. Match fixture gzip reads
drop from 328 to 5 per test worker. Production code, golden bytes, generators and
workflows are unchanged; the local JAR is byte-identical to the preceding checkpoint.
See [TEST_OPTIMIZATION.md](TEST_OPTIMIZATION.md) for exact case counts and timing limits.

Prior original-source baseline failures remain recorded and unwaived. This test-only
checkpoint reuses the unchanged local native/source evidence; fresh CI checks pass.
The migration remains incomplete and hosting exits 2.

## Preceding gameplay implementation

Local clean check/build passes **5,965 component + 33 real UDP = 5,998 Java tests**,
zero failures, errors or skips. The expanded original suite has **162 passes
and one unchanged medpack/context baseline failure**, with no errors or skips.
That failure is retained and unwaived; this is not a passing full-source baseline.
The batch adds 96 component tests, two real UDP compositions, 10,752 policy/codec/HUD
rows, 1,824 native voxel edits and 6,144 direct escape geometry evaluations. All 67
configured generators reran after fresh original five-module native and ENet builds;
all 241 recorded fixture/catalog/codegen hashes match. The frozen set includes
existing foundation files, without claiming every file is generated by those scripts.

Concrete mode/canonical-row/reload/bonus/exemption/event and end/result/persistence
bindings remain required. RoundClock implements fixed-budget arbitration; complete
serial asynchronous dispatch and delayed mode end/restart services remain pending.
Original baseline and supported-client/capacity/soak gates remain pending; earlier
unchanged Zombie/VIP and medpack-context discrepancies are unwaived. Hosting exits 2. Native packaged-loader
probes retain FixtureScene. See MATCH_FLOW.md.


Local clean check/build passes **5,869 component + 31 real UDP = 5,900 Java tests**,
zero failures, errors or skips. **91 focused original tests** pass.
The batch adds 72 component tests, two real UDP compositions and 10,240 unchanged
source observations. All 66 configured generators reran after a fresh original
five-module native build plus ENet; all 238 recorded fixture/catalog/codegen hashes
match. The frozen set includes existing foundation files, without claiming those
are all regenerated by the configured scripts.

Concrete mode/event/score-presentation, known-roster and result/persistence bindings
remain required. Full original baseline and supported-client/capacity/soak gates
remain pending; earlier unchanged Zombie/VIP discrepancies are unwaived. Hosting
exits 2. Native packaged-loader probes retain FixtureScene. See SCORE_EVIDENCE.md.


The preceding match-foundations batch implements TeamState, EscortTracker, ObjectiveGuard/
TrapTimer, RoundCombatStats/Scoreboard and two native result codecs. It adds
20,992 source observations and two combined three-client UDP flows. Local clean
verification passes 5,826 Java tests and 53 focused original tests. All 65 configured
generators reran after a fresh native build; all 235 recorded hashes match.
Explicit mode/life-event/presentation/known-roster/profile/result ports remain;
these components do not establish complete playable rounds. See MATCH_FOUNDATIONS.md.

ObjectiveDropRequests compares 2,048 actual unchanged registered-coroutine
requests with the original Player/native body, helpers and registry. A new
three-client UDP test checks bound authority, mode consumption, generic placement
and suspended death with a newer server loop. LifeRuntime routes packet 71 through
a once-before-admission binding with required completion handling. Concrete modes,
outer async packet draining and dropped-object simulation remain pending.
The preceding objective-drop checkpoint passed 5,662 Java tests; all 223 hashes reproduce through 62
configured generators after a fresh native build. See OBJECTIVE_DROP_REQUESTS.md.

ObjectiveCarry adds 4,096 unchanged helper/native-body operations, 8,192 arbitrary
sanitize vectors and 512 native DropPickup codec cases. A new three-client UDP test checks native burden,
dedicated late carrier reveal, capped drops, reliable send-failure state/retry and
spawn clear. The preceding objective-carry checkpoint passed 5,644 Java tests. Explicit reveal/send
composition does not establish concrete objective modes or outer async scheduling,
dropped-object simulation or complete snapshot/mode/gameplay binding.
See OBJECTIVE_CARRY.md.

MapResourceService and server VXL loading add 76 marker cases / 18,200 voxel
observations and 48 unchanged-source resource traces. A new three-client UDP test
uses actual MapAssembly, registry and life refills to verify canonical lights,
owner-only health/audio, late replay, flyby and repeated resource replacement.
The preceding map-resource checkpoint passed 5,563 Java tests. Score/achievement/audio/reveal ports
remain explicit; persistent achievement-engine and full round/gameplay binding
are not established. See MAP_RESOURCES.md.

PickupCrateBehavior and PickupWorld add 8,192 unchanged-source operations for
native-world edits, live support/dry rescue, parachute timing, actual health/ammo/
block refill and respawn. Another 1,024 native codec cases cover PlaySound and
ChangeEntity, including source decoding/re-encoding quirks. A new three-client
UDP test checks all refill types, sky/late replay and reliable support movement.
The preceding physical-crate checkpoint passed 5,438 Java tests. Authored placement/refill/sound/reveal
ports remain explicit; map-resource assembly is verified above and full gameplay binding remains pending. See PHYSICAL_CRATES.md.

MedpackBehavior and RadarStationBehavior add 1,024 unchanged-source lifecycle/
healing operations and 32 actual heal reliable-IO faults. A new three-client UDP
test checks shared cooldowns, owner-only HP, live radar fuse replay and owned-shot
cleanup. The preceding care/radar checkpoint passed 5,373 Java tests. Authored placement/healing/
reveal composition remains explicit; full gameplay gates remain pending.
See CARE_RADAR.md.

BlastRuntime, BlastTerrain, ExplosionGeometry and the explosive device behaviors
add 10,132 unchanged-source comparisons. Dense accepted receive sequences control
recomputed pushes through the real mover. A new real three-client UDP owned-shot
mine chain checks HP/credit/ammo, native/exact outcomes and delayed pushes. The preceding blast checkpoint
passed 5,340 Java tests. Authored placement/test domain ports remain
explicit; complete services/gameplay/client gates are pending. See BLAST_RUNTIME.md.

EntityRegistry and its base behavior/visibility model add 7,680 source registry
operations, 512 native wire cases and 16,388 integer-set snapshots. A new real
three-client UDP test routes owned shots through actual damageable objects and
checks reliable hit-before-destroy order and per-scene knowledge. The preceding registry checkpoint
passed 5,168 Java tests and 57 focused original tests. Concrete device, corpse,
projectile and complete gameplay binding remain pending; see ENTITY_REGISTRY.md.

TerrainRepair adds 3,072 unchanged-service operations and expands the existing
three-client UDP terrain test with delayed collapse confirmations and rejected-
prediction repair using the latest color/health. The preceding repair checkpoint passed 5,081 checks.
Settings/recipients/network ports are concrete test bindings; full gameplay-loop
integration remains pending. See [TERRAIN_REPAIR.md](TERRAIN_REPAIR.md).

TerrainDamage, TerrainHealth, TerrainCollapse and BlockDamageModel add 10,483
unchanged-source operations, plus 163,008 expanded-cell comparisons. A real
three-client UDP test composes admitted bullets/spades, map health/collapse,
block credit and a fresh join's ordered health reveal. Concrete sound/profile/
score/achievement/mode and repair-scheduling ports remain required; see
[TERRAIN_DAMAGE.md](TERRAIN_DAMAGE.md).

CombatRuntime, DirectDamage, HitEffects and ShotTally add 540,416 unchanged-source
operations. Two new automatic-dispatch UDP tests verify actual damage/death,
wallet/cadence, excluded-owner feedback and delayed target rewind with live bodies
unchanged. Mode/profile/scoring/achievement and terrain/entity/corpse interfaces
are required concrete work; test callback boundaries do not establish full service
parity. See [COMBAT_RUNTIME.md](COMBAT_RUNTIME.md).

ShotValidation, CombatGeometry, FiringPatterns and HitTrace now compare 87,260
new unchanged-source operations. A real UDP test binds decoded shots to actual
lives and verifies origin rejection, target/body selection and an intervening
committed wall without health/ammo mutation. The new owned commit evidence is separate from this earlier read-only test.
See [SHOT_GEOMETRY.md](SHOT_GEOMETRY.md), including the explicit
numeric intersection roundoff bound and exact classification/probe checks.

PlayerFire now executes original reload/fire cadence and wallet behavior with
real link measurements. Owned housekeeping emits reload completion; packet 76
uses bound identity/current tool and ignores completion claims. CombatCatalog and
PelletSpread preserve all original weapon data and own-frame seeded bloom.
LagCompensation records physical hitboxes before movement, keeps life/teleport
barriers and computes read-only views with bounded arrival-dated late-shot
allowance. Source comparisons total 67,151 operations; two additional UDP tests
exercise delayed-shot views and reliable reload boundaries. The original
reference's 152 focused tests pass. Concrete domain integration remains
pending; see [COMBAT_TIMING.md](COMBAT_TIMING.md).


ReplicationService now owns original cadence/recovery/transition rules, grouped
and split delivery, reorder spacing, signed entity-tail filters, tool sanitization
and causal owner history. Live rows compose owned movement/health/flight with
required trusted presentation domains. Real ENet receipts gate new-life rows and
ordered-to-unsequenced transitions. A composed two-client UDP test delays actual
ACKs across respawn and verifies row withholding/resumption. See
[WORLD_REPLICATION.md](WORLD_REPLICATION.md) for exact source evidence and limits.


PlayerFlight now runs original fuel/start/defer/tail/release/recharge policy,
canopy edges/clearance/owner-label schedule and impact-speed damage around the
production mover. FlightProfile unifies negotiation bytes and the admitted life;
real ENet RTT reaches the owned flight state. Flight transitions guard backlog
catch-up; spawn/death and dedicated jetpack-crate refill preserve source order.
Original full-path comparisons cover 65,497 operations in 44 traces, with 10,368
additional canopy-impact vectors; 117 focused
original flight tests pass. The additional UDP test proves negotiated thrust,
fuel/release and a real safe canopy landing with explicit test ports.
See [PLAYER_FLIGHT.md](PLAYER_FLIGHT.md) for exact scope and timing heuristics.

InfantrySimulation now drives the actual world-bound mover from one accepted or
refilled phase per fixed step. PlayerMotion retains current façade controls,
source starvation/backlog policy, bounded eye/queue history, labelled impulses and
fall timing. Required ports preserve original reload/lag/after-tick boundaries;
the flight binding extends this path; Crumbling Run still fails explicitly.
The original full
SimulationRuntime/Player/native-World path supplies 16,683 exact operations in 33
traces; real UDP now drives owned movement and a genuine lethal landing's ordered
health/death packets. See [INFANTRY_SIMULATION.md](INFANTRY_SIMULATION.md).

InputTimeline records bounded immutable frames and receive metadata, preserves the
fresh immediate-state gate, selects source-latched movement phases, salvages late
presses and retains connection action evidence across life resets. ActionClock
preserves label/arrival cadence and server-time token budgets. The infantry binding
above adds outer pacing, impulses and eye history, and PlayerFlight adds connected
equipment resources and physical transitions. Actual lag compensation and upstream rate/conduct
admission remain pending. Raw hit packets do not authorize damage. Original methods
execute unchanged for 14,234 input
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

Previous components plus life codecs now cover 77 packet IDs, framing/LZF/XOR, range coder, bounded
ENet host/peers, ACK/retry/fragmentation/RTT/ordering, application loader/password/
capability/ID reservation, full/delta map snapshots and committed terrain/roster
journals with a first-input live gate. VXL/KV6 codecs, core/join TOML validation and
the 60/30 Hz scheduler are available. They do not provide the missing game domain.
See [APPLICATION_LOADING.md](APPLICATION_LOADING.md) and [FEATURE_PARITY.md](FEATURE_PARITY.md).

## Actual verification

| Check | Current local result |
|---|---|
| Java 25 clean check/build | PASS |
| Unit/component tests | 5971 passed; 0 failures/errors/skips |
| Real UDP integration tests | 35 passed; 0 failures/errors/skips |
| Flight full-path source comparisons | 65,497 exact operations / 44 traces; 10,368 canopy impact vectors; 117 focused original regressions pass |
| Infantry full-path source comparisons | 16,683 exact operations / 33 traces; 57 focused original regressions pass |
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
| Current-tree independent platform CI | PASS: all five jobs; source regeneration, repeat archive and same uploaded JAR on three OSes |
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

Current flight CI: [run 39068854866](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39068854866), all five jobs passed on
`0f943c69d4d6634b2eaebacc5512adfaf0399aaa`. It regenerates all seven flight parts and the impact fixture,
plus earlier source gates; verifies native probes and reproducible packaging; and
tests one uploaded JAR on Linux, Windows and macOS. Complete gameplay remains
unverified. No local-to-CI artifact hash comparison is claimed.

Previous infantry CI: [run 38834640118](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38834640118), all five jobs passed
on `1e4c34fced6e6fc96185fd72b212d62b1e3eab70`. It reproduces the full infantry fixture/provenance and
all earlier source gates, verifies native probes, byte-compares its rebuilt JAR,
and tests one uploaded artifact on three OSes. No local-to-CI artifact hash
comparison or complete gameplay result is claimed.

Previous input/cadence CI: [run 38830815361](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361), all five migration
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

## Additional original baseline observation

The expanded physical-crate reference run passed 80 original tests and failed
`test_zombie_pick_countdown_sound_ends_on_the_pick`. That test expects the sound
at 7.5 seconds remaining; the unchanged implementation uses a 4-second lead.
Source and tests remain unchanged. This is retained alongside the earlier VIP/
incomplete-baseline evidence; the complete original baseline is not passing.

## Remaining work and readiness

Next: complete concrete objective-mode ownership, outer async scheduling and dropped-object binding, then placement policy, corpse/projectile simulation
and full repair/entity gameplay-loop integration; connect detached projectiles, full score/award/aim services and upstream
input/equipment authorization; wire the owned Character/combat/movement/flight
components to concrete modes and authenticated identity. Complete presentation,
bot and registry domains remain required.
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

The initial implementation `a831f1fe8226cde5dae8c429727eb4f3202409a3` passed the
build, native, Ubuntu and Windows jobs in run 39143288035. macOS component tests
and artifact smokes passed, but the existing UDP replication/respawn test timed
out after ACK release. Its test synchronization now waits for reliable receipts
without expiry, then broadcasts at cadence until the released row is received.
The row-hold assertions and 4s/15s timeouts remain unchanged. This failure and its
logs are retained; the corrected checkpoint passes all five jobs in run 39144143660.
