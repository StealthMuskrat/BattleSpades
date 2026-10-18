# Java test results

Local Java check/build passes **6,126 component + 36 integration = 6,162 tests**,
with zero failures, errors or skips across 98 suites. This batch adds 24 original-source
scenario/codec cases and five targeted boundary cases; two existing compositions are
upgraded rather than duplicated. **273 focused original-source regressions pass**.
The new generator records four seeded music traces, three ambience scenarios, 16
map-ballot traces and 564 distinct native codec vectors in one aggregate codec case.
The updated simple-packet generator and new media generator reproduce all six outputs
byte-for-byte. Of 251 earlier frozen paths, 250 retain their exact hashes; PacketCodec
is deliberately updated to register native-backed IDs 22/24/26/47. There are 254
checked paths and 72 configured CI generators. Unchanged prior 70-generator/native
build evidence is reused locally; CI independently rebuilds/reproduces all 72.

Two CRC-valid local archives are byte-identical: 1879059 bytes,
SHA-256 `2b1eec34f5af529436c37c2287e9b09ede815f483d02eb22896c8ef2ad49f2a6`. Class major 69, packaging/licenses and version/config smokes pass;
normal hosting exits 2. All four current isolated native transport/packaged-loader
nominal/impaired profiles pass. The loader still uses FixtureScene. Platform tests run
source-built component/integration classes and smoke the uploaded JAR. No local-to-CI
artifact hash identity or complete platform gameplay is claimed. Prior medpack/Zombie/
VIP original-baseline discrepancies remain unwaived; these focused passes do not
establish a passing full original-source baseline.

All five CI jobs passed for implementation `280aa453e20ff89709db907b86437e3adff13852`,
tested/published tree `d9661a57f823ab8bc63bf200f03a2b94d7674456`, in
[run 39168496083](https://github.com/StealthMuskrat/BattleSpades/actions/runs/39168496083). The native job independently reproduced all 72
configured original generators, including round music/ambience/ballot traces, native
codec vectors and the intentionally updated packet registration. The build byte-compared
repeated archives. Ubuntu, Windows and macOS smoked the uploaded JAR and ran source-built
component/integration tests. Every final job step completed with success or an intentional
skip. No local-to-CI artifact hash identity or complete platform gameplay is claimed.

See [ROUND_MEDIA.md](ROUND_MEDIA.md) for current scope and limits. Earlier evidence
is in [PREPARED_HOST.md](PREPARED_HOST.md), [TRANSITIONS.md](TRANSITIONS.md),
[ROUND_RUNTIME.md](ROUND_RUNTIME.md) and [TDM_RUNTIME.md](TDM_RUNTIME.md).
