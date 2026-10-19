# Hosted entities, map resources and blast support

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

StandardMatchFactory now requires per-candidate HostedEntityRuntime ports and constructs
an actual match-owned EntityRegistry, PickupWorld, MapResourceService, TerrainDamage,
BlastTerrain, BlastRuntime and DeployableRuntime. The exact prepared world, spawn geometry
and immutable metadata are retained. The player runtime attaches first, allowing the
required terrain authority to bind its real repair service. Entity construction installs
the world's canonical removal listener last; domain disposal releases it before the scene
state closes the borrowed world. Each rollover owns a fresh registry and listener.

HostedSimulation routes the post-respawn ENTITIES phase to the current match's registry.
Contexts use fixed 1/60-second dt and the source wall clock, independently of the monotonic
player/replication clock. They include exactly alive, spawned actors, including bots and
loading humans. The source network.entity_tick_batch_limit defaults to 8192, clamps to
at least 64 and reports skipped on-tick work through its required metrics port. Existing
registry rotation, touch filtering, respawn and failure semantics remain unchanged.

Mode start/restart rebuilds actual map pickups and static lights before the remaining
external resource hook. Rebuild replaces only map-owned entries, preserving foreign
objectives/deployables. Base anchors come from the candidate's owner-bound spawn geometry.
Ordinary TDM/Arena enable map pickups; respawn delay comes from RULE_CRATES_SPAWN_TIME.
Refills operate on canonical lives/inventory and record real CombatEvidence crate awards.
Achievement baseline/collection and rebuild diagnostics remain required external ports.
Personal pickup sounds use HostedGameplayServices and fly-by cues use actual reliable
host delivery. Existing fall/parachute/support-relocation policies are not reimplemented.

JoinSession now records accepted reliable CreateEntity(21) and DestroyEntity(19) lifetimes,
including controls queued behind transport backpressure. Knowledge uses canonical uint16
IDs even when packet decoding yields signed shorts; reuse starts a new create/destroy
lifetime. Replacing/reloading/closing a scene clears knowledge. ApplicationHost suppresses
duplicate creates and sends destroys only to settled peers that accepted the create.
The normal join reveal queues live wire-visible static registry entries, excludes moving
projectiles and records knowledge only when its reliable batch is accepted. The existing
reliable ACK/snapshot hold machinery continues to own release ordering.

BlastTerrain uses those actual per-session known-causer sets. Settled peers that know a
causer receive its native footprint packet; peers without it receive the existing exact
cell/health fallback. Support removal synchronously reaches the registry's real device
behavior and shared BlastRuntime, with the entity marked dead before chained blast work.
The shared blast binds the scene's friendly-fire setting, ordinary non-hardcore policy,
source game.build_damage, canonical lives/registry/world and shared SourceRandom. Stimulus
and achievement scope domains remain required, as do terrain authorization, repair commit
recording, sounds, score/profile and achievement callbacks in TerrainDamage.Domain.

Static registry entries remain on the source's reliable create/destroy/change path.
They are not appended to WorldUpdate's separately owned moving-entity/turret stream.
Player runtime presentation and moving-entity/turret ports remain required. This checkpoint
does not implement projectile simulation, device placement requests, owner retirement of
all device types, corpse factories or a synthetic fallback for absent gameplay domains.

Three grouped component checks verify wall-clock/body filtering and rotating-budget metrics;
resource rebuild isolation, canonical refill, award and respawn timing; and support-loss
charge detonation through actual blast/HP effects plus observer cleanup. The existing
session test adds uint16 create/destroy/reuse across blocked reliable delivery and scene
reset. The same two-peer production-factory UDP scenario now proves join resource reveal,
actual entity-phase ticks, duplicate-create suppression, unseen-projectile destroy gating,
known native/unknown exact blast routing, reliable position changes, knowledge clearing and
retired runtime closure on rollover. Its movement, profile, replication, repair, clock-origin,
metadata/gravity, retained peer/slot and awaited stop assertions remain. Deadlines and policy
assertions are not loosened; no additional copy of that UDP scenario is introduced.

Full combat/input/effect and remaining phase factories, projectiles/construction/corpses,
bot AI/director and identity allocation, authenticated identity/master services, results/
achievements/persistence, ending/reset/HUD ownership, full bootstrap, remaining modes,
supported-client/capacity/soak and baseline reconciliation remain pending. Normal hosting
is gated. This batch completes the selected hosted static-entity, resource and support/blast
bindings; it does not complete a playable server.
