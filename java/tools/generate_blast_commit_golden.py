"""Unchanged shared blast with actual original Player health, native map, registry and packet codecs.
Build-disabled commit traces isolate player/entity/prediction policy; separate terrain traces cover terrain.
"""
import ast,gzip,hashlib,io,json,logging,pathlib,struct,sys,time
from types import SimpleNamespace as NS
from reference_imports import prepare
ROOT=pathlib.Path(__file__).resolve().parents[2];OUT=ROOT/'java/src/test/resources/golden';prepare(ROOT);sys.path.insert(0,str(ROOT/'java/build/reference-python'))
from aoslib import vxl,world
from server.player import Player
from server.class_selection import ClassSelection
from server.world_manager import WorldManager
from server.game_rules import GameRules
from server.entities.registry import EntityRegistry
from server.entities.behaviors import DamageableEntityBehavior
from server import weapons_retail as retail
from reference_blast import methods
BattleSpadesServer=methods(ROOT)
bits=lambda v:struct.pack('>d',float(v)).hex()
vec=lambda v:','.join(map(bits,v))
clock=[100.,1000.];original=(time.monotonic,time.time);time.monotonic=lambda:clock[0];time.time=lambda:clock[1];logging.disable(logging.CRITICAL);rows=[]
try:
 for case in range(576):
  clock[:]=[100.,1000.];wire=[];events=[];spec=retail.RETAIL_EXPLOSIONS[case%len(retail.RETAIL_EXPLOSIONS)];damage=spec.damage if case%3 else spec.damage+1.;radius=[1.,4.,16.][case%3];kill=spec.kill_type;minimum=.1;maximum=.4;friendly=bool(case%2);ignore=case%7==0;frames=[None,0,1,3][case//3%4];wall=case//5%4;cid=case//23%18;protection=3. if case%5==0 else 0.;elapsed=0. if case%4==0 else 4.;weapon=[.5,1.,2.][case//7%3];modifier=['none','veto','half','double'][case//11%4];god=case%19==0;health=[20,100,300][case//13%3];crouch=case%2;self_min=.8 if case%9==0 else None;self_max=1.2 if case%9==0 else None
  rules=GameRules.server_defaults();rules.values.update(RULE_SPAWN_PROTECTION_TIME=protection,RULE_WEAPON_DAMAGE=weapon)
  config=NS(game_rules=rules,default_mode='tdm',game_mode='tdm',respawn_time=1.5,build_damage=False,friendly_fire=friendly,conduct=NS(grief_kick_enabled=False,grief_warn_points=1e9))
  manager=WorldManager(config);raw=(OUT/'world/empty.vxl').read_bytes();manager.map=vxl.VXL(None,raw,len(raw));manager.world=world.World(manager.map)
  server=NS(config=config,loop_count=1000,tick_interval=1/60,world_manager=manager,players={},teams={2:NS(color=(18,52,86)),3:NS(color=(50,60,70))},mode=NS(),entity_registry=EntityRegistry(),queue_mode_event=lambda *args:None,bot_stimuli=NS(publish=lambda *args,**kw:events.append('stimulus')))
  server._build_entity_ctx=lambda:BattleSpadesServer._build_entity_ctx(server);server.broadcast_create_entity=lambda e:events.append('create:'+str(e.entity_id));server.broadcast_destroy_entity=lambda id:events.append('destroy:'+str(id));server.broadcast_change_entity_position=lambda e:None
  def capture(lane,data,**kw):
   if data[0] in [5,46]:wire.append(lane+':'+bytes(data).hex())
  server.broadcast=lambda data,**kw:capture('B',data,**kw)
  for id,team in [(0,2),(1,3),(2,2)]:
   p=Player(id,'Life',team,6,NS(server=server,in_game=True,send=lambda data,_id=id,**kw:capture('O'+str(_id),data,**kw)));server.players[id]=p;p.apply_class_selection(ClassSelection(cid if id else 0,(5,6,23)));p.spawn(36+id*3,36,97);p.orientation=(1.,0.,0.);p.health=health;p.input.crouch=bool(crouch and id==1);p.god_mode=god and id==1
  if modifier!='none':server.mode.modify_incoming_damage=lambda p,amount,source,kind:0. if modifier=='veto' else amount*(.5 if modifier=='half' else 2.)
  for y in range(35,39):
   for z in range(96,100):
    if wall==1 or wall==2 and z==97 or wall==3 and z>=98:manager.set_block(40,y,z,True,0x406080)
  for i in range(2):server.entity_registry.place(36,42+i,37,97,behavior=DamageableEntityBehavior(health))
  clock[0]+=elapsed;clock[1]+=elapsed;wire.clear();events.clear();origin=(39.,36.,97.);thrower=server.players[0] if case%17 else None
  BattleSpadesServer._apply_blast(server,*origin,damage,0.,kill,thrower,blast_radius=radius,knockback_min=minimum,knockback_max=maximum,self_knockback_min=self_min,self_knockback_max=self_max,prediction_frame_delay=frames,ignore_player_los=ignore)
  state=[]
  for p in server.players.values():
   pending=';'.join(str(e.target_input_sequence)+','+vec(e.origin)+','+','.join(map(bits,[e.blast_radius,e.knockback_min,e.knockback_max])) for e in p._pending_explosion_impulses) or '-'
   state.append('|'.join([str(p.health),str(int(p.alive)),str(int(p.spawned)),vec(p.velocity),'-' if p._rocket_jump_blast_at is None else bits(p._rocket_jump_blast_at),pending,str(p.kills),str(p.deaths)]))
  entities=';'.join(str(e.entity_id)+','+bits(e.behavior.health) for e in server.entity_registry.all()) or '-'
  inputs=[case,kill,bits(damage),bits(radius),int(friendly),int(ignore),'-' if frames is None else frames,wall,cid,bits(protection),bits(elapsed),bits(weapon),modifier,int(god),health,crouch,'-' if self_min is None else bits(self_min),'-' if self_max is None else bits(self_max),'-' if thrower is None else thrower.id]
  rows.append((*inputs,'~'.join(state),entities,';'.join(events) or '-','|'.join(wire) or '-'))
 # Original achievement wrapper fault boundaries with controlled engine/stimulus IO.
 for case in range(8):
  events=[]
  def begin(*args):
   events.append('begin')
   if case&1:raise RuntimeError('Controlled begin fault')
   return object()
  def end(scope):
   events.append('end')
   if case&2:raise RuntimeError('Controlled end fault')
  def stimulus(*args,**kwargs):
   events.append('stimulus')
   if case&4:raise RuntimeError('Controlled stimulus fault')
  engine=NS(active=True,blast_begin=begin,blast_end=end,fault=lambda phase:events.append('fault:'+phase));server=NS(achievements=engine,config=NS(build_damage=False),bot_stimuli=NS(publish=stimulus),players={},entity_registry=EntityRegistry(),tick_interval=1/60,world_manager=NS(),broadcast_create_entity=lambda *a:None,broadcast_destroy_entity=lambda *a:None,broadcast_change_entity_position=lambda *a:None);server._build_entity_ctx=lambda:BattleSpadesServer._build_entity_ctx(server);result='ok'
  try:BattleSpadesServer._apply_blast(server,0.,0.,0.,10.,0.,254,None,ignore_player_los=True)
  except RuntimeError:result='failure'
  rows.append(('scope',case,result,';'.join(events)))
finally:time.monotonic,time.time=original
raw=('\n'.join('\t'.join(map(str,r)) for r in rows)+'\n').encode();buffer=io.BytesIO()
with gzip.GzipFile(filename='',mode='wb',fileobj=buffer,mtime=0) as stream:stream.write(raw)
path=OUT/'blast-commit.tsv.gz';path.write_bytes(buffer.getvalue());(OUT/'blast-commit-provenance.json').write_text(json.dumps(dict(source_commit='6216b771282d2d349ccd67ee58c378f035c83553',cases=576,scope_cases=8,source_files_sha256={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in ['server/main.py','server/player.py','server/weapons_retail.py','server/explosions.py','server/entities/registry.py']},fixture_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),method_substitutions=False,scope='Unchanged decorated method ASTs from server/main.py, actual native world, original Player HP/death/prediction and damageable registry. Controlled ordinary TDM/rules/clocks and lifecycle/network IO. Build damage disabled in these traces; separate terrain traces cover its commit. No complete mode/achievement/stimulus service, full gameplay loop or supported-client evidence.'),indent=2)+'\n');print('Blast commit cases',len(rows))
