# Map discovery, rotation and kick-vote policy

This connected checkpoint extends [ROUND_MEDIA.md](ROUND_MEDIA.md) with source-backed
selection and moderation policy, a single vote HUD and actual host actions. Complete
production gameplay/bootstrap and normal hosting remain pending.

## Implementations

`NavigationAreas` reads the source 40-byte little-endian BSNAV01 header and inflates
only the first three bounded column planes. The walkable flag determines playable
area; missing, malformed and short data remain advisory unknowns. Width times height
must be at most 262,144; compressed input has a 64 MiB loading bound. Like the source,
a valid first-three-plane prefix is sufficient without a complete trailing stream.
Its concurrent advisory cache uses normalized path, modification time and byte size.
Missing files are not cached. Loading and inflation belong to a worker/startup phase.

`MapCatalog` snapshots installed VXL names, area overrides/navigation areas and existing
retail metadata/playlists before owner-thread policy runs. Casefold ordering compares
Unicode code points; explicit rotations preserve source order/duplicates while dropping
unavailable names. Optional shuffle uses SourceRandom with the source stream. Unknown
areas remain neutral. Installed maps outside an explicit rotation are also measured,
so later area lookups require no owner-thread file access. Explicit rotation filtering
uses retail invalid_modes, including ClassicCTF's retail CTF alias. Implicit rotations
use a mode's stock maps or existing release/classic/mafia-aware retail pools, preserving
catalog order. Empty or unavailable filtered pools fall back to the configured catalog.
The catalog does not reload itself or construct the complete server configuration.

`MapRotation` owns a 16-entry played history, casefolded consecutive duplicate
suppression and exact source rotation after the current map. Ranking compares human
retail-cap violations, recent-map status/reverse rank, bot-weighted size-fit tier and
original cycle index. Unknown areas are neutral; fit thresholds are 1.75 and 3.0.
Human/bot population includes roster entries outside current vote eligibility, as in
the source. A zero ideal area retains the source arithmetic failure rather than silently
inventing a fit score. Ranking and mode selection use the immutable catalog only.

`KickBallot` implements authenticated canonical live-human voting, spectator/self/host/
active-vote/cooldown/team-population denial order, source reasons, localized denials,
automatic starter yes, dynamic majority and exact START/UPDATE/CLOSED wire text. Team
minimums count non-bot roster entries, including loading members. Target votes and
stale/ineligible actors do not count; revotes are supported. Wall ticks and monotonic
cast/reveal deadlines enforce the 30-second limit. Protected-host lookup and direct
send/disconnect RuntimeExceptions retain advisory handling; fatal errors propagate.
Address cooldowns survive reconnect/ID replacement; fallback ID cooldowns are forgotten
on departure. A starter cancellation uses the shorter cooldown. Successful kicks record
a match exclusion and the native disconnect reason. The active ballot retires before
terminal disconnect, preserving synchronous departure callback safety. Redundant yes/no
sets and the unused source compatibility timestamp are not copied into Java state.

`VoteText` adds the source nested localization-index tuple used for kick reasons,
preserving Python-2 Unicode/quote escaping. Wire candidate text remains an exact opaque
token; no expression is evaluated. `VotingService` owns one map/kick overlay, rejects
competing starts, preempts a kick for round-end map selection, preserves a staged map
result, ranks up to three candidates and routes native 47/48 using the actual actor.
Packet-supplied player IDs do not replace authenticated identity. Match reset clears
ballots/results and kick exclusions while retaining address cooldowns.

`HostedVoting` binds current canonical LifeRuntime actors to ApplicationHost live status,
actual normalized UDP addresses, reliable denial/ballot delivery and actual disconnect.
A host admission gate rejects excluded reconnects before allocating a gameplay slot or
loader session. The protected-host predicate and current domain/map/mode/stock/clock
ports remain required bindings. This gate is one policy; full bootstrap must compose
other admission policies and authenticated/Steam relay identities. `HostedMedia` can
share this service's exact map ballot, routing reveal/packet/departure/reset to the same
HUD owner while preserving its prior map-only composition API.

