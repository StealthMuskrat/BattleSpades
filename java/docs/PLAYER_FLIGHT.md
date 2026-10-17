# Connected-player flight integration

`PlayerFlight` binds original jetpack fuel/activation and parachute policy to the
same owned `InfantrySimulation` steps as accepted or refilled ClientData. It keeps
advertised state separate from the state supplied to the real `PlayerPhysics`
body. Arrival alone never moves the body or advances flight resources. Ordinary
hosting still exits 2; the game domain and supported-client gates remain incomplete.

## Source behavior

`FlightProfile` supplies stock, BSCF v1 and BSCF v2+ policies. JoinSession's existing
InitialInfo trailer and the admitted PlayerLife now use the same profile and
encoder. The original /64 fuel units, /1024 mover tuning, flags and ties-to-even
rounding are preserved. Version 3/4 capabilities retain flight profile v2; their
Hardcore/Crumbling Run domains are separate work. Crumbling Run movement still
fails explicitly in the owned binding.

- Packs 66–68 use held jump; pack 69 uses hover. Source start delays, activation
  costs, refill/drain rates and post-damage delays come from the exported original
  table. Negotiated combat packs use their profile rates, grounded/wading recharge
  and idle gate. Exhaustion requires release; physical release clears deferred
  activity and the remaining thrust tail. The dedicated source jetpack-crate
  method fills fuel and sends Restock type 6, retaining the other pack state.
- Advertised ignition/exhaustion and physical thrust have separate timelines.
  Retail delays use the configured boundary, queued labels and current ENet RTT;
  ignition floors the extra network frames and exhaustion rounds upward. Native
  negotiated owners retain the source 2/3-frame defaults despite retail overrides.
  Those source timings are compatibility heuristics derived from captures, not
  recovered original-client resource logic or proof of an owner acknowledgement.
- Canopy deployment uses hover/jump press edges, descent, and minimum feet
  clearance over the centre and four hull corners. An early press remains armed;
  ground/water, an equipped jetpack, timeout and excessive upward velocity gate or
  close the canopy. One deployment is allowed per fall, with the source held-edge
  and landing re-arm behavior.
- Negotiated canopy physics applies immediately. Retail canopy changes enter an
  input-label schedule only after `ownerRowQueued` is called for a row actually
  queued to the owner, or after the original 30-step unsent fallback. RTT/backlog
  delay is bounded to 1–30 labels. This explicit callback does not send a row or
  stand in for the still-unported owner replication/reconciliation policy.
- The mover receives the concrete pack, passive Glide gravity, UGC hover,
  Engineer air acceleration, canopy gravity and v2 free-fall floor. Canopy landing
  protection follows actual braking: the source free-fall-equivalent speed damage
  can exceed the native canopy-reset result. Water/class/gravity rules and the
  existing short-air/own-blast scaling precede the accepted-health/death path.
  Disabling water HP loss does not erase the source raw canopy-impact result;
  its class water multiplier remains separate from the later health rule.
- Flight transitions and held equipment jump prevent unsafe backlog double-steps;
  the settled retail-idle shortcut excludes jetpacks. Spawn/death reset the same
  pack/canopy fields as the source, retaining negotiated policy and appropriate
  resource/damage clocks. Death closes physical flight and retains mover tuning;
  the detached corpse/fuse simulation is still separate work.

The real ENet peer's smoothed RTT is forwarded through JoinSession/ConfiguredScene
to the owned PlayerFlight; an absent measurement stays absent. Flight settings are
explicit typed owner inputs, as are the existing movement settings. Full host
configuration composition is not supplied by these components.

## Verification and limits

`generate_flight_golden.py` executes unchanged original Player flight/profile
methods, `SimulationRuntime._simulate_players`, the ClientData handler, and the
compiled native World/VXL mover. It reuses the infantry fixture's original-call
observer hooks without replacing a production method. Equipment, profile,
settings, clocks, RTT, world cells and owner-row notifications are controlled
inputs. Exact comparisons cover raw body and clipped façade state, input/pacing
and eye-history digests, fuel bits, activity/defer/tail clocks, complete canopy
schedules, deployment event reason/label, profile bytes, damage timestamps,
observer order, HP/death and jetpack Restock bytes.

The flight fixture contains **65,497 operations in 44 traces**, covering all four
packs, all three negotiated policy families,
sustained burn/exhaustion/release/recharge, accepted damage and crate refill,
water/early/late deployment, timeout/lift spill, equipment stacking, death/spawn,
owner notification and unsent fallback, label backlog/loss/reorder/starvation,
RTT and override bounds, and exact clearance thresholds. Source and fixture
hashes/counts are in `flight-provenance.json`.
Full-path rows are stored in seven deterministic gzip parts; their concatenated
uncompressed bytes retain the original complete-fixture SHA-256.
The pre-existing 16,683-operation
infantry fixture remains byte-identical after extracting its reusable generator.
The five focused original flight modules pass **117 tests**, zero skips/failures.
An additional **10,368 original canopy-impact vectors** compare all 18 class
profiles, three flight policies, gravity, descent speed, physical canopy state and
water/land. They retain the raw landing result even with water HP loss disabled.

An additional real UDP test negotiates BSCF v2, checks the InitialInfo profile,
admits a production life, and advances its owned fixed scheduler. It verifies no
flight progression on receive, real Engineer thrust/fuel/release, then a v2
parachute's real landing with full health and immediate close. Its trusted
equipment changes and inert mode/reload/lag/effect ports are explicit test inputs.
It does not test a supported game's client, complete class abilities or owner rows.

Bots, corpse/fuse updates, owner WorldUpdate eligibility/queueing/reconciliation,
complete lag compensation, deferred explosions, upstream rate/conduct admission,
reload/firing/hit authorization, full modes/awards and client/capacity/soak gates
remain pending. Packaged native probes still use FixtureScene. The full Python
baseline retains the documented failures/incomplete runs, including the existing
VIP discrepancy. CI success cannot establish complete gameplay readiness.

Independent CI for implementation `0f943c69d4d6634b2eaebacc5512adfaf0399aaa` and tested tree
`e1742fc548591395ce8e44735ad0f2e543c38bae` passed all five jobs in
[run 39068854866](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39068854866). It reproduces the full-path/impact fixtures and provenance
from the unchanged original source, verifies native probes and archive
reproducibility, and tests one uploaded JAR on three OSes. Local verification passes
4,565 Java tests (4,552 unit/component + 13 UDP), zero failures/errors/skips.
The gameplay and reference-baseline limits above remain in force.
