# Round music, ambience and next-map ballots

Follow-up: [VOTING_POLICY.md](VOTING_POLICY.md) adds discovery/ranking, kick policy
and unified host ballot/admission bindings. The text below records the preceding
round-media checkpoint and its then-pending service scope.

This connected checkpoint binds source-backed presentation services after
[PREPARED_HOST.md](PREPARED_HOST.md). Complete gameplay and normal hosting remain pending.

## Implementations

`AudioPackets` now encodes/decodes CreateAmbientSound(22), PlayAmbientSound(24) and
PlayMusic(26). `VotePacket` implements GenericVoteMessage(47), its bounded candidate
records and flags. Optional fields, signed fixed-point music/stream stores and the
native positioned-flag decode/re-encode quirk match the compiled source. Negative
signed candidate counts retain the native empty-list normalization. PacketCodec and
its generator register all four IDs; production support increases from 84 to 88 IDs.
The remaining old generated packet source/specification stays byte-identical.

`MusicService` owns the source broadcast music family and seeded choice stream.
Switches reliably send the silent AL-error flush, StopMusic, then one specific track.
Gameplay music can be disabled; last-man/ending music suppresses a same-family restart,
while timeout music always switches. A family is recorded only after all sends finish.
Direct specific-track sends do not mutate broadcast family state. The source track
lists and Python-3.12 Unicode digit set are exported as inert catalog data, preserving
family suffix decisions even for superscript/fullwidth digits independently of JDK
Unicode changes. The general gameplay sound/stimulus helpers and mid-join mode-specific
music selection are still separate domain/service work.

`MapAmbience` uses the existing validated MapMetadata definitions/defaults, sends the
silent flush and pairs each CreateAmbientSound with its looping PlayAmbientSound.
Loop IDs start at one and stop at 255. Global beds have empty point lists; local
emitters bootstrap at the listener so the native media manager can allocate their
streams before moving them to authored points. This is a reveal-time helper, not a
replacement metadata parser or automatic full-server reveal pipeline.

`VoteText` produces the stock HUD's Python-2-compatible string/tuple literals. Unicode
gets explicit unicode literals/ASCII escapes; dynamic map text doubles braces before
native formatting and rejects NUL/line breaks/overlong names. `MapBallot` owns only the
next-map ballot lifecycle. It normalizes/deduplicates up to three supplied candidates,
checks a supplied startup catalog when present, emits START/UPDATE/CLOSED and localized
result packets, permits revotes, and breaks ties by lowest candidate index. It verifies
canonical actor identity and the supplied live-human eligibility predicate. Client
packet 47 echoes are treated as opaque exact tokens; no text is evaluated and a raw
name cannot substitute for an advertised literal. The decoded packet's player ID,
count values, title and flags do not replace the authenticated actor or server tally.

Deadlines use monotonic time independently of the wall-time tick escape hatch. Casting
and reveal enforce expiry before processing. Departure removes that ID's tally and can
finish a remaining fully voted roster. Result consumption clears only the staged map.
Each asynchronous result waiter has a cancellable fallback timer, so it resolves even
when the simulation tick is paused; cancelling one waiter never cancels the ballot or
another waiter. Old waiters retain ballot identity and cannot close a replacement.
Source candidate discovery, ranking, history/fit/retail-playlist policy and kick votes
are not implemented by this class and remain explicit future services.

`RoundPresentation` binds actual music, forced-score packets, owner-loop sleeps,
MapBallot result waits and result consumption into RoundEndSequence. Cancellation
propagates through its void-stage bridge to the actual nested ballot wait/timer.
Bots, statistics/achievements, screen configuration/headline, transition/restart,
nested-task cancellation and recovery remain required bindings. No empty production
implementation is supplied for these ports.

`HostedMedia` binds reliable broadcast/direct delivery to actual ApplicationHost,
canonical LifeRuntime actors and the current domain supplier. Eligibility excludes
bots and players that are not in game. Its reveal helper sends metadata ambience and
an active ballot once per canonical actor/scene; stale actors cannot replay a reveal.
Packet routing and departure/reset hooks are available for the concrete domain to bind.
Its reset method cancels/consumes the map ballot only; future kick-manager state still
requires its own reset. It does not automatically enable the normal launcher.

## Verification and limits

