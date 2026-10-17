"""Unchanged WorldManager.raycast and real native World, including the 0.1-step fallback."""
import gzip,hashlib,io,itertools,json,pathlib,random,struct
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.world_manager import WorldManager
bits=lambda x:struct.pack('>d',float(x)).hex()
voxels=vxl.VXL(-1,b'',0,2)
cells={(x,y,62) for x in range(96,109) for y in range(96,109)}|{(104,y,z) for y in range(98,104) for z in range(55,62)}
for c in cells:voxels.set_point(*c,True,0x7f123456)
manager=WorldManager.__new__(WorldManager);manager.map=voxels;manager.world=world.World(voxels);rng=random.Random(179286);rows=[]
for native in (True,):
 actual=manager.world
 if not native:manager.world=None
 for i in range(1600):
  p=rng.choice([(100.5,100.5,59.75),(104.,100.,61.),(103.999999,100.,61.),(-.01,100.,61.),(100.,100.,239.),tuple(rng.uniform(94,110) for _ in range(3))]);d=rng.choice(list(itertools.product((-1.,0.,1.),repeat=3))+[tuple(rng.uniform(-1,1) for _ in range(3))]);length=rng.choice([0.,.1,.3,10.,128.]);hit=manager.raycast(*p,*d,length)
  rows.append((str(int(native)),','.join(map(bits,(*p,*d,length))),'-' if hit is None else ','.join(map(str,hit))))
 manager.world=actual
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'combat-ray.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'combat-ray-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/world_manager.py','aoslib/world.pyx')},method_substitutions=False,scope='Unchanged WorldManager.raycast/get_solid, actual rebuilt native world and original VXL controlled terrain; accurate-query plus source fallback manager domain configuration.'),indent=2)+'\n');print('combat terrain ray operations',len(rows))
