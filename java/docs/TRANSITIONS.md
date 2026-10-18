# Map preflight, serialized transitions and retained scene peers

Follow-up: Worker spawn-cache transfer, concrete owned host state and owner-loop timers now
have [PREPARED_HOST.md](PREPARED_HOST.md) evidence. The text below records the preceding
transition-policy checkpoint and its then-pending bindings.

This checkpoint ports connected match-transition components from behavioral source
`6216b771282d2d349ccd67ee58c378f035c83553`. Complete hosting/bootstrap and the remaining
gameplay/services are still pending; normal hosting remains gated.

## Implementations

`WorldPreflight` parses an isolated immutable MapAssembly through an explicit worker
executor. It normalizes protocol map stems, resolves the configured map directory and
symlink target, confines reads to that directory, and uses the actual bounded VXL and
metadata codecs. Failed candidates never replace the live world. MutableWorld creation
and actual domain construction stay on the gameplay owner. The service currently
prepares terrain and metadata; transferring prewarmed spawn caches and the complete
bootstrap remain future work. No Python or native runtime is included in the JAR.

`MatchTransitions` serializes map, canonical-mode and same-round requests. Admin requests
retain a completion stage, defer work through the explicit queued gameplay executor,
reject a competing request, and report failed preflight through the supplied private
notification port. Same-map/alias-equivalent requests avoid a transition. Mode changes
load a fresh candidate for the current map. The catalog validates registered names;
the real mode factory must separately provide the implementation, and an unavailable
factory is rejected. Metadata alone never stands in for a playable mode.

Preflight finishes before the optional statistics overlay. A bundled stock screenshot
is required for packet 53; utility/custom maps keep the safe in-scene hold. On supported
maps, the service sends the original silent PlaySound(23), releases ForceShowScores(72),
opens ShowGameStats(53), then sends optional clamped ShowTextMessage(73), flushes and
awaits the bounded dwell. Packet construction and the screenshot list are source-backed.

`SceneRollover` snapshots live, loaded pregame and still-loading peers; announces the
new scene, resets the old vote, arms carried epochs, sends the audio flush and MapEnded
(52), and flushes before lowering every gameplay gate. Loaded team/class-selection
peers receive the freeze directly; peers still inside map loading are retired with
reason 18. The old mode's ending is cancelled, retirement is marked before bots/leaves
run, queued leaves settle, and actual lives detach before deactivation. A second vote
reset and timeline discard precede bounded grace, transient/repair/journal/HUD resets,
played-map history, candidate installation, team reset, new mode start and bot rebind.
All carried reloads start before any is awaited. A failed reload retires only that peer;
a successful scene change can request reconnect for the incompatible subset.

Ordinary rollover failures restore the old configuration/world/fog/mode through the
required state port, retire peers safely, then individually guard aborted-mode
retirement, restored-mode restart and bot rebind. Fatal errors/cancellation propagate
as in the source. Concrete state installation/restoration, bots, votes, journals,
HUD and remaining runtime services remain required bindings; this is not a completed
launcher or all-mode server.

`TransitionRoster` awaits the old mode's canonical leave actor before forgetting life
credit, team membership and the current global roster row. A transition-specific
LifeRuntime/MatchLifeBinding path avoids duplicate immediate/plugin leave dispatch and
emits no PlayerLeft into the retired scene. Queued leave settlement is implemented in
ModeEvents against the same shared deque: it snapshots only old leaves, awaits mode
then plugin, isolates one ordinary failure, and leaves newly appended events for the
later timeline discard. A failed mode hook skips that leave's plugin, matching source.

`SessionTransitions` adapts actual ApplicationHost/JoinSession peers. Arming invalidates
old suspended admissions; gameplay gating preserves the life until the awaited leave
finishes. Detach preserves the authenticated peer and reserved slot until loader reset.
Reload keeps backpressured freeze packets ahead of replacement InitialInfo(114), requires
MapDataValidation, and resolves its retained result at actual StateData transmission.
It does not require patched-client packet 110. Old packets cannot re-admit a life into
the retiring epoch. Shared SceneLifecycle supplies one strict default to scene/gameplay
interfaces, avoiding conflicting Java defaults; unbound detach fails explicitly.

