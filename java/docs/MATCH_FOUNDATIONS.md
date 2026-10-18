# Team, objective and scoreboard implementation batch

Behavioral reference: 6216b771282d2d349ccd67ee58c378f035c83553.
Full gameplay hosting remains gated with exit 2.

This batch implements four connected areas, with two new native packet codecs:

| Implementation | Original policy and behavior |
|---|---|
| TeamState | Team membership by actual player identity; duplicate rejection; cyclic/default spawns; score locks; score, capture and kill counters; intel holder/position; round reset preserves roster, authored spawns, position and score-lock state |
| EscortTracker | Ordered living/spawned teammates within the source 3D radius; hysteresis only for previous members; duplicate candidates collapse; actual object identity prevents numeric-ID reuse from inheriting membership; reset and carrier removal |
| ObjectiveGuard / TrapTimer | Body-sample voxel LOS with source endpoint shrink; buried, sealed, floating and open objectives; source native get_z(start) and dry-surface resettling in entity/player coordinates; grace timers; rounded movement/turn AFK observation, identity reuse, escape-watch lookup boundary and configurable spawn-protection cancellation |
| RoundCombatStats / Scoreboard | Ordered award counters and fractional measures; player/team score packets, source HUD clock clamp, reliable starts and sequenced-unreliable refresh, per-peer known-ID filtering, signed late replay, seeded award selection/ties, one-shot results/cache and reset ordering, scoreboard hold/headline/locks and explicit terminal packets |
| ScorePackets | GameStats(67) signed int32 count/team/player/award rows, including native negative-count roundtrip; ShowTextMessage(73) float32 assignment before native fixed encoding; PacketCodec and its development generator support 82 distinct IDs |

Scoreboard owns match rows associated with actual PlayerLife identity. Concrete
modes must connect lifecycle/score events and the existing LifeRuntime score
presentation policy to these rows. The constructor requires profile-credit,
profile-baseline, known-roster delivery, seeded-random and result-bridge domains.
It does not manufacture a mode, profile engine or persistent result service.
A required profile-baseline reset port clears an existing profile without
creating one. Profile credit runs before departed/reused-slot rejection, and
late reveal sends absolute scores without credit. The native player's own score
is signed int32 on the wire. Counter APIs cover signed-64-bit inputs; checked
arithmetic rejects overflow rather than silently wrapping. Arbitrary Python
integer/object/iterable conversion is outside this typed API contract.

The end-round latch is set before encoding/sending the two team columns. A send
fault can leave it set with an older/empty cache; a second call does not resend or
resubmit results. Both columns must send before the cache is replaced, and result
scheduling occurs afterward. Round reset clears player match counters, round
awards and actual damage histories before each score broadcast; profile/result
baseline ports retain their source ordering. Team scores are not reset by this
helper. GameStats and scoreboard hold do not silently send the terminal
ShowGameStats/MapEnded scene transitions.

Objective recovery reads the live canonical MutableWorld, not a load-time copy.
Its 48-cell LIFO flood follows the original neighbor ordering and checks open sky
and current raw column tops. Resettling preserves native get_z(start) semantics,
including the top-column fallback when no solid cell lies below the start. The
AFK book compares Python-compatible rounded binary64 positions/orientations and
actual player identity. Zone eligibility retains caller-owned life/team gates;
this helper does not add different policy. Missing terrain keeps the original
permissive LOS/unknown-trap behavior. Required escape/protection observer failures
retain the original isolation boundaries. Full escape detection remains separate.

The development generators execute unchanged source policies:

- 2,048 Team operations, 2,048 escort queries and 2,048 RoundCombatStats operations;
- 8,192 actual CPython decimal-rounding cases, including halfway and signed zero;
- 1,536 live native-world objective queries and 2,048 presence/timer observations;
- 2,048 actual scoreboard operations, with 111 send faults (15 during GameStats),
  actual source ProfileStats baseline ordering and the seeded Python random stream;
- 512 native GameStats and 512 native ShowTextMessage codec cases.

Comparisons use exact state, binary64 fields and native packet bytes. Controlled
identity actors, coordinates, lifecycle/config/clock inputs, peer known-ID sets,
terminal sends and result-bridge IO are boundary inputs. No original policy
method is substituted. Profile counter calculations, external persistence and
bridge implementation are not claimed by the Java profile/result test ports.

Two new three-client UDP tests exercise the implementations together. The first
binds team score locks, ordered escorts, live-wall LOS, actual carrier burden and
spawn-protection cancellation, then timed burial recovery with registry and
per-scene destroy/create delivery. The second checks signed scores, a real
unrevealed dead-roster actor, filtered awards, one-shot result scheduling,
post-loading absolute score/award replay, HUD delivery modes, scoreboard controls
and round reset. Known-roster/profile/result/mode composition remains explicit;
the native packaged-loader probes still use FixtureScene.

Local clean check/build passes 5,826 Java tests and 53 focused original tests.
All 65 configured generators reran after a fresh native build; all 235 recorded
fixture/catalog/codegen hashes match. The frozen set also includes existing
foundation fixtures, without claiming all are regenerated by those scripts.
Repeated CRC-valid archives, packaging/smokes and all four isolated native
profiles pass. Publication and all five current-head CI jobs are pending.
Complete concrete modes, outer async packet draining, full gameplay integration,
full original baseline and supported-client/capacity/soak gates remain pending.
The earlier unchanged Zombie cue-lead and VIP baseline discrepancies are not waived.
