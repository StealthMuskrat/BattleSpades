# Physical supply-crate components

Reference: `6216b771282d2d349ccd67ee58c378f035c83553`. Complete gameplay
hosting remains gated with exit 2.

PickupCrateBehavior preserves unconditional refill on touch, optional personal
sound, destruction and scheduled respawn. Full-health players still consume a
crate. The dead entry remains in the registry for respawn. Refill and sound
must return before consumption; drop-cue failures are isolated at the original
cosmetic boundary. Actual LifeRuntime healing, ammo restock and block-wallet
restock perform the authority and owner packet commit. Placement, refill type,
sound and drop parameters remain explicit authored composition.

On respawn an airdrop finds live support, rescues water columns to the first dry
surface in the original ring order, climbs built support and starts below a roof
when needed. Gravity integrates 1/60-second steps at 30 blocks/s². Within ten
blocks the parachute opens; velocity is multiplied by 0.75 after each step until
the crate is within two blocks of support. Missed ticks integrate elapsed wall time. Each tick
consumes at most 30 substeps and discards excess fall debt. Landing resets velocity,
fall state and support metadata. A settled crate checks support at 10 Hz; removed
support causes settling or dry rescue and a reliable ChangeEntity position update.
The optional context world is a concrete view of the canonical owned MutableWorld.

The unchanged-source oracle covers 8,192 registry/crate operations, including
native world edits, roofs, water rescue, all three actual Player refill methods,
full wallets, skipped/stalled/backward-time ticks, respawn/create/destruction and
cosmetic cue faults. It compares exact binary64 state and native wire bytes.
Another 512 native PlaySound and 512 native ChangeEntity cases compare authored
encoding and original decoding/re-encoding. Both negative fixed-point re-encoding
and Cython's unnormalized decoded positioning bint are retained. No source policy
method is replaced. Inputs, finite authored placements, clocks and IO are controlled.

A new real three-client UDP test verifies health/ammo/block refill, owner-only
health and sound, full-player consumption, sky respawn/parachute descent, live
late-peer replay and support-loss movement. Create/destruction knowledge and
reliable packet delivery are checked. This explicit component composition does
not establish the complete MapResourceService or gameplay tick assembly.

Local clean check/build passes 5,438 Java tests and 68 focused original
tests. All 58 configured generators reproduce outputs after a fresh native build;
215 recorded fixture/catalog/codegen hashes remain unchanged. Repeated CRC-valid
archives, packaging/smokes and isolated native probes pass. Publication and all
five current-head CI jobs are pending.
Stock resource placement/default assembly, objective pickup carry/drop, equipment
policy, projectile/corpse simulation, concrete mode/score/achievement/audio/debug
services, supported-game-client validation, capacity and soak remain pending.
The expanded original run passed 80 tests and failed the Zombie countdown cue
expectation: the test expects 7.5 seconds remaining, while the unchanged source
uses a 4-second lead. Source/tests remain unchanged and this failure is retained.
The complete original baseline remains incomplete/failing, including earlier VIP
evidence.