An ending task may itself initiate restart/rollover. Explicit `FromEnding` transition
methods preserve that request while retiring its old mode, reproducing Python's
current-task cancellation exclusion. Admin transitions still cancel and await an old
ending. The actual TdmMode lifecycle implements both cases; no thread-local ownership
heuristic is used.

## Evidence and limits

The new original-source oracle records 45 observations: 24 direct rollover/recovery
scenarios, eight full map-screen paths using real worker WorldManager/VXL loading, and
13 protocol-name cases. It calls intact normalization, preflight, rollover, queued-leave,
detach, journal/vote reset and rollback helpers. Actual Team and native packet bytes are
used. Virtual modes/peers, clocks and terminal overlay/HUD IO are controlled; no source
policy method is replaced. The Java source comparisons preserve ordered traces, including
empty rosters, queued-leave faults, partial detach, failed start/rebind, failed reloads,
restored-mode failures and missing custom-map screenshots.

Boundary tests cover worker resumption, no terminal UI before successful preflight,
ownership release on ordinary/cancel/fatal preflight failure, deferred retained admin
requests, canonical aliases versus unavailable factories, concurrent reload start,
awaited shared-deque leaves, actual life/team/score cleanup and ending-task self-restart.
The real-filesystem test exercises symlink confinement on POSIX hosts; Windows still
runs its lexical confinement and actual parsing checks without requiring symlink-creation
privileges. Loader tests cover team selection, stale admission, reliable backpressure
and disconnect completion.

The new real UDP composition connects one actual live TDM player and one loaded
team-selection peer, gates/detaches the old life, retains both ENet peers, sends ordered
23/52/114, synchronizes a replacement map with a different checksum, and admits two
new lives. It verifies no old-scene PlayerLeft, no disconnect, reserved slots and peer
identity, without packet 110 or relaxed deadlines. This is a production-component
composition with explicit service ports, not a supported retail-client validation.

Initial focused compilation caught Java's duplicate interface defaults and a fixture
constructor escape warning. A common strict lifecycle interface and a final fixture
resolved both. A source-fixture syntax/setup error was corrected before generation.
Review also moved reload completion to StateData transmission: a client selecting a
team immediately must not bypass a SELECT-only completion and hang the transition.
No existing test, deadline or source/hash assertion was removed or relaxed.

Local Java check/build passes **6,082 component + 36 integration = 6,118 tests**,
with zero failures, errors or skips across 93 suites. This batch adds 45 component
cases (33 source-comparison cases, nine boundaries and three loader cases) and one
real UDP composition. **175 focused original-source regressions pass**. The new
generator reproduces its three outputs byte-for-byte on a second run; all 246 prior
frozen paths remain unchanged (249 checked paths total). Prior unchanged 69-generator
and native-build evidence is reused locally; CI is configured to reproduce all 70.

Two CRC-valid local archives are byte-identical: 1826295 bytes,
SHA-256 `0912f829799888ddc65da57aa59e911bfed6f1536aacd19bbecd280cc4526160`. Java class major 69, packaging/licenses and version/config smokes
pass; normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. Loader probes use FixtureScene. Platform tests use
source-built classes; the platform JAR smokes do not establish complete gameplay.
Prior medpack/Zombie/VIP baseline discrepancies remain unwaived; these focused source
passes do not establish a passing full original baseline.

All five CI jobs passed for implementation `8a5885dea85a23c6ccd5cbea1b73787ee5f32d66`,
tested/published tree `1b2b759ace264cc0921a1382a96d108629ec25b1`, in
[run 39164290723](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39164290723). The native job independently reproduced all 70
configured original generators, including transition traces and their real worker
WorldManager loading. The build byte-compared repeated archives. Ubuntu, Windows
and macOS smoked the uploaded JAR and ran source-built component/integration tests.
Every final job step completed with success or an intentional skip. No local-to-CI
artifact hash identity or complete platform gameplay is claimed.

Remaining work includes complete owned bootstrap/state bindings, spawn-cache preparation,
concrete vote/audio/achievement/persistence/bot services, remaining modes and gameplay,
supported-client multiplayer, complete original-baseline reconciliation and capacity/soak.
