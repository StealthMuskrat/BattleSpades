# Player-name and pre-life admission policy

Reference: `6216b771282d2d349ccd67ee58c378f035c83553`.
This checkpoint implements checks before a new life. It does not create a playable
player domain, verify a Steam/Revival identity, or implement spawn/death/respawn.

PlayerNames ports server/player_names.py: NFKC display normalization, invisible
removal, Python whitespace handling, valid UTF-8 truncation to 15 bytes, staff and
reserved-tag checks, configurable reserved names, bot-prefix impersonation checks,
two-pass confusable folding, rn/vv pairs and collision suffixes. Admin-name
collisions use the neutral Player fallback. Bots and humans share the same live
name set; removing an occupant immediately releases its name. Bot allocation keeps
the prefix while shortening the base/suffix. No persistent global name registry
or locale-sensitive matching is introduced.

Python 3.12 uses Unicode 15, whereas Java 25 has newer Unicode tables. NameUnicode
therefore implements the source normalization/casefold/properties using a pinned,
48,553-byte table export and algorithmic Hangul decomposition/composition. Newer
JDK character assignments cannot change a name's visibility or collision key.
The Unicode data permission notice is packaged in META-INF/licenses/unicode-15.txt.
No Python, ICU or native runtime is needed by the application.

AdmissionPolicy ports Connection._resolve_join_team, _balance_join_team and
_enforce_join_class, plus equipment.is_class_selectable/fallback_class_id:

- Enabled spectators keep team 0 and bypass playable-team/mode selection hooks.
- Neutral/unknown requests fall back to team 2. Teams 2/3 are the playable wire
  and internal IDs; there is no 0/1 team translation.
- Auto-balance counts all existing playable-team occupants, excludes a stale self
  row, clamps its threshold to at least one and changes side only at that threshold.
  The source notice flag is set for recognized requests, not invalid-team fallbacks.
- A trusted host assignment of 2/3 bypasses balancing; actual mode team hooks still
  run. Packet forced_team never grants that authority.
- Class rules run before ordinary mode-menu restrictions. Custom mode policies and
  UGC runtime can own the finer selection policy. Fallback follows mode-list order,
  then Classic, then sorted stock classes. It replaces the entire selection with a
  freshly normalized loadout. Spectators and the source no-fallback case retain
  their selection. The UGC Builder fallback removes ordinary prefab tool 23.

ConfiguredScene now obtains an immutable occupant/verified-assignment/mode context
from its Gameplay owner and applies these checks before admission/roster creation.
An admissionResolved callback supplies the source rebalance decision to the domain;
the eventual domain must deliver its notice after reveal. The pending packet-13
class is normalized independently, matching its source pre-join cache: an invalid
cached class falls back to Classic, never a later packet-15 class. SceneConfig
reads teams.auto_balance/balance_threshold, conduct.reserved_names and
bots.name_prefix, including the source six-character printable-ASCII prefix bound.

The domain still owns authenticated nickname substitution, identity-required/join
ticket rejection and asynchronous scene-generation checks, admin grants, actual
mode hooks, team membership, map/UGC selection extensions, real Character creation,
spawn geometry/threat scoring, reveal notices, equipment/input timing and lifecycle.
The UDP/native probes use a test-only fixture domain; a metadata catalog entry
does not implement a mode. Normal hosting continues to return exit 2.

## Evidence and reproduction

`export_name_unicode.py` exports immutable tables only. With Python 3.12:

```
python -S java/tools/export_name_unicode.py
python -S java/tools/generate_admission_golden.py
```

The second generator executes original player_names functions and unmodified AST
nodes for the three connection methods and their team-conversion helpers. This
avoids eager unrelated launcher/network imports; original rules, equipment and
class-selection modules execute normally. It does not substitute policy functions.
The existing development packet extension is required to import equipment.

Java compares 1,112,064 Unicode scalar values in 272 framed SHA-256 groups, including
properties, NFKC/NFKD, skeleton and safe-base results. Another 18,568 vectors cover
844 names/normalization sequences, collisions, admin/bot/reserved handling and
Unicode combining/Hangul cases. Team policy has 6,192 source vectors, including
every byte-valued team and invalid boundaries. Class policy has 14,256 vectors
across all 33 non-Hardcore metadata modes, class-rule variants and custom/UGC flags.
Hardcore configuration remains explicitly unavailable.

A four-client real-UDP test checks reserved/confusable name allocation, neutral
fallback, automatic balancing, forged forced_team, spectator preservation, class
fallback and StateData-reserved identities through ConfiguredScene/JoinSession.
Fixture compression is deterministic gzip; fixtures are excluded from the JAR.
CI regenerates the table/fixtures/provenance and requires an empty Git diff.
