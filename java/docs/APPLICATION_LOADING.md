# Application loading and live catch-up

This component is implemented and tested with a **test-only scene**, not a playable
server. `ApplicationHost` connects real ENet events to `JoinSession`; `JoinScene`
is the boundary where the remaining mode, rule, player and physics ports belong.
The diagnostic Main does not instantiate the fixture or advertise game hosting.

## Ownership

| Component | Owner and work |
|---|---|
| ApplicationHost / EnetHost / JoinSession | One event-loop owner; bounded UDP/event work, framing, control queues and domain callbacks |
| MutableWorld / PlayerRoster / PlayerSlots | Same owner; committed cells, live life tokens, identity reservations and journal pruning |
| MapSyncSnapshot | Frozen base/overlay bytes and revision, safely published to compression workers |
| BoundedSnapshotCompression | Fixed worker/queue limits; owner collects results, cancels retired jobs and handles submission backpressure |
| JoinScene | Supplies actual InitialInfo, StateData, roster and admission; receives capability and authoritative input-arrival tick |

The caller owns scene/world disposal. ApplicationHost closes sessions, compression
and transport; it does not silently dispose an externally owned scene.

Only immutable snapshot work crosses the worker boundary. A cancelled compressor
checks interruption at record boundaries. Completed, uncollected jobs still consume
capacity so retaining results cannot silently bypass the queue limit.

## Protocol lifecycle

1. ENet connect alone sends no match metadata. The first SteamSessionTicket installs
   the XOR key. BSCF1–4 capability trailers remain outside that key; the matching
   source BSFP profile is appended to InitialInfo for capable clients.
2. A private server sends PasswordNeeded and withholds InitialInfo, maps and slots
   until a valid answer. Password pacing, attempt limits, address lockout and timeout
   follow the reference. Secrets are never included in messages or metrics.
3. Reserve the lowest free player ID before InitialInfo. The same ID must appear in
   StateData and the admitted CreatePlayer. A full server refuses before metadata.
4. InitialInfo -> client CRC -> server's actual CRC ->100ms loader delay -> frozen
   snapshot -> MapSyncStart/Chunk/End -> StateData -> skybox and roster. Full and auto
   delta rules, prefixes and an empty delta's start/end are retained. Submission and
   reliable enqueue backpressure cannot advance a cursor past unsent data.
5. NewPlayerConnection is ignored before StateData. An admitted player gets its own
   CreatePlayer and palette. Capabilities and the cached class selection go to the
   domain hook; the hook must implement game rules and actual player construction.
6. First ClientData proves GameScene entry. Replay optional frozen air repairs,
   reconcile remote roster lives/palettes/removals, replay committed terrain cells,
   then domain reveal. Keep the lease until queued reveal data drains and repeat
   catch-up before opening the live broadcast gate. A cell or roster life changed
   while an enqueue was blocked therefore cannot disappear into a loading gap.
7. Scene replacement cancels old work and drops still-loading peers with reason18.
   Completed loaders retain their ENet peer, XOR key and cleared password, receive
   MapEnded, wait the configured grace, and repeat the loader with required fresh
   validation. No custom ClientInMenu acknowledgement is required by stock retail.

The input receive path uses `protocol/runtime_packets.py` semantics, including
signed-magnitude piecewise orientation and absent/fixed/float deployment yaw tails.
The reconstructed packet4 codec remains separate. A short optional packet13 tail is
accepted, with source receive count limits. Clock replies remain unsequenced.

## Committed terrain and roster

`MutableWorld.set` is a committed-cell primitive. Dynamic colors use source alpha
0x80; paint keeps initial health, removals materialize adjacent implicit surfaces,
and snapshots freeze all modified columns before a worker runs. Geometry fills and
sparse explicit-color runs remain compact. The canonical seabed stays solid.

The bounded per-cell journal stores coordinates and sequence, not stale effects.
Replay coalesces by first occurrence and reads final state at send time. A solid
cell emits BlockBuildColored, explicit-color PaintBlock and the actual retail
BlockManagerState user-health row. Air emits exact type6 Damage31.75 with no collapse.
A partially enqueued cell resumes; a changed cell restarts its idempotent repair.
Overflow rejects the join with reason13. Leases are released on admission, failure,
disconnect, retirement and host shutdown.

`PlayerRoster` tracks concrete life tokens, even when two lives have identical wire
appearance. Plans use CreatePlayer (not ExistingPlayer's unsafe pickup sentinel),
refresh colors, retire missing IDs, and commit their known state only after the
complete reliable plan is accepted. Another reconciliation runs before admission.

## Verified scope and limits

- Source-generated fixtures: four additional packet layouts, four flight profiles,
  twelve live-input variants, eight committed-mutation operations,792 cell samples
  and72 serialized columns. These execute the original Python/Cython oracle.
- JUnit exercises deadlines, secrets, IDs, queue pressure, journal overflow, partial
  retries, lease cancellation, life changes, reentrant epoch changes and shutdown.
- Real Java UDP tests exercise the composed application on every CI platform.
- Native ENet probes independently decode full/delta map streams, create two
  simultaneous loaders, verify full refusal, XOR/passwords, exact repairs and
  same-peer scene replacement, including loss/reorder/duplicate traffic.

These are not original game-client/UI tests. Tickets currently supply the source's
XOR/capability behavior; they are **not Steam ownership authentication**. Actual mode
builders, class/rule normalization, bot eviction, spawn/death/respawn and full entity
reveal remain domain work. Marker cleanup, damage/shade history, terrain validation,
collapse, creator/UGC pre-validation source transfers and complete match rollback
are not implemented by this component. Packet38 currently covers the actual repair
layout with zero occupied rows; occupied rows remain unsupported. Resource budgets
are explicit component limits, not a passed50-player or gameplay performance gate.
