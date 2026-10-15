# Configured scene metadata and class policy

Behavioral reference: `6216b771282d2d349ccd67ee58c378f035c83553`.
Source modules: server/game_rules.py, class_data.py, class_selection.py,
mode_data.py, builders/initial_info.py, builders/state_data.py and the consumed
configuration/environment fields. This is a component checkpoint, not playable hosting.

## Rules and class selection

GameRules contains all 102 original Match Lobby definitions: key, category,
default, choices, mode tags, tool/class mapping and menu visibility. Immutable
instances preserve explicit settings independently of compatibility defaults.
Coercion distinguishes booleans, integer choices and floating choices; strings
support the source ON/OFF, numeric and percentage forms. Lowercase config aliases,
unknown-rule rejection, tool/class enablement and sorted disabled lists match the
source fixtures. Bare-server respawn/protection defaults remain 5s/0s; explicit
rules take precedence over legacy respawn/fall-damage fields.

ClassCatalog contains all 18 classes, slot/common tools, stock prefab names,
UGC-tool allow-lists and original movement/damage tables. Atomic stock selection
keeps the first requested enabled option in **source slot order**, fills missing
slots, removes cross-class tools, de-duplicates common items and preserves block,
flare and prefab ordering. Engineer Disguise does not acquire a second jetpack.
Prefab case checks preserve original selected spelling; unknown class fallback
matches the source. The UGC Builder's range-keyed tool table is exported correctly.

Rule-adjusted movement multiplies the original double class values by the
wire-rounded rule scale before the native float store. Multiplying an already
rounded float can produce a different result. Zombie speed applies to infected
classes only in Zombie mode; jump is unscaled, and the water-fall rule zeroes
only water landing damage. ConfiguredScene exposes the same profile and canonical
map gravity used by its declarations for the authoritative mover's caller.

The stock inventory tool gate checks life/spawn, recovered tool range, enabled
rules and committed membership. Mounted-gun entity authorization, active-tool
agreement, mode action gates and actual combat/deployables remain unimplemented.
Class/team authorization at admission remains the game domain's responsibility.

## Scene builders and session binding

ModeCatalog exports 43 metadata entries (13 base modes, 20 community entries,
10 Hardcore entries) and source aliases. These are wire metadata, not mode
implementations. Configured hosting of Hardcore is explicitly rejected until its
additional movement, rules, loadout and negotiated wire policies are ported.

SceneConfig reads the common server/game/team/world fields and Match Lobby rules.
ScenePackets builds InitialInfo and StateData from actual config, source CRC,
typed authored environment and current team state. It preserves map basename
versus display name, identity/IP bit patterns, feature flags, lobby scales,
disabled menus, ground palette, source lighting fallbacks, scores/classes and
empty join-time entity catalogs. Skybox values are validated plain .txt asset
filenames, with the source User_Grassland.txt fallback. Authored gravity is
canonicalized to the same 1/64 value for packets and simulation.

ConfiguredScene binds those builders to JoinSession and a separately owned
Gameplay domain. It validates the metadata/world CRC, normalizes the pending
stock selection and retains the reserved player ID through the session boundary.
The domain supplies current state, real admission, roster and input/lifecycle
callbacks. Its mode amendments run before the final source gravestone extension
and rule-disabled team-class filters. The binding enforces owner-thread access.
It does not own or close the caller's world.

The native loader probe now runs the **packaged application JAR**, with actual
root configuration and these production metadata builders. Its remaining player
domain is still FixtureScene from testSupport, excluded from the JAR. Real UDP
coverage checks configured menus, mode/CRC/map name, normalized admitted tools,
live gate, broadcast and teardown. The existing ENet probe checks both nominal
and loss/reorder/password runs, complete full/delta transfer, roster/cell catch-up
and same-peer scene reloads. This is not an original game-client session.

## Source evidence

The production TOML catalogs are exports of immutable source tables; policy logic
is handwritten Java. export_domain_catalogs.py regenerates the catalogs.
generate_scene_golden.py independently executes the original policies/builders
and compiled original packet/world modules:

- 1958 rule coercion inputs, including expected rejections and exact value types.
- 336 atomic class selections, including disabled tools, forged equipment,
  unknown classes, prefab spelling and UGC-tool allow-lists.
- 864 rule-adjusted walk/sprint/sneak profiles: 10368 original movement ticks,
  exact exposed double bits after each 12-tick run, all classes and speed choices.
- 99 InitialInfo/StateData pairs (33 non-Hardcore metadata entries, three
  environments/rule variants), compared byte-for-byte.
- Skybox validation cases, amendment ordering, config precedence, ownership and
  configured session/real-UDP composition checks.

The focused original regression subset passed 78 tests. The complete original
suite remains incomplete as documented in PYTHON_BASELINE.md.

CI additionally rebuilds original bytes/packet/glm/VXL/world, regenerates source
fixtures and production catalogs, and requires an empty diff. The normal Java
build uses committed fixtures and needs no Python or native compiler.

## Remaining boundary

This does not implement actual player identity/name handling, team assignment and
balance, per-mode admission restrictions, threat-aware spawn resolution, health,
death/respawn, live input scheduling/history/reconciliation, equipment activation,
WorldUpdate construction, entities or complete modes. Active-mode policies must
be ported behind the supplied hooks. Map/UGC-specific selection extensions and
sidecar discovery/parsing are also pending; Environment is typed caller input.
No game-client or capacity/soak claim is made. Ordinary hosting remains disabled.
