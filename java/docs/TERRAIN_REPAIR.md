# Bounded terrain repair scheduling

Source and delivery components; full gameplay-loop integration remains open.
Source: `6216b771282d2d349ccd67ee58c378f035c83553`, `server/terrain_repair.py`.

TerrainRepair implements the original regular rejected-prediction lane and the
faster collapse-confirmation lane around the existing owned canonical health/
shade packets. It retains quiet-period retiming, duplicate/rebuilt-cell handling,
per-lane cadence and budgets, and queue overflow priority. Collapse cells enter
in deterministic six-neighbor surface-first order; overflow preserves their
visible shell before unsent interior. No stale topology/color snapshots are stored.

Only recipients with an admitted player and an in-game scene receive packets.
Each recipient's own player ID is used in canonical block packets. No eligible
recipients clears both queues. Settings disable/reset behavior and per-packet
send-fault isolation retain source counters for drops, queue peak, cells, sends
and failed sends. The service is owner confined and requires concrete settings,
recipient and sender ports; integration into the full gameplay loop remains open.

The unchanged original service, actual native world and canonical packet builders
produce 3,072 stateful operations. Tests compare exact bytes/order and counters
across bounded overflow, delays, recipients, enable/reset, current world mutations
and injected send failures. Original policy methods are not replaced.

An expanded existing real three-client UDP terrain test binds the production
repair service to actual owned lives and reliable host delivery. It verifies
delayed exact-air confirmations after collapse, per-recipient actor identity,
quiet-period rejection repair and the latest painted color/damaged health. Test
policy/effect ports and explicit scheduler ticks remain visible; this is not a
supported-game-client convergence or full-mode/capacity/soak result.

Local verification passes 5,081 Java checks and 53 focused original tests. All
193 fixture/catalog hashes reproduce after a fresh original native build. Native
probes, repeated archive comparison and portability/smoke checks pass. See [CI_STATUS.md](CI_STATUS.md) for exact evidence.

Implementation `d6990d6e33181547bfa38068bb228372245e79bb` and tested tree `64cf08a198b804bda991d1886d15cff051c3ff8a` passed all five jobs
in [run 39080190230](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39080190230). This includes original-source fixture regeneration,
repeated archive comparison, native probes and one uploaded JAR tested on three
platforms. Local verification passes 5,081 Java tests and 53 focused original
tests. The preceding numeric geometry bound and required concrete-domain/gameplay limits remain in force.
