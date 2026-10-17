"""Unchanged source shield mitigation/knockback, observing attachment requests without substituting effects."""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from server.combat_runtime import CombatSystem
from server.entities import attachments
bits=lambda x:struct.pack('>d',float(x)).hex()
vec=lambda v:','.join(map(bits,v))
rows=[];rng=random.Random(179287);combat=CombatSystem(NS(entity_registry=None))
for i in range(14000):
 position=tuple(rng.uniform(0,512) for _ in range(3));attacker=tuple(position[j]+rng.uniform(-10,10) for j in range(3));aim=rng.choice([(0.,0.,0.),(1.,0.,0.),(-1.,0.,0.),tuple(rng.uniform(-1,1) for _ in range(3))]);attacker_aim=rng.choice([(0.,0.,0.),(0.,0.,1.),tuple(rng.uniform(-1,1) for _ in range(3))]);velocity=tuple(rng.uniform(-1,1) for _ in range(3))
 if i%7==0:attacker=(position[0],position[1],attacker[2])
 if i%13==0:attacker=position
 if i%17==0:attacker=(position[0]+1e-7,position[1]-1e-7,attacker[2])
 if i%19==0:attacker=(position[0]+1e-6,position[1],attacker[2])
 tool=rng.choice([52,52,52,6]);display=bool(i%3);melee=bool(i%2);damage=rng.choice([-50.,-0.,0.,.01,.5,1.,20.5,100.,900.]);impact=None if i%5==0 else tuple(position[j]+rng.uniform(-1,1) for j in range(3))
 target=NS(tool=tool,input=NS(can_display_weapon=display),x=position[0],y=position[1],z=position[2],orientation=aim,velocity=velocity);source=NS(x=attacker[0],y=attacker[1],z=attacker[2],orientation=attacker_aim);events=[]
 def profile(frame,event,arg):
  if event=='call' and frame.f_code is attachments.riot_shield_hit.__code__:
   f=frame.f_locals;events.append(vec(f['position'])+','+str(int(f['melee'])))
 sys.setprofile(profile)
 try:amount=combat._apply_riot_shield_mitigation(target,source,damage,impact=impact,melee=melee)
 finally:sys.setprofile(None)
 combat._apply_riot_shield_knockback(source,target)
 rows.append((str(i),str(tool),str(int(display)),vec(position),vec(aim),vec(attacker),vec(attacker_aim),vec(velocity),bits(damage),'-' if impact is None else vec(impact),str(int(melee)),bits(amount),vec(target.velocity),';'.join(events) or '-'))
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
# Bound API upload size without changing expected rows.
parts=[]
for start in range(0,len(rows),3000):
 raw=('\n'.join('\t'.join(r) for r in rows[start:start+3000])+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/f'shield-{start//3000:02}.tsv.gz';path.write_bytes(buffer.getvalue());parts.append(dict(path=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(OUT/'shield-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),parts=parts,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/entities/attachments.py')},method_substitutions=False,scope='Unchanged direct shield mitigation and CPython-hypot knockback. Controlled facade vectors, held/displayed tool, impact and damage; sys.setprofile observes unchanged attachment requests. Registry absent, so no concrete attachment reveal/effect or health-admission proof.'),indent=2)+'\n');print('shield operations',len(rows),'parts',len(parts))
