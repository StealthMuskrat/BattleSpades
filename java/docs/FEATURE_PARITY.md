# Feature parity and explicit backlog

Reference: source `6216b771282d2d349ccd67ee58c378f035c83553`.
`Implemented component` means only the exact behavior and evidence listed here.
It does not imply that a playable server, end-to-end feature or migration milestone
has passed. Nothing below is a production-readiness claim.

| Component / original files | Java status | Verified evidence / next gate |
|---|---|---|
| shared/bytes.pyx | Implemented binary component | byte order, widths, UTF-8, BGR, truncation tests; legacy Python façade quirks not reproduced |
| server/util.py | Implemented LZF | source golden literals; backreferences, overlap, bounded malformed/fuzz tests |
| shared/packet.pyx | 77 distinct packet IDs encoded/decoded | 171 simple vectors, ClientData/complex and 11 loading +4 session packet vectors,4 profile and12 live-input vectors; remaining IDs pending |
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
5. Implement player/combat/terrain lifecycle and one full source-equivalent mode.
6. Complete all modes, bots, creator/tutorial, plugins/admin/persistence/discovery.
7. Evaluate full regression, real-client multiplayer, identical-artifact platform
   gameplay, documented capacity gate and 15-minute soak before release assessment.

Milestone A is substantially implemented as a foundation; comprehensive behavioral
repository audit and complete configuration parity remain outstanding. Milestone B
has verified components but is not complete. Milestones C–F are not complete.

Terrain damage, health/shades, seeded footprints, collapse and configured late-join
health reveal are source-backed components; see [TERRAIN_DAMAGE.md](TERRAIN_DAMAGE.md).
Construction, concrete repair scheduling and projectile/entity/mode integration remain pending.
