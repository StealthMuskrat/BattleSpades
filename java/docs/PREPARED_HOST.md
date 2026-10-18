# Prepared maps and owned host transitions

Follow-up: [ROUND_MEDIA.md](ROUND_MEDIA.md) adds music/ambience, next-map ballot
lifecycle and actual ending/host presentation bindings. The text below records
the preceding prepared-map checkpoint and its then-pending service scope.

This checkpoint closes connected worker/owner/host binding gaps after
[TRANSITIONS.md](TRANSITIONS.md). Complete gameplay and normal hosting remain pending.

## Implementations

`WorldPreflight.prepare` runs bounded VXL/metadata parsing and the existing
SpawnGeometry prewarm on the explicit worker. `PreparedMap` retains only immutable
terrain, metadata, candidate lists and base anchors. The temporary worker world is
closed before publication. `bind` creates a fresh MutableWorld and SpawnGeometry on
the receiving owner; it transfers the frozen pools and anchors without repeating
nonempty candidate or anchor searches. Empty-pool recomputation preserves existing
source semantics. Selected candidates still undergo normal live-terrain validation.
The old `load` API remains available for terrain/metadata-only callers.

`SpawnGeometry.Prepared` requires exact underlying VxlMap identity and layout equality,
not merely CRC equality. MutableWorld checks zero revision and absence of mutable
column overlays, including load-derived lights that do not increment the revision.
Edited, retired, unrelated same-CRC worlds and changed layouts cannot import caches.
No worker-owned MutableWorld, collision adapter or geometry object crosses threads.

`OwnerLoop` provides a bounded concurrent submission queue, owner-only monotonic timers
and fixed host-driven pump budgets. Owner submissions are queued as well, so retained
admin requests defer execution. Timer completion runs on the gameplay owner; no timer
thread touches gameplay. Zero waits yield to a later pump. Timers retain their actual
cancellable futures, removing pending waits when RoundEndSequence retires. Ordinary
callback failures are reported; fatal errors propagate. Shutdown rejects new work,
cancels waits and leaves already queued continuations available for a final pump.
The host supplies relative monotonic nanoseconds and pumps while asynchronous frame,
preflight, ending and reload tasks are suspended.

`HostedSceneState` owns an explicit concrete-mode factory registry and the active
map/code/domain/scene/world/spawn bundle. Factory and metadata validation finish before
host publication. Only canonical registered factories can be installed; the mode
catalog alone never supplies gameplay. Actual ApplicationHost scene identity is checked
at attachment. Successful installation clears the prior fog override and advances the
world epoch; rollback restores the same retained scene, mode, world and fog override.
Foreign or already retired snapshots are rejected. New factories must clean up any
partial domain that they fail to return; returned invalid domains and prepared worlds
are disposed by the state owner.

Old resources stay live through new-mode start, bot rebind and every retained peer's
reload. After successful reload or completed rollback recovery, MatchTransitions calls
its optional resource-ownership completion hook. HostedSceneState then closes only
inactive domains/worlds, once each. Cleanup faults are surfaced after the committed
state and never attempt rollback into a disposed world. Fatal/cancelled transitions do
not invoke this completion hook; retained resources remain available for explicit
runtime shutdown. The caller closes ApplicationHost before closing HostedSceneState,
so loaders release their leases before the last worlds retire.

`HostedTransitionServices` implements the actual state snapshot/install/restore,
mode identity, team enumeration, shared queued leaves, event/packet discard, retained
sessions, reliable broadcast/flush and owner-loop sleeps for MatchTransitions. Peer
snapshots capture the old domain's TransitionRoster while replacement scene suppliers
resolve the current owned scene. Vote/bots, transient reset, repair, journal/HUD reset,
played-map history and in-place restart remain explicit required subsystem bindings.
No silent implementations or fake modes are registered in production.

## Verification and limits

The new generator executes unchanged WorldManager.load_map through asyncio.to_thread,
using actual compiled native VXL and sidecar parsing. Four controlled input maps cover
empty water fallback, authored spawn volumes, authored base anchors and mode-specific
Occupation spawn overrides. Its eight team caches match Java's worker-prepared and
owner-bound pools/anchors exactly. Existing geometry algorithms are not rewritten.
The 249 prior frozen paths retain their exact hashes. Original server/shared/aoslib/
protocol/modes/tests remain unchanged against source
`6216b771282d2d349ccd67ee58c378f035c83553`.

New boundaries cover immutable cache sharing across two fresh owner worlds, worker
ownership rejection, same-CRC identity rejection, layout changes, ordinary edits and
revision-zero light overlays, live revalidation, bounded deferred scheduling, timer
ordering/cancellation, shutdown/reentrancy/fatal behavior, actual host commit/rollback,
factory/metadata failures, fog restoration, increasing epochs and post-commit cleanup
failure. Fifteen distinct cases are added; no existing assertions or deadlines are
removed or relaxed.

The existing real UDP transition scenario now uses actual worker preflight, PreparedMap,
OwnerLoop, HostedSceneState and HostedTransitionServices. It retains one live TDM peer
and one loaded team-selection peer across different checksums, asserts ordered
23/52/114 and replacement StateData, no packet 110, no old PlayerLeft/disconnect, same
reserved slots/peer identities and two fresh lives. It checks the old world remains
live until reloads finish, then retires exactly once. The test domain still uses the
existing explicit gameplay/spawn proposal and terminal subsystem fixture ports; this
is not a supported-client or completed production bootstrap test.

The existing accepted-kill/TDM-ending/frame/restart composition now uses concrete
OwnerLoop sleeps for the frame yield, five-second ending delay and seven-second screen
dwell. It verifies the boundaries before releasing the controlled vote/bot services.
One additional cancellation case verifies that ending retirement removes the actual
pending timer and cannot later restart a replacement.

Local Java check/build passes **6,097 component + 36 integration = 6,133 tests**,
with zero failures, errors or skips across 96 suites. This batch adds 15 targeted
component cases and upgrades two existing compositions instead of duplicating them.
**200 focused original-source regressions pass**. The new actual-source generator
records four real worker loads and eight team caches, reproducing both outputs
byte-for-byte on a second run. All 249 previous frozen paths remain unchanged
(251 checked paths total). Prior unchanged 70-generator/native-build evidence is
reused locally; CI is configured to independently reproduce all 71 generators.

Two CRC-valid local archives are byte-identical: 1845795 bytes,
SHA-256 `ccbb032a76c5b70ced0064e8716daaf6d27645e39de8a61346bb93e3e1499955`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader probe still uses FixtureScene. Platform
component/integration tests run source-built classes and smoke the uploaded JAR;
no local-to-CI artifact hash identity or complete platform gameplay is claimed.
The prior medpack/Zombie/VIP original-baseline discrepancies remain unwaived.
These focused passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `9269d005397c982f4e5f981c146881cca76fae81`,
tested/published tree `fccc9e8406b303e10311ffc70512ab93c46989ab`, in
[run 39166316694](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39166316694). The native job independently reproduced all 71
configured original generators, including the actual worker-prewarmed map caches.
The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked the
uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

Remaining work includes production domain factories/full asynchronous hosting assembly,
concrete vote/audio/achievement/persistence/bot/reset services and their cancellation,
remaining gameplay/modes, supported-client multiplayer, baseline reconciliation and
capacity/soak. A stored fog override is restored by state ownership; delivering live
fog changes to a concrete domain remains part of that domain's service binding.
