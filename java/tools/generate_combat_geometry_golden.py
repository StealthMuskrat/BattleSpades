"""Unchanged original combat geometry, recording results and exact voxel probe order."""
import gzip,hashlib,io,json,math,pathlib,random,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import combat_runtime as R
bits=lambda x:struct.pack('>d',float(x)).hex()
vec=lambda v:','.join(map(bits,v))
cell=lambda v:','.join(map(str,v))
rows=[];rng=random.Random(987141);combat=R.CombatSystem(NS())
def result(hit):return '-' if hit is None else ','.join([bits(hit[0]),*map(bits,hit[1]),*map(str,hit[2:])])
class World:
 def __init__(self,seed):self.seed=seed;self.calls=[]
 def get_solid(self,x,y,z):
  self.calls.append((x,y,z));return (x*31+y*17+z*13+self.seed)%19==0
for i in range(6000):
 start=tuple(rng.choice([rng.uniform(-4,12),float(rng.randrange(-4,12)),.5]) for _ in range(3))
 end=tuple(rng.choice([rng.uniform(-4,12),float(rng.randrange(-4,12)),.5]) for _ in range(3))
 if i%13==0:end=start
 if i%17==0:end=tuple(v+2 for v in start)
 ss,se=rng.choice([(0,0),(.3,.3),(.3,0),(.1,.7)])
 world=World(i%19);ignore=[tuple(math.floor(v) for v in start)] if i%4==0 else []
 clear=R.segment_clear(world,start,end,shrink_start=ss,shrink_end=se,ignore=ignore)
 rows.append(('segment',str(i),vec(start),vec(end),vec((ss,se,i%19)),(';'.join(map(cell,ignore)) or '-'),str(int(clear)),(';'.join(map(cell,world.calls)) or '-')))
for i in range(1200):
 eyes=[tuple(rng.uniform(-3,8) for _ in range(3)) for _ in range(i%4)]
 target=tuple(rng.randrange(-3,8) for _ in range(3));ignore=[(1,2,3)] if i%3==0 else [];world=World(i%19)
 visible=R.cell_visible(world,eyes,target,ignore=ignore)
 rows.append(('visible',str(i),';'.join(map(vec,eyes)) or '-',cell(target),str(i%19),';'.join(map(cell,ignore)) or '-',str(int(visible)),';'.join(map(cell,world.calls)) or '-'))
classes=list(R._CLASS_HITBOXES)+[-1,999]
for i in range(16000):
 cls=classes[i%len(classes)];at=tuple(rng.uniform(10,30) for _ in range(3));angle=rng.choice([0.,math.pi/2,math.pi,math.pi*1.5,rng.uniform(-math.pi,math.pi)])
 aim=(math.cos(angle),math.sin(angle),rng.uniform(-1,1));origin=tuple(at[j]+rng.uniform(-10,10) for j in range(3));offset=tuple(rng.uniform(-1.4,1.4) for _ in range(3));direction=combat._normalize(tuple(at[j]+offset[j]-origin[j] for j in range(3)));maximum=rng.choice([.1,2.,10.,100.]);crouch=bool(i%2)
 target=NS(x=at[0],y=at[1],z=at[2],orientation=aim,class_id=cls,hitbox_crouched=crouch,input=NS(crouch=False))
 hit=combat._ray_hits_target(origin,direction,maximum,target)
 rows.append(('body',str(i),vec(origin),vec(direction),bits(maximum),str(cls),vec(at),vec(aim),str(int(crouch)),result(hit),vec((math.atan2(aim[0],aim[1]),math.cos(math.atan2(aim[0],aim[1])),math.sin(math.atan2(aim[0],aim[1]))))))
 hit=combat._ray_hits_model_bounds(origin,direction,maximum,target,(0.,0.,0.),R._CLASSIC_CORPSE_BOUNDS)
 rows.append(('corpse',str(i),vec(origin),vec(direction),bits(maximum),vec(at),vec(aim),result(hit),vec((math.atan2(aim[0],aim[1]),math.cos(math.atan2(aim[0],aim[1])),math.sin(math.atan2(aim[0],aim[1]))))))
for i in range(6000):
 origin=tuple(rng.uniform(-10,10) for _ in range(3));center=tuple(rng.uniform(-10,10) for _ in range(3));direction=combat._normalize(tuple(center[j]+rng.uniform(-2,2)-origin[j] for j in range(3)));radius=rng.choice([0.,.1,.5,1.,3.]);maximum=rng.choice([0.,1.,10.,100.])
 hit=combat._ray_sphere_entry(origin,direction,maximum,center,radius)
 rows.append(('sphere',str(i),vec(origin),vec(direction),bits(maximum),vec(center),bits(radius),result(hit)))
# Inclusive slab faces, neighbouring representable coordinates, all source classes/crouch states.
for cls,profile in sorted(R._CLASS_HITBOXES.items()):
 for crouch in (False,True):
  target=NS(x=20.,y=20.,z=20.,orientation=(0.,1.,0.),class_id=cls,hitbox_crouched=crouch,input=NS(crouch=False))
  for part in range(5):
   bounds=R._CROUCH_TORSO if crouch and part==1 else R._CROUCH_LEG if crouch and part in (3,4) else profile[part]
   offset=(0.,0.,.3) if part<2 else (0.,0.,R.C.BODY_PART_ARMS_CROUCH_Z if crouch else .5) if part==2 else ((.25 if part==3 else -.25),R.C.BODY_PART_LEG_CROUCH_Y if crouch else 0.,.7 if crouch else 1.1)
   size,pivot=bounds;position=(20.-offset[0],20.-offset[1],20.+offset[2])
   low=(position[0]+(pivot[0]-size[0])*.05,position[1]-pivot[1]*.05,position[2]-pivot[2]*.05)
   high=(position[0]+pivot[0]*.05,position[1]+(size[1]-pivot[1])*.05,position[2]+(size[2]-pivot[2])*.05)
   for axis in range(3):
    ray_axis=(axis+1)%3
    for face in (low[axis],high[axis]):
     for value in (math.nextafter(face,-math.inf),face,math.nextafter(face,math.inf)):
      origin=[(low[j]+high[j])*.5 for j in range(3)];origin[axis]=value;origin[ray_axis]=low[ray_axis]-2.;direction=[0.,0.,0.];direction[ray_axis]=1.
      hit=combat._ray_hits_target(origin,direction,128.,target)
      rows.append(('body','boundary-'+str(len(rows)),vec(origin),vec(direction),bits(128.),str(cls),vec((20.,20.,20.)),vec(target.orientation),str(int(crouch)),result(hit),vec((0.,1.,0.))))
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
# Keep individual GitHub API blobs below one megabyte.
parts=[]
for n in range(0,len(rows),5000):
 data=('\n'.join('\t'.join(r) for r in rows[n:n+5000])+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(data)
 path=OUT/f'combat-geometry-{n//5000:02}.tsv.gz';path.write_bytes(buffer.getvalue());parts.append(dict(path=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(OUT/'combat-geometry-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),parts=parts,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','shared/constants.py')},method_substitutions=False,scope='Unchanged original segment_clear, cell_visible, body/corpse oriented-KV6 slabs and sphere entry. Controlled terrain records every get_solid probe. Controlled body class, crouch, positions and rays; no shot admission or damage/mode integration proof.'),indent=2)+'\n')
print('geometry operations',len(rows),'parts',len(parts),'largest',max((OUT/p['path']).stat().st_size for p in parts))
