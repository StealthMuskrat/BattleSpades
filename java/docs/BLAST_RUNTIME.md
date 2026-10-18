# Shared blasts and explosive devices

Source: `6216b771282d2d349ccd67ee58c378f035c83553`. This is a component
checkpoint; complete gameplay hosting remains gated with exit 2.

ExplosionGeometry implements the original 23 stock handler variants, absolute
matching tolerance, squared body/point falloff, self/neutral/classic reductions,
three weighted sight rays, their 10%..110% segments and normalized world queries,
victim class/flying-pack scaling and additive impulse arithmetic. Source constants
are exported from unchanged tables and literal source assignments. Comparisons
require exact binary64 values for these helpers; the earlier shot-geometry 16-ULP
bound remains scoped to its documented intersection/libm cases.

BlastRuntime commits stimulus, optional terrain, players and then a fresh entity
snapshot in source order. Stock handler matches override radius/knockback/classic
before health. Simulated hitbox crouch controls blast damage and previews; input
crouch controls a queued impulse when its frame actually applies. Physical push
precedes the friendly-fire HP gate. Fractional HP damage passes through the owned
LifeRuntime policy once before rounding. Existing blast scopes retain outermost
instigators through chain reactions, including teammate-triggered owner deaths.
Achievement begin/end faults are isolated at the source boundaries, with cleanup
on stimulus/commit exceptions. Mode, achievement and stimulus services are required
ports; test observations do not supply their full concrete implementations.

Predicted native Damage packets select the source's three received-input-frame
delay. Pending effects retain origin/radius/knockback and recalculate against the
live authoritative position and input crouch when the dense accepted receive
sequence is consumed. Sparse protocol labels and synthesized frames do not advance
that witness. Overflow/bot fallback applies immediately. Backlog discards apply
due effects; pending effects block unsafe paired catch-up. Death/spawn clear the
queue. Own rocket pushes retain the existing landing-damage policy.

BlastTerrain composes actual health/shades/native footprints and collapse. A peer
that knows the causer gets the compact native packet; an in-game peer that never
saw it gets exact type-6 removals and surviving-cell hits. Loading peers are gated.
Projectile-style blasts broadcast their native packet without a device reference.
Empty stock footprints retain prediction; unknown footprints use the source cube
path and ordinary per-cell admission. Anonymous actor -1 is represented as the
source wire byte 255. Canonical state journals retain safe late-join outcomes
without an expired charge reference. Source-global HitEntity delivery is preserved.

TimedExplosiveBehavior, ProximityMineBehavior, RemoteChargeBehavior and GraveBehavior
implement authored behavior parameters. Timers start on first tick. Mines use
horizontal range and vertical feet/layer detection and ignore player LOS for their
blast. Charges use their rendered attachment-face center. Shooting C4 removes it;
remote activation or support loss detonates it. Owner charge lists retain the
source's cleanup/reset quirks. Graves can blast at their settled center while
retaining an airborne model position. Every blast marks its entity dead before
terrain/player/entity damage; synchronous support chains cannot recurse into that
same live object. A canonical removal binding resolves support immediately before
catch-up recording, isolates listener faults, and commits a whole collapsed batch
before support callbacks. Placement authorization/default assembly remains pending.

Source evidence executes unchanged behavior/registry/HP/native-world methods.
`reference_blast.py` compiles unmodified method ASTs and the original achievement
decorator, avoiding unrelated launcher imports. Clocks, settings, inputs and IO
ports are controlled; no policy function is replaced. The new comparisons cover:

| Evidence | Cases / operations |
|---|---:|
| Geometry, LOS/raycast order and impulse arithmetic | 4,096 |
| Stock handler matching | 161 |
| Generic victim multipliers | 288 |
| Shared player/entity blast commit | 576 |
| Achievement/stimulus fault boundaries | 8 |
| Full native-mover dense-input impulse timelines | 3,659 |
| Native/exact terrain commit and effect order | 576 |
| Concrete device, support and chain lifecycle | 768 |

The new three-client UDP test drives the automatic owned-shot path into two real
mines. Support loss chains the second blast; death credit remains with the shooting
teammate. Known peers receive both native causers and ordered destruction; a peer
without either create receives exact cell outcomes. The shooter's HP is unchanged
with friendly fire off, but its two pushes apply on dense sequence four through
sparse/synthesized labels. Actual reliable delivery, ammo, HP/death and registry
removal are checked. Test mode/effect ports and authored placement remain explicit.

Pickups/medpacks/radar, placement/equipment authorization, projectile/corpse
simulation, full debug telemetry, concrete modes/services, supported-game-client
validation, capacity and soak remain pending. The original full baseline still
has its documented incomplete runs/VIP discrepancy. Local verification passes 5,340 Java and 91 focused original tests.
All 55 configured generators reproduce source outputs after a fresh native build;
208 recorded hashes remain unchanged. Repeated CRC-valid archives, packaging,
smokes and isolated native probes pass. Publication and all five current-head CI
jobs are pending; see [CI_STATUS.md](CI_STATUS.md). Native packaged-loader probes continue to use FixtureScene.
