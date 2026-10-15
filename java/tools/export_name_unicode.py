"""Pin the reference Python 3.12 Unicode 15 database for portable name policy.

The runtime never imports Python or uses the host JDK's changing Unicode tables.
Only immutable Unicode/name tables are exported; policy logic stays in Java.
"""
import gzip, io, pathlib, struct, unicodedata as U
from reference_imports import prepare
ROOT = pathlib.Path(__file__).resolve().parents[2]
prepare(ROOT)
from server import player_names as N

if U.unidata_version != '15.0.0':
    raise RuntimeError('Regenerate with the reference Python 3.12 / Unicode 15.0.0')
out = io.BytesIO()
def integer(value): out.write(struct.pack('>i', value))
def string(value):
    raw = value.encode('utf-8'); integer(len(raw)); out.write(raw)
def mapping(values):
    integer(len(values))
    for cp, value in values:
        integer(cp); string(value)
integer(0x4e414d45); string(U.unidata_version)
ranges = []; previous = None
for cp in range(0x110000):
    c = chr(cp); category = U.category(c)
    value = (int(N._is_invisible(c)) | (int(category.startswith('M')) << 1)
             | (int(c.isspace()) << 2) | (int(c.isalnum()) << 3)
             | (U.combining(c) << 8))
    if value != previous:
        ranges.append((cp, value)); previous = value
integer(len(ranges))
for cp, value in ranges: integer(cp); integer(value)
mapping([(cp, U.normalize('NFKD', chr(cp))) for cp in range(0x110000)
         if not 0xac00 <= cp <= 0xd7a3 and U.normalize('NFKD', chr(cp)) != chr(cp)])
mapping([(cp, chr(cp).casefold()) for cp in range(0x110000) if chr(cp).casefold() != chr(cp)])
pairs = []
for cp in range(0x110000):
    raw = U.decomposition(chr(cp))
    if raw and not raw.startswith('<'):
        parts = [int(v,16) for v in raw.split()]
        if len(parts) == 2 and U.normalize('NFC', ''.join(map(chr, parts))) == chr(cp):
            pairs.append((*parts, cp))
integer(len(pairs))
for a,b,c in pairs: integer(a); integer(b); integer(c)
mapping([(ord(c), value) for c,value in sorted(N._CONFUSABLES.items())])
for values in (N.RESERVED_NAMES, N.RESERVED_TAGS):
    integer(len(values))
    for value in sorted(values): string(value)
target = ROOT/'java/src/main/resources/net/battlespades/player/name-unicode.bin.gz'
target.parent.mkdir(parents=True,exist_ok=True)
compressed=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=compressed,mtime=0) as stream:stream.write(out.getvalue())
target.write_bytes(compressed.getvalue())
print('Unicode', U.unidata_version, 'ranges',len(ranges),'composition pairs',len(pairs),'bytes',target.stat().st_size)
