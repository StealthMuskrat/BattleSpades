This is the preceding player-runtime checkpoint. [Hosted entities and resources](ENTITY_RUNTIME.md) is the current follow-up.

# Production factories and connected-player runtime

Local Java check/build passes **6,266 component + 41 integration = 6,307 tests**,
with zero failures, errors or skips across 108 suites. **353 focused original-source
regressions pass** freshly: the preceding 350 plus three configuration integration and
clamp tests. This batch adds three grouped component boundaries and one real UDP test
for the production factory/player runtime. Existing movement, replication, repair,
team-balance, profile/combat and scene-transition policy suites remain in use.

All 264 frozen paths are unchanged. The existing 77-generator/native-build evidence is
reused locally; CI independently rebuilds and reproduces all 77. No new generator,
production dependency or duplicate policy matrix is introduced. Two CRC-valid local
archives are byte-identical: 2036880 bytes, SHA-256
`f7fcea3f608af9d877c84c5201eb6f58933a47cf9b955c98965f3f8b254244ee`. Class major 69, packaging/licenses, version/config smokes and
all four fresh native transport/packaged-loader profiles pass. The native loader uses
FixtureScene; normal hosting exits 2. No supported retail-client test, complete platform
gameplay or local-to-CI artifact hash identity is claimed.

All five CI jobs passed for implementation `f96bbca34a9880450ecc27e81a444a8ba9d35977`, tested/published tree
`a781f34b3178525f352f01399a1ff8897fb71788`, in [run 39183158502](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39183158502). The native job independently rebuilt
the original oracles and reproduced all 77 configured generators. The build byte-compared
repeated application archives. Ubuntu, Windows and macOS smoked the uploaded JAR and
passed source-built component/integration tests. Every final step completed successfully
or was intentionally skipped. No local-to-CI artifact hash identity or complete platform
gameplay is claimed. Full migration and
ordinary hosting remain incomplete. A truncated intermediate repeat archive was rejected;
a subsequent rebuild passed CRC and byte-equality checks without source/assertion changes. The original medpack/Zombie/VIP baseline discrepancies
remain unwaived; these focused passes do not establish a passing full original baseline.

HostedSceneState.Factory receives the exact MapMetadata from its immutable worker-prepared
candidate together with its owner-bound world/spawn geometry. StandardMatchFactory registers
TDM and Arena, copies the selected map/mode into SceneConfig while preserving server rules
and admission settings, validates mode/CRC before allocating ports, and creates the real
StandardMatchDomain and its HostedPlayerRuntime. Candidate construction failures close
allocated external services; scene state retains ownership of the prepared world. The
factory performs no map/sidecar reads or checksum-indexed metadata lookup on the owner.
Tests prepare two identical-checksum maps with different gravity on a worker, delete both
maps and sidecars, install them and roll back with their original immutable metadata.

HostedPlayerRuntime binds InfantrySimulation to canonical lives, actual pickup burden,
required housekeeping/report/lag/physics observation ports and actual host disconnects.
After each eligible outer player tick it invokes ProfileTracker, CombatEvidence and the
bot observation port in source order. InfantrySimulation now includes trusted bots without
network sessions while retaining the settled-scene gate for human players, matching the
original SimulationRuntime gate. No bot AI/director or bot identity allocator is supplied.
Input/action authorization, effects and bot observation implementations remain required.

The runtime owns ReplicationService using live PlayerSnapshot adapters, real host links,
required effect/tool/corpse presentation and entity/turret sources, and actual delivery
metrics. Adapters are retained only for the same actor identity and replaced when a slot
is reused. LifeRuntime binds that replication service before admission, so death/respawn/
disconnect paths reset its existing cadence state. Owner anchors still require accepted
snapshot delivery; the existing reliable lifecycle/ACK hold policy is unchanged.

TerrainRepair runs on the committed world with source configuration bounds and current
settled human recipients. Rejected controls count as failed sends. Accepted terrain/collapse
committers still submit repair records explicitly; no competing world observer or synthetic
mutation stream is installed. HostedGameplayServices now receives actual TDM/Arena mode
status, score rows, team controls and Arena fighter retirement protection. Its bot director,
hearing, skill and anticheat domains remain required ports. Ordinary TDM/Arena have no VIP,
patient-zero or last-survivor exclusions. Periodic services use the same monotonic clock as
player life, team balance and replication rather than assuming the scheduler shares its
origin. Source settings clamp gap filling, repair limits and balance intervals; nonfinite
settings are rejected before attachment.

HostedSimulation's factory-backed constructor selects the current domain's player phase,
repair, periodic services and replication after each scene change. Each completed 60 Hz
frame replicates using that exact frame number. The existing host/owner pumping, bounded
catch-up debt, serial input queue, asynchronous phase order, fault handling and stop contract
remain intact. Required remaining frame phases and metrics/health implementations are
still supplied by their actual owners. The prior explicit component-binding constructor
remains available for those independently verified boundaries.

The new two-peer UDP check uses StandardMatchFactory and the factory-backed HostedSimulation
constructor for actual labelled movement, profile ticks, WorldUpdate delivery and owner
anchors, canonical terrain repair, 1 Hz clock-origin consistency and same-checksum map
rollover with a different gravity. Peers/slots are retained, the retired runtime is closed,
new canonical bodies consume their own labels, and the replacement repair queue starts
empty. Grouped component checks cover worker metadata/deleted sources/rollback, bot versus
loading-human eligibility, snapshot identity reuse, source setting clamps and construction
cleanup. Existing Arena/end-screen and reliable snapshot release tests retain their distinct
coverage. No timing deadline, policy assertion or original baseline failure is waived.

The factory still requires real lifecycle/input/effect services, trusted identities,
deployables, plugins, resources, ending transitions and persistence/results. Additional
projectile/construction/entity/corpse and bot factories, auth/master services, full bootstrap,
remaining modes, supported-client/capacity/soak and baseline reconciliation remain pending.
The ordinary launcher remains gated. This checkpoint completes the selected production
factory and connected-player service bindings; it does not complete a playable server.
