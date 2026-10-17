"""Original CombatSystem seeded pellets/bloom, actual Player zoom history and Python RNG."""
import gzip,hashlib,io,json,pathlib,random,struct
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server.combat_runtime import CombatSystem
from server.game_constants import WEAPON_PROFILES
from server.player import Player
def bits(v):return struct.pack('>d',float(v)).hex()
def number(v):return struct.unpack('>d',bytes.fromhex(v))[0]
def scalar(v):return '-' if v is None else str(int(v))
rows=[];guns=list(WEAPON_PROFILES)
for group,tool in enumerate(guns):
 p=Player(0,'Pellets',2,6,None);p.spawn(36,36,97);combat=CombatSystem(NS());rng=random.Random(2562+group);now=20.;label=100
 def state():
  s=combat._pellet_spread.get(0)
  return '-' if s is None else ','.join([str(s['tool']),bits(s['spread']),bits(s['last_at']),scalar(s['label'])])
 def emit(op,arg):
  global now
  a=arg.split(',');result='-'
  if op=='tool':p.set_tool(int(arg),raw=True)
  elif op=='zoom':
   n=int(a[0]);zoom=bool(int(a[1]));p._record_zoom_label(n,(False,False,zoom,False,True,False,False,False,False));p._observe_input_arrival_order(n,1000)
  elif op=='current':p.input.zoom=bool(int(arg))
  elif op=='bot':p.is_bot=bool(int(arg))
  elif op=='forget':combat.forget_player(0)
  elif op=='pellets':
   now=number(a[0]);shot_label=None if a[1]=='-' else int(a[1]);direction=tuple(number(v) for v in a[3:6]);packet=NS(loop_count=shot_label,seed=int(a[2]))
   pellets=combat._seeded_pellet_directions(p,direction,WEAPON_PROFILES[p.tool],packet,now);result=';'.join(','.join(map(bits,v)) for v in pellets) or '-'
  else:raise AssertionError(op)
  rows.append((group,op,arg,result,state()))
 emit('tool',str(tool))
 for step in range(240):
  if step%37==0:emit('bot',str(int(step%74==0)))
  if step%79==0:emit('forget','-')
  if step%43==0:emit('tool',str(rng.choice(guns)))
  if step%43==1:emit('tool',str(tool))
  label+=rng.choice([0,1,3,6,30,700,-20]);now+=rng.choice([0,1/60,.1,1.,-.01])
  emit('zoom',f'{label},{int(step%7<3)}');emit('zoom',f'{label+1},0');emit('current',str(int(step%2)))
  direction=[(1.,0.,0.),(.125,.25,-.5),(0.,0.,0.),(-1.,0.,.01)][step%4]
  emit('pellets',','.join([bits(now),rng.choice(['-',str(label),str(label+2),str(label-20)]),str(rng.randrange(-256,512)),*map(bits,direction)]))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'pellets.tsv.gz';path.write_bytes(buffer.getvalue())
(OUT/'pellets-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'groups':len(guns),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source_files_sha256':{s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/player.py','server/game_constants.py')},'scope':'Unchanged original CombatSystem seeded-pellet/bloom methods and Player zoom-label lookups, Python random.Random seed masking; trusted tools, labels, times and shot directions are controlled. No shot/hit authorization proof.','method_substitutions':False},indent=2)+'\n')
print('pellet operations',len(rows),'groups',len(guns),'bytes',path.stat().st_size)