## Verification and limits

The new oracle calls intact server.voting, retail metadata, bans, announcements and
native packet methods. Controlled filesystem/nav bytes, source random stream, clocks,
actors/connections and terminal disconnect isolate external services; no policy method
is replaced. Its 49 groups comprise 10 navigation cases, 12 catalog/ranking scenarios,
26 kick/arbitration traces and one aggregate of 10 fit boundary vectors. Traces compare
every action's state, direct/broadcast bytes, cooldowns, exclusions and disconnects.
They cover all reasons, denial order/advisory fault, bots/loading/spectators, stale actor,
revotes, departure, round-end preemption, opaque tokens, Unicode/NUL name handling,
address/ID cooldowns, match reset and wall/monotonic expiry. Source terminal disconnect
also synchronously forgets the actor. No supported-client claim follows from these traces.

Two focused boundaries verify worker-created immutable catalog use after source files
are removed, owner-thread/history guards and the source arithmetic edge, plus advisory
cache modification/size/missing-file behavior. Existing source traces cover the policy
branches; no large mirrored execution matrix or artificial test partition is added.
Initial focused verification corrected a missing Java test parenthesis, catalog area
snapshot coverage and integration assertions to use the runtime's throwing player lookup
contract and canonical unique names. No deadline, assertion or source baseline is waived.

A new real UDP four-human scenario sends native kick request 48 and exact-token vote 47
through ENet, application framing and actual LifeRuntime dispatch. It checks the two-vote
majority, exact localized CLOSED, reason 24, synchronous departure/slot release and retained
live peers. A fresh same-IP connection receives reason 24 before InitialInfo or slot
allocation. Match reset clears the exclusion while preserving cooldowns, then a real
loader/new-life connection reuses the released slot. This is distinct from policy traces.

The existing real UDP retained-peer rollover now creates its catalog on the actual worker,
uses ranked candidates from VotingService and shares HostedMedia's ballot. The live peer's
native cast selects the worker-prepared next map; reset/history hooks use that same service.
All existing team-selection/live-peer, different-CRC, ordered reload, peer/slot identity,
no PlayerLeft/disconnect, new life and old-resource retirement assertions remain. The
remaining gameplay domain is explicitly test support, not full production bootstrap.

Local Java check/build passes **6,177 component + 37 integration = 6,214 tests**,
with zero failures, errors or skips across 100 suites. This batch adds 49 intact-source
scenario groups, two focused boundary tests and one real UDP integration scenario;
the existing retained-peer rollover composition is upgraded. **273 focused original-source
regressions pass**. The new generator reproduces both outputs byte-for-byte; all 254
prior frozen paths retain their exact hashes, for 256 checked paths. There are 73
configured CI generators. Unchanged prior 72-generator/native-build evidence is reused
locally; CI independently rebuilds/reproduces all 73.

Two CRC-valid local archives are byte-identical: 1911352 bytes,
SHA-256 `abb8d1fbb46296797f3de2ec72a9ba36f0cd4cb65cf2e12b9e5723172ec1753a`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader still uses FixtureScene. Platform tests run
source-built component/integration classes and smoke the uploaded JAR. No local-to-CI
artifact hash identity or complete platform gameplay is claimed. Prior medpack/Zombie/
VIP original-baseline discrepancies remain unwaived; these focused passes do not
establish a passing full original-source baseline.

This checkpoint requires a new five-job CI run after publication.

Remaining work includes concrete production domain factories/full asynchronous hosting,
remaining gameplay/modes, general sound/join music, bots/reset/repair/journal/HUD/
statistics/achievement/persistence services, authenticated identities, supported-client
multiplayer, baseline reconciliation and capacity/soak. Normal hosting remains gated.
