# Hosted input checkpoint

Hosted held-tool authorization, source-ordered recovery/unmount and fresh crouch/profile/score actions are bound to TDM/Arena. 6,314 Java tests and 795 focused original regressions pass locally. All five CI jobs are pending publication. Full hosting and migration remain incomplete. See [HOSTED_INPUTS.md](HOSTED_INPUTS.md).

## Previous hosted-combat checkpoint

# Hosted shot and terrain checkpoint

Owned packet-6 combat, terrain/collapse/repair/profile/scoring/events and exact frame timing are bound to TDM/Arena factories. 6,312 Java tests and 673 focused original regressions pass locally. All five CI jobs pass in [run 39187427065](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39187427065). Full hosting and migration remain incomplete. See [HOSTED_COMBAT.md](HOSTED_COMBAT.md).

## Previous hosted-entity checkpoint

# Hosted entity and map-resource checkpoint

Hosted entity simulation, map resources, reliable per-scene entity visibility and actual support/blast delivery are bound to TDM/Arena factories. 6,310 Java tests and 417 focused original regressions pass locally. All five CI jobs pass in [run 39185377778](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39185377778). Full hosting and migration remain incomplete. See [ENTITY_RUNTIME.md](ENTITY_RUNTIME.md).

## Previous factory/player-runtime checkpoint

# Factory and player-runtime checkpoint

Prepared-metadata TDM/Arena factories and connected-player physics, statistics, replication, repair and periodic service bindings are implemented. 6,307 Java tests and 353 focused original regressions pass locally. All five CI jobs pass in [run 39183158502](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39183158502). Full hosting and migration remain incomplete. See [PLAYER_RUNTIME.md](PLAYER_RUNTIME.md).

## Previous production-assembly checkpoint

# Production-assembly checkpoint

TDM/Arena concrete domains, prepared-map spawn binding, reliable known-player delivery and asynchronous host/frame assembly are implemented. 6,303 Java tests and 350 focused original regressions pass locally. All five CI jobs pass in [run 39180327801](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39180327801). Full hosting and migration remain incomplete. See [PRODUCTION_ASSEMBLY.md](PRODUCTION_ASSEMBLY.md).

## Previous Arena checkpoint

# Arena and shared-round follow-up

ArenaMode, shared BaseRoundMode, JoinAudio and actual early admission audio binding are implemented. 6,295 Java tests and 350 focused original regressions pass locally. All five CI jobs pass in
[run 39177842753](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39177842753). See [ARENA_ROUNDS.md](ARENA_ROUNDS.md). Full production hosting and remaining domains are pending.

## Previous gameplay-services checkpoint

# Gameplay services follow-up

TeamBalancer, GameplaySounds, PeriodicServices and their actual host binding are implemented. 6,275 Java tests and 340 focused original regressions pass locally. All five CI jobs pass in
[run 39175987373](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39175987373). See [GAMEPLAY_SERVICES.md](GAMEPLAY_SERVICES.md). Full production hosting and the remaining domains are pending.

## Previous player-control checkpoint

# Feature parity and explicit backlog

Current player-control verification: 6,246 Java tests and 317 focused original
regressions pass locally. All five CI jobs pass in
[run 39174391954](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39174391954). See [PLAYER_CONTROLS.md](PLAYER_CONTROLS.md);
complete gameplay and normal hosting assembly remain pending.

Reference: source `6216b771282d2d349ccd67ee58c378f035c83553`.
`Implemented component` means only the exact behavior and evidence listed here.
It does not imply that a playable server, end-to-end feature or migration milestone
has passed. Nothing below is a production-readiness claim.

