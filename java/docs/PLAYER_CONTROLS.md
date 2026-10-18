This is the preceding player-controls checkpoint. [Gameplay services](GAMEPLAY_SERVICES.md) is the current follow-up.

# Live team, class, palette and spectator controls

This connected checkpoint extends [VOTING_POLICY.md](VOTING_POLICY.md) with live menu
controls and the spectator loading boundary. Complete production gameplay/bootstrap and
normal hosting remain pending.

## Implementations

`TeamChanges` validates current canonical actors and native teams 0/2/3, enforces the
spectator rule, mode veto/lock presentation, five-second per-actor cooldown and existing
join-balance rule over all roster entries. Forced server moves bypass cooldown/balance
but preserve spectator/mode restrictions. Leaving spectator is exempt from the cooldown.
Private localized notices and optional command explanations match the source. Mode lock
presentation failures are advisory; authorization/cleanup failures retain their ordinary
caller error boundary. Team-bound deployables retire before allegiance changes. A recent
enemy's accepted kill is credited while the victim still has its old team; remaining live
bodies retire as a team-change death after the move. Actual team rosters move the same
actor, spectator CreatePlayer replaces its roster representation and clears death time,
and leaving spectator arms an ordinary delayed respawn. The mode event is queued last.
Mid-match TeamBalancer selection, bots and command registration remain separate work.

`SelectionChanges` handles packets 13/78 as complete selections. The required normalizer
and class/mode authorization ports retain stock rule/mode checks and map/UGC extension
ownership. Packet 78 reuses a matching staged selection, so either packet order preserves
equipment choices. A loadout identical to the active selection is ignored; same-class
changed equipment still requires a fresh life. The packet's instant flag cannot grant
live equipment in an ordinary match. Valid choices stage atomically, and a live character
ends through existing transition kill-credit logic. A three-second live-death throttle
stages later choices and sends the private next-respawn notice. Dead selections remain
unthrottled. The explicit Map Creator same-builder exception applies and acknowledges a
normalized selection while clearing pending state; this port does not implement the full
editor, map prefab catalog or Hardcore domain. Source traces cover the exception with a
controlled allow-list, without replacing normalization/handler/life methods.

`PaletteRelay` commits every authorized live/spawned palette choice, updates the canonical
roster color and relays reliably to other peers. The acting client's predicted color is
not echoed. Any held tool is accepted, matching the source. At most one trailing owner-
loop timer per actor publishes the newest color after the 100 ms interval. Departure,
scene retirement and explicit palette reset cancel retained timers; callbacks check the
canonical actor and live body, so ID reuse cannot recolor a replacement. Per-actor team
and class cooldown maps are forgotten with the actor. Fatal errors are not swallowed.

`PlayerControls` routes actual authenticated actors through these services. Packet-supplied
player IDs do not replace canonical identity. A joined spectator's packet 15 routes through
the ordinary team-change policy; it cannot rename the actor or override a previously
staged packet-13 class. Other duplicate join requests are consumed without another life.
LifeRuntime supports a single control binding before admission and retires it on ordinary
or transition departure. Mutable team/color writes remain owner-guarded package operations.

`HostedPlayerControls` binds real ApplicationHost reliable owner/broadcast/excluded sends,
actual TeamState rosters and ModeEvents. The concrete mode must provide authorization,
normalization/map extensions, deployable retirement and its event-actor representation.
The bounded event queue retains source drop/count behavior on overflow. This binding
supplies no empty production domain or authorization fallback.

JoinSession now tracks packet-110 menu state. A settled admitted spectator enters the
same terrain, roster, reveal and reliable-backpressure barrier as a first-input human,
without fabricating ClientData. Incomplete settlement retries at 50 ms with at most 2,000
retries. Ordinary reveal failure leaves the connection available for an explicit retry;
fatal errors propagate. Existing lease/overflow rules remain intact. An armed scene
transition suspends old work; replacement/reset clears the pending spectator admission.
Joined packet 15 reaches the current domain's asynchronous dispatch. Normal human
first-input loading still uses the shared barrier. General join greeting and full reveal/
mode/music/bootstrap services remain required separate bindings.

## Verification and limits

