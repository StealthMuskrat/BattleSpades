# Bot horde coordination and combat controller components

The bot migration remains incomplete and `bots.enabled` remains gated. This checkpoint completes connected controller components; it does not enable production bots or claim supported-client acceptance.

## Implemented

- `StructureCollapse`: retained standing/approach floods, 18-neighbour grounding, conservative bounded solid exploration, residual vertex-cut planning, dig costs and claw-site coverage.
- `HordeCoordinator`: stable target allocation including human load, walking/breaching/collapse estimates, pursuit waypoints, siege roles and measured work/progress watchdogs.
- `ZombieSiegeService`: owner-thread mode objectives, per-call analysis budget, retained jobs, settled-target and analysis TTLs, mutation subscriptions, epoch reset and fail-safe fallback.
- `ZombieRefuge`: retained dry, flat, high-ground selection, exclusions, public region fences and breach checks.
- `BotBanter`: identity talkativeness, seeded phrase choice, typing delay, small queue, combat deferral, expiry and global/per-speaker cooldowns. It proposes lines; it does not send chat.
- `BotBrainState`, `BrainGoals`, `BrainTargeting`, `BrainInventory`: complete per-life state fields, stable visual acquisition, objective/contact/flank/assault ownership, weapon wallets and loadout-authorized mobility.
- `BrainOriented`, `BrainCombat`: stocked projectile scoring and launch safety; visible combat, melee/reload choices, range hysteresis, committed-objective pursuit, reload cover, firing cadence, stationary relocation, blocked movement recovery and cover prefab proposals.
- `BrainRoutingState`, `BrainMobility`, `BrainIntents`: atomic goal changes, bounded rejected-edge memory, tactical jumps, combat flights and thrust-free landings, fenced expiring proposals, stable travel gaze, scalar diagnostics and non-reversing crowd separation.

The structural jobs bound work in Java node operations rather than Python generator yields. Service tests verify retained progress and measured call budgets; identical scheduling at every generator yield is not claimed.

The exact source firearm set contains seventeen tools. The broader player weapon set also contains RPG/RPG2/drill tools. Bot doctrine, firearm selection and cooperative combat relevance now use the exact firearm set, preserving the source default doctrine for other tools.

## Evidence

`export_bot_live.py` invokes the unchanged source at commit `546c548780c400f21d73ca6713f86194ceb3c844`. Ten grouped Java tests cover:

- 81 tool doctrine queries, nine structural terrain scenes with multiple flood/cut bounds, and 324 connected horde boundaries;
- 24 refuge elections and 480 seeded chat boundaries;
- 120 connected main-brain targeting/goal/inventory/projectile boundaries;
- 408 full visible-combat intents across all seventeen firearms, 42 route-ownership transitions and 108 flight boundaries;
- siege budgeting, terrain subscriptions, epoch fences, failure fallback and owner-thread enforcement.

The combat trace exercises stationary engagement ranges. Navigation-pursuit callbacks, tactical high-ground geometry and live gateway execution still need connected brain/host acceptance. New catalog/fixture resources reproduce byte for byte. The 71 selected original horde, refuge, banter and native collapse-parity tests pass.

## Remaining implementation

1. Complete the main `simple_worker.py` orchestration and ordinary/water/corridor/breach/recovery navigation executors; connect the migrated combat, awareness, cooperative and skill components.
2. Complete community action behavior and durable gadget/charge/objective terrain actions.
3. Port the authoritative gateway, director lifecycle/perception/aim/input motor and host composition using ordinary Java gameplay services.
4. Enable bots only after connected lifecycle, action-authority, map/mode transition, capacity and supported-client acceptance.

No macOS job is run until the full migration is complete. The previously observed standalone UDP map-reload timeout is not claimed fixed. The complete original Python baseline and overall server migration remain unfinished.

## Checkpoint verification

Ported horde allocation and progress recovery, bounded structural collapse/approach planning, refuge election, budgeted siege coordination and seeded banter. Added the main brain's retained targeting/goal/inventory state, projectile choice/safety, complete visible-combat decision component, atomic route ownership, jetpack phases, fenced intent emission and crowd separation. Corrected firearm doctrine/selection to the exact seventeen-tool source set. **6,507 Java tests pass** (6,417 component + 90 integration), zero failures/errors/skips; all 71 selected unchanged-source horde/refuge/banter/native-collapse tests pass. Ten grouped Java tests replay 408 full combat intents, 324 horde boundaries, 120 targeting/goal/inventory boundaries, 480 chat events, 108 flight events, 42 route transitions, structural and refuge cases, and siege lifecycle budgets. The strict Java 25 build and distributions pass; the CRC-valid JAR is 3599171 bytes, SHA-256 `ec481203835cebc7f2fd3c78b0101140f25af96af639d9cd03f8aa5324658844`, class major 69. Eight original source directories and all 281 previous resources remain unchanged. Two reproducible resources bring the total to 283, with 91 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `953edf501ecf33dcfeaaafbe4d5eff2b1193f2d0`, tested tree `d3f86ab594273a0cb47161e9daf8e1ea28afd544`, in [run 39530536181](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39530536181). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 91 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. Main brain orchestration/navigation executors, community and durable gadget actions, gateway/director/motor and host composition remain incomplete; live bots remain gated. Full bot and overall migration, supported-client/capacity acceptance and the full original Python baseline remain incomplete. The existing intermittent standalone UDP map-reload timeout is not claimed fixed. See [BOT_LIVE.md](BOT_LIVE.md).
