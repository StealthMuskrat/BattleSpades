"""Execute unchanged server/action_clock.py; typed inputs mirror wire labels and server clocks.
No action, cadence function or lane is replaced. This is timing evidence, not hit authorization.
"""
import gzip,hashlib,io,json,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];prepare(ROOT)
from server import action_clock as clock
OUT=ROOT/'java/src/test/resources/golden'
def bits(v):return struct.pack('>d',float(v)).hex()
def number(v):return struct.unpack('>d',bytes.fromhex(v))[0]
def scalar(v):return '-' if v is None else str(v)
def state(p):return ';'.join(','.join(map(str,[key,scalar(l.label),bits(l.interval),bits(l.due),bits(l.tokens),bits(l.token_interval),bits(l.refilled_at),l.admitted,l.refused,l.by_label])) for key,l in clock.lanes(p).items()) or '-'
rows=[]
for group in range(32):
 p=NS(_input_newest_label=600,last_applied_input_loop=None,connection=None);rng=random.Random(9200+group);now=20.0;label=100
 def emit(op,arg):
  a=arg.split(',');result='-'
  try:
   if op in ('admit','peek'):
    key,raw_label,interval,stamp,min_frames,grace,rtt,variance=a
    p.connection=None if rtt=='-' else NS(peer=NS(roundTripTime=number(rtt),roundTripTimeVariance=number(variance)))
    result=str(int(getattr(clock,op)(p,key,label=None if raw_label=='-' else int(raw_label),interval=number(interval),now=number(stamp),min_frames=None if min_frames=='-' else int(min_frames),grace=number(grace))))
   elif op=='reset':clock.reset(p,None if arg=='-' else arg)
   elif op=='newest':p._input_newest_label=int(arg)
   elif op=='stall':
    p.connection=None if a[0]=='-' else NS(peer=NS(roundTripTime=number(a[0]),roundTripTimeVariance=number(a[1])));result=bits(clock.link_stall_seconds(p))
   else:raise AssertionError(op)
  except (ValueError,OverflowError):result='error'
  rows.append((group,op,arg,result,state(p)))
 def attempt(op='admit',key='fire',value=None,interval=.1,min_frames=None,grace=1/60,link=None):
  emit(op,','.join([key,scalar(label if value is None else value),bits(interval),bits(now),scalar(min_frames),bits(grace),'-' if link is None else bits(link[0]),'-' if link is None else bits(link[1])]))
 for value in [100,106,112,112,118,124]:attempt(value=value)
 for step in range(500):
  if group%2==0:emit('newest',str(600+step*100))
  now+=rng.choice([0,0,0.001,1/60,0.1,0.5,-0.01]);label+=rng.choice([0,1,3,6,100,-20]);
  if rng.random()<.1:emit('reset',rng.choice(['-','fire','build']))
  attempt(rng.choice(['peek','admit','admit']),rng.choice(['fire','build','grenade']),rng.choice([label,label+4,-1,'-']),rng.choice([0,-1,.001,.01,1/60,.1,.4,.7,1.5]),rng.choice([None,None,1,6,-1]),rng.choice([0,1/60,.05]),rng.choice([None,(0,0),(80,30),(250,70),(1000,250),(-1,20),(0,-3)]))
 for link in [None,(0,0),(10,0),(80,30),(250,70),(1000,250),(-1,0),(float('nan'),0),(0,float('inf')),(1e308,1e308)]:emit('stall',',' .join(['-','-'] if link is None else [bits(x) for x in link]))
 for interval in [float('nan'),float('inf'),-float('inf')]:attempt(interval=interval)
buffer=io.BytesIO();raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
fixture=OUT/'action-clock.tsv.gz';fixture.write_bytes(buffer.getvalue())
(OUT/'action-clock-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_files':{'server/action_clock.py':hashlib.sha256((ROOT/'server/action_clock.py').read_bytes()).hexdigest()},'operations':len(rows),'groups':32,'fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'method_substitutions':False,'scope':'cadence peek/admit/reset, bounded token budget, tool-interval changes and ENet link-stall estimate; no weapon or hit authorization'},indent=2)+'\n')
print('action clock operations',len(rows))