The generator invokes intact server.audio, VoteManager map lifecycle, announcement and
native codec methods against controlled clocks, random seeds, catalog inputs and
actor/connection presence. Actual asynchronous event waiters are used for resolution,
cancel/replacement and already-expired cases; no policy method is replaced. The 16
ballot traces include revotes, bots/loading/orphan connections, spectator eligibility,
canonical replacement/stale actors, departure, wall/monotonic expiry, cancel/replacement,
late reveal, opaque-token forgery, Unicode/quotes/braces and catalog/length validation.
Distinct native wire vectors are decoded/re-encoded; malformed-prefix walks are grouped
by structural size/kind instead of repeating the same walk for loop-ID variations.

Five boundaries cover independent timeout cancellation without a tick, reentrant wait
completion starting a replacement, worker-to-owner timer resumption, ending cancellation
through the presentation bridge and music delivery failure/owner guards. The initial
focused test compile caught an incorrect fixture packet-field accessor; it was corrected
before the composed/full verification. No deadlines/assertions/source policies are
removed, relaxed or waived.

The existing accepted-kill/TDM/frame/ending/restart composition now uses MusicService,
MapBallot and RoundPresentation. Actual TDM start/end hooks drive the services; timeout music is also exercised
through the real UDP delivery binding;
two canonical humans finish the ballot, the ending consumes the same-map result, holds
the screen dwell and restarts actual lives/scores/music. Vote/timer placeholders are
removed from that composition; bot preparation/statistics and other remaining ports
stay explicit controlled services.

The existing retained-peer real UDP rollover now also binds HostedMedia. One live
human receives native music/ambience and a ballot while the loaded team-selection peer
receives no gameplay music/vote. The live peer sends the exact advertised token through
actual ENet/application/packet/life routing; its selected map becomes the worker-prepared
owned-state transition target. Existing different-CRC, ordered freeze/reload, peer/slot
identity, no PlayerLeft/disconnect, new lives and resource-retirement assertions remain.
This remains a production-component composition with an explicit gameplay fixture
domain, not supported-retail-client or full-production-bootstrap validation.

Local Java check/build passes **6,126 component + 36 integration = 6,162 tests**,
with zero failures, errors or skips across 98 suites. This batch adds 24 original-source
scenario/codec cases and five targeted boundary cases; two existing compositions are
upgraded rather than duplicated. **273 focused original-source regressions pass**.
The new generator records four seeded music traces, three ambience scenarios, 16
map-ballot traces and 564 distinct native codec vectors in one aggregate codec case.
The updated simple-packet generator and new media generator reproduce all six outputs
byte-for-byte. Of 251 earlier frozen paths, 250 retain their exact hashes; PacketCodec
is deliberately updated to register native-backed IDs 22/24/26/47. There are 254
checked paths and 72 configured CI generators. Unchanged prior 70-generator/native
build evidence is reused locally; CI independently rebuilds/reproduces all 72.

Two CRC-valid local archives are byte-identical: 1879059 bytes,
SHA-256 `2b1eec34f5af529436c37c2287e9b09ede815f483d02eb22896c8ef2ad49f2a6`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader still uses FixtureScene. Platform tests run
source-built component/integration classes and smoke the uploaded JAR. No local-to-CI
artifact hash identity or complete platform gameplay is claimed. Prior medpack/Zombie/
VIP original-baseline discrepancies remain unwaived; these focused passes do not
establish a passing full original-source baseline.

All five CI jobs passed for implementation `280aa453e20ff89709db907b86437e3adff13852`,
tested/published tree `d9661a57f823ab8bc63bf200f03a2b94d7674456`, in
[run 39168496083](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39168496083). The native job independently reproduced all 72
configured original generators, including round music/ambience/ballot traces, native
codec vectors and the intentionally updated packet registration. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and ran source-built
component/integration tests. Every final job step completed with success or an intentional
skip. No local-to-CI artifact hash identity or complete platform gameplay is claimed.

Remaining work includes map discovery/rotation/ranking and kick-vote policy, concrete
production factories/full asynchronous hosting assembly, general gameplay sound and
join-music selection, bot/reset/repair/journal/HUD/statistics/achievement/persistence
services, remaining gameplay/modes, supported-client multiplayer, baseline reconciliation
and capacity/soak.
