This is the preceding Arena policy checkpoint. [Production assembly](PRODUCTION_ASSEMBLY.md) is the current follow-up.

# Arena rounds, shared lifecycle and early join audio

Local Java check/build passes **6,256 component + 39 integration = 6,295 tests**,
with zero failures, errors or skips across 106 suites. **350 focused original-source
regressions pass**. The batch adds 14 stateful native Arena/lifecycle traces, two seeded
join-audio traces, one grouped settings check, one audio identity/fatal/owner boundary,
one admission-audio backpressure boundary and one four-peer real UDP composition.
Existing TDM/media source fixtures and tests remain in use after lifecycle extraction.

The new intact-source generator reproduces both outputs byte-for-byte; all 260 earlier
frozen paths retain their hashes, for 262 checked paths and 76 configured CI generators.
Unchanged prior 75-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 76. The broad earlier test-optimization reductions remain intact;
no duplicate full-suite or extra UDP setup is added for shared BaseRoundMode behavior.

Two CRC-valid local archives are byte-identical: 1975623 bytes,
SHA-256 `0ac9955af9fff6bc9f33111dfafb589fe393642baf3f26ab223da53f26ffeb59`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `03a94ffc6c9988fa92c29ad4e0b2fbaa9bb8d3a9`,
tested/published tree `66ed8b6cc3e0f7e11a4b4d4b8cf821007f5ec5dc`, in
[run 39177842753](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39177842753). The native job independently reproduced all 76
configured original generators, including the Arena/early-audio traces and all earlier
fixtures. The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked
the uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

ArenaMode implements the original elimination mode: typed mode overlay precedence,
round/clock clamps, no-body-creation match start, held warm-up until both sides have a
live spawned actor, one waiting notice, countdown restart and cadence, FIGHT roster
snapshot, current-identity alive counts, death/leave elimination, draws and alive-count
timeouts, round-win score bars, deterministic match target and delayed next round.
It disables damage before FIGHT and after round end, blocks timed respawns throughout
live rounds/intermission, advertises the bounded remaining respawn delay and protects
live frozen fighters from bot retirement. Next-round bodies use the ordinary LifeRuntime
respawn path for every playable actor, including previously alive actors; spectators
are skipped. Pending equipment is applied once to the actual canonical actor's new life.
A late or reused roster identity never extends the frozen fighter list.

BaseRoundMode extracts the existing source BaseMode lifecycle/clock/score/end/reveal
behavior from TdmMode. TDM keeps its public constructor/settings/effects surface,
team resets, start cue and kill-specific team score/stingers; its old source traces
remain unchanged. Arena uses the shared generic personal economy, accepted timeout
remainder, mode retirement, end guards, end-vote/scheduling and scoreboard reveal.
Achievement/resource/persistence/terminal ending services remain required ports.
Arena's registered code is preserved separately from its source retail NOR metadata
fallback; SceneConfig.from already makes this distinction. The explicit integration
constructor now passes that actual Arena code rather than treating the fallback's
wire metadata as the domain mode.

MusicService adds source-seeded personal track selection without changing broadcast
family state. BaseRoundMode selects ending tracks for final-minute or active end-screen
joiners, then JoinAudio falls back to the enabled gameplay bed. Ambience precedes music;
flush 23, StopMusic 27 and specific Music 26 retain the source order. Disabled beds
consume no random choice. JoinAudio has actor/scene identity guards, independent forget/
reset, no repeated successful reveal, ordinary failure available for explicit retry,
and fatal propagation. HostedMedia binds it once before admission, catches ordinary
metadata/listener failures and uses actual reliable host owner delivery while loading.

JoinScene/ConfiguredScene/LifeRuntime/JoinSession now carry the optional early-audio hook.
It runs only at the first-input or spectator-menu admission boundary, before air/terrain/
roster replay, and queues audio through the existing reliable direct path. Backpressure
finishes those controls before any catch-up effect; callback epoch/scene replacement
is checked and reload clears the per-admission marker. Existing roster/terrain leases,
replay budgets, spectator no-fabricated-input rules and session retry/deadline behavior
remain in place. HostedMedia's later reveal skips duplicate ambience when early audio
is bound. Other per-mode join-music overrides remain pending with their modes.

The new generator calls intact Arena/BaseMode, native Player deaths, actual
RoundLifecycle respawns and Server._broadcast_create_player, plus intact
Server._send_join_audio/BaseMode.join_music_track with the shared seeded random stream.
Only clocks, external spawn proposals, terminal achievement/resource/vote/cancellation
services and IO/failure boundaries are controlled. Its actor factory supplies roster
inputs; fixture-only admission advertisements are excluded from that setup comparison.
Every subsequent mode/life packet is compared exactly, together with round state,
fighter object incarnations, scoring, current bodies/classes, respawn gates/delays,
retirement safety and terminal effects. Real admission is exercised separately over UDP.

The four-peer composition uses actual ArenaMode, MatchLifeBinding/ModeEvents, LifeRuntime,
ConfiguredScene, HostedMedia and ApplicationHost. It checks warm-up damage veto, FIGHT,
accepted damage -> death/queued elimination -> round score, no intermission respawn,
staged class at the next ordinary respawn, an admitted dead late joiner, early music
before its exact terrain catch-up cell, the next round's new canonical lives, two-round
match completion, source death-before-final-kill score ordering, ending music and forced
scores for an end-screen joiner, one terminal schedule and retained peers/slots.
Terminal achievement/end-task services and spawn proposals are explicit test ports;
this is not a complete production domain factory or supported-client test.

Review caught and fixed a new draw-path Java conditional-unboxing fault before full
verification. Focused harness corrections covered dual-interface optional-hook defaults,
PlayerLife versus killer-ID API, source Player tool versus language constructor fields,
actual score projection at respawn, trace label normalization and Arena's explicit domain
code versus retail fallback. No production policy method, baseline failure, assertion,
platform or timing deadline was waived.

Full migration and normal hosting remain incomplete: production service/domain factories
and asynchronous bootstrap, remaining modes/gameplay and per-mode audio overrides,
bot director/AI, persistence/statistics/achievements/results, repair/journal/HUD/reset,
auth/master bindings, supported-client/capacity/soak and baseline reconciliation remain
pending. Source-verified Arena policy does not complete those required domains.
