# Owned shot commit and player hit effects

Behavioral source: `6216b771282d2d349ccd67ee58c378f035c83553`, principally
`server/combat_runtime.py`, `server/player.py`, the weapon/class catalogs and
the original Cython packet codec. The new components compose the earlier timing,
validation, firing-pattern, pellet and read-only rewind work. Normal hosting
still exits 2: concrete gameplay domains and supported-client gates remain open.

`CombatRuntime` binds packet 6 to the actual connection-owned PlayerLife through
LifeRuntime. Claimed shooter, damage and penetration never select a victim or
damage amount. It preserves source mode/tool/objective and mounted-gun admission
boundaries, then consumes the actual wallet/cadence before disguise, mode,
profile/score, aim-observer and protection transitions. Gun feedback excludes the
predicting owner and uses sequenced unreliable delivery; melee sends no packet 8.
Bot shot stimuli retain source radius/lifetime. Seeded pellets resolve inside the
scoped rewind and tally cleanup. Ordinary respawn retains source burst/spread
state; disconnect forgets it before identity reuse. Shot counters belong to the
life owner and survive respawn.

Mode permission, objective state, mounted-gun registry, enemies, profile/scoring,
statistical aim analysis, achievements, stimuli, presentation, terrain, entity and
corpse effects are required interfaces. There are no permissive production
implementations or empty production registries. Tests provide explicit controlled
ports and verify their invocation/order; they do not prove those concrete services.
Upstream equipment selection and input rate/conduct authorization remain pending.

`HitEffects` resolves the actual nearest hit and routes player damage through
LifeRuntime's existing accepted-health/death path. DirectDamage retains all 65
weapon profiles, body-part damage, victim class, active pack and Hardcore scaling
without early rounding. Hardcore here is trusted test/domain state, not a port of
that mode's lifecycle. Frontal riot-shield mitigation requests its impact before
health admission, including protected victims. Actual attachment construction is
still pending. Melee preserves terrain-capped player selection, eye reach,
non-headshot damage and shield-bash knockback. Raw and facade velocity comparisons
cover the source's no-op branch as well as actual body writes.

Only an actual HP decrease produces profile-hit/tally/blood feedback. Entity hits
send reliable packet 20 before the required damage callback. Corpse and block
hits call their required concrete domains. Tally aggregates one accepted trigger,
per-tool shots/hits/headshots and pellet hits, ignores bots, and preserves seed
skew reporting and source identity guards. Diagnostic/aim/achievement fault
boundaries retain source isolation; effect failures still clear active resolution
tallies and rewind scopes.

Packets 9 and 20 now have typed codecs; 77 distinct application IDs are supported.
Fixed-position negatives retain the source's asymmetric, non-idempotent
decode/re-encode behavior. Golden checks compare the original encoded, decoded
and re-encoded values separately rather than assuming round-trip identity.

| Unchanged original comparison | Operations |
|---|---:|
| Direct player/entity damage and victim multipliers | 500,240 |
| Shield mitigation and knockback | 14,000 |
| Hit-effect packets and remote shot feedback | 12,000 |
| Player hitscan effects with actual health/death | 1,088 |
| Melee effects, reach and actual raw/facade velocity | 1,088 |
| Complete shot-handler commit ordering | 800 |
| Trigger tally and seed observations | 11,200 |
| Total new source operations | 540,416 |

Original policy functions execute unchanged. Generators control clocks, domain
inputs and IO; profile observation records calls without replacing policies.
Health/commit fixtures use the actual original Player and native world. Shield
attachment requests are observed with no registry; concrete attachments are not
proved. Mode/profile/score/achievement callbacks are observed boundaries, not full
service parity. Numeric damage, wire and aggregate checks are exact; the earlier
geometry's documented 16-ULP numeric intersection bound remains in force, with
exact hit/part/visibility/probe classifications.

Two additional two-client UDP tests use the real loader, sessions, owned lives,
packet decoder and automatic CombatRuntime dispatch. One rejects a forged origin,
ignores forged identity/damage, spends once, rejects a duplicate logical shot,
delivers reliable health/death and unreliable remote feedback, then kills on the
next admitted shot. The other moves a target with production infantry simulation,
uses real peer RTT and input-label arrival dates, proves the live-body ray misses,
then damages the historical target without changing its live position/velocity.
Both use explicit test mode/effect ports and trusted equipment setup. They are
not supported-game-client rounds; native packaged-loader probes retain FixtureScene.

Remaining combat work includes concrete terrain damage/digging/shades/collapse,
entity/corpse registries and effects, detached projectiles, full profile/scoring/
award/anticheat services, actual modes and upstream input/equipment authorization.
Identity, bots, editor/tutorial, plugins, administration, persistence/discovery and
supported-client/capacity/soak gates also remain. The full original Python baseline
retains its documented incomplete runs and VIP discrepancy.

Local verification passes 4,956 Java tests and 223 focused original tests.
All 162 fixture/catalog hashes reproduce after a fresh original native build.
See [CI_STATUS.md](CI_STATUS.md) for exact evidence.

Implementation `76a80d9f4b7840ab6730157215e69995d66323fd` and tested tree `07ce07657fc3a84285fd212673dcef0e8d7ccab2` passed all five jobs
in [run 39078168462](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39078168462). This includes original-source fixture regeneration,
repeated archive comparison, native probes and one uploaded JAR tested on three
platforms. Local verification passes 4,956 Java tests and 223 focused original
tests. The preceding numeric geometry bound and required concrete-domain/gameplay limits remain in force.
