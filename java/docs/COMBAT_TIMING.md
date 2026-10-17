# Combat timing and read-only target rewind

Source: `6216b771282d2d349ccd67ee58c378f035c83553`, principally
`server/player.py`, `server/action_clock.py`, `server/lag_compensation.py`,
`server/combat_runtime.py` and the original Cython packet codec.
This checkpoint implements firing/reload timing, pellet expansion and target
rewind components. Shot-origin/aim validation, hit resolution and complete combat
remain pending. Ordinary hosting continues to exit 2.

`PlayerFire` uses the existing owned life wallets and shared ActionClock. It
preserves source peek/admit ordering, automatic-reload label floors, magazine and
single-round reload chains, interruption/grace, per-gun wallet switching, bot
arrival cadence and launcher reload-sound relays. A new life resets the source
shot clocks and raw-tool state; it deliberately retains the original pending
completion and auto-reload-floor fields. Tool selection is a trusted internal
transition here, not permission for a client to select arbitrary equipment.

Owned housekeeping now advances the real reload timer and broadcasts reliable
completion. LifeRuntime handles packet 76 using the connection-bound player and
current selected tool. Client player/tool bytes do not select another life or
wallet, and client completion flags never complete a reload. Reload requests
retain the original handler's eligibility rules; firing still needs the separate
unported shot-validation/commit path. Actual ENet RTT and variance reach the
ActionClock before packet dispatch and at session pulses.

CombatCatalog exports all 65 original weapon profiles, 12 retail bloom curves,
two zoom overrides and source tool classifications. PelletSpread reuses the
existing Python-compatible MT19937 implementation, masks the supplied seed to
eight bits, takes three draws per pellet (including single-pellet guns), and
preserves source normalization and bloom decay. It reads the shot's own zoom
history and plausible label spacing rather than an overtaking zoom-off input.
Its per-player state has the source explicit forget boundary; expansion alone
does not admit a shot, spend ammunition or apply damage.

LagCompensation records a 128-slot hitbox history before movement under
`serverLoop - 1`, including physical crouch/hover, life generation and teleport
epochs. Rewind views interpolate only valid same-life, same-epoch samples and
never alter the live body, terrain, health or inventories. Contexts cache by
target identity. Nested ShotOwner scopes restore the preceding context even when
a handler fails. Arrival dates from actual ClientData history bound additional
rewind for a delayed reliable shot; RTT, claimed observer header, server caps and
the original half-tick threshold determine the view. Diagnostics remain log-only
and cannot reject a shot if their observer fails.

Packet 6 is now typed as ShootPacket, preserving signed scalar fields, binary32
vectors and source reserved-flag normalization. These decoded fields are
untrusted claims. The protocol layer does not turn claimed damage, penetration,
origin, identity or a valid rewind window into an authorized hit. There are now
75 distinct supported packet IDs; the earlier README's count of 71 was stale.

PlayerLife owns the source bot identity flag and optional last-rewind observation.
Snapshots use that owned identity; this does not implement bot AI or population.
The remaining presentation/effect/entity domains are still required inputs.

| Original-source comparison | Operations | Scope |
|---|---:|---|
| Target history and views | 16,477 | Ring eviction, death/life/teleport barriers, fractional samples, orientation normalization and cached live/rewound identities |
| Rewind decisions | 7,000 | RTT/caps, snapshot bounds, nearest arrival-label dating, half-tick cutoff and exact diagnostic values |
| Connected pre-movement history | 1,296 | Original Player/SimulationRuntime/native movement and flight, physical crouch/hover and respawn generations |
| Player firing/reload | 24,882 | 33 traces of unchanged original Player and ActionClock methods, timing/label floors, gun wallets, raw/legacy tools and launcher reload relay |
| Seeded pellets | 16,728 | 17 gun traces; original bloom, Python random stream, own-frame zoom, time/label gaps, switching and forget boundary |
| ShootPacket | 768 | Original Cython generate/read, signed values, float32 stores, all flag bytes and reserved-bit normalization |

The total is **67,151 source operations**. Clocks, trusted selection, peer/domain
inputs and wallet states are controlled; no original policy method is replaced.
The lag generator observes original diagnostic calls through `sys.setprofile`.
Generators retain deterministic gzip bytes and source/fixture SHA-256 provenance.

Two additional real UDP tests compose the production loader, owned lives and
simulation. One delivers a delayed ShootPacket into an explicit test handler,
uses actual ClientData arrival dates and ENet RTT to select a read-only target
view, and verifies that target physics/HP and shooter ammunition remain unchanged.
It does not resolve a hit. The other checks bound reload identity/tool, ignored
completion claims, actual RTT/variance forwarding and reliable start/completion
messages from owned housekeeping. Trusted equipment/wallet setup and mode/effect
ports remain explicit test inputs. Native packaged-loader probes retain their
separate FixtureScene scope.

**152 focused original tests pass**, including the original impaired-link shot
registration lab. That lab verifies the reference server; it does not establish
the Java server's complete shot resolution or supported-game-client behavior.
The full Python baseline retains its documented incomplete runs and VIP discrepancy.
See [CI_STATUS.md](CI_STATUS.md) for exact checkpoint build/publication evidence.

Still pending: source shot/aim/origin gates, assault/minigun grouping, authoritative
hitboxes and hitscan/melee/terrain/entity resolution, projectiles, damage rules,
awards and complete mode effects; concrete modes, identity, bots, editor/tutorial,
plugins, admin/persistence/discovery, supported-client gameplay and capacity/soak
gates. A running component test or original-reference lab is not a playable round.
