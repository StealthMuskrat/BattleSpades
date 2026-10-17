"""Unchanged original shot validator and real Player own-frame eye/aim history."""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import combat_runtime as R,anticheat
from server.player import Player
logging.disable(logging.CRITICAL)
bits=lambda v:struct.pack('>d',float(v)).hex()
vec=lambda v:','.join(map(bits,v))
scalar=lambda v:'-' if v is None else str(int(v))
f32=lambda v:struct.unpack('f',struct.pack('f',v))[0]
def values(d):return ','.join(k+'='+('s'+v if isinstance(v,str) else 'b'+str(int(v)) if isinstance(v,bool) else 'i'+str(v) if isinstance(v,int) else 'n'+bits(v)) for k,v in sorted(d.items()))
def counters(d):return ','.join(k+'='+str(v) for k,v in sorted(d.items()) if v) or '-'
def stats(p):return ';'.join(k+':'+counters(v) for k,v in sorted(getattr(p,'anticheat_stats',{}).items()) if isinstance(v,dict) and v) or '-'
rows=[];rng=random.Random(179283)
class World:
 def __init__(self,seed):self.seed=seed;self.calls=[]
 def get_solid(self,x,y,z):self.calls.append((x,y,z));return (x*31+y*17+z*13+self.seed)%19==0
for group in range(16):
 p=Player(0,'validation',2,6,None);p.spawn(24,24,80)
 for step in range(400):
  current=tuple(rng.uniform(20,30) for _ in range(3));p.eye_x,p.eye_y,p.eye_z=current;p.orientation=rng.choice([(1.,0.,0.),(0.,0.,0.),tuple(rng.uniform(-1,1) for _ in range(3))]);p.velocity=tuple(rng.uniform(-.3,.3) for _ in range(3));p.is_bot=group%4==0
  p.last_applied_input_loop=100;history={}
  for label in [76,88,94,100]:
   if rng.random()<.7:history[label]=(tuple(current[j]+rng.uniform(-4,4) for j in range(3)),rng.choice([(1.,0.,0.),(0.,0.,0.),tuple(rng.uniform(-1,1) for _ in range(3))]))
  p._eye_history=history;label=rng.choice([None,76,88,94,100,200]);eye=rng.choice([current,*[v[0] for v in history.values()]])
  origin=tuple(f32(v+rng.choice([0.,.1,1.,3.,9.])) for v in eye);aim=tuple(f32(v) for v in rng.choice([(0.,0.,0.),(1.,0.,0.),p.orientation,tuple(rng.uniform(-1,1) for _ in range(3))]))
  if step%29==0:origin=(float('nan'),origin[1],origin[2])
  if step%31==0:aim=(float('inf'),aim[1],aim[2])
  config=NS(shot_origin_tolerance=rng.choice([0.,1.5,4.]),aim_direction_tolerance_deg=rng.choice([0.,10.,45.]),enforce_shot_origin=bool(group&1),enforce_aim_direction=bool(group&2));world=World(step%19);server=NS(world_manager=world,config=NS(anticheat=config));combat=R.CombatSystem(server);packet=NS(loop_count=label,x=origin[0],y=origin[1],z=origin[2],ori_x=aim[0],ori_y=aim[1],ori_z=aim[2]);events=[]
  def profile(frame,event,arg):
   if event=='call' and frame.f_code is anticheat.report.__code__:
    f=frame.f_locals;events.append(f['kind']+','+str(int(f['enforced']))+','+values(f['detail']))
  sys.setprofile(profile)
  try:accepted=combat._validate_shot_packet(p,packet)
  finally:sys.setprofile(None)
  output='-' if accepted is None else ';'.join(map(vec,accepted[0]))+'|'+bits(accepted[1])+'|'+vec(combat._normalize(aim))
  hist=';' .join(str(k)+','+vec(v[0])+','+vec(v[1]) for k,v in sorted(history.items())) or '-'
  rows.append((str(group),str(step),vec(p.eye),vec(p.orientation),vec(p.velocity),str(int(p.is_bot)),str(p.tool),hist,scalar(label),vec(origin),vec(aim),vec((config.shot_origin_tolerance,config.aim_direction_tolerance_deg,step%19)),str(int(config.enforce_shot_origin))+str(int(config.enforce_aim_direction)),output,str(combat._shot_drop_reason or '-'),stats(p),counters(getattr(p,'anticheat_counts',{})),';'.join(events) or '-',';'.join(','.join(map(str,c)) for c in world.calls) or '-'))
parts=[]
for start in range(0,len(rows),1600):
 raw=('\n'.join('\t'.join(r) for r in rows[start:start+1600])+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/f'shot-validation-{start//1600:02}.tsv.gz';path.write_bytes(buffer.getvalue());parts.append(dict(path=path.name,sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(OUT/'shot-validation-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),groups=16,parts=parts,source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/player.py','server/anticheat.py')},method_substitutions=False,scope='Unchanged _validate_shot_packet, reference_eyes, lag_slack, Player eye/aim getters and anticheat counters/reports. Controlled actual Player history/domain inputs, trusted configuration and terrain oracle; sys.setprofile observes reports without replacing methods. No shot consumption or damage/mode authorization proof.'),indent=2)+'\n')
print('shot validation operations',len(rows),'bytes',max((OUT/p['path']).stat().st_size for p in parts))
