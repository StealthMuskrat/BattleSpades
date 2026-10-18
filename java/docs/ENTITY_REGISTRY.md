# Entity registry and base lifecycle

Source: `6216b771282d2d349ccd67ee58c378f035c83553`,
`server/entities/registry.py` and the base/damageable classes in
`server/entities/behaviors.py`. Full gameplay integration remains incomplete.

The Java registry owns the monotonic uint16 allocator, insertion-ordered entity
map and support index. Dead entries reserve IDs until removed. Removal leaves
an entity's alive flag untouched. Clearing resets allocation but preserves the
tick cursor. Runtime placement, terrain, fall and UGC fields stay outside the
native-compatible Entity/CreateEntity wire record; float32 assignment precedes
fixed-point encoding as in the compiled source.

Support loss detaches its bucket before callbacks and clears the matching
entity's support cell before invoking behavior. Reentrant notifications and stale
index entries preserve source behavior. Support callback order follows the
CPython 3.12 integer set table algorithm, implemented for uint16 IDs only and
covered by exact table-order hashes. Its PSF notice and full license are packaged.

Tick budgeting recognizes inherited on_tick overrides, rotates eligible entries,
and preserves source slicing/modulo behavior for negative caps. Tick snapshots
retain removed-but-alive objects and read their current behavior before each
callback. Touches use rebuilt 8-cell spatial buckets, source bucket/player order
and live positions/radii. Due respawns set alive and clear the timer before the
optional respawn hook and creation callback. No additional membership or player
alive filters are inserted into these source loops.

Damage routes through each entity's concrete behavior. The reusable damageable
base has per-instance health, hit geometry and source nonnegative/NaN clamps.
Destruction marks dead before sending, then removes the registry entry; send
exceptions preserve the source's partially completed lifecycle. EntityVisibility
tracks per-connection CreateEntity knowledge only after reliable enqueue, avoids
duplicates, and forgets an ID only after its known-only destruction send succeeds.
Scene transition callers must clear this knowledge explicitly.

The unchanged original registry/base behaviors and native codec produce 7,680
stateful operations and 512 wire cases. Controlled subclass callbacks exercise
mutation, inherited tick caps, touch order, respawns, stale indexes, reentrant
support loss and send faults. Another 16,388 integer-set snapshots cover resize,
dummies, all 65,536 IDs and bulk deletion/reinsertion; bulk primitive operations
are not counted as independent snapshots. Extra Java checks cover allocator
exhaustion, lifecycle failures and optional callback boundaries.

A new three-client real UDP test binds the concrete registry through the owned
combat domain. Two accepted rifle shots apply the source's 25 entity damage each,
queue reliable HitEntity before DestroyEntity, consume ammo and remove a 50-HP
object. Known-ID creation is deduplicated. A later peer receives neither a stale
destroy nor a hidden/static-excluded projectile, and receives a new static entity
once. Ordinary mode/effect ports and lifecycle scheduling remain explicit test
inputs. The packaged native-loader probe still uses FixtureScene.

Concrete pickups, deployable explosions, projectile/corpse simulation, mode/
profile/score/achievement/audio services and the complete gameplay-loop binding
remain pending. This checkpoint does not establish supported-client playability,
capacity or soak readiness. Hosting continues to exit 2. Local verification passes 5,168 Java and 57 focused original tests. All 49
configured generators reproduce source outputs after a fresh original native
build; all 197 recorded fixture/catalog hashes remain unchanged. Repeated
archives, packaging/smokes and isolated native probes pass. All five jobs pass in [run 39081721193](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39081721193); see [CI_STATUS.md](CI_STATUS.md).

The first local native build encountered a truncated compiler temporary assembly file. A piped compiler retry completed with unchanged source/strict float flags; accepted source outputs then reproduced exactly.

Implementation `82f63e3f03900cbbd75e365680d91ee2b766a84e`, tested/published tree `a437b5b53ec912b75898600d69caba2114da647d`. The CI build compares repeated archives and tests one uploaded JAR on three platforms. Full gameplay and supported-client/capacity gates remain pending.
