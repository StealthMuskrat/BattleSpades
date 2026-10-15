# Spawn geometry, selection and rescue

Behavioral reference: `6216b771282d2d349ccd67ee58c378f035c83553`.
Source: WorldManager spawn helpers, MapZone, spawn_selection.py,
RoundLifecycle.resolve_player_spawn and combat_runtime.segment_clear.
These are placement components. Actual Character health/equipment, spectator
camera lifecycle, death/respawn scheduling and playable modes remain pending.

## Geometry and live terrain

MapMetadata/MapAssembly now supply file-backed layouts; see [MAP_METADATA.md](MAP_METADATA.md).
SpawnGeometry consumes an immutable Layout of authored spawn/base
zones and fallback regions. It retains source truncation/inclusive XY bounds,
mirrored spawn-box drop ranges, floor discovery, water-zone priority and ordered
candidate enumeration. Candidates distinguish top-surface columns from explicit
indoor storeys/sea floors. They are revalidated against committed live voxels.

Dry column checks include the full standing footprint, 3x3 levelness, nine-voxel
terrain support and the source roof percentile test. Authored floors also require
reachable air, three clear body voxels, neighboring support and a bounded pocket
flood fill. Generic position validation requires body clearance and foot support;
only a team's authored zombie_spawn_area admits its retail sea spawn. Boundary
probes retain the source z=239-to-238 collision fold and negative-Z air behavior.

Source coordinate conventions are deliberately distinct: a new spawn begins
surface minus 2.75 blocks; a base/feet anchor uses surface minus 2.25. Authored
interiors can retain their floor instead of being moved onto the roof.

MutableWorld caches raw surface scans per column and invalidates a changed column
on topology edits. Its committed revision supplies the selector's mutation-version
change signal, including paint commits; revision numbers are internal, not wire
state. VxlMap exposes a read-only raw scan. Neither query needs a native index.
Geometry, random stream, caches and world access remain on their owning thread.
Call prewarm during loading before ordinary life placement; it is not a constant-
time operation or an off-thread read of a mutable world. Base anchors are map-stable.

## Selection and availability

SpawnResolver ports bounded threat selection: at most 20 sampled candidates per
pass, up to six nearby enemy sight checks, enemy distance/danger gates, recent
same-team deaths, all-team spawn stacking, ally support and jitter. A stable
descending sort and weighted pick among the top three preserve source ordering.
Hot or unusable bases can select a clearly safer candidate from the cached team
region. Sight uses the source combat segment traversal and 0.3-block endpoint
shrink, rather than substituting the native hitscan policy.

Death/spawn memories retain at most 128/64 rows and use the source 20s/6s windows.
A changed concrete mode identity clears intel and the emergency grid; a new map
uses a new geometry/resolver pair. A supplied selection random stream remains
separate from the shared emergency stream, as in the original rng argument.

Emergency placement caches a coarse 8-block grid. It refreshes after a version
change only when at least 15s have elapsed, and always revalidates a used column.
It checks up to 96 cells per pass, preferring team-side strict terrain, global
strict terrain, then global standable dry surfaces. It spreads over the first
three valid nearest cell centers. Its last resort is the team-base water position,
lifted until the full body is clear. Rescue first accepts a valid team-authored sea
spawn, then searches a six-block ring, then uses this bounded emergency path.

The resolve boundary catches a raising/invalid mode proposal and retries failed
rescues at the base. Finite squared-distance overflow follows Python's exception
fallback instead of silently sorting infinite distances. Raw Position accepts an
invalid proposal so it can be rescued; only its resolved toPhysics conversion
constructs the strict finite PlayerPhysics vector.

SourceRandom implements CPython 3.12 integer-seeded MT19937, rejection-based
randbelow, sample, shuffle, choices and uniform arithmetic. It is not a security
PRNG and does not implement Python string/float seeds or full random-module APIs.
SpawnMath retains CPython's compensated hypot and Python 3.12 floating sum so
inclusive radius gates, scores and seeded decisions compare exactly. Portable
Math.fma is a Java operation; no application native calls or Python runtime exist.
BSD/PSF notices and the Java adaptation description are retained in the JAR.

## Evidence and remaining integration

generate_spawn_golden.py directly imports and executes original policy modules
and compiled original VXL/world. It controls only synthetic input cuboids/layouts,
clock, seeds and mode proposals. No policy function is substituted. Python 3.12:

```
python -S java/tools/generate_spawn_golden.py
```

Eight synthetic maps cover dry ground, roofs/interiors, multiple storeys, small
sealed versus larger pockets, water/shore, slopes, map edges and a dug-out world.
Committed deterministic gzip fixtures contain 18,124 geometry/segment vectors,
seeded selection/rescue/mode-reset/edit events, 16,000 mixed random operations,
3,064 hypot and 3,000 compensated-sum vectors. An additional 8,640 original physics
ticks settle resolved placements through all 18 class profiles. Comparisons use
exact cells/ordering, raw floating bits, state flags and landing results.

The new real-UDP test exercises configured admission using this placement policy,
then removes the first spawn's terrain and checks that the next join gets a safe
live position. The rest of that player domain remains a testSupport fixture.
The focused original retail-box, smart-spawn, TDM-spawn and spawn-lock subset
passes 42 tests; this does not replace the incomplete full Python baseline.
CI regenerates all spawn fixtures/provenance and requires an empty diff.

The sidecar parser and map/mode-to-Layout assembly are described in
[MAP_METADATA.md](MAP_METADATA.md). The legacy
plain get_spawn_point shuffle/cluster/exhaustive fallback and non-playable native
random-position path are not exposed as implemented APIs here. Concrete modes
must supply their actual resolver through the boundary; the supported bounded
selector is not an implementation of every mode's spawn policy. Full server
identity, Character lifecycle, input/equipment timing, WorldUpdate, combat and
mode transitions remain pending. Normal hosting still exits 2. There is no
original game-client, 50-player, p99 or soak verification claim.
