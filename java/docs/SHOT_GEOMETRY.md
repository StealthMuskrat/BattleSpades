# Shot validation, firing patterns and read-only hit selection

Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`, principally
`server/combat_runtime.py`, `server/player.py`, `server/action_clock.py`,
`server/anticheat.py`, `server/world_manager.py` and `aoslib/world.pyx`.
These are components for the combat commit path. Full shot handling, damage and
presentation effects, concrete entity/corpse registries and modes remain pending.
Normal hosting continues to exit 2.

`ShotValidation` checks finite wire values and nonzero aim before constructing
physics vectors. It uses the bound PlayerLife's own-frame eye/aim history,
current eye/aim and applied-label lookbacks of 6, 12 and 24 frames. The hard gates
retain source origin distance, actual terrain line of sight and coarse aim dot.
Soft origin and aim checks retain source default log-only behavior and explicit
configuration switches. The fallback allowance uses clipped authoritative
velocity. Rejection reasons, signed-zero formatting, decimal rounding, reports,
violation counts and histogram buckets match the source fixtures. PlayerLife owns
these statistics across respawns. This is not the full anticheat service or its
rate-limited logging/reporting/administration layer.

`CombatGeometry` implements endpoint-shrunk Amanatides–Woo visibility, exact
voxel probe order, ignored cells, and the centre/face/corner cell samples used by
terrain reach checks. It exports all 18 original class KV6 bounds plus crouch and
Classic-corpse bounds. Body tests retain physical crouch/hover state and the
source torso, head, arms, left-leg, right-leg priority. They deliberately return
the first intersected part, rather than the nearest part.

Java and the reference platform's native libm can round atan2/sin/cos/pow
slightly differently. Geometry tests therefore use a 16-ULP coordinate-scaled
bound for numeric intersection coordinates/distances. Hit/miss, part identity,
headshot identity, terrain visibility and every voxel probe remain exact checks.
Boundary cases test each class and crouch state at slab faces and adjacent
representable coordinates. This does not establish universal native floating-point
bit identity or eliminate the need for supported-client validation.

`FiringPatterns` composes original assault three-round grouping and minigun
spin/ramp/recovery rules with the actual owned PlayerFire, ammo wallets,
ActionClock, controls, RTT/variance and reload floor. Failed attempts do not
advance successful burst/spin state. Explicit forget removes state before a
numeric identity is reused; ordinary life changes retain source combat grouping.
These gates do not authorize tool selection, objectives, mode actions or mounted
machine guns. The existing source gun/reload timing comparisons remain in force.

`HitTrace` compares live players, damageable entity spheres, hittable Classic
corpses and current terrain by actual entry distance. It preserves stable first
selection, enemy/dead/spawn filters, the terrain block-centre cap, and tie priority
(player, then corpse, then entity). Player body reads pass through the existing
scoped lag-compensation owner. Returned hits reference the actual owned life or
the supplied domain object; bodies and terrain are not rewound or mutated.
Its scene requires concrete enemy and registry ports. No empty production
registries or permissive mode implementation are created. Terrain raycasting
composes the verified accurate query and the source manager's 0.1-step fallback.

| Unchanged original comparison | Operations |
|---|---:|
| Voxel visibility, body/corpse slabs and sphere entry | 48,440 |
| Shot checks, reports, counters and exact probe order | 6,400 |
| Assault/minigun with actual gun and action-clock state | 24,820 |
| Player/entity/corpse/terrain hit selection | 6,000 |
| Actual native WorldManager ray and fallback | 1,600 |
| Total new source operations | 87,260 |

All policy methods run unchanged. Generators control clocks, domain inputs and
terrain/registry fixtures; sys.setprofile observes shot reports without replacing
methods. Geometry fixtures are deterministic small gzip parts. Provenance records
source and fixture hashes and distinguishes isolated selection from concrete
registry integration.

A real two-client UDP test uses the actual loader, connection-bound lives, packet
6 decoder, validation and terrain/body trace. It ignores the forged shooter byte,
rejects distant and wall-occluded origins, identifies the live target, then
identifies a newly committed intervening wall. Health and ammunition remain
unchanged: the explicit test handler exercises read-only selection, not complete
shot admission or damage. The earlier UDP timing/reload and replication tests
remain separate evidence. Native packaged-loader probes still use FixtureScene.

The production packet-6 commit path still needs source action/tool/objective and
mounted-gun authorization, disguise/protection transitions, feedback and scoring,
seed observations and pellet expansion, damage/shields, terrain edits/collapse,
entity/corpse effects, melee reach/digging and mode integration. Source reference
tests and component/UDP evidence do not establish a playable Java round. Full
Python baseline, supported-client, capacity and soak limitations remain open.

Implementation `29156d7ccb3c47b582f7ae5570dcfde204c13282` and tested tree `a0ab3bedac6a0777a30c620267bdb8188e4dc1f9` passed all five jobs
in [run 39075436598](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39075436598). This includes original-source fixture regeneration,
repeated archive comparison, native probes and one uploaded JAR tested on three
platforms. Local verification passes 4,794 Java tests and 223 focused original
tests. The numeric comparison bound and gameplay/domain limits above remain in force.
