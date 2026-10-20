# Standalone standard server and update operations

The Java launcher now owns an actual dedicated host: UDP/ENet transport, worker-prepared maps, owner-thread gameplay, administration and bans, achievements, voting, round endings, discovery, Java plugins, asynchronous logging, telemetry, map/mode changes and graceful shutdown. Production code uses its own bindings and does not package or depend on test fixtures.

This is an **offline, human-only standard/hardcore host**, not completion of the entire migration. Bot AI, tutorial/community variants, Revival registration/authentication/cosmetics, Steam/native integration and fleet orchestration remain incomplete. Configuration enabling unsupported services fails before binding a listener. Supported-client acceptance and capacity measurements remain outstanding. macOS testing is deferred until the full migration is complete.

## Running

Build with Java 25 using `cd java && ./gradlew check build`. Copy `java/config-standalone.toml` to `config-java.toml` beside the installation's existing `maps/`, `prefabs/` and `plugins/` directories. Paths resolve relative to the selected configuration file, not the process working directory. Supply a strong admin password to enable password login; the shipped placeholder leaves login disabled. Offline names and transport tickets grant no identity-based administrator privileges.

```sh
java -jar java/build/libs/BattleSpades.jar --serve --config config-java.toml --control-stdin
```

The listener defaults to `0.0.0.0:27015`. `--bind HOST` and `--port PORT` override the endpoint. `--shutdown-after SECONDS` is an optional bounded operating interval. `--serve` is optional for normal configuration-driven startup. The original `config.toml` remains unchanged; its enabled bots/Revival settings are explicitly rejected by this incomplete standalone profile.

A `READY` JSON line means plugin loading completed and the simulation started. Before that boundary, the socket services transport without accepting gameplay admission. Console ingress is bounded to 64 commands of at most 1,024 characters; replies use a separate bounded output worker. Logging and console output do not execute in an authoritative frame.

| Command | Behavior |
| --- | --- |
| `status` | Current endpoint, map, mode, players, occupied slots, ticks and telemetry |
| `map NAME` | Prepare a map on the worker and retain the accepted scene transaction |
| `mode CODE` | Reprepare the current map for a registered standard/hardcore mode |
| `restart` | Owned same-map round restart |
| `say MESSAGE` | Reliable announcement to current in-game peers |
| `stop` / `quit` | Stop frames, settle accepted scene work and drain services |

Shutdown also responds to the JVM signal hook. The owner finishes an in-flight frame, retires an ending, waits for an accepted map/restart transaction, deactivates the final mode, unloads plugins, drains durable result/achievement/review state and stops logging before closing sockets and world leases. A handler failure does not skip independent cleanup. A failed durable drain keeps the host's disposal guard active; it is reported instead of being labeled a successful shutdown. A timeout is an error.

Map preparation validates the map name, real path containment and the immutable VXL/metadata assembly. Failed preflight preserves the current match. Transition owners retain resources until reload/rollback finishes. Gameplay counters are sampled at retirement as well as completed ticks, so a partial final frame does not erase the old domain's mutation/repair totals. Health reports aggregate 600 ticks and subsystem timings; per-player source health-log text is not yet ported.

## Achievement cutover

The portable shared store defaults to `state/achievements-java.json`, selected by `[java] achievements_path`. Existing SQLite state is never opened as portable JSON. If a nonempty legacy database exists and the selected portable file does not, hosting refuses startup until an explicit export/import has been performed. Stop writers before exporting:

```sh
python java/tools/export_legacy_achievements.py --source state/achievements.sqlite3 --output state/achievements-java.json
```

This offline conversion tool opens the original database read-only and refuses to replace an existing output. The running Java application needs neither Python nor SQLite. See [HUD_ACHIEVEMENTS.md](HUD_ACHIEVEMENTS.md) for identity and shared-store semantics.

## Update checks and staging

The source schema-2 manifest and semantic-version precedence are ported to `ServerUpdates`. Startup checks run on a daemon, use the migrated source version from `VERSION`, remain quiet at ordinary log levels when offline, and log required/newer release notices. `[updates] update_check_enabled=false` disables them; `update_manifest_url` selects a mirror.

```sh
java -jar java/build/libs/BattleSpades.jar --update --config config-java.toml
```

`--manifest URL` overrides that invocation's manifest. The command returns 0 when current or successfully staged, 1 on failure. It checks mirrors in order, verifies exact size and SHA-256, reuses only verified cached downloads, cleans partial files, and stages into `update/staging/server-VERSION/payload/`. The marker retains the source preserve/mirror/remove instructions. Installation files and operator configuration are not replaced. Review the staged package before applying it: a source release manifest may describe the original server package rather than a Java distribution.

HTTPS is required, with exact localhost/127.0.0.1 HTTP allowed for local mirrors. Redirect targets are revalidated, credentials/fragments are rejected, manifests are bounded to 1 MiB, and downloads have bounded waits. Staging rejects absolute/traversing paths, duplicate files, more than 100,000 entries and over 2 GiB of extracted content. An unsuccessful extraction leaves the previous completed staging directory intact.

## Verification scope

`updates.json` records 400 unchanged-source version comparisons and 17 manifest observations. Integration checks use actual local HTTP mirrors, corrupted-first mirror fallback, verified cache reuse, CLI staging, unchanged installation/configuration, oversized responses and path-escape rejection. Dedicated-host coverage uses actual UDP peers across map/mode reloads, production console and timed process shutdown, startup refusal/recovery, and shutdown while a map preflight is accepted. Existing gameplay, transport and lifecycle suites remain part of the consolidated build.

## Checkpoint verification

Added production standalone owner/worker/service composition, a long-running launcher, bounded console control, accepted-transition shutdown, startup recovery, final retiring-domain telemetry and an offline human-only configuration. Ported schema-2 update checks, semantic-version precedence, bounded HTTP, mirror fallback, exact size/SHA-256 verification, cache reuse and opt-in archive staging. **6,463 Java tests pass** (6,375 component + 88 integration), zero failures/errors/skips. Real UDP peers survive map/mode reloads; shutdown settles accepted preflight; packaged console and timed shutdown pass, including the shipped MayanJungle map. Update evidence includes 400 unchanged-source version comparisons, 17 manifest observations and real local HTTP/CLI checks, including corrupt mirrors, oversized responses, traversal refusal and unchanged installation files. All 52 selected original update/capacity/simulation-order tests pass. Strict Java 25 check/build, portability and distributions pass. The CRC-valid JAR is 2992433 bytes, SHA-256 `dea963efb8ebd8867c45c389707d00904aa19c7663a43577f0eb444872a9b2cf`, class major 69. Eight original source directories and all 269 previous resources remain unchanged; one fixture brings resources to 270, with 84 configured source generators. All four required CI jobs—build, native ENet, Linux and Windows—passed for implementation `c39c1a5b1ce29647d5f8bfff9690d211c5d52ce3`, tested tree `b1ca68059e0ef9de8ee9eda2a6a77fe6fba5e12d`, in [run 39487222400](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39487222400). All 53 final steps succeeded or were intentionally skipped. Native impaired transport/loader checks and all 84 configured source generators passed; the build byte-compared repeated archives. Linux and Windows smoked the uploaded artifact and passed component/integration tests. No macOS job was launched; macOS acceptance remains deferred until the full migration is complete. No local-to-CI archive hash identity or supported-client acceptance is claimed. **The full migration remains incomplete:** bots, tutorial/community modes, online services, native integrations, fleet orchestration and supported-client/capacity acceptance remain outstanding. See [STANDALONE_HOSTING.md](STANDALONE_HOSTING.md).
