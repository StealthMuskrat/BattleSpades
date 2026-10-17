"""Execute unmodified server.lag_compensation with controlled bodies, clocks and arrivals.

sys.setprofile observes original diagnostic calls; no implementation is replaced.
"""
import gzip,hashlib,io,json,logging,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from server import lag_compensation as L
logging.disable(logging.CRITICAL)
def bits(v):return struct.pack('>d',float(v)).hex()
def number(v):return struct.unpack('>d',bytes.fromhex(v))[0]
def scalar(v):return '-' if v is None else str(int(v))
def vec(v):return ','.join(map(bits,v))
def sample(row):return '-' if row is None else ','.join([str(row[0]),*map(bits,row[1:7]),*map(scalar,row[7:])])
def history(p):
 h=getattr(p,'_lag_history',None)
 return '0|-|-' if h is None else str(h.epoch)+'|'+sample(h.last)+'|'+hashlib.sha256(';'.join(sample(s) for s in h.ring).encode()).hexdigest()
def body(b):return ('R' if isinstance(b,L.RewoundBody) else 'L')+','+vec((b.x,b.y,b.z,b.o_x,b.o_y,b.o_z))+','+str(int(b.hitbox_crouched))+','+bits(getattr(b,'rewound_ticks',0))
rows=[]
for group in range(6):
 p=NS(x=0.,y=0.,z=0.,o_x=1.,o_y=0.,o_z=0.,hitbox_crouched=False,alive=True,spawned=True,replication_generation=0);context=None
 def emit(op,arg):
  global context
  result='-'
  if op=='state':
   a=arg.split(',');p.x,p.y,p.z,p.o_x,p.o_y,p.o_z=map(number,a[:6]);p.hitbox_crouched,p.alive,p.spawned=map(lambda v:bool(int(v)),a[6:9]);p.replication_generation=int(a[9])
  elif op=='record':L.record_player(p,int(arg))
  elif op=='forget':L.forget_player(p)
  elif op=='body':
   tick,view=arg.split(',');result=body(L._rewound_body(p,int(tick),number(view)))
  elif op=='context':
   tick,view=arg.split(',');context=L.RewindContext(int(tick),number(view),0,0,0,None)
  elif op=='cached':result=body(context.body(p))
  else:raise AssertionError(op)
  rows.append((group,op,arg,history(p)+'#'+result))
 for tick in range(270):
  position=(tick*.125+(20 if group==1 and tick>=70 else 0),tick*.25,40+tick*.1)
  aim=(1 if tick%5<3 else -1,0.,0.) if group%2 else (1.,tick%7/8.,.125)
  alive=not(100<=tick<104);life=int(tick>=104)
  emit('state',','.join([*map(bits,(*position,*aim)),str(tick%2),str(int(alive)),str(int(alive)),str(life)]))
  if group==2 and tick==150:emit('forget','-')
  if group!=3 or tick%3:emit('record',str(tick-1 if group==4 else tick))
  if tick%11==0:emit('record',str(tick))
  for age in (0.,.0000005,.5,1.,1.0000011,20.25,128.,260.):emit('body',str(tick+1)+','+bits(tick+1-age))
  if tick%31==0:
   emit('context',str(tick+1)+','+bits(tick-.5));emit('cached','-')
   emit('state',','.join([*map(bits,(position[0]+.25,*position[1:],*aim)),str(tick%2),'1','1',str(life)]));emit('cached','-')
def write(name,data,scope):
 raw=('\n'.join('\t'.join(map(str,row)) for row in data)+'\n').encode();buffer=io.BytesIO()
 with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as z:z.write(raw)
 path=OUT/(name+'.tsv.gz');path.write_bytes(buffer.getvalue())
 (OUT/(name+'-provenance.json')).write_text(json.dumps({'source_commit':'6216b771282d2d349ccd67ee58c378f035c83553','source_file_sha256':hashlib.sha256((ROOT/'server/lag_compensation.py').read_bytes()).hexdigest(),'operations':len(data),'fixture_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'scope':scope,'method_substitutions':False},indent=2)+'\n')
 print(name,len(data),'operations',path.stat().st_size,'bytes')
write('lag-history',rows,'Unmodified target ring, epochs, life/death checks, interpolation and context identity cache, with controlled target properties.')
rows=[];rng=random.Random(2560);events=[]
def profile(frame,event,arg):
 if event=='call' and frame.f_code is L._report.__code__:events.append((frame.f_locals['kind'],dict(frame.f_locals['detail'])))
for case in range(7000):
 loop=rng.randrange(1,5000);dt=[0.,1/60,1/120,1/30][case%4];rtt=[0.,1.,8.,16.7,100.,125.05,249.95,500.,2000.][case%9];bot=case%17==0;label=1000+case;claimed=[0,loop-1,loop-2,max(1,loop-60),loop+5][case%5]
 settings=[case%19!=0,[0.,250.,500.][case%3],[-1.,0.,50.][case%3],[0.,30.][case%2],[0.,50.,400.][case%3],case%8]
 config=NS(**dict(zip(('lag_compensation_enabled','lag_compensation_max_ms','lag_compensation_extra_ms','lag_compensation_view_delay_ms','lag_compensation_late_shot_ms','worldupdate_broadcast_interval'),settings)))
 arrivals={} if case%11==0 else {label+[-8,-3,-1,0,1,3,8][case%7]:loop-[0,1,2,10,70][case%5]}
 shooter=NS(is_bot=bot,connection=NS(peer=NS(roundTripTime=rtt)),label_arrival_tick=arrivals.get)
 server=NS(loop_count=loop,tick_interval=dt,config=config);packet=NS(loop_count=label,shot_on_world_update=claimed);events.clear()
 sys.setprofile(profile)
 try:context=L.rewind_targets(server,shooter,packet)
 finally:sys.setprofile(None)
 result='-' if context is None else ','.join([str(context.current_tick),bits(context.view_tick),bits(context.rewind_ms),bits(context.allowed_ms),bits(context.rtt_ms),scalar(context.snapshot_tick)])
 diagnostics=';'.join(kind+','+','.join(k+'='+bits(v) for k,v in sorted(detail.items())) for kind,detail in events) or '-'
 args=','.join([str(loop),bits(dt),str(int(bot)),bits(rtt),str(label),str(claimed),str(int(settings[0])),*map(bits,settings[1:5]),str(settings[5]),':'.join(str(k)+'='+str(v) for k,v in arrivals.items()) or '-'])
 rows.append((args,result+'#'+diagnostics))
write('lag-decisions',rows,'Unmodified rewind decision/late-label search and log-only diagnostics; controlled settings, ENet RTT, server clock and ClientData arrival dates.')
