# Map metadata and layout assembly

Reference: `6216b771282d2d349ccd67ee58c378f035c83553`, especially
server/map_metadata.py and the common InitialInfo/StateData builders.
This checkpoint supplies map data to the existing placement and scene components.
It does not implement the objectives or lifecycle of the modes named in that data.

## Loading and priority

MapMetadata.load reads an exact VXL path's siblings in source order:
`<stem>.json`, `<stem>.ugc`, `<stem>.txt`, `<filename>.vxl.json`.
The first accepted document supplies the source path. The first occurrence of
each key wins, including explicit null/empty values; later documents fill gaps.
Duplicate keys inside a JSON document use their last value. UTF-8 BOM sidecars
are supported. Invalid or unreadable documents produce diagnostics and are skipped.

MetadataDocument is a pure-Java bounded data reader. It reads JSON and the legacy
allow-listed top-level assignments, literal list/tuple/set/dictionary containers,
quoted strings, numeric unary signs and numeric addition/subtraction. Unknown
keys and non-inert assignment values are ignored. Imports, calls, attributes,
names and executable statements are never evaluated. This is not a complete
Python parser or an implementation of arbitrary Python metadata modules; unusual
Python syntax outside the tested literal subset remains unverified. Container
nesting and counts, total nodes, document bytes and integer lengths are bounded.
No Python, native library or external parser executable is needed at runtime.

The loading boundary caps each sidecar/catalogue at 8 MiB and a VXL at 64 MiB;
reads remain bounded if a file grows after its size check. JSON nesting is capped
at 64, legacy literal nesting at 16, and legacy containers at 4096 members.
Non-finite placements are omitted instead of constructing an invalid physics
position; non-finite environment fields use their safe source defaults. These
explicit loading bounds/finite-coordinate checks are stricter than the original
unbounded JSON reader and some malformed-coordinate paths.

## Canonical data

The immutable result contains the active canonical metadata mode, presentation
environment, spawn layout, team spawn points, bases, neutral objectives,
Occupation base/bombs, Diamond bases/capacities, Demolition thresholds, crate
and static-flare specifications, ambient streams/emitters, static-light palette,
achievement regions, display limits, layout provenance, retail compatibility and
player caps. Nested community metadata is retained as immutable inert data.
Storing a placement/specification does not spawn an entity or award an achievement.

Legacy spawn boxes keep centered dimensions and the extended bottom-of-map floor.
CTF/Classic CTF replace the capture bases only when two valid bases survive.
Territory Control can replace the neutral hills. Occupation replaces each team's
spawn independently. Zombie areas lead the default Blue shore as a dry complement;
survivor areas replace Green's default. UGC rows are appended after these overrides,
retain source item extents/team ownership and filter by the canonical mode.
Aliases, Hardcore base-mode matching and unknown custom tags follow the metadata
helper rather than treating a tag as a playable mode implementation.

Stock skyboxes, gravity, fallback team regions, fog, ambience and UGC item dimensions
come from a separately exported source-data catalog, not from voxel colors.
Explicit safe skybox aliases retain their priority. A project's `.ugc` skybox
supersedes an immutable `.txt` fog pin using the selected skybox's fog color.
Gravity is canonicalized once to the StateData 1/64 value. Ambient names use the
source allow-list; emitter counts, palette entries, vectors, colors and attenuation
are validated. Ambient point rounding retains Python's ties-to-even behavior.
Pinned Unicode 15 whitespace/case-folding handles names independently of JDK updates.

retailModePool reads the external recovered catalogue, preserving playlist order
and filtering invalid map/mode pairs, classic/mafia flags and unreleased entries.
It is a selection component, not a running map-vote implementation.

## World and scene boundary

MapAssembly.load validates metadata and VXL format before returning a new immutable
map assembly. The exact source VXL CRC is shared by terrain and environment.
The caller creates an owned MutableWorld, then requests prewarmed SpawnGeometry
from the assembled layout. A mismatched map CRC is rejected.
Missing/invalid maps fail this boundary; live replacement, missing-map generation
and mode transition orchestration still belong to the pending gameplay domain.

ConfiguredScene's metadata constructor rejects a layout loaded for another mode
or a CRC different from the owned world. The existing source packet builders use
the parsed environment directly. Identity verification, concrete mode amendments,
Character creation/death/respawn, environment/entity emission and full player
simulation remain pending. The ordinary launcher still exits 2 for hosting.

## Evidence

generate_metadata_golden.py executes the original loader and builders without
replacing policy bodies. It supplies committed stock/community sidecar contents
and synthetic layered, UGC, legacy, malformed/coercion and Unicode/numeric inputs.
332 input groups produce 785 complete metadata comparisons, 659 exact packet pairs
and 4316 map-pool comparisons. Every compared coordinate/environment double keeps
its source value; packet comparisons are byte-for-byte. Source and input hashes
are recorded in metadata-provenance.json. CI regenerates these fixtures and the
97-row production catalog and requires empty diffs.

The focused original metadata/retail test suite passes 71 tests, with four skips
for missing retail `.txtc` files. The Java suite exercises the map assembly's
format/CRC/error boundary, immutable output and inert parsing limits.
The real-UDP terrain test now reads layered sidecars, verifies transmitted gravity,
fog and palette, then revalidates a placement after terrain removal. The packaged
native loader reads production metadata and checks changed gravity after scene
reload in nominal and impaired/private scenarios. Its player lives remain fixtures.
No original-game-client, full-mode, capacity or soak result is claimed.
