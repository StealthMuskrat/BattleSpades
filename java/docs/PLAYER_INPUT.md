# ClientData chronology and action cadence

The production life domain now records ClientData in an owner-confined
`InputTimeline` before forwarding fresh immediate state to its required input
policy port. Arrival never advances a body. The session's reserved identity is
used; the claimed ClientData player ID does not choose the actor.

## Implemented boundaries

- Retain the first complete buffered row, including receive tick, causal owner
  sequence, dense accepted-input sequence, unknown wire byte and terrain revision.
  Sort by signed client label, retain at most 128 rows, and distinguish duplicate,
  stale, overflow and far-ahead enforced rejection counters.
- Record zoom and first arrival tick even for dead, stale and duplicate input.
  These bounded insertion-order histories support delayed reliable action packets.
  The zoom lookup retains the original three-frame leniency, twelve-frame search
  and unknown-state fallback. It is evidence, never hit authorization.
- Measure recent unsequenced arrival displacement in bounded time buckets. A
  backward jump greater than ten labels is a ClockSync relabel. Only fresh
  arrivals update immediate aim and reach the immediate tool/action policy port.
  As in the source, freshness is independent of stale/far-ahead buffer rejection.
- `consumeOne` selects one movement phase, without a physics call. The default
  latch combines previous locomotion/aim with current crouch and action state.
  Optional unlatched selection is independently compared. Small missing-label
  gaps synthesize one held phase; wide jumps do not invent elapsed ticks. The
  selection exposes source label/tick/causal clock/unknown byte for reconciliation.
- Late originals of synthesized rows are salvaged once. They can repair the
  pending locomotion/aim or latch one otherwise-lost jump/hover press. Held fire,
  zoom and movement directions are not latched as presses. Synthesized phases
  retain action state and do not claim a newly received explosion witness.
- Spawn clears queued rows, receive sequence, phase cursor and presses. It retains
  connection newest-label/reorder evidence, owner causal clock, zoom/arrival
  histories and cumulative counters. The source's synthesized-state flags also
  survive until the next real phase. Disconnect/reused slots get fresh objects.
- `ActionClock` ports source cadence peek/admit/reset. It combines plausible
  labels, arrival deadlines and a bounded server-time token bucket sized from
  ENet RTT/variance. Interval changes preserve time credit; the previous action
  owns its cooldown. Peek does not create/spend a lane. Spawn resets lanes.
  Backward clock relabels, refusal accounting, zero/negative intervals and ordered
  Python min/max behavior are retained.

## Required next integration

The subsequent [infantry checkpoint](INFANTRY_SIMULATION.md) binds `consumeOne` to
real no-flight movers with outer starvation/backlog, velocity impulses and eye
history. It retains explicit reload/lag/mode/effect ports and does not complete
the gameplay loop. This original chronology checkpoint does not itself drive
`consumeOne` from a complete gameplay loop.
The owner loop must bind outer starvation/backlog/rate/conduct policies, equipment
and fuel transitions, velocity/explosion impulse queues, the verified mover,
eye/lag history and owner WorldUpdate reconciliation at their original boundaries.
The shared owner clock must also be claimed when actual owner rows are queued.
The production LifeRuntime binding currently uses the source default one-frame
latch and observation-only ahead-label policy; complete configuration remains
pending. Upstream admission must apply packet budgets before recording input.

The immediate input port still supplies interpreted buttons, tool authorization,
conduct and action effects. Stale rows are recorded before freshness suppression;
conduct observers must observe the receive stream at its upstream boundary.
`ActionClock` supplies timing only. Weapon/reload/stock checks, origin/hit validation,
friendly fire, mode policy and the final action-commit boundary must be implemented
before any raw client handler can call accepted damage. Detached projectile actor
lifetimes also remain pending. No new packet invokes damage in this checkpoint.

## Independent evidence

`generate_input_golden.py` executes the unchanged original ClientData handler,
receive/consume/salvage/zoom methods and real native Player.update. A profile hook
observes raw phase values at the original update boundary; it replaces no method.
Comparisons cover complete chronology state and metadata plus selected phase and
action-query results: **14,234 operations in 24 traces**, both latch settings,
loss/reorder/duplicates, presses, capacity, relabels, signed label extremes, dead
input and respawn. Physics/equipment outputs, observers and awards are outside
this comparison, rather than replaced by convenient reference implementations.

`generate_action_clock_golden.py` executes unchanged source cadence functions:
**26,234 operations in 32 traces**, including peeks, refusal, interval changes,
clock reversals, RTT/variance bounds and nonfinite interval/link inputs. Original
input-loss and action-clock regression modules separately pass **31 tests**.
CI regenerates both fixtures and provenance and requires exact differences.

The additional real-UDP test uses production session/life/history code and explicit
test mode/input/effect ports. It checks spoofed packet IDs, out-of-order fresh-state
suppression, no movement on arrival, phase metadata and respawn reset. It does not
exercise a supported client's full gameplay loop or WorldUpdate reconciliation.
Packaged native loader/transport probes still use the earlier FixtureScene domain.
Ordinary hosting remains disabled with exit 2; complete modes, supported-client
rounds, gameplay platform coverage, capacity and soak remain unverified.

Independent CI: [run 38830815361](https://github.com/StealthMuskrat/BattleSpades/actions/runs/38830815361) passes all five jobs on
`7d0d68e8e3fda4974fe7ebc070e24f7833b74a25`, including exact source-fixture regeneration, repeat-archive
comparison and same-artifact smoke/component coverage on all three OSes. These
results retain the gameplay limitations above.
