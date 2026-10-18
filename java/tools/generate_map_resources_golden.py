"""Unchanged map-resource assembly, real lives/native terrain, and compiled entity/audio packets.

Clocks and terminal send IO are controlled. Profile hooks observe original score
and achievement delivery without replacing policy functions. Achievement-engine
persistence/unlocks and complete round/mode integration are outside this fixture.
"""
import gzip,hashlib,io,json,pathlib,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT)
from aoslib import world
from server.runtime_vxl import ServerVXL
from server.world_manager import WorldManager
from server.map_metadata import MapMetadata,MapEntitySpec
from server.map_resources import MapResourceService
from server.entities.registry import EntityRegistry,EntityContext
from server.game_rules import GameRules
from server.player import Player
from server.class_selection import ClassSelection
from server import achievements,combat_scores
from shared.packet import CreateEntity,DestroyEntity
bits=lambda v:struct.pack('>d',float(v)).hex()
rows=[];clock=[1000.];old=time.time,time.monotonic;time.time=lambda:clock[0];time.monotonic=lambda:clock[0]
def row(*values):rows.append('\t'.join(map(str,values)))
def packet(e):p=CreateEntity();p.set_entity(e.to_wire_entity());return bytes(p.generate()).hex()
def baseline(v):return ','.join(map(str,v))
def profile(frame,event,arg):
 if event!='call':return
 if frame.f_code is achievements.crate_baseline.__code__:events.append('baseline:'+baseline(achievements.AchievementEngine.crate_baseline(frame.f_locals['player'])))
 elif frame.f_code is combat_scores.record_crate.__code__:events.append('award:'+str(frame.f_locals['award']))
 elif frame.f_code is achievements.crate_collected.__code__ and frame.f_locals.get('method')=='crate_collected':
  args=frame.f_locals['args'];events.append('collected:'+str(args[1])+':'+baseline(args[2]))
def observe(reg,wm,p):
 for e in reg.all():row('V',e.entity_id,e.type,e.kind,','.join(map(bits,(e.x,e.y,e.z))),int(e.alive),bits(e.respawn_at),'-' if e.color is None else ','.join(map(str,e.color)),int(e.wire_visible),int(e.behavior is not None),int(e.falling),bits(e.fall_accumulator))
 row('O','|'.join(events) or '-')
 row('P',p.health,p.max_health,p.blocks,p.ammo_clip,p.ammo_reserve)
 row('Q',';'.join(f'{a},{n}' for a,n in sorted(combat_scores.round_stats(p).awards.items())) or '-')
 row('L',';'.join(f'{x},{y},{z},{rgb}' for (x,y,z),rgb in wm.static_light_cells.items()) or '-')
 row('N',wm.topology_version,len(wm.dirty_columns));row('Z')
