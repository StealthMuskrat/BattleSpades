# Authoritative infantry simulation

`InfantrySimulation` now binds the production life/session input timeline to the
real world-bound `PlayerPhysics` body. Its `tick` method is intended for the owned
60 Hz scheduler after packet drain and before terrain commits and replication.
Raw ClientData arrivals update immediate state and timestamps without moving a
body. Ordinary hosting remains disabled; this is an infantry integration path,
not a complete game mode or server loop.

## Behavior

- Loading/closing connections do not simulate. Eligible occupants are visited in
  retained life order; each has an isolated failure boundary. The lag-history
  port runs before the alive/spawned gate, including dead bodies. The after-tick
  port retains the source profile/round/bot-observer boundary.
- Each accepted or synthesized input phase takes exactly one 1/60-second move.
  Crouch geometry, latched aim/locomotion, held jump, burden, class/rule profile,
  ordered ally/enemy collision rows and live terrain use the source update order.
  Immediate aim is restored after a real phase even if movement throws. Eye/aim
  history records completed real/refilled labels, bounded to 128 insertion rows.
- Empty queues freeze both the body and acknowledged label, while running the
  required reload/idle housekeeping port. Enforced airborne starvation applies
  neutral gravity with crouch retained and hover cleared, without advancing the
  label. Timeout uses receive/spawn/pause-rebased monotonic clocks and reports once
  per silence episode; its required disconnect port receives source reason 11.
- Observation-only backlog reports preserve ordinary pacing. Enforced backlog
  uses source delay hysteresis and a 60-tick observation window. A second move
  requires contiguous received labels, unchanged topology, no pending terrain
  mutation/impulse and no hover transition. Otherwise it discards down to the cap,
  retaining source pending-input and impulse boundaries without inventing steps.
  Settled retail idle pairs use the separate source catch-up rule and nearby-body
  guard. Queue-delay samples retain the source 600-row bound.
- Labelled velocity impulses retain order, a 64-entry bound, immediate stale-label
  and overflow application, and reset on death/spawn. Impulse addition uses the
  source clipped façade velocity; the ordinary mover retains its raw velocity
  bits. The clipping threshold is 1e-4, distinct from the idle policy's 1e-7 test.
- Fall damage tracks source airborne time and own-rocket timing, including fields
  that survive spawn. Landings use native fall results, water/rule gates, short-air
  scaling and own-blast scaling before calling the accepted-health path. Lethal
  HP/death ordering therefore uses the existing reliable production path.

## Required ports and limits

The caller supplies actual lag-compensation history, reload/idle housekeeping,
objective burden, queued terrain count, diagnostic reports, transport disconnect,
post-move effects, post-tick observers and failure reporting. These hooks are
required; inert implementations appear only in test support and are not packaged.
Their placement is verified, but their unported domain work is not claimed.

Flight-equipped bodies and Crumbling Run momentum fail explicitly in this binding.
Jetpack fuel/thrust handoff, parachute deployment/owner handoff, disguises, mounted
entities and other mode/ability effects still need their own source-backed paths.
This checkpoint covers ordinary infantry movement for all 18 numeric class
profiles without flight equipment, not complete playable class abilities or modes.

Packet-rate/conduct admission and configurable ahead-label enforcement remain
upstream work; LifeRuntime currently records already-admitted input with the
source default one-frame latch and observation-only ahead policy. Bot motors,
deferred explosion impulses, detached projectile actor lifetimes, complete reload,
firing/hit/friendly-fire authorization and full scoring remain pending. Eye history
is available, but authoritative hitscan rewind and owner WorldUpdate construction,
self-row eligibility and correction/replay are not yet bound. No normal hosting,
supported-client round or gameplay capacity/soak result is inferred.

## Source comparisons and integration evidence

`generate_simulation_golden.py` runs unchanged original
`SimulationRuntime._simulate_players`, `Player.simulate_tick/update`, movement
handler, starvation/backlog/impulse/fall methods and the real native World mover.
No movement or policy function is replaced. Controlled inputs are ordinary bodies,
world cells, clocks, rules and enforcement flags. A profile hook observes original
reload, lag-history, after-tick and anticheat-report call boundaries. Original
observers execute; their unported award/reload/replication results are outside the
expected-state comparison.

The fixture contains **16,683 operations in 33 traces**, including all 18 class
profiles, nonunit/tiny/vertical aim, loading/death/spawn, withheld/lost/reordered
input, safe catch-up, enforced drops, topology and pending-mutation guards,
starvation observation/enforcement, pause rebasing, collision filters, burden,
labelled impulse overflow, water/ordinary/own-blast and lethal falls. It compares
exact exposed raw body and clipped façade bits, input/pacing state, eye-history
and chronology digests, queue delays, callback order and HP/death bytes. Digests
compact complete histories; no approximate numeric tolerance is used.

Focused unchanged original input-loss and movement-anticheat regressions pass
**57 tests**. Additional Java checks exercise scheduler/loading gates, unsupported
flight failure isolation and immutable history. The real UDP test feeds production
sessions/lives/history, advances the owned scheduler, verifies real/refilled moves
and starvation freeze, then observes a genuine lethal landing's zero HP before
KillAction. It uses explicit test mode/reload/lag/effect ports. Packaged native
probes retain FixtureScene and do not establish supported-client gameplay.

Independent CI for implementation `1e4c34fced6e6fc96185fd72b212d62b1e3eab70` and tested tree
`4a54308beb4e425dbfa9ea0ab2ba0176b619ccf6` passed all five jobs in
[run 38834640118](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38834640118). The native job rebuilds the unchanged source world and
regenerates the infantry fixture/provenance exactly. The build repeats and
byte-compares its JAR; Linux, Windows and macOS test one uploaded artifact and
run their source/component/UDP suites. Local clean check/build passes 4,499 Java
tests (4,487 unit/component + 12 UDP), zero failures/errors/skips. These checks
retain the gameplay and reference-baseline limits above.
