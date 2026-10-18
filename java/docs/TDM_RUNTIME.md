# Serial packet/event dispatch and concrete TDM

This batch connects four areas: a bounded tick-start packet queue, serial mode
and plugin dispatch, the original TDM rules/lifecycle, and canonical score rows
for accepted life damage/death. Ordinary hosting remains gated; the migration is
not playable or release-ready. Behavioral source is
`6216b771282d2d349ccd67ee58c378f035c83553`.

## Implemented behavior

`IngamePackets` snapshots the frame's packet budget before the first await and
releases every detached row's per-connection share immediately. New arrivals stay
queued for a later frame. Both global and per-connection caps drop only the excess
arrival; byte buffers and diagnostic snapshots are copied. The receiver's current
connection/player identity is checked before each row, including after a suspended
handler resumes. One ordinary packet exception does not discard the remaining
batch. Cancellation and fatal errors abort the frame instead of becoming ordinary
handler failures.

`OwnerSequence` resumes continuations on the explicit gameplay-owner executor and
processes already-completed stages iteratively. An 8,192-event immediate batch is
covered without recursive stack growth. `ModeEvents` awaits each mode handler,
then its plugin hook, before popping the next event. Their ordinary faults are
isolated separately. Handler changes to the live deque and active mode take effect
at the original boundaries. The initial budget and timeout remainder stay fixed;
late arrivals cannot prolong the expired round or change its winner. The final
`on_tick` runs after settlement, and its failure propagates to the outer frame.

`ApplicationHost.serialPackets` opts into the actual ENet-to-loader queue before
connections arrive. The caller invokes `drainPackets` at tick start and awaits it
before simulation; network service and the owner continuation executor can keep
running while a packet suspends. `JoinSession.receiveAsync`, `ConfiguredScene`
and `LifeRuntime.packetAsync` carry the completion stage through the actual
objective-drop handler. Existing synchronous scenes and explicit legacy objective
completion callbacks retain their original path. Session generation, roster
identity and scene replacement prevent stale FIFO tails or asynchronous faults
from affecting a replacement scene. Reentrant drains are rejected.

`TdmMode` resolves its score target from the TDM overlay or game rule, independently
of the generic CTF-era score limit. Clock precedence is per-mode time, lobby match
minutes, then mode-data default. Cross-team kills add configured team points,
then generic personal/reload/defend/distraction credit, native personal stingers at
volume 0.6, the lightweight team score bar, and the guarded score-limit result.
The native stinger bytes are constructed by the mode itself. No mid-game StateData
is broadcast. Start/reset ordering, late start cues, score/time wins, retirement,
deactivation and retained end-request cancellation are implemented. `sceneState`
binds current team bars and the actual mode target to the initial HUD.

`MatchLifeBinding` decorates the required life-effects services. It maintains one
score row for each current player object, captures reload state before death clears
it, forwards actual life counters and the newly earned revenge/payback pair, and
runs profile/combat contribution observers before KillAction. Death is queued before
an eligible enemy kill after the accepted death broadcast. Round counters reset
without replacing the connection's profile state. Respawn retains its canonical
row; slot reuse receives a new one. Existing damage validation remains authoritative. An earlier dead admission can be observed lazily after a later live spawn;
canonical enumeration now follows the actual runtime roster order rather than
cache insertion order. The added regression reproduced `[1, 0]` instead of the
source `[0, 1]` before the correction, and checks score-reset packet order and
retained row identities. This Java-only correction reuses unchanged source/native
oracle evidence; current full Java checks, reproducible archives and all four
native profiles are rerun.

## Evidence

The new generator calls the unchanged `BattleSpadesServer` queue/drain helpers,
`SimulationRuntime._tick_mode`, `TDMMode`, `BaseMode`, `Team`, `ServerConfig` and
native packet codecs. It records 6,208 observations: 2,048 packet operations,
2,048 mode/plugin operations, 2,048 TDM operations and 64 configuration cases.
Clocks, actor/configuration inputs, async handler/plugin callbacks and terminal
achievement/resource/vote/send IO are controlled; no source policy method is
replaced. Native music packets are excluded from the TDM wire comparison. Audio
service implementation is not claimed. The source stopping flag suppresses the
actual delayed ending/restart task; that task body is outside this batch.

Two real UDP compositions verify an owned shot/reload through accepted death,
canonical rows, serial death/kill events, personal/team score and the score-limit
winner; and a suspended objective request that holds the global packet FIFO,
resumes on the owner, skips a replaced player's tail and accepts the other peer's
input at the original frame label. The first composition also proves that HUD
limit 1 overrides a deliberately different generic configuration limit of 999.

Initial UDP checks encountered handshake traffic ahead of the intended packet
batch. Reliable ClientData and a reliable ClockSync barrier now settle that traffic
before the scenario; deadlines and assertions were not relaxed. The first full
regression check found that legacy objective callbacks had been routed through the
new async method. The original synchronous route was restored; the opt-in serial
path continues to await the actual future. Both observations are retained in local
logs. Current verification is recorded below and in CI_STATUS.md and verification-results.json.

Local Java check/build passes **5,971 component + 35 integration = 6,006 tests**,
zero failures, errors or skips. The batch adds 59 component tests and two actual UDP
compositions. **131 focused original TDM/runtime/scoreboard/lifecycle/end-sequence
regressions pass**. All 68 configured generators ran after fresh original five-module
native and ENet builds; all 244 frozen fixture/catalog/codegen hashes match.
The known flight provenance observation recurred: two impact metadata fields were
restored by rerunning the actual original generator's `--impact-only` command,
then all 244 hashes were compared again. No fixture or hash was waived.

Two CRC-valid local archives are byte-identical: 1769051 bytes,
SHA-256 `6823264954c6828247cdcb029aea3225a4c480984a7a5b6298398f368cbd2f7d`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four isolated native transport and
packaged-loader nominal/impaired profiles pass. Loader probes still use FixtureScene.
The full original baseline remains unverified: prior medpack/Zombie/VIP discrepancies
are retained and unwaived. These focused passes do not supersede those observations.

The concrete TDM core, canonical accepted-life score binding, serial mode/plugin
event drain and opt-in actual ENet packet queue are implemented. Complete departure
settlement, asynchronous simulation/bootstrap, delayed end/restart and actual
resource/audio/vote/achievement/result services, all other modes and supported-client/
capacity/soak gates remain pending. See [TDM_RUNTIME.md](TDM_RUNTIME.md).

This checkpoint requires a new five-job CI run after publication.


## Required integration and remaining work

The caller still provides actual resource rebuild, audio, voting, achievement,
result/persistence and delayed end/transition services. The end-request port must
retain and cancel its task before retiring or replacing the mode. Full end-screen,
map preflight, restart/recovery and their service bodies are not implemented here.
Source disconnect-time queued-hook settlement must run before disposing the life;
full departure/team-transition policy and roster ownership remain pending. Queued
old-round events must be purged by the transition owner, as in the original server.

The complete asynchronous simulation step, all other concrete modes, corpse and
projectile domains, placement/equipment policies, authenticated identity, complete
service/bootstrap assembly, supported-client validation and capacity/soak gates
remain pending. Native loader probes still use FixtureScene. The original medpack,
Zombie and VIP baseline discrepancies remain recorded and unwaived; the full
original-source baseline is not declared passing.
