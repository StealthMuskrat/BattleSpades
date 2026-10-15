# Feature parity and explicit backlog

Reference: source `6216b771282d2d349ccd67ee58c378f035c83553`.
`Implemented component` means only the exact behavior and evidence listed here.
It does not imply that a playable server, end-to-end feature or migration milestone
has passed. Nothing below is a production-readiness claim.

| Component / original files | Java status | Verified evidence / next gate |
|---|---|---|
| shared/bytes.pyx | Implemented binary component | byte order, widths, UTF-8, BGR, truncation tests; legacy Python façade quirks not reproduced |
| server/util.py | Implemented LZF | source golden literals; backreferences, overlap, bounded malformed/fuzz tests |
| shared/packet.pyx | 61 distinct packet IDs encoded/decoded | 171 simple vectors, ClientData, WorldUpdate, CreateEntity, Damage source fixtures; remaining IDs pending |
| server/connection.py framing/decrypt | Implemented framing component | outbound literal chunks for 0x30/31/32, inbound 0x31 decode then XOR; full connection state pending |
| vendor ENet compress.c | Pure Java range coder | 30 native-C encode/decode vectors, rescaling/reset; bounded malformed fuzz |
| vendor ENet protocol.h | Implemented envelope component | all command layout sizes, MTU/bounds, network byte order, compressed region tests |
| vendor ENet protocol.c connect phase | Standalone handshake component | native pyenet CONNECT event + Java VERIFY_CONNECT ACK; no game client |
| ENet sequencing/fragments | Supporting components only | wrap arithmetic and bounded single-group reorder/dedup/overlap tests; full peer delivery queues pending |
| ENet host/session delivery | **Pending** | retransmission, timeouts, congestion, unsequenced windows, lifecycle/reconnect/loss; must precede gameplay hosting |
| InitialInfo/StateData/application handshake | **Pending** | complete packet codecs, source fixtures, actual loader lifecycle |
| Map transfer, CRC, late joins | **Pending end to end** | source CRC32 and VXL columns exist; map protocols, reliable cursors, journals/reveal gates missing |
| aoslib/vxl.pyx | Loader / column serializer implemented | 6 synthetic source fixtures, 3,264 source point samples, classic64/floor/fill and source column bytes |
| aoslib/kv6.pyx | Format codec implemented | standard/legacy roundtrip and malformed bounds; model operations/palette interpretation pending |
| Voxel edits/collapse/world persistence | **Pending** | original mutation, health, structure collapse and atomic editing semantics |
| shared/glm.pyx / aoslib/world.pyx | **Pending** | binary32 vector wire record exists; no movement/physics port or physics parity claim |
| server/simulation_runtime.py | Scheduling core implemented | 60/30 cadence, 5-step batches, 2s retained debt, bounded input drain and ownership tests; domain step functions missing |
| server/config.py | Core subset implemented | root config smoke test; raw TOML retained; most validation/rules/overlays/fleet pending |
| Players/team/class/spawn/respawn/identity | **Pending** | requires transport/loader and source lifecycle tests |
| Combat/ammo/reload/grenades/deployables | **Pending** | packet codecs do not implement combat |
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

1. Complete host/peer reliability, retransmission, sequencing/windows, unsequenced
   and fragment-group management; verify against native ENet under loss/reordering.
2. Port remaining application packets, full configuration/rules, InitialInfo,
   StateData, password/ticket/identity and map transfer/snapshot/journal gates.
3. Connect an unmodified supported game client and verify complete map synchronization.
4. Port original strict-float movement/collision/projectile/world mutation behavior
   with existing retail fixtures and differential traces.
5. Implement player/combat/terrain lifecycle and one full source-equivalent mode.
6. Complete all modes, bots, creator/tutorial, plugins/admin/persistence/discovery.
7. Evaluate full regression, real-client multiplayer, identical-artifact platform
   gameplay, documented capacity gate and 15-minute soak before release assessment.

Milestone A is substantially implemented as a foundation; comprehensive behavioral
repository audit and complete configuration parity remain outstanding. Milestone B
has verified components but is not complete. Milestones C–F are not complete.
