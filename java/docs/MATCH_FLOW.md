# Combat economy, escape watch and round-clock batch

## Current test execution

The later Java test optimization retains all distinct match-flow cases and ordered
traces while removing repeated stateless executions. Current totals are 5,912
component + 33 integration = 5,945 tests; all five jobs pass in [run 39145475640](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39145475640).
The original 96-test batch counts below describe the preceding implementation.
See [TEST_OPTIMIZATION.md](TEST_OPTIMIZATION.md).


This checkpoint implements four connected policy areas plus bounded HUD builders
and two native codecs. The migration remains incomplete; normal hosting exits 2.
Behavioral source remains `6216b771282d2d349ccd67ee58c378f035c83553`.

- `GenericEconomy`: BaseMode kill/headshot/melee scores, revenge/payback consumption,
  suicide/teamkill exemptions and penalties, reloading kills, defend/distraction
  credits and round awards. Departed actors receive no score mutation or packet,
  while the original nominal return values, bonus consumption and award order remain.
- `TeabagTracker`: three crouch press edges, horizontal/vertical corpse distance,
  per-connection `(enemy id, death time)` deduplication, classic/ordinary profile
  counters, round awards and optional rule-gated points. Rule lookup errors suppress
  points without breaking input. Bot profile suppression comes from ProfileTracker.
- `EscapeWatch`: native-column/body/256-cell LIFO pocket classification; throttled
  holds, two-clean-check hysteresis, actual object/roster/mode identity, stale-slot
  removal, marker ownership/reassertion, death/forget cleanup and late replay. The
  source uses latest received crouch input, including when mover posture differs.
- `RoundClock`: elapsed wall time, reset/re-arm, stale-cue windows and final countdown,
  timeout music/vote boundaries, a fixed accepted-event remainder, score/time winners
  for zero/one/multiple teams and result headline selection. Full event dispatch,
  asynchronous mode ending/restart, achievements, voting and audio remain required.

`Announcements` preserves original bounded strings, Unicode scalar lengths,
free-form variable boundaries, localized HUD parameters and safe names. New packet
17 ChangePlayer and packet 50 LocalisedMessage bring supported packet IDs to 84
(57 generated straight-line codecs plus 27 handwritten IDs). The existing byte
facade's protocol-safe string contract still rejects embedded NUL; arbitrary Python
objects, unpaired surrogates and unbounded integers are outside these typed APIs.

## Source and composition evidence

`generate_match_flow_golden.py` calls the actual unchanged policies, BaseMode,
SimulationRuntime, native WorldManager/VXL and native codecs. Inputs are controlled
actors/configuration, accepted damage, clocks, mode marker/objective callbacks,
rule-service read faults and terminal vote/plugin/broadcast IO. No source policy
method is substituted. Clock reference runs configure the original stopping flag
to suppress delayed end/restart IO; equivalence for that full sequence is not claimed.

There are 10,752 recorded policy/codec/HUD rows: 2,048 each for economy, teabag,
escape watch and clock; 512 multi-team winner cases; 1,024 native codec cases; and
1,024 HUD-builder cases. Escape traces also include 1,824 native voxel edits and
6,144 direct geometry evaluations. Ninety-six component tests compare recorded
state, exact binary64 values, original packet bytes and mutation/failure order.
The existing match-catalog exporter also adds the newly referenced neutral-team
constant; prior score-evidence bytes stay unchanged and its catalog provenance
is refreshed. The generated packet dispatcher adds the two new IDs.
The first full local regeneration check found only two missing impact metadata
fields in flight-provenance.json; all behavior fixtures matched. Rerunning the
original flight generator's `--impact-only` path reproduced its 10,368 vectors and
restored both fields. The final full 241-file hash comparison matches; the initial
check and corrective generation are retained in the verification record.

Two real UDP compositions bind actual life damage/death and damage ledgers to
reload/defend/distraction scoring, profile credit, once-per-death teabag credit
and reliable score delivery; and native terrain/escape marking to objective
presence, late marker replay, marker reassertion/clear, localized countdowns and
a timeout winner that waits only for the frozen accepted-event remainder.
The test's required end/result boundary emits awards after that winner is fixed.
Test ports are excluded from the executable.

Concrete modes must own canonical Scoreboard.Player rows, capture reloading before
LifeRuntime clears it, forward bonus/exemption and life events in source order,
consume accepted mode events serially within the budget and provide current mode,
audio/vote/achievement/end/result services. RoundClock exposes the source budget
arbitration; it is not the complete outer asynchronous queue. Teabag input edges
and escape ticks require ordinary gameplay-loop binding; call TeabagTracker.forget
when disposing a player connection. Required profile/result
persistence, projectile/corpse representation, all concrete modes, authenticated
services and full gameplay bootstrap remain pending. Original Zombie/VIP baseline
discrepancies remain unwaived. The expanded reference suite additionally retains
`tests/test_mode_fixes_round2_scoring.py::test_medpack_heals_a_boosted_vip_below_their_max`:
the unchanged test passes `ctx=None`, while unchanged MedpackBehavior reads
`ctx.server`. Both files exactly match the behavioral source; no source/test
change, skip or waiver is applied. This is outside the new policy areas. Supported-client and capacity/soak validation are
pending; native packaged-loader probes retain FixtureScene.

Local clean check/build passes **5,965 component + 33 real UDP = 5,998 Java tests**,
zero failures, errors or skips. The expanded original suite has **162 passes
and one unchanged medpack/context baseline failure**, with no errors or skips.
That failure is retained and unwaived; this is not a passing full-source baseline.
The batch adds 96 component tests, two real UDP compositions, 10,752 policy/codec/HUD
rows, 1,824 native voxel edits and 6,144 direct escape geometry evaluations. All 67
configured generators reran after fresh original five-module native and ENet builds;
all 241 recorded fixture/catalog/codegen hashes match. The frozen set includes
existing foundation files, without claiming every file is generated by those scripts.

Two CRC-valid local archives are byte-identical: 1731239 bytes,
SHA-256 `30495db15a5e695ccbff378c48e973f6e0306f0131d77e6b7b2c280d9b33e4d1`. Class major 69, packaging/licenses and smokes pass;
version/config exits 0, hosting exits 2. All four isolated native nominal/impaired
transport and packaged-loader profiles pass. No local-to-CI hash identity is claimed.

All five jobs pass in [run 39144143660](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39144143660); see [CI_STATUS.md](CI_STATUS.md).

The initial implementation `a831f1fe8226cde5dae8c429727eb4f3202409a3` passed the
build, native, Ubuntu and Windows jobs in run 39143288035. macOS component tests
and artifact smokes passed, but the existing UDP replication/respawn test timed
out after ACK release. Its test synchronization now waits for reliable receipts
without expiry, then broadcasts at cadence until the released row is received.
The row-hold assertions and 4s/15s timeouts remain unchanged. This failure and its
logs are retained; the corrected checkpoint passes all five jobs in run 39144143660.

Implementation `6f904c4f28917a03e3d0a94de259e84213bd9025`, tested/published tree `7803354387083bbf86a98bfaea387ad65d8098ed`. Complete gameplay and supported-client/capacity gates remain pending.
