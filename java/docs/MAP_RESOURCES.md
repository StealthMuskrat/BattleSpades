# Map-owned resources and server VXL markers

Reference: `6216b771282d2d349ccd67ee58c378f035c83553`. Full gameplay
hosting remains gated with exit 2.

MapAssembly now uses the original ServerVXL load policy. Raw VxlMap loading
retains native codec behavior. Server loading recognizes only the masked green
and blue families, snapshots eligibility with two air cells above before deleting
anything, preserves source iteration order and skips classic64 cleanup. Embedded
markers and team-colored art remain solid. Deleted explicit colors leave the
primitive hash table searchable. Native empty and final-run column encodings
also work when a marker occupied the last solid voxel, including the bed.

MutableWorld restores a flare voxel with canonical alpha 0x80, invalidates its
surface cache, updates the ordered static-light ledger and calls a snapshot of
listeners with isolated reported failures. Health, damage, shade and hit history
are cleared after observers run. Restoration intentionally leaves topology,
dynamic dirty columns, mutation journal and reconnect air masks unchanged, as in
the source. It can retain a previously recorded air mask. Both full map sync and
CRC-matched deltas always overlay marker columns; an unrelated authored flare
alone does not add a dynamic overlay. Its CreateEntity supplies the client voxel.

MapResourceService removes only the five map-owned resource kinds, sending
DestroyEntity for visible live entries before registry removal. Authored pickups
suppress fallback even when every authored position is invalid. Otherwise nine
source-ordered team/midfield positions use the original dry-anchor search.
Same-type pickups share a stateless behavior; respawn delay comes from the rule.
The actual life runtime refills ammo, heals to the body's own maximum, restores
blocks or refills the current jetpack. Counted refills preserve achievement
baseline, action, round-award record and achievement delivery order. Jetpack
refills are uncounted. Personal sounds and aircraft cues use the native codecs;
WW1, lunar and ordinary skyboxes select cue 24, 26 and 25 respectively.

Authored flares precede eligible marker/palette entries. Missing palette families
remain air; there is no inherited palette. The 2,048-entry cap is applied before
validation/restoration. Entity coordinates retain authored fractions while the
voxel uses integer truncation. Static flares have neutral state, no deployable
support/damage behavior, and retain the original repeated-rebuild collision
semantics. Structured diagnostics expose invalid/truncated/missing counts.

The two development-only generators run unchanged original methods over actual
native VXL and Player/refill/registry/packet implementations. Marker evidence:
76 maps, 18,200 point observations, native column bytes, ordered marker lists,
delta hashes and complete inflated Training/Atlantis full-transfer hashes before
and after restore, plus health reset. Cases include all ten stock marker maps,
Atlantis art, 64 synthetic maps and classic64 bypass. Resource evidence: 48 traces
cover authored/fallback/invalid placements, wire/mode gates, fractional and
normalized coordinates, cap ordering, refill/award-hook order, actual round crate
counts, respawn/flyby and repeated removal/rebuild with native wire bytes.
Clocks, resolved anchor-cache inputs and terminal send IO are controlled.
Profiling observes original hooks without substituting policy functions.

A new real three-client UDP test loads MapAssembly, restores authored and marker
lights, checks owner-only healing to 200 HP and personal audio, replays only live
resources to a late joiner, emits the WW1 respawn cue and replaces resources while
preserving a projectile. Existing entity knowledge and reliable delivery are
used. Required score, achievement, audio and broadcast ports have no default
production implementation. The test-domain bindings do not establish persistent
achievement unlocks or complete mode/round/gameplay integration.

Local clean check/build passes 5,563 Java tests and 64 focused original tests.
All 60 configured generators reproduce outputs after a fresh native build;
219 recorded fixture/catalog/codegen hashes remain unchanged. Repeated CRC-valid
archives, packaging/smokes and isolated native probes pass. All five jobs pass in [run 39088025414](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39088025414); see [CI_STATUS.md](CI_STATUS.md).
Objective carry/drop, equipment authorization, projectiles/corpses, concrete
modes/services, complete original baseline, supported-client validation, capacity
and soak remain pending. Earlier original Zombie/VIP baseline discrepancies are
retained unchanged and are not waived.

Implementation `56bb37003980c55de8e37afdba4ed1ac98b2af71`, tested/published tree `4346f7694523cdac88ee6408168218536667f317`. The CI build compares repeated archives and tests one uploaded JAR on three platforms. Full gameplay and supported-client/capacity gates remain pending.
