"""Execute unchanged original queued-owner history and causal lookup, with real movement/flight state."""
import gzip,hashlib,io,json,random,time
from generate_flight_golden import FlightFixture,ROOT,OUT,original,bits,num,scalar
from generate_simulation_golden import vector

def anchor(row):
 return '-' if row is None else '|'.join(map(str,[row.stamp,vector(row.position),vector(row.velocity),scalar(row.queued_server_tick),row.queued_owner_sequence]))
def state(p):return '#'.join([vector(p._spawn_owner_anchor),vector(p.last_advertised_owner_position),';'.join(anchor(row) for row in p._owner_anchor_history) or '-'])
class Fixture(FlightFixture):
 def op(self,op,arg):
  p=self.p;a=arg.split(',');query='-'
  if op=='row':
   self.events=[];self.wire=[];p.record_owner_anchor(int(a[0]),tuple(num(x) for x in a[1:4]),tuple(num(x) for x in a[4:7]),queued_server_tick=None if a[7]=='-' else int(a[7]),queued_owner_sequence=None if a[8]=='-' else int(a[8]))
   result=self.snapshot(),'-','-',self.flight()
  elif op=='query':
   self.events=[];self.wire=[];loop=None if a[0]=='-' else int(a[0]);tick=None if a[1]=='-' else int(a[1]);seq=None if a[2]=='-' else int(a[2]);entry=p._owner_anchor_entry_before_input(loop,source_received_server_tick=tick,source_received_owner_sequence=seq)
   query=('-' if entry is None else anchor(entry[1]))+'#'+vector(p._owner_anchor_before_input(loop,source_received_server_tick=tick,source_received_owner_sequence=seq));result=self.snapshot(),'-','-',self.flight()
  else:result=super().op(op,arg)
  return (*result,state(p),query)

def generate():
 rows=[]
 try:
  for group in range(5):
   f=Fixture(group);rng=random.Random(9025+group)
   def emit(op,arg):rows.append((group,group,op,arg,*f.op(op,arg)))
   def row(stamp,position,velocity,tick=None,sequence=None):emit('row',','.join(map(str,[stamp,*(bits(v) for v in position),*(bits(v) for v in velocity),scalar(tick),scalar(sequence)])))
   def query(loop=None,tick=None,sequence=None):emit('query',','.join(map(scalar,[loop,tick,sequence])))
   if group<3:
    emit('equip',f'{group},0,1');emit('negotiate',str(group))
    for i in range(220):
     label=100+i
     if i==110:emit('spawn',','.join(bits(v) for v in (36,36,20)))
     emit('receive',','.join(map(str,[label,0,152 if i>=3 else 24,rng.randrange(256),bits(1),bits(0),bits(0)])));emit('tick',bits(1/60))
     if i%2==0:row(label,f.p.position,f.p.velocity,f.server.loop_count)
     if i%9==0:row(label,f.p.position,(-0.0,.00003,-.5),None)
     query(label,f.server.loop_count+100,f.p._current_input_owner_sequence)
     query(label+1,None,f.p._owner_timeline_sequence+1)
   else:
    for i in range(260):
     row(i//2,(i/8,-i/16,-0.0),(0,.125,-.25),1000 if i%2==0 else None, i//3-10 if group==4 else None)
     if i%17==0:
      query(i//2,100000,None);query(i//2+1,-1000,i//3-9 if group==4 else i)
    for label in (None,-1,0,50,127,128,1000):
     for seq in (None,-10,0,128,500):query(label,seq,seq)
    emit('spawn',','.join(bits(v) for v in (40,41,42)));query();query(1000)
  data=('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode();buffer=io.BytesIO()
  with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(data)
  path=OUT/'owner-history.tsv.gz';path.write_bytes(buffer.getvalue())
  sources=['server/player.py','server/flight_profile.py','server/handlers/movement.py']
  (OUT/'owner-provenance.json').write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','operations':len(rows),'groups':5,'method_substitutions':False,'source_files':{n:hashlib.sha256((ROOT/n).read_bytes()).hexdigest() for n in sources},'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':'Original Player.record_owner_anchor, causal lookup and real input/mover/canopy integration. Row queue notifications are controlled inputs, not a real network delivery oracle.'},indent=2)+'\n')
  print('owner history operations',len(rows),'bytes',path.stat().st_size)
 finally:time.monotonic,time.time=original

if __name__=='__main__':generate()
