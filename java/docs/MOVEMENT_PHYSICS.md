# Movement and world-query component

Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`.
This ports `aoslib/world.pyx` Player.update, _move_box,
_check_for_ground_holes, _collide_with_players, set_crouch and world queries,
plus all 18 class movement tables in `shared/constants.py`.

`PlayerPhysics` owns its state on the constructing thread. Position and velocity
remain double-backed as in source Vector3; each source float store rounds
independently. The port preserves expression order, rather than replacing the
collision mover with a generic sweep or substeps. Java uses no FMA here.

Implemented behavior includes:

- Independent ordered movement buttons, float normalization, diagonal acceleration,
  class sprint/crouch/sneak selection, burden and air/ground/water drag.
- Jump request retention and launch flags; active/passive packs 1–4, hover,
  negotiated Engineer acceleration and canopy gravity/free-fall floor.
- Single-pass X/glide/Y/glide voxel collision, climb gating and timers,
  auto-crouch, ceiling contact and frame-start-Z landing retention.
- Negative-Z truncation, horizontal world boundaries and row239 probing row238.
- Ordered peer impulses using one frozen candidate, source axis-degenerate
  horizontal normal and rounded vertical normalization; dead/exploded exclusions.
- Crouch expansion/clearance, hover uncrouch, lock-box clamp ordering,
  actual-displacement falling distance, water damage and slowing landings.
- Dead jetpack corkscrew motion. This does not implement corpse lifecycle.
- Accurate DDA hitscan, source X/Y/Z tie order, optional water at Z>=238,
  cube-line tie order, adjacent cubes, face centers and is_centered.

`CollisionMap.of(MutableWorld)` reads the authoritative committed cells on their
owner thread; terrain edits affect subsequent collision queries immediately.
`CollisionMap.of(VxlMap)` supports immutable base maps. Peer rows are supplied in
source order by the caller and must exclude the local player. The caller also
owns gravity, class/rule selection, equipment activation and input application.
The existing FixedStepRuntime supplies the 60 Hz scheduling primitive.

`update` returns the source landing result: positive damage, -1 for a slowing
landing without damage, or zero. It does not apply health, emit game packets,
activate equipment or schedule respawns. Its jump request can be cleared by an
airborne infantry update; callers must not reapply a cached input every tick unless
the source handler would do so. Crouch requests take the ordered peer rows for
clearance. Public state reads and writes reject another thread.

World queries reject nonfinite/excessive ray lengths (>65536), ray starts beyond
1,000,000 per axis and cube lines longer than 4096 cells. These are explicit work
bounds outside normal world coordinates; the original routines are unbounded.

## Verification

`generate_movement_golden.py` executes the compiled original world.Player and VXL,
not Java, and records all setup, inputs, map edits and peer ordering. It produces
118 cases, 13745 commands and **12924 ticks**. Every exposed position/velocity is
compared by exact double bits; flags and return codes must match exactly. No
numeric tolerance is used. Private fall/climb accumulators are checked indirectly
through later motion, slowdown and damage, rather than exposed by changing source.

Cases cover all classes, straight/diagonal/opposing inputs, non-axis views,
ground/air/water, steps/walls/gaps/ceilings, edge/contact boundaries, crouch/hover,
flight/passive/canopy combinations, peer order/clearance, corpse flight, ledge drift,
lock boxes, damage thresholds and deterministic varying controls/timesteps.

`generate_query_golden.py` executes the original query functions for **941 vectors**
on 211 explicit cells plus the source floor. Ray hit position bits, voxel/face,
complete cube lines, centers, adjacency and centered decisions are compared exactly.
The large trace files use deterministic gzip; tests decode them with the Java
standard library. Fixture hashes and generators are in `src/test/resources/golden/provenance.json`;
movement case names and counts are in `movement-provenance.json`.

CI runs the Java traces on Linux, Windows and macOS. The native-oracle job also
builds only original glm/VXL/world with the source strict compiler flags and
regenerates the fixtures, requiring an empty Git diff. The normal Gradle build
uses committed fixtures and needs no Python/native compiler. No oracle or test
fixture is packaged in BattleSpades.jar.

To regenerate using a development Python3.12/Cython3.0.12/setuptools80.9.0 and GCC:

```sh
python java/tools/build_reference_world.py
python java/tools/generate_movement_golden.py
python java/tools/generate_query_golden.py
```

## Integration still required

This is a verified component, not a playable domain. Actual configured JoinScene,
player admission/spawn/death, live input/equipment policy, movement history and
reconciliation/anticheat, authoritative WorldUpdate construction and full game
replication remain pending. General GLM matrices/quaternions/model transforms,
projectile/entity simulation, combat, collapse and complete game modes are outside
this component. Original-client gameplay and capacity/soak checks have not run.
Normal JAR hosting remains disabled with exit 2.
