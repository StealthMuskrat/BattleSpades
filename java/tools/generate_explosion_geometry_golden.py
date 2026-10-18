"""Execute unchanged retail blast geometry/LOS and impulse helpers; controlled input/raycast IO only."""
import gzip,hashlib,io,json,pathlib,random,struct
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import weapons_retail as retail,explosions
bits=lambda v:struct.pack('>d',float(v)).hex()
vec=lambda v:'-' if v is None else ','.join(map(bits,v))
rows=[];rng=random.Random(138)
for case in range(4096):
 origin=tuple(rng.uniform(-100,100) for _ in range(3));position=tuple(origin[i]+rng.uniform(-8,8) for i in range(3));radius=rng.choice([-1.,0.,1.,3.,4.,6.,8.,9.,16.]);minimum=rng.choice([0.,.1,.5,.75,-.2]);maximum=rng.choice([0.,.1,.25,1.,2.]);crouched=bool(case%2);damage=rng.choice([0.,-1.,7.,40.,100.,230.,300.,500.]);los=rng.choice([-1.,0.,.2,.3,.5,.7,.8,1.,1.2]);self=case%3==0;team=[None,0,1,2,3][case%5];classic=case%7==0;mask=case%8
 if case%32==0:position=origin
 if case%32==1:position=(origin[0]+radius,origin[1],origin[2]-(1.25 if crouched else .75))
 if case%32==2:position=(origin[0]+1e-10,origin[1],origin[2])
 calls=[]
 def raycast(*args):calls.append(args);return object() if mask&(1<<(len(calls)-1)) else None
 fraction=retail.explosion_los_fraction(retail.raycast_segment_blocker(raycast),origin,position,crouched)
 segment=retail.los_ray_segment(origin,position);segment='-' if segment is None else vec(segment[0])+':'+vec(segment[1])
 arguments=','.join(map(bits,(*origin,*position,radius,minimum,maximum,damage,los)))+','+','.join(map(str,[int(crouched),int(self),'-' if team is None else team,int(classic),mask]))
 outputs=[bits(retail.explosion_falloff(origin,position,radius,1.25 if crouched else .75)),bits(retail.explosion_player_damage(origin,position,radius,damage,crouched=crouched,los_fraction=los,is_self=self,target_team=team,classic=classic)),bits(retail.explosion_entity_damage(origin,position,radius,damage)),vec(explosions.explosion_impulse(origin,position,radius,minimum,maximum,crouched=crouched)),bits(fraction),segment,'|'.join(vec(c) for c in calls) or '-',vec(retail.scale_knockback(minimum,maximum,los))]
 rows.append((case,arguments,*outputs))
for spec in retail.RETAIL_EXPLOSIONS:
 for delta in [-2e-6,-1e-6,-.5e-6,0.,.5e-6,1e-6,2e-6]:
  value=spec.damage+delta;matched=retail.retail_explosion_for(spec.kill_type,value);rows.append(('stock',spec.kill_type,bits(value),'-' if matched is None else matched.name))
import shared.constants as C
from types import SimpleNamespace as NS
victim_cases=0
for cid in sorted(C.CLASS_DAMAGE_MULTIPLIER):
 for pack in sorted(C.JETPACK_PROPERTIES):
  for active in [False,True]:
   for hardcore in [False,True]:
    target=NS(class_id=cid,jetpack_id=pack,jetpack_active=active,connection=NS(server=NS(config=NS(game_mode='hc_tdm' if hardcore else 'tdm'))));rows.append(('victim',cid,pack,int(active),int(hardcore),bits(retail.victim_damage_multiplier(target))));victim_cases+=1
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'explosion-geometry.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'explosion-geometry-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/weapons_retail.py','server/explosions.py']},geometry_cases=4096,handler_selection_cases=23*7,victim_multiplier_cases=victim_cases,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged blast falloff/player/entity damage, impulse, sight segments/weights, normalized raycast callback order, knockback scaling and stock handler matching. Controlled geometry/raycast IO. Does not establish blast commit, prediction-frame impulse binding, concrete devices, projectiles or gameplay integration.'),indent=2)+'\n');print('Explosion geometry',4096,'handler selection',23*7,'bytes',path.stat().st_size)
