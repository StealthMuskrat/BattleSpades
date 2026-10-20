# Durable round-result pipeline

The Java host now has concrete round-result accounting, durable retry storage, master HTTP delivery and shutdown integration. This is an embedding workflow; ordinary standalone match startup remains gated while the other production services are migrated.

## Accounting and lifecycle

`RoundResultLedger` implements the original Revival reservation rules with immutable player/profile snapshots. It keeps score, cumulative retail profile and participation baselines separately. Reservation occurs synchronously before the first disk/network await. Departed players retain their credit, a verified account can reconnect under a new player object, and reusing a numeric player ID does not inherit the previous identity or baseline.

Generic kill/death/capture fallback counters are suppressed for profile-managed players. The original mode-total statistic IDs, completion/win/loss/draw fields and participation limits are retained. Map evidence includes the unsigned eight-digit source CRC and the original 30–14,400-second duration gate. Tutorial/UGC modes, bots, unverified accounts and explicit offline hosting do not submit account results.

`HostedRoundResults` binds admission, departure, scoreboard reset, round start, the `gameStats` result boundary and shutdown. It reserves unfinished snapshots before a running round/world is reset or retired; those snapshots receive no completion/win bonus. Profile baselines survive score reset. The achievement field can carry IDs supplied by a trusted achievement owner; this checkpoint does not implement achievement evaluation or a local profile database.

## Durable spool

`ResultOutbox` is a portable directory format, with SHA-256 payload files and a versioned `index.json`. Its authoritative commit is an atomic index replacement after the payload files have been forced to disk. Transactions are protected by a filesystem lock with a five-second acquisition limit, allowing separate Java processes to share the spool while keeping server-ID partitions distinct.

The spool retains at most 4,096 pending events per server, bounds each event to 256 KiB and bounds the fleet index to 8 MiB. The publisher's unpersisted reservation queue holds at most 256 envelopes. An event ID reused with different bytes is refused; a failed validation/capacity batch does not commit a partial index. Pending payload checksums are verified before delivery. Malformed indexes or payloads are retained and reported instead of overwritten.

An optional account-owner mirror stores the same credential-free envelope as `<event_id>.json`. Retrying an identical event repairs a mirror interrupted after the primary commit. Acknowledgement removes the spool entry and its mirror. The filesystem must support atomic file replacement. Only acknowledgement of an indexed event deletes its payload. Unrelated files and uncommitted payloads remain intact. SQLite is not loaded by the Java application.

## Import an original SQLite outbox

Stop the original process before exporting. The exporter reads SQLite in read-only mode and refuses to overwrite an existing export file:

```sh
python java/tools/export_result_outbox.py state/round-results.sqlite3 round-results.ndjson
java -jar java/build/libs/BattleSpades.jar --results-import round-results.ndjson state/java-results
```

Use a new directory for the Java spool. Original event IDs, server partitions, payloads and row order are retained. Import is idempotent; each group of up to 64 reports is an atomic batch. If a later group fails, earlier committed groups remain and the same export can be retried. No source database, account credentials or original outbox are deleted or modified.

## Bind to a match host

Create the HTTP sender and disk executor in the application's service owner. `HttpMasterResults` accepts a configured master base URL, a write credential and a timeout. It posts the unchanged envelope to `/api/master/stats` with the original Bearer authorization/content-type/user-agent contract. Requests are asynchronous, redirects are refused, response bodies are bounded to 64 KiB, and the timeout is clamped to 1–15 seconds. HTTPS is required except for explicit loopback HTTP used by local services/tests.

```java
var outbox = new ResultOutbox(spoolDirectory, accountMirrorDirectory);
var publisher = new ResultPublisher(
    outbox, advertisedServerId, diskExecutor, ownerLoop,
    masterResultsHttpClient, reportResultFailure);
var results = new HostedRoundResults(
    host,
    new HostedRoundResults.Settings(online, advertisedServerId, playlistId, relayLobbyId),
    publisher,
    authenticatedIdentities::resultIdentity,
    achievementOwner::takeUnreported,
    java.time.Instant::now,
    reportServiceFailure);
```

Bind before attaching the scene and before accepting connections. `online` must honor offline mode, the configured Revival enable flag and the availability of a write credential. The server ID must agree with the existing registration/allocation owner. Identity verification must finish before admission; the function receives the real `JoinSession` and returns an optional verified legacy ID plus canonical nickname. Neither the packet nickname, ticket bytes nor the compact player ID establishes an account identity. Unverified players remain ordinary local participants.

A server with achievements disabled can supply an explicitly empty achievement result. A server with no verified identity service must leave players unbound. Master registration, join-ticket verification and achievement evaluation remain separate required owners; this checkpoint does not substitute claims from the client.

The host pulses the delivery owner without awaiting it in the simulation. It persists all queued envelopes before attempting HTTP delivery, accepts an acknowledgement only for HTTP 200 plus JSON boolean `accepted: true`, and otherwise retains the same ID/body for retry. Overlapping flush calls share the in-flight transaction. Cancelling a dependent future does not cancel the underlying disk transaction. Failed delivery is retried at a bounded five-second pulse cadence while online.

## Shutdown and verification

`HostedSimulation.stop()` captures the unfinished round before deactivating it and awaits result persistence. It does not require master acceptance; existing in-flight bounded requests settle and queued reports stay on disk. An embedding without that simulation must await `host.shutdownResults()` while continuing to pump its owner loop, then close the host/world, HTTP client and disk executor. `ApplicationHost.close()` refuses to discard a bound result owner whose persistence shutdown has not succeeded. Repairable persistence shutdown failures can be retried.

The round-result batch implements owner-side counter reservation, disconnected-account credit, portable durable outbox/SQLite import, acknowledged master HTTP delivery and host reset/shutdown/offline integration. **6,353 component + 67 integration = 6,420 Java tests** pass across 121 suites with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. Final full component and integration runs pass after removing automatic unindexed-payload cleanup; unrelated spool files are preserved. The earlier UDP clock-driver correction did not weaken assertions or deadlines.

All **48 targeted unchanged original-source tests pass** across result outbox, profile statistics and Revival master. Two fixture traces execute the original reservation methods and reproduce four event envelopes. An actual original SQLite outbox was exported read-only and imported twice by the packaged Java application; both reports, original database bytes and idempotent IDs were verified. Three grouped component and four new integration cases cover native accounting, durable transactions/mirrors/cancellation, actual HTTP acknowledgements/retries, packaged import, UDP departure/slot reuse/reset/shutdown and offline suppression. CI now configures 79 reference generators. The six source directories remain exact against `546c548780c400f21d73ca6713f86194ceb3c844`; all 262 existing resources are unchanged, with one new result fixture added.

The CRC-valid application JAR is 2679721 bytes, SHA-256 `ec52b48ce0aa4bbcf1da0873d3f34759125461f4624a60800a6933fc1ee22ca5`, class major 69. Version/configuration/help checks exit 0; missing UGC projects and ordinary match startup exit 2. CI for this checkpoint is pending. This supplies the results pipeline embedding; master registration/authentication, achievement evaluation/local profile persistence, bots, Tutorial, other commands and complete standalone startup remain incomplete. See [RESULTS_PIPELINE.md](RESULTS_PIPELINE.md).

