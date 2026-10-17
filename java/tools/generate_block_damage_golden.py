"""Execute unchanged source footprint/quantization/shading functions on boundary and seed vectors."""
import gzip,hashlib,io,json,math,pathlib,struct
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import block_damage_model as m
bits=lambda n:struct.pack('>d',float(n)).hex()
rows=[];cells=0
seeds=[-1025,-257,-1,0,1,2,7,31,63,127,128,255,256,257,511,1024]
positions=[(-.5,-.5,-.5),(-.5000000000000001,.49999999999999994,238.5),(0.,511.,238.),(511.5,512.,239.),(32.5,33.5,40.5),(32.49999999999999,33.50000000000001,40.49999999999999)]
amounts=[0.,.125,.25,1.,2.5,5.,9.,31.75,63.75,100.]
for type in [-1,*sorted(m.FOOTPRINTS),44,255]:
 for i,seed in enumerate(seeds):
  p=positions[i%len(positions)];raw=amounts[(i+type)%len(amounts)];amount=m.wire_damage(raw);hits=m.footprint(type,p,amount,seed);cells+=len(hits)
  entry=m.footprint_kind(type);description='-' if entry is None else entry[0]+','+bits(entry[1]);encoded=';' .join(','.join(map(str,cell))+','+bits(damage)+','+str(int(0<=cell[0]<512 and 0<=cell[1]<512 and 0<=cell[2]<=m.MAX_DAMAGEABLE_Z and damage>0)) for cell,damage in hits) or '-'
  rows.append(('F',type,seed,','.join(map(bits,p)),bits(raw),bits(amount),description,int(m.is_native(type)),encoded))
for i in range(-512,1025):
 value=i/8
 for delta in [-1,0,1]:
  damage=math.nextafter(value,-math.inf) if delta<0 else math.nextafter(value,math.inf) if delta>0 else value
  rgb=(i*7919)&0xffffff;component=(i*37)%256
  rows.append(('M',bits(damage),component,rgb,bits(m.ceil4(damage)),bits(m.wire_damage(damage)),m.retail_round(damage),m.dim(component,damage),m.dim_rgb(rgb,damage)))
files={}
for part in range((len(rows)+255)//256):
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows[part*256:(part+1)*256])+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
 name=f'block-damage-{part:02}.tsv.gz';(OUT/name).write_bytes(buffer.getvalue());files[name]=hashlib.sha256(buffer.getvalue()).hexdigest()
provenance=dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/block_damage_model.py','shared/constants.py']},cases=len(rows),footprint_cells=cells,fixture_sha256=files,method_substitutions=False,scope='Unchanged source mathematical footprint, wire quantization, cell rounding, filtering and compounded per-channel shade functions. All 44 source types plus unknowns, signed/masked seeds, map boundary and adjacent rounding values. No actual map health, topology, digging or supported-client effects established by this fixture.')
(OUT/'block-damage-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');print('Terrain model cases',len(rows),'expanded cells',cells,'parts',len(files))
