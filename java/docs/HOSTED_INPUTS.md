# Hosted held tools and fresh input actions

Local Java check/build passes **6,273 component + 41 integration = 6,314 tests**,
with zero failures, errors or skips across 111 suites. **795 focused original-source
regressions pass freshly**, including complete movement-anticheat, ammunition, score-event
and reversed-WorldUpdate modules. Two grouped component tests were added; the existing
production-factory UDP scenario was extended. Existing policy/golden matrices were retained.

All 265 frozen paths are byte-verified unchanged. No new generator, policy oracle, production
dependency or copied UDP scenario was added. The 77 configured generators/native-build
evidence is reused locally; CI independently rebuilds and reproduces all 77. Two CRC-valid
local archives are byte-identical: 2069837 bytes, SHA-256
`083adcac909e4f6e14cad039548a3d24eac1a012a27d1c4d196d40d9f0c597cb`. Class major 69, packaging/licenses, version/config smokes and all
four fresh native transport/packaged-loader profiles pass. The native loader uses FixtureScene;
ordinary hosting exits 2. No supported retail-client test, complete platform gameplay or
local-to-CI artifact hash identity is claimed.

All five CI jobs are required for this checkpoint after publication. Full migration and
ordinary hosting remain incomplete. Original medpack/Zombie/VIP baseline discrepancies
remain unwaived; focused passes do not establish a passing full original baseline.

StandardMatchDomain now owns HostedInputRuntime before admission, replacing the external
LifeRuntime.Inputs implementation in StandardMatchFactory with narrowly required input ports.
It owns authorized held-tool changes, illegal-held-tool recovery, per-actor rejected-tool
counts and fresh crouch press actions. The existing timeline still records every input before
freshness checks; stale/reordered rows cannot select a tool, report another rejection or
repeat a crouch edge. The runtime never steps physics on arrival. Life reset clears the
crouch-held edge, while rejection diagnostics survive respawn; disconnect and scene disposal
remove actor state. Slot reuse has independent state.

ToolSelection shares the source equipped_tool_authorized gate between this inbound boundary
and PlayerSnapshot's existing outbound sanitizer: alive/spawned, valid selectable byte,
enabled tool, mode permission and the committed loadout. MG additionally permits the required
actual mounted-device authorization. Ordinary TDM/Arena have no equipped-tool override.
The mounted owner is queried only for a live, enabled MG absent from the loadout. An invalid
request reports its requested and held tool and repairs an already-illegal hand selection in
source order: remembered pre-MG tool, active weapon, committed loadout. No legal candidate
leaves the held tool unchanged. The source unmount hook performs that recovery and clears the
remembered hand tool; actual MG placement/mount retirement remains the device owner's job.
PlayerFire retains its existing reload/ammunition transitions: repeated input for the same
tool preserves a reload, while a real tool change cancels it. PlayerSnapshot retains existing
invalid-tool action stripping and MG no-op sentinel behavior in ReplicationService.

Fresh crouch press edges now feed the existing TeabagTracker, ObjectiveScores, ProfileTracker
and canonical Scoreboard rows. Ordinary TDM/Arena inherit BaseMode's grave representation.
Three timely nearby presses count an actual dead enemy once per actor life, update the
profile and round award, and pay points only when RULE_POINTS_FROM_TEABAGGING permits them.
Active/retiring mode gates and actual roster identity stay authoritative. Source spawn clears
the completed-death set but retains rolling press timestamps; disconnect forgets the tracker.
Buffered physics replay is never fed back into the crouch edge tracker.

The remaining paint owner receives authorized brush state, including secondary-trigger
release; painting, its VXL/cue service and UGC policy are not fabricated here. Required ports
also retain mounted ownership, anticheat reporting, input observation, reset/forget effects
and other packet domains. Packet rate admission, reported-position/drift services, complete
input/effect ownership, device placement/retirement, projectile/construction/corpse factories,
bots/director/shared identity allocation, auth/master, persistence/results/achievements,
ending/reset/HUD factories, full bootstrap, remaining modes and supported-client/capacity/
soak/baseline reconciliation remain pending. Ordinary hosting is still gated.

Two grouped component traces exercise fresh/stale selection, reload preservation/cancellation,
forged MG rejection, revoked-mount fallback/unmount, shared snapshot authorization, disconnect/
slot reuse/disposal and actual crouch/profile/round-award behavior across a new life. The
existing two-peer factory UDP scenario now selects Soldier's actual owned minigun via input
instead of forcing an unowned rifle in test code; TDM's real class restrictions remain intact.
It retains its HP/ammo/profile/frame/terrain/event/repair/entity/resource/rollover assertions,
then selects block tool 5, rejects an unmounted MG byte without dropping the peer and verifies
retired input runtime closure on map rollover. Deadlines and source policies are unchanged.
