"""Unchanged full SimulationRuntime/Player/native mover with real dense-sequence blast impulses."""
import gzip,hashlib,io,json,pathlib,sys
from generate_simulation_golden import Fixture,bits,vector,clock,original,time
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';rows=[]
def pending(p):return ';'.join(str(e.target_input_sequence)+','+vector(e.origin)+','+','.join(bits(v) for v in [e.blast_radius,e.knockback_min,e.knockback_max]) for e in p._pending_explosion_impulses) or '-'
try:
 for group in range(12):
  cid=group%18;f=Fixture(cid)
  def emit(op,arg):
   result='-'
   if op in ['explosion','bot']:
    f.events=[];f.wire=[]
    if op=='bot':f.p.is_bot=arg=='1'
    else:
     a=arg.split(',');n=lambda v:__import__('struct').unpack('>d',bytes.fromhex(v))[0];result=f.p.queue_explosion_impulse(int(a[0]),tuple(n(x) for x in a[1:4]),*(n(x) for x in a[4:]));result='-' if result is None else str(result)
    values=(f.snapshot(),';'.join(f.events) or '-',';'.join(f.wire) or '-')
   else:values=f.op(op,arg)
   rows.append((group,cid,op,arg,result,*values,pending(f.p)))
  def receive(label,movement=0):emit('receive',','.join(map(str,[label,movement,24,0,bits(1),bits(0),bits(0)])))
  def tick():emit('tick',bits(1/60))
  def explosion(delay=3):emit('explosion',','.join(map(str,[delay,*[bits(v) for v in (34,36,97,6,.1,.25)]])))
  receive(100);tick();explosion(0 if group%3==0 else 3)
  for label in [104,110,108,120]:receive(label,32 if group%2 else 1);tick()
  for _ in range(16):tick()
  if group%3==1:
   for _ in range(65):explosion()
  if group%3==2:emit('bot','1');explosion();emit('bot','0')
  if group%4==0:emit('spawn',','.join(bits(v) for v in (36,36,97)))
  emit('settings',','.join([bits(1),'0','1','8','0',bits(8),'24','1','6']))
  for label in range(200,280):receive(label,32 if group%2 else 1)
  explosion(1)
  for index in range(85):receive(280+index,32 if group%2 else 1);tick()
  emit('spawn',','.join(bits(v) for v in (36,36,97)));receive(700);tick()
finally:time.monotonic,time.time=original
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'explosion-timeline.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'explosion-timeline-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),groups=12,source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/player.py','server/simulation_runtime.py','server/handlers/movement.py','aoslib/world.pyx']},fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged SimulationRuntime, Player queue/apply explosion impulse methods and native World mover. Full body/façade/chronology snapshots, pending parameters, observer/wire boundaries. Sparse/reordered/synthesized labels, dense sequences, overflow/bot fallback, backlog discards and life reset. Controlled input/clocks/config, not full gameplay or real-client timing proof.'),indent=2)+'\n');print('Explosion timeline operations',len(rows))
