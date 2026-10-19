# Map Creator project pipeline

The Map Creator pipeline passes **6,339 component + 55 integration = 6,394 Java tests** across 116 suites, with zero Java failures, errors or skips. Java 25 `check build`, strict compilation, portability and distributions pass. A corrupt generated local JAR was rebuilt; final packaging verification reused the passed tests. All four generated codec/catalog outputs reproduce exactly in an isolated tree. The six reference source directories still match upstream `546c548780c400f21d73ca6713f86194ceb3c844` and all 262 frozen resource paths are unchanged.

The unchanged original-source checks report **52 passed, 0 failed and 3 skipped out of 55**; those three cases require retail baseplates absent from this workspace. Independent comparisons match 24 project traces containing 1,920 mutations, 216 mode validations and four VXL capacity samples. Original native encoders reproduce all four packet-68/98/102/118 literals. This is not a full Python-baseline or supported-client acceptance claim.

The CRC-valid application JAR is 2505161 bytes, SHA-256 `c1362d073322ea2fa2699f21cd75d9ad8ee48b2d4c7fed5b04981e5661ba8678`, class major 69. Version/configuration/help checks exit 0; missing-project checks and ordinary hosting exit 2. CI for this new checkpoint is pending. Full UGC hosting, production startup/services and the overall migration remain incomplete.

## Project, assets and storage

`UgcProject` owns all 19 native editor items, nine publishable modes, recovered priority/min/max objective rows, mode-scoped/shared placements, tolerant visible-mode/item/distance/newest removal, deduplication and the 8,192-object ceiling. Retail title/description fallbacks, unsigned 64-bit workshop handles, publishable tags, stored target mode, ground RGBA and palette overrides survive sidecar reopen. The generated table contains 448 prefab definitions from the unchanged constants; installed case-insensitive KV6 names are intersected with six palette tags. Nine terrain choices resolve their VXL/TXT siblings without copying client assets into the repository.

`UgcWorkspace` creates and reopens VXL/TXT/UGC siblings, protects existing authored VXL/UGC files and permits replacement of a lone orphan TXT. Its publish-root helper requires the exact `hosted_ugc` catalog leaf. Sidecars and individual VXL/preview files are replaced atomically; the three-file set is not a filesystem transaction. Canonical VXL snapshots are serialized on the world owner only for explicit save/shutdown; disk workers receive immutable snapshots. PNG signatures and the eight-MiB ceiling gate preview persistence.

## Capacity, editor ownership and native data

`UgcCapacity` counts implicit interior solids and nonempty 16-cube chunks using the reference VXL normalization, including centered legacy and empty-water columns. The retail admission predicate becomes full only when both 2,800,000 solids and 3,200 chunks are reached. An independent committed-topology subscription updates counters idempotently and detaches without replacing canonical observers.

`UgcEditor` owns socket-identity host election or externally authenticated account ownership, five combined prefab/object slots, real held-tool/distance/top-face placement gates, native object echo, registry type-29 marker creation/removal/replay, target/palette/title changes and ordered metadata/full-save work. Periodic metadata snapshots wait behind older writes; explicit requests coalesce and snapshot after preceding work, preventing an older checkpoint from replacing an acknowledged save. Failed checkpoints remain dirty for retry. Deactivation awaits a final full snapshot before detaching listeners and retiring marker state. Owner queue, worker, verified account resolution and reliable sends remain explicit host services.

The handwritten bounded codecs add UGCObjectives(68), InitialUGCBatch(98), UGCMapInfo(102) and SetGroundColors(118). Batches use native u32 counts/eight-byte raw-short records; reveal builders emit at most 512 rows. `UgcWire` prepares MapDataStart(54), packet-56 chunks and MapDataEnd(58) as one zlib stream from 1,048-byte source feeds. A future creator join must send this before validation/MapSync; the ordinary join path was not silently changed. CI now also regenerates and checks the UGC catalog, for 78 configured reference-generation commands.

## Offline commands

From the repository root after building:

```sh
java -jar java/build/libs/BattleSpades.jar --ugc-assets RETAIL_ROOT
java -jar java/build/libs/BattleSpades.jar --ugc-create RETAIL_ROOT DIRECTORY NAME TERRAIN MODE
java -jar java/build/libs/BattleSpades.jar --ugc-check DIRECTORY NAME
```

Create preflights the source VXL and carries inert baseplate atmosphere into a new sidecar. Check reports structural validity, object counts, occupancy and each publishability requirement; structural success does not imply complete publish objectives.

## Verification and remaining boundaries

Ten grouped component cases cover project validation/normalization/removal, source packet bytes and malformed lengths, zlib round-trip/batching, committed capacity, actual live-tool checks, socket replacement, native markers, shared backpack limits, queued save ordering, retries and final flush. Three filesystem/CLI integrations cover create/reopen/protection, installed palette filtering, orphan-TXT recreation, immutable canonical saves/previews and worker ownership. Existing UDP harnesses/goldens were not duplicated or modified.

The dedicated creator socket/life factory and join-hook attachment, full builder tool/prefab erase/paint orchestration, capacity enforcement in those mutation paths, skybox/fog and preview/save-message dispatch, authenticated service integration and supported retail-client acceptance remain required. These components do not yet provide a playable editor host. Tutorial, bots, identities/admin/persistence/results/achievements/master services and full production startup/end composition remain pending. Evidence: [ugc-pipeline-local-verification.json](ugc-pipeline-local-verification.json). CI: [CI_STATUS.md](CI_STATUS.md).
