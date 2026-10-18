This is the preceding gameplay-services checkpoint. [Arena and early audio](ARENA_ROUNDS.md) is the current follow-up.

# Team balancing, gameplay sounds and periodic services

Local Java check/build passes **6,237 component + 38 integration = 6,275 tests**,
with zero failures, errors or skips across 104 suites. **340 focused original-source
regressions pass**. The batch adds 24 stateful original TeamBalancer/forced-team-change
traces, one grouped gameplay-audio trace and four async/failure boundaries. The existing
three-peer UDP controls composition now also exercises actual HostedGameplayServices:
forced move, retail notice, same-frame respawn on the new team, team-relative cues,
spectator exclusion, unreliable personal/positioned cues and centred hearing stimuli.
No new UDP test duplicates that existing setup.

The new intact-source generator reproduces both outputs byte-for-byte; all 258 earlier
frozen paths retain their hashes, for 260 checked paths and 75 configured CI generators.
Unchanged prior 74-generator/native-build evidence is reused locally; CI independently
rebuilds/reproduces all 75.

Two CRC-valid local archives are byte-identical: 1959105 bytes,
SHA-256 `33830806f66e60a95580d39c9cb2a2eb43ac1af8b2301ad77a395b4293ceee3c`. Class major 69, packaging/licenses, version/config smokes
and all four current isolated native transport/packaged-loader nominal/impaired profiles
pass. Normal hosting exits 2; the native loader uses FixtureScene. No supported retail
client, complete platform gameplay or local-to-CI artifact hash identity is claimed.
The medpack/Zombie/VIP original-baseline discrepancies remain unwaived. These focused
passes do not establish a passing full original-source baseline.

All five CI jobs passed for implementation `9dd88dd9c7dc6bef6e66911d24665077c4980617`,
tested/published tree `bd29520ff81cb0da9116820738dd84bc29645cf6`, in
[run 39175987373](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39175987373). The native job independently reproduced all 75
configured original generators, including the balancing/audio traces and all earlier
fixtures. The build byte-compared repeated archives. Ubuntu, Windows and macOS smoked
the uploaded JAR and ran source-built component/integration tests. Every final job step
completed with success or an intentional skip. No local-to-CI artifact hash identity
or complete platform gameplay is claimed.

TeamBalancer preserves the source grace/check slack, all-roster counts (including
loading clients), bot-first moves, ascending dead-bot ID, awaited safe retirement and
replacement, bot-wait preference, dead-human-only eligibility, carrier/VIP/patient-zero/
last-survivor protection and mode/transition boundaries. Human priority is captures,
score, newest observed actor and descending ID. The moved timestamp lives on the actual
PlayerLife and survives replacement of the balancer; slot reuse cannot inherit it.
Manual balance bypasses config enable/grace/wait/normal cooldown, keeps protections,
and caps at 64 moves. Source future timestamps still block manual moves. TeamChanges
remains the sole forced move path; no duplicate team/death implementation is added.
Private TEAM_FULL/49 notices are independently best effort. Replacement failure retains
the completed retirement; cancellation/fatal errors remain failures. Awaited callbacks
resume through the actual owner loop; overlapping balance passes are rejected.

GameplaySounds implements 23 one-shots, 25 loop teardown, settled team delivery,
ID-based actor exclusion, reliable stingers, optional unreliable cosmetic delivery,
team-relative good/bad cues, exact lunar flyby selection and strict z > water-plane
explosion variants. Broadcast precedes optional BLOCK_DESTROYED hearing at voxel centre,
source ID/team and lifetime 1. Personal sends preserve the original peerless send port;
the actual host port makes peerless bot sends a no-op. Team sends snapshot the roster,
exclude loading clients and peerless bots, and include dead settled humans. StopSound
can still reach an admitted loading owner through the existing reliable direct queue.
The small ApplicationHost delivery overload uses the live-scene gate for unreliable
personal cues and retains the reliable direct queue.

PeriodicServices binds the source 1 Hz service order immediately before respawns:
team balance (awaited), bot skill, anticheat report, then respawns. Ordinary failures
are isolated separately; cancellation/fatal errors abort. Non-second frames still run
respawns. HostedGameplayServices connects actual LifeRuntime, TeamChanges, ApplicationHost
reliable/unreliable delivery and OwnerLoop, with required mode/objective/score/bot/hearing/
skill/report ports. It is ready for the production factory; no placeholder production
bot director, scorer, objective policy or reporting service is introduced.

The source generator calls intact TeamBalancer, forced team handler, native Player/Team
and audio functions; only external domain state, clocks, IO and awaited bot lifecycle
are controlled. Every balancing action compares actors/teams/alive state, actor-owned
cooldowns, observed identity/arrival times, counters, cleanup/event order and exact
private packets. Audio compares exact native bytes, routing/reliability and hearing
order. Periodic service order/failure behavior is checked against the original source
regressions and asynchronous Java boundaries; its policy method is not substituted in
the new generator. Early oracle harness fixes filtered only team-change events from the
shared event queue and typed numeric timestamps; the retirement boundary was corrected
to use a live bot so it actually waits for retirement. No production policy, assertion,
platform or deadline was waived.

Full migration and normal hosting remain incomplete: production service/domain factories
and asynchronous bootstrap, remaining modes/gameplay, bot director and AI, persistence/
statistics/achievements/results, repair/journal/HUD/reset and auth/master bindings,
mode-specific late-join music, supported-client/capacity/soak and baseline reconciliation
remain pending. Existing explicit ports are not completed implementations of those domains.
