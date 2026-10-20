# Local bot routes, excavation and water recovery

This checkpoint ports the remaining active `SimpleVoxelWorld` local navigation policies. `BotNavigationWorld.plan` now produces bounded, immutable route segments through its worker-owned collision snapshot. `BotRoutePlanner` implements the search and `BotWaterNavigation` supplies live shore recovery and construction proposals. `BotDigProfile` retains the original melee action tables, footprint geometry, random-damage mean/worst-case estimates and owned-tool selection.

Live bots remain gated in standalone hosting. The production brain, director, motor/action gateway, mode/cooperative policies and host composition still require migration. These navigation proposals do not move a player, spend inventory, damage blocks or authorize a construction action. The authoritative gameplay handlers must validate current terrain, reach, tool/cadence, inventory, body overlap, protected areas and player life before committing an action.

## Source coverage

| Original active behavior | Java implementation |
| --- | --- |
| `simple_navigation.py`: `plan`, `_plan` | Segmented surface A*, source heap insertion order, ordinary traversal before excavation, useful partial routes and explicit scheduler deferral |
| `_adjacent_start_surface`, `_wall_biased_waypoint`, `_compact_straight_steps` | Capsule support recovery, parallel-wall separation bias and cardinal compaction that preserves every height/affordance change |
| `_neighbors`, climb/flight/jump clearance | Walking, crouch transitions, source-ceiling excavation, two-block jumps, failed-lip excavation alternatives, jetpack climbs and gaps, roof-aware drops and protected jump arcs |
| `_breach_plans`, `_clearance_target` | Flat/up/down excavation, real solid aim cells, retained support, source/destination protection for ceiling digs, health-aware swing and cadence costs |
| `dig_profiles.py` | Primary/navigation tool tables, secondary UGC super-spade action, exact single/column/cube/machete footprints, expected and worst-case swing estimates, source throughput ranking |
| `water_step`, directed/bounded searches | Preferred opposite-bank bearing, decreasing atlas lookahead with every edge revalidated against live terrain, exact failed-edge exclusions and bounded live fallback |
| Assisted bank selection and `water_bank_breach` | Adjacent live high-bank handoff, lowered/destroyed cached-bank recovery, exact excavation-height exclusions and floor-preserving three-cell waterline clearance |
| Jump-build, shore exclusions, roam | Capsule-clear supported water steps, failed-shore incoming edges without fencing open water, stable heading and retry after exhausted open-water exclusions |
| Bridge and shoulder lines | Cardinal face-supported short lines, landing-within-inventory checks, bridge overhang side choice and body-clear construction proposals |

The original source remains unchanged at `546c548780c400f21d73ca6713f86194ceb3c844`. The new source-data resource is `bot-navigation.toml`; the existing 272 resources are preserved. The handwritten Java policies add no runtime dependency.

## Bounds and ownership

Each local A* search expands at most 256 popped nodes. Its target heading is clamped to a 64-cell segment on each horizontal axis. Source-compatible useful partial routes are returned when the bounded search cannot reach that target. Recursive ordinary traversal and excavation share one admitted planning job, with at most two searches and 512 total expansions. A denied job returns `deferred=true`, no steps and zero expansions; scheduling pressure never labels terrain impassable. Corridor guidance retains the previous checkpoint's separate incremental limits.

One private worker owns the world, budget and queries. The public entry point copies ability/exclusion sets and returns immutable steps. Source recovery options can explicitly bypass an ordinary partial route when a recovery decision needs to cost excavation beyond its boundary. No live authoritative world is referenced.

Water atlas guidance looks ahead at most four edges and validates live heights and exclusions. The live fallback expands at most 8,192 water nodes. Shore exclusion radius is clamped to 1–4 cells; bridge/shoulder proposals contain at most eight cells, and optional landing scans at most 128 cells. These preserve the active source policies; they do not establish bot-fleet capacity or supported-client acceptance.

## Evidence

`export_bot_navigation.py` calls the unchanged Python policies with actual compact VXL snapshots and canonical deltas. The fixture includes 58 route/neighbor queries across 18 connected terrain scenes, 14 connected water/construction queries, thirteen melee profiles with exact footprints and expected/worst-case health estimates, and eleven owned-tool selection cases. Route evidence compares exact steps, costs, breaches, reached/deferred flags and expansion counts. Scenarios include built walls for every owned digger, support/ceiling safety, blocked two-block jumps, wall-overlapping capsules, stairs, parallel-wall bias, roof-aware drops, jetpack height/gap limits and blocked airborne volumes.

The water sequence loads a real immutable atlas, then lowers and removes cached banks and builds a partial bridge through canonical deltas. It compares strategic bearing, live assisted exits, exact rejected excavation edges, failed atlas lookahead, open-water recovery, swim-bob placement safety, tool-specific excavation, landing checks and both bridge shoulders.

The source regression selection is the complete `test_simple_bot_navigation.py`, `test_bot_built_block_breach.py`, `test_bot_jetpack_climb.py` and `test_bot_water_crossing.py` modules. Source brain/motor tests exercise the reference only; they do not claim those controllers have been ported. The Java differential checks cover the migrated navigation boundary, with a separate grouped check for shared-credit deferral and worker ownership.

macOS testing remains deferred until the full migration is complete.

## Checkpoint verification

Ported the remaining active local navigation policies: segmented surface A*, capsule support recovery, parallel-wall separation, height-preserving route compaction, walking/crouch/jump/drop/jetpack edges, tool- and health-aware excavation, live water and shore recovery, waterline breaches and supported bridge/shoulder proposals. Ordinary traversal and excavation share one fair planning job with two searches and 512 total expansions; each search is bounded to 256 expansions. **6,478 Java tests pass** (6,388 component + 90 integration), zero failures/errors/skips. All 110 selected unchanged-source navigation, built-wall, jetpack and water regressions pass. Source differential evidence compares 58 exact route/neighbor queries across 18 terrain scenes, 14 connected water/construction observations, thirteen tool profiles and eleven tool-selection cases. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 3155506 bytes, SHA-256 `8c575e6f917e99e6e5b40a01746c4f2fcc4233daf32d0ec6ed16de4e8872c5b7`, class major 69. Eight original source directories and all 272 previous resources remain unchanged; two new resources bring the total to 274, with 86 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `faa77d64ef8a26fce2f71d4810223c8a99e51065`, tested tree `bbabe41eda3547cc0852c4aa6c2daddfe7c7af09`, in [run 39497419697](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39497419697). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 86 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. The initial Linux attempt timed out in the existing standalone UDP map-reload integration; a single-job retry passed without code changes. The intermittent timing failure remains recorded. **Live bots remain gated:** the real brain, director, motor/action gateway, mode/cooperative policies and host composition remain unfinished. The full migration remains incomplete. See [BOT_NAVIGATION.md](BOT_NAVIGATION.md).
