# Movement packet admission and position reports

Local Java check/build passes **6,276 component + 41 integration = 6,317 tests**,
with zero failures, errors or skips across 112 suites. **852 focused original-source
regressions pass freshly**: the preceding 795 plus the complete reversed movement engine
module. Three grouped component tests were added. Existing ClockSync session coverage and
the existing production-factory two-peer UDP scenario were extended.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2079943 bytes, SHA-256
`0254c72e2cecbfd3fc86a1a9dda90f9f91996624e04cfcbc7c96cb370a0c7c07`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

MovementPacketRates implements the original handler's independent ClientData, ClockSync and
PositionData budgets. It uses the maximum elapsed monotonic/simulated time, the original
2-second hitch horizon and allowance that does not shrink while the backlog drains. Source
defaults are 240/64, 4/16 and 240/64 respectively. The current original AntiCheatConfig has no
packet-rate override fields, so the hosted factory uses these effective source defaults.
Explicit policy callers retain the source's bot and nonpositive-rate bypass. Per-actor
budgets survive respawn and disappear with actor replacement; no global peer-slot bucket
can leak into a replacement life. Local lane snapshots expose accepted/refused totals.

StandardMatchDomain admits ClientData before LifeRuntime can mark packet receipt or record
its timeline, aim, buttons, tool or crouch actions. ClockSync has a dedicated JoinScene /
ConfiguredScene hook at JoinSession's early clock branch, before the immediate unsequenced
reply. The authenticated reserved identity selects the canonical actor. Pre-admission clocks
have no player budget, matching the source None-player bypass. An already-detached actor
also has no player budget; the old scene's clock path cannot retarget a new actor. The existing
reply echo, loop bias and queue failure metrics keep their original roles.

LivePositionReport decodes runtime packet 116 independently of the generated packet codec:
12-or-more payload bytes select three raw floats, 6..11 select three unsigned sign-magnitude
fixed coordinates, and accepted tail bytes are ignored like the source. Truncated admitted
PositionData increments the session malformed counter and is ignored without disconnect,
matching the source runtime handler's decode-error isolation. NaN/infinity remain representable at this
wire boundary so the domain can count/report and ignore them before a physics Vector exists.
JoinSession routes packet 116 through the canonical admitted player to its dedicated scene
hook. Position traffic spends its own rate budget before finite validation, matching source
ordering. It is consumed there rather than forwarded to the residual packet service.

PlayerPositionReports owns accepted report count, wall timestamp, last sample and exact
sample-to-body drift vector/distance. Receipt records telemetry without moving the body or
refreshing ClientData starvation time. Source spawn replaces sample/drift with the new spawn
point while retaining report count and the last update timestamp. A new connection starts
with empty counters. The factory parses the existing game.movement_authority setting with
the source's case-insensitive client selection and default/invalid server fallback. Existing
InfantrySimulation settings callers default to server authority. Client mode applies a
fresh sample after physics, flight and fall processing, before afterMove observers and eye
history capture. The original <=1-second age boundary and positive timestamp gate apply;
velocity is retained. Server mode uses reports as telemetry. The source's dormant soft-drift
helper remains unbound in the live path.

Required movementReport ports receive every rate/nonfinite rejection with source detail;
the shared anticheat owner still supplies cross-policy accounting and diagnostic log limiting.
Other existing input/effect, mounted-device, paint and observation owners remain required.
Device placement/retirement, projectile/construction/corpse factories, bots/director/shared
identity allocation, auth/master, persistence/results/achievements, ending/reset/HUD factories,
full bootstrap, remaining modes and supported-client/capacity/soak/baseline reconciliation
remain pending. This batch completes these ordinary movement-network boundaries, not hosting.

Grouped component traces cover flood limits/independent lanes, retained hitch allowance,
simulated-clock refill/bypasses; rate rejection before every immediate/timeline effect,
respawn retention and slot reuse; and both packet layouts/nonfinite rejection plus fresh/stale
pin timing, velocity and report lifecycle. Existing session clock coverage now checks the
admitted clock gate and PositionData routing without copying the loader scenario. The existing
factory UDP trace retains all prior movement, input/tool, HP/ammo, profile, terrain, repair,
entity/resource and rollover assertions. It additionally enables the real client-authority
setting, observes a real ClockSync budget/reply, sends PositionData through the actual host,
applies it on buffered movement, rejects NaN without dropping the peer and proves new-scene
report counters start empty. Existing deadlines and source policies remain unchanged.
