"""Execute unchanged async packet-71 handler, carry helpers, Player/native body and registry."""
import asyncio,gzip,hashlib,io,json,pathlib,struct,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import vxl,world
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.player import Player
from server.class_selection import ClassSelection
from server.pickups import broadcast_pickup,broadcast_drop
from server.entities.registry import EntityRegistry
from server.handlers.world import handle_drop_pickup
from protocol.handler_registry import HANDLERS
from shared.packet import DropPickup
assert HANDLERS[71] is handle_drop_pickup
bits=lambda v:struct.pack('>d',float(v)).hex()
vec=lambda v:','.join(map(bits,v))
opt=lambda x:'-' if x is None else str(x)
def state(p):return ','.join([opt(p.pickup_id),str(int(p.pickup_burdensome)),opt(p.pickup_state),str(int(p._world_object.burdened)),str(int(p.alive)),str(int(p.spawned))])
def entity(e):
 return ';'.join([str(e.entity_id),str(e.type),str(e.state),str(e.player_id),e.kind,vec((e.x,e.y,e.z)),vec(e.vel),bits(e.radius),str(int(e.alive)),str(int(e.wire_visible)),str(e.face),str(int(e.behavior is None)),vec(e.home)])
async def generate():
 rows=[];counts={k:0 for k in ['fallback','consumed','suspended','faults','rejected']}
 for trace in range(16):
  raw=(OUT/'world/empty.vxl').read_bytes();wm=WorldManager(NS(game_rules=GameRules.server_defaults(),game_mode='tdm'));wm.map=vxl.VXL(None,raw,len(raw));wm.world=world.World(wm.map)
  wire=[];events=[];fail=[False]
  def send(data,**kw):
   assert kw.get('reliable') is True
   wire.append(bytes(data).hex())
   if fail[0]:raise RuntimeError('send-fault')
  registry=EntityRegistry();server=NS(world_manager=wm,config=NS(game_rules=GameRules.server_defaults(),game_mode='tdm'),players={},connections={},mode=NS(),broadcast=send,loop_count=0,entity_registry=registry,queue_mode_event=lambda *a:None)
  p=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda *a,**kw:None));server.players[0]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)))
  for case in range(128):
   p.spawn(36,36,97);registry.clear();wire.clear();events.clear();fail[0]=False
   type=14+trace%3;initial_state=trace%5-1;broadcast_pickup(server,p,type,burdensome=trace%2,state=initial_state);wire.clear()
   scenario=(case//2)%16;deferred=bool(case%2);server.loop_count=1000+trace*128+case
   position=[(36.5,36.25,97.),(41.,36.,97.),(41.015625,36.,97.),(33.,40.,97.)][(case//32+trace)%4]
   velocity=[(0.,0.,0.),(999.,-300.,40.),(-8.,6.,0.),(1e308,1e308,-1e308)][(case//16+trace)%4]
   packet=DropPickup();packet.player_id=254;packet.loop_count=-12345;packet.pickup_id=type;packet.position=position;packet.velocity=velocity
   guard=0 if case<64 else case//16-3
   if guard in (1,2):p.alive=False;p.spawned=False
   elif guard==3:p.pickup_id=None
   elif guard==4:packet.pickup_id=14+(trace+1)%3
   gate=asyncio.get_running_loop().create_future()
   async def hook(player,pos,vel):
    assert player is p and pos==packet.position and vel==packet.velocity
    events.append('begin')
    if scenario==10:raise RuntimeError('hook-fault')
    if deferred:await gate
    events.append('resume')
    if scenario==11:raise RuntimeError('hook-fault')
    if scenario in (5,6):broadcast_drop(server,p,(36.,36.,97.),(0.,0.,0.))
    return True if scenario in (3,4,5) else None if scenario==2 else False
   server.mode=NS() if scenario==0 else NS(handle_drop_pickup=hook)
   fail[0]=scenario==12
   task=asyncio.create_task(handle_drop_pickup(server,p,packet));await asyncio.sleep(0)
   pending=not task.done();before=state(p)+';'+str(len(registry.all()))+';'+'|'.join(wire)+';'+','.join(events)
   if pending:
    counts['suspended']+=1
    if scenario==7:
     broadcast_drop(server,p,(36.,36.,97.),(0.,0.,0.));broadcast_pickup(server,p,14+(trace+1)%3,burdensome=True,state=7)
    elif scenario==8:p.set_position(100.,100.,100.)
    elif scenario==9:p.alive=False;p.spawned=False
    elif scenario==13:p.pickup_state=9;server.loop_count+=70000
    elif scenario==14:p.spawn(36,36,97)
    gate.set_result(None)
   outcome='ok'
   try:await task
   except RuntimeError as e:outcome=str(e);counts['faults']+=1
   if registry.all():counts['fallback']+=1
   if guard:counts['rejected']+=1
   if scenario in (3,4,5) and 'begin' in events:counts['consumed']+=1
   rows.append((trace,case,scenario,int(deferred),guard,type,initial_state,1000+trace*128+case,packet.pickup_id,vec(position),vec(velocity),int(pending),before,outcome,state(p),'|'.join(wire) or '-',','.join(events) or '-', '|'.join(entity(e) for e in registry.all()) or '-'))
 assert all(counts.values()),counts
 return rows,counts
old=time.time,time.monotonic;time.time=lambda:1000.;time.monotonic=lambda:1000.
try:rows,counts=asyncio.run(generate())
finally:time.time,time.monotonic=old
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'objective-drop.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'objective-drop-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',operations=len(rows),coverage=counts,fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/handlers/world.py','protocol/handler_registry.py','server/pickups.py','server/player.py','server/entities/registry.py','aoslib/world.pyx','shared/packet.pyx']},method_substitutions=False,scope='Actual unchanged registered async handler, original carry helpers/Player/native body/registry. Explicit controlled absent and async mode callbacks are boundary inputs, not concrete objective-mode implementations. Suspended state changes, carried type/state/loop rereads, early guards, mode consumption, hook/send faults, exact state and reliable wire; no full outer packet-drain scheduler or dropped-object physics claim.'),indent=2)+'\n');print('Objective drop requests',len(rows),counts,'bytes',p.stat().st_size)
