# Common Character lifecycle

This checkpoint adds an owner-confined `PlayerLife`, `LifeInventory`, `KillFeed`
and `LifeRuntime` domain behind `ConfiguredScene.Gameplay`. It creates real
`PlayerPhysics` bodies bound to the committed world. It is a common lifecycle
component, not a complete playable mode or input/combat implementation.

## Implemented behavior

- Accepted selections commit atomically at respawn, before synchronous mode
  preparation. Class application clamps the existing block wallet; spawn grants
  the source class start using ties-to-even scaling.
- Each spawn creates a fresh mover with the selected class profile, zero velocity,
  idle controls and explicitly selected jetpack/parachute equipment. Cached aim
  remains separate from the native vertical-aim regularization. No pack is
  inferred from class membership.
- Gun wallets persist across tool switches. Spawn resets all source gun,
  oriented-item and deployable wallets and placement cadence. Ammo crates preserve
  magazines and cadence while topping reserves up. Launcher clip inference is
  used for crate accounting, not as a firing implementation.
- Initial admission grants two turrets and sends owner HP/restock notification.
  Round respawn runs the source restock path, bringing turret stock to four.
  Spectators have camera/roster state and cannot acquire a playable body.
- Death is idempotent, cancels reload/control, preserves native momentum and
  retains current HP for transition deaths. Common kill/death/streak, payback,
  multikill, domination and revenge bookkeeping match the source. Mode death-type
  policy runs before death; countdown policy observes the dead state.
- Respawn uses the wall clock captured once per scheduling pass. Fractional
  countdowns use the source ceiling tolerance; dead-join countdowns truncate.
  Mode gates and spectator guards apply. A failed respawn backs off one delay
  without preventing other players from respawning. Spawn protection uses the
  monotonic clock and supports cancellation and a shorter cap.
- Reliable roster catch-up distinguishes occupant identity, life generation and
  death. It retires reused IDs before announcing replacements, preserves source
  ledger order, hides unknown dead lives and sends quiet death replays once.
  Existing Java palette repair remains an additional, separately tested packet.
- A dead joiner who respawns while loading gets one owner Create/HP/restock repair
  at reveal. `ApplicationHost.sendPlayer` supplies bounded reliable owner control
  sends after admission, including loading. Other gameplay broadcasts retain the
  live-session gate. Shutdown suppresses outbound lifecycle messages while still
  running synchronous cleanup.

## Required integration ports and limits

`LifeRuntime.Policy` must supply verified identity context, concrete synchronous
mode policies, team colors/score and an already resolved, safe spawn point. The
existing `SpawnResolver` is available for that placement policy; this component
does not replace unported mode-specific spawn selection with an implicit default.

`Inputs` must own real ClientData chronology, accepted-frame simulation, action
dispatch and reset/forget handling. Raw packet arrival is never treated as one
simulation tick. `Effects` must implement attachment/corpse/replication cleanup,
queued mode notifications and external profile/combat/achievement observers.
Callbacks run on the owner thread; recipients must queue asynchronous mode work.
The caller drives `processRespawns` from its server loop. These are explicit
required ports; test harness implementations do not ship in the JAR.

Accepted damage/healing and contribution history are now added by the
[health checkpoint](PLAYER_HEALTH.md). The component does not implement hit validation, full flight fuel
and transition timelines, WorldUpdate replication, corpses/entities, source class
change cooldown/interaction gates, concrete modes, authentication or the complete
server loop. Validated staging APIs do not authorize raw packet 13. Main hosting
still exits 2. No supported game-client session, capacity or soak result is claimed.

## Independent comparisons

`generate_life_golden.py` executes the unmodified original Python Player,
RoundLifecycle, roster, kill_feed and deployable-inventory methods, plus compiled
original world/VXL/packet codecs. Controlled inputs are maps, clocks, mode
proposals/gates/failures and inert observer callbacks. The source server's create
broadcast callback records packet IDs only; it is not an oracle for Main's full
creation/score policy. No policy body is rewritten or substituted.

| Source comparison | Vectors |
|---|---:|
| Spawn and pre-spawn wallets, all 18 classes, six scales, seven equipment choices | 756 |
| Stateful inventory operations in 24 traces | 3624 |
| Kill-feed operations and relation observations | 4000 |
| Encoded and independently decoded life packets | 300 |
| Death/respawn scheduler transitions | 183 |
| Roster transitions, including numeric-ID reuse | 592 |

The 69-row production catalog contains source data only. CI rebuilds the original
codecs and requires exact regeneration of the catalog, fixtures and provenance.
Negative HP source coordinates are checked against the compiled source decoder:
the source fixed-point encoder is not an exact encode/decode round trip there.

The additional real-UDP test uses production lifecycle/body/session code with
explicit test mode/input/effect ports. It verifies first-life stock, loading-time
respawn, owner repair, quiet peer death catch-up and a fresh subsequent body.
It does not simulate a supported client's gameplay input stream. The packaged
native loader/transport probes retain their earlier fixture player domain.
