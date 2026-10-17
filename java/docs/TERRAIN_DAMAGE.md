# Owned terrain damage, health and collapse

Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`, principally
`server/block_damage_model.py`, `server/dig_profiles.py`, `server/world_manager.py`,
`server/combat_runtime.py`, `server/terrain_repair.py`, `server/prefab_actions.py`
and `aoslib/vxl.pyx`. These components extend [COMBAT_RUNTIME.md](COMBAT_RUNTIME.md).
Normal hosting continues to exit 2; full gameplay and supported-client gates remain open.

BlockDamageModel exports all 44 original footprint types. It retains integer-cell
rounding, quarter-unit wire/ceiling quantization, Python-compatible masked seed
streams, cube x/y/z and radius z/x/y traversal, and draws for air/out-of-map cells.
Filtering happens after expansion. Per-channel crack shading compounds the source's
Python-2-style rounding and integer shifts. The original source describes these
footprints as measured client behavior; the port adds source comparisons, not a
new independent supported-client measurement.

TerrainHealth belongs to MutableWorld and tracks accumulated damage, unscaled
user health, original/displayed shades and bounded hit history for implicit/black
client-colored cells. Paint preserves damage and original hit color; replacement
and removal clear the ledger. Normal, Classic and UGC user-health policies and
health-rule scaling are explicit trusted settings. This does not implement those
modes. Partial hits also enter the existing admission journal; a partially queued
canonical cell restarts when its health changes. Changing health scale reopens
affected admission leases. Topology-version invalidation remains conservative.

Canonical repair sends the original add/conditional-paint pair, user health,
damaged health and any necessary displayed-shade correction, in source order.
The configured scene's real first-input reveal now sends all user batches before
damaged batches, then shades and client-color hit replays. The server loop reaches
this reveal through JoinSession; replay Damage packets have chunk_check=0.
Batch size is 128 in the current configured binding; the component retains the
source 1..4096 bounds. Full configuration overlay and large-world loading/capacity
behavior remain pending. Existing bounded queue/backpressure limits still apply.

TerrainCollapse uses 18-neighbor face/edge connectivity, excludes three-axis
corners, grounds at z>238 and retains the 180,000,000-probe budget. The original
native down-first support probe avoids walking a grounded plain before the exact
DFS output traversal. Budget-exhausted branches are safe and never yield partial
collapses. Java uses primitive index tables/stacks and standard APIs. Exact chunk
and cell order match both unchanged native and unchanged fallback policies.

TerrainDamage composes accepted bullet and melee terrain commits with actual
owned map health, block wallets, accurate/fallback rays and collapse. It preserves
mode/build-damage gates, eye reach and repairs, all original melee footprints,
UGC primary/secondary choice, seed draw timing, single-cell kill damage and one
native expanding packet for area swings. Machete accumulates its vertical pair
once. Bullet removals do not grant digging blocks. Collapse removes canonical
cells immediately and invokes required profile/score/achievement/mode and repair
ports in source order. Active shot attribution reads the actual owned tally.

Mode permissions, profile/scoring/achievements, sound/stimuli, bounded rejected-
prediction/collapse-repair scheduling and concrete modes remain required domains.
Tests observe these callbacks with explicit ports; they do not prove those full
services. Projectile explosions have footprint/health primitives here, but their
actors, damage admission and full blast lifecycle are still pending. Construction,
prefab/editor validation and legacy block actions also remain incomplete.

| Unchanged original comparison | Cases/operations | Additional scope |
|---|---:|---|
| Footprint, quantization and shade functions | 5,363 | 163,008 expanded cells; all 44 types and unknowns, masked seeds, boundaries and adjacent rounding values |
| Native and fallback collapse | 512 | Exact chunks/DFS order, budgets, edges/base and an 8,400-cell floating component |
| Native-world health, paint, reveal and repair | 3,840 | 36 stateful traces; normal/Classic/UGC inputs, history cap/loss, full/target/replay rows and exact bytes |
| Admitted bullet/melee terrain commits | 768 | Actual Player/native world, health/collapse, wallets, packets and observed domain boundaries |
| Total new source operations | 10,483 | Expanded cells are additional comparisons, not extra independent operations |

All original policy functions run unchanged. Generators control map topology,
trusted mode inputs, clocks and seeded randomness; sys.setprofile observes calls
without replacing policy methods. Fixtures are deterministic gzip files with
source/fixture hashes. New damage, topology, traversal and wire comparisons are
exact; the earlier numeric-only geometry intersection bound remains documented.

A new real three-client UDP test uses automatic bound packet-6 dispatch. Rifle
hits accumulate damage, reliably remove the hit voxel and collapse its floating
neighbors; the player behind the wall takes no damage until a later shot. Two
spade swings preserve 9-health built cells until the second hit, send one native
column packet each, grant exactly two blocks and send no gun feedback. A fresh
client joins between swings and receives reliable initial health before remaining
health. An active catch-up lease also carries the current damaged row. Trusted
equipment and mode/effect ports remain explicit test inputs; this is not a stock
game-client round or proof of full concrete repair/audio/award services.

Remaining migration domains include entity/corpse effects and registries,
projectiles, complete modes, upstream input/equipment/conduct authorization,
profile/scoring/awards/aim services, bots, editor/tutorial, plugins, administration,
persistence/discovery and supported-client/capacity/soak gates. The full original
Python baseline retains its documented incomplete runs and VIP discrepancy.

Local verification passes 5,049 Java tests and 53 focused original tests.
All 191 fixture/catalog hashes reproduce after a fresh original native build.
See [CI_STATUS.md](CI_STATUS.md) for exact evidence.

Implementation `939ac63a822d0ceacfb873ee0acc881e717457f8` and tested tree `b338c0bc04772517821ab0b32e5c93199877c27f` passed all five jobs
in [run 39079408578](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39079408578). This includes original-source fixture regeneration,
repeated archive comparison, native probes and one uploaded JAR tested on three
platforms. Local verification passes 5,049 Java tests and 53 focused original
tests. The preceding numeric geometry bound and required concrete-domain/gameplay limits remain in force.
