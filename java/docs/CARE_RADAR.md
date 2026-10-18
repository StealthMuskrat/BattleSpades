# Medpack and radar behavior components

Reference: `6216b771282d2d349ccd67ee58c378f035c83553`. Complete gameplay
hosting remains gated with exit 2.

MedpackBehavior preserves team checks, the current health cap, per-pack uses and
the shared per-player Hardcore cooldown. Hardcore healing requires three seconds
since damage and two seconds since the last allowed pack. The cooldown advances
before healing; uses decrease only after the actual heal returns. Negative and
zero heal amounts retain the source behavior. LifeRuntime performs the real HP,
healing evidence and owner-only SetHP commit. Spawn clears the healing cooldown.
Live mode, wall clock and healing are required ports. The component does not
implement the complete Hardcore mode lifecycle.

RadarStationBehavior starts its lifetime on the first tick and updates its live
wire fuse on subsequent ticks. Expiry, accepted damage, explicit replacement and
canonical support loss share the idempotent teardown. Team count and a matching
owner slot are released before entity destruction/removal. A stale slot pointing
to another entity is retained. Radar detection remains the original client's
responsibility; this component adds no whole-team visibility packet.

The source oracle executes unchanged MedpackBehavior, RadarStationBehavior,
Player.heal/damage, registry and native world/canonical support methods. The
original radar-count and entity-context methods are extracted as unchanged ASTs.
Clocks, rules/mode flags, authored parameters and IO are controlled. No policy
method is replaced. The fixture covers 1,024 lifecycle/healing operations and 32
actual Player.heal reliable-IO fault cases. Exact state, cooldown timestamps,
health, uses, fuse, owner slot, team count, callbacks and wire bytes are compared.
Canonical listener errors must remain zero. A heal send failure preserves the
already committed HP/cooldown and leaves pack uses unchanged, as in the source.

A new real three-client UDP test composes the production registry, medpack,
radar, life and owned-shot components. Two packs share a player's cooldown; only
the owner receives healing packets. A late peer receives the remaining radar
fuse and no expired pack. An owned shot consumes ammo, destroys the radar and
releases its owner slot and team count through reliable packets. Authored
placement, live Hardcore healing policy and scene reveals remain explicit test
composition; complete equipment authorization and automatic server-loop assembly
are not established by this test.

Local clean check/build passes 5,373 Java tests: 5,350 component and 23 real UDP,
with no failures, errors or skips. 109 focused original tests pass. All 56 configured generators reproduce
outputs after a fresh original native build; 210 recorded fixture/catalog hashes
remain unchanged. Repeated CRC-valid archives, packaging/smokes and isolated
native probes pass. All five jobs pass in [run 39084849726](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39084849726); see [CI_STATUS.md](CI_STATUS.md).

Remaining work includes physical pickups, placement/equipment authorization,
projectile/corpse simulation, concrete modes/score/achievement/debug services,
complete gameplay binding, supported-client validation, capacity and soak. The
full original baseline retains its documented incomplete runs/VIP discrepancy.

Implementation `18f5d79818cefad2f96f5ee1a2f222862cbf989e`, tested/published tree `db584d7612c7986e694f16becd1b057cbfad58ef`. The CI build compares repeated archives and tests one uploaded JAR on three platforms. Full gameplay and supported-client/capacity gates remain pending.