| Component / original files | Java status | Verified evidence / next gate |
|---|---|---|
| shared/bytes.pyx | Implemented binary component | byte order, widths, UTF-8, BGR, truncation tests; legacy Python façade quirks not reproduced |
| server/util.py | Implemented LZF | source golden literals; backreferences, overlap, bounded malformed/fuzz tests |
| shared/packet.pyx | 88 distinct packet IDs encoded/decoded | 171 simple vectors, ClientData/complex and 11 loading +4 session packet vectors,4 profile and12 live-input vectors; remaining IDs pending |
| server/connection.py framing/decrypt | Implemented framing component | owned application sessions compose outbound framing, inbound LZF/XOR, password gate and clock routing; no full gameplay handler set |
| vendor ENet compress.c | Pure Java range coder | 30 native-C encode/decode vectors, rescaling/reset; bounded malformed fuzz |
| vendor ENet protocol.h | Implemented envelope component | all command layout sizes, MTU/bounds, network byte order, compressed region tests |
| vendor ENet protocol.c connect phase | Standalone handshake component | native pyenet CONNECT event + Java VERIFY_CONNECT ACK; no game client |
| ENet sequencing/fragments | Bounded peer components implemented | reliable reorder/dedup, 66,000-message sequence/time wrap, unsequenced windows, atomic outgoing bounds and grouped fragmentation |
| ENet host/session delivery | Server host/peer implemented; not fully certified | native ENet nominal/loss/reorder/duplicate/reconnect tests, ACK/retransmit/RTT throttle/windows, disconnect and backpressure; bandwidth-epoch redistribution and full capacity testing pending |
| InitialInfo/StateData/application handshake | Common configured builders/session binding implemented; game domain pending | 99 source packet pairs, config/map/team metadata, amendment order and actual-JAR native loading; identity, mode policies and actual player domain pending |
| Map transfer, CRC, late joins | Snapshot, committed journal and live gate implemented; **game client unverified** | 12 record hashes, immutable compression, bounded canonical catch-up, first-input gate, life-token roster reconciliation and native loader tests; marker cleanup and full player/entity lifecycles pending |
| aoslib/vxl.pyx | Loader / column serializer implemented | 6 synthetic source fixtures, 3,264 source point samples, classic64/floor/fill and source column bytes |
| aoslib/kv6.pyx | Format codec implemented | standard/legacy roundtrip and malformed bounds; model operations/palette interpretation pending |
| Voxel edits/collapse/world persistence | Owned damage/dig, health/shades, collapse and late-join health components implemented | Earlier committed-cell/column evidence plus 10,483 source operations and 163,008 expanded cells; actual three-client UDP wall/spade/health reveal. Repair scheduling has TERRAIN_REPAIR.md evidence; full loop/domain integration, construction/editor/projectiles and persistence remain pending; see TERRAIN_DAMAGE.md |
| shared/glm.pyx / aoslib/world.pyx | Player mover and world-query components implemented; gameplay integration pending | 12924 exact source ticks across118 cases/all 18 classes;941 exact query vectors; float stores, flight/crouch/peer/voxel/landing branches; remaining GLM/projectiles and live domain pending |
| server/simulation_runtime.py | Scheduling core implemented | 60/30 cadence, 5-step batches, 2s retained debt, bounded input drain and ownership tests; domain step functions missing |
| server/config.py / game_rules.py | Core/join/common scene and full Match Lobby rule catalog implemented | 102 definitions, 1958 coercion inputs, aliases/explicit precedence; diagnostics validate common settings; overlays/fleet/full domain configuration pending |
| class_data.py / class_selection.py | Stock class selection and rule-adjusted movement components implemented | all 18 classes, 336 source selections, 864 rule-scaled physics profiles, inventory gate; map/UGC extensions, mounted entities and mode authorization pending |
| Player names / pre-life team and class admission | Implemented policy component | 1,112,064 Unicode scalars, 18,568 name vectors, 6,192 team and 14,256 class vectors; configured binding and four-client UDP coverage; authenticated identity and actual mode hooks pending |
| Map metadata / mode layout / retail pools | Implemented data and assembly components | 785 source metadata cases, 659 packet pairs, 4316 pool vectors; layered JSON/legacy/UGC, stock presentation and owned-map CRC; full mode/entity lifecycle pending |
| Spawn geometry/threat selection/rescue | Implemented placement components | 18,124 geometry vectors, seeded event/score traces, 16,000 random vectors, 8,640 all-class settling ticks; file-backed layout input and UDP terrain revalidation; concrete mode policies/lifecycle pending |
| Player lives/respawn/identity | Common life/health components implemented | Fresh bodies, life wallets, death/respawn, accepted HP and roster repair; concrete mode/input/effect integration and authenticated identity remain pending; see PLAYER_LIFECYCLE.md and PLAYER_HEALTH.md |
| ClientData chronology / action_clock.py | Receive history, one-frame selector and cadence components implemented | 14,234 original input operations; 26,234 cadence operations; production life receive/respawn binding and real UDP chronology; infantry/flight add owned pacing and equipment; upstream rate/conduct, action/hit authorization and complete presentation/entity domains remain pending; see PLAYER_INPUT.md |
| Player.simulate_tick / ordinary Player.update | Infantry/connected-flight integration implemented | 16,683 original no-flight operations; owned UDP movement/refill/fall-health test; starvation/backlog, velocity impulses and eye history; full loop policies, concrete modes and complete presentation/entity domains remain pending; see INFANTRY_SIMULATION.md |
| Jetpack resources / canopy policy / flight profiles | Connected-player integration implemented | 65,497 original full-path operations in 44 traces; all four packs and stock/v1/v2 profiles; real UDP thrust/fuel/release and canopy landing; actual owner replication is now bound separately; bots/corpse-fuse/client validation pending; see PLAYER_FLIGHT.md |
| WorldUpdate cadence / routing / owner history | Connected-player replication implemented | 18,448 service, 2,235 wire, 1,132 live-row, 3,714 owner-history and 8,458 release-hold operations; two-client UDP ACK/respawn barriers; required effect/tool/entity/corpse domains and real-client validation pending; see WORLD_REPLICATION.md |
| modes/tdm.py | Concrete core and accepted-life binding implemented | 2,048 original TDM operations, 64 config cases, native stingers and actual HUD limit; owned-shot/reload/death/kill/personal/team/winner over real UDP; canonical roster order includes dead admissions. Queued departure settlement and delayed ending/restart policy now have ROUND_RUNTIME.md evidence; transition lifecycle/preflight/roster and retained peer reload now have TRANSITIONS.md evidence; remaining team policies/services and gameplay bootstrap remain pending |
| Tick-start in-game packet queue | Serial async component bound to actual ENet sessions (opt-in) | 2,048 original queue operations, per-peer fairness, fixed detached budget, owner resumption, stale identity/scene guards and actual suspended objective request with two UDP peers; complete simulation/bootstrap pending |
| SimulationRuntime mode-event drain | Implemented serial component | 2,048 original mode/plugin operations, isolated failures, live mode replacement, fixed timeout remainder and 8,192-event bounded-stack check; immediate departure settlement and ordered async frame/catch-up components now have ROUND_RUNTIME.md evidence; complete subsystem bindings remain pending |
| Round music / map ambience / next-map ballot | Implemented services and ending/host bindings | Four seeded music traces, three ambience cases,16 ballot traces,564 distinct native wire vectors; independent cancellation/timeout/identity and real UDP cast-to-map-transition; selection/kick now have VOTING_POLICY.md evidence; full bootstrap pending; see ROUND_MEDIA.md |
| Live team/class/palette / spectator admission | Implemented policy services and actual life/host bindings | 24 intact-source traces; five timer/identity/session boundaries; three-peer actual UDP menu admission, palette exclusion/trailing update, class respawn/throttle and spectator rejoin; full mode/domain/bootstrap pending; see PLAYER_CONTROLS.md |
| Map catalog / fit / retail rotation / kick votes | Implemented selection/moderation services and actual host binding | 51 intact-source groups; immutable worker catalog and cache boundaries; one map/kick HUD; actual four-human UDP kick, reconnect exclusion/reset and ranked retained-peer rollover; full bootstrap/authentication pending; see VOTING_POLICY.md |
| Worker spawn-cache / owner timers / owned host state | Implemented components and actual host transition binding | Four native worker map loads/eight team caches; exact terrain/layout guards; bounded owner scheduling; actual host commit/rollback/retirement and upgraded retained-peer UDP; remaining production domain factories/services pending; see PREPARED_HOST.md |
| Map/mode transition / retained scene epochs | Implemented policy and actual ENet loader binding | 32 original rollover/map-screen scenarios, 13 names, worker preflight, guarded restoration and live+team-select different-CRC real UDP reload; owned state/host/timer and worker-cache transfer now have PREPARED_HOST.md evidence; remaining services/full bootstrap pending |
| BaseMode delayed ending/restart | Implemented orchestration and presentation bindings | 40 original end/restart cases, retained cancellation/fallback, actual life/score reset and ROUND_MEDIA.md music/map-ballot composition; map selection/kick now have VOTING_POLICY.md evidence; bot/reset services/full bootstrap pending |
| BaseMode generic kill/penalty/teamplay economy | Implemented component | 2,048 original operations, ghost-return/bonus/score/profile/send-fault ordering; actual UDP reload/defend/distraction credit; accepted-life event/bonus/reload capture is bound in the TDM/runtime batch; exemption policies and complete gameplay binding remain pending; queued departure settlement is now implemented; see TDM_RUNTIME.md |
| Teabag accepted crouch edges | Implemented policy component | 2,048 original attempts, corpse-distance/timing/death-key/profile/rule-fault behavior and actual-life UDP credit; corpse representation and gameplay input edge binding remain pending |
| Escape watch and high-minimap markers | Implemented component | 2,048 tracking passes, 1,824 native voxel edits and 6,144 direct geometry evaluations; identity/mode ownership, late replay, recovery and objective-guard UDP composition; concrete mode/loop binding pending |
| Round countdown / timeout accepted-event remainder / winners | Implemented components | 2,048 original BaseMode/SimulationRuntime operations, 512 zero/one/multiple-team winners and native HUD bytes; fixed remainder UDP winner; serial mode/packet drain components now have TDM_RUNTIME.md evidence; complete simulation step and mode end/restart/audio/vote/achievement integration remain pending |
| server/profile_stats.py | Counter/event/activity components implemented | 4,096 unchanged profile/event/activity observations plus 2,048 exact CPython norms; real UDP accepted damage/death, score baseline, round retention, map minutes/AFK/population; persistence/results/authenticated identity and full event binding pending; see SCORE_EVIDENCE.md |
| server/combat_scores.py accepted evidence and objective credits | Award/assist and objective score/geometry components implemented | 4,096 unchanged combat/objective observations; life deduplication, assist thresholds/expiry, identity slots and send-failure mutation order; actual-life UDP score/award delivery; Teabag credit now has MATCH_FLOW.md evidence; corpse representation and concrete-mode/full loop integration pending |
| Team roster/spawns/score locks/intel state | Implemented component | 2,048 unchanged Team operations; actual object identity and round-reset retention; integrated UDP score-lock/intel flow; live team/class/palette and spectator boundaries now have PLAYER_CONTROLS.md evidence; mid-match bot/human balancer remains pending |
| Objective escort membership | Implemented component | 2,048 unchanged ordered/hysteresis queries, duplicate and numeric-ID reuse cases; actual-life UDP composition |
| Objective LOS/recovery/AFK/grace/protection guards | Implemented component | 1,536 unchanged native-world queries, 2,048 presence/timer operations and 8,192 Python-round vectors; live-wall/burial/recreate UDP flow; EscapeWatch detector now has MATCH_FLOW.md evidence; concrete-mode scheduling remains pending |
| Scoreboard/HUD/round awards and native result codecs | Implemented components | 2,048 scoreboard operations, 2,048 round-stat operations, 1,024 native codec cases; filtered dead-roster awards, signed late replay, delivery modes and actual-life reset over UDP. Profile counters/events now have SCORE_EVIDENCE.md evidence; concrete lifecycle/score-presentation/known-roster/result/persistence and full round integration remain pending; see MATCH_FOUNDATIONS.md |
| Objective drop request coroutine / authoritative dispatch | Implemented component | 2,048 unchanged registered-handler requests with actual Player/native body/registry; three-client UDP identity/type guards, mode consumption, suspended death and generic placement. TDM_RUNTIME.md now binds the outer awaited packet queue; concrete objective modes, full simulation/bootstrap and dropped-object simulation remain pending; see OBJECTIVE_DROP_REQUESTS.md |
| Objective carry/drop helpers / native drop packet | Implemented components | 4,096 original helper/native-body operations + 8,192 arbitrary sanitize vectors + 512 native codec cases; three-client UDP burden/reveal/capped drop and send-failure state. Concrete mode ownership, outer async scheduling, dropped-object simulation and full gameplay binding remain pending; request component verified separately; see OBJECTIVE_CARRY.md |
| Map-owned supplies / VXL chroma cleanup / static lights | Implemented components | 76 original map cases / 18,200 point observations, 48 original resource traces and three-client UDP MapAssembly/refill/replay/rebuild; required score/achievement/audio/reveal ports and complete round/gameplay binding remain; see MAP_RESOURCES.md |
| Physical supply crates / personal sound and entity changes | Implemented components | 8,192 unchanged crate/registry/native-world/refill operations + 1,024 native codec cases; health/ammo/block and three-client UDP parachute/replay/support move. MapResourceService assembly and objective helpers/request components are verified separately; concrete objective modes and full gameplay binding remain pending; see PHYSICAL_CRATES.md |
| Medpack / radar behaviors | Implemented components | 1,024 source lifecycle/healing operations + 32 actual heal IO faults; shared cooldown, uses, live fuse, owner/team cleanup and new three-client UDP late-replay/owned-shot test. Full Hardcore lifecycle, placement and gameplay assembly remain pending; see CARE_RADAR.md |
| Shared blast / predicted impulse / explosive devices | Implemented components | Exact source geometry, full dense-sequence native-mover timelines, native/exact terrain routing, shared HP/entity commit and timed/mine/C4/grave lifecycle; new three-client owned UDP chain/credit/push test. Placement, other devices, projectiles/corpses, concrete services and full gameplay binding remain pending; see BLAST_RUNTIME.md |
| Entity registry / base behavior / per-scene visibility | Implemented component | 7,680 source registry operations, 512 native wire cases, 16,388 integer-set snapshots; new three-client owned-shot UDP damage/destruction and known-ID delivery; concrete pickups/devices/projectile/corpse and complete loop/mode binding remain pending; see ENTITY_REGISTRY.md |
| Combat/ammo/reload/grenades/deployables | Owned shot commit, player hitscan/melee damage and feedback implemented as components; concrete combat domains pending | 540,416 new source operations plus prior timing/geometry evidence; automatic bound packet-6 real UDP health/death, ammo, feedback and delayed rewind. Terrain health/dig/collapse now have TERRAIN_DAMAGE.md evidence; repair scheduling now implemented as a component; required full loop/entity/corpse/mode/scoring/achievement/input domains and projectiles remain pending; see COMBAT_RUNTIME.md |
| TDM/CTF/ClassicCTF/Arena/VIP/Zombie | **Pending** | complete playable rounds, not identifiers/classes |
| Multi Hill/TC/Diamond/Demolition/Occupation | **Pending** | rules/objectives/timers/round reset and joins |
| Hardcore and all 20 community variants | **Pending** | all registered/revised/retired-but-loadable variants |
| Bot population/perception/planner/motors/combat | **Pending** | preserve SimpleVoxelWorld fairness and bounded worker epochs; no native Recast dependency |
| Map Creator/UGC/prefabs/project validation | **Pending** | format primitives are not a functioning editor |
| Tutorial | **Pending** | staged rules, launcher and complete training flow |
| Plugins and commands | **Pending** | Java-native API/discovery/events/cleanup; Python plugins incompatible |
| Admin/GUI/moderation/logs/metrics | **Pending** | portable management implementation and bounded diagnostics |
| A2S/LAN/master/revival/fleet | **Pending** | portable queries/discovery/HTTP and isolated multi-instance hosting |
| Steam SDK/auth/relay helpers | **Unresolved under pure-Java constraint** | disabled integrations must not silently appear available; investigate portable alternatives |
| SQLite results/progression/ban/demo persistence | **Pending** | no native sqlite-jdbc; migration and durability strategy required |
| Gradle/JDK25/wrapper/locks/checksums/JAR | Implemented foundation | executable diagnostic JAR, Java25 compilation, native-dependency checks; normal hosting returns explicit exit 2 |
| Cross-platform CI | Workflow implemented | same-artifact smoke + component/UDP tests; execution status in migration report |
| Original-client multiplayer | **Unverified** | no supported game client/interactive session supplied; ENet probe is not this test |
| 50-player 58 Hz / p99 <=12ms / zero drops / 15m | **Not evaluated** | workload requires functioning Java server; scheduler-only test is not a substitute |

