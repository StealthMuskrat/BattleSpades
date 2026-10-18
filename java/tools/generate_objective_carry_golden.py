"""Unchanged objective helper carry/burden, native tuple codecs and send-failure boundaries."""
import gzip,hashlib,io,json,math,pathlib,random,struct,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.player import Player
from server.class_selection import ClassSelection
from server.pickups import broadcast_pickup,broadcast_drop,sanitize_drop
from shared.packet import DropPickup,PickPickup
from shared.bytes import ByteReader
bits=lambda v:struct.pack('>d',float(v)).hex();vec=lambda v:','.join(map(bits,v));rows=[]
def optional(value):return '-' if value is None else str(value)
def vectors(value):return '-' if value[0] is None else vec(value[0])+';'+vec(value[1])
old=time.time,time.monotonic;time.time=lambda:1000.;time.monotonic=lambda:1000.
try:
 for trace in range(32):
  raw=(OUT/'world/empty.vxl').read_bytes();wm=WorldManager(NS(game_rules=GameRules.server_defaults(),game_mode='tdm'));wm.map=vxl.VXL(None,raw,len(raw));wm.world=world.World(wm.map);wire=[];fail=[False]
  def send(data,**kw):
   assert kw.get('reliable') is True
   wire.append(bytes(data).hex())
   if fail[0]:raise RuntimeError('controlled reliable send fault')
  server=NS(world_manager=wm,config=NS(game_rules=GameRules.server_defaults(),game_mode='tdm'),players={},connections={},mode=NS(),broadcast=send,loop_count=0,queue_mode_event=lambda *a:None)
  p=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda *a,**kw:None));server.players[0]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36,36,97)
  for step in range(128):
   wire.clear();fail[0]=(step+trace)%13==0;kind=(step+trace)%8;type=[14,15,16,3,255][(step//3+trace)%5];burden=(step+trace)%2;state=[-1,0,1,2,3][step%5];server.loop_count=step*17-1000
   positions=[(36.,36.,97.),(41.,36.,97.),(math.nextafter(41.,math.inf),36.,97.),(33.,40.,97.),(1e308,0.,0.),(float('nan'),36.,97.),(float('inf'),36.,97.),None,(36.25,35.5,96.75)]
   velocities=[(0.,0.,0.),(10.,0.,0.),(15.,0.,0.),(999.,-300.,40.),(1e308,1e308,-1e308),(float('nan'),0.,0.),(float('inf'),0.,0.),(1e-8,-0.,-1e-8),(-8.,6.,0.)]
   pos=positions[(step//2+trace)%len(positions)];vel=velocities[(step//7+trace)%len(velocities)];op='P' if kind<3 else 'D' if kind<6 else 'S' if kind==6 else 'R';result='-'
   try:
    if op=='P':result=str(int(broadcast_pickup(server,p,type,burdensome=burden,state=state)))
    elif op=='D':
     value=broadcast_drop(server,p,pos,vel);result='-' if value is None else f'{value[0]},{value[1]};'+vec(value[2])+';'+vec(value[3])
    elif op=='S':result=vectors(sanitize_drop(p,[14,15,16][trace%3],pos,vel))
    else:p.spawn(36,36,97)
   except OverflowError:result='overflow'
   except RuntimeError:result='send-fault'
   rows.append((trace,step,op,type,burden,state,server.loop_count,int(fail[0]),'-' if pos is None else vec(pos),vec(vel),result,','.join(map(str,[optional(p.pickup_id),int(p.pickup_burdensome),optional(p.pickup_state),int(p._world_object.burdened)])),'|'.join(wire) or '-'))
finally:time.time,time.monotonic=old
rng=random.Random(140516)
for case in range(8192):
 v=[rng.uniform(-1,1) for _ in range(3)];length=math.sqrt(sum(x*x for x in v));radius=[0.,1.,5.,math.nextafter(5.,0.),math.nextafter(5.,math.inf)][case%5]
 pos=tuple(base+value/length*radius for base,value in zip((36.,36.,97.),v))
 vel=tuple(math.ldexp(rng.uniform(-1,1),rng.randrange(-500,550)) if case%4==0 else rng.uniform(-1000,1000) for _ in range(3))
 if case%257==0:pos=(1e308,0.,0.)
 try:result=vectors(sanitize_drop(p,14+case%3,pos,vel))
 except OverflowError:result='overflow'
 rows.append(('sanitize',case,14+case%3,vec(pos),vec(vel),result))
for case in range(512):
 p=DropPickup();p.loop_count=(case*10000019+2147483648)%4294967296-2147483648;p.player_id=case%255;p.pickup_id=14+case%3
 values=[((case*37)%64000-32000+.5)/64,((case*73)%64000-32000+.5)/64,((case*89)%64000-32000+.5)/64,(-300+case%600)/7,(-200+case%400)/11,(-100+case%200)/13]
 values[case%6]=math.nextafter(values[case%6],math.inf if case%2 else -math.inf);p.position=tuple(values[:3]);p.velocity=tuple(values[3:]);raw=bytes(p.generate());decoded=DropPickup(ByteReader(raw[1:]));rows.append(('codec',case,p.loop_count,p.player_id,p.pickup_id,vec(p.position),vec(p.velocity),raw.hex(),bytes(decoded.generate()).hex(),vec(decoded.position),vec(decoded.velocity)))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'objective-carry.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'objective-carry-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=4096,sanitize_cases=8192,codec_cases=512,fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/pickups.py','server/player.py','aoslib/world.pyx','shared/packet.pyx','shared/constants.py']},method_substitutions=False,scope='Actual unchanged carry/drop/sanitize helpers over original Player with native body; controlled terminal reliable send faults, spawn inputs and clocks. Includes compensated arbitrary-vector/radius-boundary sums and mixed-magnitude speeds. Strict binary64 sanitized tuples, native codecs and burden/carry/send ordering. No objective mode ownership, async handler composition, dropped-object simulation or full gameplay claim.'),indent=2)+'\n');print('Objective carry operations 4096; arbitrary sanitize cases 8192; native codec cases 512; bytes',p.stat().st_size)
