# CTF and accumulated runtime checkpoint

The current accumulated implementation batch passes **6,294 component + 44 integration = 6,338 Java tests** across 113 suites, with zero failures, errors or skips. The consolidated Java 25 `check build`, strict compiler warnings, portability checks and distribution packaging pass. All **47 selected original CTF/Classic/objective regressions** pass freshly. The preceding 326 selected Python checks were run for the preceding implementation snapshot and were not rerun here.

Packet-generator reproduction is exact for all three outputs. Frozen Java fixtures/catalogs and the six original source directories remain unchanged. The CRC-valid JAR is 2261083 bytes, SHA-256 `3206345496e67d6a95b54772b888b3b24c8262cdad26f3b942ea77ffc6853091`. Version/configuration smokes exit 0; ordinary hosting remains gated at exit 2. Cross-platform/native CI is pending for this checkpoint. Full migration, production service composition and supported-client acceptance remain incomplete.

## Connected implementations

The checkpoint includes the preceding projectiles, deployables, MG/turrets, disguise, fire/goo, deferred construction, prefabs, corpses, conduct and public-command owners described in [IMPLEMENTATION_PHASE.md](IMPLEMENTATION_PHASE.md), together with CTF and Classic CTF factory bindings.

CTF owns ground intel and private base markers, source-derived base/intel anchors, raw-voxel packet-43 HUD bounds, capture boxes and open-pit tolerance, LOS pickup policy, canonical carried state, packet-71 validation, forced death/team/departure release, touch/timed returns, buried/floating/sealed recovery and same-scene reset. Base type 1 stays private; only type-16 intel enters entity replication. Late joins receive both base zones, carried intel and exposed carrier markers.

Capture updates personal/profile/team counters and the HUD before ending the round. Winning captures stop the rest of the pass. Carry/exposure/escort deadlines use source wall time, pay once after stalls, and retain object identity through slot reuse. Objective kill classifications run before the victim drops intel. CTF denies shots while carrying; Classic advertises and authorizes shooting with intel by default.

Classic uses CTF wire scene 8, the Classic feature bit, Deuce-only unlocked menus, fixed flare exclusion and operator-aware SMG/shotgun defaults. Playable joins normalize to Deuce; legal selections stage through the existing life transition. Classic binds Character corpses and respawn cleanup, defaults to five captures, and disables ordinary automatic intel return unless explicitly enabled.

## Verification scope

Six grouped component cases cover winning-pass ordering, resource retention, carry/escort exposure and reset, invalid/valid drops, cooldown/return/departure identity, intercept scoring, own-intel capture policy, Classic menus/loadouts/corpses, terrain recovery and open base pits. Two parameterized real-UDP cases exercise both production factories, late-join zones/carriers, private base suppression and canonical packet-71 dispatch. Existing tests and frozen policy fixtures are retained.

The source comparison modules are `test_ctf_entities`, `test_ctf_end_and_departure`, `test_classic_ctf`, `test_retail_intel_radar` and `test_objective_abuse`. These focused passes do not resolve the historical complete-Python-baseline discrepancies. The sparse generic test map was replaced with dry terrain only in the CTF component fixture so an excavated base pit is physically meaningful. Selected Classic weapons remain equipped by the first ClientData after respawn, matching the source.

## Remaining work

Other objective/community/hardcore modes; complete editor/UGC actions; bot director/navigation/action ownership; authenticated identity/admin and remaining commands; persistence, master/discovery, results/achievements and required production observers; explicit startup/ending/transition services; supported-client, capacity/soak and complete-baseline acceptance remain required. CTF achievement delivery still depends on the missing achievement service; this checkpoint does not claim that service or a complete production server.

Machine-readable local evidence: [ctf-local-verification.json](ctf-local-verification.json). CI status: [CI_STATUS.md](CI_STATUS.md).