## Ordered remaining implementation work

1. Implement the actual Gameplay domain behind ConfiguredScene: identity verification,
   actual mode policies, Character creation/death/respawn and entity reveals.
   Names, common team/class checks, map layout assembly and spawn placement now have source/UDP evidence;
   they require real domain occupants, verified identity and concrete mode hooks.
2. Port remaining packets, marker cleanup, full repair-loop integration and terrain construction
   validation/collapse. Complete transport bandwidth redistribution and full configuration.
   Application snapshot/cell/life-token gates now have source and real-UDP coverage.
3. Connect an unmodified supported game client and verify complete map synchronization.
4. Connect verified strict-float PlayerPhysics and queries to the actual player
   lifecycle, input/equipment policy, 60 Hz loop and reconciliation/WorldUpdate.
   Port remaining GLM/projectile/world behavior with differential traces.
5. Bind departure/frame/catch-up/ending/restart and map-transition components to the
   full production domain/bootstrap and concrete remaining services; worker cache transfer,
   owned host state and timers now have PREPARED_HOST.md evidence; then
   finish player/combat/terrain/equipment lifecycle and supported-client validation.
6. Complete all modes, bots, creator/tutorial, plugins/admin/persistence/discovery.
7. Evaluate full regression, real-client multiplayer, identical-artifact platform
   gameplay, documented capacity gate and 15-minute soak before release assessment.

Milestone A is substantially implemented as a foundation; comprehensive behavioral
repository audit and complete configuration parity remain outstanding. Milestone B
has verified components but is not complete. Milestones C–F are not complete.

Terrain damage, health/shades, seeded footprints, collapse and configured late-join
health reveal are source-backed components; see [TERRAIN_DAMAGE.md](TERRAIN_DAMAGE.md).
Construction, full repair-loop binding and concrete device/projectile/corpse/mode integration remain pending.
