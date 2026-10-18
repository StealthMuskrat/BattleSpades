# Objective carry/drop helper components

Reference: 6216b771282d2d349ccd67ee58c378f035c83553. Full gameplay
hosting remains gated with exit 2.

ObjectiveCarry ports the unchanged server/pickups.py helper policy over owned
PlayerLife state and the actual PlayerPhysics body. Valid carried types are bomb
14, diamond 15 and intel 16. Pickup rejects an unknown type or existing carrier,
sets type/burden/team state and native burden before reliable broadcast. A send
failure therefore retains the new carry state. Spawn clears carry and native
burden. Core helpers deliberately keep the source's caller-owned alive/mode gates.

Drop accepts only finite three-component tuples within five blocks of the
current authoritative position. It preserves the position and caps velocity
to 10 blocks/s for bombs or 15 for diamonds/intel. Distance and speed use the
existing CPython 3.12 compensated-sum compatibility routine. Squared-distance overflow
retains the source exception boundary; finite huge velocity squares can overflow
to infinity and scale to zero, as in the original. A validated drop broadcasts
server identity/type/loop before reading the returned state and clearing carry
and native burden. A reliable send failure leaves carry intact. The returned
tuple lets the owning mode restore its dropped object.

DropPickup(71) uses a signed int32 loop, byte player/type and six native signed
1.6 fixed values. Tuple coordinates stay binary64 until encoding; narrowing to
Vector3f would lose the one-ULP fixed-store boundary. Native negative decode/
re-encode behavior is retained. PacketCodec and its generator include packet 71.
Dedicated carrier reveal uses PickPickup(70) independently of generic entity
wire gating, through an explicit caller binding.

The development-only generator executes unchanged original carry/drop/sanitize
functions against actual original Player and native body state. It compares
4,096 operations, including native burden, spawn reset, valid/invalid types,
distance boundaries, finite/nonfinite tuples, capped/overflow speeds and reliable
send faults. Another 8,192 arbitrary/mixed-magnitude and one-ULP-radius-boundary
sanitize vectors compare compensated decisions and exact capped tuples. Another
512 native codec cases exercise binary64 values immediately
beside fixed-store boundaries and native decoding/re-encoding. Comparisons use
exact exposed binary64 bits and native wire bytes. Clocks, spawn inputs and
terminal reliable send IO are controlled; no source policy method is replaced.
Typed three-component Java tuples make malformed tuple lengths unrepresentable;
this fixture does not claim equivalence for arbitrary Python object conversion.

A new real three-client UDP test checks carry initialization, native burden,
dedicated late carrier reveal, capped drop, reliable packet identity/loop and
a fault after enqueue. It checks retained carry, successful retry/clear and
spawn reset. This is explicit helper/reveal/send composition. The asynchronous
request handler's mode callback and ownership authorization, objective modes,
dropped-object physics, complete round/gameplay integration and supported-game-
client/capacity/soak gates remain pending. Existing trusted snapshot presentation
ports still supply the pickup byte; this checkpoint does not substitute an
incomplete objective mode for that required domain.

Local clean check/build passes 5,644 Java tests and 6 focused original tests.
All 61 configured generators reproduce outputs after a fresh native build;
221 recorded fixture/catalog/codegen hashes remain unchanged. Repeated CRC-valid
archives, packaging/smokes and isolated native probes pass. All five jobs pass in [run 39089220082](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39089220082); see [CI_STATUS.md](CI_STATUS.md).
The complete original baseline remains incomplete/failing; earlier unchanged
Zombie cue-lead and VIP discrepancies remain retained without waiver.

Implementation `c9a79aa31e5355acab2ae3c35a49dfd12dd4f4a0`, tested/published tree `0a6179e3ac36a641d2a554e9af60ec96ab5239eb`. The CI build compares repeated archives and tests one uploaded JAR on three platforms. Full gameplay and supported-client/capacity gates remain pending.
