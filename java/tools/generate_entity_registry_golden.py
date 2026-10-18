"""Execute the unchanged source registry/base damage lifecycle and native entity codec.
Only behavior-domain callbacks, player inputs and lifecycle/send IO are controlled.
"""
import gzip,hashlib,io,json,pathlib,random,struct,sys
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from server.entities.registry import EntityRegistry,EntityContext,send_create_entity_to
from server.entities.behaviors import EntityBehavior,DamageableEntityBehavior
from shared.packet import CreateEntity
bits=lambda v:struct.pack('>d',float(v)).hex()
def packet(e):
 p=CreateEntity();p.set_entity(e.to_wire_entity());return bytes(p.generate()).hex()
rows=[]
for trace in range(48):
 rng=random.Random(3201+trace);reg=EntityRegistry();events=[];wire=[];fault=[False];player=NS(id=0,x=0.,y=0.,z=0.);known=set();saved={}
 def send(data,**kw):
  assert kw=={'reliable':True}
  if fault[0]:raise RuntimeError('Controlled send fault')
  wire.append(bytes(data).hex())
 connection=NS(send=send,known_entity_ids=known)
 ctx=EntityContext(1/60,100.,[player],server=NS(entity_registry=reg),create=lambda e:events.append('C:'+str(e.entity_id)),destroy=lambda id:events.append('D:'+str(id)))
 def mutate(e,action,hook):
  other=reg.get(e.entity_id+1)
  if action==1 and other is not None:reg.remove(other.entity_id)
  elif action==2 and other is not None:other.alive=False;other.respawn_at=ctx.now
  elif action==3 and other is not None:other.behavior=EntityBehavior()
  elif action==4:reg.place(4,e.x,e.y,e.z,behavior=Observed(0))
  elif action==5:reg.clear()
  elif action==6 and other is not None:other.x+=16
  elif action==7:e.alive=False;e.respawn_at=ctx.now
  elif action==8:player.x+=8
 class Observed(EntityBehavior):
  def __init__(self,action):self.action=action;self.touch_radius=2.5;self.fired=False
  def on_touch(self,e,p,c):
   events.append('U:'+str(e.entity_id))
   if not self.fired:self.fired=True;mutate(e,self.action,'U')
   return True
  def on_support_lost(self,e,c):
   events.append('S:'+str(e.entity_id)+':'+str(int(e.support_cell is None)))
   events.append('N:'+str(reg.support_removed((1,2,3),c)))
   super().on_support_lost(e,c)
 class Ticker(Observed):
  def on_tick(self,e,dt,c):
   events.append('T:'+str(e.entity_id))
   if not self.fired:self.fired=True;mutate(e,self.action,'T')
 class Inherited(Ticker):pass
 class Respawner(Observed):
  def on_respawn(self,e,c):
   events.append('R:'+str(e.entity_id)+':'+str(int(e.alive))+':'+bits(e.respawn_at));e.z+=.25
 def behavior(code,action):return [lambda:None,EntityBehavior,lambda:Observed(action),lambda:Ticker(action),lambda:Inherited(action),lambda:Respawner(action),lambda:DamageableEntityBehavior(5.)][code]()
 def state():
  parts=[]
  for e in reg.all():
   b=e.behavior;code=0 if b is None else 6 if isinstance(b,DamageableEntityBehavior) else 4 if isinstance(b,Inherited) else 3 if isinstance(b,Ticker) else 5 if isinstance(b,Respawner) else 2 if isinstance(b,Observed) else 1
   parts.append(','.join(map(str,[e.entity_id,int(e.alive),bits(e.respawn_at),bits(e.x),bits(e.y),bits(e.z),code,bits(b.health) if code==6 else '-',':'.join(map(str,e.support_cell)) if e.support_cell else '-',int(e.wire_visible),e.kind])))
  return '|'.join(parts) or '-'
 for step in range(160):
  events.clear();wire.clear();ctx.now=100.+step*.25;op=step%20;arg='-';result='-'
  if op in [0,1,2,3,4,5,6,7,8,9]:
   code=(step+trace)%7;action=(step//20+trace)%9;x=rng.choice([-8.5,-.25,0.,1.,7.9,8.,16.]);y=rng.choice([-.25,0.,8.]);z=rng.choice([0.,.5,2.5]);support=(1,2,3) if step%3 else (2,3,4);visible=bool(step%4);kind='projectile' if step%5==0 else 'crate';arg=','.join(map(str,[code,action,x,y,z,*support,int(visible),kind]));e=reg.place(4,x,y,z,behavior=behavior(code,action),support_cell=support,wire_visible=visible,kind=kind);saved[e.entity_id]=e;result=str(e.entity_id)
  elif op==10:
   cap=[None,0,1,3,-1,-2,100][(step//20+trace)%7];arg='none' if cap is None else str(cap)
   try:result=str(reg.tick(ctx,cap))
   except ZeroDivisionError:result='zero'
  elif op==11:
   e=rng.choice(list(saved.values()));e.alive=False;e.respawn_at=ctx.now;arg=str(e.entity_id)
  elif op==12:
   cell=(1,2,3) if step//20%2==0 else (2,3,4);arg=','.join(map(str,cell));result=str(reg.support_removed(cell,ctx))
  elif op==13:
   cap=[None,1,2][trace%3];arg='none' if cap is None else str(cap);result=str(reg.tick(ctx,cap))
  elif op==14:
   e=rng.choice(list(saved.values()));e.support_cell=(9,9,9);arg=str(e.entity_id)
  elif op==15:
   e=rng.choice(list(saved.values()));arg=str(e.entity_id);result=str(int(reg.remove(e.entity_id) is not None))
  elif op==16:
   id=rng.choice(list(saved));amount=rng.choice([-2.,0.,1.,5.,9.]);arg=str(id)+','+str(amount);reg.damage_entity(id,amount,None,ctx)
  elif op==17:
   e=rng.choice(list(saved.values()));fault[0]=step//20%3==0;arg=str(e.entity_id)+','+str(int(fault[0]))
   try:result=str(int(send_create_entity_to(connection,e)))
   except RuntimeError:result='sendfail'
  elif op==18:
   player.x=rng.choice([-8.,0.,8.,16.]);player.y=rng.choice([0.,8.]);arg=str(player.x)+','+str(player.y);result=str(reg.tick(ctx,2))
  else:
   if step//20==7:reg.clear();result='clear'
   else:result=','.join(str(e.entity_id) for e in reg.static_entities()) or '-'
  extras=';'.join([''.join('1' if reg.has_support_at(c) else '0' for c in [(1,2,3),(2,3,4),(9,9,9)]),','.join(map(str,sorted(known))) or '-',','.join(str(e.entity_id) for e in reg.due_respawns(ctx.now)) or '-'])
  rows.append((trace,step,op,arg,result,'|'.join(events) or '-',state(),extras,'|'.join(wire) or '-'))
assert any(r[4]=='sendfail' for r in rows) and any(r[8]!='-' for r in rows)
for hook in ['T:','U:','S:','R:','C:','D:']:
 assert any(hook in r[5] for r in rows),hook
# Wire values exercise native float32 assignment, signed uint16 IDs and optional color/UGC properties.
for case in range(512):
 reg=EntityRegistry();reg._next_id=(case*173)&65535;e=reg.place(case%40,case/7-30,case/9-20,case/13-10,yaw=case/17-12,state=case%4,color=None if case%3==0 else (case%256,(case*7)%256,(case*13)%256),player_id=case%255,vel=(case/11-20,-case/19,case/23),radius=case/29,face=case%6,fuse=case/31);e.ugc_mode=case;e.int_properties=(-case,case*12345);values=[e.entity_id,e.type,e.x,e.y,e.z,e.yaw,e.state,'-' if e.color is None else ':'.join(map(str,e.color)),e.player_id,*e.vel,e.radius,e.face,e.fuse,e.ugc_mode,*e.int_properties];rows.append(('wire',case,','.join(map(str,values)),packet(e)))
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'entity-registry.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'entity-registry-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/entities/registry.py','server/entities/behaviors.py','shared/packet.pyx']},registry_operations=48*160,wire_cases=512,fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged original registry, inert/damageable base behaviors and compiled Entity/CreateEntity codec. Controlled subclass callbacks, clocks, players and send IO; mutation snapshots, capped inherited ticks, spatial touch ordering, reentrant support loss, stale support index, respawn and known-ID/send-failure ordering. Does not establish concrete pickup/device/projectile/mode behavior or full server-loop integration.'),indent=2)+'\n');print('Entity registry operations',48*160,'wire cases',512,'bytes',path.stat().st_size)