The generator invokes intact server.handlers.team/equipment, Player, Team, class selection,
connection join balance, roster and native packet methods with a native World/VXL. Only
external mode proposals, clocks, IO and deployable cleanup are controlled. Its 24 stateful
traces compare each action's team/life/death time/color/active+pending selection, kill/death
counters, team membership, terminal packet bytes and cleanup/event order. They cover
voluntary/forced moves, spectator exit/rejoin, mode lock/refusal, balance, cooldowns,
old-team kill credit, both class packet orders, unchanged/invalid/disabled choices,
post-respawn throttle, prefab normalization, UGC selection, palette bursts, dead and
removed actors. No policy method is substituted.

The focused palette boundary checks actual timer cancellation, owner-thread guards and
replacement actor identity after slot reuse. Four existing-session extensions check
spectator backpressure and one-time reveal without input, menu changes during retries,
ordinary reveal failure/explicit retry, scene arming/replacement and lease retirement.
Existing human loading/epoch/ACK barriers and all existing tests remain unchanged.

The new three-peer real UDP composition binds HostedPlayerControls to actual LifeRuntime,
TeamState and ModeEvents. A spectator sends packet 110, becomes live without a ClientData,
and receives reliable messages. Spoofed player IDs cannot redirect palette/class/team
operations. Observers receive the first and newest throttled color while the actor sees
no echo. Packets 13/78 retire one life, apply at real respawn and preserve the canonical
actor; a rapid later choice stages without another death. Team-to-spectator publishes the
native roster row; packet 13 plus packet 15 later joins a team with the staged class and
unchanged name. Slots/peers stay live. The gameplay/mode service fixture remains explicit
test support; this is not supported-retail-client or full-production-bootstrap evidence.

Initial focused verification caught a duplicate existing Optional accessor, a controlled
source IO callback that omitted the positional reliability argument, and a test ClientData
ID outside its seven-bit wire range. These were corrected before final verification.
Review also aligned host event-queue overflow with source drop/count policy. No source
baseline, deadline, assertion or platform case is removed, relaxed or waived.

Local Java check/build passes **6,208 component + 38 integration = 6,246 tests**,
with zero failures, errors or skips across 102 suites. This batch adds 24 intact-source
team/class/palette traces, one focused timer/identity boundary, four spectator session
boundaries and one real UDP composition. **317 focused original-source regressions pass**.
The new generator reproduces both outputs byte-for-byte; all 256 earlier frozen paths
retain their exact hashes, for 258 checked paths and 74 configured CI generators.
Unchanged prior 73-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 74.

Two CRC-valid local archives are byte-identical: 1930149 bytes,
SHA-256 `37e41873cf88ada4444234fc5f51fc357b183696c248b130b5acd40a59538815`. Class major 69, packaging/licenses and version/config
smokes pass; normal hosting exits 2. All four current isolated native transport/packaged-
loader nominal/impaired profiles pass. The loader still uses FixtureScene. Platform
tests run source-built component/integration classes and smoke the uploaded JAR. No
local-to-CI artifact hash identity or complete platform gameplay is claimed. Prior
medpack/Zombie/VIP original-baseline discrepancies remain unwaived; these focused passes
do not establish a passing full original-source baseline.

Review of the first published player-control checkpoint found a loading race: a
spectator may select a playable team before packet 110 or an admission retry. Its last
published CreatePlayer still describes team 0 until respawn. JoinScene/ConfiguredScene
now require the current canonical domain spectator predicate, implemented by LifeRuntime,
instead of inferring it from that old packet. A dedicated session regression retains the
old spectator roster row while changing the current actor's team and verifies that packet
110 cannot bypass normal first-input admission. The unused roster predicate is removed.
The correction has a fresh full suite/artifact/native check and a successful five-job CI run.

All five CI jobs passed for implementation `f9964fe5185dcf36a353411aac9e85971fdf06e5`,
tested/published tree `c7ebef88711a7fd12902a3c607c7c350bf7d56af`, in
[run 39174391954](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39174391954). The native job independently reproduced all 74
configured original generators, including live team/class/palette traces and all earlier
fixtures. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and ran source-built
component/integration tests. Every final job step completed with success or an intentional
skip. No local-to-CI artifact hash identity or complete platform gameplay is claimed.

Remaining work includes concrete production domain factories/full asynchronous hosting,
TeamBalancer/bots, remaining gameplay/modes and construction/projectiles, general sound/
join music, full reset/repair/journal/HUD/statistics/achievement/persistence/authentication,
supported-client multiplayer, baseline reconciliation and capacity/soak.
