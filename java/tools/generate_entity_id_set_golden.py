"""Original CPython integer-set operations used by EntityRegistry support buckets."""
import gzip,hashlib,io,json,pathlib,random,struct,sys
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';assert sys.version_info[:2]==(3,12)
rows=[]
for trace in range(32):
 rng=random.Random(731+trace);values=set()
 for step in range(512):
  key=(rng.randrange(256)*[1,8,32,256][trace%4]+step%3)&65535;op='add' if step%7<4 else 'discard' if step%7<6 else 'contains';before=key in values
  if op=='add':values.add(key);result=not before
  elif op=='discard':values.discard(key);result=before
  else:result=before
  if step%127==126:values.clear();op='clear';result=False
  raw=b''.join(struct.pack('<I',value) for value in values);rows.append((trace,step,op,key,int(result),len(values),hashlib.sha256(raw).hexdigest()))
values=set()
for step,op in enumerate(['fill','discard_even','reinsert_low','clear']):
 if op=='fill':
  for value in range(65536):values.add(value)
 elif op=='discard_even':
  for value in range(0,65536,2):values.discard(value)
 elif op=='reinsert_low':
  for value in range(256):values.add(value)
 else:values.clear()
 state=b''.join(struct.pack('<I',value) for value in values);rows.append((32,step,op,0,0,len(values),hashlib.sha256(state).hexdigest()))
raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'entity-id-set.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'entity-id-set-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',runtime_family='CPython 3.12',runtime_source='https://github.com/python/cpython/blob/v3.12.0/Objects/setobject.c',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Actual CPython sets of uint16 entity IDs: insertion/deletion/dummies/resizes/clear/membership and full table-order digest. Includes full uint16 occupancy and bulk deletion/reinsertion. Foundation for support-callback order only; no concrete entity registry or behavior lifecycle proof yet.'),indent=2)+'\n');print('Entity ID set operations',len(rows),'bytes',path.stat().st_size)
