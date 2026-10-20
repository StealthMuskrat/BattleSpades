# Bot combat footwork, tactics and awareness

This checkpoint ports `combat_profiles.py`, `locomotion.py`, `combat_tactics.py` and `awareness.py` into pure Java. The policies consume detached perception and the worker-owned collision snapshot. They provide weapon doctrine, encounter movement, collision-checked combat choices, and per-life reactions to information the player could receive.

The production `SimpleBotBrain` orchestration, mode/cooperative policies, advanced recovery skill driver, authoritative director/motor/action gateway and host bindings remain unfinished. `BotModeDecision` supplies the immutable role contract required by awareness; it does not select mode strategies. Live bots remain explicitly gated in standalone hosting. This checkpoint does not authorize a movement, shot, dig or construction action.

## Source mapping

| Source behavior | Java implementation |
| --- | --- |
| Weapon category envelopes and recoil | `BotCombatProfiles`: catalog-limited ideal/hard ranges, stationary doctrine, burst size/pause, category recoil and unknown-tool defaults |
| Encounter footwork | `CombatFootwork`: stable strides, displacement/time-triggered settling, range hysteresis, reload and pressure responses, collision-recovery handoff and identity-stable integer variation |
| Exposed body parts and accepted-fire feedback | `BotCombatTactics`: head/torso sight, wanted-but-unaccepted shot timing, accepted-shot and reload/burst resets |
| Relocation, cover and shelter | Rate-limited stationary displacement, walkable lateral/range-correcting choices, crouch-in-place cover, reachable shelter against an entire firing line and farther hidden ground |
| Thrown hazards | Short projected grenade positions, notice variation, own-team lit-charge handling, hidden enemy mine and fast-rocket exclusions, collision-checked escape and a short retained evade heading |
| Unexpected damage | `BotAwareness`: notice delay, recent-duel and undirected/self/friendly/stale/previous-life filters, looking back, shelter progress/stall/re-hit handling, open-ground zigzags and post-reaction suspicion |
| Losing engagements | Sight-limited odds, nearby allies, recent health loss, point-blank and objective commitments, refuge/fallback/hold/regroup phases and bounded retreat cooldowns |
| Sound and ally death | Loudness/range/attention gates, alert fading, source uncertainty retained from the sensory bus, checked-location memory, shot/death correlation, public enemy-anchor fallback and role-aware head glances |
| Fire awareness | Grouped visible burning patches, height/sight/notice gates, remembered own-Molotov landing, survival escape, stable tangent steering, speed-dependent clearance, exact-step waiting and expiry |
| Life and intent ownership | Generation/life reset, explicit forget/reset, 128-life eviction, worker-thread checks, immutable reactions and overlays preserving map/mode/topology/life/action/route metadata |

Original code remains unchanged at `546c548780c400f21d73ca6713f86194ceb3c844`. `bot-combat.toml` exports data and constants separately from the handwritten policies. The existing 274 resources are preserved. No runtime dependency is added.

## Perception and bounds

Awareness never looks up an authoritative player, entity, world or mode. An unseen shot can produce a delayed glance from a heard position; incoming damage can reveal the same source direction shown to the victim. Odds require range, facing/recent-damage authorization and line of sight. A nearby teammate's death uses the heard killer's last shot or a public team anchor, rather than the complete roster's hidden enemy location. Visible combat, objective carriers, committed roles and exact work retain their source precedence.

Combat searches preserve the source limits: cover checks at most fourteen candidate rays, shelter normally eighteen rays, farther dead-ground fourteen rays, and visible odds at most six candidate enemies. Search geometry is evaluated against the actual private collision map. Per-life state retains at most six shot origins, sixteen seen-fire keys and eight remembered steering sides; awareness retains at most 128 bot lives. Fire entity lists reuse the source identity cache. The perception producer is responsible for its existing roster/entity input bounds; these policies do not claim measured fleet capacity.

An overlay preserves every intent field except the specific movement, look or debug-role change chosen by the policy. A glance cannot replace visible-enemy aim, an action, a survival intent, or an exact movement edge. Fire steering preserves ordinary route authority; an unsafe exact step waits with the source default stationary movement until the hazard expires.

## Evidence

`export_bot_combat.py` invokes the unchanged source on actual compact VXL snapshots and canonical scene edits. It records nineteen weapon envelopes (including unknown tools), fixed-integer mixing vectors, 495 committed-footwork transitions, 72 connected tactical queries and eighteen awareness traces with 731 boundaries. Repeated immutable player/profile/entity/objective inputs are interned in the fixture, keeping the resource small without dropping observations.

Footwork observations compare exact memory as well as output headings and spacing. Tactical observations compare aim, silent-gun detection, cover, relocation, grenade escape, firing lines, shelter, dead ground, walkability and engagement memory. Awareness traces compare complete reactions and complete intent overlays, including unchanged action/route/life/map/mode/topology/debug fields, explicit forgetting, respawn reset and capacity eviction. The grouped Java ownership check rejects off-worker mutation. The selected unchanged-source regression modules are `test_bot_combat_tactics.py`, `test_bot_natural_locomotion.py` and `test_bot_awareness.py`; their brain/director/motor cases exercise the reference, not a claimed Java brain or motor.

Supported-client, fleet/capacity, full source-baseline and final macOS acceptance remain outstanding. macOS is deferred until the complete migration is finished. The previous checkpoint's intermittent Linux standalone UDP map-reload timeout remains recorded; this batch does not claim to fix that test timing issue.

## Checkpoint verification

Ported source weapon engagement/recoil doctrine, committed combat footwork, sight and accepted-fire feedback, collision-checked relocation/cover/shelter/hazard escape, and the complete per-life awareness policy. Awareness includes incoming-fire timing, sound glances, ally-death cues, losing-fight retreat/regrouping, visible-fire steering and own-Molotov prediction; intent overlays retain action, route and generation/life/map/mode/topology fences. **6,483 Java tests pass** (6,393 component + 90 integration), zero failures/errors/skips. All 124 selected unchanged-source combat-tactics, natural-locomotion and awareness regressions pass. Differential evidence includes nineteen weapon envelopes, 495 footwork transitions, 72 tactical queries and eighteen awareness traces at 731 boundaries; 337 immutable input records are deduplicated. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 3207951 bytes, SHA-256 `c72e4b7a8fe222e50314457430e9a0ead9b8f9b52324da7de70dd8c0de3a5230`, class major 69. Eight original source directories and all 274 previous resources remain unchanged; two new resources bring the total to 276, with 87 configured source generators. The four required CI jobs are pending for this implementation; macOS is deferred until the full migration is complete. **Live bots remain gated:** production brain orchestration, mode/cooperative policies, advanced recovery skills, director/motor/action gateway and host composition remain unfinished. The full migration remains incomplete. See [BOT_COMBAT.md](BOT_COMBAT.md).
