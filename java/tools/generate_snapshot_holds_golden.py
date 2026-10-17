"""Run unchanged original Connection hold methods with controlled packet-release events.

Extract original AST methods without rewriting their bodies, so native Connection
launcher dependencies are unnecessary. Real ENet ACK/release integration is tested separately.
"""
import ast,gzip,hashlib,io,json,pathlib,weakref
from types import SimpleNamespace as NS
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden'
from reference_imports import prepare
prepare(ROOT)
from shared.packet import CreatePlayer
source=ROOT/'server/connection.py';tree=ast.parse(source.read_text())
names={'_hold_snapshot_rows_for','reliable_unacked_through','held_snapshot_rows'}
constants={'SNAPSHOT_LIFECYCLE_PACKET_ID','SNAPSHOT_ENTITY_PACKET_ID','SNAPSHOT_HOLD_TIMEOUT_TICKS','RELIABLE_TRACK_TIMEOUT_TICKS','RELIABLE_TRACK_LIMIT'}
body=[n for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id in constants for t in n.targets)]
original=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Connection')
methods=[n for n in original.body if isinstance(n,ast.FunctionDef) and n.name in names];assert len(methods)==3 and len(body)==5
body.append(ast.ClassDef(name='OriginalHolds',bases=[],keywords=[],body=methods,decorator_list=[]))
module=ast.fix_missing_locations(ast.Module(body=body,type_ignores=[]));namespace={'weakref':weakref,'CreatePlayer':CreatePlayer}
exec(compile(module,str(source),'exec'),namespace);Original=namespace['OriginalHolds']
class Packet:
 def __init__(self,fail):self.callback=None;self.fail=fail
 def set_free_callback(self,callback):
  if self.fail:raise RuntimeError('controlled unsupported binding')
  self.callback=callback
 def release(self):
  if self.callback is not None:self.callback()
rows=[]
for group in range(4):
 connection=Original();connection.server=NS(loop_count=0);packets={}
 def emit(op,arg):
  result='-'
  if op=='queue':
   token,data,fail=arg.split(',');packet=Packet(bool(int(fail)));packets[int(token)]=packet;connection._hold_snapshot_rows_for(bytes.fromhex(data),packet)
  elif op=='release':packets[int(arg)].release()
  elif op=='tick':connection.server.loop_count=int(arg)
  elif op=='held':
   keys=connection.held_snapshot_rows();result=';'.join(sorted('E:'+str(key[1]) if isinstance(key,tuple) else 'P:'+str(key) for key in keys)) or '-'
  elif op=='pending':result=str(int(connection.reliable_unacked_through(int(arg))))
  else:raise AssertionError(op)
  rows.append((group,op,arg,str(getattr(connection,'reliable_send_index',0))+'|'+result))
 if group==0:
  for token,data,fail in [(0,'1c07',0),(1,'1c07',0),(2,'15409c',0),(3,'0500',0),(4,'1c08',1),(5,'1c',0),(6,'15',0),(7,'26409c',0)]:emit('queue',f'{token},{data},{fail}');emit('held','-')
  for token in (0,2,0,1,3,5,6):emit('release',str(token));emit('held','-');emit('pending','100')
 elif group==1:
  emit('queue','0,1c01,0');emit('queue','1,15ffff,0')
  for tick in (119,120,121,599,600,601):
   emit('tick',str(tick));emit('held','-')
   for index in (0,1,2,3):emit('pending',str(index))
 elif group==2:
  for i in range(32):emit('queue',f'{i},1c{i%4:02x},0')
  for i in range(31,-1,-1):emit('release',str(i));emit('held','-');emit('pending',str(i));emit('pending',str(i+1))
 else:
  for i in range(8200):emit('queue',f'{i},0500,0')
  for index in (0,1,7,8,9,8192,8200):emit('pending',str(index))
  for i in range(8,20):emit('release',str(i))
  emit('pending','20');emit('pending','21');emit('tick','601');emit('pending','8200')
raw=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
fixture=OUT/'snapshot-holds.tsv.gz';fixture.write_bytes(buffer.getvalue())
(OUT/'snapshot-holds-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_file_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'operations':len(rows),'groups':4,'original_method_bodies_unchanged':True,'methods':sorted(names),'fixture_sha256':hashlib.sha256(fixture.read_bytes()).hexdigest(),'scope':'Original Connection packet-release holds and ordered-barrier predicates; controlled clocks/headers/free callbacks, separate real ENet receipt tests.'},indent=2)+'\n')
print('snapshot holds operations',len(rows),'bytes',fixture.stat().st_size)
