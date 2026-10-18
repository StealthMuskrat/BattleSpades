# Objective drop request component

Reference: 6216b771282d2d349ccd67ee58c378f035c83553. Full gameplay hosting
remains gated with exit 2.

ObjectiveDropRequests ports the unchanged registered server/handlers/world.py
packet-71 coroutine. It rejects a dead/unspawned player, no carry, or a type
that differs from the bound player's carry before invoking the current mode.
The packet's claimed player ID and loop are ignored. An explicitly optional
mode callback gets the original position/velocity and its completion is awaited.
True consumes the request, including a mode-owned rejection; false or null lets
the original helper attempt the fallback. No second life/type guard is added
after suspension: fallback rereads current carry, position, state and server loop.
The loop is sampled only after vector validation. This preserves the source's
observable behavior when a callback changes carry, teleports, respawns or a
player dies while the mode is suspended.

A successful generic fallback first reliably broadcasts DropPickup and clears
carry/native burden, then places its returned type/state/position/velocity in
the actual EntityRegistry, with kind pickup and radius 0.5. It does not attach
physics behavior or immediately broadcast CreateEntity. Hook/send failures
complete exceptionally; a failed reliable send retains carry and does not place
an entity. Helper state mutation and exception ordering remain source-defined.

The handler is owner-confined. Completed callbacks continue inline; a completion
from another thread must use the required owner executor before touching life,
registry or reliable delivery. The returned stage exposes completion and errors.
LifeRuntime offers a once-before-admission binding and routes decoded DropPickup
through it. A required completion port must preserve the outer packet drain's
serial await and error-handling boundary. The component does not create an outer
scheduler, cancellation/shutdown policy or a permissive mode. Mode implementations must also confine their own
mutations to the owner. The typed callback accepts Boolean/null; arbitrary Python
object truthiness and malformed dynamic object conversion are not claimed.

The development-only fixture executes the actual unchanged registered coroutine,
carry helpers, original Player/native body and actual original registry. Its
2,048 requests cover 384 generic placements, 192 consumed requests, 448 suspended
callbacks, 176 hook/send faults and 1,024 early rejections. It compares state
before and after suspension, exact binary64 entity fields and reliable native
wire. Mode callbacks, clocks and terminal send faults are explicit boundary
inputs; they do not replace a source policy method or implement concrete modes.
Another Java test checks worker completion, owner continuation and a wrong-thread
executor failing before mutation.

A new three-client UDP test sends decoded packets through the production
LifeRuntime binding. It checks spoofed identity/type rejection, invalid distance,
mode consumption, false-hook fallback, reliable authoritative loop/identity,
velocity caps, actual registry placement without CreateEntity and death during
a suspended mode callback. The suspended callback explicitly resumes on the
owner and samples the newer server loop. Test completion/scheduler ports remain
explicit and do not establish complete outer packet-drain scheduling.

Concrete CTF/intel, diamond and bomb ownership, persistent dropped-object
simulation, complete snapshot/mode/round/gameplay integration, full original
baseline and supported-client/capacity/soak gates remain pending. Existing
original Zombie cue-lead and VIP discrepancies are retained without waiver.

Local clean check/build passes 5,662 Java tests and 6 focused original tests.
All 62 configured generators reproduce outputs after a fresh native build;
223 recorded fixture/catalog/codegen hashes remain unchanged. Repeated CRC-valid
archives, packaging/smokes and four isolated native profiles pass.
Publication and all five current-head CI jobs are pending.
