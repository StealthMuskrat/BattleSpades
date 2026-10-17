"""Unchanged source assault/minigun gates composed with actual Player gun state and action_clock."""
import gzip,hashlib,io,json,pathlib,random,struct,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.player import Player
from server.combat_runtime import CombatSystem
from server import action_clock
bits=lambda x:struct.pack('>d',float(x)).hex()
number=lambda x:struct.unpack('>d',bytes.fromhex(x))[0]
scalar=lambda x:'-' if x is None else str(int(x))
clock=[100.];original=time.monotonic;time.monotonic=lambda:clock[0]
rows=[];voxels=vxl.VXL(-1,b'',0,2);native=world.World(voxels)
def state(p,c):
 floor=getattr(p,'_auto_reload_floor',None);burst=c._assault_bursts.get(0);spin=c._minigun_runs.get(0)
 values=[str(p.ammo_clip),str(p.ammo_reserve),str(int(p.reloading)),bits(p.reload_end_time),bits(p.last_shot_time),bits(p.next_shot_time),'-' if floor is None else ':'.join(map(str,floor))]
 values+=['-' if burst is None else ','.join([str(burst['count']),str(burst['last_loop']),str(burst['first_loop']),bits(burst['started_at'])])]
 values+=['-' if spin is None else ','.join([bits(spin['last_packet_at']),bits(spin['interval']),scalar(spin['loop'])])]
 values+=[';'.join(','.join(map(str,[key,scalar(l.label),bits(l.interval),bits(l.due),bits(l.tokens),bits(l.token_interval),bits(l.refilled_at),l.admitted,l.refused,l.by_label])) for key,l in action_clock.lanes(p).items()) or '-']
 return '~'.join(values)
try:
 for group in range(20):
  clock[0]=100.;rng=random.Random(179284+group);tool=60 if group%2==0 else 8;conn=NS(server=NS(world_manager=NS(world=native)),peer=None);p=Player(0,'Patterns',2,6,conn);p.spawn(36,36,97);c=CombatSystem(conn.server)
  def emit(op,arg):
   a=arg.split(',');result='-'
   if op=='tool':p.set_tool(int(arg),raw=True)
   elif op=='ammo':p.ammo_clip,p.ammo_reserve=map(int,a)
   elif op=='newest':p._observe_input_arrival_order(int(arg),1000)
   elif op=='time':clock[0]=number(arg)
   elif op=='link':conn.peer=None if a[0]=='-' else NS(roundTripTime=number(a[0]),roundTripTimeVariance=number(a[1]))
   elif op=='bot':p.is_bot=bool(int(arg))
   elif op=='controls':p.input.primary_fire=bool(int(a[0]));p.input.secondary_fire=bool(int(a[1]))
   elif op=='shot':result=str(int(c._accept_assault_burst_packet(p,NS(loop_count=int(arg)),clock[0]) if tool==60 else c._accept_minigun_packet(p,clock[0],loop=None if arg=='-' else int(arg))))
   elif op=='reload':result=str(int(p.start_reload(clock[0])))
   elif op=='forget':c.forget_player(0)
   elif op=='spawn':p.spawn(36,36,97)
   else:raise AssertionError(op)
   rows.append((str(group),op,arg,result,state(p,c)))
  emit('tool',str(tool));emit('ammo','70,400');label=100
  for step in range(300):
   if step%71==0:emit('bot',str(int(step%142==0)))
   if step%53==0:emit('ammo',rng.choice(['1,80','3,80','70,400','0,100']))
   if step%79==0:emit('forget','-')
   if step%101==0:emit('spawn','-')
   if step%47==0:emit('reload','-')
   if step%23==0:emit('link',rng.choice(['-,-',bits(180)+','+bits(40),bits(20)+','+bits(2)]))
   label+=rng.choice([0,1,6,12,30,-12,700]);emit('newest',str(label));emit('controls',rng.choice(['0,0','1,0','0,1','1,1']));clock[0]+=rng.choice([0,.001,.05,.1,.3,.6,2.,-.01]);emit('time',bits(clock[0]));emit('shot',str(label) if tool==60 or step%31 else '-')
finally:time.monotonic=original
raw=('\n'.join('\t'.join(r) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
path=OUT/'firing-patterns.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'firing-patterns-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),groups=20,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/combat_runtime.py','server/player.py','server/action_clock.py')},method_substitutions=False,scope='Unchanged original assault/minigun gates, actual Player gun/reload/ammo and action_clock. Controlled wall/monotonic clock, trusted tool/wallet/control/label/peer inputs; no hit/mode authorization proof.'),indent=2)+'\n');print('firing pattern operations',len(rows),'bytes',path.stat().st_size)
