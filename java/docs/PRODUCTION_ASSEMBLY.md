# TDM/Arena production assembly and asynchronous host frames

Local Java check/build passes **6,263 component + 40 integration = 6,303 tests**, with
zero failures, errors or skips across 107 suites. All **350 focused original-source
regressions pass** on a fresh run. Seven new component checks cover three shared-stream
source spawn traces, admission/score/team/respawn ownership, TDM/Arena installation and
rollback, shutdown and reliable roster visibility. One four-peer real UDP check uses the
production Arena assembly and asynchronous frame pump. Existing policy traces remain
in use; an interior spawn trace that duplicated the dry-map result was removed before
publication. Earlier broad test reductions remain intact.

The new intact-source spawn generator reproduces both outputs byte-for-byte, with 18
positions and shared random continuations across three traces. All 262 preceding frozen
paths are unchanged: 264 checked paths and 77 configured CI generators. The preceding
76-generator/native-build evidence is reused locally; CI must rebuild/reproduce all 77.
Two CRC-valid local archives are byte-identical: 2014236 bytes, SHA-256
`fcfc54c4ef6e1cb064271c2d35dafad72a4826a8ce0b1a3a45b4cebd8e5e9f87`. Class major 69, packaging/licenses, version/config smokes and
all four fresh isolated native transport/packaged-loader profiles pass. The native loader
still uses FixtureScene. Normal hosting exits 2. No complete platform gameplay,
supported retail-client test or local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration is
incomplete. The existing medpack/Zombie/VIP original-baseline discrepancies remain
unwaived; these focused passes do not establish a passing full original-source baseline.

StandardMatchDomain now constructs real TDM or Arena with the committed PreparedMap
world and prewarmed spawn geometry, actual mode settings and metadata, canonical team
membership, LifeRuntime, MatchLifeBinding, Scoreboard, profile and accepted combat
tracking, ModeEvents and TransitionRoster. It binds HostedPlayerControls, HostedMedia,
early join audio and known-player delivery after the actual host exists. Failed admission
does not leave a team member, disconnect removes the canonical actor, respawn queues the
source spawn event, and stale player objects cannot obtain a replacement actor's peer.
SceneConfig keeps Arena's actual domain code despite its retail NOR metadata fallback.
Factories for unimplemented modes are rejected; no default gameplay domain is substituted.

HostedSceneState's HostBoundDomain attachment runs for the initial domain and for a
prepared candidate before host publication. Retained domains continue through existing
commit/rollback rules; only inactive worlds are disposed after settlement. A domain owns
its service cleanup, while scene state owns its world. Disposal releases active ballot
waiters and their timers without a HUD send after transport closure, rejects reopening
from a completion callback and continues other cleanup after an ordinary/fatal failure.
The caller must await mode deactivation before disposing a running domain.

StandardSpawns binds canonical live actor views and the common SourceRandom stream to
BaseMode smart selection, native WorldManager's plain spectator/team fallback and final
RoundLifecycle validation/rescue. Native spectator selection retains the exclusive upper
region bounds, 64 dry draws and deterministic y/x fallback. Death intel uses the accepted
canonical victim and monotonic clock. Existing geometry/threat-selection/rescue traces
remain authoritative; three new traces execute the intact source chain on dry, authored
water and fully wet maps, including subsequent random values. Clocks, input actors,
synthetic terrain/layouts and seeds are controlled; no source policy body is substituted.

JoinSession now records ids at reliable CreatePlayer queue acceptance independently of
its loading reconciliation plan and of snapshot ACK holds. PlayerLeft forgets an id at
acceptance; replay does not resurrect it when queued controls drain. Reload/replacement
and close clear visibility. ApplicationHost exposes known-player broadcasts and actual
scoreboard peers capturing session epoch and actor identity; retained old peers cannot
send to a reused slot. Palette relay uses the same visibility gate. Loading clients
still reconcile through the existing roster/terrain barriers; lifecycle ACK suppression
and reliable-delivery ordering remain unchanged.

HostedSimulation binds ApplicationHost's serial packet drain, current ModeEvents,
TerrainRepair and PeriodicServices plus LifeRuntime respawns into SimulationFrame and
AsyncFixedStepRuntime. Each host pump services UDP and bounded owner continuations even
while a frame is suspended. It retains elapsed time across awaits, uses the existing
five-step/two-second debt bounds, yields between steps, prevents overlap/reentry and
stops new frames before awaiting the current frame and deactivating the current mode.
Required other frame phases, replication, metrics and health are supplied by their real
owners. The assembly creates no empty implementations for those required domains.

The new UDP check uses StandardMatchDomain directly: automatic teams and prepared-map
spawns, an active Arena roster, worker-resumed player-phase suspension while another
UDP endpoint finishes loader negotiation, dead-arrival visibility and score filtering,
early music and first-input reveal, real next-round respawns that establish visibility,
ordinary departure and slot reuse, stale peer invalidation and awaited shutdown. It adds
coverage of production assembly boundaries; existing Arena policy/end-screen and reliable
snapshot-release UDP checks are retained rather than duplicated.

Production lifecycle/input effects, trusted admin identity, deployable/entity/corpse
work, plugin hooks, escape/reveal effects, resources, result persistence and the ending
transition service remain explicit required services. HostedSimulation likewise requires
all remaining simulation phases and actual replication. Bot AI/director, remaining modes,
complete gameplay/construction/projectile factories, auth/master bindings, supported-client
capacity/soak and full-source-baseline reconciliation remain pending. The ordinary launcher
is deliberately still gated. This checkpoint completes TDM/Arena assembly and frame-host
binding; it does not establish a complete playable server or migration completion.