try:
 for trace in range(48):
  clock[0]=1000.;events=[]
  raw=(bytes([2,80,80,0])+struct.pack('<I',0x8000ff00)+bytes([0,239,239,81])+struct.pack('<I',0x80334455))*4 if trace%2==0 else bytes([0,90,90,0])+struct.pack('<I',0x800000ff)
  rules=GameRules.server_defaults();delay=[0.,.25,15.,60.][trace%4];rules.values['RULE_CRATES_SPAWN_TIME']=delay;wire=trace%3!=0;enabled=trace%7!=0;sky=['WW1.txt','LunarBase.txt','User_Lunar.txt','Training.txt',None][trace%5]
  wm=WorldManager(NS(game_rules=rules,game_mode='tdm'));wm.map=ServerVXL(None,raw,len(raw),source_format='retail');wm.world=world.World(wm.map);wm.map_raw_bytes=raw;wm.map_name='ResourceFixture';wm.map_metadata=MapMetadata(skybox_name=sky)
  reg=EntityRegistry();config=NS(game_rules=rules,entities_wire_ready=wire,game_mode='tdm',default_mode='tdm');server=NS(config=config,world_manager=wm,entity_registry=reg,mode=NS(map_pickups_enabled=enabled),players={},connections={},queue_mode_event=lambda *a:None)
  def create(e):events.append('create:'+packet(e))
  def destroy(id):
   assert reg.get(id) is not None,'Destroy must precede registry removal'
   p=DestroyEntity();p.entity_id=id;events.append('destroy:'+bytes(p.generate()).hex())
  server.broadcast_create_entity=create;server.broadcast_destroy_entity=destroy;server.broadcast=lambda data,**kw:events.append('flyby:'+bytes(data).hex())
  p=Player(0,'Life',2,6,NS(server=server,in_game=True,send=lambda data,*a,**kw:events.append('owner:'+bytes(data).hex())));server.players[0]=p;p.apply_class_selection(ClassSelection(0,(5,6,23)));p.spawn(36,36,97);p.max_health=[50,100,200][trace%3];p.health=25;p.blocks=0;p.ammo_clip=2;p.ammo_reserve=0
  row('E',trace,raw.hex(),int(wire),int(enabled),bits(delay),sky or '-',p.max_health)
  # Authored resources: type 6 is a valid inert service input even though the
  # current metadata parser produces only the three legacy crate types.
  specs=[]
  if trace%4!=0:
   specs=[MapEntitySpec(t,'fixture',40.25+t,50.75,30.5,'fixture') for t in (3,4,5,6)]
   specs.append(MapEntitySpec(3,'fixture',-1,0,0,'fixture'))
  if trace%8==5:specs=[MapEntitySpec(3,'fixture',512,0,0,'fixture')]
  for i in range(2055 if trace==47 else trace%5):specs.append(MapEntitySpec(13,'fixture',-1 if i==0 and trace%2 else 12.25+i%480,25.75,20.25,'fixture',(i%256,22,33)))
  if trace%6==0:specs.append(MapEntitySpec(13,'fixture',5,6,7,'fixture',None))
  wm.map_metadata.entities=specs
  for s in specs:row('S',s.entity_type,bits(s.x),bits(s.y),bits(s.z),'-' if s.color is None else ','.join(map(str,s.color)))
  for family in range(2):
   if (trace+family)%3:wm.map_metadata.static_light_colors[family]=(224,172,29) if family==0 else (250,250,200);row('C',family,','.join(map(str,wm.map_metadata.static_light_colors[family])))
  for x,y,z in [(72,100,100),(56,100,100),(64,108,100),(456,400,100),(440,400,100),(448,408,100),(248,256,100),(256,256,100),(264,256,100),(43,50,210),(44,50,210),(45,50,210),(46,50,210)]:wm.set_block(x,y,z,True,0x406080);row('W',x,y,z)
  # Original base-anchor policy is independently ported. This component records
  # its resolved inputs rather than replacing or re-testing that policy here.
  wm._team_base_anchors={2:(64.,100.,97.),3:(448.,400.,97.)}
  for team in (2,3):row('A',team,','.join(map(bits,wm.team_base_anchor(team))))
  for i,kind in enumerate(['landmine','map_ammo','map_health','map_block','map_jetpack','map_flare','projectile']):
   e=reg.place(3,1,2,3,kind=kind,wire_visible=i%3!=0);e.alive=i%4!=0;row('I',kind,int(e.wire_visible),int(e.alive))
  service=MapResourceService(server);events.clear();row('B');service.rebuild();observe(reg,wm,p)
  sys.setprofile(profile)
  for e in list(reg.all()):
   if e.behavior is None:continue
   events.clear();row('T',e.entity_id,bits(clock[0]));ctx=EntityContext(.05,clock[0],[],world=wm,server=server,create=create,destroy=destroy);e.behavior.on_touch(e,p,ctx);observe(reg,wm,p)
  sys.setprofile(None)
  events.clear();clock[0]+=delay+1;row('R',bits(clock[0]));reg.tick(EntityContext(.05,clock[0],[],world=wm,server=server,create=create,destroy=destroy));observe(reg,wm,p)
  events.clear();server.config.entities_wire_ready=not wire;row('B',int(not wire));service.rebuild();observe(reg,wm,p)
finally:sys.setprofile(None);time.time,time.monotonic=old
raw=('\n'.join(rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
p=OUT/'map-resources.tsv.gz';p.write_bytes(buffer.getvalue());(OUT/'map-resources-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',traces=48,fixture_sha256=hashlib.sha256(p.read_bytes()).hexdigest(),source_files_sha256={s:hashlib.sha256((ROOT/s).read_bytes()).hexdigest() for s in ['server/map_resources.py','server/entities/behaviors.py','server/entities/registry.py','server/runtime_vxl.py','server/world_manager.py','server/player.py','server/audio.py','server/achievements.py','server/combat_scores.py','shared/packet.pyx']},method_substitutions=False,scope='Original MapResourceService, actual native world and Player resource refills, original registry, audio and compiled wire codecs. Resolved base anchors and terminal send IO controlled; profile observes unchanged award/achievement hook ordering, with actual round crate counters. No persistent achievement-engine unlock or full round/mode/server assembly claim.'),indent=2)+'\n');print('Map resource traces 48; bytes',p.stat().st_size)
