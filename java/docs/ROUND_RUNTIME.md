# Departure settlement and asynchronous round orchestration

This checkpoint implements five connected runtime components against behavioral
source `6216b771282d2d349ccd67ee58c378f035c83553`. Ordinary hosting remains gated;
the complete migration is not playable or release-ready.

`ModeEvents.leave` reconciles the same shared event deque before disconnect removes
the player. It drops that player's pending spawn/team-change acquisitions, extracts
death/kill events involving the exact departing object among their first two arguments,
and runs their mode/plugin prefixes in queue order before leave. Settlement captures
the original mode; the leave hook re-reads the current mode after those prefixes.
Equal-valued replacement actors do not match identity. Suspended hooks continue as
retained independent stages and resume reporting/cleanup on the gameplay owner.
Ordinary failures are isolated; cancellation/fatal failures remain observable through
`departures()`. `MatchLifeBinding.forget` invokes this while the accepted life is still
in the actual roster, before delegated cleanup and PlayerLeft. Admission rollback
without a current life does not acquire a spurious leave hook.

`AsyncFixedStepRuntime` awaits each 60 Hz frame, publishes replication after it, then
awaits an explicit cooperative yield before the next catch-up step. A batch runs at
most five steps while retaining up to two seconds of debt. Ordinary frame/snapshot
faults are isolated independently. Stop and cancellation prevent another frame;
reentrant advances are rejected while a batch is suspended. The existing synchronous
runtime remains available for its existing callers.

`SimulationFrame` enforces packet drain, bot update/action, projectiles, players,
terrain commits, prefab/repair/discovery, mode/plugin, corpses, respawns, entities,
turrets/fire/goo, periodic services, conduct, achievements and final metrics/health
order. All services are required ports; optional services are queried at their source
boundary. The bot-presence decision survives the awaited update. Async phases resume
on the owner before the next phase; ordinary failures cannot skip later phases.
Measured phases record time even when cancelled or synchronously fatal. The underlying
player simulation, periodic policy, metric aggregation/rate-limited logs and each
remaining subsystem still require their concrete bindings.

`RoundEndSequence` runs ending audio, optional forced scores, bot sign-off, the
five-second pause, final statistics, an awaited ballot, then map selection and bounded
0–120-second dwell. Empty or Unicode-casefold-equal map results choose an in-place
restart. A modern transition owns preflight/overlay/dwell; the legacy facade sleeps
before requesting change. A failed preflight without reconnect falls back to the
transition's restart. Other failures clear the mode's running flag and attempt in-place
recovery only while the current ended mode owns the round and no competing transition
is busy. Failed recovery releases forced scores as a last resort. Native map loading,
preflight/epoch transition, vote, audio and timer implementations remain required ports.
`TdmMode.endSequenceFailed` binds the failure flag at the original boundary.

`RoundRestart` releases scores, awaits bot preparation, resets transient runtime and
teams, awaits mode start, publishes team bars, respawns a roster snapshot of connected
playing teams through the normal life path, and awaits bot reset. A single respawn
fault does not skip other players; spectators and disconnected actors are excluded.
Both ending and restart invalidate their retained continuations on explicit cancellation.
Ending additionally awaits the required nested-service cancellation port before another
request can start. Late completion of a retired preparation/timer cannot restart a
replacement. Service adapters must cancel their own nested work and keep authoritative
mutations on the owner.

## Verification and limits

The new generator invokes the intact original `_run_player_leave_hooks`,
`_drive_hook_now`, `_end_sequence_task`, `_recover_failed_end_sequence`, `_restart_round`
and configuration predicates. It records 16 departure cases and 40 ending/restart
cases, including mode replacement, retained hooks, ordinary faults, same-map Unicode,
empty selection, dwell bounds, modern/legacy transition failure and recovery guards.
Actors, virtual mode-start/handler hooks, asynchronous vote/transition/timer services
and terminal audio/score IO are controlled. No source policy method is replaced.
Prefix order is strict; independent post-await departure suffixes are sorted for
comparison. The fixture does not claim native end-screen wire validation or implement
the transition service itself.

The production-component composition drives an accepted kill through IngamePackets,
SimulationFrame, ModeEvents and TdmMode, then through gated ending/vote/dwell/bot
preparation and RoundRestart using actual LifeRuntime respawns and canonical scoreboard
rows. It checks retained row identity, personal/team reset, alive bodies and forced-score
release. Existing 35 integration cases, including actual UDP compositions, are retained;
this batch adds no new supported-client or UDP scenario. Focused boundary tests cover
worker completion, frame order/fault isolation, fixed debt/cadence/yields, stop,
cancellation, fatal timing and nested retirement.

The initial focused test harness used the scoreboard-only capture for PlayerLeft,
then called the throwing `player` lookup to assert removal. Assertions now inspect the
actual LifeRuntime wire and roster, including scoring at PlayerLeft publication.
A composition log initially shadowed an inherited fixture field; it now uses a distinct
journal. No production assertion, deadline or prior test was removed or relaxed.

Local Java check/build passes **6,037 component + 35 integration = 6,072 tests**,
with zero failures, errors or skips across 91 suites. This batch adds 66 meaningful
component cases: 56 original-source comparisons and 10 orchestration boundaries,
including one composition through the actual packet FIFO, TDM, life and score services.
**152 focused original-source regressions pass**. The new source generator reproduces
both outputs byte-for-byte on a second run; all 244 prior frozen paths remain
unchanged (246 checked paths total). The unchanged prior 68-generator/native-build
evidence is reused locally; CI is configured to reproduce all 69 generators.

Two CRC-valid local archives are byte-identical: 1794398 bytes,
SHA-256 `a880beada65ae085eff99a9ace1d8584900b9c0d80b7718087d2077c833a68b7`. Java class major 69, packaging/licenses and version/config smokes
pass. All four isolated native transport/packaged-loader nominal/impaired profiles
pass for the current JAR. Loader probes still use FixtureScene, not a supported game
client. Normal hosting exits 2. Prior medpack/Zombie/VIP baseline discrepancies remain
unwaived; these focused passes do not establish a passing full original baseline.

All five CI jobs passed for implementation `4a4bf9e6742917d14ccbd841d9b35633dc69c2a3`,
tested/published tree `6b0a25cc445c03781cb4a837f3c567a1e37f5494`, in
[run 39161548480](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39161548480). The native job independently reproduced all 69
configured original generators, including the new departure/ending fixture. The build
byte-compared repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR
and ran source-built component/integration tests. Every final job step completed
with success or an intentional skip. No local-to-CI artifact hash identity is claimed.

Remaining work includes the concrete map transition/preflight service, complete
async launcher/bootstrap and subsystem bindings, team/roster departure policies,
remaining gameplay/modes/bots/plugins/services, supported-client multiplayer,
identical-artifact platform gameplay and capacity/soak gates.
