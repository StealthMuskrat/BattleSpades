# Owned WorldUpdate replication

Source: `6216b771282d2d349ccd67ee58c378f035c83553`, chiefly
`server/replication.py`, `server/connection.py` and `server/player.py`.
This checkpoint implements connected-player snapshot scheduling and transport
ordering. It does not establish complete gameplay or a supported client's
reconciliation behavior. Ordinary hosting still exits 2.

`ReplicationService` runs after every owned physics step. It owns cadence buckets,
ordinary/airborne self-row intervals, synthesized-label exclusion, retail jump
recovery spacing, and urgent advertised jetpack/canopy transitions between regular
cadence boundaries. Split delivery sends observers unsequenced when the measured
reorder spread permits it. Wider spread changes spacing, then selects ordered
delivery; returning to unsequenced delivery waits for the preceding reliable
prefix to be released. Owner headers repeat the recipient's latest observer
clock, while the owner row's pong retains its actual applied-input stamp.

`SnapshotWire` preserves the immutable shared base, patches only the owner's tool
byte to 255, partitions player rows beside the entity tail, and filters held
entity/turret rows using the signed-short IDs actually written by CreateEntity.
Malformed variable tails are withheld. Oversized snapshots use explicitly
unreliable fragments; they never acquire reliable fragmentation accidentally.

`PlayerSnapshot` composes real owned position, orientation, clipped velocity,
health, controls, flight fuel/state and spawn protection. It requires a trusted
presentation domain for selected tool/pickup, effects, bot identity, corpse
visibility, mode tool permission and mounted-gun authorization. Unauthorized tool
rows clear the original weapon-action mask; authorized mounted MG rows preserve
their action bits but always send tool 255. Raw client fire flags do not create
server-owned fire/goo/disguise effects. Entity/turret registries remain explicit
world inputs rather than fabricated empty production registries.

`OwnerAnchors` records only accepted owner outputs on InputTimeline's shared
causal sequence clock, without rewinding physics. It retains 128 rows, strict
earlier-label/earlier-sequence eligibility, stable ties, spawn fallback and
connection sequence continuity. The last pong stamp survives a respawn, as in
the source Player facade. Recording feeds the actual canopy owner-row
handoff. The life runtime forgets replication cadence/recovery at spawn and
disconnect; observer connection state survives respawn.

ENet reliable sends can return an owner-thread `DeliveryReceipt`. Every fragment
must be ACKed, or the peer's packet discarded, before its release callback runs.
`SnapshotHolds` ports the original bounded logical reliable index, 600-tick prefix
timeout and 120-tick lifecycle holds. CreatePlayer is packet 28; CreateEntity is
packet 21. Holds start at application-queue acceptance, including local transport
backpressure, and overlapping creates require every outstanding token to release.
Ordered snapshots stay behind queued reliable controls. A packet release, queued
owner history and estimated flight handoff are three different facts; none proves
that a game client applied a WorldUpdate.

| Source comparison | Operations | Scope |
|---|---:|---|
| ReplicationService | 18,448 | 16 traces; exact native packet bytes, cadence, grouped/split delivery, urgent transitions, recovery, bots/ping, tool sanitization, reordered/barrier and loading-connection states |
| Immutable wire transforms | 2,235 | Variable entity properties, signed IDs, turret filters, malformed tails, row partitioning and owner-only overrides |
| Connected Player snapshots | 1,132 | Original flag/snapshot methods after real native movement/flight, with controlled trusted presentation fields |
| Owner anchor history | 3,714 | Original full Player record/query methods with movement/flight, duplicate stamps, bounded history, causal eligibility and life reset |
| Connection lifecycle holds | 8,458 | Unchanged original AST method bodies with exact constants, controlled clocks/header bytes/free callbacks; separate actual ENet receipt tests |

Generators never replace source policy methods. The Connection-only generator
extracts its three unchanged method ASTs and five constant assignments to avoid
unrelated launcher dependencies. Other generators execute original modules and
the compiled original packet/world code. Every fixture has reproducible gzip
bytes and source/fixture hash provenance. CI rebuilds the original native world
and regenerates these and prior fixtures.

A two-client UDP integration test composes ApplicationHost, ConfiguredScene,
LifeRuntime, InfantrySimulation, FixedStepRuntime and ReplicationService. It checks
owner history/tool sentinel versus observer tool/unsequenced delivery, delays real
ENet ACKs across respawn, observes withheld new-life rows, then releases ACKs and
observes those rows resume. Queue-pressure tests check ordering and rejected
admission does not fabricate owner anchors or consume transition urgency.
Native packaged loader probes retain their separate FixtureScene scope.

Required presentation/mode/effect, entity/corpse/bot, reload/combat and identity
domains remain incomplete. Opt-in flight transition diagnostics, full lag
compensation/anticheat, supported-client rounds, capacity and soak validation
remain pending. This is a verified replication component, not a finished server.
The full Python baseline retains its documented incomplete runs and VIP discrepancy.
See [CI_STATUS.md](CI_STATUS.md) for exact published-head results.

Independent verification for implementation `00d96933315fc201e44234912e6e95c6ed17609d` and tested tree
`276cf8712f4801de0171f8a8b37380a78c4f04e8` passed all five jobs in [run 39071633850](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39071633850).
It reproduces original fixtures, byte-compares the repeated build archive, runs
native probes and tests one uploaded JAR on three OSes. Local verification passes
4,602 Java tests and 154 focused original tests. The gameplay/domain limits above
remain in force.
