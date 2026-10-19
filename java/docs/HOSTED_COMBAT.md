# Hosted shooting and terrain combat

Local Java check/build passes **6,271 component + 41 integration = 6,312 tests**,
with zero failures, errors or skips across 110 suites. **673 focused original-source
regressions pass** freshly: the preceding 417 and seven complete combat regression modules.
This batch adds two grouped component boundaries and extends the existing production-factory
UDP scenario. Existing combat/terrain/firing/rewind policy goldens remain in use.

All 264 preceding frozen paths are unchanged. The existing domain data exporter now also
exports the original block-destruction profile statistic into one new catalog; repeating
that export is byte-identical. There are 265 frozen paths and still 77 configured generators.
The other 76 generator/native-build evidence is reused locally; CI independently rebuilds
and reproduces all 77. No new policy oracle, duplicated matrix or production dependency is
introduced. Two CRC-valid local archives are byte-identical: 2063142 bytes,
SHA-256 `2c2e418f3699783bf2763ac28616e7fc354f668cd9d37f118075b0b290770a3f`. Class major 69, packaging/licenses,
version/config smokes and all four fresh native transport/packaged-loader profiles pass.
The native loader uses FixtureScene; ordinary hosting exits 2. No supported retail-client
test, complete platform gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

HostedCombatRuntime binds the actual CombatRuntime to LifeRuntime before admission in
StandardMatchFactory's TDM/Arena composition. Player services attach first, combat second,
and entity/terrain services third. The required terrain-authority factory formerly supplied
by HostedEntityRuntime.Ports is replaced with the ordinary match's concrete TerrainDamage.Domain.
The new sixth factory port requires the remaining actual corpse, mounted-device, disguise,
shield-anchor, observation, diagnostic, hearing and achievement owners. These domains are
not fabricated by the hosted runtime. Candidate external cleanup remains Services.close.

HostedSimulation records the exact current 60 Hz frame on the match before packet drain.
Combat uses that frame, the shared monotonic life clock and fixed 1/60 tick duration. Existing
LifeRuntime packet dispatch binds ShootPacket to the connection's canonical PlayerLife,
ignoring the packet's shooter byte. Existing validation, ammo/cadence, burst/spread, reload,
read-only rewind, shot tally and life-damage policies remain the authoritative engines.
InfantrySimulation's existing canonical lag history and actual ClientData arrival history
remain in use. Settings use existing source-compatible validation and rewind parsers.

Ordinary TDM/Arena have no allow_action or weapon_fired override: their default shooting and
digging gates are retained. Burden comes from the actual pickup state; shoot_with_intel is
false in these modes. Hit targeting includes allies only when the scene enables friendly
fire. Scoring/profile opponent checks retain their separate team relation, so allied damage
does not become enemy hit/assist credit. Arena damage continues through its existing warmup/
round modifier. Direct-hit entity competition and damage use the actual match EntityRegistry
and EntityContext. Corpse lookup/explosion, mounted-gun authorization and shield anchors
remain required domains. Disguise clears through its owner only after shot admission.

Accepted shots and player hits reach actual ProfileTracker and CombatEvidence rows. Firearm
feedback uses the real host's sequenced-unreliable route and excludes the predicting owner;
its frame comes from the exact frame being drained. Hit response is unreliable, entity impacts
and terrain state are reliable, matching their existing source paths. Statistical observers,
resolved-shot achievements and diagnostic failure boundaries retain CombatRuntime's existing
isolation rather than duplicating the policy in a wrapper.

The concrete terrain domain binds actual source game.build_damage, ordinary digging policy,
canonical regular/collapse repair queues and current frame, actual profile block totals and
CombatEvidence destroyed/chunk awards. It emits immutable on_blocks_destroyed events naming
the canonical match row. Accepted block achievement calls retain the active shot tool from
the actual ShotTally; blast calls retain the source's null tool, including empty native-radius
completion callbacks. Their external achievement service remains required. Block-impact sound
is unreliable after the reliable Damage packet and uses GameplaySounds' real positioned
sound/hearing routing. Source collapse, native footprints, block health/journal, inventory
and repair scheduling are reused without a second mutation observer or alternate ledger.

Grouped component checks trace owned shooting through friendly-fire target choice without
changing profile opponents, then rejection before ammo/disguise/profile effects. A second
trace shoots a supported voxel, verifies real collapse/profile/chunk awards/active-tool
achievement/event/canonical repair, and shoots a registry-owned C4 without detonating it.
The existing factory two-peer UDP scenario retains its previous movement/entity/resource/
blast/repair/clock/metadata/rollover checks and now sends actual packet-6 requests with a
spoofed shooter byte. It proves accepted HP/ammo, owner-excluded feedback, exact observed
frame, hit/profile effects, real terrain removal/profile/mode events and retired combat
runtime closure on map rollover. No copied UDP scenario or extra timing matrix is added;
existing deadlines and policy assertions remain intact.

Device placement/retirement, projectiles/construction/corpses, input/effect ownership,
bot AI/director/shared identity allocation, auth/master, persistence/results/achievements,
ending/reset/HUD factory ownership, full bootstrap, remaining modes, supported-client/
capacity/soak and baseline reconciliation remain pending. This batch completes the selected
ordinary hosted shot and terrain bindings; it does not complete a playable server.
