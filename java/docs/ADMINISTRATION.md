# Administration and durable bans

This checkpoint supplies a server-lifetime administration owner for the existing Java match host. It implements password login, one-time private-room claims, verified auto-admin identities, persistent admission bans and moderation commands. Ordinary standalone match startup remains gated: this is concrete embedding API composition, not a bundled Steam/master authentication service.

## Bind before admitting connections

Create `BanStore` with a path relative to the selected server data directory, a wall clock, a dedicated disk executor and a thread-safe failure reporter. Load it before accepting peers; it reads the original Python `bans.json` object (`address -> {name, reason, until}`) directly. A malformed document fails startup without replacing it. A process lease prevents two writers from using the same store.

After creating `ApplicationHost`, bind `HostedAdministration` **before** attaching the scene state and before servicing the network:

```java
var administration = new HostedAdministration(
    host,
    AdminAuthority.Settings.from(rawConfig),
    bans,
    authenticatedIdentities::adminKeys,
    monotonicClock,
    ownerLoop,
    failureReporter);
sceneState.attach(host);
```

The identity function receives the actual `JoinSession` and must return only independently authenticated `steam:<id>` or `aosplay:<id>` keys. It is evaluated when that connection first needs administration state. Complete identity verification before that point. An explicitly offline embedding can return `Set.of()` and use password login or room claims. Client ticket bytes, typed names and compact player IDs never establish identity. The existing embedding service's explicit admin grants continue to work alongside this owner.

`HostedTransitionServices` attaches the scene itself; when using it, create that service after binding administration instead of separately calling `sceneState.attach(host)`.

```toml
[admin]
password = "replace with a private secret of at least 12 characters"
auto_admin = ["steam:76561198000000000", "aosplay:account-id"]
# Private client-hosted rooms may supply a random single-use token:
# creator_token = "24-to-128-ASCII-letters-digits-hyphen-underscore"

[anticheat]
admin_login_attempts = 3
```

The shipped `changeme`, empty and short passwords disable `/admin`. A creator token must contain 24–128 ASCII letters, digits, `-` or `_`. Auto-admin accepts canonical verified identity keys, including bare SteamID64 configuration entries; player names do not qualify. Credential settings redact their string representation, and the canonical command logger already redacts both `/admin` and its `/login` alias, plus `/claimhost`.

## Commands

| Command | Behavior |
| --- | --- |
| `/admin <password>`, `/login` | Constant-time credential comparison; grants this connection admin authority. |
| `/claimhost <token>` | Hidden one-time claim; grants room-host and admin authority. |
| `/roompassword`, `/roompass` | An administrator can retrieve a private client-hosted room's generated password. Dedicated-server passwords are not echoed. |
| `/kick <player> [reason]`, `/k` | Broadcasts the source HUD announcement and disconnects the matching player with reason 2. |
| `/ban <player> [duration] [reason]`, `/b` | Persists the address ban before announcing success and disconnecting with permanent reason 1 or temporary reason 19. |
| `/mute <player>`, `/unmute <player>` | Controls public/team/private chat through the existing social owner; commands remain available. |
| `/god [player]` | Toggles the existing authoritative damage immunity. |

Targets use the source's first case-insensitive name prefix match. Durations accept seconds or `s`, `m`, `h`, `d`, `w`; `perma`, `permanent` and `forever` are permanent. A non-duration second token begins the reason. Other admin/server command families, including teleport and map/settings commands, remain outside this checkpoint.

Password and room-claim failures share a bounded address ledger and a 600-second inactivity window. At the configured limit the guessing connection is kicked; non-loopback addresses receive a persistent 600-second ban. Loopback is never automatically banned by credential failures. Successful login clears that address's failure count. One-time room claims remain consumed across map changes for this server process.

## Ownership and shutdown

Admin grants follow the concrete connection across scene rollover. A replacement actor that reuses a numeric player ID receives no grant from the previous connection. Moderation persists immutable snapshots on the disk executor; a suspended ban continuation retains the original connection, so it cannot disconnect a replacement in the same slot. Ban admission checks run before the application loading session is created and compose with an existing admission gate.

Bans are immediately authoritative in owner memory, written in order through a forced temporary file and atomic replacement when supported. A disk failure is reported and the command does not announce durable success; the in-memory ban remains effective and a subsequent snapshot can persist it. Lookup purges an expired ban and queues the updated document. `BanStore.remove` is the embedding operator's unban API.

All service mutation runs on the gameplay owner. Only immutable encoded snapshots reach the disk executor. The failure callback can run on that executor and must be thread-safe. Stop/close `ApplicationHost` before closing `BanStore`; the latter waits for outstanding writes before releasing the process lease. Close the caller-owned disk executor afterwards. A timed-out or interrupted close retains the lease while writes may still run.

## Verification

The administration batch implements password login, one-time room-host claims, verified auto-admin identities, durable bans/admission and moderation on real match-host connections. **6,350 component + 63 integration = 6,413 Java tests** pass across 119 suites, with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. Three new grouped component cases and three actual-UDP cases verify persistence/failure recovery, permissions, secret log redaction, map rollover and suspended-ban safety across compact-ID reuse. Existing component results were reused after correcting only the new UDP fixture.

Unchanged original command/room-admin tests report **45 passed and 3 failed out of 48**; all three failures are GUI configuration imports of the unavailable `tomlkit` package. Original tests were neither changed nor skipped. Native duration/identity/password/token vectors match the Java checks. The six source directories remain exact against `546c548780c400f21d73ca6713f86194ceb3c844`; all 262 existing Java resource paths remain unchanged against this batch's parent.

The CRC-valid application JAR is 2631945 bytes, SHA-256 `cfe106761bc7c21c0353af907e12f24625a3af8d6a7685ba421e3bfadc16e62f`, class major 69. Version/configuration/help checks exit 0; missing UGC projects and ordinary match startup exit 2. All five CI jobs passed for implementation `e50298b4f228319f8ccd2c3eed520573afb0a2ee`, tested tree `90f75340795767b20aea0e4ef1cc380bd01bb95e`, in [run 39345668802](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39345668802). All 65 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 78 configured reference generators passed; the build byte-compared repeated archives. All three platform jobs smoked the uploaded artifact and passed component/integration tests. No local-to-CI artifact hash identity or supported-client acceptance is claimed. Observed 2026-10-20T00:13:25.830724+00:00. Steam/master verification, remaining server commands, persistent results/profiles/achievements, bots, Tutorial, normal startup and supported-client acceptance remain incomplete. See [ADMINISTRATION.md](ADMINISTRATION.md).

