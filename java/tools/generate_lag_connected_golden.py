"""Observe actual original pre-movement lag history during connected native flight/infantry."""
import gzip,hashlib,io,json,time
from generate_flight_golden import FlightFixture,ROOT,OUT,original,bits
def scalar(v):return str(int(v))
def sample(row):return '-' if row is None else ','.join([str(row[0]),*map(bits,row[1:7]),*map(scalar,row[7:])])
def history(p):
 h=getattr(p,'_lag_history',None)
 return '0|-|-' if h is None else str(h.epoch)+'|'+sample(h.last)+'|'+hashlib.sha256(';'.join(sample(s) for s in h.ring).encode()).hexdigest()
rows=[]
try:
 for group in range(4):
  f=FlightFixture(group)
  def emit(op,arg):f.op(op,arg);rows.append((group,op,arg,history(f.p)))
  emit('equip',f'{group},{[0,66,68,69][group]},1');emit('negotiate',str(group%3))
  for tick in range(160):
   emit('receive',','.join(map(str,[100+tick,[1,33,17,0][tick//20%4],152 if group==3 or tick>110 else 24,0,bits(1),bits(0),bits(0)])))
   emit('tick',bits(1/60))
   if tick==60:emit('spawn',','.join(bits(v) for v in (40,40,40)))
   if tick==100:emit('spawn',','.join(bits(v) for v in (36,36,97)))
 raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/'lag-connected.tsv.gz';path.write_bytes(buffer.getvalue())
 (OUT/'lag-connected-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'source_files_sha256':{s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ('server/lag_compensation.py','server/player.py','server/simulation_runtime.py')},'scope':'Unchanged original Player/SimulationRuntime/native movement/flight recording lag history before movement under loop_count-1, including physical crouch/hover and respawn generations. No policy method substitution.'},indent=2)+'\n')
 print('connected lag history operations',len(rows),'bytes',path.stat().st_size)
finally:time.monotonic,time.time=original
