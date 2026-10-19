# World creation system

The Java world-creation workflow passes **6,347 component + 60 integration = 6,407 Java tests** across 118 suites, with zero Java failures, errors or skips. Strict Java 25 compilation, `check build`, portability and distributions pass. A generated local archive was rebuilt after the packaged-JAR check detected corruption; the final full build and both complete test tasks pass. No assertion or deadline was weakened.

Fresh unchanged original-source checks report **113 passed, 0 failed and 3 skipped out of 116**, covering UGC, flare support, projectiles, snowball prediction, drill catch-up and map resources. The three skips require retail baseplates absent from this workspace. Four generated codec/catalog outputs reproduce exactly; all 262 frozen resource paths and the six source directories remain unchanged against `546c548780c400f21d73ca6713f86194ceb3c844`.

The CRC-valid application JAR is 2602821 bytes, SHA-256 `08ee5f09df13f9f7d7191b528bcbc308aaf67455335852066f6e0927bb756c2e`, class major 69. Version/configuration/help checks exit 0; missing projects and ordinary match startup exit 2. Dedicated world creation now starts with `--ugc-host`. CI for this new checkpoint is pending. Standalone creator operation uses offline socket ownership; verified-account ownership is supported through the embedding API. Live retail-client acceptance and the broader server migration are not claimed complete.

## Start, create and reopen a world

From the repository root after building:

```sh
java -jar java/build/libs/BattleSpades.jar --ugc-host --retail-root /path/to/AceOfSpades --project MyWorld
```

An existing project reopens its `.ugc`, `.vxl` and `.txt` siblings. A new project copies the selected genuine retail baseplate and atmosphere. Defaults come from `[map_creator]` in `--config PATH`; relative project, output and retail paths resolve beside that configuration. Supported options are `--project`, `--output-dir`, `--publish-root`, `--terrain`, `--target-mode`, `--prefab-set`, `--title`, `--description`, `--author`, `--retail-root`, `--bind`, `--port`, `--check`, `--overwrite`, `--offline` and `--control-stdin`. `--offline` documents the standalone mode; it is also the default. `--check` validates all nine VXL terrains and installed KV6 models without creating a project or binding UDP. An explicit CLI destination overrides the other configured destination. `--publish-root` must name the client's exact `hosted_ugc` directory; worlds go in its `maps` child.

The default bind is `127.0.0.1`, using the configured UDP port. `--bind 0.0.0.0` permits LAN clients. Creator settings force class 13/team 2, infinite blocks, zero combat damage and respawn delay, full source/canonical map loading and no bots/competitive mode transition. Configured player counts clamp to 2..24 and also bound ENet peers. General match startup remains independently gated; no creator mode is inserted into its competitive registry.

With `--control-stdin`, local operator commands are `status`, `title TEXT`, `mode MODE`, `skybox NAME.txt`, `save`, and `shutdown`/`stop`. Input is bounded UTF-8 text. EOF also stops the owner and saves. JVM shutdown requests stop and wait for the same final checkpoint; shutdown work stays on the world owner. Example parent-process input:

```text
title My finished world
mode ctf
save
shutdown
```

## Complete creator editing path

All class-13 editing tools now bind concrete runtime owners: blocks/lines and paint, native prefab build/erase, object placement/removal and nine-mode objective validation, primary/secondary brush, super-spade, drill 47, snowblower 48 and flare 22/packet104. The shared `HostedProjectileRuntime.Domain` connects the existing projectile engine, native terrain and blast owners to both competitive hosts and the creator, avoiding a second flight/warhead implementation. Authorization, origin/LOS/velocity/fuse bounds, launcher cadence/reload and stock decisions precede visual creation/debit. Creator snowballs preserve launch color, infinite stock and capacity admission, emit native type-32 prediction and canonical health-3 builds. Drill contact keeps the source's native type-10/20-damage bore and live-causer fallback; expiry uses the UGC type-33 blast profile. Flying entity positions/velocities also follow the engine for live snapshots and late joins. Disconnects cancel that actor's projectiles before a compact player ID can be reused.

Flares remain real type-13 light entities outside the VXL, with held-tool, coordinate, reach, LOS, collision, shared cadence and connected-terrain support gates. Infinite creator inventory incurs no ten-block debit. The existing damage/support behaviors remove unsupported groups and send normal entity destruction/light cleanup; projectile blasts can damage them. Original map chroma lights are restored using authored palette data. A creator session permits at most 2,048 flare lights and 2,048 active projectiles; native marker/entity IDs retain the existing registry bounds.

## Durable authored data and ownership

Snapshots freeze VXL, native project metadata and the light layer before yielding to the disk worker. A standard sibling `.json` map-metadata overlay merges native placements with RGB flare records, preserving unrelated atmosphere fields. The strict `.ugc` sidecar retains its 19-item native objective schema. This lets creator reload restore non-VXL light entities and lets normal map metadata consumers recover those authored lights. Periodic project checkpoints refresh overlay placements while keeping the last explicitly saved light set. Removed lights disappear on the next full save. Existing `.txt` atmosphere is retained. Each file replacement is atomic; the collection is not a multi-file filesystem transaction.

A project file lease rejects concurrent hosts and destructive overwrite while open. The host checks that prepared source/sidecar files have not changed before admission. Bind/attachment failure discards the unpublished editor without overwriting the world, and releases the lease. Normal close stops sockets/construction/projectiles, drains preview work, performs an ordered final snapshot, then releases workers/world/lease. World revision tracking includes pure recolors so edited published maps become modified before checkpoint/save.

Standalone sessions use first-socket owner election, source-compatible offline behavior, and the configured join password. Embedders can pass `CreatorHost.Access(ownerAccount, verifiedAccount)`; only an independently verified account equal to the configured owner receives host editing authority. Packet names, tickets and reused numeric player IDs do not populate that account field. Cloud identity/Steam/master services are separate server integrations and are not bundled with this offline launcher.

## Verification and limits

Three grouped component cases cover portable preflight/destination selection, protected project leases/bind rollback, and native-item/light/atmosphere snapshots plus checkpoint/removal. An existing editor failure/retry case also verifies pure-recolor modification tracking. Three additional UDP integrations cover native drill/snow/flare behavior and save/reopen/support-loss, verified ownership after compact ID reuse, and two actual packaged-JAR processes that create, edit, save, stop and reopen a world. The existing UDP harness gained bounded reliable-fragment assembly for a saved full source VXL; it was not duplicated. Existing competitive projectile tests run against the shared service extraction.

The standalone world-creation implementation is complete for the recovered offline workflow. Validation uses valid synthetic format fixtures, unchanged original-source checks and the native CI oracles. No supported retail game client or genuine installed asset collection was available for acceptance, so live rendering/menu/publish-client acceptance and capacity/soak acceptance remain unverified. The rest of the server migration (tutorial, bots, persistent identities/admin/results/achievement/master services and ordinary startup/end composition) is outside this completed world-creation workflow and remains pending.

Evidence: [world-creator-local-verification.json](world-creator-local-verification.json). CI: [CI_STATUS.md](CI_STATUS.md).
